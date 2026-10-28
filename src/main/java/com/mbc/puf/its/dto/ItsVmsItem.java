package com.mbc.puf.its.dto;

import java.util.List;

import com.mbc.puf.display.dto.VmsPageItem;

/**
 * ITS 전광판 한 대와 지금 표출 중인 페이지들(page_no 순서).
 * 표출할 내용이 하나도 없으면 pages 에 기본 문구 1장(defaultPage=true)이 들어 있다(저장된 값이 아니라 설정값).
 * 더미 전광판(dummy=true)도 실제와 똑같이 지도에 그리고 눌러서 페이지를 보여 주면 된다.
 * dist·zone 은 지하차도 기준으로 조회했을 때만 채운다(그 지하차도에서 전광판까지 거리 m, NEAR 근접=차단 정보 / FAR 원거리=차단+우회 정보). 그 외에는 null.
 */
public record ItsVmsItem(Long id, Integer itsNo, String spec, String name, double lat, double lng, boolean dummy,
                         List<VmsPageItem> pages, Integer dist, String zone) {
}
