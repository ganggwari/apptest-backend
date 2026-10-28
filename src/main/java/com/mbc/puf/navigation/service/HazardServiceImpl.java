package com.mbc.puf.navigation.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.navigation.dto.HazardState;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.underpass.entity.Underpass;

import jakarta.persistence.EntityManager;

/**
 * 내비가 피해야 할 "위험지점" = 위험 단계가 관심 이상이거나 차단 중인 지하차도 (DB에서 읽는다).
 *  - 차단(경계 이상 또는 차단 상태): 필수 우회
 *  - 관심·주의: 우회 권유
 * 판정 모양:
 *  - 기본은 지하차도 중심점 + 반경. 중심점은 도로 위가 아닐 수 있어 반경을 넉넉히(기본 70m) 둔다.
 *  - 입·출구 카메라 좌표를 실제로 측량해 둔 지하차도(puf.navigation.surveyed-underpass-ids)만
 *    상행/하행 입구→출구 선분으로 판정하고 반경을 좁게(기본 40m) 쓴다. 더미 좌표는 선분에 쓰지 않는다.
 * 상태가 바뀌면(2초마다 확인) version 을 올리고 WebSocket / SSE 로 알린다.
 */
@Service
public class HazardServiceImpl implements HazardService {

    private static final Logger log = LoggerFactory.getLogger(HazardServiceImpl.class);

    private final EntityManager em;
    private final StreamPublisher streamPublisher;
    private final double defaultRadius;
    private final double surveyedRadius;
    private final List<Long> surveyedIds;
    private final Map<Long, Double> radiusOverrides;

    private volatile List<Hazard> hazards = List.of();
    private volatile String snapshotKey = null;
    private volatile long version = System.currentTimeMillis();
    private volatile boolean lastCheckFailed = false;   // 같은 오류를 30초마다 반복해서 찍지 않으려고

    public HazardServiceImpl(EntityManager em, StreamPublisher streamPublisher,
                         @Value("${puf.navigation.default-radius-m:70}") double defaultRadius,
                         @Value("${puf.navigation.surveyed-radius-m:40}") double surveyedRadius,
                         @Value("${puf.navigation.surveyed-underpass-ids:}") List<Long> surveyedIds,
                         @Value("${puf.navigation.radius-overrides:}") String radiusOverrides) {
        this.em = em;
        this.streamPublisher = streamPublisher;
        this.defaultRadius = defaultRadius;
        this.surveyedRadius = surveyedRadius;
        this.surveyedIds = surveyedIds == null ? List.of() : surveyedIds;
        this.radiusOverrides = parseOverrides(radiusOverrides);
    }

    /** "3:120,5:90" -> {3=120, 5=90} (지하차도 id:반경 m) */
    private static Map<Long, Double> parseOverrides(String text) {
        Map<Long, Double> map = new HashMap<>();
        if (text == null || text.isBlank()) {
            return map;
        }
        for (String part : text.split(",")) {
            String[] p = part.trim().split(":");
            if (p.length == 2) {
                map.put(Long.parseLong(p[0].trim()), Double.parseDouble(p[1].trim()));
            }
        }
        return map;
    }

    /** 지금 피해야 할 위험지점 전체 (관심 이상) */
    @Override
    public List<Hazard> activeHazards() {
        return hazards;
    }

    @Override
    public long version() {
        return version;
    }

    @Override
    public HazardState state() {
        List<Hazard> list = hazards;
        List<HazardState.HazardView> views = list.stream().map(this::toView).toList();
        return new HazardState(version, !views.isEmpty(), views);
    }

    private HazardState.HazardView toView(Hazard h) {
        String message = h.blocked() ? "차단 중인 지하차도입니다." : "침수 " + h.level().getDbValue() + " 단계입니다. 우회를 권합니다.";
        return new HazardState.HazardView(h.id(), h.name(), h.lat(), h.lng(), h.level().name(), h.level().getDbValue(),
                h.blocked(), h.radiusM(), h.lengthM() > 0 ? h.lengthM() : null, message);
    }

