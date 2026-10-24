package com.mbc.puf.common.enums;

/** 예지보전 이벤트가 만들어진 이유 (DB: pm_trigger_type) */
public enum PmTriggerType implements DbEnum {
    DANGER_ENTRY("위험진입"),   // 점수가 위험 임계치 아래에서 위로 올라섬
    SURGE("급상승"),            // 주의 이상인 채로 24시간 최저 점수보다 일정 폭 이상 오름
    NO_DATA("미수신");          // 센서 데이터가 기준 시간 넘게 안 들어옴 (점수·단계 없음)

    private final String dbValue;

    PmTriggerType(String dbValue) {
        this.dbValue = dbValue;
    }

    @Override
    public String getDbValue() {
        return dbValue;
    }
}
