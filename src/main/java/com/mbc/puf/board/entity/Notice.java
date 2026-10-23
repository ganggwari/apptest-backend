package com.mbc.puf.board.entity;

import jakarta.persistence.Column;
import com.mbc.puf.common.enums.NoticeCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

/** 공지사항 (테이블: notice) */
@Entity
@Table(name = "notice")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "is_important")
    private boolean important = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private NoticeCategory category = NoticeCategory.INFO;

    @Column(name = "send_push", nullable = false)
    private boolean sendPush = false;   // 등록할 때 고객에게 알림을 보냈는지(관리자가 체크). 긴급 분류는 기본 체크

    @Column(name = "manager_id")
    private Long authorId;

    @Column(name = "is_deleted")
    private boolean deleted = false;

    @Column(name = "view_count", insertable = false, updatable = false)
    private int viewCount;   // 조회수. 저장 시엔 DB 기본값 0, 증가는 UPDATE ... + 1 로 따로 처리(덮어쓰기 방지)

    @Generated   // DB 기본값(NOW())으로 INSERT 시 채워지고, 저장 직후 값을 다시 읽어온다
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Generated(event = EventType.INSERT)   // INSERT 땐 DB 기본값(NOW()), 수정 시엔 서비스가 DbClock 시각을 넣는다
    @Column(name = "updated_at", insertable = false)
    private LocalDateTime updatedAt;

    public Notice() {
    }

    public Long getId() {
        return id;
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

    public boolean isImportant() {
        return important;
    }

    public void setImportant(boolean important) {
        this.important = important;
    }

    public NoticeCategory getCategory() {
        return category;
    }

    public void setCategory(NoticeCategory category) {
        this.category = category;
    }

    public boolean isSendPush() {
        return sendPush;
    }

    public void setSendPush(boolean sendPush) {
        this.sendPush = sendPush;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public int getViewCount() {
        return viewCount;
    }
}