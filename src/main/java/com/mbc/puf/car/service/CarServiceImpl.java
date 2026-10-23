package com.mbc.puf.car.service;

import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.car.dto.CarRegisterRequest;
import com.mbc.puf.car.dto.CarResponse;
import com.mbc.puf.car.dto.CarUpdateRequest;
import com.mbc.puf.car.entity.Car;
import com.mbc.puf.car.repository.CarRepository;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.util.CarNumber;

@Service
public class CarServiceImpl implements CarService {

    // CP-03 스펙: 차량은 고객당 최대 3대까지 (삭제한 차량은 세지 않음)
    private static final int MAX_CARS_PER_CUSTOMER = 3;

    private final CarRepository carRepository;

    public CarServiceImpl(CarRepository carRepository) {
        this.carRepository = carRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CarResponse> getMyCars(Long customerId) {
        return carRepository.findByCustomerIdAndDeletedFalseOrderByCreatedAtAsc(customerId).stream()
                .map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public CarResponse registerCar(Long customerId, CarRegisterRequest request) {
        String carNum = validCarNum(request.getCarNum());

        if (carRepository.countByCustomerIdAndDeletedFalse(customerId) >= MAX_CARS_PER_CUSTOMER) {
            throw new BusinessException(HttpStatus.CONFLICT, "차량은 최대 " + MAX_CARS_PER_CUSTOMER + "대까지 등록할 수 있습니다.");
        }
        // 다른 고객이 같은 번호를 등록한 건 허용(가족 공유 차량). 같은 고객이 같은 번호를 두 번 등록하는 것만 막는다
        if (carRepository.existsByCustomerIdAndCarNumAndDeletedFalse(customerId, carNum)) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 등록한 차량번호입니다.");
        }

        Car car = new Car();
        car.setCustomerId(customerId);
        car.setCarNum(carNum);
        try {
            carRepository.saveAndFlush(car);   // flush 해야 유니크 위반이 여기서 바로 터진다
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 등록한 차량번호입니다.");
        }
        return toResponse(car);
    }

    @Override
    @Transactional
    public CarResponse updateCar(Long customerId, Long carId, CarUpdateRequest request) {
        String carNum = validCarNum(request.getCarNum());
        Car car = requireMyCar(customerId, carId);
        if (carNum.equals(car.getCarNum())) {
            return toResponse(car);   // 같은 번호로 수정 = 변경 없음
        }
        if (carRepository.existsByCustomerIdAndCarNumAndDeletedFalse(customerId, carNum)) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 등록한 차량번호입니다.");
        }
        car.setCarNum(carNum);
        try {
            carRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 등록한 차량번호입니다.");
        }
        return toResponse(car);
    }

    // 소프트 삭제: 행은 남기고 is_deleted = TRUE. 같은 번호를 다시 등록할 수 있다
    @Override
    @Transactional
    public void deleteCar(Long customerId, Long carId) {
        requireMyCar(customerId, carId).setDeleted(true);
    }

    // ---- 내부 메서드 ----

    // 공백을 지운 뒤 형식 검사. 통과하면 저장할 값(공백 없는 번호)을 돌려준다
    private String validCarNum(String raw) {
        String carNum = CarNumber.normalize(raw);
        if (!CarNumber.isValid(carNum)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST,
                    "차량번호 형식이 올바르지 않습니다. 예: 12가3456, 123가4567, 서울12가3456");
        }
        return carNum;
    }

    // 소유자 본인의 (삭제되지 않은) 차량만 다룰 수 있다. 없거나 남의 차량이면 404
    private Car requireMyCar(Long customerId, Long carId) {
        return carRepository.findByIdAndCustomerIdAndDeletedFalse(carId, customerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 차량이거나 본인 소유가 아닙니다."));
    }

    private CarResponse toResponse(Car car) {
        return new CarResponse(car.getId(), car.getCarNum(), car.getCreatedAt());
    }
}
