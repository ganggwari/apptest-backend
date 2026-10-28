package com.mbc.puf.plate.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mbc.puf.plate.entity.PlateLog;

public interface PlateLogRepository extends JpaRepository<PlateLog, Long> {

    // Same camera, previous valid OCR results only; current result has not been inserted yet.
    @Query(value = """
            SELECT AVG(confidence) FROM (
                SELECT confidence FROM plate_log
                WHERE camera_id = :cameraId AND detected_at <= :detectedAt
                  AND confidence BETWEEN 0 AND 1 AND recog_status <> 'FAIL'
                ORDER BY detected_at DESC, id DESC LIMIT 100
            ) recent
            """, nativeQuery = true)
    java.math.BigDecimal recentConfidenceAverage(@Param("cameraId") Long cameraId,
                                                 @Param("detectedAt") LocalDateTime detectedAt);


    // 중복 인식 판단용: 같은 카메라에서 같은 번호가 from~to 사이에 이미 저장돼 있는지 (가장 최근 1건)
    java.util.Optional<PlateLog> findFirstByCameraIdAndDetectedNumAndDetectedAtBetweenOrderByDetectedAtDesc(
            Long cameraId, String detectedNum, LocalDateTime from, LocalDateTime to);

    // 마감이 지난 판별대기 건의 [판별기록 id, 지하차도 id, 지역 id] (자동처리 전에 조회해 화면에 알리기 위함)
    @Query("select p.id, u.id, u.regionId from PlateLog p, Camera c, Underpass u"
            + " where c.id = p.cameraId and u.id = c.underpassId"
            + " and p.reviewStatus = com.mbc.puf.common.enums.ReviewStatus.PENDING and p.reviewDeadline < :now")
    java.util.List<Object[]> findExpiredPending(@Param("now") LocalDateTime now);

    // 판별대기(PENDING) 중 마감(review_due)이 지난 건을 AUTO_PROCESSED로 일괄 처리 (스케줄러 전용)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PlateLog p set p.reviewStatus = com.mbc.puf.common.enums.ReviewStatus.AUTO_PROCESSED, p.reviewedAt = :now"
            + " where p.reviewStatus = com.mbc.puf.common.enums.ReviewStatus.PENDING and p.reviewDeadline < :now")
    int autoProcessExpired(@Param("now") LocalDateTime now);
}