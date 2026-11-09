package com.community.zconfiguration.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.analytics.settings.StatsCollection;
import com.community.analytics.settings.StatsCollectionSettings;
import com.community.analytics.settings.StatsCollectionStore;
import com.community.cache.definition.CacheCatalog;
import com.community.cache.definition.CacheDefinition;
import com.community.cache.definition.CacheNames;
import com.community.cache.definition.CacheServerRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.support.NoOpCache;

/**
 * 캐시 구현(Caffeine)의 「이전 값 유지」 — docs/features/cache-admin-contract.md 「서버 구현 결과」 · 원격 전환 10(같은 동작인지 같은 시험으로).
 * 설정 사본·내부 규칙 캐시는 TTL 이 지나 다시 읽다 실패하면 이전 값을 쓰고, 비우기·끄기는 이전 값까지 지운다. 공개 화면·가젯은 그대로 만료.
 */
class CaffeineCacheControlTest {

    private final AtomicLong nanos = new AtomicLong(1_000_000_000L);
    private final CaffeineCacheControl control = new CaffeineCacheControl(CacheServerRole.API, nanos::get);

    private void advance(Duration d) {
        nanos.addAndGet(d.toNanos());
    }

    /** 원본(DB) 흉내 — 값을 바꾸고, 실패시키고, 몇 번 읽었는지 센다 */
    private static class Source implements CacheReloader {

        final String name;
        final AtomicReference<Function<Object, Object>> value = new AtomicReference<>(key -> "v1");
        final AtomicBoolean down = new AtomicBoolean();
        final AtomicInteger reloads = new AtomicInteger();

        Source(String name) {
            this.name = name;
        }

        Object read(Object key) {
            if (down.get()) {
                throw new IllegalStateException("DB 읽기 실패(시험)");
            }
            return value.get().apply(key);
        }

        @Override
        public String cacheName() {
            return name;
        }

        @Override
        public Object reload(Object key) {
            reloads.incrementAndGet();
            return read(key);
        }

        void set(Object v) {
            value.set(key -> v);
        }
    }

    private Source registered(String name) {
        Source s = new Source(name);
        control.registerReloader(s);
        return s;
    }

    private static Object read(Cache cache, Source s) {
        return cache.get(CacheNames.SINGLE, () -> s.read(CacheNames.SINGLE));
    }

    private static final Duration POINT_TTL = Duration.ofSeconds(15);

    @Test
    void 이전_값_유지_캐시는_설정_사본과_내부_규칙_12개다() {
        assertThat(CacheCatalog.all().stream().filter(CacheDefinition::keepsLastValue).map(CacheDefinition::key)).containsExactlyInAnyOrder(
                CacheNames.SITE_INFO, CacheNames.SITE_NOTICE_BAR, CacheNames.SITE_POPUPS, CacheNames.TERMS_PUBLIC, CacheNames.NOTICE_GLOBAL, CacheNames.BOARD_CATALOG,
                CacheNames.STATS_COLLECTION, CacheNames.COMMERCE_RATE_LIMITS, CacheNames.HOTDEAL_RULES, CacheNames.HOTDEAL_SETTINGS, CacheNames.NOTIFICATION_TEMPLATES,
                CacheNames.POINT_POLICY);
    }

    @Test
    void TTL_전에는_다시_읽지_않고_지나면_그_읽기가_바로_새_값을_받는다() {
        Source s = registered(CacheNames.POINT_POLICY);
        Cache cache = control.getCache(CacheNames.POINT_POLICY);
        assertThat(read(cache, s)).isEqualTo("v1");

        s.set("v2");
        advance(POINT_TTL.minusSeconds(1));
        assertThat(read(cache, s)).isEqualTo("v1");
        assertThat(s.reloads).hasValue(0);

        advance(Duration.ofSeconds(2));
        assertThat(read(cache, s)).as("다시 읽기는 그 읽는 스레드에서 — 만료 직후 첫 읽기도 새 값").isEqualTo("v2");
        assertThat(s.reloads).hasValue(1);
    }

