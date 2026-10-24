package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.util.List;

import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.pm.entity.PmThreshold;

/** 점수 → 예지보전 단계 판정 (점수 수신, 임계치 수정 후 재판정에서 같이 쓴다) */
public final class PmJudge {

    // 임계치 행이 없는 설비에 쓰는 기본값 (프론트 표시 기준 0~39 안전 · 40~69 주의 · 70 이상 위험)
    public static final BigDecimal DEFAULT_CAUTION = new BigDecimal("40");
    public static final BigDecimal DEFAULT_DANGER = new BigDecimal("70");

    private PmJudge() {
    }

    /** 점수가 넘은 임계치 중 가장 높은 단계 */
    public static PmLevel judge(BigDecimal score, List<PmThreshold> rows) {
        if (rows == null || rows.isEmpty()) {
            return score.compareTo(DEFAULT_DANGER) >= 0 ? PmLevel.DANGER
                    : score.compareTo(DEFAULT_CAUTION) >= 0 ? PmLevel.CAUTION : PmLevel.SAFE;
        }
        PmLevel result = PmLevel.SAFE;
        for (PmThreshold t : rows) {
            if (score.compareTo(t.getMinScore()) >= 0 && t.getLevel().ordinal() > result.ordinal()) {
                result = t.getLevel();
            }
        }
        return result;
    }
}
