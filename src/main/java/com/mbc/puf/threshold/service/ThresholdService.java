package com.mbc.puf.threshold.service;

import java.util.List;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.threshold.dto.ThresholdResponse;
import com.mbc.puf.threshold.dto.ThresholdUpdateRequest;

/** 지하차도별 위험도 임계치 (MP-19). 조회는 관리자 전체, 수정은 최고관리자 전용 */
public interface ThresholdService {

    // 특정 지하차도의 임계치 3행 (SAFE/WARNING/DANGER, risk_score_min 오름차순)
    List<ThresholdResponse> getThresholds(LoginUser loginUser, Long underpassId);

    // 특정 단계의 risk_score_min 수정. 변경 이력이 threshold_history에 자동 기록된다
    ThresholdResponse updateThreshold(LoginUser loginUser, Long underpassId, RiskLevel level,
                                       ThresholdUpdateRequest request);

    // 지하차도 등록 시 UnderpassServiceImpl이 호출해서 기본 임계치 3행(SAFE/WARNING/DANGER)을 만든다.
    // 최초 생성분도 threshold_history에 기록된다(old값 null)
    void createDefaultThresholds(Long underpassId, Long createdBy);
}
