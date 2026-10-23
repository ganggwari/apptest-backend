package com.mbc.puf.internal.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.mbc.puf.alert.service.AlertService;
import com.mbc.puf.car.entity.Car;
import com.mbc.puf.car.repository.CarRepository;
import com.mbc.puf.common.enums.CameraRole;
import com.mbc.puf.common.enums.RecognitionStatus;
import com.mbc.puf.common.enums.ReviewStatus;
import com.mbc.puf.common.enums.RiskLevel;
import com.mbc.puf.common.enums.TransitDirection;
import com.mbc.puf.common.enums.TransitStatus;
import com.mbc.puf.common.enums.UnderpassCurrentStatus;
import com.mbc.puf.common.enums.UnderpassEventType;
import com.mbc.puf.common.exception.BusinessException;
import com.mbc.puf.common.util.CarNumber;
import com.mbc.puf.internal.dto.PlateLogCreateRequest;
import com.mbc.puf.internal.dto.PlateLogResponse;
import com.mbc.puf.internal.dto.RiskScoreCreateRequest;
import com.mbc.puf.internal.dto.RiskScoreResponse;
import com.mbc.puf.internal.entity.RiskScore;
import com.mbc.puf.common.jpa.DbClock;
import com.mbc.puf.common.enums.ManagerNotificationCategory;
import com.mbc.puf.notification.service.NotificationService;
import com.mbc.puf.stream.StreamPayloads;
import com.mbc.puf.stream.StreamPublisher;
import com.mbc.puf.internal.repository.RiskScoreRepository;
import com.mbc.puf.internal.repository.RiskForecastRepository;
import com.mbc.puf.internal.dto.RiskForecastCreateRequest;
import com.mbc.puf.internal.dto.RiskForecastIngestResponse;
import com.mbc.puf.internal.repository.WaterLevelRepository;
import com.mbc.puf.internal.entity.WaterLevel;
import com.mbc.puf.internal.dto.WaterLevelCreateRequest;
import com.mbc.puf.internal.dto.WaterLevelResponse;
import com.mbc.puf.risk.RiskLevelService;
import com.mbc.puf.plate.entity.PlateLog;
import com.mbc.puf.plate.repository.PlateLogRepository;
import com.mbc.puf.threshold.entity.Threshold;
import com.mbc.puf.threshold.repository.ThresholdRepository;
import com.mbc.puf.underpass.entity.Camera;
import com.mbc.puf.underpass.entity.Transit;
import com.mbc.puf.underpass.entity.Underpass;
import com.mbc.puf.underpass.entity.UnderpassEvent;
import com.mbc.puf.underpass.repository.CameraRepository;
import com.mbc.puf.underpass.repository.TransitRepository;
import com.mbc.puf.underpass.repository.UnderpassEventRepository;
import com.mbc.puf.underpass.repository.UnderpassRepository;

@Service
public class InternalIngestServiceImpl implements InternalIngestService {

	private static final Logger log = LoggerFactory.getLogger(InternalIngestServiceImpl.class);

	// confidence가 이 값 미만이면(매칭은 됐어도) LOW_CONFIDENCE로 본다. 정식 기준이 정해지면 조정
	private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("0.600");

	// 출구 보조 매칭(숫자만 같은 번호)을 허용할 입구 인식 후 시간(초). 같은 숫자의 다른 차와 잘못 짝짓지 않게 짧게 둔다
	private static final long SIMILAR_MATCH_WINDOW_SEC = 60;

	// 같은 카메라·같은 번호를 중복으로 보고 건너뛰는 시간(초)
	private static final long DUPLICATE_WINDOW_SEC = 30;

	// detected_num은 DB에서 NOT NULL이라, OCR이 아예 못 읽은 경우(null) 저장용으로 대신 넣는 값
	private static final String FAILED_PLACEHOLDER = "FAILED";

	// 캡처 이미지를 저장할 하위 폴더명 (file.upload-dir 아래)
	private static final String IMAGE_SUBDIR = "plate-logs";

