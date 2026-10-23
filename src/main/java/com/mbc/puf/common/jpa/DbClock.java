package com.mbc.puf.common.jpa;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;

/**
 * DB 서버 시계(LOCALTIMESTAMP, 세션 타임존 Asia/Seoul) 기준 현재 시각.
 * 개발 규칙상 시간 비교·기록은 전부 DB 시계 기준이라, 예전 SQL에서 NOW()를 쓰던 자리에 이 값을 넣는다.
 * (애플리케이션 서버와 DB 서버의 시계/타임존이 달라도 기준이 흔들리지 않게 하기 위함)
 */
@Component
public class DbClock {

    private final EntityManager em;

    public DbClock(EntityManager em) {
        this.em = em;
    }

    public LocalDateTime now() {
        Object value = em.createNativeQuery("SELECT LOCALTIMESTAMP").getSingleResult();
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return (LocalDateTime) value;
    }
}
