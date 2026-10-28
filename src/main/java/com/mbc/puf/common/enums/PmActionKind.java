package com.mbc.puf.common.enums;

/** 예지보전 조치 기록의 종류 (DB: pm_action_kind). 점검 상태를 바꾸면서 남긴 기록인지, 상태 변화 없이 덧붙인 기록인지 구분한다 */
public enum PmActionKind {
    START("점검 시작"),     // 점검중으로 바꾸면서 남긴 기록
    RECORD("기록 추가"),    // 상태는 그대로 두고 덧붙인 기록
    RESOLVED("조치 완료"),  // 조치완료로 끝내면서 남긴 기록
    ETC("기타 종료");       // 기타로 끝내면서 남긴 기록

    private final String label;

    PmActionKind(String label) {
        this.label = label;
    }

    /** 화면에 보일 한글 이름 */
    public String getLabel() {
        return label;
    }
}
