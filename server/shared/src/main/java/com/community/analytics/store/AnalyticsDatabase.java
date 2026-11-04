package com.community.analytics.store;

import com.zaxxer.hikari.HikariDataSource;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 방문 분석 표({@code analytics_*}) 전용 연결 — 빈 이름 {@code analyticsJdbc}({@link AnalyticsDatabaseConfig}). docs/features/site-analytics-contract.md 「옮기기 쉽게」.
 *
 * <ul>
 *   <li><b>지금은 메인 DB 와 같은 연결 풀</b>이다. {@code roboyam.analytics.datasource.url} 을 주면 그 DB 로 따로 붙는다(나중에 TimescaleDB 로 옮길 때 — 설정만 바꾸면 된다).
 *       분석 표를 읽고 쓰는 코드는 모두 이것을 거친다(JPA 없이 SQL). 다른 업무 표와 외래 키·조인이 없다.</li>
 *   <li>트랜잭션은 이 연결의 {@link DataSourceTransactionManager} 로 연다({@link #inTransaction}) — 메인 DB 와 같은 풀이어도 메인(JPA) 트랜잭션 밖(몰아쓰기 스레드)에서 쓰므로 섞이지 않는다.
 *       관리자 대시보드처럼 메인 읽기 트랜잭션 안에서 {@link #jdbc()} 로 읽으면 같은 풀일 때는 그 연결에 참여하고, 따로 둔 DB 면 자동 커밋으로 읽는다.</li>
 *   <li>따로 둔 풀은 앱이 내려갈 때 닫는다(이것을 쓰는 빈 — 종료 때 몰아쓰기 — 이 먼저 정리된다).</li>
 * </ul>
 */
public final class AnalyticsDatabase implements DisposableBean {

    private final DataSource dataSource;
    private final boolean separate;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    /**
     * @param separate 메인 DB 와 다른 연결 풀인가(그러면 이 객체가 닫는다)
     */
    public AnalyticsDatabase(DataSource dataSource, boolean separate) {
        this.dataSource = dataSource;
        this.separate = separate;
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    public DataSource dataSource() {
        return dataSource;
    }

    /** 메인 DB 와 다른 DB 인가 */
    public boolean separate() {
        return separate;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    /** 분석 연결의 트랜잭션 하나 안에서 — 실패하면 모두 되돌린다 */
    public <T> T inTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

    @Override
    public void destroy() {
        if (separate && dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }
}
