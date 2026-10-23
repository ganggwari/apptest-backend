package com.mbc.puf.control.dto;

import com.mbc.puf.common.enums.ReasonType;

/** 차단/해제 사유 프리셋 목록 아이템 */
public class ReasonPresetItem {
    private Long id;
    private ReasonType type;   // BLOCK / RELEASE
    private String content;

    public ReasonPresetItem(Long id, ReasonType type, String content) {
        this.id = id;
        this.type = type;
        this.content = content;
    }

    public Long getId() { return id; }
    public ReasonType getType() { return type; }
    public String getContent() { return content; }
}