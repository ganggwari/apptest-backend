package com.mbc.puf.weather.client;

/**
 * 위경도 -> 기상청 단기예보 격자(nx, ny) 변환 (기상청 공식 Lambert 방식, 5km 격자).
 * 예: 서울 (37.5665, 126.978) -> (60, 127)
 */
public final class KmaGrid {

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double STANDARD_LAT_1 = 30.0;
    private static final double STANDARD_LAT_2 = 60.0;
    private static final double ORIGIN_LNG = 126.0;
    private static final double ORIGIN_LAT = 38.0;
    private static final double ORIGIN_X = 43;
    private static final double ORIGIN_Y = 136;

    private KmaGrid() {
    }

    public record Point(int nx, int ny) {}

    public static Point of(double lat, double lng) {
        double rad = Math.PI / 180.0;
        double re = EARTH_RADIUS_KM / GRID_KM;
        double slat1 = STANDARD_LAT_1 * rad;
        double slat2 = STANDARD_LAT_2 * rad;
        double olng = ORIGIN_LNG * rad;
        double olat = ORIGIN_LAT * rad;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * rad * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lng * rad - olng;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + ORIGIN_Y + 0.5);
        return new Point(nx, ny);
    }
}
