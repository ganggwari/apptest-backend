package com.mbc.puf.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *  - 평소: 설비마다 낮은 점수(안전)를 조금씩 흔들며 보낸다 → 관리자 앱 예지보전 화면에 값이 계속 찍힌다. (난수 없이 시각으로 부드럽게 움직여서 점수 급상승 이벤트가 안 생긴다)
 *  - 시작할 때부터 위험으로 고정된 설비(DEFAULT_DANGER): 초량 CCTV C-1-1 · 전광판 D-1-1 · 수위센서 W-1-3 · 배수펌프 P-1-2.
 *    5.seed_demo SQL 의 시작 상태(점검 대기 이벤트)와 같은 점수이고, 서버를 다시 켜도 같은 점수로 계속 보내서 위험이 풀리지 않는다.
 *    demo.html 에서 고정을 풀면(release) 평소 점수로 내려간다 (서버를 다시 켜면 다시 위험).
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
    // 시작 상태의 위험 설비 (초량지하차도 설비 이름 → 점수). 5.seed_demo_v14.sql 의 위험 설비 점수와 같다
    private static final Map<String, Double> DEFAULT_DANGER = Map.of("C-1-1", 84.0, "D-1-1", 88.0, "W-1-3", 78.5, "P-1-2", 91.5);
    private static final double DANGER_WOBBLE = 1.2;     // 위험 점수는 ±1.2 안에서만 움직인다 (위험 기준 70 아래로 안 내려감)

    private final Map<Long, BigDecimal> pins = new ConcurrentHashMap<>();         // demo.html 에서 올린 점수 (그대로 고정)
    private final Map<Long, Double> basePins = new ConcurrentHashMap<>();         // 시작부터 위험인 설비 (조금씩 흔들며 고정)
    private volatile boolean basePinsLoaded = false;

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
            long equipId = ((Number) r.get("equipId")).longValue();
            BigDecimal pin = pins.get(equipId);
            if (pin == null && basePins.containsKey(equipId)) {
                pin = BigDecimal.valueOf(basePins.get(equipId));
            }
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
            basePins.clear();
        } else {
            equipIds.forEach(id -> {
                pins.remove(id);
                basePins.remove(id);
            });
        }
        return list();
    }

    /** DB 를 시연 시작 상태로 다시 넣은 뒤: 고정 점수를 모두 비우고 시작 위험 설비를 다시 찾게 한다 */
    public void resetForReseed() {
        pins.clear();
        basePins.clear();
        basePinsLoaded = false;
    }

    @Scheduled(initialDelay = 25000, fixedDelayString = "${puf.demo.pm-interval-seconds:30}000")
    public void tick() {
        if (!enabled || DemoResetService.isResetting()) {
            return;
        }
        try {
            loadBasePins();
            sendScores(null, false);
        } catch (RuntimeException e) {
            log.warn("[DEMO] 예지보전 점수 전송 실패: {}", e.toString());
        }
    }

    // 시작 상태의 위험 설비를 DB 에서 찾아 고정 목록에 넣는다 (한 번 찾으면 끝. 설비가 아직 없으면 다음 tick 에 다시)
    private void loadBasePins() {
        if (basePinsLoaded) {
            return;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT e.id, e.name FROM pm_equipment e JOIN underpass u ON u.id = e.underpass_id
                WHERE u.name = '초량지하차도' AND u.is_deleted = FALSE AND e.is_deleted = FALSE AND e.name IN ('C-1-1', 'D-1-1', 'W-1-3', 'P-1-2')
                """);
        for (Map<String, Object> r : rows) {
            Double score = DEFAULT_DANGER.get((String) r.get("name"));
            if (score != null) {
                basePins.put(((Number) r.get("id")).longValue(), score);
            }
        }
        basePinsLoaded = !rows.isEmpty();
        if (basePinsLoaded) {
            log.info("[DEMO] 시작부터 위험인 설비 고정: {}", basePins.keySet());
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
            if (pinnedOnly && !pins.containsKey(id) && !basePins.containsKey(id)) {
                continue;
            }
            String type = e.get("type").asText();
            BigDecimal pin = pins.get(id);
            if (pin == null && basePins.containsKey(id)) {
                pin = BigDecimal.valueOf(dangerScore(id, basePins.get(id))).setScale(2, RoundingMode.HALF_UP);
            }
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

    // 평소 점수: 설비마다 조금씩 다른 기준(3~16)에서 시각에 따라 ±1.5 부드럽게 움직인다 (난수 없음 → 급상승 이벤트가 안 생김)
    private double calmScore(long id) {
        double base = 3 + (id * 7) % 14;
        double t = dbClock.now().toEpochSecond(java.time.ZoneOffset.UTC);
        return Math.max(1, base + 1.5 * Math.sin(2 * Math.PI * t / 600.0 + id));
    }

    // 시작부터 위험인 설비: 기준 점수 ±1.2 안에서 부드럽게
    private double dangerScore(long id, double base) {
        double t = dbClock.now().toEpochSecond(java.time.ZoneOffset.UTC);
        return base + DANGER_WOBBLE * Math.sin(2 * Math.PI * t / 420.0 + id * 1.3);
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
