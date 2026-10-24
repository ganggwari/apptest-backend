package com.mbc.puf.pm.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.pm.dto.PmSensorSeriesResponse;
import com.mbc.puf.pm.dto.PmSensorSeriesResponse.Bucket;
import com.mbc.puf.pm.dto.PmSensorSeriesResponse.Series;
import com.mbc.puf.pm.dto.PmSensorSeriesResponse.Stat;
import com.mbc.puf.pm.repository.PmEquipmentRepository;
import com.mbc.puf.pm.repository.PmEquipmentRepository.EquipmentRow;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 예지보전 센서 조회. 장비 종류마다 센서 테이블이 달라서(pm_sensor_cctv / _pump / _display / _water_level),
 * (종류, 버킷 크기)가 같은 장비끼리 묶어 테이블마다 한 번씩 집계 쿼리를 날린다.
 * 버킷 시작 시각 = 2000-01-01 00:00:00 기준으로 bucketSec 단위로 나눈 구간의 시작 (3600 = 정각, 86400 = 자정).
 */
@Service
@Transactional(readOnly = true)
public class PmSensorServiceImpl implements PmSensorService {

    private static final int MAX_EQUIPMENTS = 50;
    private static final int DEFAULT_LIMIT = 20000;
    private static final int MIN_LIMIT = 100;      // 한 시각에 장비가 최대 50대라서, 100 이상이면 마지막 시각 하나를 빼도 결과가 비지 않는다
    private static final int MAX_LIMIT = 50000;

    // 센서 테이블 이름과 컬럼 (SQL 에 그대로 붙이므로 여기에 적은 상수만 쓴다)
    private static final Map<PmEquipmentType, String> TABLES = new EnumMap<>(PmEquipmentType.class);
    private static final Map<PmEquipmentType, List<String>> COLUMNS = new EnumMap<>(PmEquipmentType.class);

    static {
        TABLES.put(PmEquipmentType.CCTV, "pm_sensor_cctv");
        TABLES.put(PmEquipmentType.PUMP, "pm_sensor_pump");
        TABLES.put(PmEquipmentType.DISPLAY, "pm_sensor_display");
        TABLES.put(PmEquipmentType.WATER_LEVEL, "pm_sensor_water_level");
        COLUMNS.put(PmEquipmentType.CCTV, List.of("video_receive_rate", "response_time_ms", "packet_loss_rate", "image_blur_score"));
        COLUMNS.put(PmEquipmentType.PUMP, List.of("vibration", "pressure", "temperature", "current", "flow", "rpm"));
        COLUMNS.put(PmEquipmentType.DISPLAY,
                List.of("temperature_c", "ambient_c", "ripple_v", "response_time_ms", "error_count", "bad_pixel_pct"));
        COLUMNS.put(PmEquipmentType.WATER_LEVEL, List.of("level_m"));
    }

    /** 센서 테이블 이름 (조회용 화이트리스트) */
    static String tableOf(PmEquipmentType type) {
        return TABLES.get(type);
    }

    /** 센서 컬럼 이름 목록 (조회용 화이트리스트) */
    static List<String> columnsOf(PmEquipmentType type) {
        return COLUMNS.get(type);
    }

    @PersistenceContext
    private EntityManager em;

    private final PmEquipmentRepository equipmentRepository;
    private final DbClock dbClock;

    public PmSensorServiceImpl(PmEquipmentRepository equipmentRepository, DbClock dbClock) {
        this.equipmentRepository = equipmentRepository;
        this.dbClock = dbClock;
    }

    /** (종류, 버킷 크기)가 같은 장비 묶음 */
    private record GroupKey(PmEquipmentType type, int bucketSec) {
    }

    /** 쿼리 결과 1행 = 장비 1대의 버킷 1개 */
    private record Row(Long equipId, Bucket bucket) {
    }

    @Override
    public PmSensorSeriesResponse getSensorSeries(List<Long> equipIds, LocalDateTime from, LocalDateTime to,
            Integer bucketSec, LocalDateTime after, Integer limit) {
        if (equipIds == null || equipIds.isEmpty()) {
            throw badRequest("equipIds는 필수입니다.");
        }
        List<Long> ids = equipIds.stream().distinct().toList();
        if (ids.size() > MAX_EQUIPMENTS) {
            throw badRequest("equipIds는 한 번에 " + MAX_EQUIPMENTS + "개까지 조회할 수 있습니다.");
        }
        if (bucketSec != null && bucketSec <= 0) {
            throw badRequest("bucketSec는 1 이상이어야 합니다.");
        }
        int max = limit == null ? DEFAULT_LIMIT : limit;
        if (max < MIN_LIMIT || max > MAX_LIMIT) {
            throw badRequest("limit는 " + MIN_LIMIT + " 이상 " + MAX_LIMIT + " 이하여야 합니다.");
        }
        LocalDateTime end = to == null ? dbClock.now() : to;

        // 삭제되지 않은 장비만 (없거나 삭제된 장비는 unknownEquipIds 로 알려 준다)
        Map<Long, EquipmentRow> found = equipmentRepository.findRowsByIds(ids).stream()
                .collect(Collectors.toMap(EquipmentRow::getEquipId, r -> r));
        List<Long> unknown = ids.stream().filter(id -> !found.containsKey(id)).sorted().toList();

        List<EquipmentRow> equipments = found.values().stream().sorted(Comparator.comparing(EquipmentRow::getEquipId)).toList();
        Map<GroupKey, List<Long>> groups = new LinkedHashMap<>();
        Map<Long, Integer> bucketOf = new LinkedHashMap<>();
        for (EquipmentRow r : equipments) {
            int interval = r.getSensorIntervalSec();
            int bucket = bucketSec == null ? interval : bucketSec;
            if (bucket < interval) {
                throw badRequest("bucketSec(" + bucket + ")는 장비 " + r.getName() + "의 센서 주기(" + interval + "초)보다 작을 수 없습니다.");
            }
            bucketOf.put(r.getEquipId(), bucket);
            groups.computeIfAbsent(new GroupKey(PmEquipmentType.valueOf(r.getType()), bucket), k -> new ArrayList<>())
                    .add(r.getEquipId());
        }

        // 묶음마다 앞에서 max+1 개까지 가져온 뒤 시각 순으로 합친다 (합친 결과의 앞 max 개는 각 묶음의 앞 max+1 개 안에 있다)
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<GroupKey, List<Long>> g : groups.entrySet()) {
            rows.addAll(queryGroup(g.getKey(), g.getValue(), from, end, after, max + 1));
        }
        rows.sort(Comparator.comparing((Row r) -> r.bucket().t()).thenComparing(Row::equipId));

