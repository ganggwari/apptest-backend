package com.mbc.puf.common.enums;

/** 공지사항 분류 (DB: notice_category). 화면에는 label 을 그대로 보여주면 된다 */
public enum NoticeCategory {
    INFO("안내"),            // 일반 서비스 안내
    MAINTENANCE("점검"),     // 시스템 점검, 서비스 중단
    UPDATE("업데이트"),      // 앱·웹 기능 변경, 버전 안내
    URGENT("긴급");          // 즉시 알려야 하는 공지

    private final String label;

    NoticeCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
