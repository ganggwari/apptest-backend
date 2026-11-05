-- 앱(Render) 시연 시작 상태 시드 (v14)
-- 실행 순서: 1 → 2 → 3 → 4 → 5 → 6.  (6번은 마지막, 시작 상태를 만든 뒤 센서 한 줄씩)
-- 로컬 웹 시연(초량 수동 통제 + 위험 설비 5종 점검 대기)에서 이어지는 "앱 시연 시작" 상태를 만든다.
--
--  · 초량지하차도: 주의 단계(점수 72 · 수위 3.5cm) + 관리자(manager01)가 직접 건 수동 통제 중. 수위가 통제 수위(5cm) 아래라 해제 가능
--  · 다른 지하차도: 단계·점수는 로컬 시연 값(안전/관심/주의). 백엔드 stub(DemoCalmProfile)이 이 값 근처에서 조금씩 움직임
--  · 위험 설비 5종 점검 대기: 초량 CCTV C-1-1 · 전광판 D-1-1 · 수위센서 W-1-3 · 배수펌프 P-1-2 · 노면(싱크홀 의심 1건)
--  · 배수펌프 P-1-1 은 이미 점검 완료
--  · 위험 설비 4종의 점수는 백엔드(DemoPmService)가 시작할 때 같은 값으로 고정해 계속 보냄 → 재시작해도 안 풀림
--  · 시각은 모두 "지금(한국시간)" 기준 상대값이다. 시연 직전에 실행할수록 자연스럽다.
--  · 반복 실행용이 아니다 (1번부터 다시 실행할 것).

-- (Supabase SQL 에디터는 문장마다 따로 실행하는 경우가 있어 임시 테이블 대신 일반 테이블을 쓰고 마지막에 지운다)
DROP TABLE IF EXISTS demo_tmp_base, demo_tmp_now, demo_tmp_hist;

BEGIN;

-- ---------------------------------------------------------------------
-- 0. 지하차도별 시작 값 (로컬 시연 위험도 CSV. 초량만 관심 → 주의로 조정)
--    수위/강우는 단계에 맞춰 같이 정한다: 안전 0.2cm·1.5mm/h, 관심 1.9cm·5mm/h, 주의 3.6cm·13mm/h (초량은 수위 3.5cm)
-- ---------------------------------------------------------------------
CREATE TABLE demo_tmp_base (id BIGINT PRIMARY KEY, score NUMERIC, lvl risk_level, water NUMERIC, rain NUMERIC);
INSERT INTO demo_tmp_base (id, score) VALUES
(1,72.0),(2,0.7),(3,0.9),(4,65.3),(5,65.4),(6,0.9),(7,0.1),(8,0.1),(9,95.6),(10,0.4),
(11,0.7),(12,65.3),(13,0.1),(14,65.1),(15,15.6),(16,0.4),(17,0.4),(18,0.5),(19,0.1),(20,65.3),
(21,65.3),(22,65.3),(23,0.9),(24,0.4),(25,0.4),(26,65.3),(27,0.9),(28,65.3),(29,65.3),(30,65.3),
(31,0.7),(32,0.7),(33,0.9),(34,0.9),(35,0.7),(36,0.9),(37,95.2),(38,95.2),(39,65.2),(40,0.5),
(41,0.8),(42,0.1),(43,0.4),(44,0.2),(45,0.1);
UPDATE demo_tmp_base SET lvl = (CASE WHEN score >= 70 THEN '주의' WHEN score >= 40 THEN '관심' ELSE '안전' END)::risk_level;
UPDATE demo_tmp_base SET water = CASE lvl WHEN '주의' THEN 3.6 WHEN '관심' THEN 1.9 ELSE 0.2 END,
                     rain  = CASE lvl WHEN '주의' THEN 13  WHEN '관심' THEN 5   ELSE 1.5 END;
UPDATE demo_tmp_base SET water = 3.5 WHERE id = 1;

-- 이 파일에 필요한 시각 (한국시간 지금, 초 단위로 자름)
CREATE TABLE demo_tmp_now (k TIMESTAMP);
INSERT INTO demo_tmp_now SELECT date_trunc('second', NOW() AT TIME ZONE 'Asia/Seoul');

