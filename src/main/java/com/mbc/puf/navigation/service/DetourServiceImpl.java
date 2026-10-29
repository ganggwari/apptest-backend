package com.mbc.puf.navigation.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.navigation.service.HazardService.Hazard;
import com.mbc.puf.navigation.client.NaviException;
import com.mbc.puf.navigation.client.NaviRouteProvider;
import com.mbc.puf.navigation.dto.NaviRoute;
import com.mbc.puf.navigation.dto.RouteResult;
import com.mbc.puf.navigation.geo.GeoUtil;

/**
 * "위험지점을 피하는 경로"를 만드는 곳.
 *
 * 카카오 길찾기에는 "이 지점은 피해서" 옵션이 없다. 그래서 이렇게 한다.
 *  1) 일단 평소대로 길을 찾는다.
 *  2) 그 경로가 위험지점 근처를 지나가면
 *     - 차단(경계 이상) 지하차도: 피하는 경로를 찾아 그 경로를 돌려준다 (필수 우회)
 *     - 관심·주의 지하차도만 있으면: 원래 경로를 돌려주고, 피하는 경로는 "제안"으로 함께 준다 (우회 권유)
 *  3) 피하는 경로는 지하차도 "입구 앞쪽"에 경유지 후보를 만들어 하나씩 넣어 다시 물어본다.
 *     지하차도가 2km 처럼 길어도 입구 위치를 경로선에서 (중심점 ± 길이/2) 로 구하므로 입구 앞에서 빠져나간다.
 *  4) 돌아온 경로가 반드시 피해야 할 지하차도에서 충분히 떨어져 있으면 합격. 합격한 것 중 가장 빠른 걸 쓴다.
 */
@Service
public class DetourServiceImpl implements DetourService {

    private static final Logger log = LoggerFactory.getLogger(DetourServiceImpl.class);

    private static final int SOFT_PENALTY_SEC = 600;   // 권유 단계 지하차도를 아직 지나는 후보에 주는 벌점(초)

    private final NaviRouteProvider provider;
    private final HazardService hazardService;
    private final int maxTries;
    private final List<double[]> fallbackWaypoints;
    private final int underpassGuideType;
    private final double underpassGuideRadius;

    /** 같은 위험 상태 + 같은 도착지면 다음 폰은 이미 찾은 경유지를 재사용 (카카오 호출 절약) */
    private final Map<String, double[]> waypointCache = new ConcurrentHashMap<>();

    public DetourServiceImpl(NaviRouteProvider provider, HazardService hazardService,
                         @Value("${puf.navigation.max-detour-tries:10}") int maxTries,
                         @Value("${puf.navigation.fallback-waypoints:}") String fallback,
                         @Value("${puf.navigation.underpass-guide-type:15}") int underpassGuideType,
                         @Value("${puf.navigation.underpass-guide-radius-m:150}") double underpassGuideRadius) {
        this.provider = provider;
        this.hazardService = hazardService;
        this.maxTries = maxTries;
        this.fallbackWaypoints = parseFallback(fallback);
        this.underpassGuideType = underpassGuideType;
        this.underpassGuideRadius = underpassGuideRadius;
    }

