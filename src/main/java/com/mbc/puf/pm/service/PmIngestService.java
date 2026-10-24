package com.mbc.puf.pm.service;

import java.util.List;

import com.mbc.puf.pm.dto.PmInternalEquipmentItem;
import com.mbc.puf.pm.dto.PmScoreIngestRequest;
import com.mbc.puf.pm.dto.PmScoreIngestResponse;

/** 예지보전 서버(FastAPI 8002) ↔ Spring 내부 수신 */
public interface PmIngestService {

    // 예지보전 서버가 받아 갈 전체 설비 목록
    List<PmInternalEquipmentItem> getEquipments();

    // 점수 묶음 저장 → 단계 판정 → (backfill 이 아니면) 이벤트·관리자 알림·SSE
    PmScoreIngestResponse receiveScores(PmScoreIngestRequest request);
}
