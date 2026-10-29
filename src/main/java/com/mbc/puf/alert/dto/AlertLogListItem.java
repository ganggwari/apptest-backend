package com.mbc.puf.alert.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.AlertChannel;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.CameraRole;
import com.mbc.puf.common.enums.DeliveryStatus;

/**
 * 알림 발송 기록 목록 한 줄. 위험 알림·공지 알림, 알림 단위·발송(채널) 단위 4가지 목록이 모두 이 형식을 쓴다.
 *  kind        : ALERT(위험 알림: 접근/최종) / NOTICE(공지 알림)
 *  id          : alert.id 또는 notice_alert.id
 *  deliveryId  : 발송(채널) 단위 목록에서만 값 있음 (alert_delivery.id / notice_delivery.id)
 *  refId       : ALERT 면 eventId, NOTICE 면 noticeId
 *  title       : ALERT 면 "접근 알림"/"최종 알림", NOTICE 면 공지 제목
 *  region 계열, underpass 계열, car 계열 : 공지 알림은 시설·차량과 무관해서 null
 *  cameraId/cameraRole : 번호판을 인식해 이 알림을 만든 카메라와 그 역할(APPROACH_LPR/FINAL_LPR). 공지 알림·예전 알림은 null
 *  alertType   : ALERT 만
 *  channel/platform/status : 발송(채널) 단위 목록에서만 (platform 은 푸시일 때 기기 종류 WEB/ANDROID/IOS 등)
 *  channels    : 알림 단위 목록에서만. 채널별 최종 결과 (한 채널에 여러 번 시도했으면 SUCCESS > PENDING > FAIL 우선)
 *  pushStatus  : 알림 단위 목록에서만. SUCCESS(받음) / FAIL(발송 실패) / PENDING(발송 중) / NO_PUSH(보낼 기기 없음·푸시 꺼 둠)
 *  sentAt      : 알림 단위는 알림 생성 시각, 발송 단위는 발송 시도 시각
 */
public record AlertLogListItem(
        String kind,
        Long id,
        Long deliveryId,
        Long refId,
        String title,
        Long regionId,
        String regionName,
        Long underpassId,
        String underpassName,
        Long customerId,
        String customerName,
        Long carId,
        String carNum,
        Long cameraId,
        CameraRole cameraRole,
        AlertType alertType,
        AlertChannel channel,
        String platform,
        DeliveryStatus status,
        String pushStatus,
        List<ChannelResult> channels,
        boolean read,
        LocalDateTime sentAt) {

    /** 채널 하나의 최종 발송 결과 */
    public record ChannelResult(AlertChannel channel, DeliveryStatus status) {
    }

    public AlertLogListItem withDelivery(List<ChannelResult> channels, String pushStatus) {
        return new AlertLogListItem(kind, id, deliveryId, refId, title, regionId, regionName, underpassId,
                underpassName, customerId, customerName, carId, carNum, cameraId, cameraRole, alertType, channel,
                platform, status, pushStatus, channels, read, sentAt);
    }
}
