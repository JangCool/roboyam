package com.community.analytics.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 「방문자 통계」 결과 캐시(서버 메모리, 새 의존성 없음) — 대시보드 가젯 캐시({@code DashboardGadgetCache})와 같은 방식이다(그 캐시는 가젯 종류에 묶여 있어 따로 둔다).
 *
 * <ul>
 *   <li><b>키</b> = 부르는 쪽이 만든 글자(API 경로 + 정규화한 질의 값 + 언어 + 수집 스위치). 결과가 운영자 권한에 따라 달라지지 않으므로(모두 site.read 하나) 운영자끼리 공유한다.
 *       권한 검사는 캐시 앞(컨트롤러의 {@code @RequirePermission})에서 매 요청 한다.</li>
 *   <li><b>한 번만 계산</b>: 같은 키를 동시에 처음 부르면 먼저 온 요청만 계산하고 나머지는 기다린다. {@link #WAIT} 를 넘으면 기다리던 요청이 직접 계산한다(캐시에 넣지 않는다).
 *       실패는 캐시하지 않는다(기다리던 요청에도 같은 오류).</li>
 *   <li><b>크기</b>: 키에 경로·검색어 같은 입력이 들어가므로 {@link #MAX_ENTRIES} 를 넘으면 만료된 것을, 그래도 넘으면 끝난 항목을 모두 비운다. 만료 정리는 넣을 때 1분에 한 번.</li>
 *   <li><b>여러 인스턴스</b>: 관리자 서버마다 자기 캐시 — 수명 안에서 서버마다 값이 조금 다를 수 있을 뿐이다(응답의 {@code asOf} 가 그 서버의 계산 시각).</li>
 * </ul>
 */
final class VisitResultCache {

    static final int MAX_ENTRIES = 1000;
    static final Duration WAIT = Duration.ofSeconds(30);
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    private record Computed(Object value, Instant expiresAt) {
    }

    private final ConcurrentHashMap<String, CompletableFuture<Computed>> entries = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong();
    private final Clock clock;

    VisitResultCache(Clock clock) {
        this.clock = clock;
    }

    /** 수명 안의 값이 있으면 그것, 아니면 지금 계산한다. {@code compute} 는 계산 시각을 받는다 */
    @SuppressWarnings("unchecked")
    <T> T get(String key, Duration ttl, Function<Instant, T> compute) {
        while (true) {
            CompletableFuture<Computed> existing = entries.get(key);
            if (existing != null) {
                if (!existing.isDone()) {
                    Computed waited = await(existing);
                    return waited != null ? (T) waited.value() : compute.apply(clock.instant());
                }
                Computed done = existing.isCompletedExceptionally() ? null : existing.join();
                if (done != null && done.expiresAt().isAfter(clock.instant())) {
                    return (T) done.value();
                }
                entries.remove(key, existing);
                continue;
            }
            CompletableFuture<Computed> mine = new CompletableFuture<>();
            if (entries.putIfAbsent(key, mine) != null) {
                continue;
            }
            try {
                Instant at = clock.instant();
                T value = compute.apply(at);
                mine.complete(new Computed(value, at.plus(ttl)));
                trim();
                return value;
            } catch (RuntimeException | Error e) {
                entries.remove(key, mine);
                mine.completeExceptionally(e);
                throw e;
            }
        }
    }

    /** 지금 든 항목 수(시험·점검용) */
    int size() {
        return entries.size();
    }

    private static Computed await(CompletableFuture<Computed> future) {
        try {
            return future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("방문 통계 계산을 기다리다 끊겼다", e);
        } catch (ExecutionException | CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException r) {
                throw r;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(cause);
        }
    }

    private void trim() {
        long nowMillis = clock.millis();
        long last = lastSweep.get();
        boolean due = nowMillis - last >= SWEEP_EVERY.toMillis() && lastSweep.compareAndSet(last, nowMillis);
        if (!due && entries.size() <= MAX_ENTRIES) {
            return;
        }
        Instant now = clock.instant();
        entries.entrySet().removeIf(e -> e.getValue().isDone() && (e.getValue().isCompletedExceptionally() || !e.getValue().join().expiresAt().isAfter(now)));
        if (entries.size() > MAX_ENTRIES) {
            entries.entrySet().removeIf(e -> e.getValue().isDone());
        }
    }
}
