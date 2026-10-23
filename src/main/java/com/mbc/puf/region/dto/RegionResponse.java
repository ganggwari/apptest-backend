package com.mbc.puf.region.dto;

/** 지역 응답 (목록/단건 조회용). 삭제된 지역은 조회 대상이 아니므로 isDeleted는 담지 않는다. */
public class RegionResponse {

    private Long id;        // region.id (BIGSERIAL → Long)
    private String name;    // region.name

    public RegionResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
