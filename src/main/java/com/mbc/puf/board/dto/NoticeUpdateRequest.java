package com.mbc.puf.board.dto;

import com.mbc.puf.common.enums.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지사항 수정 요청 (제목/내용/중요여부 전체 교체) */
public class NoticeUpdateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private NoticeCategory category;   // INFO/MAINTENANCE/UPDATE/URGENT, 안 보내면 INFO

    private boolean important;

    public NoticeUpdateRequest() {
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public NoticeCategory getCategory() { return category; }
    public void setCategory(NoticeCategory category) { this.category = category; }

    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
}