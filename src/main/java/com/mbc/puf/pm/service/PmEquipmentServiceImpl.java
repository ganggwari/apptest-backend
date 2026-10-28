package com.mbc.puf.pm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.PmEquipmentType;
import com.mbc.puf.common.enums.PmLevel;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.pm.dto.PmEquipmentCreateRequest;
import com.mbc.puf.pm.dto.PmEquipmentCreateResponse;
import com.mbc.puf.pm.entity.PmEquipment;
import com.mbc.puf.pm.entity.PmThreshold;
import com.mbc.puf.pm.entity.PmThresholdHistory;
import com.mbc.puf.pm.repository.PmEquipmentRepository;
import com.mbc.puf.pm.repository.PmEquipmentRepository.EquipmentRow;
import com.mbc.puf.pm.repository.PmThresholdHistoryRepository;
import com.mbc.puf.pm.repository.PmThresholdRepository;
import com.mbc.puf.stream.StreamPublisher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 예지보전 설비 등록·삭제.
 * 등록: 담당 지역 검사 → 카메라·전광판 확인 → 설비 저장(이름·센서 주기는 DB 트리거가 채움) → 기본 단계 기준 3행 + 이력 → SSE.
 * 삭제: 담당 지역 검사 → is_deleted = TRUE. 점수·점검 기록은 지우지 않는다.
 */
@Service
public class PmEquipmentServiceImpl implements PmEquipmentService {

    // 새 설비의 기본 단계 기준 (시드와 같은 값: 안전 0 / 주의 40 / 위험 70). 등록 뒤에는 단계 기준 수정 API 로 바꾼다
    private static final BigDecimal DEFAULT_SAFE = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal DEFAULT_CAUTION = new BigDecimal("40.00");
    private static final BigDecimal DEFAULT_DANGER = new BigDecimal("70.00");

    @PersistenceContext
    private EntityManager em;

    private final PmEquipmentRepository equipmentRepository;
    private final PmThresholdRepository thresholdRepository;
    private final PmThresholdHistoryRepository historyRepository;
    private final ScopeResolver scopeResolver;
    private final DbClock dbClock;
    private final StreamPublisher streamPublisher;

    public PmEquipmentServiceImpl(PmEquipmentRepository equipmentRepository, PmThresholdRepository thresholdRepository,
            PmThresholdHistoryRepository historyRepository, ScopeResolver scopeResolver, DbClock dbClock,
            StreamPublisher streamPublisher) {
        this.equipmentRepository = equipmentRepository;
        this.thresholdRepository = thresholdRepository;
        this.historyRepository = historyRepository;
        this.scopeResolver = scopeResolver;
        this.dbClock = dbClock;
        this.streamPublisher = streamPublisher;
    }

    @Override
    @Transactional
    public PmEquipmentCreateResponse register(LoginUser loginUser, PmEquipmentCreateRequest request) {
        // 1) 지하차도 (삭제되지 않은 것) + 담당 지역
        List<?> underpass = em.createNativeQuery(
                "SELECT u.name, u.region_id FROM underpass u WHERE u.id = :id AND u.is_deleted = FALSE")
                .setParameter("id", request.getUnderpassId()).getResultList();
        if (underpass.isEmpty()) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지하차도입니다. id=" + request.getUnderpassId());
        }
        Object[] u = (Object[]) underpass.get(0);
        String underpassName = (String) u[0];
        Long regionId = ((Number) u[1]).longValue();
        scopeResolver.requireOwnable(loginUser, regionId);

        PmEquipmentType type = request.getType();
        Long camId = request.getCamId();
        Long itsVmsId = request.getItsVmsId();

