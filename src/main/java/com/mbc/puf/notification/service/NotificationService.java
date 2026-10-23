package com.mbc.puf.notification.service;

import com.mbc.puf.common.enums.AlertType;
import com.mbc.puf.common.enums.CustomerNotificationCategory;
import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.notification.dto.CustomerNotificationItem;
import com.mbc.puf.notification.dto.ManagerNotificationItem;
import com.mbc.puf.notification.dto.NotificationUnreadResponse;

public interface NotificationService {

    // ================= 고객 알림: 만들기 (다른 서비스가 호출) =================

    // 위험: 번호판이 매칭된 고객에게 접근 예측/진입 직전 알림이 나갈 때
    void notifyCustomerRisk(Long customerId, Long alertId, String underpassName, String carNum, AlertType alertType,
                            String reason);

    // 공지: 새 공지가 등록될 때 활성 고객 전체에게
    void notifyCustomersNewNotice(Long noticeId, String noticeTitle, boolean important);

    // 문의: 내 문의에 답변이 달렸을 때
    void notifyCustomerInquiryAnswered(Long customerId, Long inquiryId, String inquiryTitle);

    // ================= 고객 알림: 조회·읽음 =================

    // category 가 null 이면 전체. unreadOnly 면 안 읽은 것만
    PageResponse<CustomerNotificationItem> getMyNotifications(Long customerId, CustomerNotificationCategory category,
                                                             boolean unreadOnly, PageQuery page);

    NotificationUnreadResponse getMyUnread(Long customerId);

    // 읽음 처리. 본인 알림이 아니면 404
    void markMyRead(Long customerId, Long id);

    // 안 읽은 알림 모두 읽음 (category 가 null 이면 전체)
    void markMyAllRead(Long customerId, CustomerNotificationCategory category);

    // ================= 관리자 알림 =================

    // 알림 만들기 (다른 서비스가 호출). regionId 가 null 이면 모든 관리자 대상
    void notifyManagers(ManagerNotificationCategory category, Long regionId, String title, String body,
                        String refType, Long refId);

    // 내 담당 지역(+전체 대상) 알림 목록. category 가 null 이면 "전체" 탭
    PageResponse<ManagerNotificationItem> getManagerNotifications(LoginUser loginUser,
                                                                  ManagerNotificationCategory category,
                                                                  boolean unreadOnly, PageQuery page);

    NotificationUnreadResponse getManagerUnread(LoginUser loginUser);

    // 읽음 처리. 내 담당 지역 밖 알림이면 404
    void markManagerRead(LoginUser loginUser, Long id);

    void markManagerAllRead(LoginUser loginUser, ManagerNotificationCategory category);
}
