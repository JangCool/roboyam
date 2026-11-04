package com.community.analytics.forget;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.analytics.dto.VisitForget;
import com.community.analytics.store.AnalyticsDatabase;
import com.community.analytics.store.VisitForgetStore;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DB 크론 {@code R__analytics_13}({@code batch_analytics_forget_requests}) · {@code R__analytics_14}({@code batch_analytics_log_cleanup})를 실제 PostgreSQL 에서 직접 부른다.
 * 원본은 시험 전용 월 조각 둘(2098년 1·2월 — 이름이 {@code analytics_page_views_YYYY_MM} 모양이 아니라 조각 크론이 건드리지 않는다)에 넣는다.
 *
 * <p>크론은 표 전체의 대기 요청을 처리하므로 시험마다 넣은 요청·줄을 지운다(요청 받기 시험 {@code VisitForgetRequestTest} 도 같다).
 */
class VisitForgetCronTest {

    static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(915);
    private static final String JAN = "qa_forget_pv_2098_01";
    private static final String FEB = "qa_forget_pv_2098_02";
    private static final Instant JAN_AT = Instant.parse("2098-01-10T03:00:00Z");
    private static final Instant FEB_AT = Instant.parse("2098-02-10T03:00:00Z");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private final List<Long> requests = new ArrayList<>();
    private final List<String> hashes = new ArrayList<>();
    private final List<String> instances = new ArrayList<>();

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated("api", "analytics");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + JAN + " PARTITION OF analytics_page_views FOR VALUES FROM ('2098-01-01 00:00:00+09') TO ('2098-02-01 00:00:00+09')");
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + FEB + " PARTITION OF analytics_page_views FOR VALUES FROM ('2098-02-01 00:00:00+09') TO ('2098-03-01 00:00:00+09')");
    }

    @AfterAll
    static void dropPartitions() {
        jdbc.execute("DROP TABLE IF EXISTS " + JAN);
        jdbc.execute("DROP TABLE IF EXISTS " + FEB);
        jdbc.execute("DROP FUNCTION IF EXISTS qa_forget_fail() CASCADE");
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("DROP TRIGGER IF EXISTS qa_forget_fail ON " + FEB);
        if (!requests.isEmpty()) {
            jdbc.update("DELETE FROM analytics_forget_requests WHERE id = ANY(?::bigint[])", array(requests.stream().map(String::valueOf).toList()));
        }
        if (!hashes.isEmpty()) {
            String a = array(hashes);
            jdbc.update("DELETE FROM analytics_page_views WHERE visitor_hash = ANY(?::text[])", a);
            jdbc.update("DELETE FROM analytics_visit_marks WHERE visitor_hash = ANY(?::text[])", a);
            jdbc.update("DELETE FROM analytics_visit_active WHERE visitor_hash = ANY(?::text[])", a);
        }
        for (String i : instances) {
            jdbc.update("DELETE FROM analytics_collector_failures WHERE instance = ?", i);
            jdbc.update("DELETE FROM analytics_collector_hourly WHERE instance = ?", i);
            jdbc.update("DELETE FROM analytics_collector_beats WHERE instance = ?", i);
        }
    }

    private static String array(List<String> values) {
        return "{" + String.join(",", values) + "}";
    }

    private String visitor() {
        String h = "qa-fg-" + UUID.randomUUID().toString().replace("-", "");
        hashes.add(h);
        return h;
    }

    /** 원본 n 줄 */
    private void views(String hash, Instant at, int n) {
        for (int i = 0; i < n; i++) {
            jdbc.update("INSERT INTO analytics_page_views (id, viewed_at, visitor_hash, session_hash, path, kind, device, new_visitor) VALUES (?, ?, ?, ?, '/qa', 'OTHER', "
                    + "'DESKTOP', false)", IDS.nextId(), Timestamp.from(at.plusSeconds(i)), hash, "qa-session");
        }
    }

    private void mark(String hash, LocalDate day) {
        jdbc.update("INSERT INTO analytics_visit_marks (day, visitor_hash, new_visitor) VALUES (?, ?, false)", Date.valueOf(day), hash);
    }

    private void active(String hash) {
        jdbc.update("INSERT INTO analytics_visit_active (visitor_hash, last_seen) VALUES (?, now())", hash);
    }

    /** 요청 한 줄 — 받은 지 {@code ago} */
    private long request(String hash, Duration ago) {
        long id = IDS.nextId();
        requests.add(id);
        jdbc.update("INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status) VALUES (?, ?, now() - ?::interval, 'PENDING')", id, hash,
                ago.toSeconds() + " seconds");
        return id;
    }

    record Req(String status, String hash, Long deletedRows, boolean processed) {
    }

    private static Req req(long id) {
        return jdbc.queryForObject("SELECT status, visitor_hash, deleted_rows, processed_at IS NOT NULL FROM analytics_forget_requests WHERE id = ?",
                (rs, i) -> new Req(rs.getString(1), rs.getString(2), (Long) rs.getObject(3, Long.class), rs.getBoolean(4)), id);
    }

    /** [원본, 판정 표시, 지금 접속] 줄 수 */
    private static long[] rows(String hash) {
        return new long[] {jdbc.queryForObject("SELECT count(*) FROM analytics_page_views WHERE visitor_hash = ?", Long.class, hash),
                jdbc.queryForObject("SELECT count(*) FROM analytics_visit_marks WHERE visitor_hash = ?", Long.class, hash),
                jdbc.queryForObject("SELECT count(*) FROM analytics_visit_active WHERE visitor_hash = ?", Long.class, hash)};
    }

    private static void forgetCron() {
        jdbc.execute("CALL batch_analytics_forget_requests('MANUAL', NULL, NULL)");
    }

    /** 마지막 실행 [상태, 처리 건수, 문구 인자] */
    private static List<String> lastRun(String job) {
        return jdbc.queryForObject("SELECT status, affected_count, coalesce(message_args, ''), coalesce(error_message, '') FROM batch_runs WHERE job_key = ? "
                        + "ORDER BY started_at DESC, id DESC LIMIT 1",
                (rs, i) -> List.of(rs.getString(1), String.valueOf(rs.getObject(2)), rs.getString(3), rs.getString(4)), job);
    }

    private void someoneWithData(String hash) {
        views(hash, JAN_AT, 3);
        views(hash, FEB_AT, 2);
        mark(hash, LocalDate.of(2098, 1, 10));
        mark(hash, LocalDate.of(2098, 1, 11));
        active(hash);
    }

    // ── R__analytics_13 — 삭제 요청 처리 ────────────────────────────────────

    @Test
    void 받은_지_2분_넘은_요청의_해시만_원본_여러_조각과_판정_지금_접속에서_지우고_DONE_해시_NULL() {
        String target = visitor();
        String other = visitor();
        String tooNew = visitor();
        someoneWithData(target);
        someoneWithData(other);
        someoneWithData(tooNew);
        long done = request(target, Duration.ofMinutes(3));
        long waiting = request(tooNew, Duration.ofSeconds(100)); // 2분이 안 됐다

        forgetCron();

        assertThat(rows(target)).containsExactly(0, 0, 0);
        assertThat(rows(other)).as("다른 방문자는 그대로").containsExactly(5, 2, 1);
        assertThat(rows(tooNew)).containsExactly(5, 2, 1);
        assertThat(req(done)).isEqualTo(new Req("DONE", null, 8L, true));
        assertThat(req(waiting)).isEqualTo(new Req("PENDING", tooNew, null, false));
        assertThat(lastRun("analytics-forget-requests").subList(0, 2)).containsExactly("OK", "1");
        assertThat(lastRun("analytics-forget-requests").get(2)).contains("\"1\"", "\"8\"");

        // 두 번 돌려도 같다 — 처리한 요청은 그대로, 대기 요청은 아직 2분 전
        forgetCron();
        assertThat(req(done)).isEqualTo(new Req("DONE", null, 8L, true));
        assertThat(req(waiting)).isEqualTo(new Req("PENDING", tooNew, null, false));
        assertThat(rows(other)).containsExactly(5, 2, 1);
        assertThat(lastRun("analytics-forget-requests").subList(0, 2)).containsExactly("SKIP", "0");
    }

    @Test
    void 한_번에_여러_요청이면_요청마다_지운_줄을_따로_더하고_기록이_없던_요청은_0() {
        String a = visitor();
        String b = visitor();
        String empty = visitor();
        views(a, JAN_AT, 4);
        views(b, FEB_AT, 1);
        mark(b, LocalDate.of(2098, 2, 10));
        long ra = request(a, Duration.ofMinutes(10));
        long rb = request(b, Duration.ofMinutes(9));
        long re = request(empty, Duration.ofMinutes(8));

        forgetCron();

        assertThat(req(ra)).isEqualTo(new Req("DONE", null, 4L, true));
        assertThat(req(rb)).isEqualTo(new Req("DONE", null, 2L, true));
        assertThat(req(re)).isEqualTo(new Req("DONE", null, 0L, true));
        assertThat(lastRun("analytics-forget-requests").subList(0, 2)).containsExactly("OK", "3");
    }

    @Test
    void 한_번에_500개까지_오래된_차례로_처리하고_남은_것은_다음_실행() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            ids.add(request(visitor(), Duration.ofMinutes(600).minusSeconds(i))); // i 가 클수록 최근
        }
        forgetCron();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE id = ANY(?::bigint[]) AND status = 'DONE'", Long.class,
                array(ids.stream().map(String::valueOf).toList()))).isEqualTo(500);
        assertThat(req(ids.get(500)).status()).as("가장 최근 하나가 남는다").isEqualTo("PENDING");
        assertThat(req(ids.get(0)).status()).isEqualTo("DONE");
        assertThat(lastRun("analytics-forget-requests").subList(0, 2)).containsExactly("OK", "500");

        forgetCron();
        assertThat(req(ids.get(500)).status()).isEqualTo("DONE");
        assertThat(lastRun("analytics-forget-requests").subList(0, 2)).containsExactly("OK", "1");
    }

    @Test
    void 중간에_실패하면_대기로_남고_다음_실행이_이어서_지우며_지운_줄은_두_번_세지_않는다() {
        String target = visitor();
        String other = visitor();
        someoneWithData(target);
        someoneWithData(other);
        long id = request(target, Duration.ofMinutes(5));
        jdbc.execute("CREATE OR REPLACE FUNCTION qa_forget_fail() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'qa — 조각 지우기 실패'; END $$");
        jdbc.execute("CREATE TRIGGER qa_forget_fail BEFORE DELETE ON " + FEB + " FOR EACH ROW EXECUTE FUNCTION qa_forget_fail()");

        forgetCron();

        Req failed = req(id);
        assertThat(failed.status()).isEqualTo("PENDING");
        assertThat(failed.hash()).isEqualTo(target);
        assertThat(failed.processed()).isFalse();
        assertThat(failed.deletedRows()).as("1월 조각(먼저 만든 조각)은 지우고 셌다").isEqualTo(3L);
        assertThat(rows(target)).as("2월 조각 · 판정 · 지금 접속은 남았다").containsExactly(2, 2, 1);
        List<String> run = lastRun("analytics-forget-requests");
        assertThat(run.get(0)).isEqualTo("FAIL");
        assertThat(run.get(3)).contains("qa");

        jdbc.execute("DROP TRIGGER qa_forget_fail ON " + FEB);
        forgetCron();

        assertThat(req(id)).isEqualTo(new Req("DONE", null, 8L, true));
        assertThat(rows(target)).containsExactly(0, 0, 0);
        assertThat(rows(other)).containsExactly(5, 2, 1);
    }

    @Test
    void 요청_받기부터_크론까지_같은_해시로_지운다() {
        // 공개 API 와 같은 길 — 서비스가 만든 해시가 수집기 해시와 같아야 지워진다
        VisitForgetService svc = new VisitForgetService(new VisitForgetStore(new AnalyticsDatabase(dataSource, false)), VisitForgetRequestTest.INDEX, IDS);
        UUID visitor = UUID.randomUUID();
        String hash = VisitForgetRequestTest.INDEX.hash(com.community.analytics.forget.VisitForgetService.VISITOR, visitor.toString());
        assertThat(hash).isEqualTo(VisitForgetRequestTest.INDEX.hash(com.community.zconfiguration.crypto.DataPurpose.of("analytics.visitor"), visitor.toString()));
        hashes.add(hash);
        someoneWithData(hash);
        VisitForget.Response r = svc.submit(new VisitForget.Request(visitor.toString()));
        long id = Long.parseLong(r.requestId());
        requests.add(id);
        jdbc.update("UPDATE analytics_forget_requests SET received_at = received_at - interval '3 minutes' WHERE id = ?", id);

        forgetCron();

        assertThat(req(id)).isEqualTo(new Req("DONE", null, 8L, true));
        assertThat(rows(hash)).containsExactly(0, 0, 0);
    }

    // ── R__analytics_14 — 진단 7일 · 처리 끝난 요청 1년 ─────────────────────

    @Test
    void 로그_정리는_진단_7일과_처리_끝난_요청_1년_경계로_지우고_대기_요청은_남긴다() {
        String oldI = "qa-clean-old-" + UUID.randomUUID();
        String newI = "qa-clean-new-" + UUID.randomUUID();
        instances.add(oldI);
        instances.add(newI);
        jdbc.update("INSERT INTO analytics_collector_failures (failed_at, instance, reason, detail) VALUES (now() - interval '7 days 10 minutes', ?, 'UNKNOWN', 'qa')", oldI);
        jdbc.update("INSERT INTO analytics_collector_failures (failed_at, instance, reason, detail) VALUES (now() - interval '6 days 23 hours 50 minutes', ?, 'UNKNOWN', 'qa')",
                newI);
        jdbc.update("INSERT INTO analytics_collector_hourly (hour, instance, flush_ok) VALUES (date_trunc('hour', now() - interval '7 days 1 hour'), ?, 1)", oldI);
        jdbc.update("INSERT INTO analytics_collector_hourly (hour, instance, flush_ok) VALUES (date_trunc('hour', now() - interval '6 days 22 hours'), ?, 1)", newI);
        jdbc.update("INSERT INTO analytics_collector_beats (instance, last_write_at) VALUES (?, now() - interval '7 days 10 minutes')", oldI);
        jdbc.update("INSERT INTO analytics_collector_beats (instance, last_write_at) VALUES (?, now() - interval '6 days 23 hours 50 minutes')", newI);

        long oldDone = IDS.nextId();
        long newDone = IDS.nextId();
        long oldPending = IDS.nextId();
        requests.addAll(List.of(oldDone, newDone, oldPending));
        jdbc.update("INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status, deleted_rows, processed_at) "
                + "VALUES (?, NULL, now() - interval '366 days', 'DONE', 3, now() - interval '366 days')", oldDone);
        jdbc.update("INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status, deleted_rows, processed_at) "
                + "VALUES (?, NULL, now() - interval '364 days', 'DONE', 3, now() - interval '364 days')", newDone);
        // 대기 요청은 오래돼도 지우지 않는다(처리 크론이 2분 뒤 가져가므로 처리 크론은 여기서 부르지 않는다)
        String pendingHash = visitor();
        jdbc.update("INSERT INTO analytics_forget_requests (id, visitor_hash, received_at, status) VALUES (?, ?, now() - interval '400 days', 'PENDING')", oldPending,
                pendingHash);

        jdbc.execute("CALL batch_analytics_log_cleanup('MANUAL', NULL, NULL)");

        Map<String, Long> left = Map.of(
                "failOld", count("analytics_collector_failures", oldI), "failNew", count("analytics_collector_failures", newI),
                "hourOld", count("analytics_collector_hourly", oldI), "hourNew", count("analytics_collector_hourly", newI),
                "beatOld", count("analytics_collector_beats", oldI), "beatNew", count("analytics_collector_beats", newI));
        assertThat(left).containsExactlyInAnyOrderEntriesOf(Map.of("failOld", 0L, "failNew", 1L, "hourOld", 0L, "hourNew", 1L, "beatOld", 0L, "beatNew", 1L));
        assertThat(exists(oldDone)).isFalse();
        assertThat(exists(newDone)).isTrue();
        assertThat(exists(oldPending)).isTrue();
        List<String> run = lastRun("analytics-log-cleanup");
        assertThat(run.get(0)).isEqualTo("OK");
        assertThat(Long.parseLong(run.get(1))).isGreaterThanOrEqualTo(4);

        // 두 번째는 지울 것이 없다(내 줄 기준)
        jdbc.execute("CALL batch_analytics_log_cleanup('MANUAL', NULL, NULL)");
        assertThat(exists(newDone)).isTrue();
        assertThat(count("analytics_collector_failures", newI)).isEqualTo(1);
    }

    private static long count(String table, String instance) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE instance = ?", Long.class, instance);
    }

    private static boolean exists(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM analytics_forget_requests WHERE id = ?", Long.class, id) > 0;
    }
}
