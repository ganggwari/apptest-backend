package com.mbc.puf.car.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 차량 등록 요청. 형식 검사는 공백을 지운 뒤 서비스에서 한다(CarNumber).
 * 허용: 12가3456, 123가4567(신형), 서울12가3456(구형)
 */
public class CarRegisterRequest {

    @NotBlank(message = "차량번호는 필수입니다.")
    private String carNum;

    public CarRegisterRequest() {
    }

    public String getCarNum() { return carNum; }
    public void setCarNum(String carNum) { this.carNum = carNum; }
}
