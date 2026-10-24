# PM 백필 제거 안내

- 자동 시작 `PmSensorBackfillRunner` 제거
- 대량 생성 `PmSensorBackfillServiceImpl` 및 인터페이스/DTO 제거
- `POST /api/internal/pm/sensor-backfill` 제거 (404)
- `puf.pm.backfill.*` 설정 제거
- PM 점수 stub `POST /api/demo/pm` 및 `/api/internal/pm-scores` 유지
- 점수 전송 시 `backfill=false`: 점수 저장, 임계치 판정, PM 이벤트, 관리자 알림/SSE 수행
- `puf.pm.stale.enabled=false` 유지: 과거 센서 데이터가 없어도 미수신 경보가 쏟아지지 않도록 함
- 이미 Supabase에 생성된 과거 센서 데이터는 자동 삭제하지 않음. 필요 시 백업 후 DB에서 별도 정리.
- DemoPmService는 기본적으로 30초마다 점수 이력 1행/설비를 계속 생성하며, 장기 운영 시 누적량 주의.
