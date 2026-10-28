package com.mbc.puf.pm.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.pm.dto.PmSensorBackfillResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 센서 과거 데이터를 DB 안에서 generate_series 로 한 번에 만든다 (행마다 자바를 거치지 않아 빠르다).
 *
 * 값의 평소 범위는 예지보전 모델 폴더의 샘플 데이터(CCTV·펌프)와 팀원 모델 기준값(전광판)에서 가져왔다.
 *   펌프  진동 980±2 / 압력 1013±14 / 온도 2.5 / 전류 1.1 / 유량 36 / 회전수 38   (이상: 진동·압력·온도·유량·회전수 하락, 전류 상승)
 *   CCTV  수신률 99% / 응답 49ms / 손실 0.18% / 흐림 6.6                          (이상: 수신률 하락, 응답·손실·흐림 상승)
 *   전광판 본체-외기 6.5℃ / 리플 0.028V / 응답 43ms / 오류 시간당 약 0.1건 / 불량화소 0.015%
 *          (이상 원인 4가지: 열 / 전원 / 통신 / 화면 — 설비 번호 순서대로)
 *   수위  0.30m 기준 하루 주기 ±0.02 + 잡음            (이상: 서서히 올라가는 드리프트 + 잡음 증가)
 *
 * 설비 종류별 번호(rk, id 순)에 따라 시나리오가 정해진다:  rk%4==1 위험까지 악화 / 2 주의까지 악화 / 3 계속 정상 / 0 끝 3일 전에 한 번 튀었다 복구.
 * 악화는 끝 시각에 가까울수록 커지는 곡선이고 시작 시점은 종류마다 다르다(펌프 36시간 전, CCTV·전광판 6일 전, 수위 2시간 전).
 * 종류별 악화 크기(위험·주의·튐)는 예지보전 서버의 실제 모델 점수로 보정한 값이다(2026-10-24, 시드를 바꿔 3번씩 점수를 낸 중앙값):
 *   위험 → 끝에서 78~86점 / 주의 → 끝에서 50~55점 / 튐 → 최고 60점 안팎 / 정상 → 40점 아래.
 *   수위계 모델은 천천히 오르는 값을 평소로 받아들여 점수가 잘 안 오르고 최근에 바뀐 값에만 반응해서, 수위의 위험은 끝 2시간 안의 급변으로 만든다.
 *   펌프 모델은 이상이 조금만 생겨도 72~75점으로 거의 바로 포화한다(모델이 낼 수 있는 최고 점수에 가까움).
 *   모델을 다시 학습하거나 센서 값을 바꾸면 상수를 다시 맞춰야 한다.
 */
@Service
public class PmSensorBackfillServiceImpl implements PmSensorBackfillService {

    // 시간에 따라 연속적으로 조금씩 움직이는 정상 범위 잡음.
    private static final String LIVE_NOISE = "(0.55*sin(EXTRACT(EPOCH FROM x.t)/97.0+x.id)+0.25*sin(EXTRACT(EPOCH FROM x.t)/31.0+x.id))";

    // 배수펌프 실시간 값만 흔들림을 키우는 배수. 모델이 "평소"로 보는 폭(센서별 표준편차 σ)의 약 1~2σ 안에서 움직이게 맞췄다.
    // 모델은 평소에서 3σ 이상 벗어나야 "평소와 달라요"로 보므로, 키워도 정상 상태가 이상으로 잡히지 않는다. 1.0 이면 예전과 같다.
    private static final double PUMP_LIVE_NOISE_SCALE = 2.2;

    private static final String NOISE = "((random() + random() + random() - 1.5) * 2)";   // 평균 0, 표준편차 약 1

    /** 설비 종류별 만드는 방법 */
    private record Spec(PmEquipmentType type, String table, long rampSec, long spikeLenSec,
            double ampDanger, double ampCaution, double ampSpike, String insertSql) {
    }

