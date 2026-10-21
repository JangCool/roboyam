package com.community.batch.service;

import com.community.batch.catalog.BatchCatalog;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * DB 배치(저장 프로시저 + pg_cron)와 말하는 곳 — pg_cron 의 상태·등록된 job 읽기, 켜기/끄기와 cron 수정({@code cron.alter_job}), 「지금 실행」({@code CALL}).
 *
 * <p>Native SQL 인 이유: {@code cron.job}·{@code cron.alter_job}·{@code CALL} 은 PostgreSQL/pg_cron 전용이라 JPQL·QueryDSL 에 대응 문법이 없고, 확장이 없는 DB 에서는
 * 엔티티로 매핑할 수 없는 표다(그래서 읽기도 이 클래스 한 곳에 모은다). 식별자(프로시저 이름)는 카탈로그 상수에서 만들고 값은 모두 바인딩한다.
 *
 * <p><b>권한</b>: 앱 DB 계정이 cron job 의 소유자다(마이그레이션이 그 계정으로 {@code cron.schedule} 한다). 그래서 별도 GRANT 나 SECURITY DEFINER 래퍼 없이
 * 자기 job 을 읽고({@code cron.job} 은 소유자만 보이는 RLS) {@code cron.alter_job} 으로 고친다 — 권한 상승이 없다. 슈퍼유저가 아닌 계정은 {@code cron} 스키마의 USAGE 만 받으면 된다.
 *
 * <p>트랜잭션: 읽기와 {@code alter_job} 은 부르는 서비스의 트랜잭션에 참여한다. {@link #call}은 <b>트랜잭션 밖의 새 연결</b>에서 돌린다 — 프로시저가 덩어리마다
 * COMMIT 하므로 이미 열린 트랜잭션 안에서는 실행할 수 없다.
 */
@Component
public class DbBatchGateway {

    private static final Logger log = LoggerFactory.getLogger(DbBatchGateway.class);

    /** Java 배치 잠금({@code BatchLockService})과 같은 어드바이저리 잠금 이름공간 — 같은 키면 Java·DB 어느 쪽이 돌든 서로를 막는다 */
    private static final int LOCK_NAMESPACE = 7_340_002;

    /** {@code UNREADABLE}: DB 의 배치 도구를 읽지 못했다(마이그레이션이 적용되지 않은 DB 등) */
    public static final String UNREADABLE = "UNREADABLE";

    /** pg_cron 의 지금 상태. {@code status} 는 {@code READY} 이거나 쓸 수 없는 이유 코드, {@code timezone} 은 pg_cron 이 cron 식을 해석하는 시간대 */
    public record PgCronState(String status, String timezone) {

        public boolean ready() {
            return "READY".equals(status);
        }
    }

    /** DB 배치 하나의 DB 쪽 사실. {@code cronJobId} 가 null 이면 pg_cron 에 등록되어 있지 않다 */
    public record DbJob(String jobKey, String defaultCron, Long cronJobId, String schedule, boolean active) {

        public boolean scheduled() {
            return cronJobId != null;
        }
    }

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public DbBatchGateway(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.dataSource = dataSource;
    }

    /** pg_cron 의 상태. DB 의 배치 도구를 읽지 못하면 {@link #UNREADABLE}. 트랜잭션 밖에서 읽는다 — 읽기가 실패해도 호출한 쪽의 트랜잭션(PostgreSQL 은 실패하면 그 트랜잭션을 못 쓴다)을 망치지 않게 */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PgCronState state() {
        try {
            String status = jdbc.queryForObject("select batch_pg_cron_status()", String.class);
            String zone = jdbc.queryForObject("select coalesce(nullif(current_setting('cron.timezone', true), ''), 'GMT')", String.class);
            return new PgCronState(status, zone);
        } catch (DataAccessException e) {
            log.warn("pg_cron 상태를 읽지 못했다: {}", e.getMostSpecificCause().getMessage());
            return new PgCronState(UNREADABLE, "GMT");
        }
    }

    /** DB 에 정의된 배치(batch_db_jobs)와 pg_cron 의 등록 상태, 키 → 정보. pg_cron 을 쓸 수 없으면 등록 정보 없이 정의만. 트랜잭션 밖에서 읽는다({@link #state}와 같은 이유) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, DbJob> jobs(boolean cronReady) {
        Map<String, DbJob> jobs = new HashMap<>();
        try {
            if (cronReady) {
                // pg_cron 은 이 DB 의 job 만 본다(database = current_database())
                jdbc.query("select d.job_key, d.default_cron, j.jobid, j.schedule, j.active from batch_db_jobs d "
                        + "left join cron.job j on j.jobname = d.job_key and j.database = current_database()", rs -> {
                    long jobId = rs.getLong(3);
                    boolean scheduled = !rs.wasNull();
                    jobs.put(rs.getString(1), new DbJob(rs.getString(1), rs.getString(2), scheduled ? jobId : null, rs.getString(4), rs.getBoolean(5)));
                });
            } else {
                jdbc.query("select job_key, default_cron from batch_db_jobs", rs -> {
                    jobs.put(rs.getString(1), new DbJob(rs.getString(1), rs.getString(2), null, null, false));
                });
            }
        } catch (DataAccessException e) {
            log.warn("DB 배치 정의를 읽지 못했다: {}", e.getMostSpecificCause().getMessage());
        }
        return jobs;
    }

    /**
     * 켜고 끈다({@code cron.alter_job}). job 이 사라졌으면 {@link DataAccessException}. 트랜잭션 밖에서 바로 반영한다 — 서비스가 곧이어 읽는 목록에 새 값이 보이게
     * (같은 트랜잭션이면 읽기 쪽이 아직 커밋 안 된 값을 못 본다).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void setActive(long cronJobId, boolean active) {
        jdbc.queryForList("select cron.alter_job(?, active => ?)", cronJobId, active);
    }

    /** cron 을 바꾼다({@code cron.alter_job}). pg_cron 이 식을 거절하면 {@link DataAccessException} 이 난다. 트랜잭션 밖에서 바로 반영한다({@link #setActive}와 같은 이유) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void setSchedule(long cronJobId, String schedule) {
        jdbc.queryForList("select cron.alter_job(?, schedule => ?)", cronJobId, schedule);
    }

    /**
     * 앱 시작 때: pg_cron 을 만들 수 있으면 만들고(슈퍼유저 계정일 때) 정의되어 있는데 pg_cron 에 없는 배치를 기본 cron 으로 등록한다 — pg_cron 을 나중에 도입한 설치도 이것으로
     * 따라온다. 이미 있는 job 은 건드리지 않는다. 실패해도 예외를 던지지 않는다(서버는 pg_cron 없이도 떠야 한다).
     */
    public void prepare() {
        try {
            String status = jdbc.queryForObject("select batch_enable_pg_cron()", String.class);
            Integer registered = jdbc.queryForObject("select batch_register_missing()", Integer.class);
            if ("READY".equals(status)) {
                log.info("DB 배치(pg_cron)를 쓸 수 있다 — 새로 등록한 배치 {}개", registered);
            } else {
                log.warn("DB 배치(pg_cron)를 쓸 수 없다 status={} — 화면에 「사용 불가」로 보인다. Java 배치는 영향이 없다 (docs/features/batch-contract.md 「DB 배치」)", status);
            }
        } catch (DataAccessException e) {
            log.warn("DB 배치를 준비하지 못했다 — 화면에 「사용 불가」로 보인다: {}", e.getMostSpecificCause().getMessage());
        }
    }

    /**
     * 「지금 실행」 — 저장 프로시저를 {@code CALL} 하고 끝날 때까지 기다린다. 돌려주는 값은 프로시저가 만든 로그 줄 ID 이고, 같은 배치가 이미 돌고 있으면 프로시저가 아무것도 하지 않고
     * null 을 돌려준다. 새 연결(자동 커밋)에서 돌린다. 어드바이저리 잠금은 프로시저가 끝에서 푼다 — 오류로 끊기면 이 연결의 잠금을 풀고 예외를 그대로 던진다(풀 연결에 잠금이 남지 않게).
     */
    public Long call(BatchCatalog definition, long operatorId) throws SQLException {
        String procedure = definition.procedureName();
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
            try (PreparedStatement statement = connection.prepareStatement("call " + procedure + "('MANUAL', ?, NULL::bigint)")) {
                statement.setLong(1, operatorId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        return null;
                    }
                    long runId = result.getLong(1);
                    return result.wasNull() ? null : runId;
                }
            } catch (SQLException e) {
                unlock(connection, definition.key()); // 프로시저가 끝맺지 못했다 — 쥔 채로 풀에 돌아가지 않게 풀어 준다(정상이면 프로시저가 batch_run_close 에서 이미 풀었다)
                throw e;
            }
        }
    }

    private static void unlock(Connection connection, String jobKey) {
        try (PreparedStatement statement = connection.prepareStatement("select pg_advisory_unlock(?, hashtext(?))")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setString(2, jobKey);
            statement.execute();
        } catch (SQLException e) {
            log.warn("DB 배치 잠금을 풀지 못했다 jobKey={}", jobKey, e);
            try (PreparedStatement all = connection.prepareStatement("select pg_advisory_unlock_all()")) {
                all.execute();
            } catch (SQLException ignored) {
                log.error("DB 배치 잠금을 모두 풀지 못했다 jobKey={}", jobKey);
            }
        }
    }
}
