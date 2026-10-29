-- 앱 시연용: 설비마다 센서 측정값을 "딱 한 줄"만 넣는다 (센서 스텁·백필 없이 DB 용량 증가 없음).
-- 화면의 '센서 현재값'은 센서 테이블의 가장 최근 행을 읽으므로, 이 한 줄이 계속 현재값으로 보인다.
-- '최근 측정' 시각은 이 파일을 실행한 시각(한국시간)이다. 여러 번 실행해도 설비당 한 줄만 유지된다.
-- 실행 순서: 1~5 이후 마지막에 실행. (PUF_PM_SENSOR_STUB_ENABLED=false 를 전제로 한다)
DELETE FROM pm_sensor_cctv;
DELETE FROM pm_sensor_pump;
DELETE FROM pm_sensor_display;
DELETE FROM pm_sensor_water_level;

INSERT INTO pm_sensor_cctv (equip_id, recorded_at, video_receive_rate, response_time_ms, packet_loss_rate, image_blur_score)
SELECT e.id, date_trunc('minute', NOW() AT TIME ZONE 'Asia/Seoul'),
       round((97 + random()*2.5)::numeric, 2), round((110 + random()*30)::numeric, 1),
       round((0.4 + random()*0.5)::numeric, 2), round((10 + random()*5)::numeric, 1)
FROM pm_equipment e WHERE e.type = 'CCTV' AND NOT e.is_deleted;

INSERT INTO pm_sensor_pump (equip_id, recorded_at, vibration, pressure, temperature, current, flow, rpm)
SELECT e.id, date_trunc('minute', NOW() AT TIME ZONE 'Asia/Seoul'),
       round((2.2 + random()*0.5)::numeric, 2), round((3.1 + random()*0.3)::numeric, 2),
       round((44 + random()*4)::numeric, 1), round((12 + random()*1.5)::numeric, 2),
       round((175 + random()*15)::numeric, 1), round((1440 + random()*30)::numeric, 0)
FROM pm_equipment e WHERE e.type = 'PUMP' AND NOT e.is_deleted;

INSERT INTO pm_sensor_display (equip_id, recorded_at, temperature_c, ambient_c, ripple_v, response_time_ms, error_count, bad_pixel_pct)
SELECT e.id, date_trunc('minute', NOW() AT TIME ZONE 'Asia/Seoul'),
       round((42 + random()*4)::numeric, 1), round((25 + random()*3)::numeric, 1),
       round((0.15 + random()*0.06)::numeric, 3), round((100 + random()*40)::numeric, 1),
       (random()*2)::int, round((0.3 + random()*0.2)::numeric, 2)
FROM pm_equipment e WHERE e.type = 'DISPLAY' AND NOT e.is_deleted;

INSERT INTO pm_sensor_water_level (equip_id, recorded_at, level_m)
SELECT e.id, date_trunc('minute', NOW() AT TIME ZONE 'Asia/Seoul'), round((0.01 + random()*0.04)::numeric, 3)
FROM pm_equipment e WHERE e.type = 'WATER_LEVEL' AND NOT e.is_deleted;