    /** 실시간으로 쌓는 값의 흔들림 식. 전광판은 안쪽 SELECT 별칭이 y 이고, 배수펌프는 폭을 키운다 */
    private static String liveNoise(PmEquipmentType type) {
        if (type == PmEquipmentType.DISPLAY) {
            return LIVE_NOISE.replace("x.", "y.");
        }
        if (type == PmEquipmentType.PUMP) {
            return PUMP_LIVE_NOISE_SCALE + "*" + LIVE_NOISE;
        }
        return LIVE_NOISE;
    }

    private static final Spec PUMP = new Spec(PmEquipmentType.PUMP, "pm_sensor_pump", 36 * 3600L, 2 * 3600L, 0.3, 0.06, 0.06, """
            INSERT INTO pm_sensor_pump (equip_id, recorded_at, vibration, pressure, temperature, current, flow, rpm)
            SELECT x.id, x.t,
                   980 + 2.0 * {N} - 850 * x.s,
                   1013 + 14 * {N} - 250 * x.s,
                   GREATEST(0, 2.505 + 0.015 * {N} - 0.6 * x.s),
                   GREATEST(0, 1.1 + 0.3 * {N} + 14 * x.s),
                   GREATEST(0, 36.2 + 1.8 * {N} - 4.5 * x.s),
                   GREATEST(0, 38 + 3 * {N} - 6 * x.s)
              FROM ({SEV}) x
            ON CONFLICT DO NOTHING
            """);

    private static final Spec CCTV = new Spec(PmEquipmentType.CCTV, "pm_sensor_cctv", 6 * 86400L, 5 * 3600L, 0.10, 0.05, 0.20, """
            INSERT INTO pm_sensor_cctv (equip_id, recorded_at, video_receive_rate, response_time_ms, packet_loss_rate, image_blur_score)
            SELECT x.id, x.t,
                   LEAST(100, GREATEST(0, 99.0 + 0.65 * {N} - 7.5 * x.s)),
                   GREATEST(0, 48.7 + 5.8 * {N} + 47 * x.s),
                   GREATEST(0, 0.18 + 0.19 * {N} + 2.2 * x.s),
                   GREATEST(0, 6.6 + 3.5 * {N} + 31 * x.s)
              FROM ({SEV}) x
            ON CONFLICT DO NOTHING
            """);

    // 전광판: 주변 온도를 한 번만 뽑아야 본체 온도와 짝이 맞아서 안쪽 SELECT 를 한 겹 더 둔다 (OFFSET 0 = 합쳐지지 않게)
    private static final Spec DISPLAY = new Spec(PmEquipmentType.DISPLAY, "pm_sensor_display", 6 * 86400L, 8 * 3600L, 0.40, 0.20, 0.70, """
            INSERT INTO pm_sensor_display (equip_id, recorded_at, temperature_c, ambient_c, ripple_v, response_time_ms, error_count, bad_pixel_pct)
            SELECT y.id, y.t,
                   y.a + 6.5 + 0.6 * {N} + CASE WHEN y.cause = 0 THEN 9 * y.s ELSE 0 END,
                   y.a,
                   GREATEST(0, 0.028 + 0.004 * {N} + CASE WHEN y.cause = 1 THEN 0.05 * y.s ELSE 0 END),
                   GREATEST(0, 43 + 5 * {N} + CASE WHEN y.cause = 2 THEN 60 * y.s ELSE 0 END),
                   CAST(CASE WHEN random() < 0.1 + CASE WHEN y.cause = 2 THEN 0.8 * y.s ELSE 0 END
                             THEN 1 + floor(random() * (1 + CASE WHEN y.cause = 2 THEN 8 * y.s ELSE 0 END)) ELSE 0 END AS integer),
                   GREATEST(0, 0.015 + 0.003 * {N} + CASE WHEN y.cause = 3 THEN 0.35 * y.s ELSE 0 END)
              FROM (SELECT x.id, x.t, x.s, (x.rk - 1) % 4 AS cause,
                           18 + 7 * sin(2 * pi() * (EXTRACT(HOUR FROM x.t) - 9) / 24) + 1.0 * {N} AS a
                      FROM ({SEV}) x OFFSET 0) y
            ON CONFLICT DO NOTHING
            """);

