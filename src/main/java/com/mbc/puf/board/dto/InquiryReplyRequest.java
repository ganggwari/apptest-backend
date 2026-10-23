package com.mbc.puf.board.dto;

import jakarta.validation.constraints.NotBlank;

/** 접수의견 답변 작성 요청 (관리자, MP-13) */
public class InquiryReplyRequest {

    @NotBlank(message = "답변 내용은 필수입니다.")
    private String content;

    public InquiryReplyRequest() {
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
