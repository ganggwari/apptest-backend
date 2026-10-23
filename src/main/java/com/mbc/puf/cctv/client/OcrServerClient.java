package com.mbc.puf.cctv.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.mbc.puf.common.exception.BusinessException;

/**
 * OCR(FastAPI) 서버를 부르는 클라이언트. 주소는 puf.ocr-server.url, 인증은 X-Internal-Api-Key.
 * 영상(MJPEG)은 읽는 시간 제한 없이 흘려보내고, 나머지 짧은 요청은 puf.ocr-server.timeout-seconds 로 끊는다.
 */
@Component
public class OcrServerClient {

    private final String baseUrl;
    private final String apiKey;
    private final Duration timeout;
    private final HttpClient http;

    public OcrServerClient(@Value("${puf.ocr-server.url}") String baseUrl,
                           @Value("${puf.internal.api-key}") String apiKey,
                           @Value("${puf.ocr-server.timeout-seconds:10}") long timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        System.out.println("[OCR-KEY] length=" + apiKey.length());
    }

    /** 응답 본문을 흘려 읽는다(MJPEG). 호출한 쪽이 body 를 반드시 닫아야 OCR 서버 쪽 연결도 정리된다 */
    public HttpResponse<InputStream> openStream(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().header("X-Internal-Api-Key", apiKey),
                HttpResponse.BodyHandlers.ofInputStream(), null);
    }

    public HttpResponse<byte[]> getBytes(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().header("X-Internal-Api-Key", apiKey),
                HttpResponse.BodyHandlers.ofByteArray(), timeout);
    }

    public HttpResponse<byte[]> postBytes(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).header("X-Internal-Api-Key", apiKey)
                .POST(HttpRequest.BodyPublishers.noBody()), HttpResponse.BodyHandlers.ofByteArray(), timeout);
    }

    private <T> HttpResponse<T> send(HttpRequest.Builder builder, HttpResponse.BodyHandler<T> handler, Duration requestTimeout) {
        if (requestTimeout != null) builder.timeout(requestTimeout);
        try {
            return http.send(builder.build(), handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "OCR 서버 요청이 중단되었습니다.");
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "OCR 서버에 연결할 수 없습니다. 서버가 켜져 있는지, puf.ocr-server.url 을 확인하세요.");
        }
    }
}
