package com.community.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.batch.core.BatchCatalog;
import com.community.batch.entity.BatchSource;
import com.community.testsupport.CommerceTestDatabase;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 정리 DB 배치 {@code member-withdrawal-cleanup}({@code R__batch_36}, docs/plans/member-withdrawal/00-analysis.md 4절) — 탈퇴 회원 글·댓글의 접속 IP 는 작성 90일이 지난 것만,
 * 탈퇴·반려 사업자 신원의 암호문·등록증 사본은 90일이 지난 것만 지우고 나머지는 남긴다. pg_cron 이 없는 시험 DB 라 프로시저를 {@code CALL} 로 바로 부른다.
 */
class MemberWithdrawalCleanupBatchTest {

    private static final AtomicLong IDS = new AtomicLong(8_600_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);
    /** 글·댓글 ID — posts 는 Snowflake ID 구간으로 분할돼 있어 실제 발급기의 ID 를 쓴다(다른 시험과 겹치지 않는 서버 번호) */
    private static final SnowflakeIdGenerator SNOWFLAKE = new SnowflakeIdGenerator(903);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long id() {
        return IDS.incrementAndGet();
    }

    private static long user(String status) {
        long id = id();
        jdbc.update("INSERT INTO users (id, email, status, terms_version, terms_agreed_at, age_confirmed_at, deleted_at) VALUES (?, ?, ?, 'v1', now(), now(), "
                + "CASE WHEN ? = 'WITHDRAWN' THEN now() ELSE NULL END)", id, status.equals("WITHDRAWN") ? null : "wc-" + id + "@test.local", status, status);
        return id;
    }

    private static long board() {
        long id = id();
        jdbc.update("INSERT INTO boards (id, slug, name, sort_order) VALUES (?, ?, ?, 991)", id, "wc" + Long.toString(id, 36), "정리 시험 " + (id % 1000));
        return id;
    }

    private static long post(long boardId, long author, String age) {
        long id = SNOWFLAKE.nextId();
        jdbc.update("INSERT INTO posts (id, board_id, author_id, author_ip, title, content, created_at, updated_at) VALUES (?, ?, ?, '10.0.0.1', '시험', '본문', now() - ?::interval, "
                + "now() - ?::interval)", id, boardId, author, age, age);
        return id;
    }

    private static long comment(long postId, long author, String age) {
        long id = SNOWFLAKE.nextId();
        jdbc.update("INSERT INTO comments (id, post_id, board_id, author_id, author_ip, content, created_at, updated_at) SELECT ?, p.id, p.board_id, ?, '10.0.0.2', '댓글', "
                + "now() - ?::interval, now() - ?::interval FROM posts p WHERE p.id = ?", id, author, age, age, postId);
        return id;
    }

    private static long business(long userId, String status, String age) {
        long id = id();
        String hash = String.format("%064x", id);
        jdbc.update("INSERT INTO business_profiles (id, user_id, status, company_name, biz_no_enc, biz_no_hash, biz_no_last4, owner_name_enc, phone_enc, email_enc, address1_enc, "
                + "terms_version, privacy_version, agreed_at, submitted_at, reviewed_at, reject_code, reject_text, withdrawn_at, created_at, updated_at) "
                + "VALUES (?, ?, ?, '시험 상호', 'd1:AAAA', ?, '1234', 'd1:AAAA', 'd1:AAAA', 'd1:AAAA', 'd1:AAAA', 'v1', 'v1', now(), now(), "
                + "CASE WHEN ? = 'REJECTED' THEN now() - ?::interval ELSE NULL END, CASE WHEN ? = 'REJECTED' THEN 'OTHER' ELSE NULL END, "
                + "CASE WHEN ? = 'REJECTED' THEN '시험 반려' ELSE NULL END, CASE WHEN ? = 'WITHDRAWN' THEN now() - ?::interval ELSE NULL END, now(), now())",
                id, userId, status, hash, status, age, status, status, status, age);
        jdbc.update("INSERT INTO business_documents (id, user_id, profile_id, kind, file_name, content_type, size_bytes, content, sha256, created_at, attached_at) VALUES (?, ?, ?, "
                + "'CERTIFICATE', 'cert.pdf', 'application/pdf', 3, ?, ?, now(), now())", id(), userId, id, new byte[] {1, 2, 3}, hash);
        return id;
    }

