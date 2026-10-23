package com.mbc.puf.internal.service;

import org.springframework.web.multipart.MultipartFile;

import com.mbc.puf.internal.dto.PlateLogCreateRequest;
import com.mbc.puf.internal.dto.PlateLogResponse;
import com.mbc.puf.internal.dto.RiskScoreCreateRequest;
import com.mbc.puf.internal.dto.RiskScoreResponse;
import com.mbc.puf.internal.dto.WaterLevelCreateRequest;
import com.mbc.puf.internal.dto.WaterLevelResponse;

/** FastAPI로부터 위험도/번호판 인식 결과를 받는 내부 API (/api/internal/**). 인증은 API 키 헤더로 처리 */
public interface InternalIngestService {

    // 위험도 예측 결과 저장
    RiskScoreResponse receiveRiskScore(RiskScoreCreateRequest request);

    // 위험도 미래 예측 저장 (지하차도 한 곳의 앞으로 몇 시간치, 같은 시점은 덮어쓰기)
    com.mbc.puf.internal.dto.RiskForecastIngestResponse receiveRiskForecast(
            com.mbc.puf.internal.dto.RiskForecastCreateRequest request);

    // 수위계 측정값 저장 + 단계 재판정(경계 이상이면 자동 차단)
    WaterLevelResponse receiveWaterLevel(WaterLevelCreateRequest request);

    // 번호판 인식 결과 저장. recognitionStatus 계산과 등록 차량 매칭까지 여기서 처리한다.
    // image는 FastAPI가 감지 순간 캡처한 프레임(선택, 없으면 null) — 수동 판별 화면에서 보여주기 위한 것
    PlateLogResponse receivePlateLog(PlateLogCreateRequest request, MultipartFile image);
}