    /**
     * 30초마다 DB 를 보고 위험 상태가 달라졌으면 알린다. 이것은 안전 점검용이다.
     * 단계 변화·차단/해제·지하차도 수정/삭제는 커밋 직후 HazardRefreshListener 가 바로 갱신한다.
     */
    @Scheduled(initialDelay = 3_000, fixedDelay = 30_000)
    @Override
    public void refreshIfChanged() {
        try {
            refresh();
            if (lastCheckFailed) {
                lastCheckFailed = false;
                log.info("[hazard] 위험지점 점검이 다시 정상으로 돌아왔다");
            }
        } catch (RuntimeException e) {
            // 일시적인 DB 오류는 다음 주기에 다시 시도한다. 처음 실패했을 때만 로그를 남긴다
            if (!lastCheckFailed) {
                lastCheckFailed = true;
                log.warn("[hazard] 위험지점 점검 실패 (다음 주기에 다시 시도): {}", e.toString());
            }
        }
    }

    @Transactional(readOnly = true)
    @Override
    public synchronized void refresh() {
        List<Hazard> loaded = load();
        String key = loaded.stream()
                .map(h -> h.id() + ":" + h.level() + ":" + h.blocked() + ":" + h.lat() + "," + h.lng() + ":" + h.lengthM()
                        + ":" + (h.segments() == null ? "-" : h.segments().size()))
                .collect(Collectors.joining("|"));
        if (key.equals(snapshotKey)) {
            return;
        }
        boolean first = snapshotKey == null;
        snapshotKey = key;
        hazards = loaded;
        version = Math.max(version + 1, System.currentTimeMillis());
        if (!first || !loaded.isEmpty()) {
            streamPublisher.dataChangedPublic(null, "navigation-hazard", "UPDATE", null);
        }
    }

    private List<Hazard> load() {
        List<Underpass> list = em.createQuery(
                        "select u from Underpass u where u.deleted = false and (u.currentStatus = :blocked or u.currentRiskLevel <> :safe)"
                                + " order by u.id", Underpass.class)
                .setParameter("blocked", UnderpassCurrentStatus.BLOCKED)
                .setParameter("safe", RiskLevel.SAFE)
                .getResultList();
        List<Hazard> result = new ArrayList<>();
        for (Underpass u : list) {
            if (u.getLatitude() == null || u.getLongitude() == null) {
                continue;
            }
            boolean blocked = u.getCurrentStatus() == UnderpassCurrentStatus.BLOCKED || u.getCurrentRiskLevel().atLeast(RiskLevel.ALERT);
            List<double[][]> segments = surveyedIds.contains(u.getId()) ? segmentsOf(u) : null;
            double lat = u.getLatitude().doubleValue();
            double lng = u.getLongitude().doubleValue();
            if (segments != null) {
                // 측량한 곳은 입·출구 카메라 좌표의 평균을 중심으로 본다
                double sLat = 0, sLng = 0;
                for (double[][] s : segments) {
                    sLat += s[0][0] + s[1][0];
                    sLng += s[0][1] + s[1][1];
                }
                lat = sLat / (segments.size() * 2);
                lng = sLng / (segments.size() * 2);
            }
            double radius = radiusOverrides.getOrDefault(u.getId(), segments != null ? surveyedRadius : defaultRadius);
            double length = u.getLengthM() == null ? 0 : u.getLengthM().doubleValue();
            RiskLevel level = u.getCurrentRiskLevel() == RiskLevel.SAFE ? RiskLevel.ALERT : u.getCurrentRiskLevel();   // 위험 단계가 낮아도 차단 중이면 경계로 취급
            result.add(new Hazard(u.getId(), u.getName(), lat, lng, length, level, blocked, radius, segments));
        }
        return result;
    }

    // 상행·하행의 입구→출구 선분. 카메라 좌표가 없는 방향은 건너뛴다 (둘 다 없으면 null = 중심점 방식)
    private List<double[][]> segmentsOf(Underpass u) {
        List<double[][]> segs = new ArrayList<>();
        addSegment(segs, u.getUpEntryCameraId(), u.getUpExitCameraId());
        addSegment(segs, u.getDownEntryCameraId(), u.getDownExitCameraId());
        return segs.isEmpty() ? null : segs;
    }

    private void addSegment(List<double[][]> segs, Long entryCamId, Long exitCamId) {
        if (entryCamId == null || exitCamId == null) {
            return;
        }
        Camera in = em.find(Camera.class, entryCamId);
        Camera out = em.find(Camera.class, exitCamId);
        if (in == null || out == null || in.getLatitude() == null || in.getLongitude() == null
                || out.getLatitude() == null || out.getLongitude() == null) {
            return;
        }
        segs.add(new double[][]{
                {in.getLatitude().doubleValue(), in.getLongitude().doubleValue()},
                {out.getLatitude().doubleValue(), out.getLongitude().doubleValue()}});
    }

}
