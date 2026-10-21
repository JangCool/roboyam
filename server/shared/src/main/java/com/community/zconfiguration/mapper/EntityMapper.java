package com.community.zconfiguration.mapper;

import java.util.List;

/**
 * 엔티티 → DTO 매퍼의 공통 계약.
 *
 * <p>도메인 매퍼는 이것을 상속하고 본문을 쓰지 않는다. 구현은 MapStruct 가 컴파일 때 만든다.
 *
 * <pre>{@code
 * @Mapper
 * public interface PrefixMapper extends EntityMapper<PrefixInfo, PrefixEntity> { }
 * }</pre>
 *
 * <p>읽는 방향({@code Entity → DTO})만 있다. 엔티티는 기본 생성자가 {@code protected} 이고 setter 없이
 * 정적 팩터리·도메인 메서드로만 만들고 바꾸므로, 매퍼가 엔티티를 만들거나 채우게 하지 않는다.
 */
public interface EntityMapper<D, E> {

    D toDto(E entity);

    List<D> toDtoList(List<E> entities);
}
