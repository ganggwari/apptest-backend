package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 예지보전 센서 조회 응답 (GET /api/internal/pm/sensors). 예지보전 서버(FastAPI)가 모델 입력 창을 만들 때 쓴다.
 * 장비마다 버킷(시간 구간) 목록을 주고, 버킷 안에는 센서 컬럼별 집계(count, sum, sumSq, min, max)가 들어 있다.
 * 평균 = sum / count, 표준편차(모집단) = sqrt(sumSq / count - 평균^2). 여러 버킷을 합칠 때는 count·sum·sumSq 를 더한다.
 */
public record PmSensorSeriesResponse(
        boolean truncated,              // limit 에 걸려 잘렸으면 true
        LocalDateTime nextAfter,        // 잘렸을 때 다음 호출의 after 값 (안 잘렸으면 null)
        List<Long> unknownEquipIds,     // 없거나 삭제된 장비 아이디
        List<Series> series) {

    /** 장비 1대의 버킷 목록 (버킷이 없으면 빈 목록) */
    public record Series(Long equipId, String name, String type, int bucketSec, List<Bucket> buckets) {
    }

    /** 버킷 1개. t = 버킷 시작 시각, n = 이 버킷에 들어온 원본 행 수, cols = 컬럼 이름 → 집계 */
    public record Bucket(LocalDateTime t, long n, Map<String, Stat> cols) {
    }

    /** 컬럼 1개의 집계. count = 값이 있는(NULL 아닌) 행 수. count 가 0 이면 나머지는 null */
    public record Stat(long count, Double sum, Double sumSq, Double min, Double max) {
    }
}
