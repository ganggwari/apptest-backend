package com.mbc.puf.board.dto;

import com.mbc.puf.common.enums.NoticeCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공지사항 등록 요청 */
public class NoticeCreateRequest {

    @NotBlank(message = "제목은 필수입니다.")
    @Size(max = 200, message = "제목은 200자 이하여야 합니다.")   // notice.title VARCHAR(200)
    private String title;

    @NotBlank(message = "내용은 필수입니다.")
    private String content;

    private NoticeCategory category;   // INFO/MAINTENANCE/UPDATE/URGENT, 안 보내면 INFO

    private Boolean sendPush;   // 고객에게 알림을 보낼지. 안 보내면 긴급 분류만 보냄, 나머지는 안 보냄

    private boolean important;   // 안 보내면 false

    public NoticeCreateRequest() {
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public NoticeCategory getCategory() { return category; }
    public void setCategory(NoticeCategory category) { this.category = category; }

    public Boolean getSendPush() { return sendPush; }
    public void setSendPush(Boolean sendPush) { this.sendPush = sendPush; }

    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }
}