package com.mbc.puf.member.dto;

/** 담당 지역 한 개 (id + 이름). 관리자 응답에서 담당 지역 목록으로 쓴다 */
public record RegionRef(Long id, String name) {
}