    private static final Spec WATER = new Spec(PmEquipmentType.WATER_LEVEL, "pm_sensor_water_level", 2 * 3600L, 3600L, 0.50, 0.10, 0.07, """
            INSERT INTO pm_sensor_water_level (equip_id, recorded_at, level_m)
            SELECT x.id, x.t,
                   0.30 + 0.02 * sin(2 * pi() * (EXTRACT(HOUR FROM x.t) + EXTRACT(MINUTE FROM x.t) / 60.0 - 6) / 24)
                        + 0.004 * {N} * (1 + 10 * x.s) + 0.5 * x.s
              FROM ({SEV}) x
            ON CONFLICT DO NOTHING
            """);

    // 설비 × 시각 목록과 그 시각의 이상 정도 s(0~1). s 가 0 이면 정상 값, 1 이면 가장 심한 이상 값
    private static final String SEVERITY = """
            SELECT eq.id, eq.rk, g.t,
                   CASE eq.kind
                     WHEN 'D' THEN eq.amp * POWER(LEAST(1.0, GREATEST(0.0,
                                      EXTRACT(EPOCH FROM (g.t - CAST(:onset AS timestamp))) / CAST(:rampSec AS double precision))), 1.6)
                     WHEN 'S' THEN eq.amp * CASE WHEN g.t >= CAST(:spike0 AS timestamp)
                                                  AND g.t < CAST(:spike0 AS timestamp) + make_interval(secs => CAST(:spikeLen AS double precision))
                                                 THEN sin(pi() * EXTRACT(EPOCH FROM (g.t - CAST(:spike0 AS timestamp)))
                                                          / CAST(:spikeLen AS double precision))
                                                 ELSE 0 END
                     ELSE 0
                   END AS s
              FROM eq
             CROSS JOIN LATERAL generate_series(CAST(:startAt AS timestamp), CAST(:endAt AS timestamp),
                                                make_interval(secs => CAST(:stepSec AS double precision))) AS g(t)
            """;

    @PersistenceContext
    private EntityManager em;

    private final DbClock dbClock;
    private final int defaultWeeks;
    private final int defaultNormalWeeks;
    private final int pumpWeeks;
    // false 면 예전처럼 실시간 값은 DANGER 버튼을 누른 설비만 이상. (직접 만든 인스턴스는 false, 스프링은 기본 true 를 주입)
    @Value("${puf.pm.sensor-stub.persist-abnormal:true}")
    private boolean persistAbnormal = false;

    public PmSensorBackfillServiceImpl(DbClock dbClock,
            @Value("${puf.pm.backfill.weeks:14}") int defaultWeeks,
            @Value("${puf.pm.backfill.normal-weeks:4}") int defaultNormalWeeks,
            @Value("${puf.pm.backfill.pump-weeks:0}") int pumpWeeks) {
        this.dbClock = dbClock;
        this.defaultWeeks = defaultWeeks;
        this.defaultNormalWeeks = defaultNormalWeeks;
        this.pumpWeeks = pumpWeeks;
    }

