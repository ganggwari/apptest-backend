package com.mbc.puf.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.risk.RiskPolicy;
import com.mbc.puf.threshold.entity.Threshold;
import com.mbc.puf.threshold.repository.ThresholdRepository;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.UnderpassRepository;

import tools.jackson.databind.JsonNode;

/**
 * [시연용] 지하차도 위험 5단계를 버튼으로 직접 고르는 기능 (puf.demo.enabled=true 일 때만).
 *
 * 단계를 DB 에 직접 쓰지 않는다. 실제 침수모델·수위계가 하는 것과 똑같이 "위험점수"와 "수위" 값을 내부 API 로 넣고,
 * 단계 판정(RiskPolicy) → 이벤트·알림·전광판·자동 차단·내비 우회·접근/최종 알림 판단은 원래 로직이 그대로 한다.
 *
 *   안전 : 점수 5,  수위 0
 *   관심 : 점수 = 관심 하한 + 2,  수위 0
 *   주의 : 점수 = 주의 하한 + 2,  수위 0
 *   경계 : 점수 = 주의 구간,  수위 = 차단 수위 + 0.5   → 자동 차단까지 일어난다
 *   심각 : 점수 = 주의 구간,  수위 = 차단 수위 + 심각 추가분 + 0.5
 *
 * 고른 지하차도는 "고정(pin)" 되어 10초마다 같은 값이 다시 들어가므로 평상시 가짜 갱신(랜덤)이나 호우 시나리오가 덮어쓰지 않는다.
 * 고정을 풀면(release) 평상시 가짜 갱신이 다시 시작된다. 차단된 지하차도의 해제는 관리자 화면에서 직접 한다(수위가 차단 수위 미만이어야 가능).
 */
