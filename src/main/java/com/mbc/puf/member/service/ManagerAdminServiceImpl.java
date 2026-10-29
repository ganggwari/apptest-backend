package com.mbc.puf.member.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ManagerStatus;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.query.MatchScore;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.util.SearchKeyword;
import com.mbc.puf.member.dto.ManagerSummaryResponse;
import com.mbc.puf.member.dto.RegionRef;
import com.mbc.puf.member.dto.ShiftResponse;
import com.mbc.puf.member.entity.Employee;
import com.mbc.puf.member.entity.Manager;
import com.mbc.puf.member.entity.ManagerRegion;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.member.repository.ManagerRepository;
import com.mbc.puf.member.repository.ShiftRepository;
import com.mbc.puf.region.entity.Region;
import com.mbc.puf.region.repository.RegionRepository;

import jakarta.persistence.EntityManager;

@Service
public class ManagerAdminServiceImpl implements ManagerAdminService {

    // 이름·이메일·전화번호는 employee, 근무조 이름은 shift 에서 가져온다. 담당 지역은 페이지 단위로 따로 붙인다
    private static final String FROM = " from Manager m join Employee e on e.id = m.employeeId"
            + " left join Shift s on s.id = m.shiftId";

    private final ManagerRepository managerRepository;
    private final ManagerRegionRepository managerRegionRepository;
    private final RegionRepository regionRepository;
    private final ShiftRepository shiftRepository;
    private final EntityManager em;
    private final com.mbc.puf.stream.StreamPublisher stream;

    public ManagerAdminServiceImpl(ManagerRepository managerRepository, ManagerRegionRepository managerRegionRepository,
                                   RegionRepository regionRepository, ShiftRepository shiftRepository,
                                   EntityManager em, com.mbc.puf.stream.StreamPublisher stream) {
        this.managerRepository = managerRepository;
        this.managerRegionRepository = managerRegionRepository;
        this.regionRepository = regionRepository;
        this.shiftRepository = shiftRepository;
        this.em = em;
        this.stream = stream;
    }

