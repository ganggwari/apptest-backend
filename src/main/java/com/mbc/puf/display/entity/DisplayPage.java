package com.mbc.puf.display.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 전광판 페이지 (테이블: display_page) */
@Entity
@Table(name = "display_page")
public class DisplayPage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "its_vms_id", nullable = false)
    private Long vmsId;   // 전광판(its_vms) 아이디

    @Column(name = "underpass_id", nullable = false)
    private Long underpassId;   // 이 페이지가 생긴 원인 지하차도

    @Column(name = "is_manual", nullable = false)
    private boolean manual;   // 관리자가 직접 만든 페이지(true) / 자동 표출이 만든 페이지(false)

    @Column(name = "page_no", nullable = false)
    private Integer pageNo;

    @Column(name = "content", nullable = false, length = 100)
    private String content;

    @Column(name = "preset_id")
    private Long presetId;   // 직접 입력이면 NULL

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public DisplayPage() {}

    public Long getId() { return id; }

    public Long getVmsId() { return vmsId; }
    public void setVmsId(Long vmsId) { this.vmsId = vmsId; }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public boolean isManual() { return manual; }
    public void setManual(boolean manual) { this.manual = manual; }

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Long getPresetId() { return presetId; }
    public void setPresetId(Long presetId) { this.presetId = presetId; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