-- ---------------------------------------------------------------------
-- 1. 위험도 점수 · 수위 · 예측 (최근 6시간, 10분 간격 37점. 마지막 점이 현재값)
--    6시간치를 넣어 두면 서버가 켜질 때 과거 이력 자동 채우기(RiskHistoryBackfillRunner)가 건너뛴다.
--    관심 이상인 지하차도는 아래 2·3번 이벤트 시각(t1 = 관심 진입, t2 = 주의 진입) 전에는 낮은 값, 그 뒤로 올라간 모양
-- ---------------------------------------------------------------------
CREATE TABLE demo_tmp_hist AS
SELECT b.id AS uid, g AS g,
       -- 단계: 0 안전 · 1 관심 · 2 주의 (그 시점에 어느 단계였는가)
       CASE WHEN b.lvl = '안전' OR g = 0 THEN (CASE b.lvl WHEN '주의' THEN 2 WHEN '관심' THEN 1 ELSE 0 END)
            WHEN g * 10 > CASE WHEN b.id = 1 THEN 41 ELSE 95 + b.id % 20 END THEN 0
            WHEN b.lvl = '주의' AND g * 10 > CASE WHEN b.id = 1 THEN 32 ELSE 70 + b.id % 15 END THEN 1
            ELSE (CASE b.lvl WHEN '주의' THEN 2 ELSE 1 END) END AS stg
FROM demo_tmp_base b CROSS JOIN generate_series(0, 36) g;

INSERT INTO risk_score (underpass_id, score, rain, rain_1h, rain_3h, rain_6h, rain_12h, rain_24h, predicted_at)
SELECT b.id,
       GREATEST(0, ROUND((CASE
            WHEN h.g = 0 THEN b.score
            WHEN b.lvl = '안전' THEN b.score + 1.5 * (1 + SIN(b.id + h.g * 1.3))
            WHEN h.stg = 0 THEN 8 + 1.5 * (1 + SIN(b.id + h.g * 1.3))
            WHEN h.stg = 1 THEN 55 + 2 * SIN(b.id * 1.7 + h.g * 1.3)
            WHEN b.lvl = '관심' THEN b.score + 1.8 * SIN(b.id * 1.7 + h.g * 1.3)
            ELSE b.score + (CASE WHEN b.score < 80 THEN 1.0 ELSE 2.0 END) * SIN(b.id * 1.7 + h.g * 1.3) END)::numeric, 2)),
       r.rn, ROUND(LEAST(50, r.rn * 1.0)::numeric, 1), ROUND(LEAST(50, r.rn * 1.25)::numeric, 1),
       ROUND(LEAST(50, r.rn * 1.3)::numeric, 1), ROUND(LEAST(50, r.rn * 1.35)::numeric, 1), ROUND(LEAST(50, r.rn * 1.4)::numeric, 1),
       n.k - make_interval(mins => h.g * 10)
FROM demo_tmp_base b JOIN demo_tmp_hist h ON h.uid = b.id CROSS JOIN demo_tmp_now n
CROSS JOIN LATERAL (SELECT ROUND((CASE h.stg WHEN 2 THEN 13 WHEN 1 THEN 5 ELSE 1.5 END * (1 + 0.05 * SIN(b.id + h.g)))::numeric, 1) AS rn) r;

INSERT INTO water_level (underpass_id, level_cm, measured_at)
SELECT b.id,
       CASE WHEN h.g = 0 THEN b.water
            ELSE GREATEST(0, ROUND(((CASE h.stg WHEN 2 THEN b.water WHEN 1 THEN 1.9 ELSE 0.2 END) + 0.1 * SIN(b.id * 2.1 + h.g * 1.1))::numeric, 1)) END,
       n.k - make_interval(mins => h.g * 10)
FROM demo_tmp_base b JOIN demo_tmp_hist h ON h.uid = b.id CROSS JOIN demo_tmp_now n;

INSERT INTO risk_forecast (underpass_id, score, rain, target_at, predicted_at)
SELECT b.id, b.score, ROUND((b.rain * (ARRAY[1, 0.9, 0.75, 0.6, 0.45, 0.3])[h])::numeric, 1),
       date_trunc('hour', n.k) + make_interval(hours => h), n.k
