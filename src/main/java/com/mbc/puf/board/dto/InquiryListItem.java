package com.mbc.puf.board.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.mbc.puf.common.enums.InquiryStatus;

/** 접수의견 원글 목록의 한 항목. 고객 본인 목록(CP-03)과 관리자 목록(MP-13)이 공용으로 쓴다 */
public class InquiryListItem {

    private Long id;
    private String customerName;
    private String title;
    private InquiryStatus status;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;

    public InquiryListItem() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public InquiryStatus getStatus() { return status; }
    public void setStatus(InquiryStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}