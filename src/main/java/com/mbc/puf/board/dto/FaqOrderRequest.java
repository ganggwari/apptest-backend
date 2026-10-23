package com.mbc.puf.board.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/** FAQ 순서 변경 요청. 원하는 순서대로 FAQ id 를 나열해서 보낸다 (전체가 아니라 일부만 보내도 된다) */
public class FaqOrderRequest {

    @NotEmpty(message = "순서를 바꿀 FAQ id 목록은 필수입니다.")
    private List<Long> ids;

    public FaqOrderRequest() {
    }

    public List<Long> getIds() { return ids; }
    public void setIds(List<Long> ids) { this.ids = ids; }
}
