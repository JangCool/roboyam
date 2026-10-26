package com.community.commerce.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.exception.IdempotencyKeyInvalidException;
import com.community.commerce.guard.exception.IdempotencyKeyRequiredException;
import com.community.commerce.guard.exception.IdempotencyKeyReusedException;
import com.community.commerce.guard.exception.RequestRateLimitedException;
import com.community.commerce.guard.service.IdempotencyService;
import com.community.commerce.guard.service.RequestRateLimiter;
import com.community.commerce.guard.service.RequestRateLimits;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import com.community.zconfiguration.response.ApiResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import tools.jackson.databind.JsonNode;

/**
 * 돈 요청 입구 도우미(docs/domain/commerce.md 3절 · 12-4 · 18절 P26, commerce-foundation-contract 「0-5」) — 헤더 검사 → 속도 제한 → 멱등 키 → 업무. 실제 PostgreSQL.
 */
class MoneyRequestGuardTest {

    private static final AtomicLong USERS = new AtomicLong(7_400_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private MoneyRequestGuard guard;
    private long userId;

    record Purchase(long itemId, int quantity) {
    }

    record Result(long orderId, String status) {
    }

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        MutableTestClock clock = new MutableTestClock(Instant.now().truncatedTo(ChronoUnit.MINUTES).plusSeconds(10));
        guard = new MoneyRequestGuard(new RequestRateLimiter(dataSource, transactions, new RequestRateLimits(dataSource), clock),
                new IdempotencyService(dataSource, transactions, clock));
        userId = USERS.incrementAndGet();
    }

    private static JsonNode data(ResponseEntity<ApiResponse<Object>> response) {
        return (JsonNode) response.getBody().data();
    }

    @Test
    void 처음_요청은_업무를_하고_같은_키_같은_본문의_다시_보내기는_업무_없이_같은_data_와_재전송_머리글() {
        String key = UUID.randomUUID().toString();
        AtomicInteger calls = new AtomicInteger();
        ResponseEntity<ApiResponse<Object>> first = guard.handle(userId, key, RequestAction.POINT_SHOP_PURCHASE, new Purchase(3, 1),
                () -> new Result(1000 + calls.incrementAndGet(), "PAID"));
        ResponseEntity<ApiResponse<Object>> again = guard.handle(userId, key, RequestAction.POINT_SHOP_PURCHASE, new Purchase(3, 1),
                () -> new Result(1000 + calls.incrementAndGet(), "PAID"));

        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(first.getHeaders().getFirst(MoneyRequestGuard.REPLAYED_HEADER)).isNull();
        assertThat(first.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(data(first).get("orderId").asLong()).isEqualTo(1001);
        assertThat(again.getHeaders().getFirst(MoneyRequestGuard.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(data(again)).isEqualTo(data(first));
        assertThat(calls).hasValue(1);
    }

    @Test
    void 같은_키에_다른_본문이면_409_P26() {
        String key = UUID.randomUUID().toString();
        guard.handle(userId, key, RequestAction.POINT_SHOP_PURCHASE, new Purchase(3, 1), () -> new Result(1, "PAID"));
        assertThatThrownBy(() -> guard.handle(userId, key, RequestAction.POINT_SHOP_PURCHASE, new Purchase(3, 2), () -> new Result(2, "PAID")))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void 헤더가_없으면_400_IDEMPOTENCY_KEY_REQUIRED_형식이_틀리면_400_IDEMPOTENCY_KEY_INVALID() {
        assertThatThrownBy(() -> guard.handle(userId, null, RequestAction.POINT_SHOP_PURCHASE, Map.of(), () -> "x"))
                .isInstanceOf(IdempotencyKeyRequiredException.class)
                .satisfies(e -> assertThat(((IdempotencyKeyRequiredException) e).getStatus().value()).isEqualTo(400));
        assertThatThrownBy(() -> guard.handle(userId, "  ", RequestAction.POINT_SHOP_PURCHASE, Map.of(), () -> "x")).isInstanceOf(IdempotencyKeyRequiredException.class);
        assertThatThrownBy(() -> guard.handle(userId, "bad key!", RequestAction.POINT_SHOP_PURCHASE, Map.of(), () -> "x")).isInstanceOf(IdempotencyKeyInvalidException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM request_rate_counters WHERE user_id = ?", Integer.class, userId))
                .as("헤더가 없거나 비어 있으면 세기 전에 거절한다").isLessThanOrEqualTo(1);
    }

    @Test
    void 속도_제한이_먼저다_한도를_넘으면_429_이고_업무를_하지_않는다() {
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            guard.handle(userId, UUID.randomUUID().toString(), RequestAction.REFUND_REQUEST, Map.of("n", i), () -> calls.incrementAndGet());
        }
        assertThatThrownBy(() -> guard.handle(userId, UUID.randomUUID().toString(), RequestAction.REFUND_REQUEST, Map.of("n", 9), () -> calls.incrementAndGet()))
                .isInstanceOf(RequestRateLimitedException.class)
                .satisfies(e -> assertThat(((RequestRateLimitedException) e).getRetryAfterSeconds()).isEqualTo(50L));
        assertThat(calls).hasValue(3);
    }

    @Test
    void 업무가_실패하면_같은_키로_다시_할_수_있다() {
        String key = UUID.randomUUID().toString();
        assertThatThrownBy(() -> guard.handle(userId, key, RequestAction.POINT_CHARGE, Map.of("amount", 10000), () -> {
            throw new IllegalStateException("결제 실패");
        })).hasMessage("결제 실패");
        ResponseEntity<ApiResponse<Object>> retried = guard.handle(userId, key, RequestAction.POINT_CHARGE, Map.of("amount", 10000), () -> new Result(7, "PAID"));
        assertThat(retried.getHeaders().getFirst(MoneyRequestGuard.REPLAYED_HEADER)).isNull();
        assertThat(data(retried).get("orderId").asLong()).isEqualTo(7);
    }
}
