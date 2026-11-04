package com.community.analytics.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.time.ServiceTime;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * DB 배치 {@code board-activity-daily}(shared R__batch_38) — 오늘·어제(한국 날짜)만 원천에서 다시 세어 바꾼다. 실제 PostgreSQL 에서 {@code CALL}(시험 DB 에는 pg_cron 이 없다).
 * 게시판·글은 이 시험이 새로 만들고 자기 게시판 줄만 본다. 날 줄(site_activity_daily)은 다른 시험의 글도 들어 있어 직전 실행과의 차이로 본다.
 */
class BoardActivityDailyBatchTest {

    private static final SnowflakeIdGenerator IDS = new SnowflakeIdGenerator(915);
    private static final AtomicLong SEQ = new AtomicLong(System.nanoTime() % 1_000_000L);
    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void off() {
        jdbc.update("UPDATE stats_collection_settings SET board_activity = false WHERE id = 1");
    }

    private static LocalDate today() {
        return ServiceTime.dateOf(Instant.now());
    }

    private static long board(String kind) {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order, kind) VALUES (?, ?, ?, 993, ?)", id, "ba" + Long.toString(id, 36), "활동 시험 " + (id % 100000), kind);
        return id;
    }

    private static long user() {
        long id = IDS.nextId();
        jdbc.update("INSERT INTO users (id, email, status, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, ?, 'ACTIVE', 'v1', now(), now())", id,
                "ba-" + id + "@test.local");
        return id;
    }

    /** 그 날(한국) 정오에 쓴 것으로 보이는 Snowflake ID */
    private static long idOn(LocalDate day) {
        Instant noon = day.atTime(12, 0).atZone(ServiceTime.ZONE).toInstant();
        return jdbc.queryForObject("SELECT board_snowflake_floor(?) + ?", Long.class, Timestamp.from(noon), SEQ.incrementAndGet());
    }

    private static long post(long board, long author, LocalDate day, String status, boolean hidden, boolean deleted) {
        long id = idOn(day);
        jdbc.update("INSERT INTO posts (id, board_id, author_id, title, content, status, hidden_at, deleted_at) VALUES (?, ?, ?, '활동', '본문', ?, ?, ?)", id, board, author,
                status, hidden ? Timestamp.from(Instant.now()) : null, deleted ? Timestamp.from(Instant.now()) : null);
        return id;
    }

    private static void comment(long post, long board, long author, LocalDate day, boolean deleted) {
        jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, content, deleted_at) VALUES (?, ?, ?, ?, '댓글', ?)", idOn(day), post, board, author,
                deleted ? Timestamp.from(Instant.now()) : null);
    }

    /** [posts, comments] — 줄이 없으면 null */
    private static long[] boardDay(LocalDate day, long board) {
        List<long[]> rows = jdbc.query("SELECT posts, comments FROM board_activity_daily WHERE day = ? AND board_id = ?", (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2)},
                Date.valueOf(day), board);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** [posts, comments, signups] */
    private static long[] siteDay(LocalDate day) {
        List<long[]> rows = jdbc.query("SELECT posts, comments, signups FROM site_activity_daily WHERE day = ?",
                (rs, i) -> new long[] {rs.getLong(1), rs.getLong(2), rs.getLong(3)}, Date.valueOf(day));
        return rows.isEmpty() ? new long[3] : rows.get(0);
    }

    private static void run() {
        jdbc.execute("CALL batch_board_activity_daily('MANUAL', NULL, NULL)");
    }

    private static String lastMessage() {
        return jdbc.queryForObject("SELECT message_key FROM batch_runs WHERE job_key = 'board-activity-daily' ORDER BY started_at DESC, id DESC LIMIT 1", String.class);
    }

    @Test
    void 오늘_어제를_공개_게시판의_게시_글과_지워지지_않은_댓글로_세고_여러_번_돌려도_같다() {
        jdbc.update("UPDATE stats_collection_settings SET board_activity = true WHERE id = 1");
        LocalDate today = today();
        LocalDate yesterday = today.minusDays(1);
        run();
        long[] siteBefore = siteDay(today);

        long general = board("GENERAL");
        long group = board("GROUP");
        long author = user();
        long p1 = post(general, author, today, "PUBLISHED", false, false);
        post(general, author, today, "PUBLISHED", true, false); // 숨김 글은 센다
        post(general, author, today, "PUBLISHED", false, true); // 삭제 글은 빼고
        post(general, author, today, "DRAFT", false, false); // 임시저장도 뺀다
        post(general, author, yesterday, "PUBLISHED", false, false);
        comment(p1, general, author, today, false);
        comment(p1, general, author, today, false);
        comment(p1, general, author, today, true); // 삭제 댓글 제외
        long gp = post(group, author, today, "PUBLISHED", false, false); // 소모임 게시판 제외
        comment(gp, group, author, today, false);
        user();

        run();
        assertThat(boardDay(today, general)).containsExactly(2, 2);
        assertThat(boardDay(yesterday, general)).containsExactly(1, 0);
        assertThat(boardDay(today, group)).as("소모임 게시판 줄은 없다").isNull();
        long[] site = siteDay(today);
        assertThat(site[0] - siteBefore[0]).isEqualTo(2);
        assertThat(site[1] - siteBefore[1]).isEqualTo(2);
        assertThat(site[2] - siteBefore[2]).as("오늘 가입(작성자 + 한 명)").isEqualTo(2);

        run();
        run();
        assertThat(boardDay(today, general)).as("멱등 — 다시 세는 것이지 더하는 것이 아니다").containsExactly(2, 2);
        assertThat(siteDay(today)).containsExactly(site);
        assertThat(lastMessage()).isEqualTo("batch.result.boardActivityDaily");
    }

    @Test
    void 그제_줄은_다시_세지_않고_어제_줄은_원천대로_바로잡는다() {
        jdbc.update("UPDATE stats_collection_settings SET board_activity = true WHERE id = 1");
        LocalDate today = today();
        long general = board("GENERAL");
        long author = user();
        post(general, author, today.minusDays(2), "PUBLISHED", false, false);
        post(general, author, today.minusDays(1), "PUBLISHED", false, false);
        jdbc.update("INSERT INTO board_activity_daily (day, board_id, posts, comments) VALUES (?, ?, 99, 99)", Date.valueOf(today.minusDays(2)), general);
        jdbc.update("INSERT INTO board_activity_daily (day, board_id, posts, comments) VALUES (?, ?, 99, 99)", Date.valueOf(today.minusDays(1)), general);

        run();
        assertThat(boardDay(today.minusDays(2), general)).as("그제는 건드리지 않는다").containsExactly(99, 99);
        assertThat(boardDay(today.minusDays(1), general)).containsExactly(1, 0);
    }

    @Test
    void 설정이_꺼져_있으면_세지_않고_SKIP() {
        jdbc.update("UPDATE stats_collection_settings SET board_activity = false WHERE id = 1");
        LocalDate today = today();
        long general = board("GENERAL");
        post(general, user(), today, "PUBLISHED", false, false);
        run();
        assertThat(boardDay(today, general)).isNull();
        assertThat(lastMessage()).isEqualTo("batch.result.boardActivityDaily.off");
        assertThat(jdbc.queryForObject("SELECT status FROM batch_runs WHERE job_key = 'board-activity-daily' ORDER BY started_at DESC, id DESC LIMIT 1", String.class))
                .isEqualTo("SKIP");
    }
}