	// 출구 매칭 대상 상태: 아직 안 나간 차
	private static final List<TransitStatus> ACTIVE_TRANSIT = List.of(TransitStatus.IN_TRANSIT, TransitStatus.REMAINING);

	private final RiskScoreRepository riskScoreRepository;
	private final PlateLogRepository plateLogRepository;
	private final UnderpassRepository underpassRepository;
	private final UnderpassEventRepository eventRepository;
	private final TransitRepository transitRepository;
	private final ThresholdRepository thresholdRepository;
	private final CameraRepository cameraRepository;
	private final CarRepository carRepository;
	private final AlertService alertService;
	private final NotificationService notificationService;   // 관리자 알림(위험도 변화, 판별 대기)
	private final StreamPublisher streamPublisher;           // SSE 실시간 이벤트
	private final DbClock dbClock;                           // 판별 마감까지 남은 시간 계산(DB 시계)
	private final WaterLevelRepository waterLevelRepository;   // 수위계 측정값 저장
	private final RiskLevelService riskLevelService;           // 점수·수위로 단계 판정 + 자동 차단
	private final RiskForecastRepository riskForecastRepository;   // 위험도 미래 예측 저장

	// 첨부파일 저장 위치. application.properties의 file.upload-dir (board 도메인 첨부와 같은 설정을 공유)
	@Value("${file.upload-dir}")
	private String uploadDir;

	public InternalIngestServiceImpl(RiskScoreRepository riskScoreRepository, PlateLogRepository plateLogRepository,
			UnderpassRepository underpassRepository, UnderpassEventRepository eventRepository,
			TransitRepository transitRepository, ThresholdRepository thresholdRepository,
			CameraRepository cameraRepository, CarRepository carRepository, AlertService alertService,
			NotificationService notificationService, StreamPublisher streamPublisher, DbClock dbClock,
			WaterLevelRepository waterLevelRepository, RiskLevelService riskLevelService,
			RiskForecastRepository riskForecastRepository) {
		this.riskForecastRepository = riskForecastRepository;
		this.waterLevelRepository = waterLevelRepository;
		this.riskLevelService = riskLevelService;
		this.riskScoreRepository = riskScoreRepository;
		this.plateLogRepository = plateLogRepository;
		this.underpassRepository = underpassRepository;
		this.eventRepository = eventRepository;
		this.transitRepository = transitRepository;
		this.thresholdRepository = thresholdRepository;
		this.cameraRepository = cameraRepository;
		this.carRepository = carRepository;
		this.alertService = alertService;
		this.notificationService = notificationService;
		this.streamPublisher = streamPublisher;
		this.dbClock = dbClock;
	}

