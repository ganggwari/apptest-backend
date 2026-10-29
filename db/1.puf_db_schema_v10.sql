-- 통합본 실행 순서: 1.puf_db_schema.sql → 2.seed_general.sql → 3.seed_its.sql → 4.seed_pm.sql
-- 개발 DB 초기 구축용. 1번은 public 스키마와 기존 데이터를 삭제한다. 기존 운영 DB에 적용하지 말 것.
-- 네 파일을 순서대로 1회 실행하면 원본 시드의 ID/생성 순서를 유지한다. 시드는 반복 실행용이 아니다.

BEGIN;

-- =====================================================================
-- PUF DB 리셋 + 스키마 생성 (싱크홀 통합) (ENUM / TABLE / CHECK / FK / INDEX) — 시드는 필수 기준행만
-- v3 변경 요약: 최고관리자 폐지(manager_region N:M), 근무조/인수인계, 전화번호 인증, 알림 구조 분리(alert/delivery/device_token),
--              전광판 페이지/프리셋, 통제정보 이관, 예지보전(pm_*), 챗봇(chat_*), 날씨 실데이터 테이블, 컬럼/테이블명 축약
-- v6 변경 요약: ITS CCTV/전광판 원본(its_cctv, its_vms) 및 지하차도-전광판 표출 매핑(vms_underpass) 추가,
--              camera.its_cctv_id / pm_equipment.its_vms_id 추가, display_page 가 설비(equip_id) 대신 전광판(its_vms_id) + 원인 지하차도(underpass_id)를 가리키도록 변경
-- v7 변경 요약: 전광판 자동 표출 — display_page.is_manual 추가(수동/자동 페이지 구분), display_page UNIQUE(its_vms_id,page_no)를 DEFERRABLE 로 변경,
--              display_history 에 its_vms_id/underpass_id 추가 + page_id NULL 허용(페이지가 지워져도 이력 유지, ON DELETE SET NULL)
-- v8 변경 요약: DDL 구조는 v7과 같음(카메라 역할 설명 주석만 수정). 시드 쪽이 바뀜 — 카메라 일괄 5대 생성 폐지, 초량지하차도 id=1
-- v9 변경 요약: 예지보전 개편(4.pm_update_v1 · 5.pm_update_v2 를 이 DDL 에 합침)
--              · 설비 종류에 WATER_LEVEL 추가, pm_equipment 에 name(자동 부여) · installed_at(설치일) · sensor_interval_sec · data_started_at · is_deleted 추가
--              · 세로형 pm_sensor 폐지 → 설비 종류별 센서 테이블 4개(pm_sensor_cctv/_pump/_display/_water_level)
--              · pm_score 에 level/reasons/detail/model_version + (설비, 시각) 중복 금지, pm_event 에 trigger_type(위험진입/급상승/미수신)
--              · manager_device_token(관리자 푸시 토큰) 추가
-- ⚠️ 개발 DB 전용. public 스키마를 통째로 지우므로 되돌릴 수 없음. 운영 DB 실행 금지.
-- 실행 순서: 스키마 리셋 → ENUM → 테이블 → CHECK → FK/인덱스 → 필수 기준행
-- 약어 사전: cam(카메라) num(번호) addr(주소) dist(거리) lat/lng(위경도) ctrl(통제) pm(예지보전) recog(인식)
-- 단위: height/length/width/river_dist/sea_dist/dist = m, rain* = mm, temp = 섭씨, humidity/rain_prob = %
-- =====================================================================

DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- 1. ENUM 타입 생성
CREATE TYPE manager_role AS ENUM ('REGIONAL_ADMIN'); -- 관리자 구분(지역담당자만 존재, 추후 역할 확장 대비)
CREATE TYPE manager_status AS ENUM ('PENDING', 'ACTIVE', 'WITHDRAWN'); -- 관리자 계정 상태(가입 대기/활성화/탈퇴)
CREATE TYPE verification_purpose AS ENUM ('SIGNUP', 'PASSWORD_RESET'); -- 인증코드 발송 이유(가입/비밀번호 재설정)
CREATE TYPE camera_role AS ENUM ('CCTV', 'APPROACH_LPR', 'FINAL_LPR'); -- 카메라 역할(일반 CCTV/접근길목 번호판인식/진입직전 최종확인)
CREATE TYPE recognition_status AS ENUM ('SUCCESS', 'LOW_CONFIDENCE', 'FAIL', 'UNMATCHED'); -- 번호판 인식 상태(성공/신뢰도 낮음/실패/미매칭)
CREATE TYPE review_status AS ENUM ('NONE', 'PENDING', 'CONFIRMED', 'AUTO_PROCESSED'); -- 수동 검토 상태(필요없음/대기중/확인완료/자동처리)
CREATE TYPE underpass_current_status AS ENUM ('NORMAL', 'BLOCKED'); -- 지하차도 현재 상태(정상/통제)
CREATE TYPE underpass_event_type AS ENUM ('RISK_LEVEL_UP', 'RISK_LEVEL_DOWN', 'BLOCK', 'RELEASE'); -- 지하차도 이벤트 종류(위험도상승/위험도하강/통제/해제)
CREATE TYPE risk_level AS ENUM ('안전', '관심', '주의', '경계', '심각'); -- 침수 위험도 5단계(행안부 표준 단계. 경계 이상은 수위 기준, 경계부터 자동 통제)
CREATE TYPE pm_level AS ENUM ('안전', '주의', '위험'); -- 예지보전 설비 단계(침수 위험도 5단계와 별개)
CREATE TYPE alert_type AS ENUM ('PREDICTIVE', 'FINAL'); -- 알림 종류(사전예측/진입직전 최후확인)
CREATE TYPE alert_channel AS ENUM ('PUSH_WEB', 'PUSH_APP', 'SMS'); -- 알림 발송 채널(웹푸시/앱푸시/문자)
CREATE TYPE delivery_status AS ENUM ('PENDING', 'SUCCESS', 'FAIL'); -- 채널별 발송 결과(발송대기/성공/실패. 실제 발송 연동 전에는 PENDING 으로 기록)
CREATE TYPE device_platform AS ENUM ('WEB', 'APP'); -- 푸시 토큰 발급 환경
CREATE TYPE transit_status AS ENUM ('IN_TRANSIT', 'EXITED', 'REMAINING'); -- 지하차도 통과 상태(진입중/정상통과/잔류)
CREATE TYPE transit_direction AS ENUM ('UP', 'DOWN'); -- 통행 방향(상행/하행)
CREATE TYPE transfer_agency AS ENUM ('POLICE', 'FIRE'); -- 통제정보 이관 대상(경찰/소방서)
CREATE TYPE display_category AS ENUM ('SAFE', 'WATCH', 'CAUTION', 'BLOCK', 'RELEASE'); -- 전광판 프리셋 문구 분류(안전/관심/주의/통제/해제. 경계·심각은 통제 문구 사용)
CREATE TYPE pm_equipment_type AS ENUM ('CCTV', 'DISPLAY', 'PUMP', 'WATER_LEVEL', 'ROAD'); -- 예지보전 설비 종류(CCTV/전광판/배수펌프/수위센서)
CREATE TYPE pm_event_status AS ENUM ('CHECK_NEEDED', 'IN_PROGRESS', 'RESOLVED', 'ETC'); -- 예지보전 처리 상태(확인필요/점검중/조치완료/기타)
CREATE TYPE pm_action_kind AS ENUM ('START', 'RECORD', 'RESOLVED', 'ETC'); -- 예지보전 조치 기록 종류(점검 시작/기록 추가/조치 완료/기타 종료)
CREATE TYPE pm_trigger_type AS ENUM ('위험진입', '급상승', '미수신', '싱크홀탐지'); -- 예지보전 이벤트 발생 이유(점수가 위험 임계치 넘음/짧은 시간에 점수 급상승/센서 데이터 끊김, '싱크홀탐지')
CREATE TYPE inquiry_status AS ENUM ('PENDING', 'ANSWERED'); -- 접수의견 답변 여부(대기/답변완료)
CREATE TYPE customer_notification_category AS ENUM ('RISK', 'NOTICE', 'INQUIRY'); -- 고객 알림 분류(위험/공지/문의)
CREATE TYPE manager_notification_category AS ENUM ('RISK', 'EQUIPMENT', 'TASK', 'SYSTEM'); -- 관리자 알림 분류(위험/설비/업무/시스템)
CREATE TYPE notice_category AS ENUM ('INFO', 'MAINTENANCE', 'UPDATE', 'URGENT'); -- 공지 분류(안내/점검/업데이트/긴급)
CREATE TYPE faq_category AS ENUM ('USAGE', 'NOTIFICATION', 'VEHICLE', 'ACCOUNT', 'ETC'); -- FAQ 분류(이용 방법/알림·푸시/차량·번호판/계정/기타)
CREATE TYPE reason_type AS ENUM ('BLOCK', 'RELEASE'); -- 통제 사유 프리셋 구분(통제/해제)

-- 2. 테이블 생성

-- ---- 계정 / 지역 / 근무조 ----

CREATE TABLE region ( -- 지역 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    name VARCHAR(50) NOT NULL, -- 지역 이름
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE -- 삭제 여부(소프트 삭제)
);