    @Override
    public RouteResult find(double oLat, double oLng, double dLat, double dLng, Double heading) {
        List<Hazard> active = hazardService.activeHazards();
        long version = hazardService.version();

        NaviRoute base = baseRoute(oLat, oLng, dLat, dLng, heading);

        List<Hazard> near = active.stream().filter(h -> isNear(base, h)).toList();
        List<Hazard> blockedNear = near.stream().filter(Hazard::blocked).toList();
        List<Hazard> advisoryNear = near.stream().filter(h -> !h.blocked()).toList();
        List<String> passes = names(near);
        List<String> blockedNames = names(blockedNear);
        List<String> advisoryNames = names(advisoryNear);

        if (near.isEmpty()) {
            String msg = active.isEmpty() ? "정상 경로입니다." : "현재 경로는 위험 지점과 겹치지 않습니다.";
            return result(base, false, false, null, passes, blockedNames, advisoryNames, msg, null);
        }

        // ---- 차단된 지하차도가 경로에 있다: 필수 우회 ----
        if (!blockedNear.isEmpty()) {
            List<Hazard> required = active.stream().filter(Hazard::blocked).toList();
            List<Hazard> soft = active.stream().filter(h -> !h.blocked()).toList();
            Found best = search(base, required, soft, blockedNear, active, oLat, oLng, dLat, dLng, heading, version, "M");
            if (best == null) {
                return result(base, false, true, null, passes, blockedNames, advisoryNames,
                        "통제 중인 지하차도를 피하는 경로를 찾지 못했습니다.", null);
            }
            int extraSec = Math.max(0, best.route().duration() - base.duration());
            String msg = "통제 중인 지하차도 " + blockedNames.size() + "곳을 피해 우회합니다. (약 +" + Math.round(extraSec / 60.0) + "분)";
            return result(best.route(), true, true, best.waypoint(), passes, blockedNames, advisoryNames, msg, null);
        }

        // ---- 관심·주의 지하차도만 있다: 원래 경로 + 우회 제안 ----
        List<Hazard> required = new ArrayList<>(active.stream().filter(Hazard::blocked).toList());
        required.addAll(advisoryNear);
        List<Hazard> soft = active.stream().filter(h -> !h.blocked() && !advisoryNear.contains(h)).toList();
        Found best = search(base, required, soft, advisoryNear, active, oLat, oLng, dLat, dLng, heading, version, "A");
        RouteResult.SuggestedDetour suggested = null;
        if (best != null) {
            int extraSec = Math.max(0, best.route().duration() - base.duration());
            suggested = new RouteResult.SuggestedDetour(best.route().distance(), best.route().duration(), extraSec,
                    best.route().path(), best.route().guides(), best.waypoint(),
                    "우회하면 약 +" + Math.round(extraSec / 60.0) + "분 걸립니다.");
        }
        String msg = "경로에 침수 위험 단계(" + String.join(", ", advisoryNames) + ")의 지하차도가 있습니다."
                + (suggested != null ? " 우회를 권합니다." : "");
        return result(base, false, false, null, passes, blockedNames, advisoryNames, msg, suggested);
    }

    // ---------------------------------------------------------------- 내부 도우미

    private record Found(NaviRoute route, double[] waypoint, int softHits) {
    }

