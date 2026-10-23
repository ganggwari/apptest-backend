package com.mbc.puf.threshold.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.threshold.dto.ThresholdResponse;
import com.mbc.puf.threshold.dto.ThresholdUpdateRequest;
import com.mbc.puf.threshold.entity.Threshold;
import com.mbc.puf.threshold.entity.ThresholdHistory;
import com.mbc.puf.threshold.repository.ThresholdHistoryRepository;
import com.mbc.puf.threshold.repository.ThresholdRepository;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.UnderpassRepository;

import jakarta.persistence.EntityManager;

@Service
public class ThresholdServiceImpl implements ThresholdService {


    private final ThresholdRepository thresholdRepository;
    private final ThresholdHistoryRepository historyRepository;
    private final UnderpassRepository underpassRepository;
    private final DbClock dbClock;
    private final EntityManager em;

    private final ScopeResolver scopeResolver;
    private final com.mbc.puf.stream.StreamPublisher streamPublisher;   // SSE 실시간 이벤트

    public ThresholdServiceImpl(ThresholdRepository thresholdRepository, ThresholdHistoryRepository historyRepository,
                                UnderpassRepository underpassRepository, DbClock dbClock, EntityManager em, ScopeResolver scopeResolver,
                                com.mbc.puf.stream.StreamPublisher streamPublisher) {
        this.streamPublisher = streamPublisher;
        this.scopeResolver = scopeResolver;
        this.thresholdRepository = thresholdRepository;
        this.historyRepository = historyRepository;
        this.underpassRepository = underpassRepository;
        this.dbClock = dbClock;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ThresholdResponse> getThresholds(LoginUser loginUser, Long underpassId) {
        requireExistingUnderpass(underpassId);   // 지역 제한 없이 존재만 확인 (조회는 관리자 모두 허용)
        return thresholdRepository.findByUnderpassIdOrderByRiskScoreMinAsc(underpassId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ThresholdResponse updateThreshold(LoginUser loginUser, Long underpassId, RiskLevel level,
                                              ThresholdUpdateRequest request) {
        Underpass underpass = requireExistingUnderpass(underpassId);
        requireOwnableRegion(loginUser, underpass.getRegionId());   // REGIONAL_ADMIN은 본인 담당 지역만

        Threshold current = thresholdRepository.findByUnderpassIdAndLevel(underpassId, level)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "존재하지 않는 임계치입니다. underpassId=" + underpassId + ", level=" + level));

        BigDecimal oldValue = current.getRiskScoreMin();
        current.setRiskScoreMin(request.getRiskScoreMin());
        current.setUpdatedBy(loginUser.id());
        current.setUpdatedAt(dbClock.now());
        recordHistory(current.getId(), oldValue, request.getRiskScoreMin(), loginUser.id());
        em.flush();
        em.refresh(current);   // DB에 저장된 값(DECIMAL(5,2): 45 → 45.00)으로 응답 — 예전과 같게
        streamPublisher.dataChangedToAdmins(underpass.getRegionId(), "threshold", "UPDATE", underpassId);   // id 는 지하차도 id
        return toResponse(current);
    }

    // 지하차도 등록 시 UnderpassServiceImpl이 호출해서 기본 임계치 3행을 만드는 용도.
    // 여기서도 이력을 남긴다(최초 생성분도 threshold_history에 기록하기로 했으므로 old값은 null)
    @Override
    @Transactional
    public void createDefaultThresholds(Long underpassId, Long createdBy) {
        // AI 모델의 표시점수 경계(0/40/70)를 기본값으로 사용. 지하차도별로 관리자가 이후 조정 가능
        insertOne(underpassId, RiskLevel.SAFE, new BigDecimal("0.00"), createdBy);
        insertOne(underpassId, RiskLevel.WATCH, new BigDecimal("40.00"), createdBy);
        insertOne(underpassId, RiskLevel.CAUTION, new BigDecimal("70.00"), createdBy);
    }

    private void insertOne(Long underpassId, RiskLevel level, BigDecimal riskScoreMin, Long createdBy) {
        Threshold threshold = new Threshold();
        threshold.setUnderpassId(underpassId);
        threshold.setLevel(level);
        threshold.setRiskScoreMin(riskScoreMin);
        threshold.setUpdatedBy(createdBy);
        thresholdRepository.saveAndFlush(threshold);
        recordHistory(threshold.getId(), null, riskScoreMin, createdBy);
    }

    // ---- 내부 메서드 ----

    // 삭제되지 않은 지하차도, 없으면 404. regionId를 REGIONAL_ADMIN 권한 체크에 재사용한다
    private Underpass requireExistingUnderpass(Long underpassId) {
        return underpassRepository.findByIdAndDeletedFalse(underpassId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지하차도입니다. id=" + underpassId));
    }

    // REGIONAL_ADMIN이 자기 담당 지역이 아닌 지하차도의 임계치를 조정하려 하면 차단
    private void requireOwnableRegion(LoginUser loginUser, Long regionId) {
        scopeResolver.requireOwnable(loginUser, regionId);
    }

    private void recordHistory(Long thresholdId, BigDecimal oldValue, BigDecimal newValue, Long changedBy) {
        ThresholdHistory history = new ThresholdHistory();
        history.setThresholdId(thresholdId);
        history.setOldRiskScoreMin(oldValue);
        history.setNewRiskScoreMin(newValue);
        history.setChangedBy(changedBy);
        historyRepository.save(history);
    }

    private ThresholdResponse toResponse(Threshold threshold) {
        return new ThresholdResponse(threshold.getId(), threshold.getUnderpassId(), threshold.getLevel(),
                threshold.getRiskScoreMin(), threshold.getUpdatedBy(), threshold.getUpdatedAt());
    }
}