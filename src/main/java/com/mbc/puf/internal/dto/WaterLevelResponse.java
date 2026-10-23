package com.mbc.puf.internal.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;

/** 수위 수신 결과. 판정된 단계와 자동 차단 여부를 같이 돌려줘서 연동 테스트 때 바로 확인할 수 있다 */
public record WaterLevelResponse(Long id, Long underpassId, BigDecimal levelCm, LocalDateTime measuredAt,
        RiskLevel riskLevel, UnderpassCurrentStatus status, boolean autoBlocked) {
}
