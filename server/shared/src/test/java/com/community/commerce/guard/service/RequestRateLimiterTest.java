package com.community.commerce.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.exception.RequestRateLimitedException;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 돈 요청 속도 제한(docs/domain/commerce.md 12-4, commerce-foundation-contract 「0-5」) — 실제 PostgreSQL. 한도 표({@code request_rate_limits})는 모든 시험이 같이 쓰므로
 * 바꾼 값은 시험 끝에 기본값으로 되돌리고, 사본({@link RequestRateLimits})은 시험마다 새로 만든다. 회원 ID 는 시험마다 새로.
 */
class RequestRateLimiterTest {

    private static final AtomicLong USERS = new AtomicLong(7_200_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private MutableTestClock clock;
    private RequestRateLimits limits;
    private RequestRateLimiter limiter;
    private long userId;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        transactions = new DataSourceTransactionManager(dataSource);
        // 창 안의 45초 — 다음 창까지 15초
        clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(45));
        limits = new RequestRateLimits(dataSource);
        limiter = new RequestRateLimiter(dataSource, transactions, limits, clock);
        userId = USERS.incrementAndGet();
    }

    @AfterEach
    void restoreDefaults() {
        for (RequestAction action : RequestAction.values()) {
            jdbc.update("UPDATE request_rate_limits SET limit_per_minute = ? WHERE action = ?", action.defaultLimit(), action.name());
        }
    }

    private void setLimit(RequestAction action, int limit) {
        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = ? WHERE action = ?", limit, action.name());
        limits.invalidate();
    }

    private Integer counted(RequestAction action) {
        List<Integer> rows = jdbc.queryForList("SELECT count FROM request_rate_counters WHERE user_id = ? AND action = ?", Integer.class, userId, action.name());
        return rows.stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    void 기본_한도는_공구_참여_5_충전_3_상점_구매_10_환불_신청_3이고_V62_가_넣은_값과_같다() {
        Map<String, Object> rows = new java.util.HashMap<>();
        jdbc.query("SELECT action, limit_per_minute FROM request_rate_limits", rs -> {
            rows.put(rs.getString(1), rs.getInt(2));
        });
        assertThat(rows).containsEntry("GROUPBUY_JOIN", 5).containsEntry("POINT_CHARGE", 3).containsEntry("POINT_SHOP_PURCHASE", 10).containsEntry("REFUND_REQUEST", 3);
        for (RequestAction action : RequestAction.values()) {
            assertThat(rows.get(action.name())).as(action.name()).isEqualTo(action.defaultLimit());
        }
        assertThat(RequestAction.GROUPBUY_JOIN.defaultLimit()).isEqualTo(5);
        assertThat(RequestAction.POINT_CHARGE.defaultLimit()).isEqualTo(3);
        assertThat(RequestAction.POINT_SHOP_PURCHASE.defaultLimit()).isEqualTo(10);
        assertThat(RequestAction.REFUND_REQUEST.defaultLimit()).isEqualTo(3);
    }

    @Test
    void 한도까지는_통과하고_넘으면_429_와_창이_끝날_때까지의_초() {
        for (int i = 1; i <= 3; i++) {
            assertThat(limiter.check(userId, RequestAction.POINT_CHARGE)).isEqualTo(i);
        }
        assertThatThrownBy(() -> limiter.check(userId, RequestAction.POINT_CHARGE))
                .isInstanceOf(RequestRateLimitedException.class)
                .satisfies(e -> {
                    RequestRateLimitedException limited = (RequestRateLimitedException) e;
                    assertThat(limited.getStatus().value()).isEqualTo(429);
                    assertThat(limited.getErrorCode()).isEqualTo("REQUEST_RATE_LIMITED");
                    assertThat(limited.getRetryAfterSeconds()).as("45초에 걸렸으니 15초 뒤").isEqualTo(15L);
                });
    }

    @Test
    void 다음_1분_창이면_다시_허용한다() {
        for (int i = 0; i < 3; i++) {
            limiter.check(userId, RequestAction.REFUND_REQUEST);
        }
        assertThatThrownBy(() -> limiter.check(userId, RequestAction.REFUND_REQUEST)).isInstanceOf(RequestRateLimitedException.class);
        clock.advance(Duration.ofSeconds(15));
        assertThat(limiter.check(userId, RequestAction.REFUND_REQUEST)).isEqualTo(1);
    }

    @Test
    void 다시_시도_초는_올림이고_최소_1초() {
        Instant window = Instant.parse("2026-10-24T01:02:00Z");
        assertThat(RequestRateLimiter.retryAfterSeconds(window.plusMillis(59_100), window)).isEqualTo(1);
        assertThat(RequestRateLimiter.retryAfterSeconds(window.plusMillis(59_999), window)).isEqualTo(1);
        assertThat(RequestRateLimiter.retryAfterSeconds(window, window)).isEqualTo(60);
        assertThat(RequestRateLimiter.retryAfterSeconds(window.plusMillis(30_500), window)).isEqualTo(30);
    }

    @Test
    void 한도_0_은_제한_없음이고_세지도_않는다() {
        setLimit(RequestAction.POINT_SHOP_PURCHASE, 0);
        for (int i = 0; i < 150; i++) {
            assertThat(limiter.check(userId, RequestAction.POINT_SHOP_PURCHASE)).isZero();
        }
        assertThat(counted(RequestAction.POINT_SHOP_PURCHASE)).isZero();
    }

    @Test
    void 시도_수는_업무와_따로_커밋돼_업무가_실패해도_남는다() {
        TransactionTemplate business = new TransactionTemplate(transactions);
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> business.executeWithoutResult(status -> {
                limiter.check(userId, RequestAction.POINT_CHARGE);
                jdbc.update("CREATE TEMP TABLE IF NOT EXISTS zz_rate_dummy (x int)");
                throw new IllegalStateException("업무 실패");
            })).hasMessage("업무 실패");
        }
        assertThat(counted(RequestAction.POINT_CHARGE)).as("업무 트랜잭션이 되돌려져도 두 번의 시도가 남는다").isEqualTo(2);
        limiter.check(userId, RequestAction.POINT_CHARGE);
        assertThatThrownBy(() -> limiter.check(userId, RequestAction.POINT_CHARGE)).isInstanceOf(RequestRateLimitedException.class);
    }

    @Test
    void 회원별_요청_종류별로_따로_센다() {
        for (int i = 0; i < 3; i++) {
            limiter.check(userId, RequestAction.POINT_CHARGE);
        }
        assertThat(limiter.check(userId, RequestAction.REFUND_REQUEST)).isEqualTo(1);
        assertThat(limiter.check(USERS.incrementAndGet(), RequestAction.POINT_CHARGE)).isEqualTo(1);
    }

    @Test
    void 서버_여럿이_동시에_세도_전체_합으로_정확히_한도만_통과한다() throws Exception {
        setLimit(RequestAction.GROUPBUY_JOIN, 5);
        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                // 서버마다 자기 사본·제한기(같은 DB)
                RequestRateLimiter server = new RequestRateLimiter(dataSource, new DataSourceTransactionManager(dataSource), new RequestRateLimits(dataSource), clock);
                Callable<Boolean> call = () -> {
                    go.await();
                    try {
                        server.check(userId, RequestAction.GROUPBUY_JOIN);
                        return true;
                    } catch (RequestRateLimitedException e) {
                        return false;
                    }
                };
                results.add(pool.submit(call));
            }
            go.countDown();
            int passed = 0;
            for (Future<Boolean> result : results) {
                if (result.get(30, TimeUnit.SECONDS)) {
                    passed++;
                }
            }
            assertThat(passed).isEqualTo(5);
            assertThat(counted(RequestAction.GROUPBUY_JOIN)).isEqualTo(threads);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 카운터_표는_UNLOGGED_다() {
        assertThat(jdbc.queryForObject("SELECT relpersistence::text FROM pg_class WHERE relname = 'request_rate_counters'", String.class)).isEqualTo("u");
    }
}
