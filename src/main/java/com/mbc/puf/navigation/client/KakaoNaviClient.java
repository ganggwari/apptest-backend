package com.mbc.puf.navigation.client;

import com.mbc.puf.navigation.geo.GeoUtil;
import com.mbc.puf.navigation.dto.NaviRoute;
import com.mbc.puf.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 카카오모빌리티 자동차 길찾기 호출 + 응답 해석만 담당 (우회 판단 같은 "생각"은 DetourService 몫) */
@Component
public class KakaoNaviClient implements NaviRouteProvider {

    private final RestClient restClient = RestClient.create();
    private final String restApiKey;
    private final String directionsUrl;

    public KakaoNaviClient(@Value("${puf.kakao.rest-key:}") String restApiKey,
                           @Value("${puf.kakao.directions-url:https://apis-navi.kakaomobility.com/v1/directions}") String directionsUrl) {
        this.restApiKey = restApiKey;
        this.directionsUrl = directionsUrl;
    }

    /**
     * @param waypoints 경유지 {lat, lng} 목록 (없으면 빈 리스트)
     * @param heading   출발할 때 차가 향하는 방향(도, 북쪽 0 / 동쪽 90). 모르면 null.
     *                  달리는 도중에 다시 길을 찾을 때 꼭 필요하다. 안 주면 카카오가 방향을 몰라서
     *                  "방금 지나온 쪽으로 되돌아가는" 경로를 줄 수 있다.
     */
    @Override
    @SuppressWarnings("unchecked")
    public NaviRoute route(double oLat, double oLng, double dLat, double dLng,
                           List<double[]> waypoints, Double heading) {
        String origin = oLng + "," + oLat;                          // 카카오는 "경도,위도" 순서
        if (heading != null) {
            origin += ",angle=" + (((int) Math.round(heading) % 360 + 360) % 360);
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(directionsUrl)
                .queryParam("origin", origin)
                .queryParam("destination", dLng + "," + dLat)
                .queryParam("priority", "RECOMMEND");
        if (waypoints != null && !waypoints.isEmpty()) {
            String wp = waypoints.stream()
                    .map(w -> w[1] + "," + w[0])
                    .collect(Collectors.joining("|"));
            builder.queryParam("waypoints", wp);
        }
        URI uri = builder.build().encode().toUri();               // '|' 같은 문자를 안전하게 인코딩

        Map<String, Object> body = restClient.get()
                .uri(uri)
                .header("Authorization", "KakaoAK " + requireKey())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {
                });

        List<Map<String, Object>> routes = body == null ? null : (List<Map<String, Object>>) body.get("routes");
        if (routes == null || routes.isEmpty()) {
            throw new NaviException("길찾기 결과가 비어 있습니다.");
        }
        Map<String, Object> route = routes.get(0);

        int resultCode = toInt(route.get("result_code"));
        if (resultCode != 0) {
            throw new NaviException("길찾기 실패(" + resultCode + "): " + route.get("result_msg"));
        }

        Map<String, Object> summary = (Map<String, Object>) route.get("summary");
        int distance = toInt(summary.get("distance"));
        int duration = toInt(summary.get("duration"));

        // 구간(section) -> 도로(road) -> vertexes[x,y,x,y,...] 를 이어 붙여 한 줄짜리 경로로 만든다.
        List<double[]> path = new ArrayList<>();
        List<Map<String, Object>> rawGuides = new ArrayList<>();
        List<Map<String, Object>> sections = (List<Map<String, Object>>) route.get("sections");
        if (sections != null) {
            for (Map<String, Object> section : sections) {
                List<Map<String, Object>> roads = (List<Map<String, Object>>) section.get("roads");
                if (roads != null) {
                    for (Map<String, Object> road : roads) {
                        List<Number> v = (List<Number>) road.get("vertexes");
                        if (v == null) {
                            continue;
                        }
                        for (int i = 0; i + 1 < v.size(); i += 2) {
                            double lng = v.get(i).doubleValue();
                            double lat = v.get(i + 1).doubleValue();
                            double[] last = path.isEmpty() ? null : path.get(path.size() - 1);
                            if (last == null || last[0] != lat || last[1] != lng) {   // 도로가 이어지는 점 중복 제거
                                path.add(new double[]{lat, lng});
                            }
                        }
                    }
                }
                List<Map<String, Object>> guides = (List<Map<String, Object>>) section.get("guides");
                if (guides != null) {
                    rawGuides.addAll(guides);
                }
            }
        }
        if (path.size() < 2) {
            throw new NaviException("경로 좌표를 해석하지 못했습니다.");
        }

        path = trimToOrigin(path, oLat, oLng);
        return new NaviRoute(distance, duration, path, buildGuides(path, rawGuides));
    }

