package com.mbc.puf.car.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

public class CarResponse {

    private final Long id;
    private final String carNum;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private final LocalDateTime createdAt;

    public CarResponse(Long id, String carNum, LocalDateTime createdAt) {
        this.id = id;
        this.carNum = carNum;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getCarNum() { return carNum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}