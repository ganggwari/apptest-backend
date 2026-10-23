package com.mbc.puf.its.service;

import java.util.List;

import com.mbc.puf.its.dto.ItsCctvItem;
import com.mbc.puf.its.dto.ItsVmsItem;

/** 지도 레이어용 ITS CCTV·전광판 조회 (로그인 없이 볼 수 있다) */
public interface ItsService {

    /** ITS CCTV 전체 */
    List<ItsCctvItem> getAllCctvs();

    /**
     * 기준 좌표에서 가까운 순으로 CCTV 를 limit 대. 기준은 (lat,lng) 또는 underpassId(그 지하차도 좌표) 중 하나.
     * radiusM 안의 것만 낸다. 둘 다 없거나 지하차도가 없으면 400/404.
     */
    List<ItsCctvItem> getNearbyCctvs(Double lat, Double lng, Long underpassId, double radiusM, int limit);

    /** ITS 전광판 전체와 각 전광판의 현재 표출 페이지 */
    List<ItsVmsItem> getAllVms();

    /** 전광판 한 대와 현재 표출 페이지 (없으면 404) */
    ItsVmsItem getVms(Long vmsId);
}
