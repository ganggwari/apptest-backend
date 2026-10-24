package com.mbc.puf.cctv.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.mbc.puf.common.exception.BusinessException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 서울터널(신월여의지하도로) 공개 교통 페이지가 쓰는 읽기 전용 POST 주소를 대신 불러 준다.
 * 프런트가 Vite 프록시(/tunnel-source)로 직접 부르던 것을 서버가 맡는다.
 *  - getCctv-data.do     (lvl=1)        : CCTV 목록
 *  - getCctvInfo-data.do (cctvId=...)   : 한 대의 영상 주소 등 상세
 */
@Component
public class SeoulTunnelClient {

    private static final String PAGE_URL = "https://seoultunnel.co.kr/usage/traffic.do";
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; PUF-Backend)";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RestClient restClient;
    private final String baseUrl;

    public SeoulTunnelClient(@Value("${puf.tunnel-cctv.base-url:https://seoultunnel.co.kr/usage}") String baseUrl,
                             @Value("${puf.tunnel-cctv.timeout-seconds:10}") int timeoutSeconds) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 공개 CCTV 아이디 목록 (중복 제거, 목록 순서 유지) */
    public List<String> fetchCameraIds() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("lvl", "1");
        JsonNode list = post("getCctv-data.do", form).path("list");
        if (!list.isArray()) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "공개 CCTV 목록을 확인하지 못했습니다.");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode row : list) {
            String id = row.path("CCTV_ID").asString("").trim();
            if (!id.isEmpty()) {
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }

    /** 한 대의 상세. 아이디가 다르거나 영상 주소가 없으면 null (제공처가 모르는 CCTV) */
    public CameraInfo fetchCameraInfo(String id) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("cctvId", id);
        JsonNode info = post("getCctvInfo-data.do", form).path("info");
        String streamUrl = info.path("STREAMING2_ADRES").asString("").trim();
        if (info.isMissingNode() || info.isNull() || !id.equals(info.path("CCTV_ID").asString("").trim())
                || streamUrl.isEmpty()) {
            return null;
        }
        return new CameraInfo(id, info.path("CCTV_NM").asString("").trim(), info.path("DRC").asString("").trim(),
                streamUrl);
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        String text;
        try {
            text = restClient.post().uri(baseUrl + "/" + path)
                    .contentType(new MediaType(MediaType.APPLICATION_FORM_URLENCODED, StandardCharsets.UTF_8))
                    .header("Referer", PAGE_URL)
                    .header("User-Agent", USER_AGENT)
                    .body(form)
                    .retrieve().body(String.class);
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "신월여의지하도로 CCTV 제공처에 연결하지 못했습니다.");
        }
        try {
            return mapper.readTree(text == null ? "" : text);
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "신월여의지하도로 CCTV 제공처의 응답을 확인하지 못했습니다.");
        }
    }

    /** 제공처 원본 상세. rawStreamUrl 은 아직 검증·변환 전 주소 */
    public record CameraInfo(String id, String name, String direction, String rawStreamUrl) {}
}
