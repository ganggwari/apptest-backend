package com.mbc.puf.common.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.mbc.puf.common.exception.BusinessException;
import com.sun.net.httpserver.HttpServer;

/** Solapi 를 가짜 서버로 대신 받아 서명 헤더·본문·오류 처리를 확인한다 (인터넷·실제 문자 불필요) */
class SolapiSmsSenderTest {

    private HttpServer server;
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/messages/v4/send", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = (status == 200 ? "{\"groupId\":\"G1\"}" : "{\"errorCode\":\"SenderNotFound\"}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private SolapiSmsSender sender() {
        return new SolapiSmsSender("KEY123", "SECRET456", "010-2780-9514",
                "http://127.0.0.1:" + server.getAddress().getPort(), 5);
    }

    @Test
    void 서명_헤더와_본문이_맞게_나간다() throws Exception {
        sender().send("01011112222", "인증 [123456]");

        Matcher m = Pattern.compile("^HMAC-SHA256 apiKey=KEY123, date=(\\S+), salt=(\\w+), signature=([0-9a-f]{64})$")
                .matcher(auth.get());
        assertThat(m.matches()).as(auth.get()).isTrue();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("SECRET456".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String expected = HexFormat.of().formatHex(
                mac.doFinal((m.group(1) + m.group(2)).getBytes(StandardCharsets.UTF_8)));
        assertThat(m.group(3)).isEqualTo(expected);
        assertThat(m.group(1)).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z");

        assertThat(body.get()).contains("\"to\":\"01011112222\"").contains("\"from\":\"01027809514\"")
                .contains("인증 [123456]");
    }

    @Test
    void 제공처_오류는_BAD_GATEWAY_로_바뀐다() {
        status = 400;
        assertThatThrownBy(() -> sender().send("01011112222", "x")).isInstanceOf(BusinessException.class);
    }

    @Test
    void 키가_비어_있으면_시작하지_못한다() {
        assertThatThrownBy(() -> new SolapiSmsSender("", "S", "01000000000", "http://x", 5))
                .isInstanceOf(IllegalStateException.class);
    }
}
