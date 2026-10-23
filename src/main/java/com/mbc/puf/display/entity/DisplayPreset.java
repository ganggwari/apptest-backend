package com.mbc.puf.display.entity;

import com.mbc.puf.common.enums.DisplayCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 전광판 프리셋 문구 (테이블: display_preset) */
@Entity
@Table(name = "display_preset")
public class DisplayPreset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private DisplayCategory category;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    public DisplayPreset() {}

    public Long getId() { return id; }

    public DisplayCategory getCategory() { return category; }
    public void setCategory(DisplayCategory category) { this.category = category; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
