package com.community.zconfiguration.jpa;

import com.querydsl.jpa.impl.JPAQueryFactory;
import io.hypersistence.utils.spring.repository.BaseJpaRepositoryImpl;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

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
     * JPA(Hibernate) 의 SQL 예외를 {@code JdbcTemplate} 과 같은 규칙({@link SQLExceptionSubclassTranslator} — PostgreSQL 은 SQLState 로 가른다)으로 바꾼다.
     *
     * <p>그대로 두면 Hibernate 의 변환은 유일 위반(23505)도 {@code DataIntegrityViolationException} 으로만 준다 — 네이티브 SQL(JDBC)에서 JPA 로 옮긴 넣기를
     * {@code catch (DuplicateKeyException)} 로 받던 서비스가 409 대신 500 을 낸다. 저장소({@code @Repository} 예외 변환)·Spring Data 리포지토리·트랜잭션 커밋(변경 감지가
     * 커밋 때 내보낸 넣기) 모두 이 방언을 거치므로 한 곳에서 맞춘다. 이 변환이 모르는 SQLState 는 null 을 돌려 Hibernate 의 변환으로 넘어간다.
     *
     * <p>{@code static} — 빈 후처리기라 설정 클래스보다 먼저 만들어진다.
     */
    @Bean
    public static BeanPostProcessor roboyamJpaExceptionTranslation() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) {
                if (bean instanceof HibernateJpaVendorAdapter adapter) {
                    adapter.getJpaDialect().setJdbcExceptionTranslator(new SQLExceptionSubclassTranslator());
                }
                return bean;
            }
        };
    }

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
