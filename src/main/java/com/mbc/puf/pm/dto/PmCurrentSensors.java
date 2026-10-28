package com.mbc.puf.pm.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** Latest raw sensor row, independent of model scores and hourly trend averages. */
public record PmCurrentSensors(LocalDateTime recordedAt, Map<String, Double> values) {
}
