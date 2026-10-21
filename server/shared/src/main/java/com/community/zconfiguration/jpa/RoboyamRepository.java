package com.community.zconfiguration.jpa;

import io.hypersistence.utils.spring.repository.HibernateRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * 리포지토리 인터페이스의 바탕.
 *
 * <p>Spring Data 의 {@code save()} 는 {@code id != null} 이면 {@code merge()} 를 한다. PK 를 직접 채우는
 * 엔티티(문자열 ID·복합 키)는 처음 넣을 때도 id 가 차 있어서 행마다 SELECT 가 한 번씩 더 나간다.
 * {@link HibernateRepository} 가 {@code persist()} · {@code update()} · {@code merge()} 를 나눠 제공하므로
 * 넣는다·고친다를 부르는 쪽이 정한다.
 *
 * <p>{@code BaseJpaRepository} 가 아니라 {@code HibernateRepository} 를 쓴다. 앞의 것은 {@code JpaRepository} 의
 * 페이징·정렬·질의 파생을 함께 쓰지 못하고, 둘을 함께 상속하면 막으려던 {@code save()} 가 도로 살아난다.
 * 뒤의 것은 {@code save()} 계열을 {@code @Deprecated} 로 표시하고 호출하면 예외를 던진다.
 *
 * <p>동작하려면 {@code repositoryBaseClass = BaseJpaRepositoryImpl.class} 가 필요하다 — {@link JpaMasterConfig}.
 *
 * <pre>{@code
 * public interface PostRepository extends RoboyamRepository<PostEntity, Long> { }
 *
 * repository.persist(entity);   // 새로 넣는다 — SELECT 없음
 * repository.update(entity);    // 고친다
 * }</pre>
 *
 * <p>{@code save()} 는 쓰지 않는다.
 */
@NoRepositoryBean
public interface RoboyamRepository<T, ID> extends HibernateRepository<T>, JpaRepository<T, ID> {
}
