package com.mbc.puf.car.service;

import java.util.List;

import com.mbc.puf.car.dto.CarRegisterRequest;
import com.mbc.puf.car.dto.CarResponse;
import com.mbc.puf.car.dto.CarUpdateRequest;

public interface CarService {

    List<CarResponse> getMyCars(Long customerId);
    CarResponse registerCar(Long customerId, CarRegisterRequest request);
    CarResponse updateCar(Long customerId, Long carId, CarUpdateRequest request);
    void deleteCar(Long customerId, Long carId);
}