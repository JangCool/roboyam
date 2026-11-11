package com.community.analytics.collect;

import static com.community.analytics.collect.AnalyticsTestSupport.ALL_ON;
import static com.community.analytics.collect.AnalyticsTestSupport.IDS;
import static com.community.analytics.collect.AnalyticsTestSupport.INDEX;
import static com.community.analytics.collect.AnalyticsTestSupport.hash;
import static com.community.analytics.collect.AnalyticsTestSupport.settings;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.AnalyticsWriteStore;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 방문 원본 월 조각(analytics R__analytics_10 · VisitFlusher 「조각 없음 23514」) — 실제 PostgreSQL. 조각은 한국 달 경계, 보관 개월 2·3·6·12(이번 달 포함, 설정 표 V193 —
 * 3단계 결정 ①: 「이번 달부터 n번째 앞 달」보다 오래된 조각만 DROP, 줄 DELETE 없음).
 * 설정 행(stats_collection_settings id = 1)은 시험마다 바꾸고 끝나면 기본값(모두 꺼짐 · 2개월)으로 되돌린다.
 */
class PageViewPartitionTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated("api", "analytics");
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void restore() {
        jdbc.update("UPDATE stats_collection_settings SET enabled = false, visits = false, raw_events = false, raw_retention_months = 2, referrers = false, flows = false, "
                + "notification_sends = false, board_activity = false WHERE id = 1");
        jdbc.queryForObject("SELECT analytics_ensure_page_view_partitions(1)", Integer.class);
    }

    private static String partition(YearMonth month) {
        return String.format("analytics_page_views_%04d_%02d", month.getYear(), month.getMonthValue());
    }

    private static boolean exists(YearMonth month) {
        return jdbc.queryForObject("SELECT to_regclass(?) IS NOT NULL", Boolean.class, partition(month));
    }

    private static Instant start(YearMonth month) {
        return month.atDay(1).atStartOfDay(ServiceTime.ZONE).toInstant();
    }

    /** 없으면 그 달(한국 달 경계) 조각을 만든다 */
    private static void ensure(YearMonth month) {
        if (!exists(month)) {
            jdbc.execute("CREATE TABLE " + partition(month) + " PARTITION OF analytics_page_views FOR VALUES FROM ('" + start(month) + "') TO ('"
                    + start(month.plusMonths(1)) + "')");
        }
    }

    private static long row(Instant at) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO analytics_page_views (id, viewed_at, visitor_hash, session_hash, path, kind, device, new_visitor) VALUES (?, ?, ?, ?, '/qa', 'OTHER', "
                + "'DESKTOP', false)", id, Timestamp.from(at), hash(), hash());
        return id;
    }

    private static boolean rowExists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_page_views WHERE id = ?", Long.class, id) > 0;
    }

    private static void settingsRow(boolean enabled, boolean raw, int months) {
        jdbc.update("UPDATE stats_collection_settings SET enabled = ?, raw_events = ?, raw_retention_months = ? WHERE id = 1", enabled, raw, months);
    }

    private static String lastMessage() {
        return jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = 'analytics-page-view-partitions' ORDER BY started_at DESC, id DESC LIMIT 1",
                String.class);
    }

    private static YearMonth thisMonth() {
        return YearMonth.from(LocalDate.now(ServiceTime.ZONE));
    }

    /** 다음 달 조각을 떼어 낸다(비어 있을 때만 — 시험 DB 에서 다음 달 원본은 없다) */
    private static void dropNextMonth() {
        YearMonth next = thisMonth().plusMonths(1);
        if (exists(next)) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + partition(next), Long.class)).isZero();
            jdbc.execute("DROP TABLE " + partition(next));
        }
        assertThat(exists(next)).isFalse();
    }

    // ── 원본 쓰기: 조각이 없으면 만들고 다시 쓴다 ───────────────────────────

    @Test
    void 그_달_조각이_없으면_23514_를_받고_조각을_만든_뒤_다시_쓴다() {
        dropNextMonth();
        YearMonth next = thisMonth().plusMonths(1);
        Instant at = start(next).plus(Duration.ofHours(1));
        VisitCollector collector = new VisitCollector(settings(ALL_ON), INDEX, IDS);
        AnalyticsDatabase db = new AnalyticsDatabase(dataSource, false);
        VisitFlusher flusher = new VisitFlusher(collector, new AnalyticsWriteStore(db), db);
        long id = IDS.nextId();
        String path = "/qa/partition/" + id;
        collector.buffer().raw(new VisitBuffer.RawView(id, at, hash(), hash(), path, "OTHER", null, null, "DIRECT", null, null, null, "DESKTOP", true));
        collector.buffer().page(ServiceTime.dateOf(at), path, "OTHER"); // 3단계: 페이지별은 원본과 따로 쌓고 따로 쓴다(buffer.page)

        flusher.flush();

        assertThat(exists(next)).isTrue();
        assertThat(rowExists(id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_page_views WHERE id = ?", Long.class, id)).as("다시 쓴 원본도 한 줄").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT views FROM analytics_page_daily WHERE day = ? AND path = ? AND kind = 'OTHER'", Long.class,
                Date.valueOf(ServiceTime.dateOf(at)), path)).as("원본을 다시 써도 따로 쓰는 페이지별 조회는 한 번만").isEqualTo(1);
        assertThat(collector.buffer().drain().isEmpty()).isTrue();
    }

    // ── 크론: 보관 개월 경계(달 단위) ────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 6, 12})
    void 보관_기간이_다_지난_달_조각만_떼어_지우고_남길_달의_줄은_지우지_않는다(int months) {
        settingsRow(true, true, months);
        YearMonth keepFrom = thisMonth().minusMonths(months - 1); // 남길 첫 달 = 이번 달부터 n번째 앞 달(이번 달 포함 n개월)
        YearMonth expired = keepFrom.minusMonths(1);
        ensure(expired);
        ensure(keepFrom);
        long old = row(start(expired).plus(Duration.ofDays(3)));
        long lastMinutes = row(start(keepFrom).minus(Duration.ofMinutes(10))); // 지울 달의 마지막 10분
        long firstMinutes = row(start(keepFrom).plus(Duration.ofMinutes(10))); // 남길 달의 첫 10분 — 일 단위로 세면 오래됐어도 남는다
        long middle = row(start(keepFrom).plus(Duration.ofDays(14)));

        jdbc.execute("CALL batch_analytics_page_view_partitions('MANUAL', NULL, NULL)");

        assertThat(exists(expired)).as("끝이 남길 첫 달 앞인 조각 %s 는 통째로 지운다", partition(expired)).isFalse();
        assertThat(rowExists(old)).isFalse();
        assertThat(rowExists(lastMinutes)).as("지울 달의 마지막 10분 줄").isFalse();
        assertThat(exists(keepFrom)).as("남길 첫 달 조각 %s 는 남는다", partition(keepFrom)).isTrue();
        assertThat(rowExists(firstMinutes)).as("남길 달 첫 10분 줄 — 줄 DELETE 없음").isTrue();
        assertThat(rowExists(middle)).isTrue();
        assertThat(exists(thisMonth())).isTrue();
        assertThat(lastMessage()).isEqualTo("batch.result.analyticsRawMonths");
    }

    // ── 크론: 설정이 꺼지면 조각 만들기를 건너뛴다 ──────────────────────────

    @Test
    void 원본_기록이_꺼져_있으면_조각을_만들지_않고_켜면_만든다() {
        YearMonth next = thisMonth().plusMonths(1);

        settingsRow(true, false, 3); // 방문 수집은 켜졌지만 원본 기록은 꺼짐
        dropNextMonth();
        jdbc.execute("CALL batch_analytics_page_view_partitions('MANUAL', NULL, NULL)");
        assertThat(exists(next)).isFalse();

        settingsRow(false, true, 3); // 원본 기록은 켜졌지만 방문 수집 전체가 꺼짐
        jdbc.execute("CALL batch_analytics_page_view_partitions('MANUAL', NULL, NULL)");
        assertThat(exists(next)).isFalse();

        settingsRow(true, true, 3);
        jdbc.execute("CALL batch_analytics_page_view_partitions('MANUAL', NULL, NULL)");
        assertThat(exists(next)).isTrue();
        assertThat(exists(thisMonth())).isTrue();
    }
}
