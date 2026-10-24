-- 시연용 시드 (v9): 잔류차량 · 통과/번호판 로그 · 위험도 이력 · 이벤트 · 예지보전 이벤트/조치 · 관리자 알림
-- 실행 순서: 1 → 2 → 3 → 4 → 5. (OCR 영상 대신 이 데이터를 화면에서 본다)

-- 1. 번호판 인식 로그 + 통과 기록 (초량 1: 입구 228 / 출구 229, 북항 14: 252→251, 충장 15: 256→254)
DO $$
DECLARE
  r RECORD; v_in BIGINT; v_out BIGINT;
BEGIN
  FOR r IN
    SELECT * FROM (VALUES
      -- underpass, 입구캠, 출구캠, 번호, 방향, 상태, 분 전
      (1, 228, 229, '12고1252', 'UP',   'REMAINING', 42),
      (1, 228, 229, '821루4545','DOWN', 'REMAINING', 35),
      (1, 228, 229, '50마2269', 'UP',   'REMAINING', 28),
      (1, 228, 229, '23나5012', 'UP',   'EXITED',    55),
      (1, 228, 229, '19저0362', 'DOWN', 'EXITED',    50),
      (1, 228, 229, '117머1687','UP',   'EXITED',    47),
      (14,252, 251, '34가5678', 'UP',   'REMAINING', 20),
      (14,252, 251, '56다1234', 'DOWN', 'EXITED',    44),
      (15,256, 254, '78라9012', 'DOWN', 'REMAINING', 15),
      (15,256, 254, '90마3456', 'UP',   'REMAINING', 12),
      (15,256, 254, '31바7788', 'UP',   'EXITED',    38)
    ) AS t(uid, cin, cout, num, dir, st, mins)
  LOOP
    INSERT INTO plate_log (camera_id, detected_num, confidence, recog_status, review_status, detected_at)
    VALUES (r.cin, r.num, 0.96, 'SUCCESS', 'NONE', (NOW() AT TIME ZONE 'Asia/Seoul') - make_interval(mins => r.mins)) RETURNING id INTO v_in;
    v_out := NULL;
    IF r.st = 'EXITED' THEN
      INSERT INTO plate_log (camera_id, detected_num, confidence, recog_status, review_status, detected_at)
      VALUES (r.cout, r.num, 0.95, 'SUCCESS', 'NONE', (NOW() AT TIME ZONE 'Asia/Seoul') - make_interval(mins => r.mins - 3)) RETURNING id INTO v_out;
    END IF;
    INSERT INTO transit (underpass_id, car_num, direction, entry_log_id, exit_log_id, status, entry_at, exit_at)
    VALUES (r.uid, r.num, r.dir::transit_direction, v_in, v_out, r.st::transit_status,
            (NOW() AT TIME ZONE 'Asia/Seoul') - make_interval(mins => r.mins),
            CASE WHEN v_out IS NULL THEN NULL ELSE (NOW() AT TIME ZONE 'Asia/Seoul') - make_interval(mins => r.mins - 3) END);
  END LOOP;
  -- 신뢰도 낮음/실패 검토 대기 로그 몇 건
  INSERT INTO plate_log (camera_id, detected_num, confidence, recog_status, review_status, review_due, detected_at) VALUES
    (228, '12고12?2', 0.512, 'LOW_CONFIDENCE', 'PENDING', (NOW() AT TIME ZONE 'Asia/Seoul') + interval '20 minutes', (NOW() AT TIME ZONE 'Asia/Seoul') - interval '4 minutes'),
    (252, '34?5678', 0.430, 'LOW_CONFIDENCE', 'PENDING', (NOW() AT TIME ZONE 'Asia/Seoul') + interval '18 minutes', (NOW() AT TIME ZONE 'Asia/Seoul') - interval '6 minutes');
END $$;

