package com.mbc.puf.pm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 점검 수동 등록 요청 (POST /api/pm/events). 점수와 상관없이 관리자가 직접 점검 대기를 만든다 */
public class PmEventCreateRequest {

    @NotNull(message = "equipId는 필수입니다.")
    private Long equipId;

    @Size(max = 30, message = "reason은 30자 이하여야 합니다.")
    private String reason;      // 점검 사유 (현장 이상 발견 / 정기 점검 / 민원·제보 / 기타). 선택

    @Size(max = 10, message = "priority는 10자 이하여야 합니다.")
    private String priority;    // 우선순위 (보통 / 높음). 선택

    @NotBlank(message = "content는 필수입니다.")
    @Size(max = 1900, message = "content는 1900자 이하여야 합니다.")
    private String content;     // 점검 내용 (첫 점검 기록으로 남는다)

    public PmEventCreateRequest() {
    }

    public Long getEquipId() { return equipId; }
    public void setEquipId(Long equipId) { this.equipId = equipId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
