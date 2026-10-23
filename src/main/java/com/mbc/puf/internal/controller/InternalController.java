package com.mbc.puf.internal.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.internal.dto.PlateLogCreateRequest;
import com.mbc.puf.internal.dto.PlateLogResponse;
import com.mbc.puf.internal.dto.RiskScoreCreateRequest;
import com.mbc.puf.internal.dto.RiskScoreResponse;
import com.mbc.puf.internal.dto.WaterLevelCreateRequest;
import com.mbc.puf.internal.dto.WaterLevelResponse;
import com.mbc.puf.internal.service.InternalIngestService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * FastAPI가 호출하는 내부 API. 세션 인증이 아니라 X-Internal-Api-Key 헤더로 인증하며,
 * 그 검사는 InternalApiKeyInterceptor(WebConfigurer에 등록)가 담당한다 —
 * SecurityConfig에서 이 경로는 permitAll로 열려 있는 대신 인터셉터가 대신 막는 구조
 */
@Tag(name = "내부 수신(FastAPI·수위계)", description = "X-Internal-Api-Key 헤더로 인증. 위험점수·수위·번호판 인식 결과 수신")
@RestController
@RequestMapping("/api/internal")
public class InternalController {

    private final InternalIngestService internalIngestService;

    public InternalController(InternalIngestService internalIngestService) {
        this.internalIngestService = internalIngestService;
    }

    // 위험도 예측 결과 수신
    @Operation(summary = "위험점수 수신 (침수모델)", description = "점수를 저장하고 임계치와 수위로 단계를 판정한다. 단계가 바뀌면 이벤트·관리자 알림·SSE 가 나간다(201).")
    @PostMapping("/risk-scores")
    public ResponseEntity<ApiResponse<RiskScoreResponse>> receiveRiskScore(
            @Valid @RequestBody RiskScoreCreateRequest request) {
        RiskScoreResponse response = internalIngestService.receiveRiskScore(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 위험도 미래 예측 수신 (한 지하차도의 앞으로 몇 시간치). 프런트의 위험점수 예측 그래프가 읽는다
    @Operation(summary = "위험점수 예측 수신 (침수모델)", description = "한 지하차도의 앞으로 몇 시간치 예측을 보낸다. 같은 시점은 덮어쓰고 지난 시점은 지운다(201). 프런트의 예측 그래프가 읽는다.")
    @PostMapping("/risk-forecasts")
    public ResponseEntity<ApiResponse<com.mbc.puf.internal.dto.RiskForecastIngestResponse>> receiveRiskForecast(
            @Valid @RequestBody com.mbc.puf.internal.dto.RiskForecastCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(internalIngestService.receiveRiskForecast(request)));
    }

    // 수위계 측정값 수신 (지하차도당 센서 1개의 단일값, cm). 수위가 차단 기준에 닿으면 이 호출 안에서 자동 차단까지 일어난다
    @Operation(summary = "수위 수신 (수위계)", description = "지하차도당 센서 1개의 단일값(cm). 수위가 차단 기준에 닿으면 이 호출 안에서 자동 차단(이벤트·전광판·경찰 이관·알림)까지 일어난다. 응답에 riskLevel, status, autoBlocked 가 있다.")
    @PostMapping("/water-levels")
    public ResponseEntity<ApiResponse<WaterLevelResponse>> receiveWaterLevel(
            @Valid @RequestBody WaterLevelCreateRequest request) {
        WaterLevelResponse response = internalIngestService.receiveWaterLevel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 번호판 인식 결과 수신. multipart/form-data로 받는다 —
    // "request" 파트(JSON, PlateLogCreateRequest)는 필수, "image" 파트(감지 순간 캡처 이미지)는 선택
    @Operation(summary = "번호판 인식 결과 수신 (OCR)", description = "multipart: request(JSON)는 필수, image 는 선택. 같은 카메라에서 같은 번호가 직전 30초 안에 저장돼 있으면 새로 저장하지 않고 먼저 저장된 기록을 돌려준다. 신뢰도가 낮으면 판별 대기열에 오른다.")
    @PostMapping(value = "/plate-logs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<PlateLogResponse>> receivePlateLog(
            @Valid @RequestPart("request") PlateLogCreateRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        PlateLogResponse response = internalIngestService.receivePlateLog(request, image);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    // 헬스체크. 저장용 테이블이 없어 우선 200 응답만 반환 (계약 3-3)
    @Operation(summary = "헬스체크", description = "키만 확인하고 200 OK 를 돌려준다.")
    @GetMapping("/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok("OK", null);
    }
}