-- 2. 위험도 점수 · 수위 이력 (최근 12시간, 30분 간격)
INSERT INTO risk_score (underpass_id, score, rain, rain_1h, rain_3h, rain_6h, rain_12h, rain_24h, predicted_at)
SELECT u, ROUND((8 + 40 * EXP(-((g - 18)^2) / 40.0) + (u % 3) * 4)::numeric, 2), 0, 0, 0, 0, 0, 0,
       (NOW() AT TIME ZONE 'Asia/Seoul') - make_interval(mins => (24 - g) * 30)
FROM unnest(ARRAY[1,14,15]) u, generate_series(0, 24) g;

INSERT INTO water_level (underpass_id, level_cm, measured_at)
SELECT u, ROUND((GREATEST(0, 6 * EXP(-((g - 18)^2) / 30.0) - 0.5))::numeric, 1), (NOW() AT TIME ZONE 'Asia/Seoul') - make_interval(mins => (24 - g) * 30)
FROM unnest(ARRAY[1,14,15]) u, generate_series(0, 24) g;

-- 3. 위험도 이벤트 이력
INSERT INTO event (underpass_id, type, risk_level, score, water_cm, created_at) VALUES
 (1, 'RISK_LEVEL_UP',   '관심', 42.50, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '5 hours'),
 (1, 'RISK_LEVEL_UP',   '주의', 71.20, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '4 hours 20 minutes'),
 (1, 'RISK_LEVEL_DOWN', '관심', 55.00, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '3 hours 10 minutes'),
 (1, 'RISK_LEVEL_DOWN', '안전', 21.00, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '2 hours 30 minutes'),
 (14,'RISK_LEVEL_UP',   '관심', 44.10, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '6 hours'),
 (14,'RISK_LEVEL_DOWN', '안전', 18.00, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '5 hours'),
 (15,'RISK_LEVEL_UP',   '관심', 41.80, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '7 hours'),
 (15,'RISK_LEVEL_DOWN', '안전', 17.00, 0, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '6 hours');
INSERT INTO event (underpass_id, type, operator_id, is_auto, reason, created_at)
VALUES (1, 'BLOCK', 1, FALSE, '집중호우로 인한 수위 상승', (NOW() AT TIME ZONE 'Asia/Seoul') - interval '4 hours 5 minutes'),
       (1, 'RELEASE', 1, FALSE, '수위 하강 확인', (NOW() AT TIME ZONE 'Asia/Seoul') - interval '3 hours');

-- 4. 예지보전 이벤트 + 조치 기록 (초량 설비)
INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at)
SELECT e.id, '위험진입', '위험', 78.40, 'RESOLVED', (NOW() AT TIME ZONE 'Asia/Seoul') - interval '2 days'
FROM pm_equipment e WHERE e.underpass_id = 1 AND e.type = 'PUMP' ORDER BY e.id LIMIT 1;
INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at)
SELECT e.id, '급상승', '주의', 52.10, 'IN_PROGRESS', (NOW() AT TIME ZONE 'Asia/Seoul') - interval '3 hours'
FROM pm_equipment e WHERE e.underpass_id = 1 AND e.type = 'DISPLAY' ORDER BY e.id LIMIT 1;
INSERT INTO pm_action (event_id, content, manager_id, created_at)
SELECT ev.id, '펌프 베어링 점검 및 윤활유 교체 완료', 1, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '1 day 20 hours'
FROM pm_event ev WHERE ev.status = 'RESOLVED' ORDER BY ev.id LIMIT 1;

-- 5. 관리자 알림 로그
INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at)
SELECT 'RISK', u.region_id, '초량지하차도 위험도 상승', '관심 → 주의 (점수 71.2)', 'UNDERPASS', 1, FALSE, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '4 hours 20 minutes'
FROM underpass u WHERE u.id = 1;
INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at)
SELECT 'EQUIPMENT', u.region_id, '전광판 이상 징후', '급상승 · 점수 52.1 (주의)', 'UNDERPASS', 1, FALSE, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '3 hours'
FROM underpass u WHERE u.id = 1;
INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at)
SELECT 'SYSTEM', NULL, '시연 환경 안내', '이 알림은 시연용 시드 데이터입니다.', NULL, NULL, FALSE, (NOW() AT TIME ZONE 'Asia/Seoul') - interval '1 hour';
