# ⚙️ Backend Service Layer Guide (`service.md`)

## 1. 역할 및 기본 원칙
* Service 는 업무 로직, 트랜잭션 경계, 도메인 흐름 제어를 맡는다.
* HTTP 객체(`HttpServletRequest`, `ResponseEntity` 등)와 상태 코드를 다루지 않는다(`controller.md`).
* 업무 조건을 못 채우면 자바 기본 예외 대신 업무 패키지 `exception` 의 도메인 예외를 던진다(`exception.md`).

---

## 2. 주요 업무 및 규약
* **주입**: `@Service` + 명시적 생성자 주입(lombok 없음).
* **트랜잭션**: 읽기 위주 서비스는 클래스에 `@Transactional(readOnly = true)`, 변경(CUD) 메서드에만 `@Transactional`. 외부 호출(메일 서버 접속·느린 해시 비교)이나 입력 검사가 DB 연결을 붙잡으면 안 되는 서비스는 클래스 단위 트랜잭션을 두지 않고 쓰기 메서드에만 `@Transactional` 을 건다. 트랜잭션 안에서 오래 걸리는 외부 I/O 를 하지 않는다.
* **입력 검사**: 검사 순서가 계약이거나 칸 경로 키(`root.email`, `boards[0].name`)가 필요하면 Service 가 위반을 모아 `ValidationException`(칸 경로 → `MessageRef`)으로 던진다. 그 밖의 단순 검증은 DTO 의 Bean Validation 이 맡는다(`dto.md`).
* **ID·저장**: ID 는 `SnowflakeIdGenerator.nextId()` 로 발급하고 새 Entity 는 `persist()` 로 넣는다(`repository.md`).
* **도메인 상태**: Entity 필드를 밖에서 고치지 않고 도메인 메서드/정적 팩터리를 호출한다. 변경 감지(Dirty Checking)에 맡기므로 변경 메서드에는 `@Transactional` 이 있어야 한다.
* **보안 값**: 비밀번호·토큰은 로그·예외 메시지에 싣지 않는다. 인증 실패 사유는 밖으로 구분해 알리지 않는다(`_backend-core.md`).

---

## 3. 작성 예시
`VerificationTokenService` — 클래스 트랜잭션 없이 쓰기 메서드에만 `@Transactional`, 도메인 예외, Snowflake ID + `persist()`:
```java
@Service
public class VerificationTokenService {

    // 필드와 생성자 주입은 생략: users, tokens, tokenQueries, ids, props

    @Transactional
    public IssuedVerification issue(long userId) {
        UserEntity user = users.findWithLockById(userId).filter(u -> !u.isGone())
                .orElseThrow(() -> new CommonException(ErrorCode.UNAUTHENTICATED));
        if (user.isEmailVerified() || user.getEmail() == null) {
            throw new AuthAlreadyVerifiedException();
        }

        Instant now = Instant.now();
        tokenQueries.consumeAll(userId, AuthTokenPurpose.VERIFY_EMAIL, now);
        String raw = AuthTokens.newToken();
        tokens.persist(AuthTokenEntity.create(ids.nextId(), userId, AuthTokenPurpose.VERIFY_EMAIL, AuthTokens.sha256Hex(raw),
                now.plus(Duration.ofHours(props.verifyTokenHours())), now));
        return new IssuedVerification(user.getEmail(), raw);
    }
}
```

`LoginAttemptService` — 읽기는 `readOnly = true`, 변경은 행을 잠그고 읽은 뒤 도메인 메서드를 불러 변경 감지에 맡긴다:
```java
@Transactional(readOnly = true)
public void assertNotLocked(String email) {
    Instant now = Instant.now();
    attempts.findLockedUntil(key(email)).filter(until -> until.isAfter(now)).ifPresent(until -> {
        throw new AuthLoginLockedException(secondsUntil(until, now));
    });
}

@Transactional
public void recordSuccess(String email, long userId) {
    Instant now = Instant.now();
    attempts.findForUpdate(key(email)).ifPresent(attempt -> attempt.reset(now));
    users.findById(userId).ifPresent(user -> user.markLoggedIn(now));
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ Service 에서 HTTP 상태·응답 객체를 직접 제어하는 행위
2. ❌ CUD 메서드에 `@Transactional` 누락, 트랜잭션 안에서 장시간 외부 I/O
3. ❌ Entity 필드를 외부에서 `setXxx()` 로 수정하는 행위, `save()` 호출
