# Render 분리 배포 (메인라인 기반)
- 백엔드: Render Docker Web Service (`apptest-backend-tj3a`), 환경변수는 `RENDER_ENV_BACKEND.txt` 전체 붙여넣기. 이 저장소에는 비밀 파일을 넣지 않음.
- 프런트: Static Site (`apptest-front`), 환경변수는 `RENDER_ENV_FRONT.txt`. Rewrite: `/tunnel-source/*` → `https://seoultunnel.co.kr/usage/*`, `/*` → `/index.html`.
- DB(Supabase SQL Editor 순서): `db/1~4_*_v10.sql` → `db/5.seed_demo_v10.sql`.
- 크로스사이트 쿠키: `SameSite=None; Secure` (환경변수로 설정). CORS 는 프런트 origin 만 허용.
- AI 입력은 스텁: `PUF_OCR_MODE=stub`, `PUF_DEMO_ENABLED=true`. 센서 백필/스텁은 끔.
- FCM: 새 Firebase 프로젝트이므로 기기 푸시 토큰 재등록 필요. 데모 시연은 `/demo.html`.
