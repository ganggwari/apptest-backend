package com.mbc.puf.board.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.FaqCategory;

import com.fasterxml.jackson.annotation.JsonFormat;

/** FAQ 상세 응답 */
public class FaqDetailResponse {

    private Long id;
    private FaqCategory category;   // USAGE/NOTIFICATION/VEHICLE/ACCOUNT/ETC
    private String categoryLabel;    // 이용 방법/알림·푸시/차량·번호판/계정/기타 (화면 표시용)
    private int sortOrder;         // 표시 순서(작을수록 위)
    private int viewCount;         // 조회수
    private String question;
    private String answer;
    private Long authorId;
    private String authorName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime updatedAt;

    public FaqDetailResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FaqCategory getCategory() { return category; }
    public void setCategory(FaqCategory category) {
        this.category = category;
        this.categoryLabel = category == null ? null : category.getLabel();
    }

    public String getCategoryLabel() { return categoryLabel; }

    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }

    public int getViewCount() { return viewCount; }
    public void setViewCount(int viewCount) { this.viewCount = viewCount; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public Long getAuthorId() { return authorId; }
    public void setAuthorId(Long authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}