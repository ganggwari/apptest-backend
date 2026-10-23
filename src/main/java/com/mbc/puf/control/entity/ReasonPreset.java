package com.mbc.puf.control.entity;

import com.mbc.puf.common.enums.ReasonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 차단/해제 사유 프리셋 (테이블: reason_preset) */
@Entity
@Table(name = "reason_preset")
public class ReasonPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ReasonType type;   // BLOCK(차단 사유) / RELEASE(해제 사유)

    @Column(name = "content", nullable = false, length = 200)
    private String content;

    public ReasonPreset() {}

    public Long getId() { return id; }

    public ReasonType getType() { return type; }
    public void setType(ReasonType type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}