package com.mbc.puf.control.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 소방 이관 시점 잔류차량 번호 스냅샷 (테이블: ctrl_transfer_car) */
@Entity
@Table(name = "ctrl_transfer_car")
public class CtrlTransferCar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "ctrl_transfer_id", nullable = false)
    private Long ctrlTransferId;

    @Column(name = "car_num", nullable = false, length = 20)
    private String carNum;

    public CtrlTransferCar() {}

    public Long getId() { return id; }

    public Long getCtrlTransferId() { return ctrlTransferId; }
    public void setCtrlTransferId(Long ctrlTransferId) { this.ctrlTransferId = ctrlTransferId; }

    public String getCarNum() { return carNum; }
    public void setCarNum(String carNum) { this.carNum = carNum; }
}
