package com.community.commerce.guard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheNames;
import com.community.cache.definition.CacheServerRole;
import com.community.commerce.guard.dto.RequestAction;
import com.community.commerce.guard.dto.RequestRateLimitRow;
import com.community.commerce.guard.repository.master.RequestRateLimitRepositoryQDSL;
import com.community.testsupport.CommerceTestDatabase;
import com.community.testsupport.JpaTestContext;
import com.community.zconfiguration.cache.CaffeineCacheControl;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NoOpCacheManager;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** 요청 제한 값의 15초 사본(commerce.md 12-4 「서버들은 설정 사본을 최대 15초 안에」) — 실제 PostgreSQL. 바꾼 한도는 시험 끝에 기본값으로 되돌린다 */
class RequestRateLimitsTest {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void database() {
        dataSource = CommerceTestDatabase.migrated();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void restore() {
        for (RequestAction action : RequestAction.values()) {
            jdbc.update("INSERT INTO request_rate_limits (action, limit_per_minute) VALUES (?, ?) ON CONFLICT (action) DO UPDATE SET limit_per_minute = EXCLUDED.limit_per_minute",
                    action.name(), action.defaultLimit());
        }
    }

    @Test
    void 사본은_TTL_15초_동안_다시_읽지_않고_비우면_바로_다시_읽는다() {
        // 운영과 같은 캐시 구현(Caffeine — 캐시 commerce.rateLimits, 기본 TTL 15초)
        RequestRateLimits limits = new RequestRateLimits(new RequestRateLimitRepositoryQDSL(JpaTestContext.query()), new CaffeineCacheControl(CacheServerRole.API));
        assertThat(limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).isEqualTo(10);
        assertThat(CacheCatalog.find(CacheNames.COMMERCE_RATE_LIMITS).orElseThrow().defaultTtlSeconds()).isEqualTo(15);

        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 42 WHERE action = 'POINT_SHOP_PURCHASE'");
        assertThat(limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).as("다른 서버가 바꾼 값은 사본 주기 안에는 아직 옛 값").isEqualTo(10);

        limits.invalidate();
        assertThat(limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).as("저장한 서버는 커밋 뒤 비워 바로 새 값").isEqualTo(42);
    }

    @Test
    void TTL_이_지나_다시_읽다_실패하면_이전_한도를_쓰고_비운_뒤_첫_읽기_실패는_그_요청이_실패한다() {
        AtomicBoolean down = new AtomicBoolean();
        RequestRateLimitRepositoryQDSL rows = new RequestRateLimitRepositoryQDSL(JpaTestContext.query()) {
            @Override
            public List<RequestRateLimitRow> all() {
                if (down.get()) {
                    throw new IllegalStateException("DB 읽기 실패(시험)");
                }
                return super.all();
            }
        };
        AtomicLong nanos = new AtomicLong(1_000_000_000L);
        CaffeineCacheControl caches = new CaffeineCacheControl(CacheServerRole.API, nanos::get);
        RequestRateLimits limits = new RequestRateLimits(rows, caches);
        caches.registerReloader(limits);
        assertThat(limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).isEqualTo(10);

        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 42 WHERE action = 'POINT_SHOP_PURCHASE'");
        nanos.addAndGet(Duration.ofSeconds(16).toNanos());
        down.set(true);
        assertThat(limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).as("TTL 뒤 다시 읽기 실패 — 이전 한도로 계속 막는다(요청은 깨지지 않는다)").isEqualTo(10);

        down.set(false);
        assertThat(limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).as("다음 읽기에서 다시 시도 — 새 한도").isEqualTo(42);

        limits.invalidate();
        down.set(true);
        assertThatThrownBy(() -> limits.limitOf(RequestAction.POINT_SHOP_PURCHASE)).as("비운 뒤 첫 읽기 실패 — 이전 값은 지워졌다")
                .isInstanceOf(Cache.ValueRetrievalException.class);
    }

    @Test
    void 행이_없는_종류는_기본값() {
        jdbc.update("DELETE FROM request_rate_limits WHERE action = 'REFUND_REQUEST'");
        RequestRateLimits limits = new RequestRateLimits(new RequestRateLimitRepositoryQDSL(JpaTestContext.query()), new NoOpCacheManager());
        assertThat(limits.limitOf(RequestAction.REFUND_REQUEST)).isEqualTo(3);
    }

    @Test
    void DB_는_0_에서_100_밖의_한도를_막는다() {
        assertThatThrownBy(() -> jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 101 WHERE action = 'POINT_CHARGE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE request_rate_limits SET limit_per_minute = -1 WHERE action = 'POINT_CHARGE'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 0 WHERE action = 'POINT_CHARGE'");
        jdbc.update("UPDATE request_rate_limits SET limit_per_minute = 100 WHERE action = 'POINT_CHARGE'");
    }
}