CREATE TABLE employee ( -- 직원 테이블(관리자 가입 시 대조용, 회사가 사전 등록)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    name VARCHAR(50) NOT NULL, -- 이름
    birth DATE NOT NULL, -- 생년월일
    phone VARCHAR(20) NOT NULL, -- 전화번호
    email VARCHAR(100) UNIQUE NOT NULL -- 이메일
);

CREATE TABLE shift ( -- 근무조 테이블(3교대)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    name VARCHAR(20) NOT NULL, -- 근무조 이름
    start_time TIME NOT NULL, -- 근무 시작 시각
    end_time TIME NOT NULL -- 근무 종료 시각
);

CREATE TABLE manager ( -- 관리자 계정 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    employee_id BIGINT NOT NULL, -- 직원 아이디
    login_id VARCHAR(50) UNIQUE NOT NULL, -- 로그인 아이디
    password VARCHAR(255) NOT NULL, -- 비밀번호
    role manager_role NOT NULL DEFAULT 'REGIONAL_ADMIN', -- 관리자 구분
    status manager_status NOT NULL DEFAULT 'PENDING', -- 계정 상태(가입 후 일정 시간 지나면 백엔드가 자동으로 ACTIVE 전환)
    shift_id BIGINT, -- 소속 근무조 아이디(교대 근무하지 않으면 NULL)
    created_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 가입 요청 일자
    approved_at TIMESTAMP -- 승인(자동승인) 일자
);

CREATE TABLE manager_region ( -- 관리자 담당 지역 테이블(관리자 1명이 여러 지역 담당 가능)
    manager_id BIGINT NOT NULL, -- 관리자 아이디
    region_id BIGINT NOT NULL, -- 지역 아이디
    PRIMARY KEY (manager_id, region_id)
);

CREATE TABLE shift_handover ( -- 근무조 인수인계 리포트 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    shift_id BIGINT NOT NULL, -- 근무조 아이디
    manager_id BIGINT NOT NULL, -- 작성 관리자 아이디
    shift_date DATE NOT NULL, -- 근무 일자
    summary TEXT, -- 자동 집계 요약(해당 교대 시간대 위험도/예지보전 현황 + 미해결 이월 건, 작성 시점 스냅샷)
    memo TEXT, -- 수기 메모
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 작성 시각
);

CREATE TABLE customer ( -- 고객(일반 회원) 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    login_id VARCHAR(50) NOT NULL, -- 로그인 아이디(탈퇴하지 않은 계정끼리만 중복 금지: uq_customer_login_active)
    password VARCHAR(255) NOT NULL, -- 비밀번호
    name VARCHAR(50) NOT NULL, -- 이름
    birth DATE NOT NULL, -- 생년월일
    email VARCHAR(100), -- 이메일(선택, 참고용 추가정보. 탈퇴하지 않은 계정끼리만 중복 금지)
    phone VARCHAR(20) NOT NULL, -- 전화번호(필수, 인증 대상. 탈퇴하지 않은 계정끼리만 중복 금지)
    zipcode VARCHAR(10), -- 우편번호
    road_addr VARCHAR(255), -- 도로명주소
    detail_addr VARCHAR(255), -- 상세주소
    emergency VARCHAR(20), -- 긴급 연락처
    is_withdrawn BOOLEAN NOT NULL DEFAULT FALSE, -- 탈퇴 여부
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 가입일
);

