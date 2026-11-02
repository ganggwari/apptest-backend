package com.mbc.puf.pm.service;

import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmActionItem;
import com.mbc.puf.pm.dto.PmActionRequest;
import com.mbc.puf.pm.dto.PmEventCreateRequest;
import com.mbc.puf.pm.dto.PmEventItem;
import com.mbc.puf.pm.dto.PmEventStatusRequest;

/** 예지보전 점검 처리 (상태 변경·조치 기록). 담당 지역의 이벤트만 */
public interface PmEventService {

    /** 확인필요 → 점검중 → 조치완료/기타 로 상태를 바꾸고, 내용이 있으면 조치 기록도 남긴다 */
    PmEventItem changeStatus(LoginUser loginUser, Long eventId, PmEventStatusRequest request);

    /**
     * 점검 수동 등록: 확인필요 상태의 점검 대기(수동등록)를 만들고 첫 기록을 남긴다.
     * 점수 수신 경로(쿨다운 판단)를 거치지 않으므로 점검 후 쿨다운과 상관없이 등록된다.
     * 같은 설비에 처리 중인 점검이 있으면 409.
     */
    PmEventItem create(LoginUser loginUser, PmEventCreateRequest request);

    /** 상태는 그대로 두고 조치 기록만 추가 */
    PmActionItem addAction(LoginUser loginUser, Long eventId, PmActionRequest request);
}
