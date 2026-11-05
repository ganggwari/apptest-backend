package com.mbc.puf.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.service.KmaClient;
import com.mbc.puf.weather.service.WeatherCollectService;

import tools.jackson.databind.JsonNode;

/**
 * 시연용 가짜 기상청·침수모델·수위계를 10초마다 한 번씩 돌린다 (puf.demo.enabled=true 일 때만).
 *
 *  - 호우 시나리오 중인 지역: 10초마다 새 값 (기상청 관측·예보 → 침수모델 점수·예측 → 수위계)
 *  - 그 밖의 지역(평상시): DemoCalmProfile 의 값을 지하차도마다 약 1분에 한 번씩 보낸다. 점수·수위·강수가 같은 단계에 맞춰 함께 움직이고
 *    (단계는 바뀌지 않음) 45곳을 한꺼번에 보내지 않고 10초마다 일부씩 나눠 보낸다 → "갱신 지연"이 안 뜨고, 버튼을 안 누르면 알림도 안 생긴다.
 *    관측·예보 저장은 기존 주기(관측 5분, 예보 10분)대로.
 *
 * 침수모델·수위계는 실제와 똑같이 HTTP 로 내부 API(/api/internal/**)를 호출한다.
 * 시나리오는 선택한 지역에만 적용되므로 다른 지역에는 알림이 가지 않는다.
 *
 * puf.demo.model-stub-enabled=false 면 가짜 침수모델(현재·미래 점수)은 보내지 않고 날씨·수위만 만든다.
 * 진짜 침수 서버(FastAPI 8001)가 이 날씨를 읽어 점수를 계산하므로, 둘을 같이 켜면 점수가 섞인다.
 */
