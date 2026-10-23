package com.mbc.puf.common.util;

/** 이메일 일부를 * 로 가린다. 예) hong@company.com → hon*@company.com */
public final class EmailMasker {

    private static final int VISIBLE_LENGTH = 3;   // 로컬파트 앞부분을 몇 글자 보여줄지

    private EmailMasker() {
    }

    public static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";                                   // 형식이 이상하면 아무것도 보여주지 않는다
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);                // '@' 포함, 도메인은 그대로 노출

        // 로컬파트가 3글자 이하이면 3글자를 보여줄 경우 전체가 드러나므로 첫 글자만 보여준다
        int visible = local.length() > VISIBLE_LENGTH ? VISIBLE_LENGTH : 1;
        return local.substring(0, visible) + "*".repeat(local.length() - visible) + domain;
    }
}
