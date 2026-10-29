package com.mbc.puf;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.mbc.puf.common.security.InternalApiKeyInterceptor;

//접속허가
@Configuration  //<-설정
public class WebConfigurer implements WebMvcConfigurer {

	private final InternalApiKeyInterceptor internalApiKeyInterceptor;

	// 첨부파일/캡처 이미지 저장 위치 (application.properties의 file.upload-dir)
	@Value("${file.upload-dir}")
	private String uploadDir;

	// 배포 환경에서 추가로 허용할 프런트 출처 (쉼표 구분, 예: https://puf-front.onrender.com 또는 https://*.onrender.com).
	// 프런트가 /api 를 프록시로 부르는 경우에도 POST/PUT/DELETE 에는 Origin 헤더가 붙어 여기에 들어 있어야 통과한다
	@Value("${puf.cors.allowed-origin-patterns:}")
	private String extraAllowedOriginPatterns;

	public WebConfigurer(InternalApiKeyInterceptor internalApiKeyInterceptor) {
		this.internalApiKeyInterceptor = internalApiKeyInterceptor;
	}



	@Override
	public void addCorsMappings(CorsRegistry registry) {

		/*
		//모든 접속(접근) 허용
		registry.addMapping("/**").allowedOrigins("*");
		/**/

		//해당하는 주소만 접근 허용
		//registry.addMapping("/**").allowedOrigins("http://localhost:9000");
		// 세션 쿠키(JSESSIONID)를 프런트로 주고받으려면 allowCredentials(true)가 필요한데,
		// 이건 allowedOrigins("*") 와일드카드랑 같이 못 씀 (CORS 스펙상 금지) -> 구체적인 origin을 지정해야 함
		// 같은 와이파이의 아이패드/폰에서 http://<노트북IP>:8282 로 접속해도 되도록 사설 IP 대역도 허용 (개발용)
		List<String> patterns = new ArrayList<>(List.of("http://localhost:[*]", "http://127.0.0.1:[*]",
				"http://10.*:[*]", "http://172.*:[*]", "http://192.168.*:[*]",
				// 안드로이드 에뮬레이터/WebView 가 https://localhost 로 열리는 경우
				"https://localhost:[*]", "https://localhost"));
		for (String extra : extraAllowedOriginPatterns.split(",")) {
			if (!extra.isBlank()) {
				patterns.add(extra.trim());
			}
		}
		registry.addMapping("/**")
		.allowedOriginPatterns(patterns.toArray(String[]::new))
		.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
		.exposedHeaders("X-Auth-Token", "Content-Disposition") // 로그인 응답의 토큰 헤더를 프런트(다른 도메인)가 읽을 수 있게
		.allowCredentials(true);
	}

	// FastAPI 내부 API(/api/internal/**)는 세션이 아니라 API 키로 인증한다 (SecurityConfig는 이 경로를 permitAll로 열어둠)
	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(internalApiKeyInterceptor)
		.addPathPatterns("/api/internal/**");
	}

	// uploads 폴더(첨부파일, 번호판 캡처 이미지 등)를 /api/uploads/** 로 정적 제공.
	// 프런트의 다른 관리자 API 호출(fetch('/api/...'))과 같은 경로 밑에 둬야 프런트 쪽 프록시/라우팅을
	// 그대로 타고 백엔드까지 도달한다 (원래 /uploads/**로 뒀더니 다른 /api/* 요청과 달리 안 뜨는 문제가 있었음).
	// 로그인 세션이 있어야 보이도록(관리자 화면에서 <img> 태그로 쿠키 포함 요청) SecurityConfig에서는 따로 permitAll 하지 않음 —
	// 로그인 없이도 누구나 봐도 되는 파일이면 SecurityConfig에 "/api/uploads/**" permitAll을 추가할 것
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/api/uploads/**")
		.addResourceLocations("file:" + uploadDir + "/");
	}

}