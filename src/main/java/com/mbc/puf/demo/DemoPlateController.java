package com.mbc.puf.demo;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mbc.puf.cctv.service.OcrStubCctvService;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.security.LoginUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.JsonNode;

/** 시연용: 특정 고객의 차량이 카메라에 인식된 것으로 치고 인식 이후 로직(알림 등)을 한 번 돌린다 */
@Tag(name = "시연(번호판)", description = "시연 전용. puf.demo.enabled=true 이고 puf.ocr.mode=stub 일 때만 동작")
@RestController
@RequestMapping("/api/demo/plate")
@ConditionalOnProperty(name = "puf.demo.enabled", havingValue = "true")
public class DemoPlateController {

    private final ObjectProvider<OcrStubCctvService> stub;
    private final JdbcTemplate jdbc;

    public DemoPlateController(ObjectProvider<OcrStubCctvService> stub, JdbcTemplate jdbc) {
        this.stub = stub;
        this.jdbc = jdbc;
    }

    public static class FireRequest {
        private Long cameraId;
        private String loginId = "customer01";
        private String carNum;

        public Long getCameraId() { return cameraId; }
        public void setCameraId(Long cameraId) { this.cameraId = cameraId; }
        public String getLoginId() { return loginId; }
        public void setLoginId(String loginId) { this.loginId = loginId; }
        public String getCarNum() { return carNum; }
        public void setCarNum(String carNum) { this.carNum = carNum; }
    }

    @Operation(summary = "고객 차량 번호판 목록", description = "loginId(기본 customer01) 고객이 등록한 차량번호")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @GetMapping("/cars")
    public ApiResponse<List<String>> cars(@RequestParam(name = "loginId", defaultValue = "customer01") String loginId) {
        return ApiResponse.ok(carNums(loginId));
    }

    @Operation(summary = "고객 차량 인식 1회", description = "그 고객의 차량번호(carNum 을 주면 그 번호, 아니면 첫 번째 차량)가 cameraId 카메라에 인식된 것으로 "
            + "수신 로직을 한 번 돌린다. 카메라 역할(접근/최종/입·출구)과 지하차도 상태에 따라 알림·입출차 기록이 생긴다. "
            + "같은 카메라·같은 번호는 30초 안에 다시 넣으면 중복으로 건너뛴다(duplicate=true).")
    @PreAuthorize("hasRole('REGIONAL_ADMIN')")
    @PostMapping("/fire")
    public ApiResponse<JsonNode> fire(@AuthenticationPrincipal LoginUser loginUser, @RequestBody FireRequest request) {
        OcrStubCctvService service = stub.getIfAvailable();
        if (service == null) {
            throw new BusinessException(HttpStatus.CONFLICT, "번호판 stub 모드(puf.ocr.mode=stub)가 아닙니다.");
        }
        if (request.getCameraId() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "cameraId 가 필요합니다.");
        }
        String carNum = request.getCarNum();
        if (carNum == null || carNum.isBlank()) {
            List<String> cars = carNums(request.getLoginId());
            if (cars.isEmpty()) {
                throw new BusinessException(HttpStatus.NOT_FOUND, request.getLoginId() + " 고객의 등록 차량이 없습니다.");
            }
            carNum = cars.get(0);
        }
        return ApiResponse.ok(service.fireOnce(loginUser, request.getCameraId(), carNum));
    }

    private List<String> carNums(String loginId) {
        return jdbc.queryForList("""
                SELECT c.car_num FROM car c JOIN customer cu ON cu.id = c.customer_id
                WHERE cu.login_id = ? AND cu.is_withdrawn = FALSE AND c.is_deleted = FALSE
                ORDER BY c.id
                """, String.class, loginId);
    }
}
