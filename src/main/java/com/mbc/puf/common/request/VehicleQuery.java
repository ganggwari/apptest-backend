package com.mbc.puf.common.request;

/**
 * [공통 조건 묶음 4: Vehicle] 차량이 나오는 목록 공통.  ?carNum=12가3456
 * 공백을 무시한 포함 검색 ("12가" 만 넣어도 검색됨).
 */
public class VehicleQuery {

    private String carNum;

    public VehicleQuery() {
    }

    public String getCarNum() { return carNum; }
    public void setCarNum(String carNum) { this.carNum = carNum; }
}
