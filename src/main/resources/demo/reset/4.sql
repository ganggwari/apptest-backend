-- 통합본 실행 순서: 1.puf_db_schema.sql → 2.seed_general.sql → 3.seed_its.sql → 4.seed_pm.sql
-- v11: 과거 이력 더미 추가(최근 3일, 모두 끝난 상황) — 점검이력(pm_event · pm_action), 전광판 변경 이력(display_history), 설비 데일리 리포트 알림.
-- v12-render: 로컬 4.seed_pm_v12 에서 로컬 싱크홀 스트림 연결(6번)만 뺐다. 수동등록 점검 이력은 그대로(Render 백엔드에 수동 점검 등록 기능이 들어옴).
-- v12: 초량 배수펌프 P-1-1 점검이력 4건 추가(기존 1건 포함 총 5건, 모두 4~6일 전 · 데일리 리포트 대상 기간 밖이라 리포트 건수는 그대로).
--      2.seed_general v13 의 초량 새벽 사건(event)과 같은 시간대. 설비·전광판은 이 파일에서 생기므로 이 파일 끝(7번)에 둠.
-- 개발 DB 초기 구축용. 1번은 public 스키마와 기존 데이터를 삭제한다. 기존 운영 DB에 적용하지 말 것.
-- 네 파일을 순서대로 1회 실행하면 원본 시드의 ID/생성 순서를 유지한다. 시드는 반복 실행용이 아니다.

BEGIN;

-- =====================================================================
-- PUF 예지보전 설비 시드 (4.seed_pm.sql)
-- 실행 순서: 1.puf_db_schema → 2.seed_general → 3.seed_its → 4.seed_pm(이 파일)
-- 실행 전제: 3.seed_its 까지 실행 완료 (카메라 · 전광판 · 표출 매핑이 있어야 설비에 연결됨)
--
-- 설비 구성
--   · 초량지하차도(id 1)         : CCTV 1 · 전광판 2(3부두, 영주교차로) · 배수펌프 2 · 수위센서 7
--   · 초량이 속한 구(동구)의 나머지 지하차도 : 종류별(CCTV · 전광판 · 배수펌프 · 수위센서) 1대씩
--   · 다른 구의 지하차도           : 예지보전 설비 없음
-- 설비 이름(name)과 센서 주기는 스키마의 트리거가 자동으로 채움 (예: 초량 수위센서 = W-1-1 ~ W-1-7)
-- 임계치: 모든 설비 안전 0 / 주의 40 / 위험 70 (임계치 변경 기능은 나중에 추가). 이력(pm_threshold_history)은 바꿀 때부터 쌓임
-- 센서 데이터는 넣지 않음: Spring stub 이 쌓음
-- =====================================================================

-- =====================================================================
-- 1. 초량지하차도(id 1)
-- =====================================================================

-- 1-1. CCTV — 초량지하차도에 만들어진 ITS CCTV 카메라마다 1개 (카메라 230번~)
INSERT INTO pm_equipment (underpass_id, type, cam_id)
SELECT c.underpass_id, 'CCTV', c.id
FROM camera c
WHERE c.underpass_id = 1 AND c.id > 229
ORDER BY c.id;

-- 1-2. 전광판 2개 — 3부두(its_vms 57) · 영주교차로. 표출 매핑(vms_underpass)에 걸린 전광판만
INSERT INTO pm_equipment (underpass_id, type, its_vms_id)
SELECT vu.underpass_id, 'DISPLAY', vu.its_vms_id
FROM vms_underpass vu
JOIN its_vms v ON v.id = vu.its_vms_id
WHERE vu.underpass_id = 1 AND (v.id = 57 OR v.name = '영주교차로')
ORDER BY v.id;

-- 1-3. 배수펌프 2개
INSERT INTO pm_equipment (underpass_id, type)
SELECT 1, 'PUMP' FROM generate_series(1, 2);

-- 1-4. 수위센서 7개
INSERT INTO pm_equipment (underpass_id, type)
SELECT 1, 'WATER_LEVEL' FROM generate_series(1, 7);

