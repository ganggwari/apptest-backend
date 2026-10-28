package com.mbc.puf.pm.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.pm.dto.PmInternalEquipmentItem;
import com.mbc.puf.pm.dto.PmScoreIngestRequest;
import com.mbc.puf.pm.dto.PmScoreIngestResponse;
import com.mbc.puf.pm.dto.PmSensorBackfillResponse;
import com.mbc.puf.pm.dto.PmSensorSeriesResponse;
import com.mbc.puf.pm.service.PmIngestService;
import com.mbc.puf.pm.service.PmSensorBackfillService;
import com.mbc.puf.pm.service.PmSensorService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * 예지보전 서버(FastAPI 8002)가 호출하는 내부 API. InternalController 와 같이 X-Internal-Api-Key 헤더로 인증하며,
 * 그 검사는 InternalApiKeyInterceptor(WebConfigurer 에 /api/internal/** 로 등록)가 담당한다.
 */
@Tag(name = "내부 수신(예지보전)", description = "X-Internal-Api-Key 헤더로 인증. 예지보전 서버가 설비 목록을 받아 가고 점수를 보낸다")
@RestController
@RequestMapping("/api/internal")
public class PmInternalController {

    private final PmIngestService pmIngestService;
    private final PmSensorService pmSensorService;
    private final PmSensorBackfillService pmSensorBackfillService;

    public PmInternalController(PmIngestService pmIngestService, PmSensorService pmSensorService,
            PmSensorBackfillService pmSensorBackfillService) {
        this.pmIngestService = pmIngestService;
        this.pmSensorService = pmSensorService;
        this.pmSensorBackfillService = pmSensorBackfillService;
    }

    @Operation(summary = "예지보전 설비 목록", description = "삭제되지 않은 지하차도의 삭제되지 않은 전체 설비. 이름·센서 주기(초)·센서 데이터 시작 시각을 함께 준다.")
    @GetMapping("/pm/equipments")
    public ApiResponse<List<PmInternalEquipmentItem>> equipments() {
        return ApiResponse.ok(pmIngestService.getEquipments());
    }

    @Operation(summary = "예지보전 센서 조회", description = "장비별 센서 데이터를 bucketSec 초 단위 버킷으로 묶어 컬럼별 count·sum·sumSq·min·max 로 돌려준다. "
            + "bucketSec 를 비우면 각 장비의 센서 주기(= 원본 그대로). 평균 = sum/count, 표준편차 = sqrt(sumSq/count - 평균^2). "
            + "행이 없는 시각은 버킷이 나오지 않고, 행은 있는데 값이 NULL 이면 그 컬럼 count 가 0 이다. "
            + "limit(기본 20000)에 걸리면 truncated=true 와 nextAfter 를 주니, 다음 호출에 after=nextAfter 로 이어 받는다. "
            + "from 은 이상(포함), to 는 미만(기본 지금), after 는 그 시각보다 뒤의 버킷만. 없거나 삭제된 장비는 unknownEquipIds 로 알려 준다.")
    @GetMapping("/pm/sensors")
    public ApiResponse<PmSensorSeriesResponse> sensors(
            @RequestParam(name = "equipIds") List<Long> equipIds,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(name = "bucketSec", required = false) Integer bucketSec,
            @RequestParam(name = "after", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime after,
            @RequestParam(name = "limit", required = false) Integer limit) {
        return ApiResponse.ok(pmSensorService.getSensorSeries(equipIds, from, to, bucketSec, after, limit));
    }

    @Operation(summary = "예지보전 센서 과거 데이터 채우기 (시연·개발용)",
            description = "예지보전 설비마다 endAt(기본 지금)에서 weeks(기본 14)주 전부터 센서 주기대로 값을 만든다. "
                    + "앞 normalWeeks(기본 4)주는 정상, 그 뒤는 설비 종류별 번호 순서대로 위험 악화 / 주의 악화 / 정상 유지 / 일시적 튐 시나리오가 들어간다. "
                    + "기본은 센서 데이터가 아직 없는 설비만 채우고(이미 있으면 건너뜀), reset=true 면 지우고 다시 만든다. "
                    + "equipIds 로 대상 설비를 고를 수 있다. 펌프(10초 간격)는 양이 많아 수십 초 걸린다.")
    @PostMapping("/pm/sensor-backfill")
    public ApiResponse<PmSensorBackfillResponse> sensorBackfill(
            @RequestParam(name = "weeks", required = false) Integer weeks,
            @RequestParam(name = "normalWeeks", required = false) Integer normalWeeks,
            @RequestParam(name = "endAt", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(name = "reset", defaultValue = "false") boolean reset,
            @RequestParam(name = "equipIds", required = false) List<Long> equipIds) {
        return ApiResponse.ok(pmSensorBackfillService.backfill(weeks, normalWeeks, endAt, reset, equipIds));
    }

    @Operation(summary = "예지보전 점수 수신 (묶음)", description = "점수를 저장하고 설비 임계치로 단계를 판정한다. 같은 설비·같은 시각은 건너뛴다. "
            + "backfill=false 이면 점수가 위험 임계치를 넘어서거나(위험진입) 주의 이상이면서 24시간 최저보다 25점 이상 오를 때(급상승) 점검 이벤트·관리자 알림이 나가고, "
            + "pm-update SSE 가 나간다(201). 위험 단계가 되면 긴급 알림이다. 주의 단계에 머무는 것만으로는 이벤트가 생기지 않는다. "
            + "처리 중인 이벤트가 있으면 새로 만들지 않고 단계가 오를 때만 올린다.")
    @PostMapping("/pm-scores")
    public ResponseEntity<ApiResponse<PmScoreIngestResponse>> receiveScores(@Valid @RequestBody PmScoreIngestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(pmIngestService.receiveScores(request)));
    }
}