    @Test
    void TTL_이_지나_다시_읽다_실패하면_이전_값을_쓰고_다음_읽기에서_다시_시도한다() {
        Source s = registered(CacheNames.POINT_POLICY);
        Cache cache = control.getCache(CacheNames.POINT_POLICY);
        assertThat(read(cache, s)).isEqualTo("v1");

        advance(POINT_TTL.plusSeconds(1));
        s.down.set(true);
        assertThat(read(cache, s)).as("실패해도 요청은 깨지지 않는다").isEqualTo("v1");
        assertThat(read(cache, s)).isEqualTo("v1");
        assertThat(s.reloads).as("실패한 뒤 다음 읽기가 다시 시도한다").hasValue(2);

        advance(Duration.ofMinutes(30));
        assertThat(read(cache, s)).as("원본이 오래 죽어 있어도 이전 값(만료 없음)").isEqualTo("v1");

        s.down.set(false);
        s.set("v3");
        assertThat(read(cache, s)).as("살아나면 그 읽기가 새 값").isEqualTo("v3");
        assertThat(read(cache, s)).isEqualTo("v3");
        assertThat(s.reloads).hasValue(4);
    }

    @Test
    void 비우면_이전_값까지_지우고_그_뒤_첫_읽기가_실패하면_그_요청이_실패한다() {
        Source s = registered(CacheNames.POINT_POLICY);
        Cache cache = control.getCache(CacheNames.POINT_POLICY);
        assertThat(read(cache, s)).isEqualTo("v1");

        control.clear(CacheNames.POINT_POLICY); // 세대 올리기 · 「지금 갱신」
        s.down.set(true);
        assertThatThrownBy(() -> read(cache, s)).isInstanceOf(Cache.ValueRetrievalException.class).hasRootCauseMessage("DB 읽기 실패(시험)");
        assertThatThrownBy(() -> read(cache, s)).as("담지 않으므로 다음 요청도 다시 읽는다").isInstanceOf(Cache.ValueRetrievalException.class);

        s.down.set(false);
        s.set("v2");
        assertThat(read(cache, s)).isEqualTo("v2");
    }

    @Test
    void TTL_변경은_다시_읽는_주기를_바꾸고_비우지_않는다() {
        Source s = registered(CacheNames.POINT_POLICY);
        Cache cache = control.getCache(CacheNames.POINT_POLICY);
        assertThat(read(cache, s)).isEqualTo("v1");

        control.applyTtl(CacheNames.POINT_POLICY, Duration.ofSeconds(60));
        s.set("v2");
        advance(Duration.ofSeconds(30));
        assertThat(read(cache, s)).as("담긴 항목도 새 TTL — 비우지 않는다").isEqualTo("v1");
        advance(Duration.ofSeconds(31));
        assertThat(read(cache, s)).isEqualTo("v2");

        control.applyTtl(CacheNames.POINT_POLICY, Duration.ofSeconds(5));
        s.set("v3");
        advance(Duration.ofSeconds(6));
        assertThat(read(cache, s)).isEqualTo("v3");
    }

    @Test
    void 끄면_이전_값을_지우고_요청마다_원본을_읽으며_실패하면_그_요청이_실패한다() {
        Source s = registered(CacheNames.POINT_POLICY);
        assertThat(read(control.getCache(CacheNames.POINT_POLICY), s)).isEqualTo("v1");

        control.applyEnabled(CacheNames.POINT_POLICY, false);
        Cache off = control.getCache(CacheNames.POINT_POLICY);
        assertThat(off).isInstanceOf(NoOpCache.class);
        s.set("v2");
        assertThat(read(off, s)).isEqualTo("v2");
        s.down.set(true);
        assertThatThrownBy(() -> read(off, s)).isInstanceOf(Cache.ValueRetrievalException.class);

        control.applyEnabled(CacheNames.POINT_POLICY, true);
        s.down.set(false);
        s.set("v3");
        assertThat(read(control.getCache(CacheNames.POINT_POLICY), s)).as("켜면 빈 캐시 — 끄기 전 값은 없다").isEqualTo("v3");
    }

    @Test
    void 공개_화면_캐시는_그대로_만료되고_만료_뒤_실패는_그_요청이_실패한다() {
        Source s = new Source(CacheNames.HOME_LAYOUT);
        control.registerReloader(s); // 이전 값 유지가 아니라 무시된다
        Cache cache = control.getCache(CacheNames.HOME_LAYOUT);
        assertThat(read(cache, s)).isEqualTo("v1");

        advance(Duration.ofSeconds(301));
        s.down.set(true);
        assertThatThrownBy(() -> read(cache, s)).isInstanceOf(Cache.ValueRetrievalException.class);
        assertThat(s.reloads).hasValue(0);
        assertThat(control.missingReloaders()).doesNotContain(CacheNames.HOME_LAYOUT);
    }

