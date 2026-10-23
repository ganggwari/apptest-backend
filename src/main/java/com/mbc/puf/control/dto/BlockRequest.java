package com.mbc.puf.control.dto;

import jakarta.validation.constraints.Size;

/** 지하차도 차단/해제 요청 (사유와 전광판 문구는 모두 선택) */
public class BlockRequest {

    private Long presetId;   // 프리셋 선택 시 (직접 입력이면 null)

    @Size(max = 200, message = "사유는 200자 이내로 입력해 주세요.")
    private String reason;   // 직접 입력 시 (프리셋 선택이면 null)

    @Size(max = 200, message = "상세 사유는 200자 이내로 입력해 주세요.")
    private String reasonNote;   // 사유(프리셋 또는 직접 입력)에 덧붙이는 상세 사유. 선택이지만 프리셋이 '기타'이면 필수

    private Long displayPresetId;   // 전광판 문구를 프리셋에서 고를 때 (display_preset 의 id. 차단이면 BLOCK, 해제면 RELEASE 분류만 가능)

    @Size(max = 100, message = "전광판 문구는 100자 이내로 입력해 주세요.")
    private String displayContent;  // 전광판 문구를 직접 입력할 때 (있으면 프리셋보다 우선). 둘 다 없으면 기본 프리셋 문구

    public Long getPresetId() { return presetId; }
    public void setPresetId(Long presetId) { this.presetId = presetId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getReasonNote() { return reasonNote; }
    public void setReasonNote(String reasonNote) { this.reasonNote = reasonNote; }

    public Long getDisplayPresetId() { return displayPresetId; }
    public void setDisplayPresetId(Long displayPresetId) { this.displayPresetId = displayPresetId; }

    public String getDisplayContent() { return displayContent; }
    public void setDisplayContent(String displayContent) { this.displayContent = displayContent; }
}