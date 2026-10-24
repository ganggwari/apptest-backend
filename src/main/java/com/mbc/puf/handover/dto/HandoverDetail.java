package com.mbc.puf.handover.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import tools.jackson.databind.JsonNode;

/**
 * 인수인계 리포트 1건.
 *  - written=false 이면 아직 아무도 쓰지 않아서 서버가 그 시간대를 바로 집계해 보여 주는 것 (id·memo·작성자 없음)
 *  - authorName·authorPhone : 이전 근무자에게 연락해야 하므로 이름도 연락처도 가리지 않고 그대로 준다
 */
public record HandoverDetail(
        Long id,
        boolean written,
        Long shiftId,
        String shiftName,
        LocalDate shiftDate,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        Long managerId,
        String authorName,
        String authorPhone,
        String memo,
        LocalDateTime createdAt,
        JsonNode summary) {
}
