package com.mbc.puf.pm.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmLevel;

/** 예지보전 SSE 이벤트로 내려가는 내용(JSON). 이벤트 이름은 각 record 위 주석에 적어 둔다 */
public final class PmStreamPayloads {

    private PmStreamPayloads() {
    }

    // "pm-update" (관리자, 지역별) — 점수 묶음을 받을 때 지역마다 한 번만 보낸다 (설비마다 보내면 화면이 너무 자주 다시 그려짐)
    public record PmUpdate(Long regionId, LocalDateTime predictedAt, List<Item> items) {
    }

    // stale=true 면 센서 데이터가 끊긴 설비 (이때 score·level 은 마지막으로 받은 값이거나 null)
    public record Item(Long equipId, String name, Long underpassId, PmEquipmentType type, BigDecimal score, PmLevel level,
            boolean stale) {
    }
}
