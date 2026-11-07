# 🗄️ Repository Guide (`repository.md`)

## 1. 역할 및 기본 원칙
* Repository 는 DB 조회·저장만 한다. 업무 규칙과 트랜잭션은 Service 에 둔다(Repository 에 `@Transactional` 을 붙이지 않는다 — 부르는 Service 가 연다).
* 위치는 `<업무>/repository/master/` 다(`JpaMasterConfig` 가 이 패키지를 스캔한다).
* 도메인 Repository 는 `RoboyamRepository<T, ID>` 를 상속하고 `JpaRepository` 등을 직접 상속하지 않는다.
* `save()` 를 쓰지 않는다. ID 를 앱이 발급하므로 새 Entity 는 `persist()`, 고칠 때는 `update()`(또는 변경 감지)를 쓴다.

---

## 2. 주요 업무 및 규약
* **기술 선택**:
  * PK 조회/삭제: 기본 메서드 · 단순 필드 조건: 파생 메서드
  * 복합 조건/조인/정렬/페이징/프로젝션, 조건부 UPDATE: QueryDSL (`XxxRepositoryQDSL extends RoboyamRepositoryQDSL`, `@Repository`, 질의 팩토리 `query` 사용, DB 시각은 `DB_NOW`)
  * 행 잠금: 파생 메서드에 `@Lock(PESSIMISTIC_WRITE)` (트랜잭션 안에서만 의미가 있다)
  * 대량 INSERT: 건수를 재 보고 JPA 배치·JDBC 배치·PostgreSQL `COPY` 중 선택
  * DB 전용 기능: 네이티브 SQL — 사유를 주석으로 적는다
* **새 표에는 엔티티를 만든다.** 표 몇 문장에 네이티브가 필요해도 표 전체를 JDBC 로 짜지 않는다 — 그 문장만 같은 `XxxRepositoryQDSL` 안에 사유 주석과 함께 둔다(`LoginAttemptRepositoryQDSL` 이 본보기). 관리자 스트림 표의 엔티티는 `server/admin` 에 둔다(QueryDSL 처리기 있음).
* **네이티브 SQL 로 인정하는 사유**(이것 말고는 QueryDSL): `ON CONFLICT` upsert · 결과가 꼭 같은 문장이어야 하는 `RETURNING` · `pg_advisory_xact_lock` · `cron.*`/`CALL`/DB 함수 · DDL·파티션·`COPY`·`TRUNCATE` · 분석 DB(`AnalyticsDatabase`) · jsonb 연산·`generate_series`·윈도 함수·`DISTINCT ON`·`GROUPING SETS` 가 핵심인 집계 · 대량 `batchUpdate` · **JDBC 세이브포인트 안에서 도는 쓰기·읽기**(알림 발행·포인트 원장·회원 알림 — 세이브포인트 안에서 JPA 질의가 실패하면 업무 트랜잭션 전체가 롤백 전용이 된다, 2026-11-07 실측).
* **사유가 아닌 것**: 조건부 상태 전이(`update().where(status.eq(..)).execute() == 1`) · 카운터(`set(x, x.add(1))`) · `FOR UPDATE`/`FOR SHARE`(`@Lock`·`setLockMode`) · `SKIP LOCKED`(`PESSIMISTIC_WRITE` + 힌트 `jakarta.persistence.lock.timeout = -2`) · `coalesce`/`greatest`/`FILTER`(`CaseBuilder`·`Expressions` 템플릿) · 「다른 모듈과 같은 방식」「같은 연결」「flush 차례」「스프링 없는 시험」. 엔티티 조회가 부담이면 프로젝션을 쓴다.
* **잠금 세기**: Hibernate 의 `PESSIMISTIC_WRITE` 는 PostgreSQL 에서 `FOR NO KEY UPDATE` 다(조인이 있으면 `OF <루트>` 가 붙는다). 작업자끼리는 서로 막지만 그 행을 FK 로 참조하는 INSERT 는 막지 않는다 — 그것까지 막아야 하는 곳만 네이티브 `FOR UPDATE` 로 둔다.
* **시각**: 판정·기록 시각을 서비스가 `Clock` 으로 넘기는 업무(커머스·제재·탈퇴 등 — 시험이 시계를 옮긴다)는 `DB_NOW` 로 바꾸지 않고 인자 `now` 를 쓴다.
* **QueryDSL**: 정렬·페이징은 DB 에서 한다(전체 조회 후 메모리 정렬 금지). 컬렉션 `fetchJoin()` 과 페이징을 함께 쓰지 않는다. 연관은 `LAZY`, N+1 은 필요한 곳에만 `fetchJoin()`.

---

## 3. 작성 예시
```java
public interface UserRepository extends RoboyamRepository<UserEntity, Long> {
    Optional<UserEntity> findByEmail(String email);
}

@Repository
public class ProfileRepositoryQDSL extends RoboyamRepositoryQDSL {

    public ProfileRepositoryQDSL(JPAQueryFactory query) {
        super(query);
    }

    public boolean existsByNickname(String nickname) {
        QProfileEntity profile = QProfileEntity.profileEntity;
        return query.selectOne().from(profile)
                .where(profile.nickname.lower().eq(Expressions.stringTemplate("lower({0})", nickname)))
                .fetchFirst() != null;
    }
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ Repository 에 `@Transactional` 선언
2. ❌ 문자열 JPQL(`@Query("SELECT …")`) — QueryDSL 을 쓴다. 예외는 사유를 주석으로 적은 네이티브 SQL 뿐이다
3. ❌ `save()` 호출 (`persist()` / `update()` 사용)
4. ❌ N+1 해결을 위한 `EAGER` 설정
