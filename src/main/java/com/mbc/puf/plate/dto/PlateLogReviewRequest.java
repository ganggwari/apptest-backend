package com.mbc.puf.plate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 관리자가 번호판을 수동으로 고쳐서 재매칭하는 요청 */
public class PlateLogReviewRequest {

    @NotBlank(message = "수정할 번호판을 입력해주세요.")
    @Size(max = 20, message = "번호판은 20자 이하여야 합니다.")   // plate_log.corrected_num VARCHAR(20)
    private String correctedNum;

    public PlateLogReviewRequest() {
    }

    public String getCorrectedNum() { return correctedNum; }
    public void setCorrectedNum(String correctedNum) { this.correctedNum = correctedNum; }
}