    /**
     * 카카오는 출발점을 "도로 구간의 시작점"으로 맞춰서 경로를 시작하는 경우가 있다.
     * 그러면 이미 지나온 길(수십~수백 m)이 경로 앞부분에 딸려 와서
     * (1) 차가 뒤로 튀고 (2) 방금 지나친 위험지점이 "앞에 있다"고 잘못 판단된다.
     * 그래서 출발점에서 가장 가까운 지점부터로 경로 앞부분을 잘라낸다.
     */
    private List<double[]> trimToOrigin(List<double[]> path, double oLat, double oLng) {
        int bestSeg = 0;
        double bestDist = Double.MAX_VALUE;
        double acc = 0;
        for (int i = 0; i < path.size() - 1 && acc <= 300; i++) {          // 앞쪽 300m 안에서만 찾는다
            double d = GeoUtil.distanceToPolyline(oLat, oLng, path.subList(i, i + 2));
            if (d < bestDist) {
                bestDist = d;
                bestSeg = i;
            }
            acc += GeoUtil.distance(path.get(i)[0], path.get(i)[1], path.get(i + 1)[0], path.get(i + 1)[1]);
        }
        if (bestSeg == 0 && GeoUtil.distance(oLat, oLng, path.get(0)[0], path.get(0)[1]) < 30) {
            return path;   // 이미 출발점에서 시작함
        }
        double[] start = GeoUtil.closestPointOnSegment(oLat, oLng, path, bestSeg);
        List<double[]> trimmed = new ArrayList<>();
        trimmed.add(start);
        for (int i = bestSeg + 1; i < path.size(); i++) {
            double[] p = path.get(i);
            if (trimmed.size() == 1 && GeoUtil.distance(start[0], start[1], p[0], p[1]) < 0.5) {
                continue;   // 시작점과 사실상 같은 점은 건너뜀
            }
            trimmed.add(p);
        }
        return trimmed.size() >= 2 ? trimmed : path;
    }

    /** 안내 지점마다 "출발점에서 경로를 따라 몇 m 지점인지(at)"를 계산해 붙인다. */
    private List<NaviRoute.Guide> buildGuides(List<double[]> path, List<Map<String, Object>> rawGuides) {
        int n = path.size();
        double[] cum = new double[n];
        for (int i = 1; i < n; i++) {
            cum[i] = cum[i - 1] + GeoUtil.distance(path.get(i - 1)[0], path.get(i - 1)[1],
                    path.get(i)[0], path.get(i)[1]);
        }

        List<NaviRoute.Guide> result = new ArrayList<>();
        int from = 0;   // 안내는 경로 순서대로 나오므로, 이전 안내 지점 이후에서만 찾는다
        for (Map<String, Object> g : rawGuides) {
            Object text = g.get("guidance");
            if (text == null || String.valueOf(text).isBlank()) {
                continue;
            }
            double lng = ((Number) g.get("x")).doubleValue();
            double lat = ((Number) g.get("y")).doubleValue();

            int best = from;
            double bestDist = Double.MAX_VALUE;
            for (int i = from; i < n; i++) {
                double d = GeoUtil.distance(path.get(i)[0], path.get(i)[1], lat, lng);
                if (d < bestDist) {
                    bestDist = d;
                    best = i;
                }
            }
            from = best;

            String name = g.get("name") == null ? "" : String.valueOf(g.get("name"));
            int type = g.get("type") == null ? -1 : toInt(g.get("type"));
            result.add(new NaviRoute.Guide(lat, lng, name, String.valueOf(text), type, (int) cum[best]));
        }
        return result;
    }

    private String requireKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "길찾기 키(puf.kakao.rest-key)가 설정되지 않았습니다.");
        }
        return restApiKey.trim();
    }

    private static int toInt(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : -1;
    }
}