FROM demo_tmp_base b CROSS JOIN demo_tmp_now n CROSS JOIN generate_series(1, 6) h
ON CONFLICT (underpass_id, target_at) DO UPDATE SET score = EXCLUDED.score, rain = EXCLUDED.rain, predicted_at = EXCLUDED.predicted_at;

-- 지하차도 현재값 캐시 (위 마지막 점과 같다)
UPDATE underpass u SET risk_level = b.lvl, risk_score = b.score, water_cm = b.water
FROM demo_tmp_base b WHERE b.id = u.id;

-- ---------------------------------------------------------------------
-- 2. 단계 상승 이벤트 (관심 이상인 지하차도, 초량은 아래 3번에서 따로)
-- ---------------------------------------------------------------------
INSERT INTO event (underpass_id, type, risk_level, score, water_cm, created_at)
SELECT b.id, 'RISK_LEVEL_UP', '관심', LEAST(b.score, 52 + (b.id % 5)), 1.9, n.k - make_interval(mins => (95 + b.id % 20)::int)
FROM demo_tmp_base b CROSS JOIN demo_tmp_now n WHERE b.lvl IN ('관심', '주의') AND b.id <> 1;
INSERT INTO event (underpass_id, type, risk_level, score, water_cm, created_at)
SELECT b.id, 'RISK_LEVEL_UP', '주의', b.score, b.water, n.k - make_interval(mins => (70 + b.id % 15)::int)
FROM demo_tmp_base b CROSS JOIN demo_tmp_now n WHERE b.lvl = '주의' AND b.id <> 1;

-- ---------------------------------------------------------------------
-- 3. 초량지하차도: 관심 → 주의 → 관리자 수동 통제 (자동 통제 아님, 해제 가능)
-- ---------------------------------------------------------------------
DO $$
DECLARE
    k TIMESTAMP := (SELECT k FROM demo_tmp_now);
    u_cho BIGINT; r_cho BIGINT; m01 BIGINT;
    e_blk BIGINT;
    v RECORD;
    v_text TEXT;
    v_old TEXT := E'초량\n통행 재개 서행 통과';
    v_pg BIGINT;
    snap JSONB;
