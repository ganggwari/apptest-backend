package com.mbc.puf.handover.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.pm.dto.PmPeriodSummary;

/**
 * 인수인계 자동 집계. 작성하는 순간의 스냅샷이 shift_handover.summary 에 JSON 으로 저장된다.
 *  - counts      : 그 근무 시간대에 일어난 일 건수 (프런트 팝업의 통제 / 위험 상승 / 점검 / 미처리 점검)
 *  - blockedNow  : 작성 시점에 아직 차단 중인 지하차도,  elevatedNow : 차단은 아니지만 위험 단계가 안전이 아닌 지하차도 (다음 근무조가 이어받을 것)
 *  - pm          : 예지보전 요약 (단계별 설비 수, 신규 점검 대기, 점검 기록, 처리 중인 점검 = 이월, 데이터 미수신)
 *  - staff       : 이 근무 시간대 직원별로 한 일 (그 근무조 소속 + 실제로 뭔가 처리한 사람). 많이 한 순
 *  - activities  : 시간 역순 업무 기록 (통제·위험 단계 변화·점검 대기 발생·점검 기록·전광판 문구 수정). actor 는 처리한 직원 이름(자동이면 null)
 */
public record HandoverSummary(
        LocalDateTime windowStart,
        LocalDateTime windowEnd,
        Counts counts,
        List<UnderpassState> blockedNow,
        List<UnderpassState> elevatedNow,
        PmPeriodSummary pm,
        List<StaffWork> staff,
        List<Activity> activities) {

    /**
     * block 통제 시작 / release 통제 해제 / riskUp 위험 단계 상승 / riskDown 하강 /
     * pmNew 새 점검 대기 / pmChecked 점검 기록 / pmPending 지금 처리 중인 점검
     */
    public record Counts(int block, int release, int riskUp, int riskDown, int pmNew, int pmChecked, int pmPending) {
    }

    public record UnderpassState(Long underpassId, String name, RiskLevel riskLevel) {
    }

    /** category: 통제 / 위험 / 설비 / 점검 / 전광판. actor: 처리한 직원 이름 (자동 처리나 시스템이면 null) */
    public record Activity(LocalDateTime at, String category, String content, String actor) {
    }

    /**
     * 직원 1명이 이 근무 시간대에 한 일 (내 담당 지역 안에서).
     * assigned     : 이 근무조 소속이라 원래 근무해야 하는 사람 (안 한 일이 없어도 목록에 나온다)
     * block·release: 수동 통제 시작·해제,  plateReview: 번호판 수동 판별,  pmAction: 예지보전 점검 기록,
     * displayEdit  : 전광판 문구 직접 수정,  transfer: 경찰·소방 이관 발송,  inquiryAnswer: 접수의견 답변 (지역 구분 없음)
     * total        : 위 항목 합
     */
    public record StaffWork(Long managerId, String name, String phone, boolean assigned, int block, int release,
            int plateReview, int pmAction, int displayEdit, int transfer, int inquiryAnswer, int total) {
    }
}
