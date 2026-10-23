package com.mbc.puf.control.service;

import com.mbc.puf.common.enums.TransferAgency;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.enums.UnderpassEventType;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.pdf.ControlNoticeData;
import com.mbc.puf.risk.RiskPolicy;
import tools.jackson.databind.json.JsonMapper;
import com.mbc.puf.common.pdf.TransferPdfGenerator;
import com.mbc.puf.common.request.ScopeQuery;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.control.dto.BlockRequest;
import com.mbc.puf.control.dto.CtrlTransferItem;
import com.mbc.puf.control.dto.FireTransferRequest;
import com.mbc.puf.control.dto.ReasonPresetItem;
import com.mbc.puf.control.entity.CtrlTransfer;
import com.mbc.puf.control.entity.CtrlTransferCar;
import com.mbc.puf.control.entity.ReasonPreset;
import com.mbc.puf.control.repository.CtrlTransferCarRepository;
import com.mbc.puf.control.repository.CtrlTransferRepository;
import com.mbc.puf.control.repository.ReasonPresetRepository;
import com.mbc.puf.member.entity.Employee;
import com.mbc.puf.internal.entity.RiskScore;
import com.mbc.puf.internal.repository.RiskScoreRepository;
import com.mbc.puf.member.entity.Manager;
import com.mbc.puf.member.repository.EmployeeRepository;
import com.mbc.puf.member.repository.ManagerRegionRepository;
import com.mbc.puf.member.repository.ManagerRepository;
import com.mbc.puf.member.repository.ShiftRepository;
import com.mbc.puf.region.repository.RegionRepository;
import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.common.enums.ReasonType;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.display.service.VmsDisplayService;
import com.mbc.puf.stream.StreamPayloads;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.underpass.dto.UnderpassDetailResponse;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.entity.UnderpassEvent;
import com.mbc.puf.underpass.repository.UnderpassEventRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;
import com.mbc.puf.underpass.repository.TransitRepository;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ControlServiceImpl implements ControlService {

    private static final DateTimeFormatter FILE_DT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ReasonPresetRepository reasonPresetRepository;
    private final CtrlTransferRepository ctrlTransferRepository;
    private final CtrlTransferCarRepository ctrlTransferCarRepository;
    private final UnderpassRepository underpassRepository;
    private final UnderpassEventRepository eventRepository;
    private final TransitRepository transitRepository;
    private final ManagerRepository managerRepository;
    private final EmployeeRepository employeeRepository;
    private final ManagerRegionRepository managerRegionRepository;
    private final RegionRepository regionRepository;
    private final TransferPdfGenerator pdfGenerator;
    private final ScopeResolver scopeResolver;
    private final RiskScoreRepository riskScoreRepository;   // 전달서에 적을 이벤트 시점 위험도·강우
    private final ShiftRepository shiftRepository;           // 전달서에 적을 담당자 근무조
    private final EntityManager em;                          // 전광판 문구 조회(native)
    private final StreamPublisher streamPublisher;           // SSE 실시간 이벤트
    private final NotificationService notificationService;   // 관리자 알림(위험)
    private final RiskPolicy riskPolicy;                     // 해제 가능 수위 판단
    private final VmsDisplayService vmsDisplayService;       // 전광판 자동 표출(차단·해제 때 다시 맞춘다)

    // 이관 스냅샷(JSON) 만들고 읽는 용도
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public ControlServiceImpl(
            ReasonPresetRepository reasonPresetRepository,
            CtrlTransferRepository ctrlTransferRepository,
            CtrlTransferCarRepository ctrlTransferCarRepository,
            UnderpassRepository underpassRepository,
            UnderpassEventRepository eventRepository,
            TransitRepository transitRepository,
            ManagerRepository managerRepository,
            EmployeeRepository employeeRepository,
            ManagerRegionRepository managerRegionRepository,
            RegionRepository regionRepository,
            TransferPdfGenerator pdfGenerator,
            ScopeResolver scopeResolver,
            RiskScoreRepository riskScoreRepository,
            ShiftRepository shiftRepository,
            EntityManager em,
            StreamPublisher streamPublisher,
            NotificationService notificationService,
            RiskPolicy riskPolicy,
            VmsDisplayService vmsDisplayService) {
        this.riskPolicy = riskPolicy;
        this.vmsDisplayService = vmsDisplayService;
        this.reasonPresetRepository = reasonPresetRepository;
        this.ctrlTransferRepository = ctrlTransferRepository;
        this.ctrlTransferCarRepository = ctrlTransferCarRepository;
        this.underpassRepository = underpassRepository;
        this.eventRepository = eventRepository;
        this.transitRepository = transitRepository;
        this.managerRepository = managerRepository;
        this.employeeRepository = employeeRepository;
        this.managerRegionRepository = managerRegionRepository;
        this.regionRepository = regionRepository;
        this.pdfGenerator = pdfGenerator;
        this.scopeResolver = scopeResolver;
        this.riskScoreRepository = riskScoreRepository;
        this.shiftRepository = shiftRepository;
        this.em = em;
        this.streamPublisher = streamPublisher;
        this.notificationService = notificationService;
    }

    // 차단/해제 직후: 공개 이벤트(지도·목록 갱신) + 담당 지역 관리자 알림(위험). 커밋 뒤에 전송된다
    private void publishControlChange(Underpass underpass, UnderpassCurrentStatus status, String detail) {
        streamPublisher.publishPublic("control-change", underpass.getRegionId(),
                new StreamPayloads.ControlChange(underpass.getId(), underpass.getName(), status, LocalDateTime.now()));
        boolean blocked = status == UnderpassCurrentStatus.BLOCKED;
        notificationService.notifyManagers(ManagerNotificationCategory.RISK, underpass.getRegionId(),
                blocked ? "지하차도 통제(차단)" : "지하차도 통제 해제",
                underpass.getName() + (detail == null || detail.isBlank() ? "" : " — " + detail), "UNDERPASS", underpass.getId());
    }

    // ─────────────────────────── 사유 프리셋 ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ReasonPresetItem> getReasonPresets(ReasonType type) {
        List<ReasonPreset> presets = type == null
                ? reasonPresetRepository.findAllByOrderByIdAsc()
                : reasonPresetRepository.findByTypeOrderByIdAsc(type);
        return presets.stream()
                .map(p -> new ReasonPresetItem(p.getId(), p.getType(), p.getContent()))
                .collect(Collectors.toList());
    }

    // ─────────────────────────── 차단 ───────────────────────────

    @Override
    @Transactional
    public PdfResult blockUnderpass(LoginUser loginUser, Long underpassId, BlockRequest request) {
        Underpass underpass = requireUnderpass(underpassId);
        scopeResolver.requireOwnable(loginUser, underpass.getRegionId());

        if (underpass.getCurrentStatus() == UnderpassCurrentStatus.BLOCKED) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 차단된 지하차도입니다.");
        }

        String reason = resolveReason(request, ReasonType.BLOCK);
        String reasonNote = resolveReasonNote(request);   // 프리셋이 '기타'인데 상세 사유가 없으면 여기서 400

        BlockOutcome out = doBlock(underpass, loginUser.id(), false, reason, reasonNote, null);

        // PDF 생성 + 최신 상태 다시 조회해서 응답 구성
        LocalDateTime now = out.transfer().getCreatedAt() != null ? out.transfer().getCreatedAt() : LocalDateTime.now();
        byte[] pdf = pdfGenerator.generatePolicePdf(out.notice());
        UnderpassDetailResponse detail = toDetail(underpassRepository.findByIdAndDeletedFalse(underpassId).orElseThrow());
        String filename = String.format("경찰이관_차단_%s_%s.pdf", underpass.getName(), FILE_DT.format(now));
        return new PdfResult(filename, pdf, detail);
    }

    /**
     * 수위가 차단 기준에 닿았을 때 시스템이 직접 차단한다(RiskLevelService 가 호출).
     * 수동 차단과 같은 일을 한다: 차단 이벤트, 잔류차량 전환, 알림, 경찰 이관 기록(PDF 는 이력에서 내려받음), 전광판 전환.
     * 처리한 관리자가 없으므로 operator 는 NULL, 이벤트에는 자동(is_auto) 표시가 붙는다.
     */
    @Override
    @Transactional
    public void autoBlock(Long underpassId, BigDecimal waterCm) {
        Underpass underpass = requireUnderpass(underpassId);
        if (underpass.getCurrentStatus() == UnderpassCurrentStatus.BLOCKED) {
            return;   // 이미 차단 중이면 중복 처리하지 않는다
        }
        BigDecimal block = riskPolicy.blockCm(underpass);
        String reason = "수위 " + waterCm.stripTrailingZeros().toPlainString() + "cm — 차단 기준 "
                + block.stripTrailingZeros().toPlainString() + "cm 도달(자동 차단)";
        doBlock(underpass, null, true, reason, null, waterCm);
    }

    private record BlockOutcome(UnderpassEvent event, CtrlTransfer transfer, ControlNoticeData notice) {}

    // 수동/자동 차단이 같이 쓰는 본체. operatorId 가 NULL 이면 자동 차단
    private BlockOutcome doBlock(Underpass underpass, Long operatorId, boolean auto, String reason, String reasonNote,
                                 BigDecimal waterCm) {
        Long underpassId = underpass.getId();

        // 1) 차단 이벤트 저장
        UnderpassEvent event = new UnderpassEvent();
        event.setUnderpassId(underpassId);
        event.setEventType(UnderpassEventType.BLOCK);
        event.setOperatorId(operatorId);
        event.setAuto(auto);
        event.setWaterCm(waterCm);
        event.setReason(reason);
        event.setReasonNote(reasonNote);
        eventRepository.saveAndFlush(event);

        // 2) 지하차도 상태 갱신
        underpass.setCurrentStatus(UnderpassCurrentStatus.BLOCKED);
        underpass.setCurrentBlockEventId(event.getId());

        // 3) IN_TRANSIT → REMAINING 일괄 처리 (이 호출이 영속성 컨텍스트를 비우므로 엔티티 수정은 위에서 끝낸다)
        transitRepository.markRemaining(underpassId);
        publishControlChange(underpass, UnderpassCurrentStatus.BLOCKED, auto ? reason : null);
        streamPublisher.publishToAdmins(underpass.getRegionId(), "transit-update",
                new StreamPayloads.TransitUpdate(underpassId, "REMAINING", null, null));

        // 4) 이 지하차도와 연결된 전광판들의 문구를 다시 맞춘다 (차단 → 빨강 "통제 중" 페이지)
        vmsDisplayService.reconcileForUnderpass(underpassId, event.getId(), operatorId);

        // 5) 경찰 이관 기록 저장 (전달서에 들어갈 내용은 스냅샷으로 같이 저장)
        ControlNoticeData notice = buildNotice(underpass, event, null, operatorId, null);
        CtrlTransfer transfer = saveTransfer(event.getId(), TransferAgency.POLICE, null, operatorId,
                snapshotOf(notice, waterCm, auto));
        return new BlockOutcome(event, transfer, notice);
    }

    // ─────────────────────────── 해제 ───────────────────────────

    @Override
    @Transactional
    public PdfResult releaseUnderpass(LoginUser loginUser, Long underpassId, BlockRequest request) {
        Underpass underpass = requireUnderpass(underpassId);
        scopeResolver.requireOwnable(loginUser, underpass.getRegionId());

        if (underpass.getCurrentStatus() == UnderpassCurrentStatus.NORMAL) {
            throw new BusinessException(HttpStatus.CONFLICT, "이미 정상 상태인 지하차도입니다.");
        }

        // 수위가 아직 차단 기준 이상이면 해제할 수 없다 (자동 차단 기준과 같은 값)
        if (riskPolicy.reachedBlock(underpass, underpass.getCurrentWaterCm())) {
            throw new BusinessException(HttpStatus.CONFLICT, "수위가 차단 기준("
                    + riskPolicy.blockCm(underpass).stripTrailingZeros().toPlainString() + "cm) 이상이라 해제할 수 없습니다. 현재 수위 "
                    + underpass.getCurrentWaterCm().stripTrailingZeros().toPlainString() + "cm");
        }

        // 차단 이벤트 조회 (PDF에 차단 시각 표시용)
        UnderpassEvent blockEvent = underpass.getCurrentBlockEventId() != null
                ? eventRepository.findById(underpass.getCurrentBlockEventId()).orElse(null)
                : null;

        String reason = resolveReason(request, ReasonType.RELEASE);
        String reasonNote = resolveReasonNote(request);

        // 1) 해제 이벤트 저장
        UnderpassEvent event = new UnderpassEvent();
        event.setUnderpassId(underpassId);
        event.setEventType(UnderpassEventType.RELEASE);
        event.setOperatorId(loginUser.id());
        event.setReason(reason);
        event.setReasonNote(reasonNote);
        eventRepository.saveAndFlush(event);

        // 2) 지하차도 상태 갱신
        underpass.setCurrentStatus(UnderpassCurrentStatus.NORMAL);
        underpass.setCurrentBlockEventId(null);
        publishControlChange(underpass, UnderpassCurrentStatus.NORMAL, null);

        // 3) 전광판 문구를 다시 맞춘다 (해제 → 초록 "통행 재개" 페이지, 일정 시간 뒤 사라짐)
        vmsDisplayService.reconcileForUnderpass(underpassId, event.getId(), loginUser.id());

        // 4) 경찰 이관 기록 저장 + PDF 생성
        ControlNoticeData notice = buildNotice(underpass, event, blockEvent, loginUser.id(), null);
        CtrlTransfer transfer = saveTransfer(event.getId(), TransferAgency.POLICE, null, loginUser.id(),
                snapshotOf(notice, null, false));
        LocalDateTime now = transfer.getCreatedAt() != null ? transfer.getCreatedAt() : LocalDateTime.now();
        byte[] pdf = pdfGenerator.generatePolicePdf(notice);

        UnderpassDetailResponse detail = toDetail(underpassRepository.findByIdAndDeletedFalse(underpassId).orElseThrow());
        String filename = String.format("경찰이관_해제_%s_%s.pdf", underpass.getName(), FILE_DT.format(now));
        return new PdfResult(filename, pdf, detail);
    }

    // ─────────────────────────── 소방 이관 ───────────────────────────

    @Override
    @Transactional
    public PdfResult fireTransfer(LoginUser loginUser, Long underpassId, FireTransferRequest request) {
        Underpass underpass = requireUnderpass(underpassId);
        scopeResolver.requireOwnable(loginUser, underpass.getRegionId());

        // 이관할 이벤트가 실제로 있고, 이 지하차도의 것인지 확인 (없는 id 를 보내면 FK 오류로 500 이 나던 문제)
        UnderpassEvent sourceEvent = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND,
                        "이벤트를 찾을 수 없습니다. eventId=" + request.getEventId()));
        if (!underpassId.equals(sourceEvent.getUnderpassId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "이 지하차도의 이벤트가 아닙니다. eventId=" + request.getEventId());
        }

        // 이관 기록 저장
        Map<String, Object> fireSnap = new LinkedHashMap<>();
        fireSnap.put("carNums", request.getCarNums().stream().map(String::trim).collect(Collectors.toList()));
        fireSnap.put("memo", request.getMemo());
        CtrlTransfer transfer = saveTransfer(request.getEventId(), TransferAgency.FIRE, request.getMemo(), loginUser.id(),
                toJson(fireSnap));

        // 차량번호 저장
        for (String carNum : request.getCarNums()) {
            CtrlTransferCar car = new CtrlTransferCar();
            car.setCtrlTransferId(transfer.getId());
            car.setCarNum(carNum.trim());
            ctrlTransferCarRepository.save(car);
        }

        // PDF 생성
        ManagerInfo mgr = managerInfo(loginUser.id());
        LocalDateTime now = transfer.getCreatedAt() != null ? transfer.getCreatedAt() : LocalDateTime.now();
        byte[] pdf = pdfGenerator.generateFirePdf(
                underpass.getName(), request.getCarNums(),
                mgr.name(), mgr.regionName(),
                now, request.getMemo());

        streamPublisher.dataChangedToAdmins(underpass.getRegionId(), "fire-transfer", "CREATE", transfer.getId());

        UnderpassDetailResponse detail = toDetail(underpass);
        String filename = String.format("소방이관_%s_%s.pdf", underpass.getName(), FILE_DT.format(now));
        return new PdfResult(filename, pdf, detail);
    }

    // ─────────────────────────── 이관 기록 조회 ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CtrlTransferItem> getTransferHistory(LoginUser loginUser, Long underpassId) {
        requireUnderpass(underpassId);
        // 담당 지역 밖의 지하차도 이관 기록은 볼 수 없다 (403)
        scopeResolver.resolve(loginUser, ScopeQuery.of(null, underpassId));

        // event → underpass_id 역추적이 필요하므로 native query로 event.underpass_id = underpassId 필터링
        List<CtrlTransfer> transfers = ctrlTransferRepository.findByUnderpassId(underpassId);
        return transfers.stream().map(this::toItem).collect(Collectors.toList());
    }

    // ─────────────────────────── PDF 재출력 ───────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PdfResult redownloadPdf(LoginUser loginUser, Long transferId) {
        CtrlTransfer transfer = ctrlTransferRepository.findById(transferId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "이관 기록을 찾을 수 없습니다. id=" + transferId));

        UnderpassEvent event = eventRepository.findById(transfer.getEventId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."));

        Underpass underpass = underpassRepository.findByIdAndDeletedFalse(event.getUnderpassId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "지하차도를 찾을 수 없습니다."));
        // 담당 지역 밖의 이관문서는 다시 받을 수 없다 (403)
        scopeResolver.resolve(loginUser, ScopeQuery.of(null, underpass.getId()));

        ManagerInfo mgr = managerInfo(transfer.getManagerId());
        LocalDateTime transferAt = transfer.getCreatedAt() != null ? transfer.getCreatedAt() : LocalDateTime.now();

        byte[] pdf;
        String filename;

        if (transfer.getAgency() == TransferAgency.POLICE) {
            String eventTypeLabel = event.getEventType() == UnderpassEventType.BLOCK ? "차단" : "해제";

            // 해제 이벤트라면 그 해제 직전의 차단 이벤트를 찾는다 (해제 후에는 current_block_event_id 가 비어 있다)
            UnderpassEvent blockEvent = event.getEventType() == UnderpassEventType.RELEASE && event.getCreatedAt() != null
                    ? eventRepository.findFirstByUnderpassIdAndEventTypeAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(
                            underpass.getId(), UnderpassEventType.BLOCK, event.getCreatedAt()).orElse(null)
                    : null;

            // 이관 당시 스냅샷이 있으면 그 값을 쓴다(지금 다시 계산하면 점수·전광판 문구가 달라져 있을 수 있음)
            pdf = pdfGenerator.generatePolicePdf(applySnapshot(
                    buildNotice(underpass, event, blockEvent, transfer.getManagerId(), transfer.getMemo()),
                    transfer.getSnapshot()));
            filename = String.format("경찰이관_%s_%s_%s.pdf", eventTypeLabel, underpass.getName(), FILE_DT.format(transferAt));
        } else {
            List<String> carNums = ctrlTransferCarRepository.findByCtrlTransferId(transferId)
                    .stream().map(CtrlTransferCar::getCarNum).collect(Collectors.toList());
            pdf = pdfGenerator.generateFirePdf(
                    underpass.getName(), carNums,
                    mgr.name(), mgr.regionName(),
                    transferAt, transfer.getMemo());
            filename = String.format("소방이관_%s_%s.pdf", underpass.getName(), FILE_DT.format(transferAt));
        }

        UnderpassDetailResponse detail = toDetail(underpass);
        return new PdfResult(filename, pdf, detail);
    }

    // ─────────────────────────── 내부 헬퍼 ───────────────────────────

    private Underpass requireUnderpass(Long id) {
        return underpassRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "존재하지 않는 지하차도입니다. id=" + id));
    }

    /** 프리셋 문구가 이것이면 '기타'로 보고, 상세 사유를 꼭 적게 한다 */
    private static final String OTHER_REASON = "기타";

    /** 사유에 덧붙인 상세 사유. 공백뿐이면 없는 것으로 본다. 프리셋이 '기타'인데 비어 있으면 400 */
    private String resolveReasonNote(BlockRequest request) {
        if (request == null) return null;
        String note = request.getReasonNote() == null ? null : request.getReasonNote().trim();
        if (note != null && note.isEmpty()) note = null;
        if (note == null && request.getPresetId() != null) {
            boolean other = reasonPresetRepository.findById(request.getPresetId())
                    .map(p -> OTHER_REASON.equals(p.getContent().trim())).orElse(false);
            if (other) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "사유가 '기타'일 때는 상세 사유(reasonNote)를 입력해 주세요.");
            }
        }
        return note;
    }

    /** 프리셋 또는 직접 입력 사유 결정 */
    private String resolveReason(BlockRequest request, ReasonType expectedType) {
        if (request == null) return null;
        if (request.getPresetId() != null) {
            ReasonPreset preset = reasonPresetRepository.findById(request.getPresetId())
                    .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "사유 프리셋을 찾을 수 없습니다."));
            // 차단 화면에서 해제 사유를(또는 반대로) 고르는 실수를 서버에서도 막는다
            if (preset.getType() != expectedType) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, expectedType == ReasonType.BLOCK
                        ? "차단 사유 프리셋이 아닙니다." : "해제 사유 프리셋이 아닙니다.");
            }
            return preset.getContent();
        }
        return request.getReason();
    }

    private CtrlTransfer saveTransfer(Long eventId, TransferAgency agency, String memo, Long managerId, String snapshot) {
        CtrlTransfer transfer = new CtrlTransfer();
        transfer.setSnapshot(snapshot);
        transfer.setEventId(eventId);
        transfer.setAgency(agency);
        transfer.setMemo(memo);
        transfer.setManagerId(managerId);
        return ctrlTransferRepository.saveAndFlush(transfer);
    }

    /** 관리자 이름 + 첫 번째 담당 지역명 */
    private ManagerInfo managerInfo(Long managerId) {
        if (managerId == null) {
            return new ManagerInfo("시스템(자동 차단)", "-");   // 자동 차단으로 생긴 기록
        }
        Manager manager = managerRepository.findById(managerId).orElse(null);
        String name = "알 수 없음";
        if (manager != null && manager.getEmployeeId() != null) {
            Employee emp = employeeRepository.findById(manager.getEmployeeId()).orElse(null);
            if (emp != null) name = emp.getName();
        }

        String regionName = managerRegionRepository.findRegionIds(managerId).stream()
                .findFirst()
                .flatMap(regionRepository::findById)
                .map(r -> r.getName())
                .orElse("-");

        return new ManagerInfo(name, regionName);
    }

    /**
     * 통제 정보 전달서에 들어갈 내용을 모은다. 위험도·강우·전광판 문구·근무조는 있는 것만 채우고 없으면 비워 둔다.
     *
     * @param event      이 문서가 다루는 이벤트 (BLOCK 또는 RELEASE)
     * @param blockEvent 해제 문서일 때 그 해제의 짝이 되는 차단 이벤트 (차단 문서면 null)
     */
    private ControlNoticeData buildNotice(Underpass underpass, UnderpassEvent event, UnderpassEvent blockEvent,
                                          Long managerId, String memo) {
        LocalDateTime at = event.getCreatedAt() != null ? event.getCreatedAt() : LocalDateTime.now();

        // 이벤트 시점 직전에 나온 위험도 예측 1건 (점수 + 강우)
        Double score = null;
        Double rain = null;
        List<RiskScore> scores = riskScoreRepository
                .findByUnderpassIdAndPredictedAtLessThanEqualOrderByPredictedAtDesc(underpass.getId(), at, Limit.of(1));
        if (!scores.isEmpty()) {
            RiskScore rs = scores.get(0);
            score = rs.getRiskScore() != null ? rs.getRiskScore().doubleValue() : null;
            rain = rs.getRainfallMm() != null ? rs.getRainfallMm().doubleValue() : null;
        }

        ManagerInfo mgr = managerInfo(managerId);
        return new ControlNoticeData(
                underpass.getName(), underpass.getRoadAddress(),
                event.getEventType() == UnderpassEventType.BLOCK, at, event.getReason(), event.getReasonNote(),
                score, rain,
                // 전광판 문구는 통제 시작 문서에만 적는다 (해제 후에는 문구가 바뀌므로 현재 값이 해제 시점과 다를 수 있음)
                event.getEventType() == UnderpassEventType.BLOCK ? currentDisplayText(underpass.getId()) : null,
                mgr.name(), mgr.regionName(), shiftLabel(managerId),
                blockEvent != null ? blockEvent.getCreatedAt() : null,
                blockEvent != null ? blockEvent.getReason() : null,
                blockEvent != null ? blockEvent.getReasonNote() : null,
                memo);
    }

    // 그 지하차도에서 가장 가까운 전광판의 첫 페이지 문구 (전광판이 없거나 페이지가 비어 있으면 null)
    private String currentDisplayText(Long underpassId) {
        return vmsDisplayService.nearestFirstPageText(underpassId);
    }

    // "1조 06:00~14:00" (근무조가 없으면 null)
    private String shiftLabel(Long managerId) {
        if (managerId == null) return null;
        Manager manager = managerRepository.findById(managerId).orElse(null);
        if (manager == null || manager.getShiftId() == null) return null;
        return shiftRepository.findById(manager.getShiftId())
                .map(sh -> sh.getName() + " " + sh.getStartTime() + "~" + sh.getEndTime())
                .orElse(null);
    }

    private CtrlTransferItem toItem(CtrlTransfer t) {
        CtrlTransferItem item = new CtrlTransferItem();
        item.setId(t.getId());
        item.setEventId(t.getEventId());
        item.setAgency(t.getAgency());
        item.setMemo(t.getMemo());
        item.setManagerId(t.getManagerId());
        item.setCreatedAt(t.getCreatedAt());

        // 관리자 이름
        ManagerInfo mgr = managerInfo(t.getManagerId());
        item.setManagerName(mgr.name());

        // 소방 이관이면 차량번호 목록 포함
        if (t.getAgency() == TransferAgency.FIRE) {
            List<String> cars = ctrlTransferCarRepository.findByCtrlTransferId(t.getId())
                    .stream().map(CtrlTransferCar::getCarNum).collect(Collectors.toList());
            item.setCarNums(cars);
        }
        return item;
    }

    private UnderpassDetailResponse toDetail(Underpass u) {
        // UnderpassDetailResponse 생성자 / 팩토리 메서드가 없으므로 setter 방식 사용
        // (UnderpassServiceImpl 의 toDetail 이 private 이라 여기서는 간략하게 채운다)
        UnderpassDetailResponse r = new UnderpassDetailResponse();
        r.setId(u.getId());
        r.setName(u.getName());
        r.setRegionId(u.getRegionId());
        r.setRoadAddress(u.getRoadAddress());
        r.setCurrentStatus(u.getCurrentStatus());
        r.setCurrentRiskLevel(u.getCurrentRiskLevel());
        r.setCurrentRiskScore(u.getCurrentRiskScore());
        return r;
    }

    // ─────────────────────────── 스냅샷 / 전광판 전환 ───────────────────────────

    private static String toJson(Map<String, Object> map) {
        return JSON.writeValueAsString(map);
    }

    // 전달서 내용을 스냅샷(JSON)으로. 값이 없는 항목은 넣지 않는다
    private String snapshotOf(ControlNoticeData n, BigDecimal waterCm, boolean auto) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auto", auto);
        if (n.riskScore() != null) m.put("riskScore", n.riskScore());
        if (n.rainfallMm() != null) m.put("rainfallMm", n.rainfallMm());
        if (waterCm != null) m.put("waterCm", waterCm);
        if (n.displayText() != null) m.put("displayText", n.displayText());
        m.put("managerName", n.managerName());
        m.put("managerRegion", n.managerRegion());
        if (n.shiftLabel() != null) m.put("shiftLabel", n.shiftLabel());
        return toJson(m);
    }

    // 스냅샷에 있는 값으로 전달서 내용을 덮어쓴다(스냅샷이 없으면 그대로)
    @SuppressWarnings("unchecked")
    private ControlNoticeData applySnapshot(ControlNoticeData base, String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return base;
        Map<String, Object> m;
        try {
            m = JSON.readValue(snapshot, Map.class);
        } catch (RuntimeException e) {
            return base;
        }
        return new ControlNoticeData(base.underpassName(), base.address(), base.blockEvent(), base.eventAt(), base.reason(), base.reasonNote(),
                m.get("riskScore") instanceof Number v ? Double.valueOf(v.doubleValue()) : base.riskScore(),
                m.get("rainfallMm") instanceof Number v ? Double.valueOf(v.doubleValue()) : base.rainfallMm(),
                m.get("displayText") instanceof String t ? t : base.displayText(),
                m.get("managerName") instanceof String t ? t : base.managerName(),
                m.get("managerRegion") instanceof String t ? t : base.managerRegion(),
                m.get("shiftLabel") instanceof String t ? t : base.shiftLabel(),
                base.blockAt(), base.blockReason(), base.blockReasonNote(), base.memo());
    }

    private record ManagerInfo(String name, String regionName) {}
}