BEGIN
    SELECT id, region_id INTO u_cho, r_cho FROM underpass WHERE name = '초량지하차도' AND is_deleted = FALSE;
    SELECT id INTO m01 FROM manager WHERE login_id = 'manager01';

    INSERT INTO event (underpass_id, type, risk_level, score, water_cm, created_at) VALUES
      (u_cho, 'RISK_LEVEL_UP', '관심', 52.0, 1.9, k - INTERVAL '41 minutes'),
      (u_cho, 'RISK_LEVEL_UP', '주의', 71.8, 3.4, k - INTERVAL '32 minutes');
    INSERT INTO event (underpass_id, type, operator_id, is_auto, reason, created_at)
    VALUES (u_cho, 'BLOCK', m01, FALSE, '태풍 접근에 따른 사전 통제', k - INTERVAL '27 minutes')
    RETURNING id INTO e_blk;

    UPDATE underpass SET status = 'BLOCKED', block_event_id = e_blk WHERE id = u_cho;

    -- 통제정보 이관 (경찰 1 + 소방 1, 수동 통제라 manager01 이 발송)
    snap := jsonb_build_object('docType', 'CONTROL_NOTICE', 'auto', FALSE, 'riskScore', 71.8, 'rainfallMm', 13, 'waterCm', 3.4,
                               'displayText', E'초량\n통제 중 통행 금지', 'managerName', '김민수', 'managerRegion', '동구',
                               'shiftLabel', '주간조 07:00~15:00');
    INSERT INTO ctrl_transfer (event_id, agency, memo, manager_id, snapshot, created_at) VALUES
      (e_blk, 'POLICE', NULL, m01, snap, k - INTERVAL '27 minutes' + INTERVAL '5 seconds'),
      (e_blk, 'FIRE', '통제정보 전달 (잔류차량은 별도 수동 이관)', m01, snap, k - INTERVAL '27 minutes' + INTERVAL '5 seconds');

    -- 전광판: 초량에 매핑된 전광판마다 통제 문구 페이지 (300m 이내 "통행 금지", 먼 곳 "우회하세요")
    FOR v IN SELECT vu.its_vms_id, vu.dist, s.name AS vname FROM vms_underpass vu JOIN its_vms s ON s.id = vu.its_vms_id
             WHERE vu.underpass_id = u_cho ORDER BY vu.its_vms_id
    LOOP
        v_text := CASE WHEN v.dist <= 300 THEN E'초량\n통제 중 통행 금지' ELSE E'초량\n통제 중 우회하세요' END;
        INSERT INTO display_page (its_vms_id, underpass_id, page_no, content, preset_id, updated_by, is_manual, updated_at)
        VALUES (v.its_vms_id, u_cho, COALESCE((SELECT MAX(page_no) FROM display_page WHERE its_vms_id = v.its_vms_id), 0) + 1,
                v_text, NULL, m01, FALSE, k - INTERVAL '27 minutes' + INTERVAL '3 seconds')
        RETURNING id INTO v_pg;
        INSERT INTO display_history (page_id, its_vms_id, underpass_id, event_id, old_content, new_content, changed_by, changed_at)
        VALUES (v_pg, v.its_vms_id, u_cho, e_blk,
                CASE WHEN v.its_vms_id = 57 OR v.vname = '영주교차로' THEN v_old END,
                v_text, m01, k - INTERVAL '27 minutes' + INTERVAL '3 seconds');
    END LOOP;

    -- 관리자 알림 (동구 담당)
    INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at) VALUES
      ('RISK', r_cho, '위험 단계 상승', '초량지하차도 · 관심 → 주의 · 수위 3.4cm', 'UNDERPASS', u_cho, FALSE, k - INTERVAL '32 minutes'),
      ('RISK', r_cho, '지하차도 통제 시작', '초량지하차도 · 사유: 태풍 접근에 따른 사전 통제', 'UNDERPASS', u_cho, FALSE, k - INTERVAL '27 minutes');
END $$;

-- ---------------------------------------------------------------------
-- 4. 예지보전: 설비 점수 · 점검 이벤트
-- ---------------------------------------------------------------------
CREATE FUNCTION pg_temp.eq(p_type pm_equipment_type, p_name TEXT) RETURNS BIGINT AS $$
    SELECT e.id FROM pm_equipment e WHERE e.underpass_id = 1 AND e.type = p_type AND e.name = p_name AND e.is_deleted = FALSE
$$ LANGUAGE sql;

-- 4-1. 모든 설비에 "평소" 점수 한 줄 (3~16, 설비 id 로 정함)
INSERT INTO pm_score (equip_id, score, predicted_at, level, reasons, model_version)
SELECT e.id, 3 + (e.id * 7) % 14, n.k, '안전', '[]'::jsonb, 'demo-stub'
FROM pm_equipment e CROSS JOIN demo_tmp_now n WHERE e.is_deleted = FALSE AND e.type <> 'ROAD';

DO $$
DECLARE
    k TIMESTAMP := (SELECT k FROM demo_tmp_now);
    m01 BIGINT; r_cho BIGINT;
    e_c BIGINT; e_w BIGINT; e_d BIGINT; e_p2 BIGINT; e_p1 BIGINT; e_r BIGINT;
    ev_c BIGINT; ev_w BIGINT; ev_d BIGINT; ev_p2 BIGINT; ev_p1 BIGINT; ev_r BIGINT;
