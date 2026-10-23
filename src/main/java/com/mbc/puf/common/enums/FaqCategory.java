package com.mbc.puf.common.enums;

/** FAQ 분류 (DB: faq_category). 화면에는 label 을 그대로 보여주면 된다 */
public enum FaqCategory {
    USAGE("이용 방법"),
    NOTIFICATION("알림·푸시"),
    VEHICLE("차량·번호판"),
    ACCOUNT("계정"),
    ETC("기타");

    private final String label;

    FaqCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
