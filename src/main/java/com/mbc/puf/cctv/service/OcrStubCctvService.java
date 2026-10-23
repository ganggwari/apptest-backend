package com.mbc.puf.cctv.service;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.security.LoginUser;
import com.mbc.puf.common.security.ScopeResolver;
import com.mbc.puf.common.util.CarNumber;
import com.mbc.puf.internal.dto.PlateLogCreateRequest;
import com.mbc.puf.internal.service.InternalIngestService;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.repository.CameraRepository;
import com.mbc.puf.underpass.repository.TransitRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * [시연용] OCR(FastAPI) 서버 없이 번호판 인식 결과를 만들어 넣는 구현. puf.ocr.mode=stub 일 때만 쓴다 (Render 시연).
 *
 * 진짜 OCR 이 하는 일과 똑같은 길을 탄다: 번호판을 만든 뒤 FastAPI 가 부르는 수신 서비스
 * (InternalIngestService.receivePlateLog)를 그대로 호출한다. 그래서 인식 기록, 30초 중복 건너뛰기, 판별 대기열,
 * 입구·출구 통과 기록, 접근/최종 알림, SSE·푸시가 진짜 데이터가 들어온 것과 똑같이 일어난다.
 *
 * 번호판 고르는 규칙 (실행 버튼 한 번에 plates-per-run 개를 interval-seconds 간격으로 넣는다)
 *  - 접근/진입직전 카메라: DB 에 등록된 고객 차량 번호에서 뽑는다 → 그 고객 앱으로 알림이 간다
 *  - 입구 카메라(상행/하행 입구로 지정된 카메라): 등록 차량 번호
 *  - 출구 카메라: 지금 그 지하차도 안에 있는 차량(입차 기록이 있는 번호)을 먼저, 모자라면 등록 차량
 *  - 일부는 등록되지 않은 가짜 번호(unregistered-percent), 일부는 신뢰도가 낮아 판별 대기로 가는 번호(low-confidence-percent)
 *  - 관리자가 carNum 을 직접 지정하면 그 번호가 맨 먼저 들어간다 (예: 시연에 쓸 고객 차량)
 * 영상은 없다. /stream, /snapshot 은 404 를 돌려준다 (OCR 영상은 로컬 시연에서만 보여준다).
 */
@Service
@ConditionalOnProperty(name = "puf.ocr.mode", havingValue = "stub")
public class OcrStubCctvService implements CctvService {

