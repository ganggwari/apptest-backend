package com.mbc.puf.weather.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 날씨 예보 (테이블: weather_forecast). 같은 지역+대상시각은 덮어쓰고, 지난 시각 행은 지운다 */
@Entity
@Table(name = "weather_forecast")
public class WeatherForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "area_id")
    private Long areaId;

    @Column(name = "temp")
    private BigDecimal temperatureC;

    @Column(name = "humidity")
    private Integer humidityPct;

    @Column(name = "rain")
    private BigDecimal rainfallMm;

    @Column(name = "rain_prob")
    private Integer precipitationProb;

    @Column(name = "sky")
    private String sky;

    @Column(name = "target_at")
    private LocalDateTime targetAt;

    @Column(name = "predicted_at")
    private LocalDateTime predictedAt;

    public WeatherForecast() {
    }

    public Long getId() { return id; }

    public Long getAreaId() { return areaId; }
    public void setAreaId(Long areaId) { this.areaId = areaId; }

    public BigDecimal getTemperatureC() { return temperatureC; }
    public void setTemperatureC(BigDecimal temperatureC) { this.temperatureC = temperatureC; }

    public Integer getHumidityPct() { return humidityPct; }
    public void setHumidityPct(Integer humidityPct) { this.humidityPct = humidityPct; }

    public BigDecimal getRainfallMm() { return rainfallMm; }
    public void setRainfallMm(BigDecimal rainfallMm) { this.rainfallMm = rainfallMm; }

    public Integer getPrecipitationProb() { return precipitationProb; }
    public void setPrecipitationProb(Integer precipitationProb) { this.precipitationProb = precipitationProb; }

    public String getSky() { return sky; }
    public void setSky(String sky) { this.sky = sky; }

    public LocalDateTime getTargetAt() { return targetAt; }
    public void setTargetAt(LocalDateTime targetAt) { this.targetAt = targetAt; }

    public LocalDateTime getPredictedAt() { return predictedAt; }
    public void setPredictedAt(LocalDateTime predictedAt) { this.predictedAt = predictedAt; }
}
