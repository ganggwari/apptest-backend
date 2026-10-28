package com.mbc.puf.display.dto;

import java.time.LocalDateTime;

/**
 * 전광판 문구 변경 이력 1행 (GET /api/display/history).
 * source: AUTO(차단·해제 등 상태 변화로 자동 변경) / MANUAL(관리자가 직접 추가·수정)
 * changeType: CREATE(처음 표출·추가, 변경 전 문구 없음) / UPDATE(문구가 바뀜)
 */
public class DisplayHistoryItem {

    private Long historyId;
    private Long vmsId;
    private String vmsName;             // 전광판 이름
    private Long pageId;                // 지금도 남아 있는 페이지면 그 아이디, 삭제됐으면 null
    private Long underpassId;           // 원인 지하차도
    private String underpassName;
    private Long eventId;               // 계기가 된 차단/해제 이벤트 (수동이면 null)
    private String oldContent;          // 변경 전 문구 (줄바꿈은 \n, 처음이면 null)
    private String newContent;          // 변경 후 문구
    private Long changedBy;             // 변경한 관리자 (자동이면 null)
    private String changedByName;
    private LocalDateTime changedAt;

    public DisplayHistoryItem() {
    }

    public String getSource() { return changedBy == null ? "AUTO" : "MANUAL"; }

    public String getChangeType() { return oldContent == null ? "CREATE" : "UPDATE"; }

    /** 화면에 보일 한글 이름 (자동 표출 / 자동 변경 / 수동 추가 / 수동 수정) */
    public String getChangeLabel() {
        return (changedBy == null ? "자동 " : "수동 ") + (oldContent == null ? (changedBy == null ? "표출" : "추가") : (changedBy == null ? "변경" : "수정"));
    }

    public Long getHistoryId() { return historyId; }
    public void setHistoryId(Long historyId) { this.historyId = historyId; }

    public Long getVmsId() { return vmsId; }
    public void setVmsId(Long vmsId) { this.vmsId = vmsId; }

    public String getVmsName() { return vmsName; }
    public void setVmsName(String vmsName) { this.vmsName = vmsName; }

    public Long getPageId() { return pageId; }
    public void setPageId(Long pageId) { this.pageId = pageId; }

    public Long getUnderpassId() { return underpassId; }
    public void setUnderpassId(Long underpassId) { this.underpassId = underpassId; }

    public String getUnderpassName() { return underpassName; }
    public void setUnderpassName(String underpassName) { this.underpassName = underpassName; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getOldContent() { return oldContent; }
    public void setOldContent(String oldContent) { this.oldContent = oldContent; }

    public String getNewContent() { return newContent; }
    public void setNewContent(String newContent) { this.newContent = newContent; }

    public Long getChangedBy() { return changedBy; }
    public void setChangedBy(Long changedBy) { this.changedBy = changedBy; }

    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }

    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
}