    // ==================== 목록 ====================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ManagerSummaryResponse> getManagerList(String keyword, ManagerStatus status, Long regionId,
                                                               Long shiftId, boolean includeWithdrawn,
                                                               int page, int size) {
        PageParams pageParams = PageParams.of(page, size);
        QueryConditions c = new QueryConditions();

        // 상태를 지정하면 그 상태만. 지정하지 않으면 기본은 탈퇴(WITHDRAWN) 제외, includeWithdrawn=true 면 전체
        if (status != null) {
            c.add("m.status = ?", status);
        } else if (!includeWithdrawn) {
            c.add("m.status <> ?", ManagerStatus.WITHDRAWN);
        }
        if (regionId != null) {
            c.add("exists (select 1 from ManagerRegion mr where mr.managerId = m.id and mr.regionId = ?)", regionId);
        }
        c.eqIfPresent("m.shiftId", shiftId);

        // 통합 검색어: 아이디·이름·이메일·전화번호 일치 점수(정확 3/앞부분 2/포함 1)를 합산해 점수 순으로 정렬
        SearchKeyword searchKeyword = SearchKeyword.of(keyword);
        String orderBy = " order by m.createdAt desc, m.id desc";
        if (searchKeyword != null) {
            MatchScore score = new MatchScore(c)
                    .text(searchKeyword.getText(), "m.loginId")
                    .text(searchKeyword.getText(), "e.name")
                    .text(searchKeyword.getText(), "e.email")
                    .phone(searchKeyword.getPhone(), "e.phone");
            c.add(score.expression() + " > 0");
            orderBy = " order by " + score.expression() + " desc, m.createdAt desc, m.id desc";
        }

        PageResponse<Object[]> rows = PagedQuery.fetch(em, "select m, e, s.name" + FROM, "select count(m)" + FROM, c,
                orderBy, Object[].class, row -> row, pageParams);

        Map<Long, List<RegionRef>> regions = regionsOf(rows.getItems().stream()
                .map(row -> ((Manager) row[0]).getId()).toList());
        List<ManagerSummaryResponse> items = rows.getItems().stream()
                .map(row -> toResponse(row, regions)).toList();
        return new PageResponse<>(items, rows.getPage(), rows.getSize(), rows.getTotalItems());
    }

    // ==================== 지역·근무조 배정 / 탈퇴 ====================

    @Override
    @Transactional
    public ManagerSummaryResponse assignRegions(Long managerId, List<Long> regionIds) {
        requireNotWithdrawn(managerId);
        List<Long> distinct = List.copyOf(new LinkedHashSet<>(regionIds));
        for (Long regionId : distinct) {
            if (regionId == null || regionRepository.findByIdAndDeletedFalse(regionId).isEmpty()) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지역입니다. id=" + regionId);
            }
        }
        // 통째로 교체: 지우고 다시 넣는다 (deleteByManagerId 가 먼저 flush + 비우기)
        managerRegionRepository.deleteByManagerId(managerId);
        managerRegionRepository.saveAll(distinct.stream().map(rid -> new ManagerRegion(managerId, rid)).toList());
        managerRegionRepository.flush();
        assignmentChanged(managerId, "REGIONS");
        return getSummary(managerId);
    }

    @Override
    @Transactional
    public ManagerSummaryResponse assignShift(Long managerId, Long shiftId) {
        Manager manager = requireNotWithdrawn(managerId);
        if (shiftId != null && !shiftRepository.existsById(shiftId)) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 근무조입니다. id=" + shiftId);
        }
        manager.setShiftId(shiftId);
        em.flush();
        assignmentChanged(managerId, "SHIFT");
        return getSummary(managerId);
    }

    private void assignmentChanged(Long managerId, String changed) {
        stream.publishToManager(managerId, "manager-assignment-change",
                new com.mbc.puf.stream.RealtimePayloads.ManagerAssignmentChange(managerId, changed));
    }

    @Override
    @Transactional
    public ManagerSummaryResponse withdraw(Long managerId, Long operatorId) {
        if (managerId.equals(operatorId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "본인 계정은 마이페이지에서 탈퇴해 주세요.");
        }
        requireNotWithdrawn(managerId);
        if (managerRepository.withdraw(managerId) == 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 탈퇴 처리된 계정입니다.");
        }
        managerRegionRepository.deleteByManagerId(managerId);   // 탈퇴한 관리자는 담당 지역에서 뺀다
        return getSummary(managerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShiftResponse> getShifts() {
        return shiftRepository.findAllByOrderByStartTimeAsc().stream()
                .map(s -> new ShiftResponse(s.getId(), s.getName(), s.getStartTime(), s.getEndTime())).toList();
    }

    // ---- 내부 메서드 ----

    private Manager requireNotWithdrawn(Long managerId) {
        Manager manager = managerRepository.findById(managerId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 관리자입니다."));
        if (manager.getStatus() == ManagerStatus.WITHDRAWN) {
            throw new BusinessException(HttpStatus.CONFLICT, "탈퇴 처리된 계정입니다.");
        }
        return manager;
    }

    // 단건 요약 (목록과 같은 조인)
    private ManagerSummaryResponse getSummary(Long managerId) {
        Object[] row = em.createQuery("select m, e, s.name" + FROM + " where m.id = :id", Object[].class)
                .setParameter("id", managerId).getSingleResult();
        return toResponse(row, regionsOf(List.of(managerId)));
    }

    // 관리자 id → 담당 지역 목록 (한 번에 조회해서 N+1 방지)
    private Map<Long, List<RegionRef>> regionsOf(List<Long> managerIds) {
        if (managerIds.isEmpty()) {
            return Map.of();
        }
        List<ManagerRegion> links = managerRegionRepository.findByManagerIdIn(managerIds);
        Map<Long, String> names = regionRepository.findAllById(links.stream().map(ManagerRegion::getRegionId)
                .distinct().toList()).stream().collect(Collectors.toMap(Region::getId, Region::getName));
        return links.stream()
                .sorted((a, b) -> Long.compare(a.getRegionId(), b.getRegionId()))
                .collect(Collectors.groupingBy(ManagerRegion::getManagerId,
                        Collectors.mapping(l -> new RegionRef(l.getRegionId(), names.get(l.getRegionId())),
                                Collectors.toList())));
    }

    // row = [Manager, Employee, 근무조 이름]
    private ManagerSummaryResponse toResponse(Object[] row, Map<Long, List<RegionRef>> regions) {
        Manager m = (Manager) row[0];
        Employee e = (Employee) row[1];
        return new ManagerSummaryResponse(m.getId(), m.getLoginId(), e.getName(), e.getEmail(), e.getPhone(),
                m.getRole(), m.getStatus(), regions.getOrDefault(m.getId(), List.of()), m.getShiftId(),
                (String) row[2], m.getCreatedAt(), m.getApprovedAt());
    }
}
