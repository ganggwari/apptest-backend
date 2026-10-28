package com.mbc.puf.internal.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.exception.BusinessException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
public class RiskHistoryBackfillServiceImpl implements RiskHistoryBackfillService {

    // 점수 모양: 과거 시작점은 현재 점수의 88%, 지금에 가까울수록 100%. 그 위에 지하차도마다 다른 완만한 흔들림을 더한다.
    // 현재 점수 캐시가 0 이면(DB 를 막 만든 직후) 평소 점수 :calmBase 를 기준으로 삼는다.
    // 강수량 칸은 NULL 로 둔다 (스키마가 허용).
    private static final String INSERT_SQL = """
            WITH target AS (
                SELECT u.id AS underpass_id,
                       CASE WHEN u.risk_score > 0 THEN CAST(u.risk_score AS double precision)
                            ELSE CAST(:calmBase AS double precision) END AS base,
                       COALESCE(f.first_at, LOCALTIMESTAMP(0)) AS until_at
                  FROM underpass u
                  CROSS JOIN LATERAL (SELECT MIN(r.predicted_at) AS first_at
                                        FROM risk_score r WHERE r.underpass_id = u.id) f
                 WHERE u.is_deleted = FALSE
                   AND (f.first_at IS NULL
                        OR f.first_at > LOCALTIMESTAMP(0) - make_interval(hours => CAST(:hours AS integer))
                                        + make_interval(mins => CAST(:stepMin AS integer)))
            )
            INSERT INTO risk_score (underpass_id, score, predicted_at)
            SELECT t.underpass_id,
                   ROUND(CAST(LEAST(100, GREATEST(0,
                         t.base * (0.88 + 0.12 * (EXTRACT(EPOCH FROM (g.at - (t.until_at - make_interval(hours => CAST(:hours AS integer)))))
                                                  / EXTRACT(EPOCH FROM make_interval(hours => CAST(:hours AS integer)))))
                         + 2.0 * sin(EXTRACT(EPOCH FROM g.at) / 1500.0 + t.underpass_id)
                         + 1.2 * sin(EXTRACT(EPOCH FROM g.at) / 520.0 + t.underpass_id * 3)
                   )) AS numeric), 2),
                   g.at
              FROM target t
              CROSS JOIN LATERAL generate_series(
                    date_bin(make_interval(mins => CAST(:stepMin AS integer)), t.until_at, TIMESTAMP '2000-01-01')
                        - make_interval(hours => CAST(:hours AS integer)),
                    date_bin(make_interval(mins => CAST(:stepMin AS integer)), t.until_at, TIMESTAMP '2000-01-01'),
                    make_interval(mins => CAST(:stepMin AS integer))) AS g(at)
             WHERE g.at < t.until_at
            """;

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public int backfill(int hours, int stepMinutes, double calmBase) {
        if (hours < 1 || hours > 72) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "hours는 1~72 사이여야 합니다.");
        }
        if (stepMinutes < 1 || stepMinutes > 60) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "stepMinutes는 1~60 사이여야 합니다.");
        }
        // 센서 스텁 및 다른 백필과 같은 트랜잭션 잠금으로 동시에 두 번 돌지 않게 한다.
        em.createNativeQuery("SELECT TRUE FROM pg_advisory_xact_lock(731905211)").getSingleResult();
        return em.createNativeQuery(INSERT_SQL)
                .setParameter("hours", hours)
                .setParameter("stepMin", stepMinutes)
                .setParameter("calmBase", calmBase)
                .executeUpdate();
    }
}
