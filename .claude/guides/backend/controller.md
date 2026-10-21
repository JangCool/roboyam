# 🎮 Backend Controller Layer Guide (`controller.md`)

## 1. 역할 및 기본 원칙
* Controller 는 HTTP 요청 수신, 입력 검증(`@Valid`), Service 호출, 응답 구성만 한다. 조건 분기·데이터 가공·트랜잭션 등 업무 로직을 넣지 않는다.
* `HttpServletRequest`·`HttpSession`·헤더 같은 Web 객체는 Controller(와 그 옆의 세션 보조 클래스)에서 끝낸다. Service 에는 IP·"인증됐는가" 같은 값만 뽑아 넘긴다.
* 예외는 `try-catch` 로 처리하지 않고 `GlobalExceptionHandler` 에 맡긴다(`exception.md`). Swagger 등 문서화 어노테이션은 쓰지 않는다.
* 접근 규칙(로그인 필요 여부)은 Controller 가 아니라 `SecurityConfig` 에서 정한다.

---

## 2. 주요 업무 및 규약
* **URL**: `/api/<업무>/<리소스>` — 리소스는 명사 `kebab-case`, 버전 접두어(`/v1`)는 없다. CRUD 가 아닌 동작만 동사형 하위 경로를 허용한다(`/login`, `/verify-token`).
* **HTTP Method/상태**: `GET` 조회 · `POST` 생성(`201`)·동작 · `PUT` 전체 수정 · `PATCH` 부분 수정 · `DELETE` 삭제(`204`). 본문이 없는 성공은 `204`, 비동기 접수는 `202`.
* **응답**: 본문이 있는 성공은 `ApiResponse.success(data)`(공통 응답 래퍼, `com.community.zconfiguration.response`)로 감싼다 — `{success:true, data, meta}`. 상태 코드가 기본(200)이면 반환 타입을 `ApiResponse<T>` 로, `201` 등 상태·헤더를 정해야 하면 `ResponseEntity<ApiResponse<T>>` 로 쓴다. 본문이 없는 성공(`204`)은 래퍼 없이 `ResponseEntity<Void>`. `data` 에는 Response DTO(또는 그 목록)만 넣고, `meta` 는 팩터리가 채우므로 Controller 가 만들지 않는다. 목록은 `data` 가 배열이고, 페이징 응답 모양은 필요해질 때 정해 이 가이드에 적는다. 인증·설치처럼 민감한 응답은 `Cache-Control: no-store`.
* **Entity 노출 금지**: Entity 를 파라미터·반환 타입으로 쓰지 않는다. DTO 이름은 용도가 드러나게 짓는다(`LoginRequest`, `MeResponse`).
* **입력 검증**: 단순 요청은 Request DTO 의 Bean Validation + 파라미터의 `@Valid`. 검사 순서가 계약이거나 경로 키(`root.email`)가 필요한 요청은 `@Valid` 없이 Service 가 `ValidationException` 으로 모은다(`service.md`).
* **의존성**: Service 와 세션 보조 클래스만 생성자 주입한다.

---

## 3. 작성 예시
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final MeService meService;
    private final LoginSession loginSession; // 세션 만들기·끊기 — 웹 계층 보조 클래스

    public AuthController(LoginService loginService, MeService meService, LoginSession loginSession) {
        this.loginService = loginService;
        this.meService = meService;
        this.loginSession = loginSession;
    }

    @PostMapping("/login")
    public ApiResponse<MeResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        AuthUser principal = loginService.authenticate(body.email(), body.password());
        loginSession.establish(request, response, principal);
        return ApiResponse.success(meService.of(principal.id()).orElseGet(MeResponse::anonymous));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        loginSession.terminate(request);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ Controller 에 `@Transactional` 선언, Repository 직접 주입
2. ❌ `Map<String, Object>` 요청/응답
3. ❌ 본문 있는 성공 응답을 `ApiResponse` 없이 반환, Controller 에서 `ApiResponse` 로 오류 응답 조립·`try-catch`
