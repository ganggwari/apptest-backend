package com.mbc.puf.common.pdf;

import java.time.LocalDateTime;

/**
 * 도로 통제 정보 전달서(PDF)에 들어갈 내용. 값이 없으면 null 로 두면 "-" 로 표시한다.
 *
 * @param underpassName  지하차도 이름
 * @param address        지하차도 주소(시·구 포함)
 * @param blockEvent     true 면 "통제 시작" 문서, false 면 "통제 해제" 문서
 * @param eventAt        이 문서가 다루는 이벤트(통제 시작 또는 해제) 발생 일시
 * @param reason         그 이벤트의 사유
 * @param reasonNote     사유에 덧붙인 상세 사유 (없으면 null)
 * @param riskScore      이벤트 시점의 위험도 점수 (없으면 null)
 * @param rainfallMm     이벤트 시점의 강우량 mm (없으면 null)
 * @param displayText    전광판 표출 문구 (없으면 null)
 * @param managerName    담당자 이름
 * @param managerRegion  담당자 담당 지역
 * @param shiftLabel     담당자 근무조 (예: "1조 06:00~14:00", 없으면 null)
 * @param blockAt        (해제 문서일 때) 통제 시작 일시
 * @param blockReason    (해제 문서일 때) 통제 시작 사유
 * @param blockReasonNote (해제 문서일 때) 통제 시작 상세 사유
 * @param memo           비고 (없으면 null)
 */
public record ControlNoticeData(
        String underpassName, String address,
        boolean blockEvent, LocalDateTime eventAt, String reason, String reasonNote,
        Double riskScore, Double rainfallMm, String displayText,
        String managerName, String managerRegion, String shiftLabel,
        LocalDateTime blockAt, String blockReason, String blockReasonNote,
        String memo) {
}