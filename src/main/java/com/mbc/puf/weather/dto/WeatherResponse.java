package com.mbc.puf.weather.dto;

import java.math.BigDecimal;
import java.util.List;

/** 고객 웹 날씨 화면(CP-08) 응답. 필드명은 weather.vue의 Weather / Hour / Day 타입과 같다 */
public class WeatherResponse {

    private final String areaName;                    // 조회된 날씨 지역(가장 가까운 곳)
    private final BigDecimal temperature;             // 현재 기온(℃)
    private final Integer humidity;                   // 현재 습도(%)
    private final BigDecimal rainfall;                // 현재 강우량(mm, 최근 1시간)
    private final String condition;                   // 현재 날씨 상태(맑음/구름많음/흐림/비/소나기)
    private final String observedAt;                  // 관측 시각(yyyy-MM-dd HH:mm)
    private final List<Hour> hourly;                  // 시간별 예보
    private final List<Day> daily;                    // 일별 예보

    public WeatherResponse(String areaName, BigDecimal temperature, Integer humidity, BigDecimal rainfall,
                           String condition, String observedAt, List<Hour> hourly, List<Day> daily) {
        this.areaName = areaName;
        this.temperature = temperature;
        this.humidity = humidity;
        this.rainfall = rainfall;
        this.condition = condition;
        this.observedAt = observedAt;
        this.hourly = hourly;
        this.daily = daily;
    }

    public String getAreaName() { return areaName; }
    public BigDecimal getTemperature() { return temperature; }
    public Integer getHumidity() { return humidity; }
    public BigDecimal getRainfall() { return rainfall; }
    public String getCondition() { return condition; }
    public String getObservedAt() { return observedAt; }
    public List<Hour> getHourly() { return hourly; }
    public List<Day> getDaily() { return daily; }

    /** 시간별 예보 한 칸 */
    public static class Hour {
        private final String time;                        // "15시"
        private final BigDecimal temperature;
        private final BigDecimal rainfall;
        private final Integer precipitationProbability;

        public Hour(String time, BigDecimal temperature, BigDecimal rainfall, Integer precipitationProbability) {
            this.time = time;
            this.temperature = temperature;
            this.rainfall = rainfall;
            this.precipitationProbability = precipitationProbability;
        }

        public String getTime() { return time; }
        public BigDecimal getTemperature() { return temperature; }
        public BigDecimal getRainfall() { return rainfall; }
        public Integer getPrecipitationProbability() { return precipitationProbability; }
    }

    /** 일별 예보 한 칸 */
    public static class Day {
        private final String date;                        // "9/29(화)"
        private final BigDecimal minTemperature;
        private final BigDecimal maxTemperature;
        private final Integer precipitationProbability;

        public Day(String date, BigDecimal minTemperature, BigDecimal maxTemperature, Integer precipitationProbability) {
            this.date = date;
            this.minTemperature = minTemperature;
            this.maxTemperature = maxTemperature;
            this.precipitationProbability = precipitationProbability;
        }

        public String getDate() { return date; }
        public BigDecimal getMinTemperature() { return minTemperature; }
        public BigDecimal getMaxTemperature() { return maxTemperature; }
        public Integer getPrecipitationProbability() { return precipitationProbability; }
    }
}
