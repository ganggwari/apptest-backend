package com.mbc.puf.board.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.FaqCategory;

import com.fasterxml.jackson.annotation.JsonFormat;

/** FAQ 목록의 한 항목. 목록에서는 답변(answer)을 내려주지 않는다 */
public class FaqListItem {

    private Long id;
    private FaqCategory category;   // USAGE/NOTIFICATION/VEHICLE/ACCOUNT/ETC
    private String categoryLabel;    // 이용 방법/알림·푸시/차량·번호판/계정/기타 (화면 표시용)
    private int sortOrder;         // 표시 순서(작을수록 위)
    private int viewCount;         // 조회수
    private String question;
    private String authorName;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    public FaqListItem() {
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

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}