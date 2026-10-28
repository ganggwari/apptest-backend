package com.mbc.puf.display.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.mbc.puf.display.dto.VmsPageItem;

/**
 * 전광판(VMS) 자동 표출. 지하차도 상태(위험단계·차단·해제)가 바뀔 때마다 그 지하차도와 연결된 전광판들의
 * 페이지를 "지금 상태에 맞는 모습"으로 다시 맞춘다 (만들고, 고치고, 지운다).
 * 같은 입력이면 몇 번을 불러도 결과가 같으므로 서버 재시작·만료 때도 그대로 다시 불러 쓴다.
 */
public interface VmsDisplayService {

    /**
     * 지하차도에 연결된 모든 전광판을 다시 맞춘다. 호출한 쪽의 트랜잭션 안에서 실행된다.
     *
     * @param eventId    계기가 된 차단/해제/위험도 이벤트(없으면 null) — 이력에 같이 남긴다
     * @param operatorId 수동 차단/해제를 한 관리자(자동이면 null)
     */
    void reconcileForUnderpass(Long underpassId, Long eventId, Long operatorId);

    /** 전광판 1대를 다시 맞춘다. 바뀐 게 있으면 true */
    boolean reconcileVms(Long vmsId, Long eventId, Long operatorId);

    /** 주기 점검 대상 전광판: 자동 페이지가 남아 있거나, 연결된 지하차도가 안전이 아닌 전광판 */
    List<Long> targetVmsIds();

    /** 전광판들의 현재 페이지 (vmsIds 가 null 이면 전체). 페이지 없는 전광판은 키가 없다 */
    Map<Long, List<VmsPageItem>> pagesOf(Collection<Long> vmsIds);

    /** 페이지가 하나도 없는 전광판에 보여줄 기본 문구 페이지 (설정값, DB 에는 저장하지 않는다) */
    VmsPageItem defaultPage(Long vmsId);

    /** 지하차도에서 가장 가까운 전광판의 1페이지 문구 (없으면 null). 경찰 전달서 등에 쓴다 */
    String nearestFirstPageText(Long underpassId);

    /**
     * 지하차도가 차단됐을 때 가장 가까운 전광판에 뜰 문구를 미리 계산한다 (DB 는 바꾸지 않는다, 연결된 전광판이 없으면 null).
     * 차단 직후 전광판 갱신은 커밋 뒤로 미뤄지므로, 그 전에 만드는 경찰 전달서(스냅샷)의 문구를 이 값으로 채운다.
     */
    String previewBlockText(Long underpassId);
}
