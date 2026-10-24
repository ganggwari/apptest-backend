package com.mbc.puf.pm.dto;

import com.mbc.puf.common.enums.PmEventStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 점검 대기 상태 변경 요청 (PATCH /api/pm/events/{eventId}/status) */
public class PmEventStatusRequest {

    @NotNull(message = "status는 필수입니다.")
    private PmEventStatus status;      // IN_PROGRESS(점검중) / RESOLVED(조치완료) / ETC(기타 종료)

    @Size(max = 2000, message = "content는 2000자 이하여야 합니다.")
    private String content;            // 조치 내용. RESOLVED·ETC 로 끝낼 때는 필수, 점검중으로 바꿀 때는 선택

    public PmEventStatusRequest() {
    }

    public PmEventStatus getStatus() { return status; }
    public void setStatus(PmEventStatus status) { this.status = status; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
