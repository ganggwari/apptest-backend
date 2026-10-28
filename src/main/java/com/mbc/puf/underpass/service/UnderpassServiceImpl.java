package com.mbc.puf.underpass.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mbc.puf.common.enums.ReviewStatus;
import com.mbc.puf.common.enums.TransitStatus;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.enums.UnderpassEventType;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.query.PagedQuery;
import com.mbc.puf.common.query.QueryConditions;
import com.mbc.puf.common.request.PageParams;
import com.mbc.puf.common.request.PageQuery;
import com.mbc.puf.common.request.PeriodQuery;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.request.VehicleQuery;
import com.mbc.puf.common.response.PageResponse;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.internal.dto.RiskScoreTrendItem;
import com.mbc.puf.internal.entity.RiskScore;
import com.mbc.puf.internal.repository.RiskScoreRepository;
import com.mbc.puf.threshold.service.ThresholdService;
import com.mbc.puf.underpass.dto.GateCameraRequest;
import com.mbc.puf.underpass.dto.ControlHistoryItem;
import com.mbc.puf.underpass.dto.TransitHistoryItem;
import com.mbc.puf.underpass.dto.UnderpassCreateRequest;
import com.mbc.puf.underpass.dto.UnderpassDetailResponse;
import com.mbc.puf.underpass.dto.UnderpassListItem;
import com.mbc.puf.underpass.dto.UnderpassUpdateRequest;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.underpass.entity.Transit;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.entity.UnderpassEvent;
import com.mbc.puf.underpass.repository.CameraRepository;
import com.mbc.puf.underpass.repository.TransitRepository;
import com.mbc.puf.underpass.repository.UnderpassEventRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;

@Service
public class UnderpassServiceImpl implements UnderpassService {


    // 지하차도 목록은 지도에 한 번에 뿌리는 용도가 있어서(2차 범위 100개 이상) 페이지 상한을 따로 크게 둔다
    private static final int UNDERPASS_LIST_MAX_SIZE = 500;

    // hours 없이 limit만 쓸 때의 기본 개수(기존 동작과 동일)
    private static final int DEFAULT_RISK_SCORE_LIMIT = 24;
    // hours를 쓰는데 limit을 안 줬을 때의 안전 상한. 스케줄러가 60초마다 지하차도당 1건씩 쌓는 구조라
    // 하루(24시간)치를 다 받아도 1440건 남짓 — 넉넉하게 3000으로 잡아둔다
    private static final int HOURS_SAFETY_LIMIT = 3000;
    // 잔류/장시간 IN_TRANSIT 차량을 자동으로 "통과완료" 처리하기까지 기다리는 시간(분). 2026-10-01 확정값
    // (※ 새 DB 구조 단계에서 이 타이머는 삭제 예정 — 소방 보고 흐름과 충돌)

    // 목록용: 지역명 + 최신 위험점수 1건 + 판별대기 건수 + 잔류차량 건수를 한 번에 (예전 LATERAL JOIN 3개와 같음)
    private static final String LIST_SELECT = "select u, r.name,"
            + " (select rs.riskScore from RiskScore rs where rs.underpassId = u.id order by rs.predictedAt desc limit 1) as riskScore,"
            + " (select count(p) from PlateLog p join Camera c on c.id = p.cameraId"
            + "   where c.underpassId = u.id and p.reviewStatus = com.mbc.puf.common.enums.ReviewStatus.PENDING),"
            + " (select count(t) from Transit t"
            + "   where t.underpassId = u.id and t.status = com.mbc.puf.common.enums.TransitStatus.REMAINING)"
            + " from Underpass u left join Region r on r.id = u.regionId";

    private final UnderpassRepository underpassRepository;
    private final UnderpassEventRepository eventRepository;
    private final TransitRepository transitRepository;
    private final CameraRepository cameraRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final ThresholdService thresholdService;
    private final ScopeResolver scopeResolver;
    private final DbClock dbClock;
    private final com.mbc.puf.stream.StreamPublisher streamPublisher;   // SSE 실시간 이벤트
    private final EntityManager em;
    private final com.mbc.puf.internal.repository.RiskForecastRepository riskForecastRepository;
    private final ApplicationEventPublisher eventPublisher;   // 내비 위험지점 즉시 갱신 요청