        boolean truncated = false;
        LocalDateTime nextAfter = null;
        if (rows.size() > max) {
            // limit 를 넘으면, 모든 장비가 같은 시각까지 온전히 오도록 마지막으로 걸친 시각 하나가 덜 찼으면 그 시각은 뺀다
            truncated = true;
            LocalDateTime cut = rows.get(max - 1).bucket().t();
            int keep = max;
            if (rows.get(max).bucket().t().equals(cut)) {
                keep = 0;
                while (!rows.get(keep).bucket().t().equals(cut)) {
                    keep++;
                }
            }
            nextAfter = keep == max ? cut : rows.get(keep - 1).bucket().t();
            rows = rows.subList(0, keep);
        }

        Map<Long, List<Bucket>> byEquip = new LinkedHashMap<>();
        for (Row r : rows) {
            byEquip.computeIfAbsent(r.equipId(), k -> new ArrayList<>()).add(r.bucket());
        }
        List<Series> series = equipments.stream()
                .map(r -> new Series(r.getEquipId(), r.getName(), r.getType(), bucketOf.get(r.getEquipId()),
                        byEquip.getOrDefault(r.getEquipId(), List.of())))
                .toList();
        return new PmSensorSeriesResponse(truncated, nextAfter, unknown, series);
    }

    /** 같은 종류·같은 버킷 크기인 장비들의 집계 (센서 테이블 1개에 쿼리 1번) */
    private List<Row> queryGroup(GroupKey key, List<Long> equipIds, LocalDateTime from, LocalDateTime to,
            LocalDateTime after, int rowLimit) {
        String table = TABLES.get(key.type());
        List<String> cols = COLUMNS.get(key.type());
        // bucketSec 는 검증된 정수라서 SQL 에 그대로 붙인다
        String bin = "date_bin(make_interval(secs => " + key.bucketSec() + "), s.recorded_at, TIMESTAMP '2000-01-01 00:00:00')";

        StringBuilder sql = new StringBuilder("SELECT s.equip_id, ").append(bin).append(" AS t, COUNT(*) AS n");
        for (String c : cols) {
            sql.append(", COUNT(s.").append(c).append(')')
               .append(", SUM(s.").append(c).append(')')
               .append(", SUM(CAST(s.").append(c).append(" AS double precision) * CAST(s.").append(c).append(" AS double precision))")
               .append(", MIN(s.").append(c).append(')')
               .append(", MAX(s.").append(c).append(')');
        }
        sql.append(" FROM ").append(table).append(" s WHERE s.equip_id IN (:ids) AND s.recorded_at < :to");
        if (from != null) {
            sql.append(" AND s.recorded_at >= :from");
        }
        if (after != null) {
            sql.append(" AND s.recorded_at >= :after");
        }
        sql.append(" GROUP BY s.equip_id, 2");
        if (after != null) {
            sql.append(" HAVING ").append(bin).append(" > :after");
        }
        sql.append(" ORDER BY 2, 1");

        jakarta.persistence.Query q = em.createNativeQuery(sql.toString())
                .setParameter("ids", equipIds)
                .setParameter("to", to)
                .setMaxResults(rowLimit);
        if (from != null) {
            q.setParameter("from", from);
        }
        if (after != null) {
            q.setParameter("after", after);
        }

        @SuppressWarnings("unchecked")
        List<Object[]> result = q.getResultList();
        List<Row> rows = new ArrayList<>(result.size());
        for (Object[] r : result) {
            Map<String, Stat> stats = new LinkedHashMap<>();
            int i = 3;
            for (String c : cols) {
                long count = ((Number) r[i]).longValue();
                stats.put(c, count == 0 ? new Stat(0, null, null, null, null)
                        : new Stat(count, num(r[i + 1]), num(r[i + 2]), num(r[i + 3]), num(r[i + 4])));
                i += 5;
            }
            rows.add(new Row(((Number) r[0]).longValue(), new Bucket(toLocalDateTime(r[1]), ((Number) r[2]).longValue(), stats)));
        }
        return rows;
    }

    private static Double num(Object o) {
        return o == null ? null : ((Number) o).doubleValue();
    }

    private static LocalDateTime toLocalDateTime(Object o) {
        if (o instanceof LocalDateTime l) {
            return l;
        }
        if (o instanceof Timestamp ts) {
            return ts.toLocalDateTime();
        }
        throw new IllegalStateException("지원하지 않는 시각 타입: " + (o == null ? "null" : o.getClass()));
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }
}