CREATE TABLE device_token ( -- 고객 푸시 토큰 테이블(고객 1명이 웹/앱 여러 기기 보유 가능)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    customer_id BIGINT NOT NULL, -- 고객 아이디
    token VARCHAR(255) UNIQUE NOT NULL, -- 푸시 토큰
    platform device_platform NOT NULL, -- 발급 환경
    is_active BOOLEAN NOT NULL DEFAULT TRUE, -- 사용 여부(만료/로그아웃/탈퇴 시 FALSE, 행은 보존)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

CREATE TABLE manager_device_token ( -- 관리자 푸시 토큰 테이블(관리자 1명이 웹/앱 여러 기기 보유 가능)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    manager_id BIGINT NOT NULL, -- 관리자 아이디
    token VARCHAR(255) UNIQUE NOT NULL, -- 푸시 토큰
    platform device_platform NOT NULL, -- 발급 환경
    is_active BOOLEAN NOT NULL DEFAULT TRUE, -- 사용 여부(만료/로그아웃 시 FALSE, 행은 보존)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

CREATE TABLE phone_verify ( -- 전화번호 인증코드 발송 내역 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    customer_id BIGINT, -- 고객 아이디(PASSWORD_RESET + 고객일 때만 값 있음)
    manager_id BIGINT, -- 관리자 아이디(PASSWORD_RESET + 관리자일 때만 값 있음)
    phone VARCHAR(20) NOT NULL, -- 인증 대상 전화번호(가입 전에는 계정 아이디가 없으므로 항상 번호를 직접 기록)
    purpose verification_purpose NOT NULL, -- 발송 목적
    code VARCHAR(10) NOT NULL, -- 인증 코드
    expires_at TIMESTAMP NOT NULL, -- 만료 시각
    verified_at TIMESTAMP, -- 인증 완료 시각
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 발송 시각
);

-- ---- 지하차도 / 카메라 / 차량 / 번호판 ----

CREATE TABLE underpass ( -- 지하차도 마스터 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    name VARCHAR(100) NOT NULL, -- 이름
    region_id BIGINT NOT NULL, -- 지역 아이디
    road_addr VARCHAR(255), -- 도로명주소
    lat DECIMAL(10,7) NOT NULL, -- 위도
    lng DECIMAL(10,7) NOT NULL, -- 경도
    height DECIMAL(4,2), -- 높이(깊이 개념의 층고, m, AI 모델 입력값)
    length DECIMAL(8,2), -- 길이(m)
    width DECIMAL(6,2), -- 폭(m)
    lanes INT, -- 차로수
    safety_grade VARCHAR(10), -- 안전등급(FMS 등급 A~E, 미실시/불명은 'UNKNOWN')
    river_dist DECIMAL(10,1), -- 가까운 하천까지 직선거리(m, AI 모델 입력값)
    sea_dist DECIMAL(10,1), -- 바다(해안선)까지 직선거리(m, AI 모델 입력값)
    flood_count INT NOT NULL DEFAULT 0, -- 침수 이력 횟수
    early_block BOOLEAN NOT NULL DEFAULT FALSE, -- 선제 통제 대상 여부(관리자 지정. 침수 이력이 있거나 true면 수위 5cm에서 통제, 아니면 15cm)
    up_entry_cam_id BIGINT, -- 상행 입구 카메라 아이디
    up_exit_cam_id BIGINT, -- 상행 출구 카메라 아이디
    down_entry_cam_id BIGINT, -- 하행 입구 카메라 아이디
    down_exit_cam_id BIGINT, -- 하행 출구 카메라 아이디
    status underpass_current_status NOT NULL DEFAULT 'NORMAL', -- 현재 상태
    block_event_id BIGINT, -- 통제 중이면 그 통제 이벤트 아이디
    risk_level risk_level NOT NULL DEFAULT '안전', -- 현재 위험도 단계(FastAPI 수신 시마다 갱신, 오르내림 둘 다 반영)
    risk_score DECIMAL(5,2) NOT NULL DEFAULT 0, -- 현재 위험도 점수(0~100, 최신값 캐시)
    water_cm DECIMAL(5,1) NOT NULL DEFAULT 0, -- 현재 수위(cm, 수위계 최신값 캐시)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(소프트 삭제)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

CREATE TABLE camera ( -- 지하차도 카메라 등록 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    role camera_role NOT NULL, -- 카메라 역할
    stream_url VARCHAR(255), -- 스트림 주소
    dist INT, -- 진입지점까지 거리(m)
    lat DECIMAL(10,7), -- 위도
    lng DECIMAL(10,7), -- 경도
    its_cctv_id BIGINT UNIQUE, -- ITS CCTV 아이디(실제 ITS 영상과 연결된 카메라만 값 있음, 역할은 지하차도~카메라 거리로 CCTV/FINAL_LPR/APPROACH_LPR 중 하나, 더미는 NULL. 한 ITS CCTV 는 한 지하차도에만 귀속)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(소프트 삭제, plate_log 참조 보존용)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

-- ---- ITS CCTV / 전광판 (부산 ITS 원본, 지도 레이어용) ----

CREATE TABLE its_cctv ( -- ITS CCTV 원본 테이블(지도 CCTV 레이어, 가까운 CCTV 조회용. 지하차도 귀속은 camera.its_cctv_id)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    its_id INT, -- ITS 원본 CCTV 번호(원본에 번호가 없는 CCTV는 NULL)
    name VARCHAR(100) NOT NULL, -- 이름
    lat DECIMAL(10,7) NOT NULL, -- 위도
    lng DECIMAL(10,7) NOT NULL, -- 경도
    stream_url VARCHAR(255) NOT NULL, -- 영상 스트림 주소(HLS)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

CREATE TABLE its_vms ( -- ITS 전광판(VMS) 테이블(지도 전광판 레이어. 실제 ITS 전광판 + 지하차도마다 근접/원거리 전광판을 채우는 더미)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    its_no INT, -- ITS 원본 전광판 번호(더미는 NULL)
    spec VARCHAR(20), -- 규격(예: 2단10열, 원본에 없으면 NULL)
    name VARCHAR(100) NOT NULL, -- 이름
    lat DECIMAL(10,7) NOT NULL, -- 위도
    lng DECIMAL(10,7) NOT NULL, -- 경도
    is_dummy BOOLEAN NOT NULL DEFAULT FALSE, -- 더미 여부(실제 ITS 전광판이 아니라 시연용으로 만든 가짜 전광판. 지도에는 똑같이 표시)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

CREATE TABLE vms_underpass ( -- 지하차도-전광판 표출 매핑 테이블(통제 정보를 어느 전광판에 띄울지, 지하차도 반경 1km 이내 쌍. 한 전광판이 여러 지하차도와 겹칠 수 있음)
    its_vms_id BIGINT NOT NULL, -- 전광판 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    dist INT NOT NULL, -- 지하차도에서 전광판까지 직선거리(m, 300 이하 근접=통제 정보 / 초과 원거리=통제+우회 정보. 기준값은 백엔드 설정)
    PRIMARY KEY (its_vms_id, underpass_id)
);

CREATE TABLE car ( -- 고객 등록 차량 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    customer_id BIGINT NOT NULL, -- 고객 아이디
    car_num VARCHAR(20) NOT NULL, -- 차량 번호(같은 번호판을 여러 고객이 등록 가능: 가족 공유 차량)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(소프트 삭제, 고객 탈퇴 시에도 TRUE. 조회/알림 매칭 시 반드시 제외)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

CREATE TABLE plate_log ( -- 번호판 인식 이벤트 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    camera_id BIGINT NOT NULL, -- 카메라 아이디
    detected_num VARCHAR(20) NOT NULL, -- OCR 원본 인식 결과
    corrected_num VARCHAR(20), -- 관리자가 수동 수정한 값
    confidence DECIMAL(4,3), -- OCR 신뢰도
    image VARCHAR(255), -- 감지 순간 캡처 이미지 경로(FastAPI가 전송, 수동 판별용. 없을 수 있음)
    recog_status recognition_status NOT NULL, -- 인식 상태
    car_id BIGINT, -- 매칭된 등록 차량 중 대표 1건(참고용, 같은 번호 다수 등록 시 알림은 car_num으로 전체 조회)
    review_status review_status NOT NULL DEFAULT 'NONE', -- 수동 검토 상태
    review_due TIMESTAMP, -- 검토 대기 만료 시각(초과 시 자동처리)
    reviewed_by BIGINT, -- 검토한 관리자 아이디
    reviewed_at TIMESTAMP, -- 검토 시각
    detected_at TIMESTAMP NOT NULL -- 인식 시각
);

CREATE TABLE transit ( -- 지하차도 입출구 통과(잔류차량 추적) 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    car_num VARCHAR(20), -- 차량 번호
    direction transit_direction NOT NULL, -- 통행 방향
    entry_log_id BIGINT NOT NULL, -- 입구 인식 로그 아이디
    exit_log_id BIGINT, -- 출구 인식 로그 아이디(통과 전이면 NULL)
    status transit_status NOT NULL DEFAULT 'IN_TRANSIT', -- 통과 상태
    entry_at TIMESTAMP NOT NULL, -- 진입 시각
    exit_at TIMESTAMP -- 통과 시각
);

-- ---- 이벤트 / 알림 / 통제정보 이관 / 전광판 ----

CREATE TABLE event ( -- 지하차도 이벤트 테이블(위험도상승/위험도하강/통제/해제)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    type underpass_event_type NOT NULL, -- 이벤트 종류
    risk_level risk_level, -- 위험도 단계(RISK_LEVEL_UP/DOWN일 때만 값 있음)
    score DECIMAL(5,2), -- 이벤트 발생 시점의 위험도 점수(0~100, RISK_LEVEL_UP/DOWN일 때만)
    water_cm DECIMAL(5,1), -- 이벤트 발생 시점의 수위(cm, 수위 때문에 단계가 바뀌었거나 자동 통제 중인 경우)
    operator_id BIGINT, -- 실행한 관리자 아이디(수동 BLOCK/RELEASE일 때만, 자동 통제이면 NULL)
    is_auto BOOLEAN NOT NULL DEFAULT FALSE, -- 시스템 자동 처리 여부(수위 기준 자동 통제)
    reason VARCHAR(200), -- 사유(BLOCK/RELEASE 시 프리셋 선택 또는 직접 입력)
    reason_note VARCHAR(200), -- 상세 사유(사유에 덧붙여 따로 적은 글, 없으면 NULL)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 발생 시각
);

CREATE TABLE water_level ( -- 수위계 측정값 테이블(지하차도당 센서 1개의 단일값, cm)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    level_cm DECIMAL(5,1) NOT NULL CHECK (level_cm >= 0), -- 측정 수위(cm)
    measured_at TIMESTAMP NOT NULL, -- 측정 시각
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 수신(저장) 시각
);

CREATE TABLE reason_preset ( -- 이벤트 사유 프리셋 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    type reason_type NOT NULL, -- 구분(BLOCK 통제 사유 / RELEASE 해제 사유)
    content VARCHAR(200) NOT NULL -- 사유 문구
);

CREATE TABLE alert ( -- 개인화 알림 테이블(고객 1명당 이벤트 1건 = 1행, 채널별 발송은 alert_delivery)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    event_id BIGINT NOT NULL, -- 계기가 된 지하차도 이벤트 아이디
    customer_id BIGINT NOT NULL, -- 수신 고객 아이디
    car_id BIGINT, -- 매칭된 차량 아이디(삭제 시 NULL)
    car_num VARCHAR(20) NOT NULL, -- 발송 시점 번호판 스냅샷
    type alert_type NOT NULL, -- 알림 종류
    is_read BOOLEAN NOT NULL DEFAULT FALSE, -- 고객 읽음 여부
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 알림 생성 시각
);

CREATE TABLE alert_delivery ( -- 개인화 알림 채널별 발송 시도 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    alert_id BIGINT NOT NULL, -- 알림 아이디
    channel alert_channel NOT NULL, -- 발송 채널
    token_id BIGINT, -- 푸시 토큰 아이디(푸시 채널일 때만 값 있음, 문자는 NULL)
    status delivery_status NOT NULL, -- 발송 결과
    sent_at TIMESTAMP NOT NULL DEFAULT NOW() -- 발송 시각
);

CREATE TABLE notice_alert ( -- 공지 일괄발송 알림 테이블(공지 1건 x 수신 고객 1명 = 1행)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    notice_id BIGINT NOT NULL, -- 공지사항 아이디
    customer_id BIGINT NOT NULL, -- 수신 고객 아이디
    is_read BOOLEAN NOT NULL DEFAULT FALSE, -- 고객 읽음 여부
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 알림 생성 시각
);

CREATE TABLE notice_delivery ( -- 공지 알림 채널별 발송 시도 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    notice_alert_id BIGINT NOT NULL, -- 공지 알림 아이디
    channel alert_channel NOT NULL, -- 발송 채널
    token_id BIGINT, -- 푸시 토큰 아이디(푸시 채널일 때만 값 있음, 문자는 NULL)
    status delivery_status NOT NULL, -- 발송 결과
    sent_at TIMESTAMP NOT NULL DEFAULT NOW() -- 발송 시각
);

CREATE TABLE customer_notification ( -- 고객 알림 센터 테이블(위험/공지/문의를 한 곳에 모아 보여줌, 발송 기록은 alert·notice_alert 가 따로 가짐)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    customer_id BIGINT NOT NULL, -- 수신 고객 아이디
    category customer_notification_category NOT NULL, -- 알림 분류
    title VARCHAR(100) NOT NULL, -- 알림 제목
    body VARCHAR(255), -- 알림 내용
    ref_type VARCHAR(20), -- 눌렀을 때 이동할 대상 종류(ALERT/NOTICE/INQUIRY)
    ref_id BIGINT, -- 이동할 대상 아이디
    is_read BOOLEAN NOT NULL DEFAULT FALSE, -- 고객 읽음 여부
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 알림 생성 시각
);

CREATE TABLE manager_notification ( -- 관리자 알림 센터 테이블(알림 1건 = 1행, 읽음은 manager_notification_read 에 관리자별로 기록)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    category manager_notification_category NOT NULL, -- 알림 분류
    region_id BIGINT, -- 대상 지역 아이디(그 지역 담당 관리자에게만 보임, NULL 이면 모든 관리자)
    title VARCHAR(100) NOT NULL, -- 알림 제목
    body VARCHAR(255), -- 알림 내용
    ref_type VARCHAR(20), -- 눌렀을 때 이동할 대상 종류(UNDERPASS/INQUIRY 등)
    ref_id BIGINT, -- 이동할 대상 아이디
    is_urgent BOOLEAN NOT NULL DEFAULT FALSE, -- 긴급 여부(TRUE 면 화면에 긴급 팝업, 기기 푸시도 높은 우선순위. 예: 설비 위험 진입, 수위 자동 통제)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 알림 생성 시각
);

CREATE TABLE manager_notification_read ( -- 관리자별 알림 읽음 기록 테이블(읽은 (알림, 관리자)만 행이 생김)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    notification_id BIGINT NOT NULL, -- 알림 아이디
    manager_id BIGINT NOT NULL, -- 읽은 관리자 아이디
    read_at TIMESTAMP NOT NULL DEFAULT NOW() -- 읽은 시각
);

CREATE TABLE ctrl_transfer ( -- 통제정보 이관 기록 테이블(경찰: 통제/해제 시 신호조정 협조, 소방: 관리자가 잔류차량 보고 발송 시). 실제 전송 없이 PDF 출력 + 기록만
    id BIGSERIAL PRIMARY KEY, -- 아이디
    event_id BIGINT NOT NULL, -- 계기가 된 통제/해제 이벤트 아이디
    agency transfer_agency NOT NULL, -- 이관 대상 기관
    memo TEXT, -- 비고
    manager_id BIGINT, -- 발송 처리한 관리자 아이디(자동 통제으로 생긴 경찰 이관이면 NULL)
    snapshot JSONB, -- 이관 시점 내용 스냅샷(점수·수위·강우·전광판 문구·차량번호 등. PDF 재출력 시 그대로 사용)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 발송 시각
);

CREATE TABLE ctrl_transfer_car ( -- 통제정보 이관 시점의 잔류차량 번호 스냅샷 테이블(소방 이관 건에만 붙음)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    ctrl_transfer_id BIGINT NOT NULL, -- 통제정보 이관 아이디
    car_num VARCHAR(20) NOT NULL -- 보고 시점 잔류차량 번호
);

CREATE TABLE display_preset ( -- 전광판 프리셋 문구 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    category display_category NOT NULL, -- 문구 분류(위험도 단계/통제/해제)
    content VARCHAR(100) NOT NULL -- 문구
);

CREATE TABLE display_page ( -- 전광판 페이지 테이블(전광판 1대가 여러 페이지를 순환 표시)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    its_vms_id BIGINT NOT NULL, -- 전광판 아이디(its_vms)
    underpass_id BIGINT NOT NULL, -- 이 페이지가 생긴 원인 지하차도 아이디(vms_underpass 로 매핑된 지하차도. 해제 시 그 지하차도 페이지만 삭제)
    page_no INT NOT NULL, -- 페이지 순서
    content VARCHAR(100) NOT NULL, -- 표출 문구(프리셋 선택 또는 직접 입력)
    preset_id BIGINT, -- 사용한 프리셋 아이디(직접 입력이면 NULL)
    updated_by BIGINT, -- 마지막 수정 관리자 아이디(수동 통제/해제 전환 시 해당 관리자, 자동 통제이면 NULL)
    is_manual BOOLEAN NOT NULL DEFAULT FALSE, -- 수동 페이지 여부(관리자가 직접 만든 페이지 TRUE / 자동 표출이 만든 페이지 FALSE. 자동 페이지는 관리자가 수정·삭제 불가)
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 마지막 수정 시각
    UNIQUE (its_vms_id, page_no) DEFERRABLE INITIALLY DEFERRED -- 페이지 번호 재정렬을 한 문장으로 하려고 커밋 시점에 검사
);

CREATE TABLE display_history ( -- 전광판 페이지 수정 이력 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    page_id BIGINT, -- 전광판 페이지 아이디(페이지가 지워지면 NULL)
    its_vms_id BIGINT NOT NULL, -- 전광판 아이디
    underpass_id BIGINT NOT NULL, -- 원인 지하차도 아이디
    event_id BIGINT, -- 계기가 된 통제/해제 이벤트 아이디(수동 수정이면 NULL)
    old_content VARCHAR(100), -- 변경 전 문구(최초 생성이면 NULL)
    new_content VARCHAR(100) NOT NULL, -- 변경 후 문구
    changed_by BIGINT, -- 변경한 관리자 아이디(자동 통제에 의한 전환이면 NULL)
    changed_at TIMESTAMP NOT NULL DEFAULT NOW() -- 변경 시각
);

-- ---- 위험도 / 임계치 ----

CREATE TABLE risk_score ( -- 위험도 예측 결과 테이블(현재 시점 기록, 계속 누적)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    score DECIMAL(5,2) NOT NULL, -- 예측 위험도 점수(0~100, AI 모델 표시점수. 확률 아님)
    rain DECIMAL(6,2), -- 현재(이번 시간) 강수량(mm). 관측 실패 시 NULL 허용
    rain_1h DECIMAL(6,2), -- 직전 1시간 강수량(mm)
    rain_3h DECIMAL(6,2), -- 최근 3시간 누적 강수량(mm)
    rain_6h DECIMAL(6,2), -- 최근 6시간 누적 강수량(mm)
    rain_12h DECIMAL(6,2), -- 최근 12시간 누적 강수량(mm)
    rain_24h DECIMAL(6,2), -- 최근 24시간 누적 강수량(mm)
    predicted_at TIMESTAMP NOT NULL -- 예측 계산 시각
);

CREATE TABLE risk_forecast ( -- 위험도 미래 예측 테이블(현재~+6시간, 같은 시점은 덮어쓰기, 지난 시점 행은 삭제)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    score DECIMAL(5,2) NOT NULL, -- 예측 위험도 점수(0~100)
    rain DECIMAL(6,2), -- 예측 대상 시점 강수량(mm, 예보 기반)
    rain_1h DECIMAL(6,2), -- 직전 1시간 강수량(mm)
    rain_3h DECIMAL(6,2), -- 최근 3시간 누적 강수량(mm)
    rain_6h DECIMAL(6,2), -- 최근 6시간 누적 강수량(mm)
    rain_12h DECIMAL(6,2), -- 최근 12시간 누적 강수량(mm)
    rain_24h DECIMAL(6,2), -- 최근 24시간 누적 강수량(mm)
    target_at TIMESTAMP NOT NULL, -- 예측 대상 시점
    predicted_at TIMESTAMP NOT NULL, -- 예측 계산 시각
    UNIQUE (underpass_id, target_at)
);

CREATE TABLE threshold ( -- 지하차도별 위험도 임계치 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    level risk_level NOT NULL, -- 임계치 단계
    min_score DECIMAL(5,2) NOT NULL, -- 해당 단계로 판정되는 위험도 하한값(0~100)
    updated_by BIGINT NOT NULL, -- 마지막 수정 관리자 아이디
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 마지막 수정 시각
    UNIQUE (underpass_id, level)
);

CREATE TABLE threshold_history ( -- 임계치 변경 이력 테이블(최초 생성분부터 기록)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    threshold_id BIGINT NOT NULL, -- 임계치 아이디
    old_min DECIMAL(5,2), -- 변경 전 값(최초 생성이면 NULL, 0~100)
    new_min DECIMAL(5,2) NOT NULL, -- 변경 후 값(0~100)
    changed_by BIGINT NOT NULL, -- 변경한 관리자 아이디
    changed_at TIMESTAMP NOT NULL DEFAULT NOW() -- 변경 시각
);

-- ---- 날씨 ----

CREATE TABLE weather_area ( -- 날씨 지역 테이블(고객 웹 날씨 화면의 지역 선택 목록)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    name VARCHAR(50) NOT NULL UNIQUE, -- 지역 이름
    lat DECIMAL(10,7) NOT NULL, -- 위도
    lng DECIMAL(10,7) NOT NULL -- 경도
);

CREATE TABLE weather ( -- 날씨 관측 테이블(기상청 API 실측값, 계속 누적. 시연 시 스텁이 같은 테이블에 값만 채움)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    area_id BIGINT NOT NULL, -- 날씨 지역 아이디
    temp DECIMAL(4,1), -- 기온(섭씨)
    humidity INT, -- 습도(%)
    rain DECIMAL(6,2), -- 강수량(mm)
    observed_at TIMESTAMP NOT NULL, -- 관측 시각
    UNIQUE (area_id, observed_at)
);

CREATE TABLE weather_forecast ( -- 날씨 예보 테이블(같은 시점은 덮어쓰기, 지난 시점 행은 삭제)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    area_id BIGINT NOT NULL, -- 날씨 지역 아이디
    temp DECIMAL(4,1), -- 예보 기온(섭씨)
    humidity INT, -- 예보 습도(%)
    rain DECIMAL(6,2), -- 예보 강수량(mm)
    rain_prob INT, -- 강수확률(%)
    sky VARCHAR(20), -- 하늘 상태(맑음/구름많음/흐림/비 등)
    target_at TIMESTAMP NOT NULL, -- 예보 대상 시점
    predicted_at TIMESTAMP NOT NULL, -- 예보 수신 시각
    UNIQUE (area_id, target_at)
);

-- ---- 예지보전(pm) ----

CREATE TABLE pm_equipment ( -- 예지보전 설비 테이블(지하차도별 CCTV/전광판/배수펌프/수위센서)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    underpass_id BIGINT NOT NULL, -- 지하차도 아이디
    type pm_equipment_type NOT NULL, -- 설비 종류
    name VARCHAR(30) NOT NULL UNIQUE, -- 설비 이름(종류 글자-지하차도id-번호. 예: P-12-1. 비워서 넣으면 트리거가 자동 부여: CCTV=C, 전광판=D, 배수펌프=P, 수위센서=W)
    cam_id BIGINT, -- 카메라 아이디(type=CCTV일 때만 값 있음)
    its_vms_id BIGINT, -- ITS 전광판 아이디(type=DISPLAY일 때만 값 있음, 더미 전광판 포함. 삭제되지 않은 설비끼리는 한 전광판을 한 설비에만 연결)
    installed_at DATE, -- 설치일(모르면 NULL)
    sensor_interval_sec INTEGER, -- 센서 측정 주기(초). 비워서 넣으면 종류별 기본값: CCTV 3600 / 전광판 3600 / 수위센서 300 / 배수펌프 10
    data_started_at TIMESTAMP, -- 센서 데이터가 시작된 시각(Spring 이 stub/백필을 시작할 때 기록. 모델의 이력 부족 판단용)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(TRUE 면 FastAPI 조회·화면에서 제외, 행은 보존)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 등록일
);

-- 센서 테이블 4개(설비 종류별, 한 행 = 한 시각의 측정값 전체). 기본키 (설비, 시각)이 중복 방지 + 설비별 기간·최신 시각 조회 인덱스 역할
-- 측정값 컬럼은 NULL 허용(결측을 그대로 저장, 모델이 결측을 센서 이상으로 판단). 컬럼 이름은 FastAPI 가 읽는 이름과 같음
-- FastAPI 는 기간·버킷별 집계(count, sum, sum_sq, min, max)로 조회. stub 은 INSERT ... ON CONFLICT DO NOTHING 으로 넣으면 재실행해도 안전
CREATE TABLE pm_sensor_cctv ( -- CCTV 센서 데이터(1시간 간격)
    equip_id BIGINT NOT NULL, -- 설비 아이디(CCTV)
    recorded_at TIMESTAMP NOT NULL, -- 측정 시각
    video_receive_rate DOUBLE PRECISION, -- 영상 수신률
    response_time_ms DOUBLE PRECISION, -- 응답 시간(ms)
    packet_loss_rate DOUBLE PRECISION, -- 패킷 손실률
    image_blur_score DOUBLE PRECISION, -- 영상 흐림 정도
    PRIMARY KEY (equip_id, recorded_at)
);

CREATE TABLE pm_sensor_pump ( -- 배수펌프 센서 데이터(10초 간격)
    equip_id BIGINT NOT NULL, -- 설비 아이디(PUMP)
    recorded_at TIMESTAMP NOT NULL, -- 측정 시각
    vibration DOUBLE PRECISION, -- 진동
    pressure DOUBLE PRECISION, -- 압력
    temperature DOUBLE PRECISION, -- 온도
    current DOUBLE PRECISION, -- 전류
    flow DOUBLE PRECISION, -- 유량
    rpm DOUBLE PRECISION, -- 회전수
    PRIMARY KEY (equip_id, recorded_at)
);

CREATE TABLE pm_sensor_display ( -- 전광판 센서 데이터(1시간 간격)
    equip_id BIGINT NOT NULL, -- 설비 아이디(DISPLAY)
    recorded_at TIMESTAMP NOT NULL, -- 측정 시각
    temperature_c DOUBLE PRECISION, -- 본체 온도(℃)
    ambient_c DOUBLE PRECISION, -- 주변 온도(℃)
    ripple_v DOUBLE PRECISION, -- 전원 리플 전압(V)
    response_time_ms DOUBLE PRECISION, -- 응답 시간(ms)
    error_count INTEGER, -- 오류 횟수
    bad_pixel_pct DOUBLE PRECISION, -- 불량 화소 비율(%)
    PRIMARY KEY (equip_id, recorded_at)
);

CREATE TABLE pm_sensor_water_level ( -- 수위센서 장비 센서 데이터(5분 간격. 수위계 장비 자체의 측정값이며 지하차도 물 높이 water_level 테이블과 별개)
    equip_id BIGINT NOT NULL, -- 설비 아이디(WATER_LEVEL)
    recorded_at TIMESTAMP NOT NULL, -- 측정 시각
    level_m DOUBLE PRECISION, -- 수위(m)
    PRIMARY KEY (equip_id, recorded_at)
);

CREATE TABLE pm_score ( -- 예지보전 위험도 점수 테이블(FastAPI 계산 결과, 침수 위험도 모델 입력으로도 사용)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    equip_id BIGINT NOT NULL, -- 설비 아이디
    score DECIMAL(5,2) NOT NULL, -- 고장 위험도 점수(0~100)
    predicted_at TIMESTAMP NOT NULL, -- 예측 계산 시각(센서 마지막 시각 기준)
    level pm_level, -- 판정 단계(Spring 이 pm_threshold 로 판정해서 저장)
    reasons JSONB, -- 사유 문장 목록(예: ["영상 수신률 평소보다 매우 많이 낮아요"])
    detail JSONB, -- 모델별 세부값(features[] 특징값 · anomalyStartedAt · extra. 펌프 센서별 현재/평소는 extra.sensors)
    model_version VARCHAR(30), -- 점수를 만든 모델 버전(시연 재생 = 'replay-v1')
    CONSTRAINT uq_pm_score_equip_time UNIQUE (equip_id, predicted_at) -- 같은 설비·같은 시각 점수 중복 금지(예지보전 서버가 다시 켜져 같은 시각을 또 보내면 Spring 이 ON CONFLICT DO NOTHING 으로 건너뜀)
);

CREATE TABLE pm_threshold ( -- 설비별 예지보전 임계치 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    equip_id BIGINT NOT NULL, -- 설비 아이디
    level pm_level NOT NULL, -- 임계치 단계
    min_score DECIMAL(5,2) NOT NULL, -- 해당 단계로 판정되는 점수 하한값(0~100)
    updated_by BIGINT NOT NULL, -- 마지막 수정 관리자 아이디
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 마지막 수정 시각
    UNIQUE (equip_id, level)
);

CREATE TABLE pm_threshold_history ( -- 예지보전 임계치 변경 이력 테이블(최초 생성분부터 기록)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    threshold_id BIGINT NOT NULL, -- 예지보전 임계치 아이디(pm_threshold)
    old_min DECIMAL(5,2), -- 변경 전 값(최초 생성이면 NULL)
    new_min DECIMAL(5,2) NOT NULL, -- 변경 후 값
    changed_by BIGINT NOT NULL, -- 변경한 관리자 아이디
    changed_at TIMESTAMP NOT NULL DEFAULT NOW() -- 변경 시각
);

CREATE TABLE pm_event ( -- 예지보전 이벤트 테이블(점검 목록 역할, 상태로 처리 진행 관리)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    equip_id BIGINT NOT NULL, -- 설비 아이디
    trigger_type pm_trigger_type NOT NULL, -- 발생 이유(위험진입/급상승/미수신)
    level pm_level, -- 넘은 임계치 단계(미수신이면 NULL)
    score DECIMAL(5,2), -- 이벤트 발생 시점 점수(미수신이면 NULL)
    status pm_event_status NOT NULL DEFAULT 'CHECK_NEEDED', -- 처리 상태
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 발생 시각
);

CREATE TABLE pm_action ( -- 예지보전 조치 기록 테이블(조치 로그 및 리포트 출력용)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    event_id BIGINT NOT NULL, -- 예지보전 이벤트 아이디
    content TEXT NOT NULL, -- 조치 내용
    manager_id BIGINT NOT NULL, -- 조치한 관리자 아이디
    kind pm_action_kind NOT NULL DEFAULT 'RECORD', -- 기록 종류(점검 시작/기록 추가/조치 완료/기타 종료)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 조치 기록 시각
);

-- ---- 챗봇(chat) ----

CREATE TABLE chat_session ( -- 챗봇 대화 세션 테이블(비회원 이용 가능)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    customer_id BIGINT, -- 고객 아이디(비회원이면 NULL)
    token VARCHAR(100) UNIQUE NOT NULL, -- 세션 식별 토큰
    started_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 시작 시각
    active_at TIMESTAMP NOT NULL DEFAULT NOW() -- 마지막 활동 시각
);

CREATE TABLE chat_log ( -- 챗봇 대화 로그 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    session_id BIGINT NOT NULL, -- 세션 아이디
    question TEXT NOT NULL, -- 질문
    answer TEXT, -- 답변
    is_answered BOOLEAN NOT NULL DEFAULT TRUE, -- 답변 성공 여부(FALSE면 못 답한 질문)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 질문 시각
);

CREATE TABLE chat_synonym ( -- 챗봇 유의어 테이블(축약어/별칭을 기준 단어로 매칭)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    keyword VARCHAR(50) NOT NULL, -- 기준 단어
    synonym VARCHAR(50) NOT NULL, -- 유의어
    UNIQUE (keyword, synonym)
);

CREATE TABLE chat_suggestion ( -- 챗봇 추천질문 테이블(후순위)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    content VARCHAR(200) NOT NULL, -- 추천 질문
    seq INT NOT NULL DEFAULT 0 -- 표시 순서
);

-- ---- 게시판 ----

CREATE TABLE notice ( -- 공지사항 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    title VARCHAR(200) NOT NULL, -- 제목
    content TEXT NOT NULL, -- 내용
    category notice_category NOT NULL DEFAULT 'INFO', -- 분류(안내/점검/업데이트/긴급)
    is_important BOOLEAN NOT NULL DEFAULT FALSE, -- 중요공지 여부(목록 맨 위 고정 + 알림에 [긴급] 표시)
    send_push BOOLEAN NOT NULL DEFAULT FALSE, -- 등록 시 고객 알림 발송 여부(TRUE면 고객 전체에 notice_alert 일괄발송)
    manager_id BIGINT NOT NULL, -- 작성 관리자 아이디
    view_count INT NOT NULL DEFAULT 0, -- 조회수(중복 방지는 백엔드 쿠키/세션)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(소프트 삭제)
    created_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 작성일
    updated_at TIMESTAMP NOT NULL DEFAULT NOW() -- 수정일
);

CREATE TABLE notice_attachment ( -- 공지사항 첨부파일 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    notice_id BIGINT NOT NULL, -- 공지사항 아이디
    file_url VARCHAR(255) NOT NULL, -- 파일 경로
    file_name VARCHAR(255) NOT NULL, -- 파일명
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 업로드 시각
);

CREATE TABLE faq ( -- 자주묻는질문 테이블(질문/답변 1행 구성)
    id BIGSERIAL PRIMARY KEY, -- 아이디
    category faq_category NOT NULL DEFAULT 'ETC', -- 분류(이용 방법/알림·푸시/차량·번호판/계정/기타)
    question TEXT NOT NULL, -- 질문
    answer TEXT NOT NULL, -- 답변
    sort_order INT NOT NULL DEFAULT 0, -- 표시 순서(작을수록 위)
    manager_id BIGINT NOT NULL, -- 작성 관리자 아이디
    view_count INT NOT NULL DEFAULT 0, -- 조회수(중복 방지는 백엔드 쿠키/세션)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(소프트 삭제)
    created_at TIMESTAMP NOT NULL DEFAULT NOW(), -- 작성일
    updated_at TIMESTAMP NOT NULL DEFAULT NOW() -- 수정일
);

CREATE TABLE faq_attachment ( -- FAQ 첨부파일 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    faq_id BIGINT NOT NULL, -- FAQ 아이디
    file_url VARCHAR(255) NOT NULL, -- 파일 경로
    file_name VARCHAR(255) NOT NULL, -- 파일명
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 업로드 시각
);

CREATE TABLE inquiry ( -- 접수의견(1:1 문의) 테이블, 자기참조로 답글 표현
    id BIGSERIAL PRIMARY KEY, -- 아이디
    parent_id BIGINT, -- 원글 아이디(NULL이면 원글, 값 있으면 그 원글의 답글)
    customer_id BIGINT, -- 작성 고객 아이디(원글만 값 있음)
    manager_id BIGINT, -- 답변 관리자 아이디(답글만 값 있음)
    title VARCHAR(200), -- 제목(원글만 값 있음)
    content TEXT NOT NULL, -- 내용
    status inquiry_status NOT NULL DEFAULT 'PENDING', -- 답변 여부(원글 행 기준)
    view_count INT NOT NULL DEFAULT 0, -- 조회수(중복 방지는 백엔드 쿠키/세션)
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- 삭제 여부(소프트 삭제)
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 작성일
);

CREATE TABLE inquiry_attachment ( -- 접수의견 첨부파일 테이블
    id BIGSERIAL PRIMARY KEY, -- 아이디
    inquiry_id BIGINT NOT NULL, -- 접수의견 아이디
    file_url VARCHAR(255) NOT NULL, -- 파일 경로
    file_name VARCHAR(255) NOT NULL, -- 파일명
    created_at TIMESTAMP NOT NULL DEFAULT NOW() -- 업로드 시각
);

-- 3. 특수 조건 추가 (CHECK 제약 조건)

-- 가입 인증(SIGNUP): 계정이 아직 없으므로 번호만 기록 / 비밀번호 재설정: 고객·관리자 계정 중 정확히 하나
ALTER TABLE phone_verify
ADD CONSTRAINT chk_phone_verify_owner
CHECK (
    (purpose = 'SIGNUP' AND customer_id IS NULL AND manager_id IS NULL) OR
    (purpose = 'PASSWORD_RESET' AND ((customer_id IS NULL) <> (manager_id IS NULL)))
);

ALTER TABLE event
ADD CONSTRAINT chk_event_cond
CHECK (
    (type IN ('RISK_LEVEL_UP', 'RISK_LEVEL_DOWN') AND risk_level IS NOT NULL AND score IS NOT NULL) OR
    (type = 'RELEASE' AND operator_id IS NOT NULL AND is_auto = FALSE) OR
    (type = 'BLOCK' AND ((operator_id IS NOT NULL AND is_auto = FALSE) OR (operator_id IS NULL AND is_auto = TRUE)))
);

-- 푸시 채널은 토큰 필수, 문자는 토큰 없음
ALTER TABLE alert_delivery
ADD CONSTRAINT chk_alert_delivery_token
CHECK ((channel = 'SMS' AND token_id IS NULL) OR (channel IN ('PUSH_WEB', 'PUSH_APP') AND token_id IS NOT NULL));

ALTER TABLE notice_delivery
ADD CONSTRAINT chk_notice_delivery_token
CHECK ((channel = 'SMS' AND token_id IS NULL) OR (channel IN ('PUSH_WEB', 'PUSH_APP') AND token_id IS NOT NULL));

-- 카메라 아이디는 CCTV 설비에만 연결
ALTER TABLE pm_equipment
ADD CONSTRAINT chk_pm_equipment_cam
CHECK (type = 'CCTV' OR cam_id IS NULL);

-- 전광판 아이디는 DISPLAY 설비에만 연결
ALTER TABLE pm_equipment
ADD CONSTRAINT chk_pm_equipment_vms
CHECK (type = 'DISPLAY' OR its_vms_id IS NULL);

-- 센서 주기는 0보다 커야 함
ALTER TABLE pm_equipment
ADD CONSTRAINT chk_pm_equipment_interval
CHECK ((type = 'ROAD' AND sensor_interval_sec IS NULL) OR (type <> 'ROAD' AND sensor_interval_sec > 0 AND sensor_interval_sec IS NOT NULL));

-- 미수신·싱크홀탐지 이벤트는 점수·단계가 없을 수 있음
ALTER TABLE pm_event
ADD CONSTRAINT chk_pm_event_trigger
CHECK (trigger_type IN ('미수신', '싱크홀탐지') OR (score IS NOT NULL AND level IS NOT NULL));

ALTER TABLE inquiry
ADD CONSTRAINT chk_inquiry_author
CHECK ((customer_id IS NULL) <> (manager_id IS NULL));

-- 4. 외래 키 (Foreign Key) 설정

ALTER TABLE manager ADD CONSTRAINT fk_manager_employee FOREIGN KEY (employee_id) REFERENCES employee(id);
ALTER TABLE manager ADD CONSTRAINT fk_manager_shift FOREIGN KEY (shift_id) REFERENCES shift(id);

ALTER TABLE manager_region ADD CONSTRAINT fk_mregion_manager FOREIGN KEY (manager_id) REFERENCES manager(id);
ALTER TABLE manager_region ADD CONSTRAINT fk_mregion_region FOREIGN KEY (region_id) REFERENCES region(id);

ALTER TABLE shift_handover ADD CONSTRAINT fk_handover_shift FOREIGN KEY (shift_id) REFERENCES shift(id);
ALTER TABLE shift_handover ADD CONSTRAINT fk_handover_manager FOREIGN KEY (manager_id) REFERENCES manager(id);

ALTER TABLE device_token ADD CONSTRAINT fk_dtoken_customer FOREIGN KEY (customer_id) REFERENCES customer(id);
ALTER TABLE manager_device_token ADD CONSTRAINT fk_mdtoken_manager FOREIGN KEY (manager_id) REFERENCES manager(id);

ALTER TABLE phone_verify ADD CONSTRAINT fk_pverify_customer FOREIGN KEY (customer_id) REFERENCES customer(id);
ALTER TABLE phone_verify ADD CONSTRAINT fk_pverify_manager FOREIGN KEY (manager_id) REFERENCES manager(id);

ALTER TABLE underpass ADD CONSTRAINT fk_underpass_region FOREIGN KEY (region_id) REFERENCES region(id);
ALTER TABLE underpass ADD CONSTRAINT fk_underpass_up_entry_cam FOREIGN KEY (up_entry_cam_id) REFERENCES camera(id);
ALTER TABLE underpass ADD CONSTRAINT fk_underpass_up_exit_cam FOREIGN KEY (up_exit_cam_id) REFERENCES camera(id);
ALTER TABLE underpass ADD CONSTRAINT fk_underpass_down_entry_cam FOREIGN KEY (down_entry_cam_id) REFERENCES camera(id);
ALTER TABLE underpass ADD CONSTRAINT fk_underpass_down_exit_cam FOREIGN KEY (down_exit_cam_id) REFERENCES camera(id);
ALTER TABLE underpass ADD CONSTRAINT fk_underpass_block_event FOREIGN KEY (block_event_id) REFERENCES event(id);

ALTER TABLE camera ADD CONSTRAINT fk_camera_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE camera ADD CONSTRAINT fk_camera_its_cctv FOREIGN KEY (its_cctv_id) REFERENCES its_cctv(id);

ALTER TABLE vms_underpass ADD CONSTRAINT fk_vmsup_vms FOREIGN KEY (its_vms_id) REFERENCES its_vms(id);
ALTER TABLE vms_underpass ADD CONSTRAINT fk_vmsup_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);

ALTER TABLE water_level ADD CONSTRAINT fk_water_level_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);

ALTER TABLE car ADD CONSTRAINT fk_car_customer FOREIGN KEY (customer_id) REFERENCES customer(id);

ALTER TABLE plate_log ADD CONSTRAINT fk_plate_camera FOREIGN KEY (camera_id) REFERENCES camera(id);
-- (FK 해제) 고객/차량 삭제와 무관하게 로그를 보존하기 위해 car/customer 참조 FK를 두지 않음: plate_log.car_id
ALTER TABLE plate_log ADD CONSTRAINT fk_plate_manager FOREIGN KEY (reviewed_by) REFERENCES manager(id);

ALTER TABLE transit ADD CONSTRAINT fk_transit_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE transit ADD CONSTRAINT fk_transit_entry_log FOREIGN KEY (entry_log_id) REFERENCES plate_log(id);
ALTER TABLE transit ADD CONSTRAINT fk_transit_exit_log FOREIGN KEY (exit_log_id) REFERENCES plate_log(id);

ALTER TABLE event ADD CONSTRAINT fk_event_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE event ADD CONSTRAINT fk_event_operator FOREIGN KEY (operator_id) REFERENCES manager(id);

ALTER TABLE alert ADD CONSTRAINT fk_alert_event FOREIGN KEY (event_id) REFERENCES event(id);
-- (FK 해제) 고객/차량 삭제와 무관하게 로그를 보존하기 위해 car/customer 참조 FK를 두지 않음: alert.customer_id, alert.car_id

ALTER TABLE alert_delivery ADD CONSTRAINT fk_adelivery_alert FOREIGN KEY (alert_id) REFERENCES alert(id);
ALTER TABLE alert_delivery ADD CONSTRAINT fk_adelivery_token FOREIGN KEY (token_id) REFERENCES device_token(id);

ALTER TABLE notice_alert ADD CONSTRAINT fk_nalert_notice FOREIGN KEY (notice_id) REFERENCES notice(id);
ALTER TABLE customer_notification ADD CONSTRAINT fk_cnotif_customer FOREIGN KEY (customer_id) REFERENCES customer(id);
ALTER TABLE manager_notification ADD CONSTRAINT fk_mnotif_region FOREIGN KEY (region_id) REFERENCES region(id);
ALTER TABLE manager_notification_read ADD CONSTRAINT fk_mnotifread_notif FOREIGN KEY (notification_id) REFERENCES manager_notification(id) ON DELETE CASCADE;
ALTER TABLE manager_notification_read ADD CONSTRAINT fk_mnotifread_manager FOREIGN KEY (manager_id) REFERENCES manager(id);
-- (FK 해제) 고객 삭제와 무관하게 로그를 보존하기 위해 customer 참조 FK를 두지 않음: notice_alert.customer_id

ALTER TABLE notice_delivery ADD CONSTRAINT fk_ndelivery_alert FOREIGN KEY (notice_alert_id) REFERENCES notice_alert(id);
ALTER TABLE notice_delivery ADD CONSTRAINT fk_ndelivery_token FOREIGN KEY (token_id) REFERENCES device_token(id);

ALTER TABLE ctrl_transfer ADD CONSTRAINT fk_transfer_event FOREIGN KEY (event_id) REFERENCES event(id);
ALTER TABLE ctrl_transfer ADD CONSTRAINT fk_transfer_manager FOREIGN KEY (manager_id) REFERENCES manager(id);

ALTER TABLE ctrl_transfer_car ADD CONSTRAINT fk_tcar_transfer FOREIGN KEY (ctrl_transfer_id) REFERENCES ctrl_transfer(id);

ALTER TABLE display_page ADD CONSTRAINT fk_dpage_vms FOREIGN KEY (its_vms_id) REFERENCES its_vms(id);
ALTER TABLE display_page ADD CONSTRAINT fk_dpage_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE display_page ADD CONSTRAINT fk_dpage_preset FOREIGN KEY (preset_id) REFERENCES display_preset(id);
ALTER TABLE display_page ADD CONSTRAINT fk_dpage_updated FOREIGN KEY (updated_by) REFERENCES manager(id);

ALTER TABLE display_history ADD CONSTRAINT fk_dhistory_page FOREIGN KEY (page_id) REFERENCES display_page(id) ON DELETE SET NULL;
ALTER TABLE display_history ADD CONSTRAINT fk_dhistory_vms FOREIGN KEY (its_vms_id) REFERENCES its_vms(id);
ALTER TABLE display_history ADD CONSTRAINT fk_dhistory_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE display_history ADD CONSTRAINT fk_dhistory_event FOREIGN KEY (event_id) REFERENCES event(id);
ALTER TABLE display_history ADD CONSTRAINT fk_dhistory_changed FOREIGN KEY (changed_by) REFERENCES manager(id);

ALTER TABLE risk_score ADD CONSTRAINT fk_risk_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE risk_forecast ADD CONSTRAINT fk_rforecast_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);

ALTER TABLE threshold ADD CONSTRAINT fk_threshold_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE threshold ADD CONSTRAINT fk_threshold_updated FOREIGN KEY (updated_by) REFERENCES manager(id);

ALTER TABLE threshold_history ADD CONSTRAINT fk_thistory_threshold FOREIGN KEY (threshold_id) REFERENCES threshold(id);
ALTER TABLE threshold_history ADD CONSTRAINT fk_thistory_changed FOREIGN KEY (changed_by) REFERENCES manager(id);

ALTER TABLE weather ADD CONSTRAINT fk_weather_area FOREIGN KEY (area_id) REFERENCES weather_area(id);
ALTER TABLE weather_forecast ADD CONSTRAINT fk_wforecast_area FOREIGN KEY (area_id) REFERENCES weather_area(id);

ALTER TABLE pm_equipment ADD CONSTRAINT fk_pmequip_underpass FOREIGN KEY (underpass_id) REFERENCES underpass(id);
ALTER TABLE pm_equipment ADD CONSTRAINT fk_pmequip_cam FOREIGN KEY (cam_id) REFERENCES camera(id);
ALTER TABLE pm_equipment ADD CONSTRAINT fk_pmequip_its_vms FOREIGN KEY (its_vms_id) REFERENCES its_vms(id);

ALTER TABLE pm_sensor_cctv ADD CONSTRAINT fk_pmsensor_cctv_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);
ALTER TABLE pm_sensor_pump ADD CONSTRAINT fk_pmsensor_pump_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);
ALTER TABLE pm_sensor_display ADD CONSTRAINT fk_pmsensor_display_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);
ALTER TABLE pm_sensor_water_level ADD CONSTRAINT fk_pmsensor_wlevel_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);
ALTER TABLE pm_score ADD CONSTRAINT fk_pmscore_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);

