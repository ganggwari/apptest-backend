package com.mbc.puf.display.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 전광판 수동 페이지 생성/수정 요청.
 * content 는 줄바꿈("\n")으로 줄을 나눈다. underpassId 는 생성할 때만 필요하다(원인 지하차도, 이 전광판에 연결된 지하차도 권장).
 */
public class DisplayPageSaveRequest {

    private Long underpassId;   // 생성할 때 필수. 수정할 때는 무시

    private Long presetId;   // 프리셋 선택 시 (직접 입력이면 null)

    @NotBlank(message = "표출 문구는 필수입니다.")
    @Size(max = 100, message = "표출 문구는 100자 이내로 입력해 주세요.")
    private String content;

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public Long getPresetId() { return presetId; }
    public void setPresetId(Long presetId) { this.presetId = presetId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
