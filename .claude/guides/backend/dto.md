# 📦 Request / Response DTO Guide (`dto.md`)

## 1. 역할 및 기본 원칙
* DTO 는 API 계약에 필요한 필드만 갖고 업무 로직이나 DB 접근을 넣지 않는다.
* Java `record` 를 쓴다(lombok 없음). 프레임워크 요구로 상태 변경이 필요할 때만 class 를 검토한다.
* Entity 를 요청/응답으로 노출하거나 DTO 필드로 포함하지 않는다.

---

## 2. 주요 업무 및 규약
* **패키지**: `<업무>/dto/request/`, `<업무>/dto/response/`. 서비스 사이에서만 오가는 값 객체는 `<업무>/dto/` 바로 아래에 둔다. 한 파일에 DTO 여럿을 모으지 않는다.
* **Request**:
  * 단순 요청은 Bean Validation(`@NotBlank`·`@Size`·`@Pattern` …)으로 입력 계약을 정의하고, 중첩 객체는 필드/컬렉션에 `@Valid`. 문구는 한국어 리터럴이 아니라 `message = "{키}"` 로 두어 메시지 번들(`exception.md`)에서 풀리게 한다.
  * 검사 순서가 계약이거나 경로 키가 필요한 요청(설치 마법사)은 어노테이션 없이 받고 Service 가 검사한다(`service.md`).
  * 서버가 정하는 값(사용자 ID·권한·감사 시각)은 포함하지 않는다.
  * 비밀번호·토큰 같은 비밀값을 가진 DTO 는 `toString()` 을 재정의해 값을 싣지 않는다.
* **Response**:
  * 화면이 쓰는 공개 데이터만 담는다. 비밀값·내부 구조를 담지 않는다. 목록과 상세는 용도에 맞게 분리한다.
  * 관리자가 입력해 DB 에 저장된 이름(게시판 이름·말머리 이름 등)은 `name` 과 선택 `nameEn` 을 함께 내려주고, 어느 언어를 보일지는 화면이 정한다. 서버가 요청 언어로 골라 한 필드로 합치지 않는다.
  * Snowflake ID 는 `String` 으로 내린다. 값이 없을 수 있는 필드는 `null` 로 둔다(계약에 명시).
* **변환**: Entity → Response 는 `mapper.md`. Request → Entity 자동 변환은 하지 않는다 — Service 가 입력·권한·업무 규칙을 확인한 뒤 Entity 팩터리/도메인 메서드로 만든다.

---

## 3. 작성 예시
```java
public record LoginRequest(
        @NotBlank(message = "{user.email.required}") @Size(max = 254, message = "{user.email.max}") String email,
        @NotBlank(message = "{user.password.required}") String password) {

    @Override
    public String toString() {
        return "LoginRequest[email=(감춤), password=(감춤)]";
    }
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ 단일 거대 DTO 파일에 모든 DTO 를 집어넣는 행위
2. ❌ 서버 관리 필드(생성자·시각·권한)를 클라이언트가 보내도록 설계하는 행위
3. ❌ Entity 를 DTO 에 포함하거나 검증 문구를 코드에 리터럴로 쓰는 행위
