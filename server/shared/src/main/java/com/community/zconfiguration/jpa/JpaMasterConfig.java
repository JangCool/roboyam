package com.community.zconfiguration.jpa;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.hypersistence.utils.spring.repository.BaseJpaRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 마스터 데이터소스의 JPA 구성.
 *
 * <p>데이터소스가 하나라고 가정하지 않는다. 조회용 slave 가 붙으면 이 파일 옆에 {@code JpaSlaveConfig} 를
 * 더하는 것으로 끝나야 하므로, 빈에 이름을 붙이고({@link #ENTITY_MANAGER} · {@link #TRANSACTION_MANAGER} ·
 * {@link #QUERY_FACTORY}) 리포지토리를 패키지로 가른다 — {@code ...repository.master} 만 이 설정이 스캔한다.
 *
 * <p>{@code LocalContainerEntityManagerFactoryBean} 은 손으로 만들지 않는다. Spring Boot 자동 구성이 만든 것을
 * 마스터로 이름 붙여 쓴다 — 손으로 만들면 {@code spring.jpa.*} 설정을 전부 다시 옮겨야 하고 하나라도 빠지면
 * 조용히 동작이 달라진다.
 *
 * <p>주의: 앱 진입점의 {@code @SpringBootApplication} 스캔 범위에 {@code com.community.zconfiguration} 이 들어와야 한다.
 */
@Configuration
@EnableJpaRepositories(
        // 마스터용 리포지토리만 스캔한다. slave 가 생기면 그쪽은 자기 설정이 스캔한다.
        basePackages = "com.community.**.repository.master",
        // save() 대신 persist()/update() 를 쓰게 하는 바탕 구현 (RoboyamRepository 참조)
        repositoryBaseClass = BaseJpaRepositoryImpl.class,
        entityManagerFactoryRef = JpaMasterConfig.ENTITY_MANAGER,
        transactionManagerRef = JpaMasterConfig.TRANSACTION_MANAGER)
public class JpaMasterConfig {

    /** Spring Boot 자동 구성이 만드는 이름. 그것을 마스터로 삼는다. */
    public static final String ENTITY_MANAGER = "entityManagerFactory";

    /** 마찬가지. */
    public static final String TRANSACTION_MANAGER = "transactionManager";

    /** 마스터 질의 팩토리. slave 가 생기면 slave 용이 옆에 선다. */
    public static final String QUERY_FACTORY = "roboyam-master-query-factory";

    /**
     * QueryDSL 질의 팩토리. 자동 구성이 만들어 주지 않으므로 여기서 만든다.
     *
     * <p>{@code @Primary} 라 slave 가 생겨도 이름을 지정하지 않은 기존 주입은 계속 마스터로 간다.
     */
    @Bean(QUERY_FACTORY)
    @Primary
    public JPAQueryFactory masterQueryFactory(@Qualifier(ENTITY_MANAGER) EntityManager entityManager) {
        return new JPAQueryFactory(entityManager);
    }
}
