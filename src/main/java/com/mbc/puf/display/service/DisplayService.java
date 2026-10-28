package com.mbc.puf.display.service;

import java.util.List;

import com.mbc.puf.common.enums.DisplayCategory;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.display.dto.DisplayHistoryItem;
import com.mbc.puf.display.dto.DisplayPageSaveRequest;
import com.mbc.puf.display.dto.DisplayPresetItem;
import com.mbc.puf.display.dto.VmsPageItem;

/** 관리자의 전광판 문구 관리 (수동 페이지). 자동 표출 페이지는 VmsDisplayService 가 만든다 */
public interface DisplayService {

    /** 특정 카테고리(또는 전체)의 전광판 프리셋 목록 */
    List<DisplayPresetItem> getPresets(DisplayCategory category);

    /** 전광판(its_vms_id)의 페이지 목록 (page_no 오름차순). 페이지가 없으면 기본 문구 1장(defaultPage=true) */
    List<VmsPageItem> getPages(Long vmsId);

    /** 수동 페이지 추가 (맨 뒤). 원인 지하차도(underpassId)가 내 담당 지역이어야 한다 */
    VmsPageItem createPage(LoginUser loginUser, Long vmsId, DisplayPageSaveRequest request);

    /** 수동 페이지 문구 수정. 자동 페이지는 수정할 수 없다(409) */
    VmsPageItem updatePage(LoginUser loginUser, Long vmsId, Integer pageNo, DisplayPageSaveRequest request);

    /** 수동 페이지 삭제 (이후 page_no 재정렬). 자동 페이지는 삭제할 수 없다(409) */
    void deletePage(LoginUser loginUser, Long vmsId, Integer pageNo);

    /**
     * 전광판 문구 변경 이력 (최근 순). 내 담당 지역의 지하차도가 원인인 것만 본다.
     * vmsId·source(AUTO/MANUAL)는 선택 필터, period 는 변경 시각 기준(endDate 당일 포함)
     */
    PageResponse<DisplayHistoryItem> getHistory(LoginUser loginUser, ScopeQuery scope, Long vmsId, String source,
                                                String managerName, PeriodQuery period, PageQuery page);
}
