package com.mbc.puf.car.controller;

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

import com.mbc.puf.car.dto.CarRegisterRequest;
import com.mbc.puf.car.dto.CarResponse;
import com.mbc.puf.car.dto.CarUpdateRequest;
import com.mbc.puf.car.service.CarService;
import com.mbc.puf.common.response.ApiResponse;
import com.mbc.puf.common.security.LoginUser;

import jakarta.validation.Valid;

/** 고객 마이페이지의 차량 등록/조회/수정/삭제. 전부 본인 소유 차량만 다룬다 */
@RestController
@RequestMapping("/api/customers/me/cars")
@PreAuthorize("hasRole('CUSTOMER')")
public class CarController {

    private final CarService carService;

    public CarController(CarService carService) {
        this.carService = carService;
    }

    @GetMapping
    public ApiResponse<List<CarResponse>> list(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(carService.getMyCars(loginUser.id()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CarResponse>> register(@AuthenticationPrincipal LoginUser loginUser,
                                                              @Valid @RequestBody CarRegisterRequest request) {
        CarResponse response = carService.registerCar(loginUser.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("차량이 등록되었습니다.", response));
    }

    // 차량번호 수정 (본인 소유 차량만)
    @PutMapping("/{carId}")
    public ApiResponse<CarResponse> update(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable("carId") Long carId,
                                            @Valid @RequestBody CarUpdateRequest request) {
        return ApiResponse.ok("차량 정보가 수정되었습니다.", carService.updateCar(loginUser.id(), carId, request));
    }

    @DeleteMapping("/{carId}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser, @PathVariable("carId") Long carId) {
        carService.deleteCar(loginUser.id(), carId);
        return ApiResponse.ok("차량이 삭제되었습니다.", null);
    }
}