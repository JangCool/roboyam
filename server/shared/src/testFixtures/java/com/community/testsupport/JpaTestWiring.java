package com.community.testsupport;

import com.community.zconfiguration.jpa.JpaMasterConfig;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 스프링 부트 앱 전체를 띄우지 않고 손으로 조립하는 시험 문맥({@code AnnotationConfigApplicationContext} — 공구·결제·포인트 상점·이모티콘 시험 키트)에 <b>운영과 같은 JPA</b>를 붙인다.
 *
 * <ul>
 *   <li>엔티티 관리자·트랜잭션 관리자는 부트 자동 설정({@link HibernateJpaAutoConfiguration})이 만든다 — 이름 규칙(낙타 → 밑줄)·방언이 운영과 같다. 트랜잭션 관리자는
 *       {@code JpaTransactionManager} 라서 같은 트랜잭션 안의 {@code JdbcTemplate}(남겨 둔 네이티브 문장)도 같은 연결을 쓴다.</li>
 *   <li>리포지토리 스캔·{@code save()} 막는 바탕·QueryDSL 질의 팩토리는 운영 설정({@link JpaMasterConfig})을 그대로 쓴다.</li>
 *   <li>부르는 쪽 문맥에 {@code DataSource} 빈이 하나 있어야 한다({@link CommerceTestDatabase#migrated}). {@code PlatformTransactionManager} 빈을 따로 만들지 않는다
 *       (만들면 JPA 쓰기가 그 트랜잭션에 실리지 않는다).</li>
 * </ul>
 *
 * <pre>{@code
 * @Configuration
 * @Import(JpaTestWiring.class)
 * static class Wiring { @Bean DataSource dataSource() { return CommerceTestDatabase.migrated(); } ... }
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(HibernateJpaAutoConfiguration.class)
@EntityScan("com.community")
@Import(JpaMasterConfig.class)
public class JpaTestWiring {
}