ALTER TABLE pm_threshold ADD CONSTRAINT fk_pmthreshold_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);
ALTER TABLE pm_threshold ADD CONSTRAINT fk_pmthreshold_updated FOREIGN KEY (updated_by) REFERENCES manager(id);

ALTER TABLE pm_threshold_history ADD CONSTRAINT fk_pmthistory_threshold FOREIGN KEY (threshold_id) REFERENCES pm_threshold(id);
ALTER TABLE pm_threshold_history ADD CONSTRAINT fk_pmthistory_changed FOREIGN KEY (changed_by) REFERENCES manager(id);

ALTER TABLE pm_event ADD CONSTRAINT fk_pmevent_equip FOREIGN KEY (equip_id) REFERENCES pm_equipment(id);

ALTER TABLE pm_action ADD CONSTRAINT fk_pmaction_event FOREIGN KEY (event_id) REFERENCES pm_event(id);
ALTER TABLE pm_action ADD CONSTRAINT fk_pmaction_manager FOREIGN KEY (manager_id) REFERENCES manager(id);

ALTER TABLE chat_session ADD CONSTRAINT fk_csession_customer FOREIGN KEY (customer_id) REFERENCES customer(id);
ALTER TABLE chat_log ADD CONSTRAINT fk_clog_session FOREIGN KEY (session_id) REFERENCES chat_session(id);

