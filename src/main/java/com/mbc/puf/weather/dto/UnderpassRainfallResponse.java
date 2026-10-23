package com.mbc.puf.weather.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 지하차도 강수 (FastAPI 침수 모델 입력용). 가장 가까운 날씨 지역의 관측값(weather_hourly)으로 계산.
 *  강수 필드명은 FastAPI가 Spring에 보내는 위험도 요청(RiskScoreCreateRequest)과 같다 */
public class UnderpassRainfallResponse {

    private Long underpassId;
    private String underpassName;
    private String areaName;              // 강수를 가져온 날씨 지역
    private LocalDateTime observedAt;     // 기준 관측 시각(정시)
    private BigDecimal rainfallMm;        // 이번 시간
    private BigDecimal rainfall1hMm;      // 직전 1시간
    private BigDecimal rainfall3hMm;      // 최근 3시간 누적(이번 시간 포함)
    private BigDecimal rainfall6hMm;
    private BigDecimal rainfall12hMm;
    private BigDecimal rainfall24hMm;

    public UnderpassRainfallResponse() {
    }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public String getAreaName() { return areaName; }
    public void setAreaName(String areaName) { this.areaName = areaName; }

    public LocalDateTime getObservedAt() { return observedAt; }
    public void setObservedAt(LocalDateTime observedAt) { this.observedAt = observedAt; }

    public BigDecimal getRainfallMm() { return rainfallMm; }
    public void setRainfallMm(BigDecimal rainfallMm) { this.rainfallMm = rainfallMm; }

    public BigDecimal getRainfall1hMm() { return rainfall1hMm; }
    public void setRainfall1hMm(BigDecimal rainfall1hMm) { this.rainfall1hMm = rainfall1hMm; }

    public BigDecimal getRainfall3hMm() { return rainfall3hMm; }
    public void setRainfall3hMm(BigDecimal rainfall3hMm) { this.rainfall3hMm = rainfall3hMm; }

    public BigDecimal getRainfall6hMm() { return rainfall6hMm; }
    public void setRainfall6hMm(BigDecimal rainfall6hMm) { this.rainfall6hMm = rainfall6hMm; }

    public BigDecimal getRainfall12hMm() { return rainfall12hMm; }
    public void setRainfall12hMm(BigDecimal rainfall12hMm) { this.rainfall12hMm = rainfall12hMm; }

    public BigDecimal getRainfall24hMm() { return rainfall24hMm; }
    public void setRainfall24hMm(BigDecimal rainfall24hMm) { this.rainfall24hMm = rainfall24hMm; }
}
