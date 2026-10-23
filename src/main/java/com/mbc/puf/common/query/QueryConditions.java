package com.mbc.puf.common.query;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.util.LikePattern;

import jakarta.persistence.Query;

/**
 * 목록 조회용 동적 WHERE 절 빌더 (JPQL). 값이 비어 있으면 그 조건은 자동으로 빠진다.
 * MyBatis XML의 &lt;if test="x != null"&gt;AND ...&lt;/if&gt; 를 자바 코드로 옮긴 것.
 *
 *   QueryConditions c = new QueryConditions()
 *       .add("u.deleted = false")
 *       .eqIfPresent("u.regionId", regionId)
 *       .containsIfPresent(keyword, "u.name")
 *       .periodIfPresent("a.sentAt", period);
 *
 * 파라미터 이름은 :p1, :p2 ... 로 자동으로 붙는다.
 */
public class QueryConditions {

    private final List<String> clauses = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();
    private int seq;

    // 항상 붙는 조건 (예: "u.deleted = false")
    public QueryConditions add(String clause) {
        clauses.add(clause);
        return this;
    }

    // 값과 함께 항상 붙는 조건. clause 안의 "?" 자리에 값이 들어간다 (예: "p.reviewStatus = ?")
    public QueryConditions add(String clause, Object value) {
        clauses.add(clause.replace("?", ":" + bind(value)));
        return this;
    }

    // value != null 일 때만: path = value
    public QueryConditions eqIfPresent(String path, Object value) {
        if (value != null) {
            clauses.add(path + " = :" + bind(value));
        }
        return this;
    }

    // values != null 일 때만: path in (values)  (지역 범위처럼 여러 값 중 하나)
    public QueryConditions inIfPresent(String path, java.util.Collection<?> values) {
        if (values != null) {
            clauses.add(path + " in (:" + bind(values) + ")");
        }
        return this;
    }

    // condition이 true 일 때만 clause 추가 (값 없는 조건)
    public QueryConditions addIf(boolean condition, String clause) {
        if (condition) {
            clauses.add(clause);
        }
        return this;
    }

    // 검색어가 있을 때만: 지정한 항목 중 하나라도 검색어를 포함하면 통과 (대소문자 무시, %·_ 이스케이프)
    public QueryConditions containsIfPresent(String raw, String... paths) {
        LikePattern pattern = LikePattern.of(raw);
        if (pattern != null) {
            String p = bind(pattern.getContains().toLowerCase());
            List<String> ors = new ArrayList<>();
            for (String path : paths) {
                ors.add("lower(" + path + ") like :" + p + " escape '\\'");
            }
            clauses.add("(" + String.join(" or ", ors) + ")");
        }
        return this;
    }

    // 차량번호 검색어가 있을 때만: 공백을 무시하고 포함 검색 (예: "12가 3456" 으로도 "12가3456"을 찾음)
    public QueryConditions carNumIfPresent(String raw, String... paths) {
        LikePattern pattern = LikePattern.ofCar(raw);
        if (pattern != null) {
            String p = bind(pattern.getContains());
            List<String> ors = new ArrayList<>();
            for (String path : paths) {
                ors.add("replace(" + path + ", ' ', '') like :" + p + " escape '\\'");
            }
            clauses.add("(" + String.join(" or ", ors) + ")");
        }
        return this;
    }

    // 기간이 있을 때만: startDate 00:00 이상 ~ endDate 다음날 00:00 미만 (endDate 당일 포함)
    public QueryConditions periodIfPresent(String path, PeriodQuery period) {
        if (period == null) {
            return this;
        }
        LocalDateTime from = period.startAt();
        LocalDateTime to = period.endAtExclusive();
        if (from != null) {
            clauses.add(path + " >= :" + bind(from));
        }
        if (to != null) {
            clauses.add(path + " < :" + bind(to));
        }
        return this;
    }

    // SELECT/ORDER BY 절에서도 쓸 값을 등록하고 ":pN" 자리표시자를 돌려준다 (예: 일치 점수 계산식)
    // 같은 값을 WHERE 에서도 쓰면 count 쿼리에도 그대로 적용된다
    public String param(Object value) {
        return ":" + bind(value);
    }

    // " where a and b ..." (조건이 하나도 없으면 빈 문자열)
    public String where() {
        return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
    }

    public void applyTo(Query query) {
        params.forEach(query::setParameter);
    }

    private String bind(Object value) {
        String name = "p" + (++seq);
        params.put(name, value);
        return name;
    }
}
