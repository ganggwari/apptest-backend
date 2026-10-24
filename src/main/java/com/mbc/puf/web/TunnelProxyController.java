package com.mbc.puf.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/**
 * /tunnel-source/** → https://seoultunnel.co.kr/usage/** 로 그대로 전달한다 (프런트의 지하차도 참고 영상 조회용).
 * 개발 때는 vite 프록시가 하던 일이고, 정적 호스팅(Render Static Site)에서는 rewrite 규칙이 하던 일이다.
 */
@RestController
public class TunnelProxyController {

	private static final String TARGET = "https://seoultunnel.co.kr/usage/";
	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	@RequestMapping("/tunnel-source/**")
	public ResponseEntity<byte[]> forward(HttpServletRequest request) throws IOException, InterruptedException {
		String rest = request.getRequestURI().substring("/tunnel-source/".length());
		String query = request.getQueryString();
		URI uri = URI.create(TARGET + rest + (query == null ? "" : "?" + query));
		byte[] body = request.getInputStream().readAllBytes();
		HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15));
		String contentType = request.getContentType();
		if (contentType != null) {
			b.header(HttpHeaders.CONTENT_TYPE, contentType);
		}
		b.header(HttpHeaders.USER_AGENT, "Mozilla/5.0");
		b.method(request.getMethod(), body.length == 0 && "GET".equals(request.getMethod())
				? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
		HttpResponse<byte[]> res;
		try {
			res = client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
		} catch (IOException e) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
		}
		ResponseEntity.BodyBuilder out = ResponseEntity.status(res.statusCode());
		res.headers().firstValue(HttpHeaders.CONTENT_TYPE).ifPresent(v -> out.header(HttpHeaders.CONTENT_TYPE, v));
		return out.body(res.body());
	}
}
