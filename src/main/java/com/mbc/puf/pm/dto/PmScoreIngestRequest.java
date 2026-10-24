package com.mbc.puf.pm.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 예지보전 서버가 보내는 점수 묶음 (POST /api/internal/pm-scores).
 * backfill=true 는 서버를 켤 때 보내는 지난 7일치 — 저장만 하고 이벤트·알림·SSE 는 만들지 않는다.
 */
public class PmScoreIngestRequest {

    private boolean backfill;

    @NotEmpty(message = "scores가 비어 있습니다.")
    @Size(max = 1000, message = "scores는 한 번에 1000건까지 보낼 수 있습니다.")   // 예지보전 서버는 500건씩 나눠 보낸다
    @Valid
    private List<PmScoreIngestItem> scores;

    public PmScoreIngestRequest() {
    }

    public boolean isBackfill() { return backfill; }
    public void setBackfill(boolean backfill) { this.backfill = backfill; }

    public List<PmScoreIngestItem> getScores() { return scores; }
    public void setScores(List<PmScoreIngestItem> scores) { this.scores = scores; }
}
