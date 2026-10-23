package com.mbc.puf.member.dto;

import java.time.LocalTime;

/** 근무조 (3교대 시간대) */
public record ShiftResponse(Long id, String name, LocalTime startTime, LocalTime endTime) {
}
