package com.mbc.puf.pm.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmEquipmentCreateRequest;
import com.mbc.puf.pm.dto.PmEquipmentCreateResponse;
import com.mbc.puf.pm.service.PmEquipmentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 예지보전 설비 등록·삭제. 관리자 담당 지역만 */
@Tag(name = "예지보전 설비 관리", description = "설비 등록(이름은 DB 가 자동 부여)과 삭제(소프트 삭제). 수정 API 는 없다")
@RestController
@RequestMapping("/api/pm/equipments")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmEquipmentController {

    private final PmEquipmentService pmEquipmentService;

    public PmEquipmentController(PmEquipmentService pmEquipmentService) {
        this.pmEquipmentService = pmEquipmentService;
    }

    @Operation(summary = "설비 등록", description = "underpassId·type 필수. camId 는 CCTV, itsVmsId 는 DISPLAY 일 때만(그 지하차도의 카메라 / 다른 설비에 안 붙은 전광판), "
            + "installedAt(설치일)은 선택. 이름(예: P-12-1)과 센서 주기는 DB 가 정하고, 단계 기준은 기본값(안전 0 / 주의 40 / 위험 70)으로 같이 만든다. "
            + "담당 지역 밖이면 403, 이미 연결된 카메라·전광판이면 409.")
    @PostMapping
    public ResponseEntity<ApiResponse<PmEquipmentCreateResponse>> register(@AuthenticationPrincipal LoginUser loginUser,
                                                                          @Valid @RequestBody PmEquipmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pmEquipmentService.register(loginUser, request)));
    }

    @Operation(summary = "설비 삭제", description = "삭제 표시(소프트 삭제)만 한다. 목록과 예지보전 서버 조회에서 빠지고, 연결했던 전광판은 다시 쓸 수 있다. "
            + "점수·점검 기록은 남는다. 담당 지역 밖이면 403, 없거나 이미 삭제됐으면 404.")
    @DeleteMapping("/{equipId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("equipId") Long equipId) {
        pmEquipmentService.delete(loginUser, equipId);
        return ApiResponse.ok("설비가 삭제되었습니다.", null);
    }
}
