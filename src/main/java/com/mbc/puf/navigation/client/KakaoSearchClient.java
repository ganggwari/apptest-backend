package com.mbc.puf.navigation.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.navigation.dto.NavigationPlaceResponse;

import tools.jackson.databind.JsonNode;

/** 카카오 로컬(장소·주소 검색)과 카카오모빌리티 길찾기(후보 경로)를 부르고 응답을 화면에서 쓰는 모양으로 돌려준다 */
@Component
public class KakaoSearchClient implements NaviSearchProvider {

    // 장소 검색 기준점: 부산 시청 (가까운 곳이 먼저 나오도록)
    private static final double BUSAN_LNG = 129.0756;
    private static final double BUSAN_LAT = 35.1796;

    private final RestClient restClient = RestClient.create();
    private final String restApiKey;
    private final String localUrl;
    private final String directionsUrl;

    public KakaoSearchClient(@Value("${puf.kakao.rest-key:}") String restApiKey,
                             @Value("${puf.kakao.local-url:https://dapi.kakao.com/v2/local}") String localUrl,
                             @Value("${puf.kakao.directions-url:https://apis-navi.kakaomobility.com/v1/directions}") String directionsUrl) {
        this.restApiKey = restApiKey;
        this.localUrl = localUrl;
        this.directionsUrl = directionsUrl;
    }

    @Override
    public List<NavigationPlaceResponse> searchPlaces(String query) {
        try {
            List<NavigationPlaceResponse> places = new ArrayList<>();
            URI keywordUri = UriComponentsBuilder.fromUriString(localUrl + "/search/keyword.json")
                    .queryParam("query", query).queryParam("size", 8)
                    .queryParam("x", BUSAN_LNG).queryParam("y", BUSAN_LAT)
                    .build().encode().toUri();
            JsonNode keyword = get(keywordUri);
            for (JsonNode d : documents(keyword)) {
                String road = d.path("road_address_name").asString("");
                String address = road.isBlank() ? d.path("address_name").asString("") : road;
                places.add(new NavigationPlaceResponse(d.path("id").asString(null), d.path("place_name").asString(""), address,
                        d.path("y").asDouble(), d.path("x").asDouble()));
            }
            if (!places.isEmpty()) {
                return places;
            }
            URI addressUri = UriComponentsBuilder.fromUriString(localUrl + "/search/address.json")
                    .queryParam("query", query).queryParam("size", 8)
                    .build().encode().toUri();
            for (JsonNode d : documents(get(addressUri))) {
                String name = d.path("address_name").asString("");
                places.add(new NavigationPlaceResponse(null, name, name, d.path("y").asDouble(), d.path("x").asDouble()));
            }
            return places;
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "장소 검색 서비스에 연결하지 못했습니다.");
        }
    }

    @Override
    public List<JsonNode> directions(String origin, String destination, String waypoint) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(directionsUrl)
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .queryParam("priority", "RECOMMEND");
        if (waypoint != null) {
            builder.queryParam("waypoints", waypoint);
        } else {
            builder.queryParam("alternatives", true);
        }
        JsonNode body;
        try {
            body = get(builder.build().encode().toUri());
        } catch (RestClientException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "길찾기 서비스에 연결하지 못했습니다.");
        }
        List<JsonNode> routes = new ArrayList<>();
        for (JsonNode r : body.path("routes")) {
            if (r.path("result_code").asInt(-1) == 0) {
                routes.add(r);
            }
        }
        return routes;
    }

    private JsonNode get(URI uri) {
        JsonNode body = restClient.get().uri(uri)
                .header("Authorization", "KakaoAK " + requireKey())
                .retrieve().body(JsonNode.class);
        if (body == null) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "검색 서비스 응답이 비어 있습니다.");
        }
        return body;
    }

    private static Iterable<JsonNode> documents(JsonNode body) {
        return body.path("documents");
    }

    private String requireKey() {
        if (restApiKey == null || restApiKey.isBlank()) {
            throw new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "카카오 키(puf.kakao.rest-key)가 설정되지 않았습니다.");
        }
        return restApiKey.trim();
    }
}
