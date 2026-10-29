-- v12: 개인화 알림(alert)에 인식 카메라(camera_id) 추가
-- 이미 만들어진 DB(로컬/Render)에 한 번 적용. 재실행해도 안전하다. 기존 알림 행의 camera_id 는 NULL 로 남는다.
-- 새로 DB 를 만드는 경우에는 1.puf_db_schema_v10.sql 에 이미 포함되어 있으므로 이 파일은 필요 없다.
BEGIN;

ALTER TABLE alert ADD COLUMN IF NOT EXISTS camera_id BIGINT; -- 번호판을 인식해 이 알림을 만든 카메라 아이디

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_alert_camera') THEN
        ALTER TABLE alert ADD CONSTRAINT fk_alert_camera FOREIGN KEY (camera_id) REFERENCES camera(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_alert_camera_time ON alert (camera_id, created_at);

COMMIT;