    private static long call() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true); // 프로시저가 COMMIT 한다
            try (CallableStatement call = connection.prepareCall("CALL batch_member_withdrawal_cleanup('MANUAL', NULL, NULL)")) {
                call.execute();
            }
        }
        return jdbc.queryForObject("SELECT id FROM batch_runs WHERE job_key = 'member-withdrawal-cleanup' ORDER BY started_at DESC, id DESC LIMIT 1", Long.class);
    }

    private static String ip(String table, long id) {
        return jdbc.queryForObject("SELECT author_ip FROM " + table + " WHERE id = ?", String.class, id);
    }

    @Test
    void 탈퇴_회원의_90일_지난_글_댓글_IP와_90일_지난_탈퇴_반려_사업자_암호문만_지운다() throws Exception {
        long withdrawn = user("WITHDRAWN");
        long active = user("ACTIVE");
        long boardId = board();
        long oldPost = post(boardId, withdrawn, "91 days");
        long newPost = post(boardId, withdrawn, "10 days");
        long activeOldPost = post(boardId, active, "200 days");
        long oldComment = comment(oldPost, withdrawn, "100 days");
        long newComment = comment(oldPost, withdrawn, "5 days");
        long bizWithdrawnOld = business(user("WITHDRAWN"), "WITHDRAWN", "91 days");
        long bizWithdrawnNew = business(user("WITHDRAWN"), "WITHDRAWN", "30 days");
        long bizRejectedOld = business(user("ACTIVE"), "REJECTED", "120 days");
        long bizApproved = business(user("ACTIVE"), "APPROVED", "400 days");

        long run = call();

        assertThat(ip("posts", oldPost)).isNull();
        assertThat(ip("posts", newPost)).isEqualTo("10.0.0.1"); // 90일 안 — 다음에
        assertThat(ip("posts", activeOldPost)).isEqualTo("10.0.0.1"); // 탈퇴하지 않은 회원 — 이 배치의 몫이 아니다
        assertThat(ip("comments", oldComment)).isNull();
        assertThat(ip("comments", newComment)).isEqualTo("10.0.0.2");

        Map<String, Object> erased = jdbc.queryForMap("SELECT biz_no_enc, biz_no_hash, owner_name_enc, email_enc, erased_at, company_name, biz_no_last4 FROM business_profiles WHERE id = ?",
                bizWithdrawnOld);
        assertThat(erased.get("biz_no_enc")).isNull();
        assertThat(erased.get("biz_no_hash")).isNull();
        assertThat(erased.get("owner_name_enc")).isNull();
        assertThat(erased.get("email_enc")).isNull();
        assertThat(erased.get("erased_at")).isNotNull();
        assertThat(erased.get("company_name")).isEqualTo("시험 상호"); // 상호·끝 4자리는 남는다
        assertThat(erased.get("biz_no_last4")).isEqualTo("1234");
        assertThat(jdbc.queryForMap("SELECT content, erased_at FROM business_documents WHERE profile_id = ?", bizWithdrawnOld)).containsEntry("content", null)
                .hasEntrySatisfying("erased_at", at -> assertThat(at).isNotNull());
        assertThat(jdbc.queryForObject("SELECT erased_at FROM business_profiles WHERE id = ?", Object.class, bizRejectedOld)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT erased_at FROM business_profiles WHERE id = ?", Object.class, bizWithdrawnNew)).isNull();
        assertThat(jdbc.queryForObject("SELECT erased_at FROM business_profiles WHERE id = ?", Object.class, bizApproved)).isNull();
        assertThat(jdbc.queryForObject("SELECT biz_no_enc FROM business_profiles WHERE id = ?", String.class, bizApproved)).isEqualTo("d1:AAAA");

        Map<String, Object> log = jdbc.queryForMap("SELECT status, affected_count FROM batch_runs WHERE id = ?", run);
        assertThat(log.get("status")).isEqualTo("OK");
        assertThat(((Number) log.get("affected_count")).longValue()).isGreaterThanOrEqualTo(6); // 글 1 + 댓글 1 + 사업자 2 + 등록증 2

        long again = call();
        assertThat(jdbc.queryForObject("SELECT status FROM batch_runs WHERE id = ?", String.class, again)).isIn("OK", "SKIP"); // 다른 시험의 행이 있을 수 있다
    }

    @Test
    void 카탈로그는_DB_배치이고_프로시저_이름이_맞다() {
        assertThat(BatchCatalog.MEMBER_WITHDRAWAL_CLEANUP.source()).isEqualTo(BatchSource.POSTGRESQL);
        assertThat(BatchCatalog.MEMBER_WITHDRAWAL_CLEANUP.procedureName()).isEqualTo("batch_member_withdrawal_cleanup");
        assertThat(BatchCatalog.MEMBER_WITHDRAWAL_COMPLETE.source()).isEqualTo(BatchSource.JAVA);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_db_jobs WHERE job_key = 'member-withdrawal-cleanup'", Integer.class)).isEqualTo(1);
    }
}
