package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 예지보전 단계 기준 수정 요청 (PUT /api/pm/thresholds).
 * 안전은 항상 0점부터이고, 주의·위험이 시작되는 점수만 바꾼다 (0 &lt; 주의 &lt; 위험 &lt;= 100).
 * 설비를 여러 개 한 번에 고를 수 있다 (같은 지하차도의 같은 종류 설비에 한꺼번에 적용하는 화면용).
 */
public class PmThresholdUpdateRequest {

    @NotEmpty(message = "equipIds는 필수입니다.")
    @Size(max = 200, message = "equipIds는 한 번에 200개까지 보낼 수 있습니다.")
    private List<Long> equipIds;

    @NotNull(message = "cautionMin은 필수입니다.")
    @DecimalMin(value = "0.01", message = "cautionMin은 0보다 커야 합니다.")
    @DecimalMax(value = "100.00", message = "cautionMin은 100 이하여야 합니다.")
    private BigDecimal cautionMin;

    @NotNull(message = "dangerMin은 필수입니다.")
    @DecimalMin(value = "0.01", message = "dangerMin은 0보다 커야 합니다.")
    @DecimalMax(value = "100.00", message = "dangerMin은 100 이하여야 합니다.")
    private BigDecimal dangerMin;

    public PmThresholdUpdateRequest() {
    }

    public List<Long> getEquipIds() { return equipIds; }
    public void setEquipIds(List<Long> equipIds) { this.equipIds = equipIds; }

    public BigDecimal getCautionMin() { return cautionMin; }
    public void setCautionMin(BigDecimal cautionMin) { this.cautionMin = cautionMin; }

    public BigDecimal getDangerMin() { return dangerMin; }
    public void setDangerMin(BigDecimal dangerMin) { this.dangerMin = dangerMin; }
}
