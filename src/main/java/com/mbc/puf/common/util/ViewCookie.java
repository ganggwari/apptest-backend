package com.mbc.puf.common.util;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 조회수 중복 증가 방지용 쿠키.
 * 브라우저가 이미 본 글 번호를 쿠키에 담아 두고, 같은 글을 다시 열면 조회수를 올리지 않는다.
 * 서버는 아무것도 기억하지 않는다(재시작해도 유지). 쿠키를 지우거나 시크릿 창이면 다시 올라간다.
 * 쿠키 하나에 최근 본 글 번호를 최대 MAX_IDS 개까지 "3-5-9" 형태로 담고, 하루(24시간) 뒤 사라진다.
 */
public final class ViewCookie {

    private static final int MAX_IDS = 100;
    private static final int MAX_AGE_SECONDS = 24 * 60 * 60;

    private ViewCookie() {
    }

    /**
     * 이 글을 처음 보는 거면 true(조회수를 올려야 함)이고 쿠키에 기록한다. 이미 봤으면 false.
     * @param cookieName 글 종류별 쿠키 이름 (예: viewed_notice, viewed_faq)
     */
    public static boolean firstView(HttpServletRequest request, HttpServletResponse response,
                                    String cookieName, Long id) {
        List<String> seen = new ArrayList<>();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    for (String part : c.getValue().split("-")) {
                        if (part.matches("\\d{1,18}")) {   // 숫자만 인정 (조작된 값 무시)
                            seen.add(part);
                        }
                    }
                }
            }
        }
        String key = String.valueOf(id);
        if (seen.contains(key)) {
            return false;
        }
        seen.add(key);
        if (seen.size() > MAX_IDS) {
            seen = seen.subList(seen.size() - MAX_IDS, seen.size());   // 오래된 것부터 버림
        }
        response.addHeader("Set-Cookie", cookieName + "=" + String.join("-", seen)
                + "; Path=/; Max-Age=" + MAX_AGE_SECONDS + "; HttpOnly; SameSite=Lax");
        return true;
    }
}
