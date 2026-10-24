package com.mbc.puf.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 컨트롤러 메서드에 @PreAuthorize("hasRole('REGIONAL_ADMIN')") 같은 권한 검사를 쓰기 위해 필요
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthErrorHandler errorHandler)
			throws Exception {
		http
				// TODO(보안 점검): 세션 쿠키 방식이라 CSRF 대책이 필요하다. 지금은 SameSite=Lax
				// 쿠키(application.properties)에 의존한다.
				.csrf(AbstractHttpConfigurer::disable).cors(Customizer.withDefaults()) // CORS 설정(WebConfigurer 등)을 그대로
																							// 이어받는다
				// 기본 로그아웃 필터(LogoutFilter)를 꺼야 한다. 켜져 있으면 /logout 요청을 컨트롤러보다 먼저 가로채 302 로 보내
				// 버린다.
				// 로그아웃은 AuthController 에서 직접 처리한다.
				.logout(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.exceptionHandling(e -> e.authenticationEntryPoint(errorHandler).accessDeniedHandler(errorHandler))
				.authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // 브라우저 사전
																												// 요청(CORS)

						// ---- 로그인 없이 쓰는 API ----
						.requestMatchers("/api/customers/signup", "/api/customers/login",
								"/api/customers/check-login-id", "/api/customers/check-phone", "/api/customers/check-email",
								"/api/customers/phone-code/**", "/api/customers/password-reset/**",
								"/api/managers/login", "/api/managers/check-login-id", "/api/managers/signup",
								"/api/managers/signup/**", "/api/managers/password-reset/**", "/api/auth/logout")
						.permitAll()

						// ---- 로그인 없이 조회 가능한 정보 (고객 화면의 지역 필터, 관리자 화면의 드롭다운) ----
						.requestMatchers(HttpMethod.GET, "/api/regions").permitAll()
						// ---- 로그인 없이 조회 가능한 게시판 (공지사항 목록·상세, CP-09) ----
						.requestMatchers(HttpMethod.GET, "/api/notices", "/api/notices/**").permitAll()
						// ---- 로그인 없이 조회 가능한 게시판 (자주 묻는 질문 목록·상세, CP-10) ----
						.requestMatchers(HttpMethod.GET, "/api/faqs", "/api/faqs/**").permitAll()
						// ---- 로그인 없이 조회 가능한 지하차도 목록·상세 (CP-05~07). 등록/수정/삭제/차단 등
						//                  나머지 경로는 각 컨트롤러 메서드의 @PreAuthorize 가 여전히 관리자만 허용한다 ----
						// 통과 기록(차량번호)은 개인정보라 공개 목록·상세 규칙보다 먼저 로그인 필수로 막는다 (/api/underpasses/* 에 걸리지 않게)
						.requestMatchers(HttpMethod.GET, "/api/underpasses/transits").authenticated()
						.requestMatchers(HttpMethod.GET, "/api/underpasses", "/api/underpasses/*",
								"/api/underpasses/*/risk-scores", "/api/underpasses/*/risk-forecast").permitAll()
						// ---- 로그인 없이 조회 가능한 통제현황 통계 (MP-06/08, CP 지도 화면의 통계 위젯) ----
						.requestMatchers(HttpMethod.GET, "/api/dashboard/status-summary", "/api/dashboard/region-status").permitAll()
						// ---- 로그인 없이 조회 가능한 ITS CCTV·전광판 (지도 레이어, 지하차도 상세의 주변 CCTV, 전광판 표출 문구) ----
						.requestMatchers(HttpMethod.GET, "/api/its-cctvs", "/api/its-cctvs/nearby", "/api/its-vms", "/api/its-vms/*").permitAll()
						// ---- 로그인 없이 조회 가능한 날씨 (CP-08) ----
						.requestMatchers(HttpMethod.GET, "/api/weather").permitAll()
						// ---- 로그인 없이 조회 가능한 신월여의지하도로 공개 CCTV (서버가 서울터널 제공처를 대신 조회) ----
						.requestMatchers(HttpMethod.GET, "/api/tunnel-cctv/cameras", "/api/tunnel-cctv/cameras/*/stream").permitAll()
						.requestMatchers(HttpMethod.GET, "/api/navigation/places", "/api/navigation/directions", "/api/navigation/weather").permitAll()
						// ---- API 문서(Swagger UI) 화면과 문서 JSON. 화면만 열릴 뿐이고, 화면에서 실제 API를 호출할 때는
						//                  각 API의 로그인 규칙이 그대로 적용된다. 운영에서는 springdoc.swagger-ui.enabled=false 로 끈다 ----
						.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll()
						// ---- 로그인 없이 연결 가능한 실시간 스트림(SSE). 비로그인은 공개 이벤트만 받는다.
						//                  관리자 스트림(/api/admin/stream)은 여기 넣지 않아 로그인이 필요하다 ----
						.requestMatchers(HttpMethod.GET, "/api/stream").permitAll()

						// [개발 전용] 스텁 문자 인증코드 확인 (puf.sms-stub.expose-code=true 일 때만 컨트롤러가 있음)
						.requestMatchers(HttpMethod.GET, "/api/dev/sms/last").permitAll()
						// [임시] 개발용 테스트 API. dbtest 패키지를 지울 때 이 줄도 삭제
						.requestMatchers("/test/**").permitAll().requestMatchers("/error").permitAll()
						// ---- FastAPI 내부 API. 세션이 아니라 API 키(X-Internal-Api-Key)로 인증하며,
						//                  그 검사는 InternalApiKeyInterceptor(WebConfigurer에 등록)가 담당한다.
						//                  /api/internal/weather/** 도 이 아래로 그대로 포함된다 ----
						.requestMatchers("/api/internal/**").permitAll()

						// 그 밖의 모든 API는 기본적으로 로그인 필요 → 허용 목록에 빠뜨려도 노출되지 않는다.
						// 역할별 제한(고객 전용, 관리자 전용)은 각 컨트롤러 메서드의 @PreAuthorize 가 담당한다.
						.anyRequest().authenticated());
		return http.build();
	}

	// Spring Boot 가 자동으로 만드는 임시 계정(실행 때마다 "generated security password" 로그가 찍힘)을 막기
	// 위한 빈.
	// 로그인은 직접 만든 방식(CustomerService 등)으로 처리하므로 이 서비스는 쓰이지 않는다.
	@Bean
	public UserDetailsService userDetailsService() {
		return username -> {
			throw new UsernameNotFoundException("사용하지 않는 인증 방식입니다.");
		};
	}
}