package com.mbc.puf.control.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 소방 이관 요청 (잔류차량 번호 목록 + 비고) */
public class FireTransferRequest {

    @NotNull(message = "통제 이벤트 아이디는 필수입니다.")
    private Long eventId;

    @Size(max = 500)
    @NotEmpty(message = "잔류차량 번호는 최소 1개 이상 입력해 주세요.")
    private List<@jakarta.validation.constraints.NotBlank @Size(max = 20) String> carNums;

    @Size(max = 500, message = "비고는 500자 이내로 입력해 주세요.")
    private String memo;

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public List<String> getCarNums() { return carNums; }
    public void setCarNums(List<String> carNums) { this.carNums = carNums; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
}
