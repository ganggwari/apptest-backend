package com.mbc.puf.pm.service;

import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmEquipmentCreateRequest;
import com.mbc.puf.pm.dto.PmEquipmentCreateResponse;

/** 예지보전 설비 등록·삭제 (관리자 담당 지역 안에서만) */
public interface PmEquipmentService {

    /** 설비를 등록한다. 이름은 DB 가 붙이고, 단계 기준은 기본값(안전 0 / 주의 40 / 위험 70)으로 함께 만든다 */
    PmEquipmentCreateResponse register(LoginUser loginUser, PmEquipmentCreateRequest request);

    /** 설비를 삭제 표시한다(소프트 삭제). 목록·예지보전 서버 조회에서 빠지고, 연결했던 전광판은 다시 쓸 수 있다 */
    void delete(LoginUser loginUser, Long equipId);
}
