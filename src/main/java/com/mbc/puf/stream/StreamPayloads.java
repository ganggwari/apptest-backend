package com.mbc.puf.stream;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mbc.puf.common.enums.CustomerNotificationCategory;
import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.enums.ReviewStatus;
import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;

/**
 * SSE 이벤트로 내려가는 내용(JSON)을 모아둔 곳. 이벤트 이름은 각 record 위 주석에 적어 둔다.
 * 시각은 기존 화면용 DTO 와 같이 "yyyy-MM-dd HH:mm" 형식.
 */
public final class StreamPayloads {

    private StreamPayloads() {
    }

    private static final String DT = "yyyy-MM-dd HH:mm";

    /** [risk-update] 위험점수가 갱신될 때마다(등급이 그대로여도) 공개. 목록/상세의 점수를 바로 바꾸는 용도 */
    public record RiskUpdate(Long underpassId, BigDecimal riskScore, RiskLevel riskLevel, BigDecimal rainfall1hMm,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DT) LocalDateTime predictedAt) {
    }

    /** [risk-level-change] 위험 등급이 바뀐 순간에만 공개. direction 은 UP / DOWN */
    public record RiskLevelChange(Long underpassId, String underpassName, RiskLevel from, RiskLevel to,
            String direction, BigDecimal riskScore) {
    }

    /**
     * [data-changed] 목록·상세 데이터가 바뀌었으니 다시 조회하라는 신호. 내용(본문)은 담지 않는다 —
     * 받은 쪽이 기존 조회 API 를 다시 호출하므로 권한 검사와 응답 형식이 그대로 적용된다.
     * resource : fire-transfer / threshold / camera / underpass / plate-log / inquiry / faq / notice
     * action   : CREATE / UPDATE / DELETE / REORDER
     * id       : 바뀐 대상의 id (threshold 는 지하차도 id, faq 순서 변경은 null)
     * regionId : 관련 지역 (지역 구분이 없으면 null)
     */
    public record DataChanged(String resource, String action, Long id, Long regionId) {
    }

    /** [water-update] 수위 측정값이 들어올 때마다 공개. riskLevel 은 그 값을 반영한 현재 단계 */
    public record WaterUpdate(Long underpassId, BigDecimal waterCm, RiskLevel riskLevel,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DT) LocalDateTime measuredAt) {
    }

    /** [control-change] 차단/해제 때 공개. status 는 BLOCKED / NORMAL */
    public record ControlChange(Long underpassId, String underpassName, UnderpassCurrentStatus status,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DT) LocalDateTime changedAt) {
    }

    /** [transit-update] (관리자) 통과 차량 변화. action: ENTRY(입구 통과) / EXIT(출구 통과) / REMAINING(차단으로 잔류 처리) */
    public record TransitUpdate(Long underpassId, String action, String direction, String carNum) {
    }

    /** [plate-queue-add] / [plate-queue-done] (관리자) 번호판 수동 판별 대기열. done 의 reviewStatus 는 CONFIRMED / AUTO_PROCESSED.
 *  remainingSeconds 는 add 일 때만 값이 있는 마감까지 남은 초(타이머용) */
    public record PlateQueue(Long plateLogId, Long underpassId, Long cameraId, String detectedNum, BigDecimal confidence,
            ReviewStatus reviewStatus,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime reviewDeadline,
            Long remainingSeconds) {
    }

    /** 판별대기 건의 마감까지 남은 초 (DB 시계 기준 now 와 비교). 대기 상태가 아니거나 마감 지났으면 0 */
    public static long remainingSeconds(ReviewStatus status, LocalDateTime deadline, LocalDateTime now) {
        if (status != ReviewStatus.PENDING || deadline == null) {
            return 0;
        }
        return Math.max(0, java.time.Duration.between(now, deadline).getSeconds());
    }

    /** [notice-new] 새 공지가 등록됨 (공개). 로그인 고객은 알림 개수를 다시 조회하면 된다 */
    public record NoticeNew(Long noticeId, String title, boolean important) {
    }

    /** [inquiry-new] (관리자) 새 1:1 문의 접수 */
    public record InquiryNew(Long inquiryId, String title,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DT) LocalDateTime createdAt) {
    }

    /** [display-change] (관리자) 전광판(its_vms) 페이지 생성/수정/삭제. action: CREATE / UPDATE / DELETE */
    public record DisplayChange(Long vmsId, Long underpassId, Integer pageNo, String action) {
    }

    /** [notification-new] (고객 본인) 내 알림이 도착함. unread 는 도착 직후의 안 읽은 개수 */
    public record CustomerNotificationNew(Long id, CustomerNotificationCategory category, String title, String body,
            String refType, Long refId,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DT) LocalDateTime createdAt,
            NotificationUnreadResponse unread) {
    }

    /** [notification-new] (관리자) 새 관리자 알림. 읽음은 관리자별이라 개수는 REST(unread-count)로 다시 조회한다. urgent=true 면 긴급 팝업을 띄운다 */
    public record ManagerNotificationNew(Long id, ManagerNotificationCategory category, Long regionId, String title,
            String body, String refType, Long refId, boolean urgent,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DT) LocalDateTime createdAt) {
    }
}