@Service
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoLevelService {

    private static final Logger log = LoggerFactory.getLogger(DemoLevelService.class);
    private static final int FORECAST_HOURS = 6;

    /** 고정한 값 */
    public record Pin(RiskLevel level, BigDecimal score, BigDecimal waterCm) {}

    private final UnderpassRepository underpassRepository;
    private final ThresholdRepository thresholdRepository;
    private final RiskPolicy policy;
    private final DemoInternalApi api;
    private final DbClock dbClock;
    private final List<Long> targetIds;
    private final BigDecimal severeExtraCm;

    private final Map<Long, Pin> pins = new ConcurrentHashMap<>();

    public DemoLevelService(UnderpassRepository underpassRepository, ThresholdRepository thresholdRepository,
                            RiskPolicy policy, DemoInternalApi api, DbClock dbClock,
                            @Value("${puf.demo.level-target-ids:1,14,15}") String targets,
                            @Value("${puf.risk.water.severe-extra-cm:15}") BigDecimal severeExtraCm) {
        this.underpassRepository = underpassRepository;
        this.thresholdRepository = thresholdRepository;
        this.policy = policy;
        this.api = api;
        this.dbClock = dbClock;
        this.targetIds = Arrays.stream(targets.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).toList();
        this.severeExtraCm = severeExtraCm;
    }

    public boolean isPinned(Long underpassId) {
        return pins.containsKey(underpassId);
    }

    /** 시연 대상 지하차도 (id 로 찾는다. 시드 기준 초량=1, 북항=14, 충장=15) */
    public List<Underpass> targets() {
        List<Underpass> result = new ArrayList<>();
        for (Long id : targetIds) {
            underpassRepository.findById(id).filter(u -> !u.isDeleted()).ifPresent(result::add);
        }
        return result;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Underpass u : targets()) {
            Pin pin = pins.get(u.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("underpassId", u.getId());
            row.put("name", u.getName());
            row.put("level", u.getCurrentRiskLevel().name());
            row.put("levelLabel", u.getCurrentRiskLevel().getDbValue());
            row.put("riskScore", u.getCurrentRiskScore());
            row.put("waterCm", u.getCurrentWaterCm());
            row.put("status", u.getCurrentStatus().name());
            row.put("blockCm", policy.blockCm(u));
            row.put("pinnedLevel", pin == null ? null : pin.level().name());
            rows.add(row);
        }
        return rows;
    }

    /** 고른 지하차도들을 해당 단계로 만든다 (underpassIds 가 비어 있으면 대상 전체) */
    public List<Map<String, Object>> set(List<Long> underpassIds, RiskLevel level) {
        List<Underpass> chosen = pick(underpassIds);
        for (Underpass u : chosen) {
            Pin pin = pinFor(u, level);
            pins.put(u.getId(), pin);
            push(u.getId(), pin, true);
        }
        log.info("[DEMO] 위험단계 {} 고정: {}", level, chosen.stream().map(Underpass::getName).toList());
        return list();
    }

    /** 고정 풀기 (underpassIds 가 비어 있으면 전체). 값은 그대로 두고 평상시 가짜 갱신이 이어받는다 */
    public List<Map<String, Object>> release(List<Long> underpassIds) {
        if (underpassIds == null || underpassIds.isEmpty()) {
            pins.clear();
        } else {
            underpassIds.forEach(pins::remove);
        }
        return list();
    }

    /** 스케줄러가 10초마다 부른다: 고정된 값을 다시 넣어 "갱신 지연"이 안 뜨고 값이 유지되게 한다 */
    public void refreshPinned() {
        pins.forEach((uid, pin) -> {
            try {
                push(uid, pin, false);
            } catch (RuntimeException e) {
                log.warn("[DEMO] 고정값 갱신 실패 underpass {}: {}", uid, e.toString());
            }
        });
    }

    // ---------------------------------------------------------------- 값 계산

    private List<Underpass> pick(List<Long> ids) {
        List<Underpass> all = targets();
        if (ids == null || ids.isEmpty()) {
            return all;
        }
        List<Underpass> chosen = all.stream().filter(u -> ids.contains(u.getId())).toList();
        if (chosen.isEmpty()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "시연 대상 지하차도가 아닙니다.");
        }
        return chosen;
    }

    private Pin pinFor(Underpass u, RiskLevel level) {
        List<Threshold> thresholds = thresholdRepository.findByUnderpassIdOrderByRiskScoreMinAsc(u.getId());
        BigDecimal block = policy.blockCm(u);
        BigDecimal score;
        BigDecimal water = BigDecimal.ZERO;
        switch (level) {
            case SAFE -> score = BigDecimal.valueOf(5);
            case WATCH -> score = minOf(thresholds, RiskLevel.WATCH).add(BigDecimal.valueOf(2));
            case CAUTION -> score = minOf(thresholds, RiskLevel.CAUTION).add(BigDecimal.valueOf(2));
            case ALERT -> {
                score = minOf(thresholds, RiskLevel.CAUTION).add(BigDecimal.valueOf(2));
                water = block.add(BigDecimal.valueOf(0.5));
            }
            case SEVERE -> {
                score = minOf(thresholds, RiskLevel.CAUTION).add(BigDecimal.valueOf(2));
                water = block.add(severeExtraCm).add(BigDecimal.valueOf(0.5));
            }
            default -> throw new BusinessException(HttpStatus.BAD_REQUEST, "알 수 없는 단계");
        }
        return new Pin(level, score.setScale(2, RoundingMode.HALF_UP), water.setScale(1, RoundingMode.HALF_UP));
    }

    private static BigDecimal minOf(List<Threshold> thresholds, RiskLevel level) {
        return thresholds.stream().filter(t -> t.getLevel() == level).map(Threshold::getRiskScoreMin).findFirst()
                .orElse(BigDecimal.valueOf(switch (level) {
                    case WATCH -> 40;
                    case CAUTION -> 70;
                    default -> 0;
                }));
    }

    // ---------------------------------------------------------------- 넣기 (실제 침수모델·수위계와 같은 내부 API)

    private void push(Long uid, Pin pin, boolean withForecast) {
        // 단계가 내려갈 때 중간에 한 단계 더 높게 판정되지 않도록: 수위를 먼저, 점수를 나중에 넣는다
        postWater(uid, pin.waterCm());
        postScore(uid, pin.score());
        if (withForecast) {
            postForecast(uid, pin.score());
        }
    }

    private void postWater(Long uid, BigDecimal water) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("levelCm", water);
        body.put("measuredAt", dbClock.now().truncatedTo(ChronoUnit.MILLIS));
        api.postWaterLevel(body);
    }

    private void postScore(Long uid, BigDecimal score) {
        JsonNode rain = api.rainfall(uid);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("riskScore", score);
        for (String key : new String[] {"rainfallMm", "rainfall1hMm", "rainfall3hMm", "rainfall6hMm", "rainfall12hMm", "rainfall24hMm"}) {
            JsonNode v = rain == null ? null : rain.get(key);
            body.put(key, v == null || v.isNull() ? BigDecimal.ZERO : v.decimalValue());
        }
        body.put("predictedAt", dbClock.now().truncatedTo(ChronoUnit.MILLIS));
        api.postRiskScore(body);
    }

    // 예측 그래프도 같은 점수로 평평하게 (평상시 랜덤값이 섞이지 않게)
    private void postForecast(Long uid, BigDecimal score) {
        JsonNode forecast = api.forecast(uid);
        if (forecast == null || !forecast.isArray()) {
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        int count = 0;
        for (JsonNode f : forecast) {
            if (count++ >= FORECAST_HOURS) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetAt", f.get("targetAt"));
            item.put("riskScore", score);
            item.put("rainfallMm", f.get("rainfallMm"));
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("predictedAt", dbClock.now().truncatedTo(ChronoUnit.MILLIS));
        body.put("items", items);
        api.postRiskForecast(body);
    }
}
