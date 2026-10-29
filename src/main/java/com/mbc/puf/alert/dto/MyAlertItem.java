package com.mbc.puf.alert.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.CameraRole;

/**
 * 고객용 "내 알림" 화면(휴대폰 문자처럼 보여주는 목업)용 DTO.
 * 관리자용 AlertLogListItem과 달리 표시에 바로 쓸 수 있게 지하차도 이름까지 조인해서 받고,
 * 문자 본문(messageText)도 서버에서 alertType별로 미리 조합해서 내려준다.
 */
public class MyAlertItem {

    private final Long id;
    private final String underpassName;
    private final String carNum;
    private final String recipientPhone;
    private final AlertType alertType;
    private final LocalDateTime sentAt;
    private final boolean blocked;   // 알림을 만든 계기가 차단 이벤트인지
    private final String reason;     // 차단 사유 (없으면 null)
    private final Long cameraId;         // 번호판을 인식한 카메라 (예전 알림은 null)
    private final CameraRole cameraRole; // 그 카메라 역할 (APPROACH_LPR / FINAL_LPR)
    private final Integer cameraDistM;   // 그 카메라에서 진입지점까지 거리(m, 모르면 null)

    public MyAlertItem(Long id, String underpassName, String carNum, String recipientPhone,
                        AlertType alertType, LocalDateTime sentAt, boolean blocked, String reason,
                        Long cameraId, CameraRole cameraRole, Integer cameraDistM) {
        this.blocked = blocked;
        this.reason = reason;
        this.cameraId = cameraId;
        this.cameraRole = cameraRole;
        this.cameraDistM = cameraDistM;
        this.id = id;
        this.underpassName = underpassName;
        this.carNum = carNum;
        this.recipientPhone = recipientPhone;
        this.alertType = alertType;
        this.sentAt = sentAt;
    }

    public Long getId() { return id; }
    public String getUnderpassName() { return underpassName; }
    public String getCarNum() { return carNum; }
    public String getRecipientPhone() { return recipientPhone; }
    public AlertType getAlertType() { return alertType; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getReason() { return reason; }
    public Long getCameraId() { return cameraId; }
    public CameraRole getCameraRole() { return cameraRole; }
    public Integer getCameraDistM() { return cameraDistM; }

    // 고객에게 보여줄 인식 카메라 안내. 예: "인식 카메라 #12 (진입 300m 전)" (거리를 모르면 괄호 생략)
    public static String cameraLabel(Long cameraId, Integer distM) {
        return "인식 카메라 #" + cameraId + (distM == null ? "" : " (진입 " + distM + "m 전)");
    }

    // 카메라 정보가 없는 예전 알림이면 null
    public String getCameraText() {
        return cameraId == null ? null : cameraLabel(cameraId, cameraDistM);
    }

    // 화면에 보여줄 알림 종류 한글 라벨 (접근 예측 vs 진입 직전)
    public String getAlertTypeLabel() {
        return alertType == AlertType.PREDICTIVE ? "접근 알림" : "진입 직전 알림";
    }

    // 문자메시지처럼 보이는 본문. PREDICTIVE/FINAL 구분해서 문구를 다르게 조합하고, 차단 사유가 있으면 같이 안내한다
    public String getMessageText() {
        String text = baseMessageText();
        return cameraId == null ? text : text + " (" + getCameraText() + ")";
    }

    private String baseMessageText() {
        String why = (reason == null || reason.isBlank()) ? "" : " 사유: " + reason + ".";
        if (alertType == AlertType.PREDICTIVE) {
            if (blocked) {
                return String.format("[긴급재난문자] %s 접근 차량이 감지되었습니다. 현재 통제 중인 구간입니다.%s 차량번호 %s, 우회하세요.",
                        underpassName, why, carNum);
            }
            return String.format("[긴급재난문자] %s 접근 차량이 감지되었습니다. 차량번호 %s, 현재 위험 단계이니 우회를 권장합니다.",
                    underpassName, carNum);
        }
        return String.format("[긴급재난문자] %s 진입 직전입니다. 현재 통제 중인 구간이니 즉시 정차 후 우회하세요.%s (차량번호 %s)",
                underpassName, why, carNum);
    }
}
