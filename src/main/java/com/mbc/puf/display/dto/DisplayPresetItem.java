package com.mbc.puf.display.dto;

import com.mbc.puf.common.enums.DisplayCategory;

/** 전광판 프리셋 문구 목록 아이템 */
public class DisplayPresetItem {
    private Long id;
    private DisplayCategory category;
    private String content;

    public DisplayPresetItem(Long id, DisplayCategory category, String content) {
        this.id = id;
        this.category = category;
        this.content = content;
    }

    public Long getId() { return id; }
    public DisplayCategory getCategory() { return category; }
    public String getContent() { return content; }
}
