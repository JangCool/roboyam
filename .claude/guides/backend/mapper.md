# 🗺️ MapStruct Guide (`mapper.md`)

## 1. 역할 및 기본 원칙
* MapStruct 는 컴파일 시점의 `Entity → Response DTO` 변환에만 쓴다. 업무 규칙·검증·DB 조회·외부 호출·트랜잭션을 넣지 않는다.
* 단방향이다. Request DTO → Entity 자동 매핑과 `@MappingTarget` 부분 수정은 쓰지 않는다(`dto.md`).
* 단일 Entity 로 응답을 만들 수 있을 때 쓴다. 여러 소스를 조합하거나 계산이 필요한 응답은 Service 가 Response 생성자로 직접 만든다.

---

## 2. 주요 업무 및 규약
* **위치**: 매퍼는 `<업무>/mapper/` 에 둔다. 공통 인터페이스와 설정은 `com.community.zconfiguration.mapper` 에 있다.
* **공통 계약**: `EntityMapper<D, E>`(`toDto`, `toDtoList`)를 상속한다. 매퍼 인터페이스는 `@Mapper` 를 붙이고 본문 없이 상속만 하며, 이름이 다른 필드만 `toDto` 를 `@Override` 해 `@Mapping` 을 단다.
* **전역 설정**: 빌드 규약(`roboyam.mapstruct-conventions`)이 `componentModel=spring`, `unmappedTargetPolicy=ERROR` 를 건다. 매퍼에는 `@Mapper` 만 붙이고 누락 필드는 컴파일 오류로 잡는다. 의도적으로 비우는 필드만 `@Mapping(target = "...", ignore = true)` 로 밝힌다.
* **커스터마이징**: 이름이 다르면 `@Mapping(source, target)`, 반복되는 변환은 `@Named` 메서드. 복잡한 조립은 매퍼가 아니라 Service 에서 한다.

---

## 3. 작성 예시
공통 계약(`com.community.zconfiguration.mapper.EntityMapper`):
```java
public interface EntityMapper<D, E> {

    D toDto(E entity);

    List<D> toDtoList(List<E> entities);
}
```

---

## 4. 금지 사항 (Anti-Patterns)
1. ❌ `expression = "java(...)"` 인라인 코드
2. ❌ 감사 필드·서버 생성값을 매퍼에서 설정하는 행위
3. ❌ 생성된 `*MapperImpl` 직접 수정, lombok 과 함께 쓰기
