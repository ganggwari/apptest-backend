package com.mbc.puf.control.dto;

import com.mbc.puf.common.enums.TransferAgency;
import java.time.LocalDateTime;
import java.util.List;

/** 이관 기록 목록 아이템 */
public class CtrlTransferItem {
    private String documentType;
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String value) { documentType = value; }
    private Long id;
    private Long eventId;
    private TransferAgency agency;
    private String memo;
    private Long managerId;
    private String managerName;
    private LocalDateTime createdAt;
    private List<String> carNums;   // 소방 이관일 때만 값 있음

    public CtrlTransferItem() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public TransferAgency getAgency() { return agency; }
    public void setAgency(TransferAgency agency) { this.agency = agency; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public Long getManagerId() { return managerId; }
    public void setManagerId(Long managerId) { this.managerId = managerId; }

    public String getManagerName() { return managerName; }
    public void setManagerName(String managerName) { this.managerName = managerName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<String> getCarNums() { return carNums; }
    public void setCarNums(List<String> carNums) { this.carNums = carNums; }
}
