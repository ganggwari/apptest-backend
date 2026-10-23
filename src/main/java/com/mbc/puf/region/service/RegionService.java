package com.mbc.puf.region.service;

import java.util.List;

import com.mbc.puf.region.dto.RegionCreateRequest;
import com.mbc.puf.region.dto.RegionResponse;
import com.mbc.puf.region.dto.RegionUpdateRequest;

public interface RegionService {

    // 지역 목록 (삭제되지 않은 지역, '미배정' 포함)
    List<RegionResponse> getRegionList();

    // 지역 추가
    RegionResponse createRegion(RegionCreateRequest request);

    // 지역 이름 변경
    RegionResponse updateRegion(Long id, RegionUpdateRequest request);

    // 지역 삭제 (소속 지하차도/지역담당자를 '미배정'으로 이동 후 소프트 삭제)
    void deleteRegion(Long id);
}