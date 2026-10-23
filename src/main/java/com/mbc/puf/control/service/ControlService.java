package com.mbc.puf.control.service;

import com.mbc.puf.common.enums.ReasonType;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.control.dto.BlockRequest;
import com.mbc.puf.control.dto.CtrlTransferItem;
import com.mbc.puf.control.dto.FireTransferRequest;
import com.mbc.puf.control.dto.ReasonPresetItem;
import com.mbc.puf.underpass.dto.UnderpassDetailResponse;
import java.util.List;

public interface ControlService {

    /** 사유 프리셋 목록. type 을 주면 차단(BLOCK)/해제(RELEASE) 사유만, null 이면 전체 */
    List<ReasonPresetItem> getReasonPresets(ReasonType type);

    /**
     * 지하차도 차단 (차단 이벤트 저장 + 경찰 이관 PDF 바이트 반환)
     * @return PDF 바이트 배열
     */
    PdfResult blockUnderpass(LoginUser loginUser, Long underpassId, BlockRequest request);

    /** 수위가 차단 기준에 닿았을 때 시스템이 하는 자동 차단 (처리한 관리자 없음, PDF 는 이관 이력에서 내려받는다) */
    void autoBlock(Long underpassId, java.math.BigDecimal waterCm);

    /**
     * 지하차도 해제 (해제 이벤트 저장 + 경찰 이관 PDF 바이트 반환)
     * @return PDF 바이트 배열
     */
    PdfResult releaseUnderpass(LoginUser loginUser, Long underpassId, BlockRequest request);

    /**
     * 소방 이관 (잔류차량 목록 저장 + PDF 바이트 반환)
     */
    PdfResult fireTransfer(LoginUser loginUser, Long underpassId, FireTransferRequest request);

    /**
     * 이관 기록 목록 조회 (특정 지하차도 기준)
     */
    List<CtrlTransferItem> getTransferHistory(LoginUser loginUser, Long underpassId);

    /**
     * 이관 기록 단건 PDF 재출력
     */
    PdfResult redownloadPdf(LoginUser loginUser, Long transferId);

    /** PDF 파일명과 바이트를 묶는 값 객체 */
    record PdfResult(String filename, byte[] bytes, UnderpassDetailResponse underpassDetail) {}
}