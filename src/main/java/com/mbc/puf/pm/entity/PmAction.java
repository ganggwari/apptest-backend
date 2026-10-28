package com.mbc.puf.pm.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.mbc.puf.common.enums.PmActionKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 예지보전 조치 기록 (테이블: pm_action). 점검 이력의 원본. 이벤트 1건에 여러 건 쌓일 수 있다 */
@Entity
@Table(name = "pm_action")
public class PmAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "content")
    private String content;

    @Column(name = "manager_id")
    private Long managerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private PmActionKind kind = PmActionKind.RECORD;   // 점검 시작 / 기록 추가 / 조치 완료 / 기타 종료

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public PmAction() {
    }

    public Long getId() { return id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public PmActionKind getKind() { return kind; }
    public void setKind(PmActionKind kind) { this.kind = kind; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
