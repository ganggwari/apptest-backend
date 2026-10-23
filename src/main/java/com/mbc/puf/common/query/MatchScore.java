package com.mbc.puf.common.query;

import java.util.ArrayList;
import java.util.List;

import com.mbc.puf.common.util.LikePattern;

/**
 * 통합 검색 "일치 점수" 계산식(JPQL) 빌더.
 * 항목마다 정확히 같으면 3점, 앞부분이 같으면 2점, 포함되면 1점을 주고 모두 더한다.
 * 여러 항목에서 일치할수록, 더 정확하게 일치할수록 점수가 높아 위로 올라온다. (예전 매퍼 XML의 matchScore와 같은 규칙)
 */
public class MatchScore {

    private final QueryConditions conditions;
    private final List<String> parts = new ArrayList<>();

    public MatchScore(QueryConditions conditions) {
        this.conditions = conditions;
    }

    // 문자 항목: 대소문자 무시 (아이디·이름·이메일)
    public MatchScore text(LikePattern keyword, String path) {
        if (keyword != null) {
            parts.add(caseExpr("lower(" + path + ")",
                    keyword.getExact().toLowerCase(), keyword.getPrefix().toLowerCase(), keyword.getContains().toLowerCase()));
        }
        return this;
    }

    // 전화번호 항목: 하이픈을 지운 값끼리 비교 (keyword가 null이면 = 숫자가 아닌 검색어면 이 항목은 빠진다)
    public MatchScore phone(LikePattern keyword, String path) {
        if (keyword != null) {
            parts.add(caseExpr("replace(" + path + ", '-', '')",
                    keyword.getExact(), keyword.getPrefix(), keyword.getContains()));
        }
        return this;
    }

    // 하위 쿼리 등으로 직접 만든 점수식 추가
    public MatchScore raw(String expression) {
        parts.add(expression);
        return this;
    }

    // 직접 점수식을 만들 때 쓰는 공통 CASE 식 (target은 이미 소문자/정규화된 식이어야 함)
    public String caseExpr(String target, String exact, String prefix, String contains) {
        return "(case when " + target + " = " + conditions.param(exact)
                + " then 3 when " + target + " like " + conditions.param(prefix) + " escape '\\'"
                + " then 2 when " + target + " like " + conditions.param(contains) + " escape '\\'"
                + " then 1 else 0 end)";
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public String expression() {
        return "(" + String.join(" + ", parts) + ")";
    }
}
