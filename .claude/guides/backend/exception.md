# ⚠️ Backend Exception Guide (`exception.md`)

## 1. 역할 및 기본 원칙
* 업무 예외는 해당 업무 패키지의 `exception/` 에 두고 `CommonException` 을 상속한다. 공통 코드·응답·처리기(`CommonException`·`ErrorCode`·`ValidationException`·`ErrorResponse`·`GlobalExceptionHandler`·`ErrorResponseWriter`)는 `com.community.zconfiguration.exception` 에 있다. 업무 예외를 `zconfiguration` 이나 임의의 `common` 에 모으지 않는다.
* 예외 이름은 실패 원인을 나타낸다(`MemberNotFoundException`, `BoardAccessDeniedException`).
* 스택 트레이스·SQL·서버 경로·접속 문자열·비밀값은 응답에 담지 않고 로그에만 남긴다(비밀값은 로그에도 남기지 않는다).

---

## 2. 주요 업무 및 규약

### 2-1. 공통 응답 (`ApiResponse`) 과 오류 응답
* 성공·실패 모두 같은 래퍼다: 성공 `{success:true, data, meta}`, 실패 `{success:false, error:{code, message, fields?, retryAfterSeconds?}, meta}`(`error` 본문이 `ErrorResponse`). `null` 필드는 나가지 않는다(성공엔 `error` 없음, 실패엔 `data` 없음). 본문 없는 `204` 는 래퍼도 없다.
* `meta` = `{timestamp(UTC ISO-8601), requestId, locale(ko|en)}`. `requestId` 는 요청 헤더 `X-Request-Id`(영숫자·`-`·`_` 64자 이하)를 쓰고 없거나 형식이 틀리면 서버가 UUID 를 만든다. 같은 값을 응답 헤더 `X-Request-Id` 와 로그(MDC `requestId`)에 싣는다. 필터·보안 단계에서 쓰는 오류 응답(`ErrorResponseWriter`)도 같은 모양이다.
* `fields` 는 칸 오류가 있을 때만 나가고(없으면 키 자체가 없다 — 화면은 `fields` 가 없는 것으로 읽는다), `retryAfterSeconds` 는 해당될 때만 나가고 `Retry-After` 헤더도 단다.
* `CommonException` 은 HTTP 상태, 안정적인 `code`, 메시지 키(`MessageRef`)를 들고 온다. 핸들러가 하나라 새 예외를 만들 때 처리기를 고치지 않는다. 화면은 `code`(와 `fields` 경로)로 분기하므로 문구·언어가 바뀌어도 이 값은 유지된다.
* 공통 코드(`ErrorCode`): `VALIDATION`·`MALFORMED_REQUEST`·`INVALID_PARAMETER`·`UNAUTHENTICATED`·`FORBIDDEN`·`CSRF_INVALID`·`NOT_FOUND`·`METHOD_NOT_ALLOWED`·`FILE_TOO_LARGE`·`CRYPTO_KEY_MISSING`·`DATABASE_UNAVAILABLE`·`INTERNAL`. 업무 코드는 여기에 추가하지 않고 업무 예외로 정의한다.
* **`VALIDATION`(400)**: `fields` 키는 점·대괄호 경로(`root.email`, `items[0].name`)이고 DTO 선언 순서로 결정적이다. 칸마다 첫 문구만 담고 대표 `message` 는 첫 칸의 문구다. `@Valid` 실패는 처리기가, 서비스가 모으는 위반은 `ValidationException` 이 같은 응답으로 만든다(`service.md`).

### 2-2. 응답 문구는 코드에 박지 않는다 (ko/en)
* 사용자에게 보이는 문구(오류 `message`, `fields` 문구, 설치 점검 `label`/`detail`, 프리셋 이름, 인증 메일 본문 …)는 `server/shared/src/main/resources/messages/messages_{ko,en}.properties`(UTF-8)의 **키**로 정의하고, 코드는 `MessageRef(key, args…)` 만 들고 다닌다. 키는 `업무.무엇`, 오류 기본 문구는 `error.<CODE>`.
* 예외는 `super(status, "CODE", new MessageRef("error.CODE"))` 로 키를 준다. 처리기·`ErrorResponseWriter` 가 요청 `Accept-Language`(`ko`|`en`, 그 밖·없음은 `ko`)로 풀어 `message` 에 싣고 응답에 `Content-Language` 를 단다. 그 언어에 키가 없으면 `ko` 로 되돌린다.
* 새 문구는 **두 번들에 같은 키로 함께 추가**한다(키 집합과 `{0}` 자리표시자 일치 — local/dev 기동 때 불일치를 경고한다). 인자가 있는 문구에는 작은따옴표를 쓰지 않는다(MessageFormat 인용 문자). 인자에는 한도 같은 규칙 값만 싣고 사용자가 입력한 값은 싣지 않는다.
* 옮기지 않는 것: 서버 로그, 기동 때 출력하는 설치 토큰 안내, CLI 출력, 운영자가 입력한 값, 개발자용 내부 예외 메시지.

---

## 3. 작성 예시
```java
public class AuthLoginLockedException extends CommonException {

    public AuthLoginLockedException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED", new MessageRef("error.LOGIN_LOCKED"),
                Map.of(), retryAfterSeconds);
    }
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 업무 예외를 `zconfiguration`·`common` 에 모으는 행위
2. ❌ 응답 문구를 한국어/영어 리터럴로 코드에 쓰는 행위 (메시지 키로)
3. ❌ 예외를 잡고 삼키거나 정상 응답으로 바꾸는 행위, 같은 예외를 여러 계층에서 잡아 다시 던지는 행위
4. ❌ 한쪽 언어 번들에만 키를 추가하는 행위
