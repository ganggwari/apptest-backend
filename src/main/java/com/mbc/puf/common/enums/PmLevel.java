package com.mbc.puf.common.enums;

/**
 * 예지보전 설비 단계 3단계 (DB: pm_level). 침수 위험도 5단계(RiskLevel)와 별개.
 * 예지보전 서버가 보낸 점수를 설비별 임계치(pm_threshold)와 비교해서 정한다.
 * 순서가 중요하다(ordinal 이 클수록 위험) — 상수 순서를 바꾸지 말 것.
 */
public enum PmLevel implements DbEnum {
    SAFE("안전"),
    CAUTION("주의"),
    DANGER("위험");

    private final String dbValue;

    PmLevel(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    /** 이 단계가 other 이상인지 */
    public boolean atLeast(PmLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
