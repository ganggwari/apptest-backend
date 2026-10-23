package com.mbc.puf.region.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 지역 추가 요청 */
public class RegionCreateRequest {

    @NotBlank(message = "지역 이름은 필수입니다.")
    @Size(max = 50, message = "지역 이름은 50자 이하여야 합니다.")   // region.name VARCHAR(50)
    private String name;

    public RegionCreateRequest() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
