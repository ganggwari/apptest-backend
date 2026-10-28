package com.mbc.puf.risk;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.enums.UnderpassEventType;
import com.mbc.puf.control.service.ControlService;
import com.mbc.puf.display.service.VmsReconcileRequested;
import com.mbc.puf.navigation.service.HazardRefreshRequested;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.stream.StreamPayloads;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.threshold.entity.Threshold;
import com.mbc.puf.threshold.repository.ThresholdRepository;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.entity.UnderpassEvent;
import com.mbc.puf.underpass.repository.UnderpassEventRepository;

/**
 * 위험점수나 수위가 새로 들어올 때마다 호출해서 지하차도의 단계를 다시 정한다.
 * 단계가 바뀌면 이벤트(RISK_LEVEL_UP / RISK_LEVEL_DOWN)를 남기고, 수위가 차단 기준에 닿으면 자동 차단까지 한다.
 * 규칙 자체는 RiskPolicy 에 있다.
 */
@Service
public class RiskLevelService {

    private final RiskPolicy policy;
    private final ThresholdRepository thresholdRepository;
    private final UnderpassEventRepository eventRepository;
    private final StreamPublisher streamPublisher;
    private final NotificationService notificationService;
    private final ControlService controlService;
    private final ApplicationEventPublisher eventPublisher;   // 위험도가 바뀌면 커밋 뒤에 전광판 문구를 다시 맞추도록 알린다

    public RiskLevelService(RiskPolicy policy, ThresholdRepository thresholdRepository,
            UnderpassEventRepository eventRepository, StreamPublisher streamPublisher,
            NotificationService notificationService, ControlService controlService,
            ApplicationEventPublisher eventPublisher) {
        this.policy = policy;
        this.thresholdRepository = thresholdRepository;
        this.eventRepository = eventRepository;
        this.streamPublisher = streamPublisher;
        this.notificationService = notificationService;
        this.controlService = controlService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 지하차도의 현재 점수(currentRiskScore)와 수위(currentWaterCm)로 단계를 다시 정한다.
     * 호출하기 전에 둘 중 새로 들어온 값을 엔티티에 먼저 넣어 두어야 한다.
     *
     * @return 이번 호출로 자동 차단이 일어났으면 true
     */
    @Transactional
    public boolean reevaluate(Underpass underpass) {
        List<Threshold> thresholds = thresholdRepository.findByUnderpassIdOrderByRiskScoreMinAsc(underpass.getId());
        RiskLevel before = underpass.getCurrentRiskLevel();
        BigDecimal score = underpass.getCurrentRiskScore();
        BigDecimal water = underpass.getCurrentWaterCm();

        RiskLevel after = policy.decide(underpass, thresholds, score, water, before);
        Long levelEventId = null;
        if (after != before) {
            underpass.setCurrentRiskLevel(after);

            UnderpassEvent event = new UnderpassEvent();
            event.setUnderpassId(underpass.getId());
            event.setEventType(after.ordinal() > before.ordinal() ? UnderpassEventType.RISK_LEVEL_UP
                    : UnderpassEventType.RISK_LEVEL_DOWN);
            event.setRiskLevel(after);
            event.setRiskScoreAtEvent(score);
            event.setWaterCm(water);
            eventRepository.saveAndFlush(event);
            levelEventId = event.getId();
            eventPublisher.publishEvent(new HazardRefreshRequested());   // 내비 위험지점도 커밋 뒤에 바로 다시 계산

            boolean up = after.ordinal() > before.ordinal();
            streamPublisher.publishPublic("risk-level-change", underpass.getRegionId(),
                    new StreamPayloads.RiskLevelChange(underpass.getId(), underpass.getName(), before, after,
                            up ? "UP" : "DOWN", score));
            notificationService.notifyManagers(ManagerNotificationCategory.RISK, underpass.getRegionId(),
                    up ? "위험도 상승" : "위험도 하락",
                    underpass.getName() + ": " + before.getDbValue() + " → " + after.getDbValue()
                            + (water != null && water.signum() > 0 ? " (수위 " + water.stripTrailingZeros().toPlainString() + "cm)" : ""),
                    "UNDERPASS", underpass.getId());
        }

        // 수위가 차단 기준에 닿았고 아직 열려 있으면 자동 차단. 이미 차단 중이면 아무 것도 하지 않는다
        if (underpass.getCurrentStatus() == UnderpassCurrentStatus.NORMAL && policy.reachedBlock(underpass, water)) {
            controlService.autoBlock(underpass.getId(), water);   // 전광판은 차단이 커밋된 뒤에 다시 맞춘다
            return true;
        }
        if (levelEventId != null) {
            // 전광판 갱신은 이 처리가 커밋된 뒤에 따로 한다 (전광판 쪽 오류가 위험도 저장을 되돌리지 않게)
            eventPublisher.publishEvent(new VmsReconcileRequested(underpass.getId(), levelEventId, null));
        }
        return false;
    }
}
