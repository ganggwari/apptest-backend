package com.mbc.puf.its.dto;

import java.util.List;

import com.mbc.puf.display.dto.VmsPageItem;

/**
 * ITS 전광판 한 대와 지금 표출 중인 페이지들(page_no 순서).
 * 표출할 내용이 하나도 없으면 pages 에 기본 문구 1장(defaultPage=true)이 들어 있다(저장된 값이 아니라 설정값).
 * 더미 전광판(dummy=true)도 실제와 똑같이 지도에 그리고 눌러서 페이지를 보여 주면 된다.
 */
public record ItsVmsItem(Long id, Integer itsNo, String spec, String name, double lat, double lng, boolean dummy,
                         List<VmsPageItem> pages) {
}
