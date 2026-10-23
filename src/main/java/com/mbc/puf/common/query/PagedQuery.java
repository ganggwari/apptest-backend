package com.mbc.puf.common.query;

import java.util.List;
import java.util.function.Function;

import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.response.PageResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

/**
 * 목록 조회 공통 실행기: 같은 WHERE 조건으로 "이번 페이지 행"과 "전체 개수"를 한 번에 뽑아 PageResponse로 만든다.
 *
 *   select : "select a, e.underpassId from Alert a join UnderpassEvent e on e.id = a.eventId"
 *   count  : "select count(a) from Alert a join UnderpassEvent e on e.id = a.eventId"
 *   orderBy: " order by a.sentAt desc, a.id desc"
 */
public final class PagedQuery {

    private PagedQuery() {
    }

    public static <R, T> PageResponse<T> fetch(EntityManager em, String select, String count,
                                               QueryConditions conditions, String orderBy,
                                               Class<R> rowType, Function<R, T> mapper, PageParams page) {
        TypedQuery<R> query = em.createQuery(select + conditions.where() + orderBy, rowType);
        conditions.applyTo(query);
        query.setFirstResult((int) page.getOffset());
        query.setMaxResults(page.getSize());
        List<T> items = query.getResultList().stream().map(mapper).toList();

        TypedQuery<Long> countQuery = em.createQuery(count + conditions.where(), Long.class);
        conditions.applyTo(countQuery);
        long total = countQuery.getSingleResult();

        return new PageResponse<>(items, page.getPage(), page.getSize(), total);
    }

    // 페이징 없이 전체 (개수가 고정적으로 적은 목록용)
    public static <R, T> List<T> fetchAll(EntityManager em, String select, QueryConditions conditions,
                                          String orderBy, Class<R> rowType, Function<R, T> mapper) {
        TypedQuery<R> query = em.createQuery(select + conditions.where() + orderBy, rowType);
        conditions.applyTo(query);
        return query.getResultList().stream().map(mapper).toList();
    }
}
