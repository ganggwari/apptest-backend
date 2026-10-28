package com.mbc.puf.navigation.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.navigation.dto.HazardState;
import com.mbc.puf.navigation.dto.NavigationConfigResponse;
import com.mbc.puf.navigation.dto.NavigationPlaceResponse;
import com.mbc.puf.navigation.dto.RouteResult;
import com.mbc.puf.navigation.service.DetourService;
import com.mbc.puf.navigation.service.HazardService;
import com.mbc.puf.navigation.service.NavigationSearchService;
import com.mbc.puf.weather.dto.WeatherResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.JsonNode;

/**
 * 내비게이션(PWA) API.
 *  - /route, /hazards : 로그인한 고객/관리자만 (카카오 호출 한도 보호)
 *  - /config, /places, /directions, /weather : 로그인 없이 쓸 수 있는 화면용 조회 (places, directions 는 IP 별 분당 호출 제한)
 */
@Tag(name = "내비게이션", description = "장소 검색·후보 경로·날씨, 길찾기(위험 지하차도 우회), 위험 상태")
@RestController
@RequestMapping("/api/navigation")
public class NavigationController {

    private final DetourService detourService;
    private final HazardService hazardService;
    private final NavigationSearchService searchService;
    private final String kakaoJsKey;

    public NavigationController(DetourService detourService, HazardService hazardService,
                                NavigationSearchService searchService,
                                @Value("${puf.kakao.js-key:}") String kakaoJsKey) {
        this.detourService = detourService;
        this.hazardService = hazardService;
        this.searchService = searchService;
        this.kakaoJsKey = kakaoJsKey == null ? "" : kakaoJsKey.trim();
    }

    @Operation(summary = "길찾기 (위험 지하차도 우회)",
            description = "origin/destination 은 '경도,위도'. heading(도, 0~360)은 달리는 도중 재탐색할 때 현재 진행방향을 보내면 된다. "
                    + "경로에 차단(경계 이상)된 지하차도가 있으면 피하는 경로를 돌려주고(mandatory=true, detoured=true), "
                    + "관심·주의 단계 지하차도만 있으면 원래 경로를 주되 suggestedDetour 로 우회를 권한다. 로그인 필요.")
    @GetMapping("/route")
    public ApiResponse<RouteResult> route(@RequestParam(name = "origin") String origin,
                                          @RequestParam(name = "destination") String destination,
                                          @RequestParam(name = "heading", required = false) Double heading) {
        double[] o = parse(origin, "origin");
        double[] d = parse(destination, "destination");
        if (heading != null && (heading < 0 || heading > 360)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "heading 은 0~360 이어야 합니다.");
        }
        return ApiResponse.ok(detourService.find(o[1], o[0], d[1], d[0], heading));
    }

    @Operation(summary = "현재 위험 상태", description = "관심 이상이거나 차단 중인 지하차도 목록. 접속 직후 확인이나 폴링용(실시간 변화는 SSE data-changed navigation-hazard). version 이 바뀌면 상태가 바뀐 것. 로그인 필요.")
    @GetMapping("/hazards")
    public ApiResponse<HazardState> hazards() {
        return ApiResponse.ok(hazardService.state());
    }

    @Operation(summary = "내비 화면 공개 설정", description = "카카오 JavaScript 키(도메인 제한 키). REST 키는 절대 내려주지 않는다. 로그인 없이 사용 가능(지도 화면이 키를 읽는다).")
    @GetMapping("/config")
    public ApiResponse<NavigationConfigResponse> config() {
        return ApiResponse.ok(new NavigationConfigResponse(kakaoJsKey));
    }

    @Operation(summary = "장소 검색", description = "키워드로 출발지·도착지 후보를 찾는다(최대 8건). 키워드로 못 찾으면 주소로 다시 찾는다. 로그인 없이 사용 가능, IP 별 분당 호출 제한(429).")
    @GetMapping("/places")
    public ApiResponse<List<NavigationPlaceResponse>> places(@RequestParam(name = "q") String q,
                                                             HttpServletRequest request) {
        return ApiResponse.ok(searchService.places(q, request.getRemoteAddr()));
    }

    @Operation(summary = "후보 경로 목록", description = "origin/destination 은 '경도,위도'. waypoint(선택, '경도,위도')를 주면 그곳을 거쳐 가는 경로 하나를 준다. "
            + "응답 data 는 카카오 길찾기 routes 중 성공한 것을 그대로 담은 배열(summary, sections[].roads[].vertexes, sections[].guides[]). "
            + "위험 지하차도 우회 판단은 하지 않는다(그건 /route). 로그인 없이 사용 가능, IP 별 분당 호출 제한(429).")
    @GetMapping("/directions")
    public ApiResponse<List<JsonNode>> directions(@RequestParam(name = "origin") String origin,
                                                  @RequestParam(name = "destination") String destination,
                                                  @RequestParam(name = "waypoint", required = false) String waypoint,
                                                  HttpServletRequest request) {
        return ApiResponse.ok(searchService.directions(origin, destination, waypoint, request.getRemoteAddr()));
    }

    @Operation(summary = "도착지 날씨", description = "lat/lng 근처의 현재 날씨. /api/weather 와 같은 내용이며 내비 화면이 쓰던 주소를 맞춰 둔 것. 로그인 없이 사용 가능.")
    @GetMapping("/weather")
    public ApiResponse<WeatherResponse> weather(@RequestParam(name = "lat") BigDecimal lat,
                                                @RequestParam(name = "lng") BigDecimal lng) {
        return ApiResponse.ok(searchService.weather(lat, lng));
    }

    /** "경도,위도" -> {경도, 위도} */
    private static double[] parse(String text, String name) {
        try {
            String[] p = text.split(",");
            if (p.length != 2) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, name + " 은(는) '경도,위도' 형식이어야 합니다.");
            }
            double lng = Double.parseDouble(p[0].trim());
            double lat = Double.parseDouble(p[1].trim());
            if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, name + " 의 좌표 범위가 올바르지 않습니다.");
            }
            return new double[]{lng, lat};
        } catch (NumberFormatException e) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, name + " 에 숫자가 아닌 값이 있습니다: " + text);
        }
    }
}
