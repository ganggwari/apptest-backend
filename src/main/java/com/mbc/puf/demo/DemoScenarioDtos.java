package com.mbc.puf.demo;

import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 호우 시나리오 요청·응답 */
public final class DemoScenarioDtos {

    private DemoScenarioDtos() {
    }

    /** 시작 요청 */
    public static class StartRequest {

        @NotEmpty(message = "regionIds는 1개 이상이어야 합니다.")
        @Size(max = 20, message = "regionIds가 너무 많습니다.")
        private List<Long> regionIds;

        // 진행 속도 배율 (1.0 = 약 2분 50초). 2.0 이면 절반 시간. 안 보내면 1.0
        @DecimalMin(value = "0.5", message = "speed는 0.5 이상이어야 합니다.")
        @DecimalMax(value = "3.0", message = "speed는 3.0 이하여야 합니다.")
        private Double speed;

        public StartRequest() {
        }

        public List<Long> getRegionIds() { return regionIds; }
        public void setRegionIds(List<Long> regionIds) { this.regionIds = regionIds; }

        public Double getSpeed() { return speed; }
        public void setSpeed(Double speed) { this.speed = speed; }
    }

    /** 진행 단계 한 칸 (초는 실제로 흐르는 시간 기준, 시작 버튼을 누른 순간이 0초) */
    public record Step(String key, String label, int fromSec, int toSec) {}

    /** 현재 상태. running=false 면 phase 등은 null/빈 값 */
    public record Status(boolean running, String phase, String phaseLabel, int elapsedSec, int totalSec,
                         List<Long> regionIds, List<Long> underpassIds, double speed, List<Step> steps) {}
}
