# PUF Render 분리 배포 — Spring GitHub 저장소

- 기존 Docker Web Service: https://apptest-backend-tj3a.onrender.com
- 기존 Static Site: https://apptest-front.onrender.com (별도 GitHub 저장소 유지)
- Dockerfile은 Spring Boot JAR만 빌드하고 실행; `front/`를 복사하거나 npm을 실행하지 않습니다.
- **Render 대시보드 Environment**의 기존 DB/FCM/Kakao 등 시크릿은 그대로 보존하세요.
- 반드시 적용/수정:
  - `PUF_CORS_ALLOWED_ORIGINS=https://apptest-front.onrender.com`
  - `PUF_NAVIGATION_WS_ALLOWED_ORIGINS=https://apptest-front.onrender.com`
  - `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`
  - `SERVER_SERVLET_SESSION_COOKIE_SAME_SITE=none`
  - `PUF_FCM_ENABLED=true`
- `application.properties`가 WebSocket origin 및 SameSite 환경변수를 실제로 읽도록 수정됐습니다.
- Render Docker build context는 백엔드 저장소 루트여야 합니다 (`Dockerfile` 위치도 저장소 루트).
- Render Blueprint(`render.yaml`)은 신규 서비스 생성용으로 실행하지 마세요. 이미 연결한 서비스를 대시보드에서 유지하세요.
- 데모 점수 stub, 번호판 stub, 관리자 FCM, 4KB SSE heartbeat, 대량 백필 제거는 유지합니다.
- `SameSite=None` 쿠키라도 일부 브라우저의 서드파티 쿠키 차단 정책은 세션을 막을 수 있습니다. 실제 PWA에서 로그인 후 `/api/customers/me` 또는 `/api/managers/me`가 200인지 검증하세요.
