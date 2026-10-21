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