    private static final Logger log = LoggerFactory.getLogger(OcrStubCctvService.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String HANGUL = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주하허호";

    private final CameraRepository cameraRepository;
    private final UnderpassRepository underpassRepository;
    private final TransitRepository transitRepository;
    private final ScopeResolver scopeResolver;
    private final InternalIngestService ingestService;
    private final JdbcTemplate jdbc;
    private final DbClock dbClock;
    private final ObjectMapper objectMapper;

    private final int platesPerRun;
    private final int intervalSeconds;
    private final int unregisteredPercent;
    private final int lowConfidencePercent;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ocr-stub");
        t.setDaemon(true);
        return t;
    });

    /** 지금 돌고 있는 실행 한 건. 영상은 한 번에 하나만 돈다(진짜 OCR 서버와 같음) */
    private static final class Run {
        final long cameraId;
        final List<Planned> plan;
        final List<ScheduledFuture<?>> futures = new ArrayList<>();
        volatile int sent = 0;
        volatile boolean finished = false;
        volatile String error = null;

        Run(long cameraId, List<Planned> plan) {
            this.cameraId = cameraId;
            this.plan = plan;
        }
    }

    private record Planned(String carNum, BigDecimal confidence, String kind) {
    }

    private volatile Run current;

    public OcrStubCctvService(CameraRepository cameraRepository, UnderpassRepository underpassRepository,
                              TransitRepository transitRepository, ScopeResolver scopeResolver,
                              InternalIngestService ingestService, JdbcTemplate jdbc, DbClock dbClock,
                              ObjectMapper objectMapper,
                              @Value("${puf.ocr.stub.plates-per-run:6}") int platesPerRun,
                              @Value("${puf.ocr.stub.interval-seconds:2}") int intervalSeconds,
                              @Value("${puf.ocr.stub.unregistered-percent:20}") int unregisteredPercent,
                              @Value("${puf.ocr.stub.low-confidence-percent:10}") int lowConfidencePercent) {
        this.cameraRepository = cameraRepository;
        this.underpassRepository = underpassRepository;
        this.transitRepository = transitRepository;
        this.scopeResolver = scopeResolver;
        this.ingestService = ingestService;
        this.jdbc = jdbc;
        this.dbClock = dbClock;
        this.objectMapper = objectMapper;
        this.platesPerRun = Math.max(1, platesPerRun);
        this.intervalSeconds = Math.max(1, intervalSeconds);
        this.unregisteredPercent = clamp(unregisteredPercent);
        this.lowConfidencePercent = clamp(lowConfidencePercent);
        log.info("[OCR-STUB] 번호판 인식을 가짜로 대신합니다 (한 번에 {}개, {}초 간격)", this.platesPerRun, this.intervalSeconds);
    }

    // ---------------------------------------------------------------- 영상: 시연(stub)에서는 없다

    @Override
    public HttpResponse<InputStream> openStream(Long cameraId) {
        requireCamera(cameraId);
        throw new BusinessException(HttpStatus.NOT_FOUND, "시연(stub) 모드에서는 OCR 영상이 없습니다. 영상은 로컬 시연에서만 볼 수 있습니다.");
    }

    @Override
    public byte[] snapshot(Long cameraId) {
        requireCamera(cameraId);
        throw new BusinessException(HttpStatus.NOT_FOUND, "시연(stub) 모드에서는 OCR 영상이 없습니다. 영상은 로컬 시연에서만 볼 수 있습니다.");
    }

    // ---------------------------------------------------------------- 실행 버튼

    @Override
    public JsonNode plateState() {
        return state(current);
    }

    @Override
    public JsonNode startPlate(LoginUser loginUser, Long cameraId) {
        return startPlate(loginUser, cameraId, null);
    }

    @Override
    public synchronized JsonNode startPlate(LoginUser loginUser, Long cameraId, String carNum) {
        Camera camera = requireCamera(cameraId);
        Underpass underpass = requireOwnable(loginUser, camera);

        cancel(current);   // 돌고 있던 다른 실행은 멈춘다 (영상은 한 번에 하나)

        List<Planned> plan = buildPlan(camera, underpass, CarNumber.normalize(carNum));
        Run run = new Run(cameraId, plan);
        current = run;
        for (int i = 0; i < plan.size(); i++) {
            final Planned planned = plan.get(i);
            run.futures.add(scheduler.schedule(() -> emit(run, planned), (long) (i + 1) * intervalSeconds, TimeUnit.SECONDS));
        }
        run.futures.add(scheduler.schedule(() -> run.finished = true, (long) (plan.size() + 1) * intervalSeconds, TimeUnit.SECONDS));
        log.info("[OCR-STUB] 시작 camera={} role={} plates={}", cameraId, camera.getCameraRole(),
                plan.stream().map(Planned::carNum).toList());
        return state(run);
    }

    @Override
    public synchronized JsonNode stopPlate(LoginUser loginUser, Long cameraId) {
        Camera camera = requireCamera(cameraId);
        requireOwnable(loginUser, camera);
        Run run = current;
        if (run != null && run.cameraId == cameraId) {
            cancel(run);
        }
        return state(current);
    }

    /**
     * [시연 버튼] 번호판 하나가 카메라에 인식된 것으로 치고 수신 로직을 즉시 한 번 돌린다 (알림·입출차 기록·SSE·푸시).
     * 돌고 있는 자동 실행과는 별개다.
     */
    public JsonNode fireOnce(LoginUser loginUser, Long cameraId, String carNum) {
        Camera camera = requireCamera(cameraId);
        Underpass underpass = requireOwnable(loginUser, camera);
        String normalized = CarNumber.normalize(carNum);
        PlateLogCreateRequest request = new PlateLogCreateRequest();
        LocalDateTime detectedAt = now();
        request.setCameraId(cameraId);
        request.setDetectedNum(normalized);
        request.setConfidence(BigDecimal.valueOf(0.985));
        request.setDetectedAt(detectedAt);
        var saved = ingestService.receivePlateLog(request, null);
        boolean duplicate = saved.getDetectedAt() != null && !saved.getDetectedAt().equals(detectedAt);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("cameraId", cameraId);
        root.put("cameraRole", String.valueOf(camera.getCameraRole()));
        root.put("underpass", underpass.getName());
        root.put("carNum", normalized);
        root.put("plateLogId", saved.getId());
        root.put("recognitionStatus", String.valueOf(saved.getRecognitionStatus()));
        root.put("matchedCar", saved.getCarId() != null);
        root.put("duplicate", duplicate);
        root.put("message", duplicate ? "30초 안에 같은 카메라·같은 번호가 이미 들어와 건너뛰었습니다. 잠시 후 다시 누르세요."
                : "인식 처리 완료. 등록 차량이면 접근/최종 조건에 따라 알림이 생성됩니다.");
        return root;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }

    // ---------------------------------------------------------------- 번호판 계획

    private List<Planned> buildPlan(Camera camera, Underpass underpass, String forcedCarNum) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<String> registered = new ArrayList<>(registeredCarNums());
        Collections.shuffle(registered, rnd);
        Set<String> registeredSet = new LinkedHashSet<>(registered);

        // 출구 카메라면 지금 지하차도 안에 있는 차량이 먼저 나가야 통과 기록이 완료로 바뀐다
        List<String> inside = new ArrayList<>();
        if (isExitCamera(camera, underpass)) {
            inside.addAll(transitRepository.findActiveCarNums(underpass.getId()));
            Collections.shuffle(inside, rnd);
        }

        List<Planned> plan = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        if (forcedCarNum != null && !forcedCarNum.isBlank()) {
            plan.add(new Planned(forcedCarNum, confidence(rnd, 0.93, 0.99), "지정"));
            used.add(forcedCarNum);
        }
        for (String num : inside) {
            if (plan.size() >= platesPerRun) break;
            if (used.add(num)) plan.add(new Planned(num, confidence(rnd, 0.90, 0.99), "지하차도 안 차량"));
        }
        int guard = 0;
        while (plan.size() < platesPerRun && guard++ < platesPerRun * 20) {
            int roll = rnd.nextInt(100);
            if (roll < unregisteredPercent || registered.isEmpty()) {
                String fake = fakeCarNum(rnd, registeredSet);
                if (used.add(fake)) plan.add(new Planned(fake, confidence(rnd, 0.88, 0.99), "미등록"));
            } else {
                String num = registered.get(rnd.nextInt(registered.size()));
                if (!used.add(num)) continue;
                if (roll < unregisteredPercent + lowConfidencePercent) {
                    plan.add(new Planned(num, confidence(rnd, 0.45, 0.59), "신뢰도 낮음"));
                } else {
                    plan.add(new Planned(num, confidence(rnd, 0.82, 0.99), "등록 차량"));
                }
            }
        }
        return plan;
    }

    private List<String> registeredCarNums() {
        return jdbc.queryForList("""
                SELECT DISTINCT c.car_num FROM car c
                JOIN customer cu ON cu.id = c.customer_id
                WHERE c.is_deleted = FALSE AND cu.is_withdrawn = FALSE
                """, String.class);
    }

    private static boolean isExitCamera(Camera camera, Underpass u) {
        Long id = camera.getId();
        return Objects.equals(id, u.getUpExitCameraId()) || Objects.equals(id, u.getDownExitCameraId());
    }

    private static String fakeCarNum(ThreadLocalRandom rnd, Set<String> registered) {
        for (int i = 0; i < 20; i++) {
            String num = (10 + rnd.nextInt(90)) + String.valueOf(HANGUL.charAt(rnd.nextInt(HANGUL.length())))
                    + String.format("%04d", rnd.nextInt(10000));
            if (!registered.contains(num)) return num;
        }
        return "99허9999";
    }

    private static BigDecimal confidence(ThreadLocalRandom rnd, double min, double max) {
        return BigDecimal.valueOf(min + rnd.nextDouble() * (max - min)).setScale(3, java.math.RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- 넣기

    private void emit(Run run, Planned planned) {
        if (current != run || run.finished) return;
        try {
            PlateLogCreateRequest request = new PlateLogCreateRequest();
            request.setCameraId(run.cameraId);
            request.setDetectedNum(planned.carNum());
            request.setConfidence(planned.confidence());
            request.setDetectedAt(now());
            ingestService.receivePlateLog(request, null);
            run.sent++;
        } catch (Exception e) {
            run.error = e.getMessage();
            log.warn("[OCR-STUB] 번호판 넣기 실패 camera={} num={}: {}", run.cameraId, planned.carNum(), e.getMessage());
        }
    }

    private LocalDateTime now() {
        try {
            return dbClock.now();
        } catch (Exception e) {
            return LocalDateTime.now(SEOUL);
        }
    }

    private void cancel(Run run) {
        if (run == null) return;
        run.finished = true;
        run.futures.forEach(f -> f.cancel(false));
    }

    // ---------------------------------------------------------------- 응답 / 검사

    // 진짜 OCR 서버의 /control/plate 응답과 비슷한 모양: 카메라별 running, videoOpen, error
    private JsonNode state(Run run) {
        boolean running = run != null && !run.finished;
        ObjectNode root = objectMapper.createObjectNode();
        root.put("mode", "stub");
        root.put("running", running);
        if (run != null) {
            root.put("cameraId", run.cameraId);
            root.put("sent", run.sent);
            root.put("total", run.plan.size());
            ArrayNode plates = root.putArray("plates");
            run.plan.forEach(p -> plates.addObject().put("carNum", p.carNum()).put("kind", p.kind()));
        }
        ArrayNode cameras = root.putArray("cameras");
        if (run != null) {
            ObjectNode c = cameras.addObject();
            c.put("cameraId", run.cameraId);
            c.put("running", running);
            c.put("videoOpen", false);
            if (run.error != null) c.put("error", run.error); else c.putNull("error");
        }
        return root;
    }

    private Camera requireCamera(Long cameraId) {
        return cameraRepository.findByIdAndDeletedFalse(cameraId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "카메라를 찾을 수 없습니다. cameraId=" + cameraId));
    }

    private Underpass requireOwnable(LoginUser loginUser, Camera camera) {
        Underpass underpass = underpassRepository.findByIdAndDeletedFalse(camera.getUnderpassId())
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "카메라의 지하차도를 찾을 수 없습니다."));
        scopeResolver.requireOwnable(loginUser, underpass.getRegionId());
        return underpass;
    }

    private static int clamp(int percent) {
        return Math.max(0, Math.min(100, percent));
    }
}
