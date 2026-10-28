# API 변경사항 (MyBatis → JPA 전환 + 목록 API 정리)

프론트는 아직 안 고쳤어요. 아래 **바뀐 API**만 프론트에서 맞춰주면 되고, 나머지 API는 요청·응답이 예전과 똑같아요.
(전환 전/후 jar를 같은 DB에 붙여서 약 200개 요청의 응답을 비교 검증함)

## 1. 공통 조건 묶음 (모든 목록 API 공통 규칙)

| 묶음 | 파라미터 | 규칙 |
|---|---|---|
| Page | `page`, `size` | page 1부터, size 기본 20 · 최대 100 (지하차도 목록만 최대 500). 응답은 항상 `PageResponse` `{items, page, size, totalItems, totalPages}` |
| Period | `startDate`, `endDate` | `yyyy-MM-dd`, 둘 다 선택, **endDate 당일 포함**. 시작일 > 종료일이면 400 |
| Scope | `regionId`, `underpassId` | REGIONAL_ADMIN은 `regionId`를 뭘 보내든 본인 담당 지역으로 고정. 담당 지역 밖 지하차도의 `underpassId`를 보내면 **403**, 없는 지하차도면 **404** |
| Vehicle | `carNum` | 공백 무시 포함검색 (`12가`만 보내도 검색됨) |

- `keyword`는 "그 목록의 대표 항목 통합검색"으로 고정 (예: 공지=제목+내용, 지하차도=이름). 차량번호 검색은 `keyword`가 아니라 **`carNum`** 으로 통일.
- 그래프용 시계열(위험도 추이, 대시보드 차트)은 페이지 없이 `List` 그대로이고, 기간은 `days`/`hours`/`limit` 그대로.
- 개수가 고정적으로 적은 목록(지역, 카메라, 임계치, 내 차량)도 `List` 그대로.

## 2. 바뀐 API (프론트 수정 필요)

### 2-1. 지하차도 목록 `GET /api/underpasses`
- **기본 정렬이 바뀜**: 최근 등록순 → **이름 가나다순**
- 새 파라미터
  - `sort`: `NAME`(기본) / `RISK_SCORE`(최신 위험점수 높은 순, 점수 없으면 맨 뒤) / `LATEST`(예전 기본 정렬)
  - `status`: `NORMAL` / `BLOCKED`
- `size` 최대 500 (지도에 한 번에 뿌리기용)
- 응답 모양은 같음

### 2-2. 잔류차량 목록 → 통과 기록 목록으로 합침
- **삭제**: `GET /api/underpasses/{id}/transits?status=` (List 응답)
- **대신**: `GET /api/underpasses/transits?underpassId={id}&status=REMAINING` (PageResponse)
- `GET /api/underpasses/transits`의 `keyword` → **`carNum`** 으로 이름 변경
- 응답 항목 `TransitHistoryItem`에는 `underpassName`이 더 들어 있음 (예전 `TransitResponse`에 없던 필드)

### 2-3. 제어이력 `GET /api/underpasses/{id}/events`
- 응답: `List` → **`PageResponse`** (`data.items` 로 꺼내기)
- `page`, `size` 추가 (기본 20)

### 2-4. 번호판 판별대기 → 번호판 인식 기록 목록
- **삭제**: `GET /api/plate-logs/pending?underpassId=` (List 응답)
- **대신**: `GET /api/plate-logs?reviewStatus=PENDING` (PageResponse, 마감 임박한 것부터)
- 새 파라미터: `reviewStatus`, `recognitionStatus`, `carNum`(인식값·정정값 둘 다 검색), Scope, Period, Page
- 응답 항목 `PlateLogListItem` = 예전 판별대기 항목 필드 전부 + `underpassName`, `correctedNum`, `recognitionStatus`, `carId`, `reviewStatus`, `reviewedBy`, `reviewedAt`

### 2-5. 알림 발송 로그 `GET /api/alert-logs`
- `keyword` → **`carNum`** 으로 이름 변경
  - 참고: 예전엔 `keyword`가 SQL에 아예 안 걸려 있어서 **차량번호 검색이 무시되던 버그**가 있었음 → 이제 제대로 걸러짐
- `regionId` 추가 (Scope). **REGIONAL_ADMIN은 이제 본인 담당 지역에서 나간 알림만 보임** (예전엔 전국 전체)

### 2-6. 내 알림 `GET /api/customers/me/alerts`
- `startDate`, `endDate` 추가 (선택). 나머지 같음

### 2-7. 접수의견 관리자 목록 `GET /api/inquiries`
- `startDate`, `endDate` 추가 (작성일 기준, 선택). 나머지 같음

### 2-8. 관리자 승인대기 목록
- **삭제**: `GET /api/managers/pending`
- **대신**: `GET /api/managers?status=PENDING` (오래된 신청부터 정렬 — 예전 /pending 과 같은 순서)

### 2-9. enum 값 이름 (DB 이름 정리 때 바뀐 것)
| 항목 | 예전 | 지금 |
|---|---|---|
| 카메라 역할 `cameraRole` | `ROI_LPR` | `FINAL_LPR` |
| 알림 종류 `alertType` | `ROI_FINAL` | `FINAL` |

(위험도는 API에선 계속 `SAFE/WARNING/DANGER`. DB 값만 `경고` → `주의`)

## 3. 소소하게 달라진 점 (프론트 수정 불필요)
- 대시보드의 `underpassId`에 **없는 지하차도**를 넣으면: 예전 SUPER_ADMIN은 빈 통계, 지금은 404 (공통 Scope 규칙)
- 차량 등록 응답의 `createdAt`이 예전엔 `null`이었는데 이제 값이 채워짐
- 번호판 수동 판별 응답의 `reviewedAt`이 애플리케이션 서버 시각 → DB 시각 (개발 규칙 "DB 시계 기준"에 맞춤)
- 파라미터 형식 오류(예: `startDate=2026-13-01`)는 `요청 파라미터 형식이 올바르지 않습니다. (startDate)` 로 응답
- **삭제**: 임시 테스트용 API `GET /test/db`, `/test/email/*` (dbtest 패키지. 코드에 "확인 끝나면 삭제"라고 돼 있던 것)

## 4. 그대로인 API
위에 없는 API는 전부 그대로예요 (회원/로그인/비밀번호 재설정, 지역, 지하차도 등록·수정·삭제·차단·해제·카메라 지정,
카메라, 임계치, 차량, 공지/FAQ/접수의견 CRUD, 고객 관리, 관리자 승인·거절·탈퇴·지역변경, 대시보드 12개, 날씨, 내부 API).
