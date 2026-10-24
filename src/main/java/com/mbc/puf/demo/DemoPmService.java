package com.mbc.puf.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mbc.puf.common.jpa.DbClock;

import tools.jackson.databind.JsonNode;

/**
 * [시연용] 가짜 예지보전 서버. 실제 예지보전 서버(FastAPI)가 하는 것처럼 설비 점수를 /api/internal/pm-scores 로 주기적으로 보낸다.
 *  - 평소: 설비마다 낮은 점수(안전)를 조금씩 흔들며 보낸다 → 관리자 앱 예지보전 화면에 값이 계속 찍힌다.
 *  - demo.html 에서 설비 점수를 끌어올리면(pin) 그 값이 즉시 들어가고 같은 값이 계속 유지된다.
 *    점수를 받은 쪽(PmIngestService)이 원래 로직대로 점검 이벤트 · 관리자 알림 · 앱 푸시(위험은 긴급) · SSE(pm-update, notification-new)를 낸다.
 *  - 고정을 풀면 평소 점수로 돌아간다(점수가 내려가는 건 이벤트를 만들지 않는다).
 */
@Service
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoPmService {

    private static final Logger log = LoggerFactory.getLogger(DemoPmService.class);

    private final DemoInternalApi api;
    private final JdbcTemplate jdbc;
    private final DbClock dbClock;
    private final boolean enabled;
    private final Random random = new Random();
    private final Map<Long, BigDecimal> pins = new ConcurrentHashMap<>();
    private final Map<Long, Double> calm = new ConcurrentHashMap<>();

    public DemoPmService(DemoInternalApi api, JdbcTemplate jdbc, DbClock dbClock,
                         @Value("${puf.demo.pm-stub-enabled:true}") boolean enabled) {
        this.api = api;
        this.jdbc = jdbc;
        this.dbClock = dbClock;
        this.enabled = enabled;
    }

    /** 설비 목록 + 최신 점수 + 고정 여부 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id AS "equipId", e.name, e.type::text AS type, u.id AS "underpassId", u.name AS "underpassName",
                       s.score AS score, s.level::text AS level, s.predicted_at AS "predictedAt"
                FROM pm_equipment e
                JOIN underpass u ON u.id = e.underpass_id
                LEFT JOIN LATERAL (SELECT score, level, predicted_at FROM pm_score WHERE equip_id = e.id ORDER BY predicted_at DESC LIMIT 1) s ON TRUE
                WHERE e.is_deleted = FALSE AND u.is_deleted = FALSE
                ORDER BY u.id, e.type, e.id
                """);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            BigDecimal pin = pins.get(((Number) r.get("equipId")).longValue());
            m.put("pinnedScore", pin);
            out.add(m);
        }
        return out;
    }

    /** 설비 점수를 올린다(고정). 즉시 한 번 보내고, 이후 tick 마다 같은 값을 보낸다 */
    public List<Map<String, Object>> boost(List<Long> equipIds, BigDecimal score) {
        BigDecimal s = score.max(BigDecimal.ZERO).min(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        List<Long> ids = equipIds == null || equipIds.isEmpty() ? List.of() : equipIds;
        for (Long id : ids) {
            pins.put(id, s);
        }
        sendScores(ids, true);
        log.info("[DEMO] 예지보전 설비 {} 점수 {} 고정", ids, s);
        return list();
    }

    public List<Map<String, Object>> release(List<Long> equipIds) {
        if (equipIds == null || equipIds.isEmpty()) {
            pins.clear();
        } else {
            equipIds.forEach(pins::remove);
        }
        return list();
    }

    @Scheduled(initialDelay = 25000, fixedDelayString = "${puf.demo.pm-interval-seconds:30}000")
    public void tick() {
        if (!enabled) {
            return;
        }
        try {
            sendScores(null, false);
        } catch (RuntimeException e) {
            log.warn("[DEMO] 예지보전 점수 전송 실패: {}", e.toString());
        }
    }

    // ids == null 이면 전체 설비
    private void sendScores(List<Long> ids, boolean pinnedOnly) {
        JsonNode equipments = api.pmEquipments();
        if (equipments == null || !equipments.isArray()) {
            return;
        }
        var now = dbClock.now().truncatedTo(ChronoUnit.SECONDS);
        List<Map<String, Object>> scores = new ArrayList<>();
        for (JsonNode e : equipments) {
            long id = e.get("equipId").asLong();
            if (ids != null && !ids.isEmpty() && !ids.contains(id)) {
                continue;
            }
            if (pinnedOnly && !pins.containsKey(id)) {
                continue;
            }
            String type = e.get("type").asText();
            BigDecimal pin = pins.get(id);
            BigDecimal score = pin != null ? pin : BigDecimal.valueOf(calmScore(id)).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("equipId", id);
            item.put("type", type);
            item.put("score", score);
            item.put("predictedAt", now);
            item.put("reasons", pin != null ? List.of(reason(type, score)) : List.of());
            item.put("modelVersion", "demo-stub");
            scores.add(item);
        }
        if (scores.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("scores", scores);
        body.put("backfill", false);
        api.postPmScores(body);
    }

    private double calmScore(long id) {
        double base = 8 + (id * 7) % 14;                       // 설비마다 조금씩 다른 평소 점수 (8~21)
        double cur = calm.getOrDefault(id, base);
        double next = cur + 0.2 * (base - cur) + random.nextGaussian() * 1.5;
        next = Math.max(3, Math.min(30, next));
        calm.put(id, next);
        return next;
    }

    private static String reason(String type, BigDecimal score) {
        boolean danger = score.compareTo(BigDecimal.valueOf(70)) >= 0;
        String level = danger ? "매우 " : "";
        return switch (type) {
            case "PUMP" -> "진동·전류가 평소보다 " + level + "높아요";
            case "CCTV" -> "영상 수신률이 평소보다 " + level + "낮아요";
            case "DISPLAY" -> "본체 온도·오류 횟수가 평소보다 " + level + "높아요";
            case "WATER_LEVEL" -> "수위 측정값이 평소와 " + level + "달라요";
            default -> "설비 상태가 평소와 달라요";
        };
    }
}
