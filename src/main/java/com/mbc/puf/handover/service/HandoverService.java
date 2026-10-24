package com.mbc.puf.handover.service;

import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.handover.dto.HandoverCreateRequest;
import com.mbc.puf.handover.dto.HandoverDetail;
import com.mbc.puf.handover.dto.HandoverListItem;
import com.mbc.puf.handover.dto.HandoverMemoRequest;

/** 근무조 인수인계 리포트 (교대 때 위험도·예지보전 현황 자동 집계 + 수기 메모, 미해결 건 이월) */
public interface HandoverService {

    /** 로그인 직후 팝업용: 직전 근무조의 리포트. 아무도 안 썼으면 그 시간대를 바로 집계해서 돌려준다 */
    HandoverDetail getPrevious(LoginUser loginUser);

    /** 쓰기 전 미리보기: 내 근무조(또는 shiftId)의 지금 근무를 집계만 하고 저장하지 않는다 */
    HandoverDetail preview(LoginUser loginUser, Long shiftId);

    /** 작성: 지금 시점을 스냅샷으로 저장 */
    HandoverDetail create(LoginUser loginUser, HandoverCreateRequest request);

    PageResponse<HandoverListItem> list(Long shiftId, PeriodQuery period, PageQuery page);

    HandoverDetail get(Long id);

    /** 메모 수정 (쓴 사람만) */
    HandoverDetail updateMemo(LoginUser loginUser, Long id, HandoverMemoRequest request);
}