    @Override
    @Transactional
    public PmSensorBackfillResponse backfill(Integer weeksArg, Integer normalWeeksArg, LocalDateTime endAtArg, boolean reset,
            List<Long> equipIds) {
        int weeks = weeksArg == null ? defaultWeeks : weeksArg;
        int normalWeeks = normalWeeksArg == null ? (weeksArg == null ? weeks : Math.min(defaultNormalWeeks,weeks)) : normalWeeksArg;
        if (weeks < 1 || weeks > 52) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "weeks는 1~52 사이여야 합니다.");
        }
        if (normalWeeks < 0 || normalWeeks > weeks) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "normalWeeks는 0 이상 weeks 이하여야 합니다.");
        }
        // 센서 스텁 및 다른 백필과 같은 트랜잭션 잠금으로 중복 대량 생성을 막는다.
        em.createNativeQuery("SELECT TRUE FROM pg_advisory_xact_lock(731905210)").getSingleResult();
        LocalDateTime endAt = (endAtArg == null ? dbClock.now() : endAtArg).truncatedTo(ChronoUnit.SECONDS);
        if (endAt.isAfter(dbClock.now())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "endAt은 지금보다 이후일 수 없습니다.");
        }

        List<PmSensorBackfillResponse.TypeResult> results = new ArrayList<>();
        int skipped = 0;
        for (Spec spec : List.of(PUMP, CCTV, DISPLAY, WATER)) {
            long started = System.currentTimeMillis();
            // 과거 버전에서 메타데이터만 빠졌어도 실제 센서가 있으면 다시 생성하지 않는다.
            em.createNativeQuery("UPDATE pm_equipment e SET data_started_at = (SELECT MIN(s.recorded_at) FROM "
                    + spec.table() + " s WHERE s.equip_id = e.id)"
                    + " WHERE e.data_started_at IS NULL AND e.is_deleted = FALSE AND CAST(e.type AS text) = :type"
                    + " AND EXISTS (SELECT 1 FROM " + spec.table() + " s WHERE s.equip_id = e.id)")
                    .setParameter("type", spec.type().name()).executeUpdate();
            int typeWeeks = spec == PUMP && pumpWeeks > 0 ? Math.min(weeks, pumpWeeks) : weeks;
            int typeNormal = Math.min(normalWeeks, typeWeeks);

            List<Long> targets = targets(spec, reset, equipIds);
            skipped += skippedCount(spec, reset, equipIds, targets);
            if (targets.isEmpty()) {
                results.add(new PmSensorBackfillResponse.TypeResult(spec.type(), 0, 0, null, 0));
                continue;
            }
            if (reset) {
                em.createNativeQuery("DELETE FROM " + spec.table() + " WHERE equip_id IN (:ids)").setParameter("ids", targets).executeUpdate();
                em.createNativeQuery("UPDATE pm_equipment SET data_started_at = NULL WHERE id IN (:ids)").setParameter("ids", targets).executeUpdate();
            }

            int step = intervalOf(spec);
            LocalDateTime end = alignDown(endAt, step);
            LocalDateTime start = alignDown(end.minusWeeks(typeWeeks), step);
            LocalDateTime normalEnd = start.plusWeeks(typeNormal);
            LocalDateTime onset = end.minusSeconds(spec.rampSec()).isBefore(normalEnd) ? normalEnd : end.minusSeconds(spec.rampSec());
            long rampSec = Math.max(1, Duration.between(onset, end).getSeconds());
            // 튐은 끝 3일 전에 (지난 7일 점수 그래프 안에서 보이게). 정상 구간 안으로 들어가면 정상 구간 끝으로 미룬다
            LocalDateTime spike0 = end.minusDays(3).isBefore(normalEnd) ? normalEnd : end.minusDays(3);

            String sql = spec.insertSql().replace("{SEV}", "WITH ranked AS (" + RANKED + "), eq AS (SELECT * FROM ranked WHERE id IN (:ids)) " + SEVERITY)
                    .replace("{N}", NOISE);
            long rows = em.createNativeQuery(sql)
                    .setParameter("type", spec.type().name())
                    .setParameter("ids", targets)
                    .setParameter("startAt", start).setParameter("endAt", end).setParameter("stepSec", step)
                    .setParameter("onset", onset).setParameter("rampSec", rampSec)
                    .setParameter("spike0", spike0).setParameter("spikeLen", spec.spikeLenSec())
                    .setParameter("ampD", spec.ampDanger()).setParameter("ampC", spec.ampCaution()).setParameter("ampS", spec.ampSpike())
                    .executeUpdate();

            em.createNativeQuery("UPDATE pm_equipment e SET data_started_at = (SELECT MIN(s.recorded_at) FROM " + spec.table()
                    + " s WHERE s.equip_id = e.id) WHERE e.id IN (:ids)")
                    .setParameter("ids", targets).executeUpdate();
            results.add(new PmSensorBackfillResponse.TypeResult(spec.type(), targets.size(), rows, start,
                    System.currentTimeMillis() - started));
        }
        return new PmSensorBackfillResponse(endAt, weeks, normalWeeks, reset, skipped, results);
    }

    // 종류별 번호(rk)는 지워지지 않은 같은 종류 전체에서 id 순으로 매긴다 (대상을 골라도 시나리오가 바뀌지 않게)
    /**
     * 실시간 시연 센서. 과거 백필과 같은 값 범위·설비별 시나리오를 재사용한다.
     * DB 시계, 장비의 sensor_interval_sec, 조회 API와 같은 2000년 버킷 기준을 사용한다.
     * 중단 중 생긴 공백은 최근 maxSamples개만 채운다 (장기 이력은 기존 backfill API 담당).
     */
    @Override
    @Transactional
    public int appendRecent(int maxSamples) {
        if (maxSamples < 1 || maxSamples > 360) {
            throw new IllegalArgumentException("maxSamples must be between 1 and 360");
        }
        // 여러 인스턴스가 동시에 떠도 생성 쿼리는 한 인스턴스만 실행한다.
        if (!Boolean.TRUE.equals(em.createNativeQuery("SELECT pg_try_advisory_xact_lock(731905210)").getSingleResult())) {
            return 0;
        }
        LocalDateTime now = dbClock.now();
        int total = 0;
        for (Spec spec : List.of(PUMP, CCTV, DISPLAY, WATER)) {
            String times = """
                    WITH ranked AS (
                        SELECT e.id, e.sensor_interval_sec AS step,
                               row_number() OVER (ORDER BY e.id) AS rk
                          FROM pm_equipment e
                          JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE
                         WHERE e.is_deleted = FALSE AND CAST(e.type AS text) = :type
                    ), eq AS (
                        SELECT r.*,
                               date_bin(make_interval(secs => r.step), CAST(:now AS timestamp),
                                        TIMESTAMP '2000-01-01 00:00:00') AS until_at,
                               (SELECT MAX(s.recorded_at) FROM {TABLE} s WHERE s.equip_id = r.id) AS last_at
                          FROM ranked r
                    )
                    SELECT e.id, e.rk, g.t,
                           CASE WHEN EXISTS (SELECT 1 FROM pm_stub_mode m WHERE m.equip_id=e.id
                               AND m.mode='DANGER' AND g.t>=m.changed_at)
                               THEN CAST(:ampD AS double precision)
                                WHEN EXISTS (SELECT 1 FROM pm_stub_mode m WHERE m.equip_id=e.id)
                               THEN 0.0
                                ELSE {BASE} END AS s
                      FROM eq e
                     CROSS JOIN LATERAL generate_series(
                         GREATEST(COALESCE(
                             date_bin(make_interval(secs => e.step), e.last_at, TIMESTAMP '2000-01-01')
                                 + make_interval(secs => e.step), e.until_at),
                             e.until_at - make_interval(secs => e.step * (:maxSamples - 1))),
                         e.until_at, make_interval(secs => e.step)) AS g(t)
                    """.replace("{TABLE}", spec.table());
            // 모든 이름 파라미터가 SQL 안에 한 번씩은 나오게 한다 (안 나오는 파라미터를 설정하면 하이버네이트가 예외를 낸다)
            String unused = " + 0.0 * (CAST(:ampC AS double precision) + CAST(:ampS AS double precision))";
            times = times.replace("{BASE}", "(" + (persistAbnormal ? liveBaseSeverity(spec) : "0.0") + unused + ")");
            String sql = spec.insertSql().replace("+ 1.0 * {N} AS a", "+ 1.0 * "+LIVE_NOISE+" AS a").replace("{SEV}", times).replace("{N}", liveNoise(spec.type()));
            int added = em.createNativeQuery(sql)
                    .setParameter("type", spec.type().name()).setParameter("now", now)
                    .setParameter("ampD", spec.ampDanger()).setParameter("ampC", spec.ampCaution())
                    .setParameter("ampS", spec.ampSpike())
                    .setParameter("maxSamples", maxSamples).executeUpdate();
            total += added;
            // 새 설비 또는 기존 메타데이터가 비어 있는 설비의 시작 시각을 기록한다.
            if (added > 0) {
                em.createNativeQuery("UPDATE pm_equipment e SET data_started_at = (SELECT MIN(s.recorded_at) FROM "
                        + spec.table() + " s WHERE s.equip_id = e.id)"
                        + " WHERE e.data_started_at IS NULL AND CAST(e.type AS text) = :type"
                        + " AND e.is_deleted = FALSE AND EXISTS (SELECT 1 FROM " + spec.table()
                        + " s WHERE s.equip_id = e.id)")
                        .setParameter("type", spec.type().name()).executeUpdate();
            }
        }
        return total;
    }

    /**
     * 실시간 값의 평소 이상 정도 (DANGER 버튼을 누른 적 없는 설비). 과거 백필의 설비별 시나리오와 이어지게 번호(rk)로 정한다.
     *   rk%4==1 계속 위험 / 2 계속 주의 / 0 가끔 한 번씩 튀었다 돌아옴 / 3 계속 정상.
     * 수위계는 천천히 오르는 값을 모델이 평소로 받아들이므로 "계속"이 아니라 8시간마다 2시간 동안 올랐다가 내려오는 식으로 한다.
     * 조치 완료(NORMAL 버튼)를 누른 설비는 pm_stub_mode 행이 생겨서 이 값 대신 0 이 된다.
     */
    private static String liveBaseSeverity(Spec spec) {
        String hourOfDay = "EXTRACT(EPOCH FROM g.t) / 3600.0";
        if (spec.type() == PmEquipmentType.WATER_LEVEL) {
            String ph = "MOD(CAST(" + hourOfDay + " + e.rk * 1.7 AS numeric), 8.0)";
            return "CASE MOD(e.rk, 4) WHEN 1 THEN CAST(:ampD AS double precision) WHEN 2 THEN CAST(:ampC AS double precision) ELSE 0.0 END"
                    + " * CASE WHEN MOD(e.rk, 4) IN (1, 2) AND " + ph + " < 2 THEN CAST(" + ph + " / 2.0 AS double precision) ELSE 0.0 END";
        }
        double spikeHours = spec.spikeLenSec() / 3600.0;
        String ph = "MOD(CAST(" + hourOfDay + " + e.rk * 5 AS numeric), 30.0)";
        return "CASE MOD(e.rk, 4) WHEN 1 THEN CAST(:ampD AS double precision) WHEN 2 THEN CAST(:ampC AS double precision)"
                + " WHEN 0 THEN CASE WHEN " + ph + " < " + spikeHours + " THEN CAST(:ampS AS double precision)"
                + " * sin(pi() * CAST(" + ph + " AS double precision) / " + spikeHours + ") ELSE 0.0 END"
                + " ELSE 0.0 END";
    }

    private static final String RANKED = """
            SELECT r.id, r.rk,
                   CASE MOD(r.rk, 4) WHEN 1 THEN 'D' WHEN 2 THEN 'D' WHEN 0 THEN 'S' ELSE 'N' END AS kind,
                   CASE MOD(r.rk, 4) WHEN 1 THEN CAST(:ampD AS double precision) WHEN 2 THEN CAST(:ampC AS double precision)
                                     WHEN 0 THEN CAST(:ampS AS double precision) ELSE 0.0 END AS amp
              FROM (SELECT e.id, row_number() OVER (ORDER BY e.id) AS rk
                      FROM pm_equipment e JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE
                     WHERE e.is_deleted = FALSE AND CAST(e.type AS text) = :type) r
            """;

    @SuppressWarnings("unchecked")
    private List<Long> targets(Spec spec, boolean reset, List<Long> equipIds) {
        String sql = "SELECT e.id FROM pm_equipment e JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE"
                + " WHERE e.is_deleted = FALSE AND CAST(e.type AS text) = :type"
                + (reset ? "" : " AND NOT EXISTS (SELECT 1 FROM " + spec.table() + " s WHERE s.equip_id = e.id)")
                + (equipIds == null || equipIds.isEmpty() ? "" : " AND e.id IN (:ids)") + " ORDER BY e.id";
        var q = em.createNativeQuery(sql).setParameter("type", spec.type().name());
        if (equipIds != null && !equipIds.isEmpty()) {
            q.setParameter("ids", equipIds);
        }
        List<Long> ids = new ArrayList<>();
        for (Object o : q.getResultList()) {
            ids.add(((Number) o).longValue());
        }
        return ids;
    }

    // reset 이 아닐 때 이미 센서 데이터가 있어서 건너뛴 설비 수
    private int skippedCount(Spec spec, boolean reset, List<Long> equipIds, List<Long> targets) {
        if (reset) {
            return 0;
        }
        String sql = "SELECT COUNT(*) FROM pm_equipment e JOIN underpass u ON u.id = e.underpass_id AND u.is_deleted = FALSE"
                + " WHERE e.is_deleted = FALSE AND CAST(e.type AS text) = :type"
                + (equipIds == null || equipIds.isEmpty() ? "" : " AND e.id IN (:ids)");
        var q = em.createNativeQuery(sql).setParameter("type", spec.type().name());
        if (equipIds != null && !equipIds.isEmpty()) {
            q.setParameter("ids", equipIds);
        }
        return ((Number) q.getSingleResult()).intValue() - targets.size();
    }

    /** 모드 전환 시 주기를 기다리지 않고 대상 한 대의 새 센서값을 추가한다. */
    @Transactional
    public int emitNow(long equipId, String type, boolean danger) {
        em.createNativeQuery("SELECT TRUE FROM pg_advisory_xact_lock(731905210)").getSingleResult();
        Spec spec = List.of(PUMP,CCTV,DISPLAY,WATER).stream().filter(v->v.type().name().equals(type))
                .findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported sensor type"));
        String times = "SELECT e.id, (SELECT COUNT(*) FROM pm_equipment p WHERE p.type=e.type AND NOT p.is_deleted AND p.id<=e.id) AS rk, "
                + "GREATEST(CAST(:now AS timestamp), COALESCE((SELECT MAX(recorded_at)+interval '1 microsecond' FROM "
                +spec.table()+" WHERE equip_id=e.id),CAST(:now AS timestamp))) AS t, CAST(:severity AS double precision) AS s "
                +"FROM pm_equipment e WHERE e.id=:equipId AND NOT e.is_deleted";
        return em.createNativeQuery(spec.insertSql().replace("+ 1.0 * {N} AS a", "+ 1.0 * "+LIVE_NOISE+" AS a").replace("{SEV}",times).replace("{N}",liveNoise(spec.type())))
                .setParameter("now",dbClock.now()).setParameter("severity",danger?spec.ampDanger():0.0)
                .setParameter("equipId",equipId).executeUpdate();
    }

    private static int intervalOf(Spec spec) {
        return switch (spec.type()) {
            case PUMP -> 10;
            case WATER_LEVEL -> 300;
            default -> 3600;
        };
    }

    // 센서 주기(초)의 배수 시각으로 내림 (버킷 기준 2000-01-01 과 같은 규칙: 3600 이면 정각)
    private static LocalDateTime alignDown(LocalDateTime t, int stepSec) {
        long epoch = t.toEpochSecond(java.time.ZoneOffset.UTC);
        return LocalDateTime.ofEpochSecond(epoch - Math.floorMod(epoch, (long) stepSec), 0, java.time.ZoneOffset.UTC);
    }
}
