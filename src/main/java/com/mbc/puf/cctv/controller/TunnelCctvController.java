package com.mbc.puf.cctv.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.cctv.dto.TunnelCameraItem;
import com.mbc.puf.cctv.dto.TunnelCameraStreamResponse;
import com.mbc.puf.cctv.service.TunnelCctvService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "신월여의 공개 CCTV", description = "서울터널(신월여의지하도로) 공개 영상을 서버가 대신 조회. 로그인 없이 사용. 시설(우리 지하차도) CCTV와는 별개")
@RestController
@RequestMapping("/api/tunnel-cctv")
public class TunnelCctvController {

    private final TunnelCctvService tunnelCctvService;

    public TunnelCctvController(TunnelCctvService tunnelCctvService) {
        this.tunnelCctvService = tunnelCctvService;
    }

    @Operation(summary = "공개 CCTV 목록", description = "신월여의지하도로 공개 CCTV 아이디 목록(제공처 순서). 서버에서 5분 캐시")
    @GetMapping("/cameras")
    public ApiResponse<List<TunnelCameraItem>> cameras() {
        return ApiResponse.ok(tunnelCctvService.getCameras());
    }

    @Operation(summary = "공개 CCTV 한 대의 영상 정보",
            description = "name/direction/streamUrl. streamUrl 은 검증을 마친 https HLS(.m3u8) 주소라 브라우저가 바로 재생한다. 목록에 없는 아이디는 404, 서버에서 1분 캐시")
    @GetMapping("/cameras/{cctvId}/stream")
    public ApiResponse<TunnelCameraStreamResponse> stream(@PathVariable("cctvId") String cctvId) {
        return ApiResponse.ok(tunnelCctvService.getStream(cctvId));
    }
}
