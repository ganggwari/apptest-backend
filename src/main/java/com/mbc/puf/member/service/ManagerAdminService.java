package com.mbc.puf.member.service;

import java.util.List;

import com.mbc.puf.common.enums.ManagerStatus;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.member.dto.ManagerSummaryResponse;
import com.mbc.puf.member.dto.ShiftResponse;

/** 관리자 계정 관리 (MP-12). 가입 승인은 자동 승인 스케줄러가 대신한다 */
public interface ManagerAdminService {

    // 관리자 목록 (검색·필터·페이징). regionId 를 주면 그 지역 담당자만
    PageResponse<ManagerSummaryResponse> getManagerList(String keyword, ManagerStatus status, Long regionId,
                                                        Long shiftId, boolean includeWithdrawn, int page, int size);

    // 담당 지역 배정: 보낸 목록으로 통째로 교체 (빈 목록 = 담당 없음)
    ManagerSummaryResponse assignRegions(Long managerId, List<Long> regionIds);

    // 근무조 배정 (null = 근무조 없음)
    ManagerSummaryResponse assignShift(Long managerId, Long shiftId);

    // 강제 탈퇴: 활성/승인대기 → 탈퇴(WITHDRAWN). 본인 계정은 마이페이지 탈퇴를 쓴다
    ManagerSummaryResponse withdraw(Long managerId, Long operatorId);

    // 근무조 목록 (드롭다운용)
    List<ShiftResponse> getShifts();
}