BEGIN
    SELECT id INTO m01 FROM manager WHERE login_id = 'manager01';
    SELECT region_id INTO r_cho FROM underpass WHERE id = 1;
    e_c  := pg_temp.eq('CCTV', 'C-1-1');
    e_d  := pg_temp.eq('DISPLAY', 'D-1-1');
    e_w  := pg_temp.eq('WATER_LEVEL', 'W-1-3');
    e_p2 := pg_temp.eq('PUMP', 'P-1-2');
    e_p1 := pg_temp.eq('PUMP', 'P-1-1');
    SELECT id INTO e_r FROM pm_equipment WHERE underpass_id = 1 AND type = 'ROAD' AND is_deleted = FALSE ORDER BY id LIMIT 1;
    IF e_c IS NULL OR e_d IS NULL OR e_w IS NULL OR e_p2 IS NULL OR e_p1 IS NULL OR e_r IS NULL THEN
        RAISE EXCEPTION '초량 설비(C-1-1, D-1-1, W-1-3, P-1-1, P-1-2, 노면)를 찾을 수 없습니다. 4번 시드를 확인하세요.';
    END IF;

    -- 4-2. 위험 설비 4종: 발생 시점 점수 + 현재 점수 (현재 점수는 백엔드가 고정해 보내는 값과 같게)
    --      CCTV 84 · 수위센서 78.5 · 전광판 88 · 배수펌프 P-1-2 91.5
    INSERT INTO pm_score (equip_id, score, predicted_at, level, reasons, model_version) VALUES
      (e_c,  84.0, k - INTERVAL '34 minutes', '위험', '["영상 수신률이 평소보다 매우 낮아요"]', 'demo-stub'),
      (e_w,  78.5, k - INTERVAL '29 minutes', '위험', '["수위 측정값이 평소와 매우 달라요"]', 'demo-stub'),
      (e_d,  88.0, k - INTERVAL '26 minutes', '위험', '["본체 온도·오류 횟수가 평소보다 매우 높아요"]', 'demo-stub'),
      (e_p2, 91.5, k - INTERVAL '22 minutes', '위험', '["진동·전류가 평소보다 매우 높아요"]', 'demo-stub');
    DELETE FROM pm_score WHERE equip_id IN (e_c, e_w, e_d, e_p2) AND predicted_at = k;
    INSERT INTO pm_score (equip_id, score, predicted_at, level, reasons, model_version) VALUES
      (e_c,  84.0, k, '위험', '["영상 수신률이 평소보다 매우 낮아요"]', 'demo-stub'),
      (e_w,  78.5, k, '위험', '["수위 측정값이 평소와 매우 달라요"]', 'demo-stub'),
      (e_d,  88.0, k, '위험', '["본체 온도·오류 횟수가 평소보다 매우 높아요"]', 'demo-stub'),
      (e_p2, 91.5, k, '위험', '["진동·전류가 평소보다 매우 높아요"]', 'demo-stub');

    -- 4-3. 점검 대기(확인필요) 이벤트 4건 + 알림 (설비 위험 진입, 긴급)
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at) VALUES (e_c,  '위험진입', '위험', 84.0, 'CHECK_NEEDED', k - INTERVAL '34 minutes') RETURNING id INTO ev_c;
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at) VALUES (e_w,  '위험진입', '위험', 78.5, 'CHECK_NEEDED', k - INTERVAL '29 minutes') RETURNING id INTO ev_w;
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at) VALUES (e_d,  '위험진입', '위험', 88.0, 'CHECK_NEEDED', k - INTERVAL '26 minutes') RETURNING id INTO ev_d;
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at) VALUES (e_p2, '위험진입', '위험', 91.5, 'CHECK_NEEDED', k - INTERVAL '22 minutes') RETURNING id INTO ev_p2;
    INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at) VALUES
      ('EQUIPMENT', r_cho, '설비 위험 진입', '초량지하차도 · CCTV C-1-1 · 위험 단계 (84점) · 영상 수신률이 평소보다 매우 낮아요', 'PM_EVENT', ev_c, TRUE, k - INTERVAL '34 minutes'),
      ('EQUIPMENT', r_cho, '설비 위험 진입', '초량지하차도 · 수위센서 W-1-3 · 위험 단계 (78.5점) · 수위 측정값이 평소와 매우 달라요', 'PM_EVENT', ev_w, TRUE, k - INTERVAL '29 minutes'),
      ('EQUIPMENT', r_cho, '설비 위험 진입', '초량지하차도 · 전광판 D-1-1 · 위험 단계 (88점) · 본체 온도·오류 횟수가 평소보다 매우 높아요', 'PM_EVENT', ev_d, TRUE, k - INTERVAL '26 minutes'),
      ('EQUIPMENT', r_cho, '설비 위험 진입', '초량지하차도 · 배수펌프 P-1-2 · 위험 단계 (91.5점) · 진동·전류가 평소보다 매우 높아요', 'PM_EVENT', ev_p2, TRUE, k - INTERVAL '22 minutes');

    -- 4-4. 노면 싱크홀 의심 1건 (점검 대기) + 분석 결과 (사진: 백엔드가 시작할 때 sinkhole-demo.jpg 를 사진 폴더로 복사)
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at) VALUES (e_r, '싱크홀탐지', NULL, NULL, 'CHECK_NEEDED', k - INTERVAL '12 minutes') RETURNING id INTO ev_r;
    INSERT INTO pm_road_result (job_id, equip_id, camera_id, event_id, analyzed_at, detected, confidence, detection_count, frame_seconds,
                                model_version, image_name, review_status)
    VALUES ('demo-sinkhole-0001', e_r, 230, ev_r, k - INTERVAL '12 minutes', TRUE, 0.83000, 1, 4.2, 'sinkhole-yolo-demo', 'sinkhole-demo.jpg', 'PENDING');
    INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at)
    VALUES ('EQUIPMENT', r_cho, '노면 싱크홀 의심', '초량지하차도 노면에서 싱크홀 의심이 탐지되었습니다. 사진을 확인해 주세요.', 'PM_EVENT', ev_r, TRUE, k - INTERVAL '12 minutes');

    -- 4-5. 배수펌프 P-1-1: 오늘 위험 진입 → 조치 완료 (점검 완료 1건)
    INSERT INTO pm_score (equip_id, score, predicted_at, level, reasons, model_version)
    VALUES (e_p1, 83.0, k - INTERVAL '3 hours 50 minutes', '위험', '["진동·전류가 평소보다 매우 높아요"]', 'demo-stub');
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at)
    VALUES (e_p1, '위험진입', '위험', 83.0, 'RESOLVED', k - INTERVAL '3 hours 50 minutes') RETURNING id INTO ev_p1;
    INSERT INTO pm_action (event_id, content, manager_id, kind, created_at) VALUES
      (ev_p1, '점검 시작: 배수펌프 P-1-1 진동·전류 이상 경보 확인, 현장 점검 출동', m01, 'START', k - INTERVAL '3 hours 25 minutes'),
      (ev_p1, '흡입구 이물질 제거 후 시운전, 진동·전류가 평소 범위로 돌아오는지 확인', m01, 'RECORD', k - INTERVAL '3 hours'),
      (ev_p1, '조치 완료: 시운전 결과 정상, 센서 값 평소 수준 복귀', m01, 'RESOLVED', k - INTERVAL '2 hours 35 minutes');
    INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at)
    VALUES ('EQUIPMENT', r_cho, '설비 위험 진입', '초량지하차도 · 배수펌프 P-1-1 · 위험 단계 (83점) · 진동·전류가 평소보다 매우 높아요', 'PM_EVENT', ev_p1, TRUE, k - INTERVAL '3 hours 50 minutes');
    -- 3시간 전 알림은 담당 관리자들이 이미 읽은 것으로
    INSERT INTO manager_notification_read (notification_id, manager_id, read_at)
    SELECT n.id, mr.manager_id, n.created_at + INTERVAL '10 minutes'
    FROM manager_notification n JOIN manager_region mr ON mr.region_id = n.region_id
    WHERE n.ref_type = 'PM_EVENT' AND n.ref_id = ev_p1
    ORDER BY n.id, mr.manager_id;
END $$;

DROP FUNCTION pg_temp.eq(pm_equipment_type, TEXT);

-- ---------------------------------------------------------------------
-- 5. 확인용 (실행하면 결과가 보인다)
-- ---------------------------------------------------------------------
SELECT id, name, status, risk_level, risk_score, water_cm, block_event_id FROM underpass WHERE id = 1;
SELECT e.name, ev.trigger_type, ev.status
FROM pm_event ev JOIN pm_equipment e ON e.id = ev.equip_id WHERE ev.status IN ('CHECK_NEEDED', 'IN_PROGRESS') ORDER BY ev.id;

COMMIT;

DROP TABLE IF EXISTS demo_tmp_base, demo_tmp_now, demo_tmp_hist;
