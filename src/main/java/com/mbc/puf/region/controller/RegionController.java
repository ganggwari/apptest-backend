package com.mbc.puf.region.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.region.dto.RegionCreateRequest;
import com.mbc.puf.region.dto.RegionResponse;
import com.mbc.puf.region.dto.RegionUpdateRequest;
import com.mbc.puf.region.service.RegionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    // 지역 목록
    @GetMapping
    public ApiResponse<List<RegionResponse>> getRegionList() {
        return ApiResponse.ok(regionService.getRegionList());
    }

    // 지역 추가 (201 Created) - 관리자 전용 (누가 할 수 있는지는 확인 필요)
    @PostMapping
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ResponseEntity<ApiResponse<RegionResponse>> createRegion(
            @Valid @RequestBody RegionCreateRequest request) {
        RegionResponse created = regionService.createRegion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("지역이 추가되었습니다.", created));
    }

    // 지역 이름 변경 - 관리자 전용 (누가 할 수 있는지는 확인 필요)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<RegionResponse> updateRegion(
            @PathVariable("id") Long id,
            @Valid @RequestBody RegionUpdateRequest request) {
        return ApiResponse.ok("지역 이름이 변경되었습니다.", regionService.updateRegion(id, request));
    }

    // 지역 삭제 - 관리자 전용 (누가 할 수 있는지는 확인 필요)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<Void> deleteRegion(@PathVariable("id") Long id) {
        regionService.deleteRegion(id);
        return ApiResponse.ok("지역이 삭제되었습니다.", null);
    }
}