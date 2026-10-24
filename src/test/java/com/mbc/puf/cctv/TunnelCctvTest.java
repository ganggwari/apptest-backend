package com.mbc.puf.cctv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.cctv.client.SeoulTunnelClient;
import com.mbc.puf.cctv.dto.TunnelCameraStreamResponse;
import com.mbc.puf.cctv.service.TunnelCctvServiceImpl;
import com.mbc.puf.cctv.service.TunnelStreamUrl;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** 서울터널 제공처를 가짜 서버로 대신 받아 목록·상세·주소 검증·캐시를 확인한다 (인터넷 불필요) */
class TunnelCctvTest {

    private HttpServer server;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private volatile boolean down = false;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/getCctv-data.do", ex -> reply(ex,
                "{\"list\":[{\"CCTV_ID\":\"101\"},{\"CCTV_ID\":102},{\"CCTV_ID\":\"101\"}]}"));
        server.createContext("/getCctvInfo-data.do", ex -> {
            String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String id = form.replace("cctvId=", "");
            if (id.equals("102")) {
                reply(ex, "{\"info\":{\"CCTV_ID\":\"102\",\"CCTV_NM\":\"신월 입구\",\"DRC\":\"여의도 방향\","
                        + "\"STREAMING2_ADRES\":\"http://211.61.148.68:1935/live/cam102/playlist.m3u8?a=1\"}}");
            } else {
                reply(ex, "{\"info\":{\"CCTV_ID\":\"101\",\"CCTV_NM\":\"\",\"DRC\":\"\","
                        + "\"STREAMING2_ADRES\":\"https://evil.example.com/x.m3u8\"}}");
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void reply(HttpExchange ex, String body) throws java.io.IOException {
        calls.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath());
        if (down) {
            ex.sendResponseHeaders(500, -1);
            ex.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html;charset=UTF-8");   // 제공처가 JSON 을 html 로 줘도 읽어야 한다
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private TunnelCctvServiceImpl service(int listTtl, int streamTtl) {
        SeoulTunnelClient client = new SeoulTunnelClient("http://127.0.0.1:" + server.getAddress().getPort(), 5);
        return new TunnelCctvServiceImpl(client, listTtl, streamTtl);
    }

    @Test
    void listIsDeduplicatedAndCached() {
        TunnelCctvServiceImpl s = service(300, 60);
        assertThat(s.getCameras()).extracting("id").containsExactly("101", "102");
        s.getCameras();
        assertThat(calls).hasSize(1);
    }

    @Test
    void legacyStreamHostIsRewrittenToOfficialHttps() {
        TunnelCameraStreamResponse r = service(300, 60).getStream("102");
        assertThat(r.getName()).isEqualTo("신월 입구");
        assertThat(r.getDirection()).isEqualTo("여의도 방향");
        assertThat(r.getStreamUrl()).isEqualTo("https://www.seoultunnel.co.kr/live/cam102/playlist.m3u8?a=1");
    }

    @Test
    void foreignStreamHostIsRejected() {
        assertThatThrownBy(() -> service(300, 60).getStream("101"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void unknownOrBadIdsNeverReachTheProvider() {
        TunnelCctvServiceImpl s = service(300, 60);
        assertThatThrownBy(() -> s.getStream("../etc")).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThatThrownBy(() -> s.getStream("999")).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(calls).containsExactly("POST /getCctv-data.do");   // 상세 호출은 한 번도 없다
    }

    @Test
    void staleValueIsUsedWhenProviderGoesDown() throws Exception {
        TunnelCctvServiceImpl s = service(0, 0);   // 캐시 즉시 만료
        assertThat(s.getStream("102").getName()).isEqualTo("신월 입구");
        down = true;
        assertThat(s.getStream("102").getName()).isEqualTo("신월 입구");   // 제공처 500 이어도 이전 값
    }

    @Test
    void streamUrlRules() {
        assertThat(TunnelStreamUrl.normalize("https://seoultunnel.co.kr/a.m3u8")).isEqualTo("https://seoultunnel.co.kr/a.m3u8");
        assertThatThrownBy(() -> TunnelStreamUrl.normalize("http://www.seoultunnel.co.kr/a.m3u8")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TunnelStreamUrl.normalize("https://user:pw@www.seoultunnel.co.kr/a")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TunnelStreamUrl.normalize("ftp://www.seoultunnel.co.kr/a")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> TunnelStreamUrl.normalize("https://www.seoultunnel.co.kr.evil.com/a")).isInstanceOf(BusinessException.class);
    }
}