    @Test
    void 다시_읽기가_등록되지_않으면_TTL_뒤_한_번_이전_값을_주고_버린다() {
        assertThat(control.missingReloaders()).contains(CacheNames.POINT_POLICY);
        Cache cache = control.getCache(CacheNames.POINT_POLICY);
        assertThat(cache.get(CacheNames.SINGLE, () -> "v1")).isEqualTo("v1");

        advance(POINT_TTL.plusSeconds(1));
        assertThat(cache.get(CacheNames.SINGLE, () -> "x")).isEqualTo("v1");
        assertThat(cache.get(CacheNames.SINGLE, () -> "v2")).as("다음 읽기는 부르는 쪽 로더로 새로 읽는다(만료와 같은 결과)").isEqualTo("v2");
    }

    @Test
    void 키가_있는_캐시는_그_키로_다시_읽고_같은_캐시에_다시_읽기를_둘_등록하면_띄우지_않는다() {
        Source s = registered(CacheNames.NOTICE_GLOBAL);
        s.value.set(key -> "a" + key);
        Cache cache = control.getCache(CacheNames.NOTICE_GLOBAL);
        assertThat(cache.get(7L, () -> s.read(7L))).isEqualTo("a7");
        s.value.set(key -> "b" + key);
        advance(Duration.ofSeconds(16));
        assertThat(cache.get(7L, () -> s.read(7L))).isEqualTo("b7");

        control.registerReloader(s); // 같은 것을 다시는 괜찮다
        assertThatThrownBy(() -> control.registerReloader(new Source(CacheNames.NOTICE_GLOBAL))).isInstanceOf(IllegalStateException.class);
        assertThat(control.missingReloaders()).doesNotContain(CacheNames.NOTICE_GLOBAL);
    }

    @Test
    void 다시_읽는_동안_같은_항목을_읽는_다른_요청은_기다리지_않고_이전_값을_받는다() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Source s = new Source(CacheNames.POINT_POLICY) {
            @Override
            public Object reload(Object key) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "v2";
            }
        };
        control.registerReloader(s);
        Cache cache = control.getCache(CacheNames.POINT_POLICY);
        assertThat(read(cache, s)).isEqualTo("v1");
        advance(POINT_TTL.plusSeconds(1));

        CompletableFuture<Object> first = CompletableFuture.supplyAsync(() -> read(cache, s));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        long started = System.nanoTime();
        assertThat(read(cache, s)).isEqualTo("v1");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));

        release.countDown();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("v2");
        assertThat(read(cache, s)).isEqualTo("v2");
    }

    // ── 부르는 쪽 규칙 — 통계 수집 설정(처음부터 못 읽으면 꺼짐) ─────────────────────

    private static final StatsCollection ON = new StatsCollection(true, true, false, 90, false, false, false, false);

    @Test
    void 통계_수집_설정은_TTL_뒤_실패하면_이전_값_비운_뒤_첫_읽기_실패는_꺼짐() {
        AtomicReference<StatsCollection> row = new AtomicReference<>(ON);
        AtomicBoolean down = new AtomicBoolean();
        StatsCollectionStore store = new StatsCollectionStore(null, null, null) {
            @Override
            public Optional<Stored> find() {
                if (down.get()) {
                    throw new IllegalStateException("DB 읽기 실패(시험)");
                }
                return Optional.of(new Stored(row.get(), Instant.EPOCH, null, null));
            }
        };
        CaffeineCacheControl admin = new CaffeineCacheControl(CacheServerRole.ADMIN, nanos::get);
        StatsCollectionSettings settings = new StatsCollectionSettings(store, admin);
        admin.registerReloader(settings);

        down.set(true);
        assertThat(settings.current()).as("처음부터 못 읽으면 꺼짐").isEqualTo(StatsCollection.OFF);
        down.set(false);
        assertThat(settings.current()).as("담지 않았으니 다음 요청이 다시 읽는다").isEqualTo(ON);

        advance(Duration.ofSeconds(31));
        down.set(true);
        assertThat(settings.current()).as("TTL 뒤 실패 = 이전 값").isEqualTo(ON);

        settings.invalidate();
        assertThat(settings.current()).as("비운 뒤 첫 읽기 실패 = 꺼짐").isEqualTo(StatsCollection.OFF);
    }
}
