package com.mbc.puf.common.util;

/**
 * 검색어 하나를 SQL LIKE 비교에 쓰는 세 가지 형태로 만든다.
 *   exact    : 원문 (LOWER(컬럼) = LOWER(exact) 로 대소문자 무시 동일 비교)
 *   prefix   : 앞부분 일치용  (값%)
 *   contains : 포함 일치용   (%값%)
 * 검색어에 들어 있는 % _ \ 는 일반 문자로 취급되도록 이스케이프한다 (안 그러면 "%" 한 글자로 전체가 검색된다).
 */
public final class LikePattern {

    private final String exact;
    private final String prefix;
    private final String contains;

    private LikePattern(String exact, String prefix, String contains) {
        this.exact = exact;
        this.prefix = prefix;
        this.contains = contains;
    }

    // 비어 있으면 null (= 이 조건은 사용하지 않음)
    public static LikePattern of(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        String escaped = escape(value);
        return new LikePattern(value, escaped + "%", "%" + escaped + "%");
    }

    // 전화번호: 하이픈·공백을 지운 값 (DB 쪽도 REPLACE(phone, '-', '') 로 같은 형태로 비교한다)
    public static LikePattern ofPhone(String raw) {
        return raw == null ? null : of(raw.replaceAll("[\\s-]", ""));
    }

    // 차량번호: 공백을 지운 값 (DB 쪽도 REPLACE(car_num, ' ', '') 로 비교한다)
    public static LikePattern ofCar(String raw) {
        return raw == null ? null : of(raw.replaceAll("\\s", ""));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public String getExact() { return exact; }
    public String getPrefix() { return prefix; }
    public String getContains() { return contains; }
}
