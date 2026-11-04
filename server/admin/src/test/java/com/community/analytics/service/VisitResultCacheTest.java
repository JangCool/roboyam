package com.community.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.analytics.service.VisitAnalyticsAdminServiceTest.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 「방문자 통계」 결과 캐시(VisitResultCache) — 수명·실패·한 번만 계산·크기. DB 없음 */
class VisitResultCacheTest {

    private static final Instant T0 = Instant.parse("2097-03-13T01:20:30Z");

    @Test
    void 수명_안에는_같은_값_지나면_새로_계산하고_계산_시각을_넘긴다() {
        MutableClock clock = new MutableClock(T0);
        VisitResultCache cache = new VisitResultCache(clock);
        AtomicInteger calls = new AtomicInteger();
        assertThat(cache.<Instant>get("k", Duration.ofSeconds(20), at -> {
            calls.incrementAndGet();
            return at;
        })).isEqualTo(T0);
        clock.now = T0.plusSeconds(19);
        assertThat(cache.<Instant>get("k", Duration.ofSeconds(20), at -> at)).isEqualTo(T0);
        clock.now = T0.plusSeconds(20);
        assertThat(cache.<Instant>get("k", Duration.ofSeconds(20), at -> at)).as("수명 끝 시각에는 만료").isEqualTo(T0.plusSeconds(20));
        assertThat(calls).hasValue(1);
    }

    @Test
    void 실패는_캐시하지_않는다() {
        VisitResultCache cache = new VisitResultCache(new MutableClock(T0));
        AtomicInteger calls = new AtomicInteger();
        assertThatThrownBy(() -> cache.get("k", Duration.ofSeconds(60), at -> {
            calls.incrementAndGet();
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(cache.size()).isZero();
        assertThat(cache.<String>get("k", Duration.ofSeconds(60), at -> {
            calls.incrementAndGet();
            return "ok";
        })).isEqualTo("ok");
        assertThat(calls).hasValue(2);
    }

    @Test
    void 같은_키를_동시에_처음_부르면_한_번만_계산하고_기다린_요청도_같은_값() throws Exception {
        VisitResultCache cache = new VisitResultCache(new MutableClock(T0));
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> cache.get("k", Duration.ofSeconds(60), at -> {
                calls.incrementAndGet();
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "v";
            }));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Object> second = pool.submit(() -> cache.get("k", Duration.ofSeconds(60), at -> {
                calls.incrementAndGet();
                return "other";
            }));
            Thread.sleep(200);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("v");
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("v");
            assertThat(calls).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 항목이_상한을_넘으면_비운다() {
        VisitResultCache cache = new VisitResultCache(new MutableClock(T0));
        for (int i = 0; i <= VisitResultCache.MAX_ENTRIES; i++) {
            int n = i;
            cache.get("k" + i, Duration.ofSeconds(60), at -> n);
        }
        assertThat(cache.size()).isLessThanOrEqualTo(VisitResultCache.MAX_ENTRIES);
    }
}
