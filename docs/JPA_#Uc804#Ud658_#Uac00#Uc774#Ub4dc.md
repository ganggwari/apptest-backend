# JPA 전환 가이드 (팀원용)

MyBatis(XML 매퍼)를 전부 걷어내고 Spring Data JPA로 바꿨어요. 로직은 최대한 그대로 두고 "DB 접근 방식"만 바꿨습니다.

## 1. 패키지 구조 (도메인마다 같은 모양)

```
com.mbc.puf.<도메인>
 ├─ controller   (그대로)
 ├─ service      (Service 인터페이스 + Impl, 그대로)
 ├─ dto          (Request / Response 만 남김. DB용 DTO는 entity 로 대체)
 ├─ entity       (새로 생김) 테이블 1개 = 클래스 1개
 └─ repository   (새로 생김) dao 대신. Spring Data JPA 인터페이스
```
- `dao/` 패키지와 `resources/sqls/` 폴더는 없어졌어요.
- 엔티티끼리는 연관관계(@ManyToOne) 없이 **id(Long)로만 연결**했어요. 예전 MyBatis 코드가 id로 다루던 방식 그대로라 로직을 안 바꿔도 되고, 지연 로딩 문제도 안 생겨요.

## 2. 상황별로 쓰는 방법

| 상황 | 쓰는 것 | 예시 |
|---|---|---|
| 단건 조회·저장·수정·삭제 | Repository 메서드 이름 규칙 | `regionRepository.findByIdAndDeletedFalse(id)` |
| 수정 | 엔티티를 조회해서 setter 로 값만 바꾸면 끝 (트랜잭션 끝날 때 UPDATE 자동) | `customer.setPhone(...)` |
| 조건이 붙었다 빠졌다 하는 목록 | `QueryConditions` + `PagedQuery` (JPQL) | 아래 3번 |
| 통계·집계 (LATERAL, FILTER, LEAD 등) | `NativeSql` (네이티브 SQL + DTO 자동 채움) | `DashboardServiceImpl` |
| 여러 행 한 번에 UPDATE | `@Modifying @Query` | `TransitRepository.markRemaining` |

### 수정은 setter 만 하면 된다
```java
@Transactional
public void withdraw(Long customerId) {
    Customer customer = customerRepository.findById(customerId).orElseThrow(...);
    customer.setWithdrawn(true);   // 이걸로 끝. save() 안 불러도 트랜잭션 끝날 때 UPDATE 된다
}
```
⚠️ 그래서 **값을 바꾸는 서비스 메서드에는 꼭 `@Transactional`** 이 있어야 해요 (MyBatis 땐 없어도 UPDATE가 바로 나갔음).

## 3. 목록 API 만드는 법 (공통 조건 묶음 + 동적 조건)

```java
// 컨트롤러: 공통 조건 묶음은 @ModelAttribute 로 받는다
@GetMapping
public ApiResponse<PageResponse<AlertLogListItem>> list(@AuthenticationPrincipal LoginUser loginUser,
        @ModelAttribute ScopeQuery scope, @ModelAttribute VehicleQuery vehicle,
        @ModelAttribute PeriodQuery period, @ModelAttribute PageQuery page,
        @RequestParam(required = false) AlertType alertType) {
    period.validate();
    ...
}

// 서비스: 값이 있을 때만 조건이 붙는다 (MyBatis <if> 대신)
ScopeQuery resolved = scopeResolver.resolve(loginUser, scope);   // 지역담당자 범위 강제는 여기 한 곳에서
QueryConditions c = new QueryConditions()
        .eqIfPresent("u.regionId", resolved.getRegionId())
        .eqIfPresent("a.alertType", alertType)
        .carNumIfPresent(vehicle.getCarNum(), "a.carNum")
        .periodIfPresent("a.sentAt", period);

String from = " from Alert a join UnderpassEvent ue on ue.id = a.eventId join Underpass u on u.id = ue.underpassId";
return PagedQuery.fetch(em, "select a, ue.underpassId" + from, "select count(a)" + from, c,
        " order by a.sentAt desc, a.id desc", Object[].class, row -> 응답DTO로변환(row), page.toParams());
```
- JPQL에선 **테이블·컬럼명이 아니라 엔티티·필드명**을 써요 (`alert.created_at` → `Alert.sentAt`).
- 연관관계가 없어도 `join 엔티티 별칭 on 조건` 으로 자유롭게 조인돼요.
- 검색어 일치 점수 정렬(고객·관리자 목록)은 `MatchScore` 를 써요.

## 4. 꼭 알아야 할 설정·규칙

- **enum 컬럼**: DB 접속 URL 끝의 `?stringtype=unspecified` 가 PostgreSQL enum 변환을 대신해줘요. 엔티티엔 `@Enumerated(EnumType.STRING)` 만 붙이면 됨.
  - `RiskLevel`(DB엔 '안전/주의/위험')은 `RiskLevelConverter` 가 자동 적용돼서 아무것도 안 붙여도 돼요.
- **시간은 DB 시계 기준** (개발 규칙): 예전 SQL에서 `NOW()` 쓰던 자리는 `dbClock.now()` 를 써요.
  - `created_at` 처럼 DB 기본값(NOW())이 있는 컬럼은 엔티티에 `@Generated` 가 붙어 있어서 INSERT 때 DB가 채우고, 저장 직후 값을 다시 읽어와요.
- **일괄 UPDATE(@Modifying) 주의**: 실행 전에 그때까지 바꾼 값을 DB에 반영하고, 실행 후엔 메모리의 엔티티를 비워요.
  그래서 "엔티티 setter → 일괄 UPDATE" 순서로 써야 하고, 일괄 UPDATE 뒤에 그 엔티티를 쓰려면 다시 조회해야 해요. (`RegionServiceImpl.deleteRegion`, `UnderpassServiceImpl.blockUnderpass` 참고)
- **유니크 위반(409) 처리**: `saveAndFlush()` 를 try 안에서 부르고 `DataIntegrityViolationException` 을 잡아요 (예전 `DuplicateKeyException` 자리).
- **스키마는 DDL 스크립트로만 관리**: `ddl-auto=none` 이라 JPA가 테이블을 만들거나 고치지 않아요. 컬럼을 바꾸면 DDL과 엔티티를 같이 고쳐야 해요.

## 5. 테스트
- 서비스 단위 테스트는 Repository 를 `@Mock` 으로 바꿔서 그대로 돌아가요 (`src/test`).
- 엔티티 id는 setter가 없어서 테스트에서는 `TestEntities.withId(new Car(), 5L)` 로 채워요.
- `FinalApplicationTests` 는 실제 DB에 붙어서 스프링 전체가 뜨는지 보는 테스트라 DB가 켜져 있어야 통과해요.
