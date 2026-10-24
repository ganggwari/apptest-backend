package com.mbc.puf.pm.service;

import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmEngineStatusResponse;

/** 예지보전 서버(엔진) 동작 상태: 마지막 점수 수신, 설비별 점수·센서 신선도 */
public interface PmEngineService {

    PmEngineStatusResponse getStatus(LoginUser loginUser);
}