ALTER TABLE notice ADD CONSTRAINT fk_notice_manager FOREIGN KEY (manager_id) REFERENCES manager(id);
ALTER TABLE notice_attachment ADD CONSTRAINT fk_nattach_notice FOREIGN KEY (notice_id) REFERENCES notice(id);

ALTER TABLE faq ADD CONSTRAINT fk_faq_manager FOREIGN KEY (manager_id) REFERENCES manager(id);
ALTER TABLE faq_attachment ADD CONSTRAINT fk_fattach_faq FOREIGN KEY (faq_id) REFERENCES faq(id);

ALTER TABLE inquiry ADD CONSTRAINT fk_inquiry_parent FOREIGN KEY (parent_id) REFERENCES inquiry(id);
ALTER TABLE inquiry ADD CONSTRAINT fk_inquiry_customer FOREIGN KEY (customer_id) REFERENCES customer(id);
ALTER TABLE inquiry ADD CONSTRAINT fk_inquiry_manager FOREIGN KEY (manager_id) REFERENCES manager(id);
ALTER TABLE inquiry_attachment ADD CONSTRAINT fk_iattach_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiry(id);

-- 5. 인덱스 / 부분 유니크 인덱스

-- 지역 이름은 삭제되지 않은 것끼리만 중복 금지(삭제 후 같은 이름 재사용 가능)
CREATE UNIQUE INDEX uq_region_name_active ON region (name) WHERE is_deleted = FALSE;

