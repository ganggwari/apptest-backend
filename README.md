# PUF – 단일 서비스 배포 패키지 (백엔드 + 앱 화면 + DB)

시연용 구성: **백엔드(Spring Boot)가 고객 앱·관리자 앱(PWA)을 같은 주소에서 함께 서빙**합니다.
외부 API 키(카카오·기상청·FCM)는 전부 백엔드 환경변수에만 있고 프런트에는 없습니다. 번호판 인식은 스텁(OCR 영상 화면 없음).

```
back (이 폴더)  Spring Boot + Dockerfile (프런트를 빌드해 /app/public 에 포함)
front/          Vue PWA (/customer-app/*, /admin-app/*, /demo.html)
db/             1~4 = 최신 스키마·시드, 5 = 시연 데이터(잔류차량·번호판 로그·위험점수 이력·예지보전 이벤트)
render.yaml     Render Blueprint (서비스 1개)
```

## 1. DB (Supabase)
Session pooler(포트 5432, 사용자 `postgres.<ref>`)를 쓰고, `db/` 의 SQL을 번호 순서(1→5)로 실행합니다.
- 1 스키마 → 2 일반 시드 → 3 ITS 시드 → 4 예지보전 설비 시드 → 5 시연 데이터
- 센서 이력 백필은 쓰지 않습니다(기본 꺼짐). 점수 이력은 시연 스텁이 30초마다 쌓습니다.

## 2. Render
New > Blueprint(또는 Docker Web Service, 루트 = 이 폴더). 환경변수는 `render.yaml` 참고.
`SECRET_DB_URL=jdbc:postgresql://<pooler>:5432/postgres?stringtype=unspecified&sslmode=require` (`stringtype=unspecified` 필수).
무료 플랜은 15분 유휴 시 잠들고 세션이 사라지니 시연 직전에 한 번 열어 두세요.

## 3. 시연 순서
1. `https://<주소>/demo.html` 에서 계정 전환(관리자 manager01 / 고객 customer01)
2. 예지보전: ⑤ 에서 설비 선택 → 점수 85 → 관리자 앱에 **푸시 + 긴급 팝업**, `/admin-app/screen-11` 점검 대상에 등록
3. 지하차도 위험단계(①), 번호판(②), 호우 시나리오(③), 가상 주행 내비(④)는 기존 그대로

## 보안
`application-secret.properties`, `.env*`, 서비스 계정 JSON 은 이 패키지에 없습니다. 채팅에 붙여 넣었던 키는 시연 후 재발급하세요.
