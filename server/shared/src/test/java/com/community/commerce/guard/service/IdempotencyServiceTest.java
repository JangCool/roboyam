package com.community.commerce.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.dto.StoredResponse;
import com.community.commerce.guard.exception.IdempotencyInProgressException;
import com.community.commerce.guard.exception.IdempotencyKeyInvalidException;
import com.community.commerce.guard.exception.IdempotencyKeyReusedException;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.MutableTestClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

/**
 * 멱등 키(docs/domain/commerce.md 3절 · 18절 P26, commerce-foundation-contract 「0-5」) — 실제 PostgreSQL.
 * 회원 ID 는 시험마다 새로 만들어 다른 시험의 행과 겹치지 않는다(표 전체를 비우지 않는다).
 */
class IdempotencyServiceTest {

    private static final AtomicLong USERS = new AtomicLong(7_100_000_000_000L + (System.nanoTime() % 1_000_000L) * 1_000L);

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private MutableTestClock clock;
    private IdempotencyService service;
    private long userId;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS zz_idem_work (id bigserial PRIMARY KEY, user_id bigint NOT NULL, note text)");
    }

    @BeforeEach
    void setUp() {
        clock = new MutableTestClock(Instant.now());
        service = new IdempotencyService(dataSource, new DataSourceTransactionManager(dataSource), clock);
        userId = USERS.incrementAndGet();
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private static String hash(Object body) {
        return RequestHashes.of(RequestAction.POINT_SHOP_PURCHASE, body);
    }

    /** 업무: 시험 표에 한 줄을 쓰고 그 번호를 응답으로 */
    private StoredResponse work(AtomicInteger calls) {
        calls.incrementAndGet();
        Long id = jdbc.queryForObject("INSERT INTO zz_idem_work (user_id, note) VALUES (?, 'ok') RETURNING id", Long.class, userId);
        return StoredResponse.fresh(200, "{\"orderId\":" + id + "}");
    }

    private int workRows() {
        return jdbc.queryForObject("SELECT count(*) FROM zz_idem_work WHERE user_id = ?", Integer.class, userId);
    }

    private Map<String, Object> row(String key) {
        return jdbc.queryForMap("SELECT * FROM idempotency_requests WHERE user_id = ? AND idem_key = ?", userId, key);
    }

    private int rows(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM idempotency_requests WHERE user_id = ? AND idem_key = ?", Integer.class, userId, key);
    }

    @Nested
    class 같은_키 {

        @Test
        void 같은_본문으로_다시_오면_업무를_다시_하지_않고_처음_응답을_준다() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            StoredResponse first = service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("itemId", 3)), () -> work(calls));
            StoredResponse again = service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("itemId", 3)), () -> work(calls));

            assertThat(first.replayed()).isFalse();
            assertThat(again.replayed()).isTrue();
            assertThat(again.status()).isEqualTo(200);
            assertThat(again.body()).isEqualTo(first.body());
            assertThat(calls).hasValue(1);
            assertThat(workRows()).isEqualTo(1);
            assertThat(row(key)).containsEntry("status", "COMPLETED").containsEntry("response_code", 200);
        }

        @Test
        void 다른_본문이면_409_P26() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("itemId", 3)), () -> work(calls));

            assertThatThrownBy(() -> service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("itemId", 4)), () -> work(calls)))
                    .isInstanceOf(IdempotencyKeyReusedException.class)
                    .satisfies(e -> assertThat(((IdempotencyKeyReusedException) e).getStatus().value()).isEqualTo(409));
            assertThat(calls).hasValue(1);
        }

        @Test
        void 다른_요청_종류에_같은_키를_쓰면_409() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, RequestHashes.of(RequestAction.POINT_SHOP_PURCHASE, Map.of("x", 1)), () -> work(calls));

            assertThatThrownBy(() -> service.execute(userId, key, RequestAction.REFUND_REQUEST, RequestHashes.of(RequestAction.REFUND_REQUEST, Map.of("x", 1)),
                    () -> work(calls))).isInstanceOf(IdempotencyKeyReusedException.class);
        }

        @Test
        void 처리_중에_같은_키로_오면_409_IDEMPOTENCY_IN_PROGRESS() throws Exception {
            String key = key();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ExecutorService pool = Executors.newSingleThreadExecutor();
            try {
                Future<StoredResponse> slow = pool.submit(() -> service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> {
                    started.countDown();
                    try {
                        release.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return StoredResponse.fresh(200, "{\"done\":true}");
                }));
                assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(row(key)).as("처리 중 행은 업무와 따로 먼저 커밋돼 다른 연결에서 보인다").containsEntry("status", "IN_PROGRESS");

                assertThatThrownBy(() -> service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> StoredResponse.fresh(200, "{}")))
                        .isInstanceOf(IdempotencyInProgressException.class)
                        .satisfies(e -> assertThat(((IdempotencyInProgressException) e).getStatus().value()).isEqualTo(409));

                release.countDown();
                assertThat(slow.get(10, TimeUnit.SECONDS).body()).isEqualTo("{\"done\":true}");
                assertThat(service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> StoredResponse.fresh(500, "{}")).replayed()).isTrue();
            } finally {
                release.countDown();
                pool.shutdownNow();
            }
        }

        @Test
        void 다른_회원의_같은_키는_따로다() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> work(calls));
            long other = USERS.incrementAndGet();
            StoredResponse response = service.execute(other, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 2)), () -> StoredResponse.fresh(200, "{\"other\":1}"));
            assertThat(response.replayed()).isFalse();
        }
    }

    @Nested
    class 실패와_트랜잭션 {

        @Test
        void 업무가_실패하면_처리_중_행을_지워_같은_키로_다시_할_수_있다() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            assertThatThrownBy(() -> service.execute(userId, key, RequestAction.POINT_CHARGE, hash(Map.of("a", 1)), () -> {
                work(calls);
                throw new IllegalStateException("업무 실패");
            })).isInstanceOf(IllegalStateException.class).hasMessage("업무 실패");

            assertThat(rows(key)).as("실패하면 행이 없다").isZero();
            assertThat(workRows()).as("업무 트랜잭션은 되돌려졌다").isZero();

            StoredResponse retried = service.execute(userId, key, RequestAction.POINT_CHARGE, hash(Map.of("a", 1)), () -> work(calls));
            assertThat(retried.replayed()).isFalse();
            assertThat(workRows()).isEqualTo(1);
        }

        @Test
        void 결과는_업무_트랜잭션_안에서_저장된다_결과_저장이_실패하면_업무도_되돌린다() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            String tooBig = "\"" + "가".repeat(6000) + "\""; // UTF-8 18KB
            assertThatThrownBy(() -> service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> {
                work(calls);
                return StoredResponse.fresh(200, tooBig);
            })).isInstanceOf(IllegalStateException.class).hasMessageContaining("16384");

            assertThat(workRows()).as("응답을 저장하지 못하면 업무도 남지 않는다").isZero();
            assertThat(rows(key)).isZero();
        }

        @Test
        void 응답은_16KB_까지_저장한다() {
            String key = key();
            String body = "\"" + "a".repeat(StoredResponse.MAX_BODY_BYTES - 2) + "\"";
            service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of()), () -> StoredResponse.fresh(201, body));
            StoredResponse replay = service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of()), () -> StoredResponse.fresh(500, "{}"));
            assertThat(replay.status()).isEqualTo(201);
            assertThat(replay.body()).hasSize(StoredResponse.MAX_BODY_BYTES);
        }
    }

    @Nested
    class 보관과_형식 {

        @Test
        void 키는_24시간_동안만_기억한다() {
            String key = key();
            AtomicInteger calls = new AtomicInteger();
            service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> work(calls));

            clock.advance(Duration.ofHours(23).plusMinutes(59));
            assertThat(service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("a", 1)), () -> work(calls)).replayed()).isTrue();

            clock.advance(Duration.ofMinutes(2));
            StoredResponse after = service.execute(userId, key, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of("b", 2)), () -> work(calls));
            assertThat(after.replayed()).as("24시간이 지나면 없는 키로 본다 — 다른 본문도 새 요청").isFalse();
            assertThat(calls).hasValue(2);
        }

        @Test
        void 키_형식이_틀리면_400() {
            for (String bad : new String[] {null, "", "short", "a".repeat(65), "공백 있는 키-1234", "semi;colon-key"}) {
                assertThatThrownBy(() -> service.execute(userId, bad, RequestAction.POINT_SHOP_PURCHASE, hash(Map.of()), () -> StoredResponse.fresh(200, "{}")))
                        .as("키 %s", bad).isInstanceOf(IdempotencyKeyInvalidException.class)
                        .satisfies(e -> assertThat(((IdempotencyKeyInvalidException) e).getStatus().value()).isEqualTo(400));
            }
            assertThat(service.execute(userId, "Abc_123-xyz", RequestAction.POINT_SHOP_PURCHASE, hash(Map.of()), () -> StoredResponse.fresh(200, "{}")).replayed()).isFalse();
        }

        @Test
        void DB_도_키_형식과_응답_크기와_완료_조건을_막는다() {
            assertThatThrownBy(() -> jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status) VALUES (?, 'bad key!', 'X', ?, 'IN_PROGRESS')",
                    userId, "0".repeat(64))).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status, response_code, response_body, completed_at) "
                    + "VALUES (?, ?, 'X', ?, 'COMPLETED', 200, ?, now())", userId, key(), "0".repeat(64), "a".repeat(16385)))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO idempotency_requests (user_id, idem_key, action, request_hash, status) VALUES (?, ?, 'X', ?, 'COMPLETED')",
                    userId, key(), "0".repeat(64))).as("끝난 행에는 응답 상태가 있어야 한다").isInstanceOf(DataIntegrityViolationException.class);
        }
    }
}