-- 같은 직원이 WITHDRAWN이 아닌 관리자 계정을 동시에 여러 개 가질 수 없도록 방지(탈퇴 후 재가입은 허용)
CREATE UNIQUE INDEX uq_manager_employee_active ON manager (employee_id) WHERE status <> 'WITHDRAWN';

-- 답글은 살아있는 것 기준 1개만 허용(삭제된 답변은 제외하고 새 답변 가능)
CREATE UNIQUE INDEX uq_inquiry_parent_id_active ON inquiry (parent_id) WHERE is_deleted = FALSE;

-- 탈퇴하지 않은 고객끼리만 로그인 아이디/전화번호/이메일 중복 금지(탈퇴 행은 로그 보존용으로 남기고, 같은 번호로 재가입 허용)
CREATE UNIQUE INDEX uq_customer_login_active ON customer (login_id) WHERE is_withdrawn = FALSE;
CREATE UNIQUE INDEX uq_customer_phone_active ON customer (phone) WHERE is_withdrawn = FALSE;
CREATE UNIQUE INDEX uq_customer_email_active ON customer (email) WHERE is_withdrawn = FALSE AND email IS NOT NULL;

-- 한 고객이 같은 번호를 중복 등록하지 못하게 하되, 삭제한 차량은 제외(삭제 후 재등록 허용)
CREATE UNIQUE INDEX uq_car_customer_num_active ON car (customer_id, car_num) WHERE is_deleted = FALSE;

