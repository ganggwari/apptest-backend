package com.mbc.puf.risk;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.threshold.entity.Threshold;
import com.mbc.puf.underpass.entity.Underpass;

/**
 * 위험도 단계를 정하는 규칙 (계산만 하고 DB 는 건드리지 않는다).
 *
 * 규칙
 *  - 점수: 지하차도별 임계치(threshold)로 안전 / 관심 / 주의 까지만 정한다.
 *  - 수위: 차단 수위(B)를 기준으로 정한다. B 이상이면 경계(자동 차단), B + 심각 추가분 이상이면 심각.
 *          B 의 1/3 이상이면 관심, 2/3 이상이면 주의.
 *  - B 는 기본 15cm. 침수 이력이 있거나 선제 차단 대상으로 지정된 지하차도는 5cm.
 *  - 최종 단계 = 점수 단계와 수위 단계 중 더 높은 쪽.
 *  - 올라갈 때는 바로 반영, 내려갈 때는 여유값(히스테리시스)을 더한 값으로 판정해서 경계선에서 왔다갔다 하는 걸 막는다.
 */
@Component
public class RiskPolicy {

    private final BigDecimal defaultBlockCm;
    private final BigDecimal earlyBlockCm;
    private final BigDecimal severeExtraCm;
    private final BigDecimal scoreMargin;
    private final BigDecimal waterMarginCm;

    public RiskPolicy(
            @Value("${puf.risk.water.default-block-cm:15}") BigDecimal defaultBlockCm,
            @Value("${puf.risk.water.early-block-cm:5}") BigDecimal earlyBlockCm,
            @Value("${puf.risk.water.severe-extra-cm:15}") BigDecimal severeExtraCm,
            @Value("${puf.risk.hysteresis.score:3}") BigDecimal scoreMargin,
            @Value("${puf.risk.hysteresis.water-cm:1}") BigDecimal waterMarginCm) {
        this.defaultBlockCm = defaultBlockCm;
        this.earlyBlockCm = earlyBlockCm;
        this.severeExtraCm = severeExtraCm;
        this.scoreMargin = scoreMargin;
        this.waterMarginCm = waterMarginCm;
    }

    /** 이 지하차도의 자동 차단 수위(cm). 침수 이력이 있거나 선제 차단 대상이면 짧은 쪽(5cm) */
    public BigDecimal blockCm(Underpass underpass) {
        boolean early = underpass.isEarlyBlock() || underpass.getFloodHistoryCount() > 0;
        return early ? earlyBlockCm : defaultBlockCm;
    }

    /** 수위만으로 정한 단계 */
    public RiskLevel waterLevel(BigDecimal waterCm, BigDecimal blockCm) {
        if (waterCm == null) {
            return RiskLevel.SAFE;
        }
        if (waterCm.compareTo(blockCm.add(severeExtraCm)) >= 0) {
            return RiskLevel.SEVERE;
        }
        if (waterCm.compareTo(blockCm) >= 0) {
            return RiskLevel.ALERT;
        }
        if (waterCm.compareTo(blockCm.multiply(BigDecimal.valueOf(2)).divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP)) >= 0) {
            return RiskLevel.CAUTION;
        }
        if (waterCm.compareTo(blockCm.divide(BigDecimal.valueOf(3), 2, java.math.RoundingMode.HALF_UP)) >= 0) {
            return RiskLevel.WATCH;
        }
        return RiskLevel.SAFE;
    }

    /** 점수만으로 정한 단계. thresholds 는 하한값 오름차순. 점수는 주의(CAUTION)까지만 만든다 */
    public RiskLevel scoreLevel(List<Threshold> thresholds, BigDecimal score) {
        RiskLevel level = RiskLevel.SAFE;
        if (score == null) {
            return level;
        }
        for (Threshold t : thresholds) {
            if (score.compareTo(t.getRiskScoreMin()) >= 0) {
                level = t.getLevel();
            }
        }
        return level;
    }

    /** 수위가 자동 차단 기준에 도달했는지 (여유값 없이 정확히 비교 — 해제 가능 기준과 같다) */
    public boolean reachedBlock(Underpass underpass, BigDecimal waterCm) {
        return waterCm != null && waterCm.compareTo(blockCm(underpass)) >= 0;
    }

    /**
     * 점수와 수위를 같이 보고 새 단계를 정한다.
     * 올라가거나 같으면 바로 target, 내려가야 하면 여유값을 더해서 다시 판정한 단계까지만 내린다
     */
    public RiskLevel decide(Underpass underpass, List<Threshold> thresholds, BigDecimal score, BigDecimal waterCm,
            RiskLevel current) {
        BigDecimal block = blockCm(underpass);
        RiskLevel target = RiskLevel.max(scoreLevel(thresholds, score), waterLevel(waterCm, block));
        if (target.atLeast(current)) {
            return target;
        }
        BigDecimal scoreWithMargin = score == null ? null : score.add(scoreMargin);
        BigDecimal waterWithMargin = waterCm == null ? null : waterCm.add(waterMarginCm);
        RiskLevel held = RiskLevel.max(scoreLevel(thresholds, scoreWithMargin), waterLevel(waterWithMargin, block));
        return held.atLeast(current) ? current : held;
    }
}