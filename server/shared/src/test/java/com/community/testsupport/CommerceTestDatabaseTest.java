package com.community.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 커머스 시험 DB({@link CommerceTestDatabase})가 실제 스키마로 뜨는지, 그리고 그 위에서 <b>지금 DB 가 지키는 포인트 원장 규칙</b>(docs/domain/commerce.md 5-1·5-4·3절)이
 * 실제 PostgreSQL 에서 지켜지는지 본다 — 원장 수정 금지 트리거, 잔액 음수 불가, 「사유 + 대상」 유일.
 */
class CommerceTestDatabaseTest {

    /** 다른 시험 클래스와 같은 DB 를 쓰므로 겹치지 않는 큰 ID 를 쓴다 */
    private static final AtomicLong IDS = new AtomicLong(9_000_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setUp() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    private static long newUser() {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO users (id, terms_version, terms_agreed_at, age_confirmed_at) VALUES (?, 'v1', now(), now())", id);
        return id;
    }

    private static long ledger(long userId, String reason, Long refId) {
        long id = IDS.incrementAndGet();
        jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, balance_after, ref_type, ref_id, created_at) VALUES (?, ?, 'EARN', ?, 10, 10, ?, ?, now())",
                id, userId, reason, refId == null ? null : "POST", refId);
        return id;
    }

    @Test
    void shared_스트림이_끝까지_이관된다() {
        Integer failed = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        String last = jdbc.queryForObject("SELECT max(version::int)::text FROM flyway_schema_history WHERE version ~ '^[0-9]+$'", String.class);

        assertThat(failed).isZero();
        assertThat(Integer.parseInt(last)).isGreaterThanOrEqualTo(56);
        assertThat(CommerceTestDatabase.migrated()).isSameAs(dataSource); // 다시 불러도 같은 DB, 다시 이관하지 않는다
    }

    @Test
    void 포인트_원장은_고칠_수_없다_추가만() {
        long user = newUser();
        long entry = ledger(user, "POST", IDS.incrementAndGet());

        assertThatThrownBy(() -> jdbc.update("UPDATE point_ledger SET delta = 999 WHERE id = ?", entry))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT delta FROM point_ledger WHERE id = ?", Integer.class, entry)).isEqualTo(10);
    }

    @Test
    void 잔액은_음수가_될_수_없다() {
        long user = newUser();
        jdbc.update("INSERT INTO point_balances (user_id, balance, updated_at) VALUES (?, 5, now())", user);

        assertThatThrownBy(() -> jdbc.update("UPDATE point_balances SET balance = balance - 6 WHERE user_id = ?", user)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("SELECT balance FROM point_balances WHERE user_id = ?", Long.class, user)).isEqualTo(5L);
    }

    @Test
    void 같은_사유_같은_대상의_원장은_한_줄뿐이다() {
        long user = newUser();
        long post = IDS.incrementAndGet();
        ledger(user, "POST", post);

        assertThatThrownBy(() -> ledger(user, "POST", post)).isInstanceOf(DataAccessException.class);
        ledger(user, "POST_REVOKE", post); // 사유가 다르면 같은 대상이어도 된다
        ledger(user, "ADMIN_ADJUST", null);
        ledger(user, "ADMIN_ADJUST", null); // 대상이 없는 줄(운영 조정)은 여러 줄이어도 된다
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_ledger WHERE user_id = ?", Integer.class, user)).isEqualTo(4);
    }

    @Test
    void 연결을_따로_여러_개_잡을_수_있다() throws SQLException {
        try (var first = dataSource.getConnection(); var second = dataSource.getConnection()) {
            assertThat(first).isNotSameAs(second);
            assertThat(first.isValid(2) && second.isValid(2)).isTrue();
        }
    }
}