CREATE INDEX idx_car_num ON car (car_num) WHERE is_deleted = FALSE;
CREATE INDEX idx_plate_log_camera_time ON plate_log (camera_id, detected_at);
CREATE INDEX idx_transit_underpass_status ON transit (underpass_id, status);
CREATE INDEX idx_event_underpass_time ON event (underpass_id, created_at);
CREATE INDEX idx_alert_customer_time ON alert (customer_id, created_at);
CREATE INDEX idx_notice_alert_customer ON notice_alert (customer_id, is_read);
CREATE INDEX idx_customer_notification_list ON customer_notification (customer_id, is_read, created_at DESC);
CREATE INDEX idx_manager_notification_list ON manager_notification (region_id, category, created_at DESC);
CREATE INDEX idx_manager_notification_urgent ON manager_notification (created_at DESC) WHERE is_urgent;
CREATE UNIQUE INDEX uq_manager_notification_read ON manager_notification_read (notification_id, manager_id);
CREATE INDEX idx_risk_score_underpass_time ON risk_score (underpass_id, predicted_at);
CREATE INDEX idx_water_level_underpass_time ON water_level (underpass_id, measured_at);
-- 점수 조회는 uq_pm_score_equip_time (설비, 시각) 인덱스를 그대로 사용
CREATE UNIQUE INDEX uq_pm_equipment_its_vms ON pm_equipment (its_vms_id) WHERE is_deleted = FALSE AND its_vms_id IS NOT NULL; -- 삭제되지 않은 설비끼리만 한 전광판을 한 설비에 연결
CREATE INDEX idx_pm_equipment_underpass_type ON pm_equipment (underpass_id, type) WHERE is_deleted = FALSE;
CREATE UNIQUE INDEX uq_pm_event_open_stale ON pm_event (equip_id) WHERE trigger_type = '미수신' AND status IN ('CHECK_NEEDED', 'IN_PROGRESS'); -- 같은 설비의 처리 안 끝난 미수신 이벤트는 1건만
CREATE INDEX idx_mdtoken_manager ON manager_device_token (manager_id) WHERE is_active;
CREATE INDEX idx_pm_event_equip_status ON pm_event (equip_id, status);
CREATE INDEX idx_chat_log_answered ON chat_log (is_answered, created_at);
CREATE INDEX idx_its_cctv_pos ON its_cctv (lat, lng);
CREATE INDEX idx_its_vms_pos ON its_vms (lat, lng);
CREATE INDEX idx_vms_underpass_up ON vms_underpass (underpass_id, dist);
CREATE INDEX idx_display_page_underpass ON display_page (underpass_id);
CREATE INDEX idx_display_history_underpass ON display_history (underpass_id, changed_at DESC);

