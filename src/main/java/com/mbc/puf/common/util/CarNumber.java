package com.mbc.puf.common.util;

import java.util.regex.Pattern;

/**
 * 차량번호 정리·형식 검사.
 *  - 공백은 모두 지우고 저장한다 ("12가 3456" → "12가3456")
 *  - 허용 형식: 신형 2~3자리 숫자 + 한글 1자 + 4자리 (12가3456, 123가4567)
 *              구형 시·도명 + 2자리 숫자 + 한글 1자 + 4자리 (서울12가3456)
 */
public final class CarNumber {

    private static final String REGIONS = "서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주";
    private static final String HANGUL = "[가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호배육해공국합아바사자]";
    private static final Pattern PATTERN =
            Pattern.compile("^((" + REGIONS + ")\\d{2}|\\d{2,3})" + HANGUL + "\\d{4}$");

    private CarNumber() {
    }

    // 공백(띄어쓰기·탭 등) 제거. null 이면 null
    public static String normalize(String raw) {
        return raw == null ? null : raw.replaceAll("\\s+", "");
    }

    // normalize 한 값이 형식에 맞는지
    public static boolean isValid(String normalized) {
        return normalized != null && PATTERN.matcher(normalized).matches();
    }
}
