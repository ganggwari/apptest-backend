package com.mbc.puf.pm.service;

import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmActionRequest;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmEventStatusRequest;

/** 예지보전 점검 처리 (상태 변경·조치 기록). 담당 지역의 이벤트만 */
public interface PmEventService {

    /** 확인필요 → 점검중 → 조치완료/기타 로 상태를 바꾸고, 내용이 있으면 조치 기록도 남긴다 */
    PmEventItem changeStatus(LoginUser loginUser, Long eventId, PmEventStatusRequest request);

    /** 상태는 그대로 두고 조치 기록만 추가 */
    PmActionItem addAction(LoginUser loginUser, Long eventId, PmActionRequest request);
}
