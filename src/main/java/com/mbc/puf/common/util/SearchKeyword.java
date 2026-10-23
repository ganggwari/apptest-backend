package com.mbc.puf.common.util;

/**
 * 통합 검색어 하나를 여러 항목에 맞는 형태로 나눠 둔다.
 *   text  : 아이디·이름·이메일 비교용 (입력한 그대로)
 *   phone : 전화번호 비교용 (하이픈·공백을 지운 값). 숫자만 입력했을 때만 사용한다 ("김" 같은 검색어로 전화번호를 찾지 않도록)
 *   car   : 차량번호 비교용 (공백을 지운 값)
 */
public final class SearchKeyword {

    private final LikePattern text;
    private final LikePattern phone;
    private final LikePattern car;

    private SearchKeyword(LikePattern text, LikePattern phone, LikePattern car) {
        this.text = text;
        this.phone = phone;
        this.car = car;
    }

    // 검색어가 비어 있으면 null (= 검색어 없이 전체 조회)
    public static SearchKeyword of(String raw) {
        LikePattern text = LikePattern.of(raw);
        if (text == null) {
            return null;
        }
        String digits = raw.replaceAll("[\\s-]", "");
        LikePattern phone = digits.matches("\\d+") ? LikePattern.of(digits) : null;
        return new SearchKeyword(text, phone, LikePattern.ofCar(raw));
    }

    public LikePattern getText() { return text; }
    public LikePattern getPhone() { return phone; }
    public LikePattern getCar() { return car; }
}
