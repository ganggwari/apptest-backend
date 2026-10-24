package com.mbc.puf.its.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.its.dto.ItsCctvItem;
import com.mbc.puf.its.dto.ItsVmsItem;
import com.mbc.puf.its.service.ItsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "ITS CCTV·전광판", description = "로그인 없이 조회. 지도 레이어와 지하차도 상세의 주변 CCTV, 전광판 표출 문구")
@RestController
public class ItsController {

    private final ItsService itsService;

    public ItsController(ItsService itsService) {
        this.itsService = itsService;
    }

    @Operation(summary = "ITS CCTV 전체", description = "지도 CCTV 레이어용. stream_url 이 .m3u8 이면 HLS 로 재생한다.")
    @GetMapping("/api/its-cctvs")
    public ApiResponse<List<ItsCctvItem>> cctvs() {
        return ApiResponse.ok(itsService.getAllCctvs());
    }

    @Operation(summary = "가까운 ITS CCTV",
            description = "기준 좌표(lat,lng) 또는 지하차도(underpassId)에서 가까운 순으로 limit 대(기본 3). radius(m, 기본 3000) 안의 것만. "
                    + "distance(m)가 같이 내려간다.")
    @GetMapping("/api/its-cctvs/nearby")
    public ApiResponse<List<ItsCctvItem>> nearby(@RequestParam(name = "lat", required = false) Double lat,
                                                 @RequestParam(name = "lng", required = false) Double lng,
                                                 @RequestParam(name = "underpassId", required = false) Long underpassId,
                                                 @RequestParam(name = "radius", defaultValue = "3000") double radius,
                                                 @RequestParam(name = "limit", defaultValue = "3") int limit) {
        return ApiResponse.ok(itsService.getNearbyCctvs(lat, lng, underpassId,
                Math.min(Math.max(radius, 50), 20000), Math.min(Math.max(limit, 1), 50)));
    }

    @Operation(summary = "ITS 전광판 전체 + 현재 표출 페이지",
            description = "더미 전광판도 똑같이 내려간다(dummy=true). 표출할 내용이 없으면 pages 에 기본 문구 1장(defaultPage=true). "
                    + "페이지 content 는 줄바꿈(\\n)으로 줄이 나뉘고 lines 로도 내려간다. color 는 RED/YELLOW/GREEN.")
    @GetMapping("/api/its-vms")
    public ApiResponse<List<ItsVmsItem>> vmsList() {
        return ApiResponse.ok(itsService.getAllVms());
    }

    @Operation(summary = "ITS 전광판 한 대 + 현재 표출 페이지")
    @GetMapping("/api/its-vms/{vmsId}")
    public ApiResponse<ItsVmsItem> vms(@PathVariable("vmsId") Long vmsId) {
        return ApiResponse.ok(itsService.getVms(vmsId));
    }
}