-- =====================================================================
-- 2. 초량이 속한 구의 나머지 지하차도 — 종류별 1대씩
--    · CCTV: 그 지하차도의 카메라(230번~) 중 id 가 가장 작은 것
--    · 전광판: 표출 매핑에서 가장 가까운 전광판(300m 이내). 다른 설비에 이미 연결된 전광판은 제외. 없으면 연결 없이 등록
-- =====================================================================
DO $$
DECLARE
    u RECORD;
    v_cam BIGINT;
    v_vms BIGINT;
BEGIN
    FOR u IN
        SELECT id FROM underpass
        WHERE region_id = (SELECT region_id FROM underpass WHERE id = 1)
          AND id <> 1 AND is_deleted = FALSE
        ORDER BY id
    LOOP
        SELECT MIN(c.id) INTO v_cam FROM camera c WHERE c.underpass_id = u.id AND c.id > 229;
        INSERT INTO pm_equipment (underpass_id, type, cam_id) VALUES (u.id, 'CCTV', v_cam);

        SELECT vu.its_vms_id INTO v_vms
        FROM vms_underpass vu
        WHERE vu.underpass_id = u.id AND vu.dist <= 300
          AND NOT EXISTS (SELECT 1 FROM pm_equipment e WHERE e.its_vms_id = vu.its_vms_id AND e.is_deleted = FALSE)
        ORDER BY vu.dist, vu.its_vms_id
        LIMIT 1;
        INSERT INTO pm_equipment (underpass_id, type, its_vms_id) VALUES (u.id, 'DISPLAY', v_vms);

        INSERT INTO pm_equipment (underpass_id, type) VALUES (u.id, 'PUMP');
        INSERT INTO pm_equipment (underpass_id, type) VALUES (u.id, 'WATER_LEVEL');
    END LOOP;
END $$;

-- =====================================================================
-- 3. 설치일 — 더미: 설비마다 2~8년 전 (설비 id 로 정해서 다시 실행해도 같은 값)
-- =====================================================================
UPDATE pm_equipment
SET installed_at = CURRENT_DATE - (730 + (id * 211) % 2190)::int;

-- =====================================================================
-- 4. pm_threshold — 모든 설비 안전 0 / 주의 40 / 위험 70
--    updated_by = 그 지하차도 지역을 담당하는 관리자 (초량이 속한 동구 = 관리자 id 1)
-- =====================================================================
INSERT INTO pm_threshold (equip_id, level, min_score, updated_by)
SELECT e.id, s.level::pm_level, s.min_score,
       (SELECT MIN(mr.manager_id) FROM manager_region mr WHERE mr.region_id = u.region_id)
FROM pm_equipment e
JOIN underpass u ON u.id = e.underpass_id
CROSS JOIN (VALUES ('안전', 0), ('주의', 40), ('위험', 70)) AS s(level, min_score)
ORDER BY e.id, s.min_score;

-- =====================================================================
-- 5. 확인용 (ROAD 추가 전 집계) — 지하차도별 설비 종류 수 (기대값: 초량 CCTV 1 · DISPLAY 2 · PUMP 2 · WATER_LEVEL 7, 동구 나머지는 종류별 1, 다른 구는 없음)
-- =====================================================================
SELECT u.id AS 지하차도, u.name, r.name AS 구,
       COUNT(*) FILTER (WHERE e.type = 'CCTV') AS cctv,
       COUNT(*) FILTER (WHERE e.type = 'DISPLAY') AS display,
       COUNT(*) FILTER (WHERE e.type = 'PUMP') AS pump,
       COUNT(*) FILTER (WHERE e.type = 'WATER_LEVEL') AS water_level,
       STRING_AGG(e.name, ', ' ORDER BY e.id) AS 설비이름
FROM pm_equipment e
JOIN underpass u ON u.id = e.underpass_id
JOIN region r ON r.id = u.region_id
GROUP BY u.id, u.name, r.name
ORDER BY u.id;


