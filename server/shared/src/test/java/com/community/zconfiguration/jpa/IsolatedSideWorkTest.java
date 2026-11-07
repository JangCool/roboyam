package com.community.zconfiguration.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.testsupport.JpaTestContext;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 세이브포인트로 격리한 덧붙은 일({@link IsolatedSideWork}) — 실제 PostgreSQL + 운영과 같은 공유 엔티티 관리자(JPA 트랜잭션 관리자).
 *
 * <p>트랜잭션 밖에서 부르면 예외 없이 「하지 않음」(fallback)이어야 한다 — 예전에는 확인보다 먼저 {@code flush} 를 불러 공유 엔티티 관리자가
 * {@code TransactionRequiredException} 을 던졌다(설명과 달리 500).
 */
class IsolatedSideWorkTest {

    private static JdbcTemplate jdbc;
    private static IsolatedSideWork isolated;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setUp() {
        jdbc = new JdbcTemplate(JpaTestContext.dataSource());
        jdbc.execute("CREATE TABLE IF NOT EXISTS zz_isolated_side_work (id bigserial PRIMARY KEY, mark text NOT NULL, note text NOT NULL)");
        isolated = new IsolatedSideWork(JpaTestContext.dataSource(), JpaTestContext.entityManager());
        tx = new TransactionTemplate(JpaTestContext.transactions());
    }

    private static String mark() {
        return UUID.randomUUID().toString();
    }

    private static void insert(String mark, String note) {
        jdbc.update("INSERT INTO zz_isolated_side_work (mark, note) VALUES (?, ?)", mark, note);
    }

    private static int rows(String mark, String note) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM zz_isolated_side_work WHERE mark = ? AND note = ?", Integer.class, mark, note);
        return n == null ? 0 : n;
    }

    @Test
    void 트랜잭션_밖이면_예외_없이_일을_하지_않고_fallback() {
        String mark = mark();
        AtomicInteger calls = new AtomicInteger();

        Integer result = isolated.run("test:outside", () -> {
            calls.incrementAndGet();
            insert(mark, "side");
            return 1;
        }, 0);

        assertThat(result).isZero();
        assertThat(calls).hasValue(0);
        assertThat(rows(mark, "side")).isZero();
    }

    @Test
    void 트랜잭션_안이면_일을_하고_업무와_같이_커밋된다() {
        String mark = mark();

        Integer result = tx.execute(status -> {
            insert(mark, "business");
            return isolated.run("test:inside", () -> {
                insert(mark, "side");
                return 1;
            }, 0);
        });

        assertThat(result).isEqualTo(1);
        assertThat(rows(mark, "business")).isEqualTo(1);
        assertThat(rows(mark, "side")).isEqualTo(1);
    }

    @Test
    void 덧붙은_일이_실패하면_그것만_되돌리고_업무는_커밋된다() {
        String mark = mark();

        Integer result = tx.execute(status -> {
            insert(mark, "business");
            Integer side = isolated.run("test:fail", () -> {
                insert(mark, "side");
                jdbc.update("INSERT INTO zz_isolated_side_work (mark, note) VALUES (?, NULL)", mark); // NOT NULL 위반 — 이 문장 뒤 트랜잭션은 세이브포인트로만 살아난다
                return 1;
            }, -1);
            insert(mark, "after");
            return side;
        });

        assertThat(result).isEqualTo(-1);
        assertThat(rows(mark, "business")).isEqualTo(1);
        assertThat(rows(mark, "side")).isZero();
        assertThat(rows(mark, "after")).isEqualTo(1);
    }
}
