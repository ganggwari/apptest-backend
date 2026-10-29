package com.mbc.puf.alert.service;

import com.mbc.puf.alert.dto.MyAlertItem;
import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

/** camera_role 감지를 계기로 한 개인화 알림 발송. 실제 푸시 연동은 없고 alert 테이블에만 남긴다 */
public interface AlertService {

    // 사전예측(PREDICTIVE): APPROACH_LPR 카메라가 매칭했는데 그 지하차도 위험도가 관심 단계 이상이거나 차단 중일 때, 그 차주에게 발송
    void sendPredictiveAlert(Long eventId, Long carId, String carNum, com.mbc.puf.underpass.entity.Camera camera);

    // 진입직전 최후확인(FINAL): FINAL_LPR 카메라가 매칭했는데 그 지하차도가 지금 BLOCKED일 때, 그 차주에게 발송
    void sendRoiFinalAlert(Long eventId, Long carId, String carNum, com.mbc.puf.underpass.entity.Camera camera);

    /**
     * 번호판이 매칭됐을 때 그 카메라 역할과 지하차도 상태를 보고 알림을 보낸다 (자동 인식·수동 정정 둘 다 이걸 쓴다).
     * 인식한 카메라는 alert.camera_id 에 남고, 고객 알림 문구에도 카메라 번호(와 진입지점까지 거리)가 들어간다.
     *  - APPROACH_LPR : 지하차도가 차단 중이거나 위험도가 관심 단계 이상이면 접근 예측(PREDICTIVE). 차단 중이면 차단 사유가 문구에 들어간다
     *  - FINAL_LPR    : 지하차도가 차단 중이면 진입 직전(FINAL)
     */
    void sendAlertsForPlate(com.mbc.puf.underpass.entity.Underpass underpass, com.mbc.puf.underpass.entity.Camera camera,
                            java.util.List<com.mbc.puf.car.entity.Car> cars, String carNum);

    // 고객용 "내 알림" 목록 (문자처럼 보여주기용). customerId는 로그인한 본인으로 고정해서 넘긴다
    PageResponse<MyAlertItem> getMyAlerts(Long customerId, AlertType alertType, PeriodQuery period, PageQuery page);
}