-- 초량지하차도 노면: 반복 실행 시 중복 등록하지 않는다. 카메라는 사진 전송 시 소속을 검증한다.
INSERT INTO pm_equipment (underpass_id, type)
SELECT u.id, 'ROAD'::pm_equipment_type FROM underpass u
WHERE u.name = '초량지하차도' AND u.is_deleted = FALSE
AND NOT EXISTS (SELECT 1 FROM pm_equipment e WHERE e.underpass_id = u.id AND e.type = 'ROAD' AND e.is_deleted = FALSE);

-- 6. (Render) 로컬 싱크홀 스트림 연결(camera 230 stream_url)은 생략한다 — Render 에는 싱크홀 영상 서버가 없다.

-- =====================================================================
-- 7. 과거 이력 더미 (v11) — 최근 3일, 모두 끝난 상황 (2.seed_general 100번과 같은 날짜 기준)
--   · 점검이력 10건: 조치 완료(RESOLVED) 8 + 기타 종료(ETC) 2. 열린 점검(확인필요·점검중)은 만들지 않는다 (초량 P-1-1 은 5건 — 6일 전~4일 전)
--   · 조치자는 동구 담당 주간조 manager01(07~15시) / 야간조 manager06(23~07시)가 근무하는 시간에만 둔다
--   · 싱크홀 탐지 점검은 만들지 않음: 사진 분석 결과(pm_road_result)가 같이 있어야 화면이 정상으로 열림
--   · pm_score · 센서 값은 넣지 않음: Spring stub 이 쌓음(과거 점검 건을 열면 점수 추이 그래프는 비어 있을 수 있음)
--   · 전광판 변경 이력 4건: 초량 사건의 자동 통제 → 해제 때 초량 전광판 2대(3부두·영주교차로) 문구가 바뀐 기록
--   · 데일리 리포트 알림 3건(EQUIPMENT): 동구 담당 관리자에게 사건 날부터 하루 한 건. 미처리 0건
-- =====================================================================

-- 설비 찾기: 지하차도 이름 + 종류 + 그 지하차도에서 n번째 설비
CREATE FUNCTION pg_temp.eq(p_underpass TEXT, p_type pm_equipment_type, p_n INT)
RETURNS BIGINT AS $$
    SELECT e.id FROM pm_equipment e JOIN underpass u ON u.id = e.underpass_id
    WHERE u.name = p_underpass AND e.type = p_type AND e.is_deleted = FALSE
    ORDER BY e.id LIMIT 1 OFFSET (p_n - 1);
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.seed_pm_event(p_equip BIGINT, p_trigger pm_trigger_type, p_level pm_level, p_score NUMERIC,
                                      p_status pm_event_status, p_at TIMESTAMP)
RETURNS BIGINT AS $$
    INSERT INTO pm_event (equip_id, trigger_type, level, score, status, created_at)
    VALUES (p_equip, p_trigger, p_level, p_score, p_status, p_at)
    RETURNING id;
$$ LANGUAGE sql;

CREATE FUNCTION pg_temp.seed_pm_action(p_event BIGINT, p_kind pm_action_kind, p_content TEXT, p_manager TEXT, p_at TIMESTAMP)
RETURNS VOID AS $$
    INSERT INTO pm_action (event_id, content, manager_id, kind, created_at)
    SELECT p_event, p_content, m.id, p_kind, p_at FROM manager m WHERE m.login_id = p_manager;
$$ LANGUAGE sql;

DO $$
DECLARE
    dn TIMESTAMP := date_trunc('day', LOCALTIMESTAMP);                         -- 오늘 0시
    d0 TIMESTAMP := date_trunc('day', LOCALTIMESTAMP) - INTERVAL '3 days';     -- 초량 사건 당일 0시
    d2 TIMESTAMP := date_trunc('day', LOCALTIMESTAMP) - INTERVAL '2 days';
    d1 TIMESTAMP := date_trunc('day', LOCALTIMESTAMP) - INTERVAL '1 day';
    d4 TIMESTAMP := date_trunc('day', LOCALTIMESTAMP) - INTERVAL '4 days';
    d5 TIMESTAMP := date_trunc('day', LOCALTIMESTAMP) - INTERVAL '5 days';
    d6 TIMESTAMP := date_trunc('day', LOCALTIMESTAMP) - INTERVAL '6 days';
    ev BIGINT;
    u_cho BIGINT; r_cho BIGINT;
    e_blk BIGINT; t_blk TIMESTAMP; e_rel BIGINT; t_rel TIMESTAMP; m06 BIGINT;
    v RECORD;
    v_block TEXT; v_release TEXT := E'초량\n통행 재개 서행 통과';
