package com.mbc.puf.pm.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.pdf.PmDailyReportPdfGenerator;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.pm.dto.PmDailyReport;
import com.mbc.puf.pm.service.PmReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 예지보전 리포트. 관리자 담당 지역만 */
@Tag(name = "예지보전 리포트", description = "하루치 요약(데일리 리포트)")
@RestController
@RequestMapping("/api/pm/reports")
@PreAuthorize("hasRole('REGIONAL_ADMIN')")
public class PmReportController {

    private final PmReportService pmReportService;

    private final PmDailyReportPdfGenerator pdfGenerator;

    public PmReportController(PmReportService pmReportService, PmDailyReportPdfGenerator pdfGenerator) {
        this.pmReportService = pmReportService;
        this.pdfGenerator = pdfGenerator;
    }

    @Operation(summary = "데일리 리포트",
            description = "date(yyyy-MM-dd, 생략하면 오늘=지금까지) 하루치 예지보전 요약. 단계별 설비 수(전일 시작 시점 대비), 설비 종류별 현황, "
                    + "신규 점검 대기, 그날 남긴 점검 기록, 아직 처리 중인 점검(이월), 데이터 미수신 설비, 점검 권장 설비. "
                    + "regionId·underpassId 는 담당 지역 안에서만")
    @GetMapping("/daily")
    public ApiResponse<PmDailyReport> daily(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(pmReportService.getDailyReport(loginUser, scope, date));
    }

    @Operation(summary = "데일리 리포트 PDF",
            description = "'PDF 뽑기' 버튼용. /daily 와 같은 조건(date·regionId·underpassId)으로 그 순간 집계해서 PDF 파일로 내려준다 "
                    + "(Content-Disposition: attachment, application/pdf)")
    @GetMapping("/daily/pdf")
    public ResponseEntity<byte[]> dailyPdf(
            @AuthenticationPrincipal LoginUser loginUser,
            @ModelAttribute ScopeQuery scope,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        PmDailyReport report = pmReportService.getDailyReport(loginUser, scope, date);
        byte[] bytes = pdfGenerator.generate(report);
        String filename = "데일리리포트_" + report.date() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20"));
        headers.setContentLength(bytes.length);
        return ResponseEntity.ok().headers(headers).body(bytes);
    }
}
