-- v13: 푸시 알림을 직접 끈 상태를 계정에 저장 (고객·관리자)
-- 이미 만들어진 DB(로컬/Render)에 한 번 적용. 재실행해도 안전하다.
-- 새로 DB 를 만드는 경우에는 1.puf_db_schema_v10.sql 에 이미 포함되어 있으므로 이 파일은 필요 없다.
BEGIN;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS push_opt_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE manager ADD COLUMN IF NOT EXISTS push_opt_out BOOLEAN NOT NULL DEFAULT FALSE;
COMMIT;
