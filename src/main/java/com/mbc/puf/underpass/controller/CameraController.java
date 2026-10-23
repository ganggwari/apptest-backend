package com.mbc.puf.underpass.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.underpass.dto.CameraCreateRequest;
import com.mbc.puf.underpass.dto.CameraResponse;
import com.mbc.puf.underpass.dto.CameraUpdateRequest;
import com.mbc.puf.underpass.service.CameraService;

import jakarta.validation.Valid;

/** 지하차도 하위 카메라 관리. entry_camera_id/exit_camera_id 지정은 여기서 다루지 않는다(지하차도 쪽 별도 API) */
@RestController
@RequestMapping("/api/underpasses/{underpassId}/cameras")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class CameraController {

    private final CameraService cameraService;

    public CameraController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    // 해당 지하차도의 카메라 목록 (관리자면 지역 상관없이 조회 가능)
    @GetMapping
    public ApiResponse<List<CameraResponse>> list(@AuthenticationPrincipal LoginUser loginUser,
                                                    @PathVariable("underpassId") Long underpassId) {
        return ApiResponse.ok(cameraService.getCameras(loginUser, underpassId));
    }

    // 카메라 상세
    @GetMapping("/{id}")
    public ApiResponse<CameraResponse> detail(@AuthenticationPrincipal LoginUser loginUser,
                                               @PathVariable("underpassId") Long underpassId,
                                               @PathVariable("id") Long id) {
        return ApiResponse.ok(cameraService.getCamera(loginUser, underpassId, id));
    }

    // 카메라 등록 (201 Created). REGIONAL_ADMIN은 본인 담당 지역의 지하차도만 가능
    @PostMapping
    public ResponseEntity<ApiResponse<CameraResponse>> create(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("underpassId") Long underpassId,
            @Valid @RequestBody CameraCreateRequest request) {
        CameraResponse created = cameraService.createCamera(loginUser, underpassId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("카메라가 등록되었습니다.", created));
    }

    // 카메라 수정
    @PutMapping("/{id}")
    public ApiResponse<CameraResponse> update(
            @AuthenticationPrincipal LoginUser loginUser,
            @PathVariable("underpassId") Long underpassId,
            @PathVariable("id") Long id,
            @Valid @RequestBody CameraUpdateRequest request) {
        return ApiResponse.ok("카메라 정보가 수정되었습니다.", cameraService.updateCamera(loginUser, underpassId, id, request));
    }

    // 카메라 삭제 (소프트 삭제)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                     @PathVariable("underpassId") Long underpassId,
                                     @PathVariable("id") Long id) {
        cameraService.deleteCamera(loginUser, underpassId, id);
        return ApiResponse.ok("카메라가 삭제되었습니다.", null);
    }
}
