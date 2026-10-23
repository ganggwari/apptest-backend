package com.mbc.puf.common.enums;

/**
 * 침수 위험도 5단계 (DB: risk_level). 행안부 표준 단계에 맞춘다.
 * 안전 / 관심 / 주의 는 위험도 점수와 수위로, 경계 / 심각 은 수위로만 정해진다.
 * 경계부터는 자동 차단 대상이다.
 * 순서가 중요하다(ordinal 이 클수록 위험) — 상수 순서를 바꾸지 말 것.
 */
public enum RiskLevel implements DbEnum {
    SAFE("안전"),
    WATCH("관심"),
    CAUTION("주의"),
    ALERT("경계"),
    SEVERE("심각");

    private final String dbValue;

    RiskLevel(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }

    /** 이 단계가 other 이상인지 */
    public boolean atLeast(RiskLevel other) {
        return this.ordinal() >= other.ordinal();
    }

    public static RiskLevel max(RiskLevel a, RiskLevel b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}