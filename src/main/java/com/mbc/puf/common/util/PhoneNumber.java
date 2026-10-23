package com.mbc.puf.common.util;

/** 전화번호 정리·마스킹. DB에는 하이픈·공백 없이 숫자만 저장한다 */
public final class PhoneNumber {

    private PhoneNumber() {
    }

    // 하이픈·공백 제거. 비어 있으면 null
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[-\\s]", "");
        return digits.isEmpty() ? null : digits;
    }

    // 01012345678 → 010-****-5678 (앞 3자리와 끝 4자리만 보여준다)
    public static String mask(String phone) {
        String digits = normalize(phone);
        if (digits == null || digits.length() < 8) {
            return "****";
        }
        String head = digits.substring(0, 3);
        String tail = digits.substring(digits.length() - 4);
        return head + "-" + "*".repeat(digits.length() - 7) + "-" + tail;
    }
}
