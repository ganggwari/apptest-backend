package com.mbc.puf.underpass.dto;

import java.time.LocalDateTime;

import com.mbc.puf.common.enums.TransitStatus;

/** 통과 이력 조회 응답 (시설 전체/지역 단위 조회용이라 지하차도 이름을 같이 내려준다) */
public class TransitHistoryItem {

    private final Long id;
    private final Long underpassId;
    private final String underpassName;
    private final String carNum;          // OCR 원본 (출구 매칭에 쓰이는 값)
    private final String displayCarNum;   // 화면 표시용: 관리자가 정정한 번호가 있으면 그 값, 없으면 원본
    private final TransitStatus status;
    private final LocalDateTime entryAt;
    private final LocalDateTime exitAt;

    public TransitHistoryItem(Long id, Long underpassId, String underpassName, String carNum, String displayCarNum, TransitStatus status,
                              LocalDateTime entryAt, LocalDateTime exitAt) {
        this.id = id;
        this.underpassId = underpassId;
        this.underpassName = underpassName;
        this.carNum = carNum;
        this.displayCarNum = displayCarNum;
        this.status = status;
        this.entryAt = entryAt;
        this.exitAt = exitAt;
    }

    public Long getId() { return id; }
    public Long getUnderpassId() { return underpassId; }
    public String getUnderpassName() { return underpassName; }
    public String getCarNum() { return carNum; }
    public String getDisplayCarNum() { return displayCarNum; }
    public TransitStatus getStatus() { return status; }
    public LocalDateTime getEntryAt() { return entryAt; }
    public LocalDateTime getExitAt() { return exitAt; }
}