	@Override
	@Transactional
	public RiskScoreResponse receiveRiskScore(RiskScoreCreateRequest request) {
		Underpass underpass = underpassRepository.findByIdAndDeletedFalse(request.getUnderpassId())
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
						"존재하지 않는 지하차도입니다. underpassId=" + request.getUnderpassId()));

		RiskScore riskScore = new RiskScore();
		riskScore.setUnderpassId(request.getUnderpassId());
		riskScore.setRiskScore(request.getRiskScore());
		riskScore.setRainfallMm(request.getRainfallMm());
		riskScore.setRainfall1hMm(request.getRainfall1hMm());
		riskScore.setRainfall3hMm(request.getRainfall3hMm());
		riskScore.setRainfall6hMm(request.getRainfall6hMm());
		riskScore.setRainfall12hMm(request.getRainfall12hMm());
		riskScore.setRainfall24hMm(request.getRainfall24hMm());
		riskScore.setPredictedAt(request.getPredictedAt());

		riskScoreRepository.save(riskScore);

		// 최신 점수를 반영하고, 점수와 수위를 같이 보고 단계를 다시 정한다 (단계 변화 이벤트·알림, 자동 차단까지 여기서)
		underpass.setCurrentRiskScore(request.getRiskScore());
		riskLevelService.reevaluate(underpass);

		// 실시간: 점수는 갱신될 때마다(등급 그대로여도) 공개로 보낸다
		streamPublisher.publishPublic("risk-update", underpass.getRegionId(),
				new StreamPayloads.RiskUpdate(underpass.getId(), request.getRiskScore(), underpass.getCurrentRiskLevel(),
						request.getRainfall1hMm(), request.getPredictedAt()));

		return new RiskScoreResponse(riskScore.getId(), riskScore.getUnderpassId(), riskScore.getRiskScore(),
				riskScore.getPredictedAt());
	}

	@Override
	@Transactional
	public RiskForecastIngestResponse receiveRiskForecast(RiskForecastCreateRequest request) {
		Underpass underpass = underpassRepository.findByIdAndDeletedFalse(request.getUnderpassId())
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
						"존재하지 않는 지하차도입니다. underpassId=" + request.getUnderpassId()));

		// 같은 시점은 덮어쓰고, 이미 지난 시점 행은 지운다 (예측 계산 시각 이전 시점)
		for (RiskForecastCreateRequest.Item item : request.getItems()) {
			riskForecastRepository.upsert(underpass.getId(), item.getRiskScore(), item.getRainfallMm(),
					item.getRainfall1hMm(), item.getRainfall3hMm(), item.getRainfall6hMm(), item.getRainfall12hMm(),
					item.getRainfall24hMm(), item.getTargetAt(), request.getPredictedAt());
		}
		riskForecastRepository.deletePast(underpass.getId(), request.getPredictedAt());

		// 실시간: 예측 그래프를 다시 그리라고 알린다 (id 는 지하차도 id)
		streamPublisher.dataChangedPublic(underpass.getRegionId(), "risk-forecast", "UPDATE", underpass.getId());
		return new RiskForecastIngestResponse(underpass.getId(), request.getItems().size());
	}

	@Override
	@Transactional
	public PlateLogResponse receivePlateLog(PlateLogCreateRequest request, MultipartFile image) {
		Camera camera = cameraRepository.findByIdAndDeletedFalse(request.getCameraId()).orElse(null);
		if (camera == null) {
			throw new BusinessException(HttpStatus.BAD_REQUEST, "존재하지 않는 카메라입니다. cameraId=" + request.getCameraId());
		}

		// 같은 카메라에서 같은 번호가 30초 안에 또 들어오면 저장하지 않고 건너뛴다 (정차·서행 중 연속 인식, 중복 알림 방지).
		// 응답은 먼저 저장된 기록을 그대로 돌려준다. 번호를 못 읽은 건(null)은 비교 대상이 아니다
		if (request.getDetectedNum() != null && request.getDetectedAt() != null) {
			PlateLog duplicate = plateLogRepository
					.findFirstByCameraIdAndDetectedNumAndDetectedAtBetweenOrderByDetectedAtDesc(request.getCameraId(),
							request.getDetectedNum(), request.getDetectedAt().minusSeconds(DUPLICATE_WINDOW_SEC),
							request.getDetectedAt())
					.orElse(null);
			if (duplicate != null) {
				log.info("번호판 중복 인식 건너뜀 (카메라 {}, {}초 이내): {}", request.getCameraId(), DUPLICATE_WINDOW_SEC,
						request.getDetectedNum());
				return new PlateLogResponse(duplicate.getId(), duplicate.getCameraId(), duplicate.getRecognitionStatus(),
						duplicate.getCarId(), duplicate.getReviewStatus(), duplicate.getImagePath(),
						duplicate.getDetectedAt());
			}
		}

		// 같은 번호를 여러 고객이 등록할 수 있다(가족 공유 차량). 삭제한 차량·탈퇴 고객의 차량은 빠진다.
		// plate_log.car_id 에는 첫 번째 차량을 남기고, 알림은 매칭된 고객마다 1건씩 보낸다
		List<Car> matchedCars = (request.getDetectedNum() == null) ? List.of()
				: carRepository.findActiveByCarNum(CarNumber.normalize(request.getDetectedNum()));
		Long carId = matchedCars.isEmpty() ? null : matchedCars.get(0).getId();

		RecognitionStatus recognitionStatus = determineRecognitionStatus(request.getDetectedNum(),
				request.getConfidence(), carId);

		// detected_num은 NOT NULL이라, OCR이 못 읽었으면(null) FAILED_PLACEHOLDER로 대신 저장한다.
		// recognitionStatus 판정과 차량 매칭은 원래 값(null 여부)을 기준으로 이미 끝낸 뒤라 영향 없다
		String storedDetectedNum = (request.getDetectedNum() != null) ? request.getDetectedNum() : FAILED_PLACEHOLDER;

		PlateLog plateLog = new PlateLog();
		plateLog.setCameraId(request.getCameraId());
		plateLog.setDetectedNum(storedDetectedNum);
		plateLog.setConfidence(request.getConfidence());
		plateLog.setImagePath(saveImage(image));
		plateLog.setRecognitionStatus(recognitionStatus);
		plateLog.setCarId(carId);
		plateLog.setDetectedAt(request.getDetectedAt());

		// 신뢰도가 기준 미만이면 판별대기로 건다. 등록 차량 매칭 여부(LOW_CONFIDENCE/UNMATCHED)와는 무관하고,
		// 신뢰도가 충분한 UNMATCHED나 OCR 실패(FAIL)는 걸지 않는다. recognition_status 값 자체는 그대로 둔다
		if (isLowConfidence(request.getDetectedNum(), request.getConfidence())) {
			plateLog.setReviewStatus(ReviewStatus.PENDING);
			plateLog.setReviewDeadline(request.getDetectedAt().plusMinutes(1)); // 만료되면 스케줄러가 AUTO_PROCESSED로 바꿈
		} else {
			plateLog.setReviewStatus(ReviewStatus.NONE);
			plateLog.setReviewDeadline(null);
		}

		plateLogRepository.save(plateLog);

		Underpass underpass = underpassRepository.findByIdAndDeletedFalse(camera.getUnderpassId()).orElse(null);
		if (underpass != null) {
			// 입출구 매칭: 이 카메라가 소속 지하차도의 상행/하행 입·출구 카메라로 지정돼 있을 때만 처리
			// (아직 지정 안 된 지하차도라면 조용히 건너뛴다 — 이건 "실제 게이트가 어디냐"의 문제라 camera_role과는 무관)
			processTransit(underpass, camera, plateLog, storedDetectedNum);

			// 신뢰도가 낮아 수동 판별 대기열에 들어간 건: 관리자 화면에 바로 추가 + 업무 알림
			if (plateLog.getReviewStatus() == ReviewStatus.PENDING) {
				streamPublisher.publishToAdmins(underpass.getRegionId(), "plate-queue-add",
						new StreamPayloads.PlateQueue(plateLog.getId(), underpass.getId(), camera.getId(),
								storedDetectedNum, plateLog.getConfidence(), ReviewStatus.PENDING,
								plateLog.getReviewDeadline(), StreamPayloads.remainingSeconds(ReviewStatus.PENDING,
										plateLog.getReviewDeadline(), dbClock.now())));
				notificationService.notifyManagers(ManagerNotificationCategory.TASK, underpass.getRegionId(),
						"번호판 수동 판별 필요", underpass.getName() + " · " + storedDetectedNum, "PLATE_LOG", plateLog.getId());
			}

			boolean matched = carId != null && (recognitionStatus == RecognitionStatus.SUCCESS
					|| recognitionStatus == RecognitionStatus.LOW_CONFIDENCE);

			// 알림: 접근 카메라면 선제(PREDICTIVE), 입구 직전 카메라면 최종(FINAL). 조건 판단과 차단 사유 포함은 AlertService 가 한다.
			// 판별 대기(PENDING)로 들어간 건은 여기서 보내지 않는다 — 자동 마감되거나 수동 판별이 끝나는 시점에 한 번만 보낸다
			// (대기 중에는 이 번호가 맞는지 아직 모르고, 마감될지 정정될지도 모르기 때문)
			if (matched && plateLog.getReviewStatus() != ReviewStatus.PENDING) {
				alertService.sendAlertsForPlate(underpass, camera.getCameraRole(), matchedCars, storedDetectedNum);
			}
		}

		return new PlateLogResponse(plateLog.getId(), plateLog.getCameraId(), plateLog.getRecognitionStatus(),
				plateLog.getCarId(), plateLog.getReviewStatus(), plateLog.getImagePath(), plateLog.getDetectedAt());
	}

	// ---- 내부 메서드 ----

	// FastAPI가 감지 순간 이미지를 같이 보냈으면 uploads/plate-logs/{yyyyMMdd}/{uuid}.{ext}로 저장하고
	// DB/응답에는 upload-dir 기준 상대경로만 남긴다. image가 없거나(null/empty) 저장이 실패하면 null을 반환한다
	// — 이미지 저장 실패가 번호판 인식 자체를 막을 이유는 아니므로, 로그만 남기고 나머지 처리는 계속 진행한다
	private String saveImage(MultipartFile image) {
		if (image == null || image.isEmpty()) {
			return null;
		}
		try {
			String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE); // yyyyMMdd
			Path dir = Paths.get(uploadDir, IMAGE_SUBDIR, today);
			Files.createDirectories(dir);

			String original = image.getOriginalFilename();
			String ext = "";
			if (original != null && original.contains(".")) {
				ext = original.substring(original.lastIndexOf('.'));
			}
			String filename = UUID.randomUUID() + ext;

			Path target = dir.resolve(filename);
			image.transferTo(target);

			return IMAGE_SUBDIR + "/" + today + "/" + filename;
		} catch (IOException e) {
			log.warn("번호판 캡처 이미지 저장 실패 (인식 결과 저장은 계속 진행): {}", e.getMessage());
			return null;
		}
	}

	// 수위계 측정값 수신: 저장 → 최신 수위 캐시 갱신 → 단계 재판정(경계 이상이면 자동 차단)
	@Override
	@Transactional
	public WaterLevelResponse receiveWaterLevel(WaterLevelCreateRequest request) {
		Underpass underpass = underpassRepository.findByIdAndDeletedFalse(request.getUnderpassId())
				.orElseThrow(() -> new BusinessException(HttpStatus.BAD_REQUEST,
						"존재하지 않는 지하차도입니다. underpassId=" + request.getUnderpassId()));

		WaterLevel water = new WaterLevel();
		water.setUnderpassId(request.getUnderpassId());
		water.setLevelCm(request.getLevelCm());
		water.setMeasuredAt(request.getMeasuredAt());
		waterLevelRepository.save(water);

		underpass.setCurrentWaterCm(request.getLevelCm());
		boolean autoBlocked = riskLevelService.reevaluate(underpass);

		streamPublisher.publishPublic("water-update", underpass.getRegionId(),
				new StreamPayloads.WaterUpdate(underpass.getId(), request.getLevelCm(), underpass.getCurrentRiskLevel(),
						request.getMeasuredAt()));

		return new WaterLevelResponse(water.getId(), underpass.getId(), request.getLevelCm(), request.getMeasuredAt(),
				underpass.getCurrentRiskLevel(), underpass.getCurrentStatus(), autoBlocked);
	}

	private void processTransit(Underpass underpass, Camera camera, PlateLog plateLog,
			String storedDetectedNum) {
		Long cameraId = camera.getId();
		boolean upEntry = cameraId.equals(underpass.getUpEntryCameraId());
		boolean downEntry = cameraId.equals(underpass.getDownEntryCameraId());
		boolean upExit = cameraId.equals(underpass.getUpExitCameraId());
		boolean downExit = cameraId.equals(underpass.getDownExitCameraId());

		if (upEntry || downEntry) {
			if (FAILED_PLACEHOLDER.equals(storedDetectedNum)) {
				// 입구에서 번호판을 아예 못 읽었으면(OCR 완전 실패) 어느 차인지 식별할 방법이 없다.
				// 여기서 그대로 IN_TRANSIT 기록을 만들면 car_num="FAILED"인 유령 통과기록이 쌓이고,
				// 출구에서도 같은 이유로 절대 매칭·종료되지 않아 차단 시 REMAINING(잔류차량) 목록에
				// 실체 없는 항목으로 계속 남는다. plate_log 자체는 그대로 저장되니 인식 실패 이력은
				// 남되, 통과 추적 대상에서는 제외한다.
				return;
			}
			Transit transit = new Transit();
			transit.setUnderpassId(underpass.getId());
			transit.setDirection(upEntry ? TransitDirection.UP : TransitDirection.DOWN);
			transit.setCarNum(storedDetectedNum);
			transit.setEntryPlateLogId(plateLog.getId());
			transit.setStatus(TransitStatus.IN_TRANSIT);
			transit.setEntryAt(plateLog.getDetectedAt());
			transitRepository.save(transit);
			streamPublisher.publishToAdmins(underpass.getRegionId(), "transit-update",
					new StreamPayloads.TransitUpdate(underpass.getId(), "ENTRY", transit.getDirection().name(),
							storedDetectedNum));
			return;
		}

		if (upExit || downExit) {
			if (FAILED_PLACEHOLDER.equals(storedDetectedNum)) {
				return; // 출구에서 번호판을 못 읽었으면 어느 통과 기록인지 특정할 수 없어 매칭을 시도하지 않는다
			}
			TransitDirection direction = upExit ? TransitDirection.UP : TransitDirection.DOWN;
			// 같은 방향에서 아직 안 나간(IN_TRANSIT/REMAINING) 기록과 매칭. 잔류 판정된 차량이 나중에 빠져나와도 여기서 정리된다
			Transit active = transitRepository.findFirstByUnderpassIdAndDirectionAndCarNumAndStatusInOrderByEntryAtDesc(
					underpass.getId(), direction, storedDetectedNum, ACTIVE_TRANSIT).orElse(null);
			if (active == null) {
				// 같은 번호가 없으면: 최근 SIMILAR_MATCH_WINDOW_SEC 안에 들어온 차 중 숫자가 모두 같은 것과 매칭한다.
				// OCR이 입구/출구 중 한쪽에서 가운데 한글만 다르게 읽는 경우(더↔저, 무↔부 등)를 보정하기 위함
				active = transitRepository.findSimilarActive(underpass.getId(), direction.name(), storedDetectedNum,
						storedDetectedNum.replaceAll("[^0-9]", ""),
						plateLog.getDetectedAt().minusSeconds(SIMILAR_MATCH_WINDOW_SEC), plateLog.getDetectedAt())
						.orElse(null);
			}
			if (active != null) {
				active.setStatus(TransitStatus.EXITED);
				active.setExitPlateLogId(plateLog.getId());
				active.setExitAt(plateLog.getDetectedAt());
				streamPublisher.publishToAdmins(underpass.getRegionId(), "transit-update",
						new StreamPayloads.TransitUpdate(underpass.getId(), "EXIT", direction.name(), active.getCarNum()));
			}
		}
	}

	// OCR이 번호를 읽었고(detectedNum != null) 신뢰도가 기준 미만이면 true. 차량 매칭 여부는 보지 않는다
	private boolean isLowConfidence(String detectedNum, BigDecimal confidence) {
		return detectedNum != null && confidence != null && confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;
	}

	private RecognitionStatus determineRecognitionStatus(String detectedNum, BigDecimal confidence, Long carId) {
		if (detectedNum == null) {
			return RecognitionStatus.FAIL; // OCR이 아예 못 읽음
		}
		if (carId == null) {
			return RecognitionStatus.UNMATCHED; // 읽긴 했는데 등록 차량과 매칭 안 됨
		}
		if (confidence != null && confidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0) {
			return RecognitionStatus.LOW_CONFIDENCE; // 매칭은 됐지만 신뢰도가 낮음
		}
		return RecognitionStatus.SUCCESS;
	}
}