package com.mbc.puf.board.entity;

import com.mbc.puf.common.enums.InquiryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;

/** 접수의견(1:1 문의). parent_id로 원글/답글 구분 (테이블: inquiry) */
@Entity
@Table(name = "inquiry")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "manager_id")
    private Long authorManagerId;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private InquiryStatus status = InquiryStatus.PENDING;

    @Column(name = "is_deleted")
    private boolean deleted = false;

    @Column(name = "view_count", insertable = false, updatable = false)
    private int viewCount;   // 조회수. 저장 시엔 DB 기본값 0, 증가는 UPDATE ... + 1 로 따로 처리(덮어쓰기 방지)

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Inquiry() {
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getAuthorManagerId() {
        return authorManagerId;
    }

    public void setAuthorManagerId(Long authorManagerId) {
        this.authorManagerId = authorManagerId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public void setStatus(InquiryStatus status) {
        this.status = status;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getViewCount() {
        return viewCount;
    }
}