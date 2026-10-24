package com.mbc.puf.demo;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * 시연용 가짜 침수모델·수위계가 서버 자신의 내부 API(/api/internal/**)를 부르는 클라이언트.
 * 실제 FastAPI·수위계가 호출하는 것과 똑같이 HTTP + X-Internal-Api-Key 로 호출하므로
 * 나중에 진짜로 바꿔도 받는 쪽 코드는 그대로다.
 */
@Component
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoInternalApi {

    private final Environment env;
    private final String apiKey;
    private volatile RestClient client;

    public DemoInternalApi(Environment env, @Value("${puf.internal.api-key}") String apiKey) {
        this.env = env;
        this.apiKey = apiKey;
    }

    // 서버 포트는 기동 후에 정해지므로 처음 쓸 때 만든다
    private RestClient client() {
        if (client == null) {
            String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
            client = RestClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .defaultHeader("X-Internal-Api-Key", apiKey)
                    .build();
        }
        return client;
    }

    /** GET /api/internal/weather/underpasses/{id}/rainfall → data 부분 */
    public JsonNode rainfall(Long underpassId) {
        JsonNode body = client().get().uri("/api/internal/weather/underpasses/{id}/rainfall", underpassId)
                .retrieve().body(JsonNode.class);
        return body == null ? null : body.get("data");
    }

    /** GET /api/internal/weather/underpasses/{id}/forecast → data 부분(배열) */
    public JsonNode forecast(Long underpassId) {
        JsonNode body = client().get().uri("/api/internal/weather/underpasses/{id}/forecast", underpassId)
                .retrieve().body(JsonNode.class);
        return body == null ? null : body.get("data");
    }

    public void postRiskScore(Map<String, Object> body) {
        post("/api/internal/risk-scores", body);
    }

    public void postRiskForecast(Map<String, Object> body) {
        post("/api/internal/risk-forecasts", body);
    }

    public void postWaterLevel(Map<String, Object> body) {
        post("/api/internal/water-levels", body);
    }

    /** GET /api/internal/pm/equipments → data 배열 */
    public JsonNode pmEquipments() {
        JsonNode body = client().get().uri("/api/internal/pm/equipments").retrieve().body(JsonNode.class);
        return body == null ? null : body.get("data");
    }

    /** POST /api/internal/pm-scores (예지보전 서버가 하는 것과 같은 점수 수신) */
    public void postPmScores(Map<String, Object> body) {
        post("/api/internal/pm-scores", body);
    }

    private void post(String path, Map<String, Object> body) {
        client().post().uri(path).header("Content-Type", "application/json").body(body).retrieve().toBodilessEntity();
    }
}
