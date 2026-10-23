package com.mbc.puf.board.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 1:1 문의(접수의견) 등록 요청 (고객, CP-03) */
public class InquiryCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")   // inquiry.title VARCHAR(200)
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    public InquiryCreateRequest() {
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