-- 5-1. 트리거

-- 설비 이름·센서 주기 자동 부여: name 을 비워서 INSERT 하면 종류글자-지하차도id-번호 로 채움(삭제된 설비 번호도 세어서 이름 재사용 방지)
CREATE FUNCTION pm_equipment_before_insert() RETURNS trigger AS $$
DECLARE v_letter CHAR(1); v_seq INTEGER;
BEGIN
    IF NEW.type::text = 'ROAD' THEN NEW.sensor_interval_sec := NULL;
    ELSIF NEW.sensor_interval_sec IS NULL THEN
        NEW.sensor_interval_sec := CASE NEW.type::text
            WHEN 'CCTV' THEN 3600 WHEN 'DISPLAY' THEN 3600 WHEN 'WATER_LEVEL' THEN 300 WHEN 'PUMP' THEN 10 END;
    END IF;
    IF NEW.name IS NULL THEN
        v_letter := CASE NEW.type::text WHEN 'CCTV' THEN 'C' WHEN 'DISPLAY' THEN 'D'
            WHEN 'WATER_LEVEL' THEN 'W' WHEN 'PUMP' THEN 'P' WHEN 'ROAD' THEN 'R' END;
        PERFORM pg_advisory_xact_lock(7001, NEW.underpass_id::int);
        SELECT COALESCE(MAX(split_part(e.name, '-', 3)::int), 0) + 1 INTO v_seq
        FROM pm_equipment e WHERE e.underpass_id = NEW.underpass_id AND e.type = NEW.type;
        NEW.name := v_letter || '-' || NEW.underpass_id || '-' || v_seq;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_pm_equipment_before_insert
    BEFORE INSERT ON pm_equipment
    FOR EACH ROW EXECUTE FUNCTION pm_equipment_before_insert();

-- 6. 필수 기준행 (시드 데이터가 아니라 서비스 로직이 참조하는 고정 행)
-- 지역 삭제 시 소속 underpass/manager_region을 옮겨두는 특수 지역. 서비스 로직에서 삭제 금지 처리 필요.
-- 미배정 지역(id=1)은 2.seed_general.sql 에서 생성한다.


-- 노면 분석 결과. 사진 원본은 Spring의 별도 파일 저장소에 보관한다.
CREATE TABLE IF NOT EXISTS pm_road_result (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL UNIQUE,
    equip_id BIGINT NOT NULL REFERENCES pm_equipment(id),
    camera_id BIGINT REFERENCES camera(id),
    event_id BIGINT REFERENCES pm_event(id),
    analyzed_at TIMESTAMP NOT NULL,
    detected BOOLEAN NOT NULL,
    confidence DECIMAL(6,5) CHECK (confidence BETWEEN 0 AND 1),
    detection_count INTEGER NOT NULL CHECK (detection_count >= 0),
    frame_seconds DOUBLE PRECISION CHECK (frame_seconds >= 0),
    model_version VARCHAR(80) NOT NULL,
    image_name VARCHAR(80),
    review_status VARCHAR(24) NOT NULL DEFAULT 'PENDING'
        CHECK (review_status IN ('PENDING', 'CONFIRMED', 'FALSE_POSITIVE', 'RESOLVED')),
    reviewed_by BIGINT REFERENCES manager(id),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CHECK (NOT detected OR (image_name IS NOT NULL AND confidence IS NOT NULL)),
    CHECK (NOT detected OR event_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_pm_road_result_latest ON pm_road_result(equip_id, analyzed_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_pm_road_result_event ON pm_road_result(event_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_pm_event_open_sinkhole ON pm_event(equip_id)
    WHERE trigger_type = '싱크홀탐지' AND status IN ('CHECK_NEEDED', 'IN_PROGRESS');

-- v11: 예지보전 센서 stub 설비별 정상/이상 모드
-- 기존 v10 DB에 한 번 적용. 재실행 가능. 과거 센서/점수/점검 기록은 보존한다.
CREATE TABLE IF NOT EXISTS pm_stub_mode (
    equip_id BIGINT PRIMARY KEY REFERENCES pm_equipment(id),
    mode VARCHAR(8) NOT NULL CHECK (mode IN ('NORMAL','DANGER')),
    changed_at TIMESTAMP NOT NULL DEFAULT LOCALTIMESTAMP
);

COMMIT;