BEGIN
    SELECT id, region_id INTO u_cho, r_cho FROM underpass WHERE name = '초량지하차도' AND is_deleted = FALSE;
    SELECT id INTO m06 FROM manager WHERE login_id = 'manager06';

    -- ---------------------------------------------------------------- 점검이력 (조치 완료 5 + 기타 종료 1)
    -- 1-1) 초량 배수펌프 P-1-1 이전 점검이력 (v12, 6일 전 ~ 5일 전): 정기 점검 → 진동 상승 → 위험 → 전류 변동. 4일 전 밤(위 1번)에 베어링·흡입구 조치로 이어진다
    --      수동등록 점검은 점수·단계가 없음. 조치자는 근무 시간(주간조 manager01 07~15시 / 야간조 manager06 23~07시)에만 둔다
    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'PUMP', 1), '수동등록', NULL, NULL, 'RESOLVED', d6 + TIME '09:30');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 배수펌프 P-1-1 정기 점검 수동 등록, 현장 육안·시운전 점검', 'manager01', d6 + TIME '09:45');
    PERFORM pg_temp.seed_pm_action(ev, 'RECORD',   '토출 압력·전류 측정값 확인, 베어링 부위 온도 점검', 'manager01', d6 + TIME '10:30');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 정기 점검 결과 이상 없음, 윤활유 보충', 'manager01', d6 + TIME '11:20');

    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'PUMP', 1), '급상승', '주의', 56.8, 'RESOLVED', d6 + TIME '21:10');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 배수펌프 P-1-1 진동 점수 상승 확인, 현장 점검 출동', 'manager06', d6 + TIME '23:20');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 모터 고정 볼트 이완 확인 후 재조임, 진동 평소 범위 복귀', 'manager06', d6 + TIME '23:55');

    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'PUMP', 1), '위험진입', '위험', 81.5, 'RESOLVED', d5 + TIME '13:15');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 배수펌프 P-1-1 진동·온도 동시 상승 경보, 예비 펌프 전환 후 현장 확인', 'manager01', d5 + TIME '13:35');
    PERFORM pg_temp.seed_pm_action(ev, 'RECORD',   '커플링 정렬 불량 의심, 분해 점검 진행', 'manager01', d5 + TIME '14:05');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 커플링 재정렬 후 시운전 정상, 진동·온도 평소 수준 복귀', 'manager01', d5 + TIME '14:50');

    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'PUMP', 1), '급상승', '주의', 54.2, 'ETC', d5 + TIME '22:30');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 배수펌프 P-1-1 전류 점수 상승 확인', 'manager06', d5 + TIME '23:20');
    PERFORM pg_temp.seed_pm_action(ev, 'ETC',      '기타 종료: 직전 정비 후 시운전 중 일시적 전류 변동으로 확인되어 자동 복구, 별도 조치 없음', 'manager06', d5 + TIME '23:50');

    -- 1) 초량 배수펌프 P-1-1 위험 진입 — 사건 전날 밤 → 야간조가 조치
    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'PUMP', 1), '위험진입', '위험', 84.2, 'RESOLVED', d4 + TIME '22:40');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 배수펌프 P-1-1 전류·진동 이상 경보 확인, 현장 점검 출동', 'manager06', d4 + TIME '23:10');
    PERFORM pg_temp.seed_pm_action(ev, 'RECORD',   '흡입구 이물질 제거 후 시운전, 진동·전류가 평소 범위로 돌아오는지 확인 중', 'manager06', d0 + TIME '00:15');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 시운전 결과 정상, 센서 값 평소 수준 복귀', 'manager06', d0 + TIME '00:50');

    -- 2) 초량 수위센서 W-1-3 점수 급상승 — 사건 새벽(강우 시간대)
    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'WATER_LEVEL', 3), '급상승', '주의', 61, 'RESOLVED', d0 + TIME '02:15');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 수위센서 W-1-3 측정값 급변 확인(강우 시간대), 센서 상태 점검', 'manager06', d0 + TIME '02:30');
    PERFORM pg_temp.seed_pm_action(ev, 'RECORD',   '센서 보호관 부유물 제거, 인접 수위센서와 값을 비교해 오차 확인', 'manager06', d0 + TIME '03:10');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 보정 후 인접 센서와 오차 해소', 'manager06', d0 + TIME '04:20');

    -- 3) 초량 CCTV C-1-1 데이터 미수신 — 2일 전 낮
    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'CCTV', 1), '미수신', NULL, NULL, 'RESOLVED', d2 + TIME '13:20');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: CCTV 영상·센서 데이터 미수신, 네트워크·전원 상태 확인', 'manager01', d2 + TIME '13:40');
    PERFORM pg_temp.seed_pm_action(ev, 'RECORD',   '현장 영상 인코더 전원 재투입 후 재부팅', 'manager01', d2 + TIME '14:20');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 데이터 수신 재개 확인', 'manager01', d2 + TIME '14:50');

    -- 4) 초량 전광판 D-1-1 점수 급상승 — 2일 전 오전
    ev := pg_temp.seed_pm_event(pg_temp.eq('초량지하차도', 'DISPLAY', 1), '급상승', '주의', 53, 'RESOLVED', d2 + TIME '09:05');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 전광판 D-1-1 본체 온도·오류 횟수 상승 확인', 'manager01', d2 + TIME '09:20');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 환기팬 청소 후 온도 정상 범위 복귀', 'manager01', d2 + TIME '11:00');

    -- 5) 북항지하차도 배수펌프 위험 진입 — 어제 새벽, 야간조가 시작해 주간조가 마무리
    ev := pg_temp.seed_pm_event(pg_temp.eq('북항지하차도', 'PUMP', 1), '위험진입', '위험', 77, 'RESOLVED', d1 + TIME '05:50');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: 배수펌프 전류 상승 경보, 예비 펌프로 전환 후 현장 확인', 'manager06', d1 + TIME '06:15');
    PERFORM pg_temp.seed_pm_action(ev, 'RECORD',   '펌프 베어링 마모 의심, 정비 요청 접수', 'manager06', d1 + TIME '06:40');
    PERFORM pg_temp.seed_pm_action(ev, 'RESOLVED', '조치 완료: 베어링 교체 및 시운전 정상(주간조 인수 후 마무리)', 'manager01', d1 + TIME '09:30');

    -- 6) 충장지하차도 CCTV 위험 진입 — 어제 낮, 일시 지연으로 확인되어 기타 종료
    ev := pg_temp.seed_pm_event(pg_temp.eq('충장지하차도', 'CCTV', 1), '위험진입', '위험', 74, 'ETC', d1 + TIME '13:10');
    PERFORM pg_temp.seed_pm_action(ev, 'START',    '점검 시작: CCTV 영상 수신률 저하 경보 확인', 'manager01', d1 + TIME '13:30');
    PERFORM pg_temp.seed_pm_action(ev, 'ETC',      '기타 종료: 일시적인 네트워크 지연으로 확인되어 자동 복구, 별도 조치 없음', 'manager01', d1 + TIME '14:20');

    -- ---------------------------------------------------------------- 전광판 변경 이력 (초량 사건: 자동 통제 → 해제)
    SELECT id, created_at INTO e_blk, t_blk FROM event WHERE underpass_id = u_cho AND type = 'BLOCK'   ORDER BY id LIMIT 1;
    SELECT id, created_at INTO e_rel, t_rel FROM event WHERE underpass_id = u_cho AND type = 'RELEASE' ORDER BY id LIMIT 1;
    IF e_blk IS NULL OR e_rel IS NULL THEN
        RAISE EXCEPTION '2.seed_general v13 의 초량 통제/해제 이벤트가 필요합니다. 실행 순서를 확인하세요.';
    END IF;
    FOR v IN
        SELECT vu.its_vms_id, vu.dist
        FROM vms_underpass vu JOIN its_vms s ON s.id = vu.its_vms_id
        WHERE vu.underpass_id = u_cho AND (s.id = 57 OR s.name = '영주교차로')
        ORDER BY s.id
    LOOP
        -- 지하차도에서 300m 이내 전광판은 "통행 금지", 먼 전광판은 "우회하세요" (VmsTextBuilder 와 같은 문구)
        v_block := CASE WHEN v.dist <= 300 THEN E'초량\n통제 중 통행 금지' ELSE E'초량\n통제 중 우회하세요' END;
        INSERT INTO display_history (page_id, its_vms_id, underpass_id, event_id, old_content, new_content, changed_by, changed_at)
        VALUES (NULL, v.its_vms_id, u_cho, e_blk, NULL,    v_block,   NULL, t_blk + INTERVAL '3 seconds'),
               (NULL, v.its_vms_id, u_cho, e_rel, v_block, v_release, m06,  t_rel + INTERVAL '3 seconds');
    END LOOP;

    -- ---------------------------------------------------------------- 설비 데일리 리포트 알림 (동구 담당 관리자, 사건 날~어제 분)
    INSERT INTO manager_notification (category, region_id, title, body, ref_type, ref_id, is_urgent, created_at) VALUES
    ('EQUIPMENT', r_cho, '데일리 리포트',
     to_char(d0, 'YYYY-MM-DD') || ' · 신규 점검 1건 · 점검 기록 5건 · 미처리 0건 · 위험 0 · 주의 1 · 싱크홀 의심 탐지 0건 / 미처리 0건 · 통제 1건 / 해제 1건',
     'PM_REPORT', to_char(d0, 'YYYYMMDD')::bigint, FALSE, d2 + TIME '00:10'),
    ('EQUIPMENT', r_cho, '데일리 리포트',
     to_char(d2, 'YYYY-MM-DD') || ' · 신규 점검 2건 · 점검 기록 5건 · 미처리 0건 · 위험 0 · 주의 1 · 싱크홀 의심 탐지 0건 / 미처리 0건 · 통제 0건 / 해제 0건',
     'PM_REPORT', to_char(d2, 'YYYYMMDD')::bigint, FALSE, d1 + TIME '00:10'),
    ('EQUIPMENT', r_cho, '데일리 리포트',
     to_char(d1, 'YYYY-MM-DD') || ' · 신규 점검 2건 · 점검 기록 5건 · 미처리 0건 · 위험 2 · 주의 0 · 싱크홀 의심 탐지 0건 / 미처리 0건 · 통제 0건 / 해제 0건',
     'PM_REPORT', to_char(d1, 'YYYYMMDD')::bigint, FALSE, dn + TIME '00:10');

    -- 오늘 0시 이전에 온 리포트는 담당 관리자들이 이미 읽은 것으로 (오늘 0시 10분 리포트는 안 읽음으로 남김)
    INSERT INTO manager_notification_read (notification_id, manager_id, read_at)
    SELECT n.id, mr.manager_id, n.created_at + INTERVAL '8 hours'
    FROM manager_notification n
    JOIN manager_region mr ON mr.region_id = n.region_id
    WHERE n.ref_type = 'PM_REPORT' AND n.created_at < dn
    ORDER BY n.id, mr.manager_id;
END $$;

DROP FUNCTION pg_temp.seed_pm_action(BIGINT, pm_action_kind, TEXT, TEXT, TIMESTAMP);
DROP FUNCTION pg_temp.seed_pm_event(BIGINT, pm_trigger_type, pm_level, NUMERIC, pm_event_status, TIMESTAMP);
DROP FUNCTION pg_temp.eq(TEXT, pm_equipment_type, INT);

COMMIT;