    public UnderpassServiceImpl(UnderpassRepository underpassRepository, UnderpassEventRepository eventRepository,
                                TransitRepository transitRepository,
                                CameraRepository cameraRepository, RiskScoreRepository riskScoreRepository,
                                ThresholdService thresholdService, ScopeResolver scopeResolver,
                                DbClock dbClock, EntityManager em,
                                com.mbc.puf.stream.StreamPublisher streamPublisher,
                                com.mbc.puf.internal.repository.RiskForecastRepository riskForecastRepository,
                                ApplicationEventPublisher eventPublisher) {
        this.riskForecastRepository = riskForecastRepository;
        this.eventPublisher = eventPublisher;
        this.streamPublisher = streamPublisher;
        this.underpassRepository = underpassRepository;
        this.eventRepository = eventRepository;
        this.transitRepository = transitRepository;
        this.cameraRepository = cameraRepository;
        this.riskScoreRepository = riskScoreRepository;
        this.thresholdService = thresholdService;
        this.scopeResolver = scopeResolver;
        this.dbClock = dbClock;
        this.em = em;
    }

    // ---- 조회: 로그인 없이 공개 ----

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UnderpassListItem> getUnderpassList(LoginUser loginUser, ListScope scope, Long regionId,
                                                            String keyword, UnderpassCurrentStatus status,
                                                            Sort sort, PageQuery page) {
        PageParams pageParams = page.toParams(UNDERPASS_LIST_MAX_SIZE);
        QueryConditions c = new QueryConditions().add("u.deleted = false");
        if (scope == ListScope.MINE) {
            // 내 담당 지역만. 관리자가 아니면 거절, regionId 를 주면 그 지역이 내 담당일 때만(아니면 403)
            if (loginUser == null) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "인증이 필요합니다. 다시 로그인해주세요.");
            }
            if (!loginUser.isManager()) {
                throw new BusinessException(HttpStatus.FORBIDDEN, "scope=MINE 은 관리자만 사용할 수 있습니다.");
            }
            c.inIfPresent("u.regionId", scopeResolver.regionsOf(loginUser, regionId));
        } else {
            c.eqIfPresent("u.regionId", regionId);        // 안 주면 전체 지역
        }
        c
                .eqIfPresent("u.currentStatus", status)       // NORMAL/BLOCKED
                .containsIfPresent(keyword, "u.name");        // 이름 검색

        String orderBy = switch (sort == null ? Sort.NAME : sort) {
            case NAME -> " order by u.name asc, u.id asc";
            case RISK_SCORE -> " order by riskScore desc nulls last, u.name asc, u.id asc";
            case LATEST -> " order by u.createdAt desc, u.id desc";
        };
        Set<Long> mine = myRegionIds(loginUser);   // 목록의 각 시설을 내가 관리할 수 있는지(manageable) 표시용
        return PagedQuery.fetch(em, LIST_SELECT, "select count(u) from Underpass u", c, orderBy,
                Object[].class, row -> toListItem(row, mine), pageParams);
    }

    @Override
    @Transactional(readOnly = true)
    public UnderpassDetailResponse getUnderpass(LoginUser loginUser, Long id) {
        UnderpassDetailResponse detail = toDetail(requireExistingUnderpass(id));
        detail.setManageable(myRegionIds(loginUser).contains(detail.getRegionId()));
        // 앱 상세 화면(시설 현황 카드)이 쓰는 값: 목록 응답과 같은 기준으로 센다
        detail.setPendingReviewCount(em.createQuery("select count(p) from PlateLog p join Camera c on c.id = p.cameraId"
                + " where c.underpassId = :id and p.reviewStatus = com.mbc.puf.common.enums.ReviewStatus.PENDING", Long.class)
                .setParameter("id", id).getSingleResult());
        detail.setRemainingCount(em.createQuery("select count(t) from Transit t where t.underpassId = :id"
                + " and t.status = com.mbc.puf.common.enums.TransitStatus.REMAINING", Long.class)
                .setParameter("id", id).getSingleResult());
        riskScoreRepository.findByUnderpassIdOrderByPredictedAtDesc(id, Limit.of(1)).stream().findFirst()
                .ifPresent(r -> detail.setRainfallMm(r.getRainfall1hMm()));
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskScoreTrendItem> getRiskScoreTrend(Long id, Integer limit, Integer hours) {
        requireExistingUnderpass(id);   // 존재하지 않으면 404
        int effectiveLimit = limit != null ? limit : (hours != null ? HOURS_SAFETY_LIMIT : DEFAULT_RISK_SCORE_LIMIT);

        List<RiskScore> recent = (hours == null)
                ? riskScoreRepository.findByUnderpassIdOrderByPredictedAtDesc(id, Limit.of(effectiveLimit))
                : riskScoreRepository.findByUnderpassIdAndPredictedAtGreaterThanEqualOrderByPredictedAtDesc(
                        id, dbClock.now().minusHours(hours), Limit.of(effectiveLimit));

        List<RiskScoreTrendItem> items = new ArrayList<>(recent.stream().map(UnderpassServiceImpl::toTrendItem).toList());
        Collections.reverse(items);   // DB에서는 최신순으로 가져왔으니, 그래프용으로 시간 오름차순으로 뒤집는다
        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.mbc.puf.internal.dto.RiskForecastItem> getRiskForecast(Long id) {
        requireExistingUnderpass(id);   // 존재하지 않으면 404
        // 지금 시각이 속한 시(時)부터 보여준다 (예: 14:25 이면 14:00 시점부터)
        java.time.LocalDateTime from = dbClock.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        return riskForecastRepository.findByUnderpassIdAndTargetAtGreaterThanEqualOrderByTargetAtAsc(id, from).stream()
                .map(f -> new com.mbc.puf.internal.dto.RiskForecastItem(f.getTargetAt(), f.getRiskScore(),
                        f.getRainfallMm(), f.getPredictedAt()))
                .toList();
    }

    // ---- 등록/수정/삭제: REGIONAL_ADMIN은 본인 담당 지역만 ----

    @Override
    @Transactional
    public UnderpassDetailResponse createUnderpass(LoginUser loginUser, UnderpassCreateRequest request) {
        requireOwnableRegion(loginUser, request.getRegionId());

        Underpass underpass = new Underpass();
        underpass.setName(request.getName().trim());
        underpass.setRegionId(request.getRegionId());
        underpass.setRoadAddress(request.getRoadAddress());
        underpass.setLatitude(request.getLatitude());
        underpass.setLongitude(request.getLongitude());
        underpass.setHeightM(request.getHeightM());
        underpass.setLengthM(request.getLengthM());
        underpass.setWidthM(request.getWidthM());
        underpass.setLanes(request.getLanes());
        underpass.setSafetyGrade(request.getSafetyGrade());
        underpass.setRiverDistM(request.getRiverDistM());
        underpass.setSeaDistM(request.getSeaDistM());
        underpass.setFloodHistoryCount(request.getFloodHistoryCount() == null ? 0 : request.getFloodHistoryCount());   // 안 보내면 0
        underpass.setEarlyBlock(Boolean.TRUE.equals(request.getEarlyBlock()));
        underpass.setCurrentStatus(UnderpassCurrentStatus.NORMAL);   // 등록 시 항상 정상 상태로 고정
        underpassRepository.saveAndFlush(underpass);

        thresholdService.createDefaultThresholds(underpass.getId(), loginUser.id());   // 안전/관심/주의 3행 기본 생성
        streamPublisher.dataChangedPublic(underpass.getRegionId(), "underpass", "CREATE", underpass.getId());
        return toDetail(reload(underpass));
    }

    @Override
    @Transactional
    public UnderpassDetailResponse updateUnderpass(LoginUser loginUser, Long id, UnderpassUpdateRequest request) {
        Underpass underpass = requireExistingUnderpass(id);
        requireOwnableRegion(loginUser, underpass.getRegionId());      // 지금 속한 지역이 본인 담당인지
        requireOwnableRegion(loginUser, request.getRegionId());        // 옮기려는 지역도 본인 담당인지

        // 상태(currentStatus/currentBlockEventId)는 여기서 건드리지 않는다
        underpass.setName(request.getName().trim());
        underpass.setRegionId(request.getRegionId());
        underpass.setRoadAddress(request.getRoadAddress());
        underpass.setLatitude(request.getLatitude());
        underpass.setLongitude(request.getLongitude());
        underpass.setHeightM(request.getHeightM());
        underpass.setLengthM(request.getLengthM());
        underpass.setWidthM(request.getWidthM());
        underpass.setLanes(request.getLanes());
        underpass.setSafetyGrade(request.getSafetyGrade());
        underpass.setRiverDistM(request.getRiverDistM());
        underpass.setSeaDistM(request.getSeaDistM());
        if (request.getEarlyBlock() != null) {   // 안 보내면 기존 값 유지
            underpass.setEarlyBlock(request.getEarlyBlock());
        }
        if (request.getFloodHistoryCount() != null) {   // 안 보내면 기존 값 유지
            underpass.setFloodHistoryCount(request.getFloodHistoryCount());
        }
        streamPublisher.dataChangedPublic(underpass.getRegionId(), "underpass", "UPDATE", underpass.getId());
        eventPublisher.publishEvent(new com.mbc.puf.navigation.service.HazardRefreshRequested());   // 좌표·길이 등이 바뀌었을 수 있다
        return toDetail(reload(underpass));
    }

    @Override
    @Transactional
    public void deleteUnderpass(LoginUser loginUser, Long id) {
        Underpass underpass = requireExistingUnderpass(id);
        requireOwnableRegion(loginUser, underpass.getRegionId());
        underpass.setDeleted(true);
        streamPublisher.dataChangedPublic(underpass.getRegionId(), "underpass", "DELETE", underpass.getId());
        eventPublisher.publishEvent(new com.mbc.puf.navigation.service.HazardRefreshRequested());   // 삭제된 지하차도는 위험지점에서 빠진다
    }

    // ---- 상행/하행 입·출구 카메라 지정 (MP-10-1): 담당 지역만 ----

    @Override
    @Transactional
    public UnderpassDetailResponse assignGateCameras(LoginUser loginUser, Long id, GateCameraRequest request) {
        Underpass underpass = requireExistingUnderpass(id);
        requireOwnableRegion(loginUser, underpass.getRegionId());

        List<Long> assigned = new ArrayList<>();
        for (Long cameraId : new Long[] { request.getUpEntryCameraId(), request.getUpExitCameraId(),
                request.getDownEntryCameraId(), request.getDownExitCameraId() }) {
            if (cameraId == null) {
                continue;
            }
            if (assigned.contains(cameraId)) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "한 카메라를 두 자리에 지정할 수 없습니다. cameraId=" + cameraId);
            }
            requireCameraOfThisUnderpass(id, cameraId);
            assigned.add(cameraId);
        }

        underpass.setUpEntryCameraId(request.getUpEntryCameraId());
        underpass.setUpExitCameraId(request.getUpExitCameraId());
        underpass.setDownEntryCameraId(request.getDownEntryCameraId());
        underpass.setDownExitCameraId(request.getDownExitCameraId());
        streamPublisher.dataChangedPublic(underpass.getRegionId(), "underpass", "UPDATE", underpass.getId());
        eventPublisher.publishEvent(new com.mbc.puf.navigation.service.HazardRefreshRequested());   // 입·출구 카메라가 바뀌면 구간 좌표가 달라진다
        return toDetail(underpass);
    }

    // ---- 통과 기록 / 제어이력 조회 ----

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TransitHistoryItem> getTransitHistory(LoginUser loginUser, ScopeQuery scope,
                                                              TransitStatus status, VehicleQuery vehicle,
                                                              PeriodQuery period, PageQuery page) {
        // 지역을 안 주면 내 담당 지역 전체, 담당 밖 지역·시설을 지정하면 403
        ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);
        QueryConditions c = new QueryConditions()
                .eqIfPresent("t.underpassId", resolved.getUnderpassId())
                .inIfPresent("u.regionId", resolved.getRegionIds())
                .eqIfPresent("t.status", status)
                .carNumIfPresent(vehicle.getCarNum(), "t.carNum", "p.correctedNum")   // 원본·정정값 둘 다
                .periodIfPresent("t.entryAt", period);   // 진입 시각 기준

        // 입구 인식 기록에 관리자가 정정한 번호가 있으면 화면에는 그 값을 보여준다 (t.carNum 은 출구 매칭용 OCR 원본 그대로)
        String from = " from Transit t join Underpass u on u.id = t.underpassId"
                + " left join PlateLog p on p.id = t.entryPlateLogId";
        return PagedQuery.fetch(em, "select t, u.name, p.correctedNum" + from, "select count(t)" + from, c,
                " order by t.entryAt desc, t.id desc", Object[].class, row -> {
                    Transit t = (Transit) row[0];
                    String corrected = (String) row[2];
                    return new TransitHistoryItem(t.getId(), t.getUnderpassId(), (String) row[1], t.getCarNum(),
                            corrected != null ? corrected : t.getCarNum(),
                            t.getStatus(), t.getEntryAt(), t.getExitAt());
                }, page.toParams());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ControlHistoryItem> getControlHistory(LoginUser loginUser, Long id, String kind,
                                                              String operatorName, PeriodQuery period, PageQuery page) {
        Underpass underpass = requireExistingUnderpass(id);
        scopeResolver.resolve(loginUser, ScopeQuery.of(null, id));   // 담당 지역 밖의 제어이력은 볼 수 없다(403)
        PageParams pageParams = page.toParams();

        // 이벤트와 전광판 조작을 각각 "이번 페이지까지 필요한 만큼"만 최신순으로 가져와서 합친 뒤 잘라낸다
        // (예전 UNION ALL + ORDER BY created_at DESC 와 같은 결과)
        int needed = (int) pageParams.getOffset() + pageParams.getSize();
        List<ControlHistoryItem> merged = new ArrayList<>();
        long total = 0;

        if (!"DISPLAY".equals(kind)) {
            QueryConditions c = new QueryConditions().add("e.underpassId = ?", id)
                    .managerNameIfPresent(operatorName, "e.operatorId")   // 처리한 직원 이름
                    .periodIfPresent("e.createdAt", period);
            merged.addAll(top(c, "select e from UnderpassEvent e", " order by e.createdAt desc, e.id desc",
                    UnderpassEvent.class, needed).stream()
                    .map(e -> {
                        ControlHistoryItem item = new ControlHistoryItem("EVENT", id, underpass.getName(), e.getEventType(),
                                e.getRiskLevel(), e.getRiskScoreAtEvent(), e.getReason(), null, e.getOperatorId(),
                                e.getCreatedAt());
                        item.setReasonNote(e.getReasonNote());
                        return item;
                    })
                    .toList());
            total += count(c, "select count(e) from UnderpassEvent e");
        }
        if (!"EVENT".equals(kind)) {
            // 전광판 문구 변경 이력: display_history.underpass_id (자동 표출은 가장 가까운 전광판 것만 남는다)
            String opLike = com.mbc.puf.common.util.LikePattern.lowerContainsOrNull(operatorName);
            String where = " WHERE dh.underpass_id = :id"
                    + (opLike == null ? "" : " AND dh.changed_by IN (SELECT mm.id FROM manager mm JOIN employee ee ON ee.id = mm.employee_id"
                    + " WHERE LOWER(ee.name) LIKE :opLike ESCAPE '\\')")
                    + (period != null && period.startAt() != null ? " AND dh.changed_at >= :fromAt" : "")
                    + (period != null && period.endAtExclusive() != null ? " AND dh.changed_at < :toAt" : "");
            String from = " FROM display_history dh";
            Query rowsQuery = em.createNativeQuery("SELECT dh.new_content, dh.changed_by, dh.changed_at" + from + where
                    + " ORDER BY dh.changed_at DESC, dh.id DESC").setMaxResults(needed);
            Query countQuery = em.createNativeQuery("SELECT COUNT(*)" + from + where);
            for (Query q : List.of(rowsQuery, countQuery)) {
                q.setParameter("id", id);
                if (period != null && period.startAt() != null) {
                    q.setParameter("fromAt", period.startAt());
                }
                if (period != null && period.endAtExclusive() != null) {
                    q.setParameter("toAt", period.endAtExclusive());
                }
                if (opLike != null) {
                    q.setParameter("opLike", opLike);
                }
            }
            for (Object row : rowsQuery.getResultList()) {
                Object[] r = (Object[]) row;
                merged.add(new ControlHistoryItem("DISPLAY", id, underpass.getName(), null, null, null, null,
                        (String) r[0], r[1] == null ? null : ((Number) r[1]).longValue(), toLocalDateTime(r[2])));   // changed_by 는 자동 표출이면 NULL
            }
            total += ((Number) countQuery.getSingleResult()).longValue();
        }

        merged.sort(Comparator.comparing(ControlHistoryItem::getCreatedAt).reversed());
        int from = (int) Math.min(pageParams.getOffset(), merged.size());
        int to = Math.min(from + pageParams.getSize(), merged.size());
        List<ControlHistoryItem> pageItems = merged.subList(from, to);
        fillOperatorNames(pageItems);
        return new PageResponse<>(pageItems, pageParams.getPage(), pageParams.getSize(), total);
    }

    // 이 페이지 항목들의 처리 직원 이름을 한 번에 채운다 (항목마다 조회하지 않게 모아서)
    private void fillOperatorNames(List<ControlHistoryItem> items) {
        List<Long> ids = items.stream().map(ControlHistoryItem::getOperatorId).filter(java.util.Objects::nonNull)
                .distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        for (Object[] r : em.createQuery("select m.id, e.name from Manager m, Employee e"
                + " where e.id = m.employeeId and m.id in :ids", Object[].class).setParameter("ids", ids).getResultList()) {
            names.put((Long) r[0], (String) r[1]);
        }
        items.forEach(i -> i.setOperatorName(names.get(i.getOperatorId())));
    }

    // ---- 통과 기록 수동 완료 (단건) ----

    @Override
    @Transactional
    public TransitHistoryItem completeTransit(LoginUser loginUser, Long transitId) {
        Transit transit = transitRepository.findById(transitId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 통과 기록입니다. id=" + transitId));
        Underpass underpass = requireExistingUnderpass(transit.getUnderpassId());
        requireOwnableRegion(loginUser, underpass.getRegionId());   // 담당 지역 밖이면 403

        if (transit.getStatus() == TransitStatus.EXITED) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 통과 완료된 기록입니다.");
        }
        // 실제 출구 인식이 있었던 게 아니라 관리자가 확인해서 닫는 것이므로 exit_log_id 는 비워 두고 시각만 남긴다.
        // 처리한 관리자는 따로 저장하지 않는다 (처리 시각은 exit_at, 담당자는 그 시각의 근무조로 알 수 있음)
        transit.setStatus(TransitStatus.EXITED);
        transit.setExitAt(dbClock.now());
        streamPublisher.publishToAdmins(underpass.getRegionId(), "transit-update",
                new com.mbc.puf.stream.StreamPayloads.TransitUpdate(underpass.getId(), "EXIT",
                        transit.getDirection() == null ? null : transit.getDirection().name(), transit.getCarNum()));
        return new TransitHistoryItem(transit.getId(), transit.getUnderpassId(), underpass.getName(), transit.getCarNum(),
                transit.getCarNum(), transit.getStatus(), transit.getEntryAt(), transit.getExitAt());
    }

    // ---- 내부 메서드 ----

    // 로그인한 관리자의 담당 지역 id 집합. 비로그인·고객이면 빈 집합
    private Set<Long> myRegionIds(LoginUser loginUser) {
        if (loginUser == null || !loginUser.isManager()) {
            return Set.of();
        }
        return new HashSet<>(scopeResolver.myRegionIds(loginUser));
    }

    // 삭제되지 않은 지하차도, 없으면 404 (지역 제한 없음 — 상세·목록은 공개 조회)
    private Underpass requireExistingUnderpass(Long id) {
        return underpassRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지하차도입니다. id=" + id));
    }

    // REGIONAL_ADMIN이 자기 담당 지역이 아닌 regionId를 등록/수정/삭제 대상으로 쓰면 차단
    private void requireOwnableRegion(LoginUser loginUser, Long regionId) {
        scopeResolver.requireOwnable(loginUser, regionId);
    }

    // 입구/출구로 지정하려는 카메라가 실제로 이 지하차도 소속인지 확인 (다른 지하차도 카메라를 잘못 지정하는 것 방지)
    private void requireCameraOfThisUnderpass(Long underpassId, Long cameraId) {
        Camera camera = cameraRepository.findByIdAndDeletedFalse(cameraId).orElse(null);
        if (camera == null || !camera.getUnderpassId().equals(underpassId)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이 지하차도에 속한 카메라가 아닙니다. cameraId=" + cameraId);
        }
    }

    // 저장 직후 DB에 실제로 저장된 값으로 다시 읽는다 (DECIMAL 자릿수 등을 예전 응답과 똑같이 맞추기 위함:
    // 요청의 35.15 가 DB 에선 35.1500000 으로 저장되고, 예전에도 그 값을 응답했다)
    private <T> T reload(T entity) {
        em.flush();
        em.refresh(entity);
        return entity;
    }

    private static java.time.LocalDateTime toLocalDateTime(Object value) {
        return (value instanceof java.sql.Timestamp ts) ? ts.toLocalDateTime() : (java.time.LocalDateTime) value;
    }

    private <T> List<T> top(QueryConditions c, String select, String orderBy, Class<T> type, int max) {
        TypedQuery<T> q = em.createQuery(select + c.where() + orderBy, type);
        c.applyTo(q);
        return q.setMaxResults(max).getResultList();
    }

    private long count(QueryConditions c, String select) {
        TypedQuery<Long> q = em.createQuery(select + c.where(), Long.class);
        c.applyTo(q);
        return q.getSingleResult();
    }

    // row = [Underpass, 지역명, 최신 위험점수, 판별대기 건수, 잔류차량 건수]
    private UnderpassListItem toListItem(Object[] row, Set<Long> mine) {
        Underpass u = (Underpass) row[0];
        UnderpassListItem item = new UnderpassListItem();
        item.setId(u.getId());
        item.setName(u.getName());
        item.setRegionId(u.getRegionId());
        item.setRegionName((String) row[1]);
        item.setManageable(mine.contains(u.getRegionId()));
        item.setRoadAddress(u.getRoadAddress());
        item.setLatitude(u.getLatitude());
        item.setLongitude(u.getLongitude());
        item.setCurrentStatus(u.getCurrentStatus());
        item.setCurrentRiskLevel(u.getCurrentRiskLevel());
        item.setRiskScore((BigDecimal) row[2]);
        item.setFloodHistoryCount(u.getFloodHistoryCount());
        item.setCreatedAt(u.getCreatedAt());
        item.setPendingReviewCount(((Number) row[3]).longValue());
        item.setRemainingCount(((Number) row[4]).longValue());
        return item;
    }

    // 다른 도메인(control 의 차단/해제 응답)도 같은 모양으로 쓰려고 public 으로 연다
    public static UnderpassDetailResponse toDetail(Underpass u) {
        UnderpassDetailResponse d = new UnderpassDetailResponse();
        d.setId(u.getId());
        d.setName(u.getName());
        d.setRegionId(u.getRegionId());
        d.setRoadAddress(u.getRoadAddress());
        d.setLatitude(u.getLatitude());
        d.setLongitude(u.getLongitude());
        d.setHeightM(u.getHeightM());
        d.setLengthM(u.getLengthM());
        d.setFloodHistoryCount(u.getFloodHistoryCount());
        d.setEarlyBlock(u.isEarlyBlock());
        d.setCurrentWaterCm(u.getCurrentWaterCm());
        d.setCurrentStatus(u.getCurrentStatus());
        d.setCurrentBlockEventId(u.getCurrentBlockEventId());
        d.setCurrentRiskLevel(u.getCurrentRiskLevel());
        d.setWidthM(u.getWidthM());
        d.setLanes(u.getLanes());
        d.setSafetyGrade(u.getSafetyGrade());
        d.setRiverDistM(u.getRiverDistM());
        d.setSeaDistM(u.getSeaDistM());
        d.setCurrentRiskScore(u.getCurrentRiskScore());
        d.setUpEntryCameraId(u.getUpEntryCameraId());
        d.setUpExitCameraId(u.getUpExitCameraId());
        d.setDownEntryCameraId(u.getDownEntryCameraId());
        d.setDownExitCameraId(u.getDownExitCameraId());
        d.setCreatedAt(u.getCreatedAt());
        return d;
    }

    static RiskScoreTrendItem toTrendItem(RiskScore rs) {
        RiskScoreTrendItem item = new RiskScoreTrendItem();
        item.setRiskScore(rs.getRiskScore());
        item.setRainfallMm(rs.getRainfallMm());
        item.setRainfall1hMm(rs.getRainfall1hMm());
        item.setRainfall3hMm(rs.getRainfall3hMm());
        item.setRainfall6hMm(rs.getRainfall6hMm());
        item.setRainfall12hMm(rs.getRainfall12hMm());
        item.setRainfall24hMm(rs.getRainfall24hMm());
        item.setPredictedAt(rs.getPredictedAt());
        return item;
    }
}