        // 2) 종류에 맞는 연결만 허용 (카메라는 CCTV, 전광판은 DISPLAY)
        if (camId != null && type != PmEquipmentType.CCTV) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "camId는 CCTV 설비에만 지정할 수 있습니다.");
        }
        if (itsVmsId != null && type != PmEquipmentType.DISPLAY) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "itsVmsId는 DISPLAY 설비에만 지정할 수 있습니다.");
        }
        if (request.getInstalledAt() != null && request.getInstalledAt().isAfter(dbClock.now().toLocalDate())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "설치일(installedAt)은 오늘 이후일 수 없습니다.");
        }

        if (camId != null) {
            List<?> cam = em.createNativeQuery("SELECT c.underpass_id FROM camera c WHERE c.id = :id AND c.is_deleted = FALSE")
                    .setParameter("id", camId).getResultList();
            if (cam.isEmpty()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "존재하지 않는 카메라입니다. camId=" + camId);
            }
            if (((Number) cam.get(0)).longValue() != request.getUnderpassId()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "다른 지하차도의 카메라는 연결할 수 없습니다. camId=" + camId);
            }
            if (exists("SELECT 1 FROM pm_equipment e WHERE e.cam_id = :id AND e.is_deleted = FALSE", camId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "이미 다른 CCTV 설비에 연결된 카메라입니다. camId=" + camId);
            }
        }
        if (itsVmsId != null) {
            if (!exists("SELECT 1 FROM its_vms v WHERE v.id = :id", itsVmsId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "존재하지 않는 전광판입니다. itsVmsId=" + itsVmsId);
            }
            if (exists("SELECT 1 FROM pm_equipment e WHERE e.its_vms_id = :id AND e.is_deleted = FALSE", itsVmsId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "이미 다른 전광판 설비에 연결된 전광판입니다. itsVmsId=" + itsVmsId);
            }
        }

        // 3) 설비 저장 — 이름(P-12-1)과 센서 주기는 DB 트리거가 채우고, 저장 직후 다시 읽어 온다
        PmEquipment equipment = new PmEquipment();
        equipment.setUnderpassId(request.getUnderpassId());
        equipment.setType(type);
        equipment.setCamId(camId);
        equipment.setItsVmsId(itsVmsId);
        equipment.setInstalledAt(request.getInstalledAt());
        equipment = equipmentRepository.saveAndFlush(equipment);

        // 노면은 고장 위험 점수를 계산하지 않는다.
        if (type == PmEquipmentType.ROAD) {
            streamPublisher.dataChangedToAdmins(regionId, "pm-equipment", "CREATE", equipment.getId());
            return new PmEquipmentCreateResponse(equipment.getId(), equipment.getName(), type, request.getUnderpassId(),
                    underpassName, null, null, equipment.getInstalledAt(), null, null, null);
        }

        // 4) 기본 단계 기준 3행 + 최초 이력(변경 전 값 없음)
        LocalDateTime now = dbClock.now();
        createThreshold(equipment.getId(), PmLevel.SAFE, DEFAULT_SAFE, loginUser.id(), now);
        createThreshold(equipment.getId(), PmLevel.CAUTION, DEFAULT_CAUTION, loginUser.id(), now);
        createThreshold(equipment.getId(), PmLevel.DANGER, DEFAULT_DANGER, loginUser.id(), now);

        streamPublisher.dataChangedToAdmins(regionId, "pm-equipment", "CREATE", equipment.getId());
        return new PmEquipmentCreateResponse(equipment.getId(), equipment.getName(), type, request.getUnderpassId(),
                underpassName, camId, itsVmsId, equipment.getInstalledAt(), equipment.getSensorIntervalSec(),
                DEFAULT_CAUTION, DEFAULT_DANGER);
    }

    @Override
    @Transactional
    public void delete(LoginUser loginUser, Long equipId) {
        EquipmentRow row = equipmentRepository.findRowsByIds(List.of(equipId)).stream().findFirst().orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 예지보전 설비입니다. id=" + equipId));
        scopeResolver.requireOwnable(loginUser, row.getRegionId());

        PmEquipment equipment = equipmentRepository.findById(equipId).orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 예지보전 설비입니다. id=" + equipId));
        equipment.setDeleted(true);
        equipmentRepository.save(equipment);

        streamPublisher.dataChangedToAdmins(row.getRegionId(), "pm-equipment", "DELETE", equipId);
    }

    private void createThreshold(Long equipId, PmLevel level, BigDecimal minScore, Long managerId, LocalDateTime now) {
        PmThreshold t = new PmThreshold();
        t.setEquipId(equipId);
        t.setLevel(level);
        t.setMinScore(minScore);
        t.setUpdatedBy(managerId);
        t = thresholdRepository.save(t);
        historyRepository.save(new PmThresholdHistory(t.getId(), null, minScore, managerId, now));
    }

    private boolean exists(String sql, Long id) {
        return !em.createNativeQuery(sql).setParameter("id", id).setMaxResults(1).getResultList().isEmpty();
    }
}
