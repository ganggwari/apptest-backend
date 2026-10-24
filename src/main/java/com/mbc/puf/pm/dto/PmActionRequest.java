package com.mbc.puf.pm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 점검 기록 추가 요청 (POST /api/pm/events/{eventId}/actions). 상태는 바꾸지 않고 조치 내용만 쌓는다 */
public class PmActionRequest {

    @NotBlank(message = "content는 필수입니다.")
    @Size(max = 2000, message = "content는 2000자 이하여야 합니다.")
    private String content;

    public PmActionRequest() {
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
