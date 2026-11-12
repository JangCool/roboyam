package com.community.analytics.store;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 분석 전용 연결({@link AnalyticsDatabase}, 빈 이름 {@code analyticsJdbc}). {@code DataSource} 빈을 하나 더 만들지 않는다 — 만들면 Boot 의 메인 DB 자동 구성(JPA·Flyway·세션)이 물러난다.
 *
 * <p>설정({@code irobit-shared.yml} 의 {@code irobit.analytics.datasource.*}):
 * <ul>
 *   <li>{@code url} 이 비면(기본) 메인 DB 연결 풀을 그대로 쓴다.</li>
 *   <li>주면 그 DB 로 작은 풀({@code maximum-pool-size}, 기본 3)을 따로 연다. 비밀번호는 메인과 같이 {@code ENC(...)} 로 줄 수 있다(EncryptedPropertyPostProcessor 가 모든 설정 값을 푼다).
 *       이때 analytics 스트림의 크론(R__analytics_*)이 기대는 공통 함수·설정 표는 메인 DB 에 있으므로 그 DB 쪽으로 옮겨야 한다(계약 「미확정」).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class AnalyticsDatabaseConfig {

    @Bean(name = "analyticsJdbc")
    AnalyticsDatabase analyticsJdbc(DataSource dataSource, Environment env) {
        String url = env.getProperty("irobit.analytics.datasource.url", "").strip();
        if (url.isEmpty()) {
            return new AnalyticsDatabase(dataSource, false);
        }
        HikariDataSource separate = new HikariDataSource();
        separate.setPoolName("analytics");
        separate.setJdbcUrl(url);
        separate.setUsername(env.getProperty("irobit.analytics.datasource.username"));
        separate.setPassword(env.getProperty("irobit.analytics.datasource.password"));
        separate.setMaximumPoolSize(env.getProperty("irobit.analytics.datasource.maximum-pool-size", Integer.class, 3));
        return new AnalyticsDatabase(separate, true);
    }
}
