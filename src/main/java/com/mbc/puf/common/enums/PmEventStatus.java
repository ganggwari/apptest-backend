package com.mbc.puf.common.enums;

/** 예지보전 이벤트 처리 상태 (DB: pm_event_status) */
public enum PmEventStatus {
    CHECK_NEEDED,   // 확인필요
    IN_PROGRESS,    // 점검중
    RESOLVED,       // 조치완료
    ETC;            // 기타

    /** 아직 처리 중인 상태(확인필요·점검중)인지. 이 상태의 이벤트가 있으면 새 이벤트를 만들지 않는다 */
    public boolean isOpen() {
        return this == CHECK_NEEDED || this == IN_PROGRESS;
    }
}
