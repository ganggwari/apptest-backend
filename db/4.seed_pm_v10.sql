-- 통합본 실행 순서: 1.puf_db_schema.sql → 2.seed_general.sql → 3.seed_its.sql → 4.seed_pm.sql
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

-- 6. 초량 CCTV230 로컬 싱크홀 스트림 연결 (원본 6번 패치 통합)
-- 초량지하차도 CCTV230의 시연 영상을 싱크홀 서버로 연결. 기존 226~229는 변경하지 않는다.

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM camera c JOIN underpass u ON u.id=c.underpass_id
                   WHERE c.id=230 AND u.name='초량지하차도' AND NOT c.is_deleted AND NOT u.is_deleted) THEN
        RAISE EXCEPTION '활성 초량지하차도 CCTV230이 필요합니다. 다른 카메라의 소속을 자동 변경하지 않습니다.';
    END IF;
END $$;
UPDATE camera SET stream_url='http://localhost:8003/stream/230'
WHERE id=230 AND underpass_id=(SELECT id FROM underpass WHERE name='초량지하차도' AND NOT is_deleted);

COMMIT;
