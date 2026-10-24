package com.mbc.puf.handover.dto;

import jakarta.validation.constraints.Size;

/** 인수인계 메모 수정 요청 (PUT /api/shift-handovers/{id}/memo). 쓴 사람만 */
public class HandoverMemoRequest {

    @Size(max = 2000, message = "memo는 2000자 이하여야 합니다.")
    private String memo;

    public HandoverMemoRequest() {
    }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
