package com.mbc.puf.board.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.NoticeCategory;

import com.fasterxml.jackson.annotation.JsonFormat;

/** 공지사항 상세 응답 */
public class NoticeDetailResponse {

    private Long id;
    private String title;
    private String content;
    private NoticeCategory category;   // INFO/MAINTENANCE/UPDATE/URGENT
    private String categoryLabel;      // 안내/점검/업데이트/긴급 (화면 표시용)
    private boolean important;
    private boolean sendPush;      // 등록할 때 고객에게 알림을 보냈는지
    private int viewCount;         // 조회수
    private Long authorId;
    private String authorName;   // manager.name
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    public NoticeDetailResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public NoticeCategory getCategory() { return category; }
    public void setCategory(NoticeCategory category) {
        this.category = category;
        this.categoryLabel = category == null ? null : category.getLabel();
    }

    public String getCategoryLabel() { return categoryLabel; }

    public boolean isSendPush() { return sendPush; }
    public void setSendPush(boolean sendPush) { this.sendPush = sendPush; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public boolean isImportant() { return important; }
    public void setImportant(boolean important) { this.important = important; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}