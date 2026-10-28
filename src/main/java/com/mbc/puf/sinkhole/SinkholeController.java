package com.mbc.puf.sinkhole;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.IOException;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class SinkholeController {
    private final SinkholeService service;
    public SinkholeController(SinkholeService service) { this.service = service; }

    // WebConfigurer의 /api/internal/** API 키 인터셉터로 보호된다.
    @PostMapping(value = "/api/internal/sinkhole/results", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SinkholeService.Result> receive(
            @Valid @RequestPart("metadata") SinkholeResultRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image) throws IOException {
        return ApiResponse.ok(service.receive(request, image));
    }

    @GetMapping("/api/pm/equipments/{equipId}/road")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<SinkholeService.Detail> detail(@AuthenticationPrincipal LoginUser user,
            @PathVariable("equipId") Long equipId) {
        return ApiResponse.ok(service.detail(user, equipId));
    }

    public record Review(@NotNull Long eventId,
            @NotBlank @Pattern(regexp = "CONFIRMED|FALSE_POSITIVE|RESOLVED") String verdict,
            @Size(max = 2000) String content) {}

    @PatchMapping("/api/pm/equipments/{equipId}/road/review")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ApiResponse<SinkholeService.Detail> review(@AuthenticationPrincipal LoginUser user,
            @PathVariable("equipId") Long equipId, @Valid @RequestBody Review request) {
        return ApiResponse.ok(service.review(user, equipId, request));
    }

    @GetMapping("/api/pm/road-results/{resultId}/image")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    public ResponseEntity<Resource> image(@AuthenticationPrincipal LoginUser user,
            @PathVariable("resultId") Long resultId) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.noStore()).body(service.image(user, resultId));
    }
}
