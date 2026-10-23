package com.community.batch.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * DB 배치 쪽(반복 마이그레이션) — 매시 등록 도우미 {@code batch_define_hourly}(R__batch_00_helpers.sql)와 보관 정리 {@code batch-log-cleanup}(R__batch_14) 가
 * 실행 요청·단계·실패 건도 정리하는지. 시험 DB 에는 pg_cron 이 없다(등록은 건너뛰어야 한다). 실제 PostgreSQL.
 */
class DbBatchHelpersTest {

    private static BatchTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = new BatchTestKit();
    }

    @Test
    void 매시_도우미는_한국시간_기준_분으로_매시_식을_만들고_pg_cron_이_없으면_등록을_건너뛴다() throws Exception {
        try (var connection = kit.dataSource.getConnection()) {
            JdbcTemplate session = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertThat(session.queryForObject("SELECT batch_hourly_cron(15)", String.class)).as("cron.timezone 없음 = GMT — 정시 단위 시차라 분이 그대로").isEqualTo("15 * * * *");
            session.queryForObject("SELECT set_config('cron.timezone', 'Asia/Seoul', false)", String.class);
            assertThat(session.queryForObject("SELECT batch_hourly_cron(0)", String.class)).isEqualTo("0 * * * *");
            session.queryForObject("SELECT set_config('cron.timezone', 'Asia/Kolkata', false)", String.class);
            assertThat(session.queryForObject("SELECT batch_hourly_cron(15)", String.class)).as("30분 단위 시차(KST-3:30)면 분이 바뀐다").isEqualTo("45 * * * *");
            assertThat(session.queryForObject("SELECT batch_daily_cron(3, 40)", String.class)).as("매일 식은 그대로 시각까지 환산").isEqualTo("10 0 * * *");
            session.queryForObject("SELECT set_config('cron.timezone', '', false)", String.class);
        }

        String key = BatchTestKit.newKey("hourly");
        String procedure = "batch_" + key.replace('-', '_');
        try {
            assertThat(kit.jdbc.queryForObject("SELECT batch_define_hourly(?, ?, 15)", String.class, key, procedure)).isEqualTo("NO_PG_CRON");
            Map<String, Object> row = kit.jdbc.queryForMap("SELECT * FROM batch_db_jobs WHERE job_key = ?", key);
            assertThat(row.get("cadence")).isEqualTo("HOURLY");
            assertThat(row.get("default_cron")).isEqualTo("15 * * * *");
            assertThat(row.get("procedure_name")).isEqualTo(procedure);
            assertThat(kit.jdbc.queryForObject("SELECT batch_register(?)", String.class, key)).isEqualTo("NO_PG_CRON");
            assertThat(kit.jdbc.queryForObject("SELECT batch_define_hourly(?, ?, 20)", String.class, key, procedure)).as("다시 불러도 된다(정의는 갱신)").isEqualTo("NO_PG_CRON");
            assertThat(kit.jdbc.queryForObject("SELECT default_cron FROM batch_db_jobs WHERE job_key = ?", String.class, key)).isEqualTo("20 * * * *");
            assertThat(kit.jdbc.queryForObject("SELECT cadence FROM batch_db_jobs WHERE job_key = 'batch-log-cleanup'", String.class)).as("기존 매일 배치").isEqualTo("DAILY");
        } finally {
            kit.jdbc.update("DELETE FROM batch_db_jobs WHERE job_key = ?", key);
        }
    }

    @Test
    void 배치_로그_정리는_오래된_실행과_끝난_요청을_지우고_단계와_실패_건도_같이_지워진다() {
        String key = BatchTestKit.newKey("cleanup");
        long oldRun = run(key, "120 days");
        kit.jdbc.update("INSERT INTO batch_run_steps (run_id, step_name, step_order, status) VALUES (?, 'main', 1, 'OK')", oldRun);
        long oldFailure = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO batch_run_failures (id, run_id, step_name, target_type, target_id, error_code, message, occurred_at) "
                + "VALUES (?, ?, 'main', 'ZZ_ITEM', '1', 'X', 'x', now() - interval '120 days')", oldFailure, oldRun);
        long recentRun = run(key, "1 day");
        long resumedRun = kit.ids.nextId(); // 오래된 실행을 다시 돌린 최근 실행 — 원래 실행이 지워지면 이어 받은 번호만 비운다
        kit.jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source, run_mode, resumed_from_run_id) "
                + "VALUES (?, ?, 'OK', 'MANUAL', now() - interval '1 day', now() - interval '1 day', 'JAVA', 'RETRY_FAILED', ?)", resumedRun, key, oldRun);
        long oldDone = request(key, "DONE", "120 days", oldRun);
        String pendingKey = BatchTestKit.newKey("cleanup");
        long oldPending = request(pendingKey, "PENDING", "120 days", null);

        try {
            kit.jdbc.execute("CALL batch_batch_log_cleanup('MANUAL', NULL, NULL)");

            assertThat(kit.count("SELECT count(*) FROM batch_runs WHERE id = ?", oldRun)).as("보관 기간(90일)이 지난 실행").isZero();
            assertThat(kit.count("SELECT count(*) FROM batch_run_steps WHERE run_id = ?", oldRun)).as("단계는 CASCADE").isZero();
            assertThat(kit.count("SELECT count(*) FROM batch_run_failures WHERE id = ?", oldFailure)).as("실패 건은 CASCADE").isZero();
            assertThat(kit.count("SELECT count(*) FROM batch_run_requests WHERE id = ?", oldDone)).as("끝난 지 오래된 요청").isZero();
            assertThat(kit.count("SELECT count(*) FROM batch_run_requests WHERE id = ?", oldPending)).as("대기 중 요청은 남긴다").isEqualTo(1);
            assertThat(kit.count("SELECT count(*) FROM batch_runs WHERE id = ?", recentRun)).isEqualTo(1);
            assertThat(kit.jdbc.queryForObject("SELECT resumed_from_run_id FROM batch_runs WHERE id = ?", Long.class, resumedRun)).isNull();
            Map<String, Object> cleanup = kit.jdbc.queryForMap(
                    "SELECT status, affected_count FROM batch_runs WHERE job_key = 'batch-log-cleanup' AND source = 'POSTGRESQL' ORDER BY started_at DESC LIMIT 1");
            assertThat(cleanup.get("status")).isEqualTo("OK");
            assertThat((Long) cleanup.get("affected_count")).isGreaterThanOrEqualTo(2L);
        } finally {
            kit.jdbc.update("UPDATE batch_run_requests SET status = 'REJECTED', started_at = now(), finished_at = now() WHERE id = ?", oldPending);
        }
    }

    private static long run(String key, String age) {
        long id = kit.ids.nextId();
        kit.jdbc.update("INSERT INTO batch_runs (id, job_key, status, trigger_type, started_at, finished_at, source) "
                + "VALUES (?, ?, 'OK', 'MANUAL', now() - ?::interval, now() - ?::interval, 'JAVA')", id, key, age, age);
        return id;
    }

    private static long request(String key, String status, String age, Long runId) {
        long id = kit.ids.nextId();
        boolean finished = !status.equals("PENDING");
        kit.jdbc.update("INSERT INTO batch_run_requests (id, job_key, kind, status, run_id, requested_at, started_at, finished_at) "
                        + "VALUES (?, ?, 'NOW', ?, ?, now() - ?::interval, " + (finished ? "now() - ?::interval, now() - ?::interval" : "NULL, NULL") + ")",
                finished ? new Object[] {id, key, status, runId, age, age, age} : new Object[] {id, key, status, runId, age});
        return id;
    }
}
