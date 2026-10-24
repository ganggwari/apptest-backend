package com.mbc.puf.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.weather.entity.WeatherArea;
import com.mbc.puf.weather.service.KmaClient;
import com.sun.net.httpserver.HttpServer;

/** 기상청 호출을 가짜 서버(JDK HttpServer)로 대신 받아 격자 변환·주소·응답 해석을 확인한다 (인터넷 불필요) */
class KmaApiClientTest {

    private static final String NCST = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"dataType":"JSON","items":{"item":[
            {"baseDate":"20260724","baseTime":"1400","category":"PTY","nx":98,"ny":75,"obsrValue":"1"},
            {"baseDate":"20260724","baseTime":"1400","category":"REH","nx":98,"ny":75,"obsrValue":"91"},
            {"baseDate":"20260724","baseTime":"1400","category":"RN1","nx":98,"ny":75,"obsrValue":"12.5"},
            {"baseDate":"20260724","baseTime":"1400","category":"T1H","nx":98,"ny":75,"obsrValue":"24.3"}]},
            "pageNo":1,"numOfRows":100,"totalCount":4}}}""";

    private static final String FCST = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},"body":{"dataType":"JSON","items":{"item":[
            {"category":"T1H","fcstDate":"20260724","fcstTime":"1500","fcstValue":"24"},
            {"category":"RN1","fcstDate":"20260724","fcstTime":"1500","fcstValue":"30.0~50.0mm"},
            {"category":"SKY","fcstDate":"20260724","fcstTime":"1500","fcstValue":"4"},
            {"category":"PTY","fcstDate":"20260724","fcstTime":"1500","fcstValue":"1"},
            {"category":"REH","fcstDate":"20260724","fcstTime":"1500","fcstValue":"90"},
            {"category":"T1H","fcstDate":"20260724","fcstTime":"1600","fcstValue":"25"},
            {"category":"RN1","fcstDate":"20260724","fcstTime":"1600","fcstValue":"강수없음"},
            {"category":"SKY","fcstDate":"20260724","fcstTime":"1600","fcstValue":"3"},
            {"category":"PTY","fcstDate":"20260724","fcstTime":"1600","fcstValue":"0"},
            {"category":"REH","fcstDate":"20260724","fcstTime":"1600","fcstValue":"80"},
            {"category":"T1H","fcstDate":"20260724","fcstTime":"1400","fcstValue":"23"}]},
            "pageNo":1,"numOfRows":100,"totalCount":11}}}""";

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile String ncstBody = NCST;
    private volatile String fcstBody = FCST;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/getUltraSrtNcst", ex -> reply(ex, ncstBody));
        server.createContext("/getUltraSrtFcst", ex -> reply(ex, fcstBody));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void reply(com.sun.net.httpserver.HttpExchange ex, String body) throws java.io.IOException {
        requests.add(ex.getRequestURI().toString());
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json;charset=UTF-8");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private KmaApiClient client(String key, LocalDateTime now) {
        DbClock clock = mock(DbClock.class);
        when(clock.now()).thenReturn(now);
        return new KmaApiClient(clock, key, "http://127.0.0.1:" + server.getAddress().getPort(), 5);
    }

    private static WeatherArea busanJung() {
        WeatherArea a = new WeatherArea();
        a.setName("중구");
        a.setLatitude(new BigDecimal("35.1060"));
        a.setLongitude(new BigDecimal("129.0324"));
        return a;
    }

    @Test
    void gridMatchesKmaReferencePoints() {
        assertThat(KmaGrid.of(37.5665, 126.9780)).isEqualTo(new KmaGrid.Point(60, 127));   // 서울
        KmaGrid.Point busan = KmaGrid.of(35.1796, 129.0756);                               // 부산시청 근처
        assertThat(busan.nx()).isBetween(96, 99);
        assertThat(busan.ny()).isBetween(73, 76);
    }

    @Test
    void observationUsesPreviousHourAndEncodesKey() {
        String key = "ab+cd/ef==";
        KmaApiClient c = client(key, LocalDateTime.of(2026, 7, 24, 14, 20));   // 14:20 -> 13:20 -> base 13:00? 14:20-40m = 13:40 -> 1300
        KmaClient.Observation o = c.fetchObservation(busanJung());
        assertThat(o).isNotNull();
        assertThat(o.temp()).isEqualByComparingTo("24.3");
        assertThat(o.humidity()).isEqualTo(91);
        assertThat(o.rain()).isEqualByComparingTo("12.5");
        assertThat(o.observedAt()).isEqualTo(LocalDateTime.of(2026, 7, 24, 13, 0));
        String uri = requests.get(0);
        assertThat(uri).contains("serviceKey=ab%2Bcd%2Fef%3D%3D");
        assertThat(uri).contains("base_date=20260724").contains("base_time=1300").contains("dataType=JSON");
    }

    @Test
    void sameGridAndBaseTimeIsCalledOnlyOnce() {
        KmaApiClient c = client("key", LocalDateTime.of(2026, 7, 24, 14, 50));
        c.fetchObservation(busanJung());
        c.fetchObservation(busanJung());
        assertThat(requests).hasSize(1);
    }

    @Test
    void forecastBuildsPointsAndSkipsPastHours() {
        KmaApiClient c = client("key", LocalDateTime.of(2026, 7, 24, 14, 50));   // base 14:30
        List<KmaClient.ForecastPoint> points = c.fetchForecast(busanJung());
        assertThat(points).hasSize(2);   // 15:00, 16:00 (14:00 은 지난 시점이라 뺀다)
        KmaClient.ForecastPoint first = points.get(0);
        assertThat(first.targetAt()).isEqualTo(LocalDateTime.of(2026, 7, 24, 15, 0));
        assertThat(first.rain()).isEqualByComparingTo("40.00");   // 30~50mm 의 가운데
        assertThat(first.sky()).isEqualTo("비");                  // PTY 가 SKY 보다 우선
        assertThat(first.humidity()).isEqualTo(90);
        assertThat(first.rainProb()).isNull();
        assertThat(points.get(1).sky()).isEqualTo("구름많음");
        assertThat(points.get(1).rain()).isEqualByComparingTo("0");
        assertThat(requests.get(0)).contains("base_time=1330");
    }

    @Test
    void noDataFallsBackToPreviousBaseTimeThenGivesUp() {
        ncstBody = "{\"response\":{\"header\":{\"resultCode\":\"03\",\"resultMsg\":\"NODATA_ERROR\"}}}";
        KmaApiClient c = client("key", LocalDateTime.of(2026, 7, 24, 14, 50));
        assertThat(c.fetchObservation(busanJung())).isNull();
        assertThat(requests).hasSize(2);   // 현재 기준 시각 + 한 시간 전
    }

    @Test
    void nonJsonErrorResponseIsHandled() {
        ncstBody = "<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>";
        fcstBody = ncstBody;
        KmaApiClient c = client("key", LocalDateTime.of(2026, 7, 24, 14, 50));
        assertThat(c.fetchObservation(busanJung())).isNull();
        assertThat(c.fetchForecast(busanJung())).isEmpty();
    }

    @Test
    void emptyKeyMakesNoCall() {
        KmaApiClient c = client("  ", LocalDateTime.of(2026, 7, 24, 14, 50));
        assertThat(c.fetchObservation(busanJung())).isNull();
        assertThat(c.fetchForecast(busanJung())).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void rainTextAndSkyMapping() {
        assertThat(KmaApiClient.rainMm("강수없음")).isEqualByComparingTo("0");
        assertThat(KmaApiClient.rainMm("1mm 미만")).isEqualByComparingTo("0.5");
        assertThat(KmaApiClient.rainMm("1.0mm")).isEqualByComparingTo("1");
        assertThat(KmaApiClient.rainMm("50.0mm 이상")).isEqualByComparingTo("50");
        assertThat(KmaApiClient.rainMm("-999")).isNull();
        assertThat(KmaApiClient.skyOf("1", "0")).isEqualTo("맑음");
        assertThat(KmaApiClient.skyOf("4", "0")).isEqualTo("흐림");
        assertThat(KmaApiClient.skyOf(null, "3")).isEqualTo("눈");
    }
}
