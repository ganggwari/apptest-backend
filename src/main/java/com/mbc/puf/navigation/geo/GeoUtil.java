package com.mbc.puf.navigation.geo;

import java.util.List;

/** 위도/경도 계산 도우미. 좌표는 항상 (lat, lng) 순서, 경로 한 점은 double[]{lat, lng}. */
public final class GeoUtil {

    private static final double R = 6_371_000.0; // 지구 반지름(m)

    private GeoUtil() {
    }

    /** 두 점 사이 거리(m) - 하버사인 */
    public static double distance(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * R * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** 점 1 -> 점 2 방향(도, 북쪽 0 / 동쪽 90) */
    public static double bearing(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    /** 출발점에서 bearing 방향으로 meters 만큼 간 지점 {lat, lng} */
    public static double[] destination(double lat, double lng, double bearingDeg, double meters) {
        double ang = meters / R;
        double brg = Math.toRadians(bearingDeg);
        double p1 = Math.toRadians(lat);
        double l1 = Math.toRadians(lng);
        double p2 = Math.asin(Math.sin(p1) * Math.cos(ang) + Math.cos(p1) * Math.sin(ang) * Math.cos(brg));
        double l2 = l1 + Math.atan2(Math.sin(brg) * Math.sin(ang) * Math.cos(p1),
                Math.cos(ang) - Math.sin(p1) * Math.sin(p2));
        return new double[]{Math.toDegrees(p2), Math.toDegrees(l2)};
    }

    /** 점에서 경로(꺾은선)까지의 최단 거리(m) */
    public static double distanceToPolyline(double lat, double lng, List<double[]> path) {
        int seg = nearestSegment(lat, lng, path);
        if (seg < 0) {
            return Double.MAX_VALUE;
        }
        return segmentDistance(lat, lng, path, seg);
    }

    /** 점과 가장 가까운 구간의 시작 인덱스(구간 = path[i] ~ path[i+1]). 경로가 비어 있으면 -1 */
    public static int nearestSegment(double lat, double lng, List<double[]> path) {
        if (path == null || path.isEmpty()) {
            return -1;
        }
        if (path.size() == 1) {
            return 0;
        }
        int best = 0;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < path.size() - 1; i++) {
            double d = segmentDistance(lat, lng, path, i);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }

    /** 점에서 구간(path[i] ~ path[i+1])까지 가장 가까운 지점 {lat, lng} (구간 위로 "수선의 발"을 내린 점) */
    public static double[] closestPointOnSegment(double lat, double lng, List<double[]> path, int i) {
        double[] a = path.get(i);
        double[] b = path.get(Math.min(i + 1, path.size() - 1));
        double cos = Math.cos(Math.toRadians(lat));
        double ax = Math.toRadians(a[1] - lng) * cos * R;
        double ay = Math.toRadians(a[0] - lat) * R;
        double bx = Math.toRadians(b[1] - lng) * cos * R;
        double by = Math.toRadians(b[0] - lat) * R;
        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return new double[]{lat + Math.toDegrees(py / R), lng + Math.toDegrees(px / (R * cos))};
    }

    private static double segmentDistance(double lat, double lng, List<double[]> path, int i) {
        double[] a = path.get(i);
        double[] b = path.get(Math.min(i + 1, path.size() - 1));

        // 기준점 부근에서는 위/경도를 평면(미터)으로 펴서 계산해도 오차가 매우 작다.
        double cos = Math.cos(Math.toRadians(lat));
        double ax = Math.toRadians(a[1] - lng) * cos * R;
        double ay = Math.toRadians(a[0] - lat) * R;
        double bx = Math.toRadians(b[1] - lng) * cos * R;
        double by = Math.toRadians(b[0] - lat) * R;

        double dx = bx - ax;
        double dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        double px = ax + t * dx;
        double py = ay + t * dy;
        return Math.sqrt(px * px + py * py);
    }

    /** 경로 위 한 지점 {lat, lng, 진행방향(도)} */
    public record OnPath(double lat, double lng, double bearing) {
    }

    /** 경로를 따라 "출발점부터 몇 m 지점인지"를 구간 앞쪽부터 누적한 배열 (길이 = path.size()) */
    public static double[] cumulative(List<double[]> path) {
        double[] cum = new double[path.size()];
        for (int i = 1; i < path.size(); i++) {
            cum[i] = cum[i - 1] + distance(path.get(i - 1)[0], path.get(i - 1)[1], path.get(i)[0], path.get(i)[1]);
        }
        return cum;
    }

    /**
     * 점(lat,lng)에서 경로 위로 수선을 내린 지점을 기준으로, 경로를 따라 deltaMeters 만큼 앞(+)/뒤(-)로 간 지점.
     * 경로 시작/끝을 넘으면 시작/끝 지점으로 맞춘다. 진행방향은 그 지점이 속한 구간의 방향.
     */
    public static OnPath pointAlong(double lat, double lng, List<double[]> path, double deltaMeters) {
        int seg = nearestSegment(lat, lng, path);
        double[] cum = cumulative(path);
        double[] foot = closestPointOnSegment(lat, lng, path, seg);
        double s0 = cum[seg] + distance(path.get(seg)[0], path.get(seg)[1], foot[0], foot[1]);
        double s = Math.max(0, Math.min(cum[cum.length - 1], s0 + deltaMeters));
        int i = 0;
        while (i < path.size() - 2 && cum[i + 1] < s) {
            i++;
        }
        double len = cum[i + 1] - cum[i];
        double t = len == 0 ? 0 : (s - cum[i]) / len;
        double[] a = path.get(i);
        double[] b = path.get(i + 1);
        return new OnPath(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, bearing(a[0], a[1], b[0], b[1]));
    }

    /** 점에서 선분(a-b)까지의 최단 거리(m) */
    public static double distanceToSegment(double lat, double lng, double[] a, double[] b) {
        return distanceToPolyline(lat, lng, List.of(a, b));
    }

    /** 경로(꺾은선)와 선분(a-b) 사이의 최단 거리(m). 선분을 일정 간격으로 쪼개 각 점의 경로까지 거리의 최솟값으로 근사 */
    public static double polylineToSegmentDistance(List<double[]> path, double[] a, double[] b) {
        double len = distance(a[0], a[1], b[0], b[1]);
        int steps = (int) Math.max(1, Math.ceil(len / 25.0));
        double best = Double.MAX_VALUE;
        for (int k = 0; k <= steps; k++) {
            double t = (double) k / steps;
            double d = distanceToPolyline(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, path);
            best = Math.min(best, d);
        }
        return best;
    }

    /** path 중 인덱스 [from, to] 구간만 잘라낸 경로 (to 가 더 작으면 순서를 바꾼다) */
    public static List<double[]> subPath(List<double[]> path, int from, int to) {
        int lo = Math.max(0, Math.min(from, to));
        int hi = Math.min(path.size() - 1, Math.max(from, to));
        return new java.util.ArrayList<>(path.subList(lo, hi + 1));
    }
}
