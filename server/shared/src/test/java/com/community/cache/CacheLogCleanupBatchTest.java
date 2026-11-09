package com.community.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.testsupport.CommerceTestDatabase;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 캐시 관리 기록 정리 DB 배치 {@code cache-log-cleanup}({@code R__batch_42}, shared V175 표) — 실제 PostgreSQL 에서 프로시저를 {@code CALL} 한다.
 * 갱신 기록 90일 · 설정 변경 이력 365일이 지난 줄과 7일 넘게 보고가 없는 서버 줄만 지우고 캐시 설정은 그대로다. 카탈로그는 DB 배치 · 매일 03:25 KST, 번들 두 언어.
 */
class CacheLogCleanupBatchTest {

    private static final String JOB = "cache-log-cleanup";
    private static final AtomicLong IDS = new AtomicLong(System.currentTimeMillis() * 1000);

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        jdbc = new JdbcTemplate(CommerceTestDatabase.migrated());
    }

    private static Timestamp daysAgo(int days) {
        return Timestamp.from(Instant.now().minus(Duration.ofDays(days)).plus(Duration.ofMinutes(days == 0 ? 0 : 1)));
    }

    private static Timestamp olderThan(int days) {
        return Timestamp.from(Instant.now().minus(Duration.ofDays(days)).minus(Duration.ofMinutes(1)));
    }

    private static long refreshLog(Timestamp at) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO cache_refresh_logs (id, cache_key, trigger_kind, generation, requested_at) VALUES (?, 'home.layout', 'MANUAL', 1, ?)", id, at);
        return id;
    }

    private static long settingChange(String key, Timestamp at) {
        long id = IDS.incrementAndGet();
        if (key == null) {
            jdbc.update("INSERT INTO cache_setting_changes (id, cache_key, kind, enabled, changed_at) VALUES (?, NULL, 'GLOBAL_ENABLED', true, ?)", id, at);
        } else {
            jdbc.update("INSERT INTO cache_setting_changes (id, cache_key, kind, from_seconds, to_seconds, changed_at) VALUES (?, ?, 'TTL', 15, 30, ?)", id, key, at);
        }
        return id;
    }

    private static String node(String tag, Timestamp reportedAt) {
        String id = "test-" + tag + "-" + IDS.incrementAndGet();
        jdbc.update("INSERT INTO cache_node_states (node_id, cache_key, role, applied_generation, applied_enabled, reported_at) VALUES (?, 'point.policy', 'API', 0, true, ?)",
                id, reportedAt);
        return id;
    }

    private static boolean exists(String table, String column, Object id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Integer.class, id) > 0;
    }

    @Test
    void 카탈로그는_DB_배치이고_매일_03시_25분_기간_90_365_7일_번들_두_언어() throws Exception {
        assertThat(BatchCatalog.ofKey(JOB)).isEqualTo(BatchCatalog.CACHE_LOG_CLEANUP);
        assertThat(BatchCatalog.CACHE_LOG_CLEANUP.isJava()).isFalse();
        assertThat(BatchCatalog.CACHE_LOG_CLEANUP.procedureName()).isEqualTo("batch_cache_log_cleanup");
        Map<String, Object> defined = jdbc.queryForMap("SELECT procedure_name, kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(3);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(25);
        assertThat(defined.get("cadence")).isEqualTo("DAILY");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_db_jobs WHERE cadence = 'DAILY' AND kst_hour = 3 AND kst_minute = 25", Integer.class))
                .as("같은 시각의 다른 DB 배치 없음").isEqualTo(1);
        for (String[] p : new String[][] {{"refresh_log_days", "90"}, {"setting_change_days", "365"}, {"node_days", "7"}}) {
            assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = ?", Long.class, JOB, p[0])).isEqualTo(Long.parseLong(p[1]));
        }
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new InputStreamReader(CacheLogCleanupBatchTest.class.getResourceAsStream("/messages/messages_" + lang + ".properties"), StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            assertThat(bundle).containsKeys("batch.job." + JOB + ".name", "batch.job." + JOB + ".desc", "batch.result.cacheLogCleanup", "batch.result.cacheLogCleanup.none");
        }
    }

    @Test
    void 기간이_지난_갱신_기록_설정_변경_이력_보고_끊긴_서버_줄만_지우고_캐시_설정은_그대로() {
        long logOld = refreshLog(olderThan(90));
        long logKept = refreshLog(daysAgo(89));
        long changeOld = settingChange("point.policy", olderThan(365));
        long globalOld = settingChange(null, olderThan(365));
        long changeKept = settingChange("point.policy", daysAgo(364));
        long globalKept = settingChange(null, daysAgo(200));
        String nodeOld = node("old", olderThan(7));
        String nodeKept = node("kept", daysAgo(6));
        Map<String, Object> settingBefore = jdbc.queryForMap("SELECT ttl_seconds, enabled, generation FROM cache_settings WHERE cache_key = 'point.policy'");

        jdbc.execute("CALL batch_cache_log_cleanup('MANUAL', NULL, NULL)");

        assertThat(exists("cache_refresh_logs", "id", logOld)).isFalse();
        assertThat(exists("cache_refresh_logs", "id", logKept)).isTrue();
        assertThat(exists("cache_setting_changes", "id", changeOld)).isFalse();
        assertThat(exists("cache_setting_changes", "id", globalOld)).as("전체 켬/끔 이력도 같은 기간").isFalse();
        assertThat(exists("cache_setting_changes", "id", changeKept)).isTrue();
        assertThat(exists("cache_setting_changes", "id", globalKept)).isTrue();
        assertThat(exists("cache_node_states", "node_id", nodeOld)).isFalse();
        assertThat(exists("cache_node_states", "node_id", nodeKept)).isTrue();
        assertThat(jdbc.queryForMap("SELECT ttl_seconds, enabled, generation FROM cache_settings WHERE cache_key = 'point.policy'")).isEqualTo(settingBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cache_global_settings", Integer.class)).isEqualTo(1);

        Map<String, Object> run = jdbc.queryForMap("SELECT status, affected_count, message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", JOB);
        assertThat(run.get("status")).isEqualTo("OK");
        assertThat(run.get("message_key")).isEqualTo("batch.result.cacheLogCleanup");

        jdbc.execute("CALL batch_cache_log_cleanup('MANUAL', NULL, NULL)");
        assertThat(jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB))
                .as("남은 줄은 대상이 아니다").isEqualTo("batch.result.cacheLogCleanup.none");
        assertThat(exists("cache_refresh_logs", "id", logKept)).isTrue();
    }
}