@Component
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoStubScheduler {

    private static final Logger log = LoggerFactory.getLogger(DemoStubScheduler.class);

    private static final int TICK_SECONDS = 10;         // @Scheduled 주기와 같아야 한다
    private static final int FORECAST_HOURS = 6;        // 위험도 미래 예측: 앞으로 6시간

    private final DemoWaterProfile waterProfile;
    private final DemoScenarioState state;
    private final DemoScenarioService scenarioService;
    private final DemoInternalApi api;
    private final KmaClient kmaClient;
    private final WeatherCollectService weatherCollect;
    private final DbClock dbClock;
    private final StreamPublisher streamPublisher;
    private final org.springframework.beans.factory.ObjectProvider<DemoLevelService> levels;

    // 시나리오 밖(평상시) 지역의 갱신 주기. application.properties 의 puf.demo.calm-*-minutes 로 조절한다
    private final int calmScoreEvery;
    private final int calmWaterEvery;
    private final int calmForecastEvery;

    // 평상시 위험점수가 조금씩 오르내리게 하는 값 (지하차도별 현재값). 안전 ~ 주의 사이를 왔다갔다 한다
    private final double calmMaxScore;
    private final double calmStep;
    private final boolean modelStubEnabled;     // false = 점수는 진짜 침수 서버가 보낸다
    private final Map<Long, Double> calmScores = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Random random = new java.util.Random();

    private final int calmSlots;               // 평상시 갱신을 몇 tick 에 나눠 보내는가 (60초 / 10초 = 6)
    private final int calmForecastCycles;       // 예측을 몇 번에 한 번 다시 넣는가 (10분 / 60초 = 10)

    private long tickCount = 0;

    public DemoStubScheduler(DemoScenarioState state, DemoScenarioService scenarioService, DemoInternalApi api,
                             KmaClient kmaClient, WeatherCollectService weatherCollect, DbClock dbClock,
                             StreamPublisher streamPublisher,
                             org.springframework.beans.factory.ObjectProvider<DemoLevelService> levels,
                             @Value("${puf.demo.calm-score-minutes:5}") int calmScoreMinutes,
                             @Value("${puf.demo.calm-water-minutes:5}") int calmWaterMinutes,
                             @Value("${puf.demo.calm-forecast-minutes:10}") int calmForecastMinutes,
                             @Value("${puf.demo.calm-max-score:75}") double calmMaxScore,
                             @Value("${puf.demo.calm-step:10}") double calmStep,
                             @Value("${puf.demo.model-stub-enabled:true}") boolean modelStubEnabled, DemoWaterProfile waterProfile,
                             @Value("${puf.demo.calm-update-seconds:60}") int calmUpdateSeconds) {
        this.waterProfile=waterProfile;
        this.state = state;
        this.scenarioService = scenarioService;
        this.api = api;
        this.kmaClient = kmaClient;
        this.weatherCollect = weatherCollect;
        this.dbClock = dbClock;
        this.streamPublisher = streamPublisher;
        this.levels = levels;
        this.calmScoreEvery = ticks(calmScoreMinutes);
        this.calmWaterEvery = ticks(calmWaterMinutes);
        this.calmForecastEvery = ticks(calmForecastMinutes);
        this.calmMaxScore = calmMaxScore;
        this.calmStep = calmStep;
        this.modelStubEnabled = modelStubEnabled;
        this.calmSlots = Math.max(1, calmUpdateSeconds / TICK_SECONDS);
        this.calmForecastCycles = Math.max(1, calmForecastEvery / this.calmSlots);
    }

    // 평상시 점수: 중간값(35) 쪽으로 살짝 끌려가면서 매번 조금씩(±calmStep 정도) 움직인다. 바닥은 baseline, 위는 calmMaxScore
    private double nextCalmScore(Long uid, double baseline) {
        double cur = calmScores.getOrDefault(uid, baseline);
        double next = cur + 0.08 * (35 - cur) + random.nextGaussian() * calmStep;
        next = Math.max(baseline, Math.min(calmMaxScore, next));
        calmScores.put(uid, next);
        return next;
    }

    // 분 → tick 횟수 (최소 1)
    private static int ticks(int minutes) {
        return Math.max(1, minutes * 60 / TICK_SECONDS);
    }

    /** 시연 초기화: 평상시 점수 기억값을 비운다 */
    public void forgetState() {
        calmScores.clear();
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = TICK_SECONDS * 1000L)
    public void tick() {
        if (DemoResetService.isResetting()) {
            return;
        }
        try {
            run();
        } catch (RuntimeException e) {
            log.warn("[demo] stub tick failed: {}", e.toString());
        }
    }

    private void run() {
        long n = tickCount++;
        boolean calmScore = n % calmScoreEvery == 0;
        boolean calmWater = n % calmWaterEvery == 0;
        boolean calmForecast = n % calmForecastEvery == 0;

        // 시나리오가 끝났거나 중지됐으면 전체를 잔잔한 값으로 한 번 되돌린다 (수위 0, 점수 평소 값, 예보 평소 값)
        if (state.finished()) {
            scenarioService.finishNow();
        }
        boolean resetAll = false;
        if (state.takeReset()) {
            calmScore = true;
            calmWater = true;
            calmForecast = true;
            resetAll = true;   // 시나리오가 끝난 직후: 모든 지하차도를 이번 tick 에 평상시 값으로 되돌린다
        }

        DemoScenarioState.Run r = state.current();
        List<WeatherArea> areas = weatherCollect.allAreas();
        List<Long> allUnderpasses = scenarioService.allUnderpassIds();

        // ---- 1) 기상청: 관측값/예보 저장 ----
        for (WeatherArea area : areas) {
            boolean active = state.isAreaActive(area.getId());
            if (active || calmScore) {
                weatherCollect.saveObservation(area, kmaClient.fetchObservation(area));
            }
            if (active || calmForecast) {
                weatherCollect.saveForecast(area, kmaClient.fetchForecast(area));
            }
        }
        if (calmForecast) {
            weatherCollect.purgeOldObservations();
        }

        // ---- 2) 침수모델 + 3) 수위계 ----
        double t = state.scenarioSec();
        DemoLevelService pinned = levels.getIfAvailable();
        if (pinned != null) {
            pinned.refreshPinned();   // 버튼으로 단계를 고정한 지하차도는 같은 값을 계속 넣는다
        }
        for (int idx = 0; idx < allUnderpasses.size(); idx++) {
            Long uid = allUnderpasses.get(idx);
            if (pinned != null && pinned.isPinned(uid)) {
                continue;   // 고정된 곳은 평상시 값·호우 시나리오가 덮어쓰지 않는다
            }
            boolean active = r != null && state.isUnderpassActive(uid);
            try {
                if (!active && modelStubEnabled) {
                    // 평상시: 자기 차례(약 1분에 한 번)인 지하차도만 보낸다. 점수·수위·강수는 같은 단계에 맞춰 함께.
                    if (resetAll || idx % calmSlots == n % calmSlots) {
                        calmUpdate(uid, resetAll || (n / calmSlots + idx) % calmForecastCycles == 0);
                    }
                    continue;
                }
                if (modelStubEnabled && (active || calmScore)) {
                    modelStub(uid, active);
                }
                waterStub(uid, active, t, active || calmWater);
                if (modelStubEnabled && (active || calmForecast)) {
                    forecastStub(uid, active);
                }
            } catch (RuntimeException e) {
                log.warn("[demo] stub failed for underpass {}: {}", uid, e.toString());
            }
        }

        // ---- 단계가 바뀌면 프런트에 알림 ----
        if (r != null) {
            DemoScenarioState.Phase phase = state.phaseAt(t);
            if (phase != state.lastPublishedPhase()) {
                state.markPublished(phase);
                streamPublisher.dataChangedPublic(null, "demo-scenario", "UPDATE", null);
            }
        }
    }

    // 평상시 한 번 보내기: 수위 → 점수(+강수) → (가끔) 예측. DemoCalmProfile 의 같은 단계 값이라 단계가 바뀌지 않는다
    private void calmUpdate(Long uid, boolean withForecast) {
        state.resetScore(uid);
        long sec = dbClock.now().toEpochSecond(ZoneOffset.UTC);
        double water = DemoCalmProfile.waterCm(uid, sec);
        double rain = DemoCalmProfile.rainMmH(uid, sec);
        LocalDateTime now = dbClock.now().truncatedTo(ChronoUnit.MILLIS);

        waterProfile.sync(uid, water);
        Map<String, Object> w = new LinkedHashMap<>();
        w.put("underpassId", uid);
        w.put("levelCm", dec(water, 1));
        w.put("measuredAt", now);
        api.postWaterLevel(w);

        double[] acc = DemoCalmProfile.accumulations(rain);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("underpassId", uid);
        s.put("riskScore", dec(DemoCalmProfile.score(uid, sec), 2));
        s.put("rainfallMm", dec(rain, 1));
        String[] keys = {"rainfall1hMm", "rainfall3hMm", "rainfall6hMm", "rainfall12hMm", "rainfall24hMm"};
        for (int i = 0; i < keys.length; i++) {
            s.put(keys[i], dec(acc[i], 1));
        }
        s.put("predictedAt", now);
        api.postRiskScore(s);

        if (!withForecast) {
            return;
        }
        JsonNode forecast = api.forecast(uid);
        if (forecast == null || !forecast.isArray()) {
            return;
        }
        double score = DemoCalmProfile.score(uid, sec);
        List<Map<String, Object>> items = new ArrayList<>();
        int count = 0;
        for (JsonNode f : forecast) {
            if (count++ >= FORECAST_HOURS) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetAt", f.get("targetAt"));
            item.put("riskScore", dec(score, 2));
            item.put("rainfallMm", dec(DemoCalmProfile.forecastMm(rain, count), 1));
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("predictedAt", now);
        body.put("items", items);
        api.postRiskForecast(body);
    }

    // 침수모델 흉내: 강수(내부 API)를 읽어 위험점수를 계산해 보낸다
    private void modelStub(Long uid, boolean active) {
        JsonNode rain = api.rainfall(uid);
        if (rain == null || rain.isNull()) {
            return;
        }
        double current = num(rain, "rainfallMm");
        double baseline = 3 + (uid % 7);
        double score;
        if (active) {
            double factor = 0.9 + (uid % 5) * 0.05;
            double target = 100 * (1 - Math.exp(-(current * factor) / 4.0));
            score = state.nextScore(uid, target, baseline);
        } else {
            state.resetScore(uid);
            score = nextCalmScore(uid, baseline);   // 평상시에도 안전 ~ 주의 사이를 조금씩 왔다갔다
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("riskScore", dec(Math.min(100, score), 2));
        body.put("rainfallMm", rain.get("rainfallMm"));
        body.put("rainfall1hMm", rain.get("rainfall1hMm"));
        body.put("rainfall3hMm", rain.get("rainfall3hMm"));
        body.put("rainfall6hMm", rain.get("rainfall6hMm"));
        body.put("rainfall12hMm", rain.get("rainfall12hMm"));
        body.put("rainfall24hMm", rain.get("rainfall24hMm"));
        body.put("predictedAt", dbClock.now().truncatedTo(ChronoUnit.MILLIS));
        api.postRiskScore(body);
    }

    // 침수모델 흉내(미래): 예보(내부 API)를 읽어 앞으로 6시간의 위험점수를 계산해 보낸다
    private void forecastStub(Long uid, boolean active) {
        JsonNode forecast = api.forecast(uid);
        if (forecast == null || !forecast.isArray()) {
            return;
        }
        double factor = 0.9 + (uid % 5) * 0.05;
        double baseline = 3 + (uid % 7);
        LocalDateTime now = dbClock.now().truncatedTo(ChronoUnit.MILLIS);
        List<Map<String, Object>> items = new ArrayList<>();
        double calmFuture = calmScores.getOrDefault(uid, baseline);
        int count = 0;
        for (JsonNode f : forecast) {
            if (count++ >= FORECAST_HOURS) {
                break;
            }
            double rain = num(f, "rainfallMm");
            double score;
            if (active) {
                score = Math.max(baseline, 100 * (1 - Math.exp(-(rain * factor) / 30.0)));
            } else {
                // 평상시: 예보 강수가 0이라 그대로 계산하면 미래만 바닥값(안전)으로 납작해진다.
                // 현재 점수에서 출발해 중간값(35) 쪽으로 천천히 끌려가며 조금씩 흔들리게 한다.
                calmFuture = calmFuture + 0.08 * (35 - calmFuture) + random.nextGaussian() * calmStep * 0.6;
                calmFuture = Math.max(baseline, Math.min(calmMaxScore, calmFuture));
                score = calmFuture;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("targetAt", f.get("targetAt"));
            item.put("riskScore", dec(Math.min(100, score), 2));
            item.put("rainfallMm", f.get("rainfallMm"));
            items.add(item);
        }
        if (items.isEmpty()) {
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("predictedAt", now);
        body.put("items", items);
        api.postRiskForecast(body);
    }

    // 수위계 흉내: 시나리오 중에는 곡선대로, 평소에는 0 근처
    private void waterStub(Long uid, boolean active, double t, boolean due) {
        // 평상시(시나리오 아님)에는 위험점수가 아니라 최근 강수에서 수위를 만든다: 비가 없으면 수위도 0 에 가깝다
        java.util.OptionalDouble rainWater=java.util.OptionalDouble.empty();
        if(!active&&due){
            try {
                JsonNode rain=api.rainfall(uid);
                if(rain!=null&&!rain.isNull())rainWater=java.util.OptionalDouble.of(DemoWaterProfile.rainWaterCm(num(rain,"rainfallMm"),num(rain,"rainfall1hMm")));
            } catch (RuntimeException e) {
                log.warn("[demo] rainfall read failed for underpass {}: {}", uid, e.toString());
            }
        }
        var sample=waterProfile.sample(uid,due,active?state.waterAt(t):0,active,rainWater);
        if(sample.isEmpty())return;
        double level=sample.getAsDouble();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("underpassId", uid);
        body.put("levelCm", dec(level, 1));
        body.put("measuredAt", dbClock.now().truncatedTo(ChronoUnit.MILLIS));
        api.postWaterLevel(body);
    }

    private static double num(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? 0 : v.asDouble();
    }

    private static BigDecimal dec(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_UP);
    }
}