package com.mbc.puf.board.entity;

import jakarta.persistence.Column;
import com.mbc.puf.common.enums.FaqCategory;
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

/** 자주묻는질문(질문/답변 1행 구성) (테이블: faq) */
@Entity
@Table(name = "faq")
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "question")
    private String question;

    @Column(name = "answer")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private FaqCategory category = FaqCategory.ETC;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;   // 목록 표시 순서(작을수록 위). 새 FAQ는 맨 끝 번호

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

    public Faq() {
    }

    public Long getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public FaqCategory getCategory() {
        return category;
    }

    public void setCategory(FaqCategory category) {
        this.category = category;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
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