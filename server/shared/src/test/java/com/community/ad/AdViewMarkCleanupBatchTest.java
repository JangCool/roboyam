package com.community.ad;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.testsupport.CommerceTestDatabase;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 공개 광고 하루 1번 판정 표 정리 DB 배치 {@code ad-view-mark-cleanup}({@code R__batch_33}, V118 — ad-contract 「공개 노출 목업」 확정 2026-11-01) — 실제 PostgreSQL 에서
 * 프로시저를 {@code CALL} 한다. 한국 날짜로 오늘·어제 줄은 남기고 2일 이상 지난 줄만 지우며 일별 집계는 그대로다. 카탈로그는 DB 배치 · 매일 04:15 KST · 보관 2일, 번들 두 언어.
 */
class AdViewMarkCleanupBatchTest {

    private static final String JOB = "ad-view-mark-cleanup";

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        DataSource dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private void mark(String hash, int daysAgo) {
        jdbc.update("INSERT INTO ad_view_marks (day, visitor_hash, kind, application_id, version, placement) "
                + "VALUES ((now() AT TIME ZONE 'Asia/Seoul')::date - ?, ?, 'I', 1, 1, 'HOME')", daysAgo, hash);
    }

    private boolean exists(String hash) {
        return jdbc.queryForObject("SELECT count(*) FROM ad_view_marks WHERE visitor_hash = ?", Integer.class, hash) > 0;
    }

    private static String hash(String tag) {
        return (tag + "0".repeat(64)).substring(0, 64);
    }

    @Test
    void 카탈로그는_DB_배치이고_매일_04시_15분_보관_2일_번들_두_언어() throws Exception {
        assertThat(BatchCatalog.ofKey(JOB)).isEqualTo(BatchCatalog.AD_VIEW_MARK_CLEANUP);
        assertThat(BatchCatalog.AD_VIEW_MARK_CLEANUP.isJava()).isFalse();
        assertThat(BatchCatalog.AD_VIEW_MARK_CLEANUP.procedureName()).isEqualTo("batch_ad_view_mark_cleanup");
        Map<String, Object> defined = jdbc.queryForMap("SELECT procedure_name, kst_hour, kst_minute, cadence FROM batch_db_jobs WHERE job_key = ?", JOB);
        assertThat(((Number) defined.get("kst_hour")).intValue()).isEqualTo(4);
        assertThat(((Number) defined.get("kst_minute")).intValue()).isEqualTo(15);
        assertThat(defined.get("cadence")).isEqualTo("DAILY");
        assertThat(jdbc.queryForObject("SELECT param_value FROM batch_params WHERE job_key = ? AND param_key = 'retention_days'", Long.class, JOB)).isEqualTo(2L);
        for (String lang : new String[] {"ko", "en"}) {
            Properties bundle = new Properties();
            try (var in = new java.io.InputStreamReader(AdViewMarkCleanupBatchTest.class.getResourceAsStream("/messages/messages_" + lang + ".properties"),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                bundle.load(in);
            }
            assertThat(bundle).containsKeys("batch.job." + JOB + ".name", "batch.job." + JOB + ".desc", "batch.result.adViewMarkCleanup",
                    "batch.result.adViewMarkCleanup.none");
        }
    }

    @Test
    void 한국_날짜로_오늘_어제는_남기고_2일_지난_줄만_지운다() {
        String tag = Long.toString(System.nanoTime(), 16);
        String today = hash("a" + tag);
        String yesterday = hash("b" + tag);
        String twoDays = hash("c" + tag);
        String old = hash("d" + tag);
        mark(today, 0);
        mark(yesterday, 1);
        mark(twoDays, 2);
        mark(old, 10);

        jdbc.execute("CALL batch_ad_view_mark_cleanup('MANUAL', NULL, NULL)");

        assertThat(exists(today)).isTrue();
        assertThat(exists(yesterday)).isTrue();
        assertThat(exists(twoDays)).isFalse();
        assertThat(exists(old)).isFalse();
        assertThat(jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = ? ORDER BY started_at DESC, id DESC LIMIT 1", String.class, JOB))
                .isEqualTo("batch.result.adViewMarkCleanup");
    }
}