    private NaviRoute baseRoute(double oLat, double oLng, double dLat, double dLng, Double heading) {
        try {
            return provider.route(oLat, oLng, dLat, dLng, List.of(), heading);
        } catch (NaviException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (RestClientResponseException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "길찾기 서버가 오류를 돌려줬습니다. (" + e.getStatusCode().value() + ")");
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "길찾기 서버에 연결하지 못했습니다.");
        }
    }

    /** 피해야 할(required) 지하차도를 모두 벗어난 경로를 찾는다. 못 찾으면 null */
    private Found search(NaviRoute base, List<Hazard> required, List<Hazard> soft, List<Hazard> anchors,
                         List<Hazard> active, double oLat, double oLng, double dLat, double dLng, Double heading,
                         long version, String mode) {
        // 1) 이전에 찾아둔 경유지가 있으면 먼저 시도 (호출 1번)
        String cacheKey = version + "|" + mode + "|" + round5(dLat) + "," + round5(dLng);
        double[] cached = waypointCache.get(cacheKey);
        if (cached != null) {
            Found f = tryWaypoint(cached, oLat, oLng, dLat, dLng, heading, required, soft);
            if (f != null) {
                return f;
            }
        }

        // 2) 후보 경유지를 하나씩 시도
        List<Found> clear = new ArrayList<>();
        int tries = 0;
        for (double[] wp : candidates(base, anchors, active, oLat, oLng, dLat, dLng)) {
            if (tries >= maxTries) {
                break;
            }
            tries++;
            Found f = tryWaypoint(wp, oLat, oLng, dLat, dLng, heading, required, soft);
            if (f != null) {
                clear.add(f);
            }
            if (clear.size() >= 2) {
                break;   // 합격 경로 2개면 충분 - 카카오 호출 아끼기
            }
        }

        // 3) 그래도 없으면 수동 경유지(설정값) 시도
        if (clear.isEmpty()) {
            for (double[] wp : fallbackWaypoints) {
                Found f = tryWaypoint(wp, oLat, oLng, dLat, dLng, heading, required, soft);
                if (f != null) {
                    clear.add(f);
                }
            }
        }
        if (clear.isEmpty()) {
            log.warn("우회 경로 탐색 실패: 위험지점 {}, 시도 {}회", names(required), tries);
            return null;
        }

        Found best = clear.stream()
                .min(Comparator.comparingInt(f -> f.route().duration() + f.softHits() * SOFT_PENALTY_SEC)).get();
        if (waypointCache.size() > 200) {
            waypointCache.clear();
        }
        waypointCache.put(cacheKey, best.waypoint());
        return best;
    }

    private Found tryWaypoint(double[] wp, double oLat, double oLng, double dLat, double dLng, Double heading,
                              List<Hazard> required, List<Hazard> soft) {
        try {
            NaviRoute r = provider.route(oLat, oLng, dLat, dLng, List.of(wp), heading);
            if (required.stream().anyMatch(h -> isNear(r, h))) {
                return null;
            }
            int softHits = (int) soft.stream().filter(h -> isNear(r, h)).count();
            return new Found(r, wp, softHits);
        } catch (NaviException | RestClientException e) {
            // 경유지가 도로에서 너무 먼 곳이면 카카오가 거절할 수 있다. 다음 후보로 넘어간다.
            log.debug("경유지 {} 실패: {}", wp, e.getMessage());
            return null;
        }
    }

    /**
     * 경유지 후보 만들기 (각각 출발->경유지->도착 직선 길이가 짧은 순).
     *  1순위: 지하차도 "입구 앞쪽" 경로 위 지점에서 진행방향 좌/우 직각으로 빠지는 점
     *         (입구 = 경로선 위 중심점에서 길이/2 + 100/400/800m 앞, 옆으로 150/350/600m)
     *  2순위: 중심점에서 좌/우 300/600/900m (짧은 지하차도용)
     *  3순위: 중심점 둘레 450m 8방향
     * 지하차도 구간(입구~출구)이나 위험지점 중심에 너무 가까운 후보와 서로 겹치는 후보는 뺀다.
     */
    private List<double[]> candidates(NaviRoute base, List<Hazard> anchors, List<Hazard> active,
                                      double oLat, double oLng, double dLat, double dLng) {
        List<double[]> entryLateral = new ArrayList<>();
        List<double[]> centerLateral = new ArrayList<>();
        List<double[]> ring = new ArrayList<>();
        List<List<double[]>> zones = new ArrayList<>();
        List<double[]> path = base.path();

        for (Hazard h : anchors) {
            double half = Math.max(100, h.lengthM() / 2);
            GeoUtil.OnPath entry = GeoUtil.pointAlong(h.lat(), h.lng(), path, -half);
            GeoUtil.OnPath exit = GeoUtil.pointAlong(h.lat(), h.lng(), path, half);

            // 지하차도 구간(경로선 중 입구~출구) - 이 근처 후보는 쓸모없다
            int iEntry = GeoUtil.nearestSegment(entry.lat(), entry.lng(), path);
            int iExit = GeoUtil.nearestSegment(exit.lat(), exit.lng(), path);
            List<double[]> zone = new ArrayList<>();
            zone.add(new double[]{entry.lat(), entry.lng()});
            zone.addAll(GeoUtil.subPath(path, iEntry + 1, iExit));
            zone.add(new double[]{exit.lat(), exit.lng()});
            zones.add(zone);

            for (double back : new double[]{half + 100, half + 400, half + 800}) {
                GeoUtil.OnPath p = GeoUtil.pointAlong(h.lat(), h.lng(), path, -back);
                for (double dist : new double[]{150, 350, 600}) {
                    for (double off : new double[]{90, -90}) {
                        entryLateral.add(GeoUtil.destination(p.lat(), p.lng(), p.bearing() + off, dist));
                    }
                }
            }

            int seg = GeoUtil.nearestSegment(h.lat(), h.lng(), path);
            double[] a = path.get(seg);
            double[] b = path.get(Math.min(seg + 1, path.size() - 1));
            double heading = GeoUtil.bearing(a[0], a[1], b[0], b[1]);
            for (double dist : new double[]{300, 600, 900}) {
                for (double off : new double[]{90, -90}) {
                    centerLateral.add(GeoUtil.destination(h.lat(), h.lng(), heading + off, dist));
                }
            }
            for (int k = 0; k < 8; k++) {
                ring.add(GeoUtil.destination(h.lat(), h.lng(), k * 45.0, 450));
            }
        }

        Comparator<double[]> byDetourLength = Comparator.comparingDouble(
                w -> GeoUtil.distance(oLat, oLng, w[0], w[1]) + GeoUtil.distance(w[0], w[1], dLat, dLng));
        entryLateral.sort(byDetourLength);
        centerLateral.sort(byDetourLength);
        ring.sort(byDetourLength);

        List<double[]> ordered = new ArrayList<>(entryLateral);
        ordered.addAll(centerLateral);
        ordered.addAll(ring);

        List<double[]> picked = new ArrayList<>();
        for (double[] w : ordered) {
            boolean nearHazard = active.stream()
                    .anyMatch(h -> GeoUtil.distance(h.lat(), h.lng(), w[0], w[1]) < h.radiusM() * 2);
            boolean inZone = zones.stream().anyMatch(z -> GeoUtil.distanceToPolyline(w[0], w[1], z) < 120);
            boolean duplicate = picked.stream()
                    .anyMatch(p -> GeoUtil.distance(p[0], p[1], w[0], w[1]) < 120);
            if (!nearHazard && !inZone && !duplicate) {
                picked.add(w);
            }
        }
        return picked;
    }

    /**
     * 경로가 위험지점 근처를 지나는지.
     *  1) 측량한 곳은 입구→출구 선분, 아니면 중심점과 경로선의 거리로 본다 (기본 판정)
     *  2) 보조 판정: 카카오 안내 중 "지하차도 진입"(type 15) 지점이 위험지점 중심에서 가까우면 지나는 것으로 본다.
     *     중심점이 도로에서 조금 벗어나 반경(기본 70m) 밖으로 나온 경우를 잡기 위한 것이다.
     */
    private boolean isNear(NaviRoute route, Hazard h) {
        if (h.segments() != null) {
            if (h.segments().stream()
                    .anyMatch(s -> GeoUtil.polylineToSegmentDistance(route.path(), s[0], s[1]) < h.radiusM())) {
                return true;
            }
        } else if (GeoUtil.distanceToPolyline(h.lat(), h.lng(), route.path()) < h.radiusM()) {
            return true;
        }
        return passesUnderpassGuide(route, h);
    }

    /** 지하차도 진입 안내(type 15) 지점이 위험지점 중심에서 보조 반경 이내인가 */
    private boolean passesUnderpassGuide(NaviRoute route, Hazard h) {
        if (underpassGuideType <= 0 || underpassGuideRadius <= 0 || route.guides() == null) {
            return false;
        }
        for (NaviRoute.Guide g : route.guides()) {
            if (g.type() == underpassGuideType
                    && GeoUtil.distance(h.lat(), h.lng(), g.lat(), g.lng()) < underpassGuideRadius) {
                return true;
            }
        }
        return false;
    }

    private static List<String> names(List<Hazard> list) {
        return list.stream().map(Hazard::name).toList();
    }

    private RouteResult result(NaviRoute r, boolean detoured, boolean mandatory, double[] wp, List<String> passes,
                               List<String> blocked, List<String> advisory, String message,
                               RouteResult.SuggestedDetour suggested) {
        return new RouteResult(r.distance(), r.duration(), r.path(), r.guides(), detoured, mandatory, wp, passes,
                blocked, advisory, message, suggested);
    }

    private static double round5(double v) {
        return Math.round(v * 1e5) / 1e5;
    }

    /** "경도,위도|경도,위도" -> [{lat,lng}, ...] */
    private static List<double[]> parseFallback(String text) {
        List<double[]> list = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return list;
        }
        for (String part : text.split("\\|")) {
            String[] p = part.trim().split(",");
            if (p.length == 2) {
                list.add(new double[]{Double.parseDouble(p[1].trim()), Double.parseDouble(p[0].trim())});
            }
        }
        return list;
    }
}
