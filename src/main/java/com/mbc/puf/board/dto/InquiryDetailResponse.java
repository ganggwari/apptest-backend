package com.mbc.puf.board.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.mbc.puf.common.enums.InquiryStatus;

/** 접수의견 상세 응답. 원글 + 답글(있으면)을 한 번에 담는다.
 *  고객 본인 조회(CP-03)와 관리자 조회(MP-13)가 공용으로 쓴다 */
public class InquiryDetailResponse {

    private Long id;
    private String customerName;
    private String title;
    private String content;
    private InquiryStatus status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
    private String replyContent;       // 답글 없으면 null
    private String replyAuthorName;    // 답글 없으면 null
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime repliedAt;   // 답글 없으면 null

    public InquiryDetailResponse() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public InquiryStatus getStatus() { return status; }
    public void setStatus(InquiryStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getReplyContent() { return replyContent; }
    public void setReplyContent(String replyContent) { this.replyContent = replyContent; }

    public String getReplyAuthorName() { return replyAuthorName; }
    public void setReplyAuthorName(String replyAuthorName) { this.replyAuthorName = replyAuthorName; }

    public LocalDateTime getRepliedAt() { return repliedAt; }
    public void setRepliedAt(LocalDateTime repliedAt) { this.repliedAt = repliedAt; }
}