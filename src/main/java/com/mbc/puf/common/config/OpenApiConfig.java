package com.mbc.puf.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

/** Swagger UI(springdoc) 기본 정보. 화면은 /swagger-ui.html, 원본 JSON은 /v3/api-docs */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI pufOpenApi() {
        return new OpenAPI().info(new Info()
                .title("PUF 지하차도 침수예방 API")
                .version("v1")
                .description("공통 응답은 ApiResponse{success, message, data}, 실패해도 HTTP 상태코드는 그대로 유지됩니다.\n\n"
                        + "로그인은 세션 쿠키 방식입니다. 관리자 API를 시험하려면 먼저 POST /api/managers/login 을 호출하세요. "
                        + "같은 브라우저에서 쿠키(JSESSIONID)가 자동으로 붙어 이후 호출이 인증됩니다."));
    }
}
