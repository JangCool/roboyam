package com.community.testsupport;

import com.community.zconfiguration.jpa.JpaMasterConfig;
import com.querydsl.jpa.impl.JPAQueryFactory;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 손으로 조립하는 작은 시험({@code new XxxQueries(...)} 처럼 스프링 빈 하나둘만 쓰는 시험)이 <b>운영과 같은 JPA·QueryDSL</b>을 쓰게 하는 JVM 하나당 한 개의 문맥 —
 * {@link JpaTestWiring} + 커머스 시험 DB({@link CommerceTestDatabase#migrated}, shared 스트림). 처음 부를 때 만들고 JVM 이 끝날 때 닫힌다.
 *
 * <pre>{@code
 * RequestRateLimits limits = new RequestRateLimits(new RequestRateLimitRepositoryQDSL(JpaTestContext.query()));
 * new TransactionTemplate(JpaTestContext.transactions()).execute(...);   // JPA 쓰기·잠금이 필요하면 이 트랜잭션 관리자로
 * }</pre>
 */
public final class JpaTestContext {

    private static AnnotationConfigApplicationContext context;

    private JpaTestContext() {
    }

    @Configuration(proxyBeanMethods = false)
    @Import(JpaTestWiring.class)
    static class Wiring {
        @Bean
        DataSource dataSource() {
            return CommerceTestDatabase.migrated();
        }
    }

    /** 문맥(처음이면 만든다) */
    public static synchronized AnnotationConfigApplicationContext context() {
        if (context == null) {
            context = new AnnotationConfigApplicationContext(Wiring.class);
            context.registerShutdownHook();
        }
        return context;
    }

    /** 마스터 QueryDSL 질의 팩토리(운영과 같은 빈) */
    public static JPAQueryFactory query() {
        return context().getBean(JpaMasterConfig.QUERY_FACTORY, JPAQueryFactory.class);
    }

    /** JPA 트랜잭션 관리자 — 같은 트랜잭션 안의 {@code JdbcTemplate} 도 같은 연결을 쓴다 */
    public static PlatformTransactionManager transactions() {
        return context().getBean(PlatformTransactionManager.class);
    }

    /** 문맥의 데이터소스({@link CommerceTestDatabase#migrated} 와 같은 것) */
    public static DataSource dataSource() {
        return context().getBean(DataSource.class);
    }
}
