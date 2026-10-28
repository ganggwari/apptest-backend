package com.mbc.puf.alert.service;

import com.mbc.puf.alert.dto.AlertLogListItem;
import com.mbc.puf.alert.dto.AlertLogQuery;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;

/** 알림 발송 기록 조회 (관리자용). 위험 알림/공지 알림 x 알림 단위/발송(채널) 단위 = 4가지, 응답 형식은 모두 AlertLogListItem */
public interface AlertLogService {

    // 위험 알림(접근/최종), 알림 1건 = 1행. Scope: 안 주면 담당 지역 전체
    PageResponse<AlertLogListItem> getAlerts(LoginUser loginUser, ScopeQuery scope, AlertLogQuery query,
                                             VehicleQuery vehicle, PeriodQuery period, PageQuery page);

    // 위험 알림의 발송 시도(채널·기기별), alert_delivery 1건 = 1행
    PageResponse<AlertLogListItem> getAlertDeliveries(LoginUser loginUser, ScopeQuery scope, AlertLogQuery query,
                                                      VehicleQuery vehicle, PeriodQuery period, PageQuery page);

    // 공지 알림, notice_alert 1건(공지 x 고객) = 1행. 공지는 시설과 무관해서 Scope 없음
    PageResponse<AlertLogListItem> getNotices(AlertLogQuery query, PeriodQuery period, PageQuery page);

    // 공지 알림의 발송 시도, notice_delivery 1건 = 1행
    PageResponse<AlertLogListItem> getNoticeDeliveries(AlertLogQuery query, PeriodQuery period, PageQuery page);
}
