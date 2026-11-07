package com.community.batch.repository.master;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * DB 배치(저장 프로시저 + pg_cron)의 DB 문장 — pg_cron 상태, 정의 표({@code batch_db_jobs})와 등록된 job({@code cron.job}) 읽기, {@code cron.alter_job}, 기동 준비 함수.
 * 트랜잭션 경계(읽기·고치기는 트랜잭션 밖 — {@code NOT_SUPPORTED})와 실패 처리(로그·「사용 불가」)는 서비스 {@code DbBatchGateway} 가 정한다.
 *
 * <p>Native SQL 인 이유: {@code cron.job}·{@code cron.alter_job} 은 pg_cron 전용이고 확장이 없는 DB 에서는 엔티티로 매핑할 수 없는 표다. 상태·준비는 DB 함수
 * ({@code batch_pg_cron_status()}·{@code batch_enable_pg_cron()}·{@code batch_register_missing()} — R__batch_00_helpers)다. 값은 모두 바인딩한다.
 * 실패는 스프링 {@code DataAccessException} 으로 올라간다(부르는 쪽이 잡는다).
 */
@Repository
public class PgCronQueries {

    /** 정의 한 줄 + 등록 정보(등록이 없으면 {@code cronJobId}·{@code schedule} 이 null, {@code active} 는 false) */
    public record DbJobRow(String jobKey, String defaultCron, Long cronJobId, String schedule, boolean active) {
    }

    private final JdbcTemplate jdbc;

    public PgCronQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /** pg_cron 상태 코드({@code READY} 또는 쓸 수 없는 이유) */
    public String status() {
        return jdbc.queryForObject("select batch_pg_cron_status()", String.class);
    }

    /** pg_cron 이 cron 식을 해석하는 시간대(설정이 없으면 GMT) */
    public String timezone() {
        return jdbc.queryForObject("select coalesce(nullif(current_setting('cron.timezone', true), ''), 'GMT')", String.class);
    }

    /** 정의와 pg_cron 등록(이 DB 의 job 만 — {@code database = current_database()}) */
    public List<DbJobRow> jobsWithCron() {
        List<DbJobRow> rows = new ArrayList<>();
        jdbc.query("select d.job_key, d.default_cron, j.jobid, j.schedule, j.active from batch_db_jobs d "
                + "left join cron.job j on j.jobname = d.job_key and j.database = current_database()", rs -> {
            long jobId = rs.getLong(3);
            boolean scheduled = !rs.wasNull();
            rows.add(new DbJobRow(rs.getString(1), rs.getString(2), scheduled ? jobId : null, rs.getString(4), rs.getBoolean(5)));
        });
        return rows;
    }

    /** 정의만(pg_cron 을 쓸 수 없을 때 — {@code cron} 스키마를 읽지 않는다) */
    public List<DbJobRow> jobDefinitions() {
        return jdbc.query("select job_key, default_cron from batch_db_jobs", (rs, i) -> new DbJobRow(rs.getString(1), rs.getString(2), null, null, false));
    }

    /** 켜고 끈다({@code cron.alter_job}) — job 이 사라졌으면 예외 */
    public void setActive(long cronJobId, boolean active) {
        jdbc.queryForList("select cron.alter_job(?, active => ?)", cronJobId, active);
    }

    /** cron 을 바꾼다({@code cron.alter_job}) — pg_cron 이 식을 거절하면 예외 */
    public void setSchedule(long cronJobId, String schedule) {
        jdbc.queryForList("select cron.alter_job(?, schedule => ?)", cronJobId, schedule);
    }

    /** pg_cron 을 만들 수 있으면 만든다(슈퍼유저 계정일 때) — 상태 코드 */
    public String enablePgCron() {
        return jdbc.queryForObject("select batch_enable_pg_cron()", String.class);
    }

    /** 정의되어 있는데 pg_cron 에 없는 배치를 기본 cron 으로 등록한다(이미 있는 job 은 그대로) — 새로 등록한 수 */
    public Integer registerMissing() {
        return jdbc.queryForObject("select batch_register_missing()", Integer.class);
    }
}
