package com.mbc.puf.pm.controller;

import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.pm.service.PmStubModeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pm/equipments")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmStubModeController {
    private final PmStubModeService service;
    public PmStubModeController(PmStubModeService service){this.service=service;}
    public record Request(@NotNull @Pattern(regexp="NORMAL|DANGER") String mode) {}
    @PutMapping("/{equipId}/stub-mode")
    public ApiResponse<PmStubModeService.State> change(@AuthenticationPrincipal LoginUser user,
            @PathVariable("equipId") long equipId,@Valid @RequestBody Request request) {
        return ApiResponse.ok(service.change(user,equipId,request.mode()));
    }
}
