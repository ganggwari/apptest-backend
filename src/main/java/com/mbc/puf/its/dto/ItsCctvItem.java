package com.mbc.puf.its.dto;

/** ITS CCTV 한 대. distance 는 nearby 조회일 때만 값이 있다(m, 소수점 없음). 그 외에는 null */
public record ItsCctvItem(Long id, Integer itsId, String name, double lat, double lng, String streamUrl, Integer distance) {
}
