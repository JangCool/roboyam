package com.community.dashboard.service;

import com.community.dashboard.dto.DashboardGadgetType;
import com.community.dashboard.dto.response.gadget.DashboardGadgetData;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * 가젯 결과 캐시(서버 메모리) — 「서버 부하가 최소화되어야 한다」(사용자 결정 2026-11-03). 같은 종류·같은 질의 값의 결과를 짧게 다시 쓴다.
 *
 * <ul>
 *   <li><b>키</b> = 종류 + 그 종류의 질의 값(limit · days) + 요청 언어 + (결과가 운영자 권한에 따라 달라지는 종류만) 그 종류 권한 중 운영자가 가진 것.
 *       언어를 넣는 이유: 몇 종류(오늘 일정의 배치 이름 · 최근 운영자 활동의 작업 이름 · 배치 상태 · 공구 Q&A 비밀글 문구)는 요청 언어의 문구를 담는다.
 *       권한 조합은 지금 {@code TODAY_SCHEDULE} 뿐이다(항목마다 광고·공구·포인트 상점·배치 권한 — {@link #scoped}). {@code POINT_SUMMARY} 의 환불 승인 대기는 승인 기능이 없어
 *       늘 null 이라 권한에 따라 달라지지 않는다.</li>
 *   <li><b>권한 검사</b>(볼 수 있나)는 캐시가 아니라 서비스가 매 요청 먼저 한다 — 캐시에 든 값은 그 종류를 볼 수 있는 운영자에게만 나간다.</li>
 *   <li><b>수명</b>은 종류마다 {@link #ttl} 표 한 곳. 숫자·목록 60초, 기간 합·추이(지난 날의 값이 거의 바뀌지 않는 것) 300초.</li>
 *   <li><b>한 번만 계산</b>: 같은 키를 동시에 여러 요청이 처음 부르면 먼저 온 요청만 계산하고 나머지는 그 결과를 기다린다(계산 중 표시 = 끝나지 않은 {@link CompletableFuture}).
 *       계산하는 요청은 계산하는 동안 다른 키를 기다리지 않으므로 서로 기다리다 멈추지 않는다. 기다림이 {@link #WAIT} 를 넘으면 기다리던 요청이 직접 계산한다(캐시에 넣지 않는다).
 *       계산이 실패하면 그 키를 비우고(실패는 캐시하지 않는다) 기다리던 요청에도 같은 오류를 준다.</li>
 *   <li><b>크기</b>: 키는 종류 수 × 질의 조합 × 언어 × (오늘 일정의 권한 조합) 정도라 작다. 그래도 {@link #MAX_ENTRIES} 를 넘으면 만료된 것을 지우고, 그래도 넘으면 끝난 항목을 비운다.
 *       만료된 항목은 넣을 때 1분에 한 번 정리한다.</li>
 *   <li><b>여러 인스턴스</b>: 관리자 서버가 여러 대여도 각자 자기 캐시를 갖는다 — 값이 서버마다 수명 안에서 조금 다를 수 있을 뿐 공유할 상태가 아니라 괜찮다
 *       (응답의 {@code generatedAt} 이 그 서버의 계산 시각을 알려 준다).</li>
 *   <li>값 객체(응답 레코드)는 바꾸지 않는 값이라 여러 요청이 같은 객체를 함께 써도 된다.</li>
 * </ul>
 */
final class DashboardGadgetCache {

    static final int MAX_ENTRIES = 500;
    static final Duration SHORT = Duration.ofSeconds(60);
    static final Duration LONG = Duration.ofSeconds(300);
    static final Duration WAIT = Duration.ofSeconds(30);
    private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

    /** 캐시 키 — {@code scope} 는 권한에 따라 결과가 달라지는 종류만(아니면 빈 집합) */
    record Key(DashboardGadgetType type, int limit, int days, String locale, Set<String> scope) {
    }

    /** 계산한 결과 — {@code computedAt} 이 그 결과의 「기준 시각」이다 */
    record Computed(DashboardGadgetData data, Instant computedAt, Instant expiresAt) {
    }

    private final ConcurrentHashMap<Key, CompletableFuture<Computed>> entries = new ConcurrentHashMap<>();
    private final AtomicLong lastSweep = new AtomicLong();
    private final Clock clock;

    DashboardGadgetCache(Clock clock) {
        this.clock = clock;
    }

    /**
     * 종류별 수명 표(한 곳) — 새 종류를 더하면 이 switch 가 컴파일되지 않으니 여기서 정한다.
     * 300초: 기간 합·추이(매출 요약 · 결제 추이 · 광고 성과 · 가입 추이) — 지난 날의 값이 거의 바뀌지 않고 오늘 몫만 늦게 반영된다(최대 5분).
     */
    static Duration ttl(DashboardGadgetType type) {
        return switch (type) {
            case SALES_SUMMARY, ORDER_TREND, AD_PERFORMANCE, SIGNUP_TREND -> LONG;
            case TODO, COMMERCE_TODO, TODAY_SCHEDULE, REPORTS, POST_ACTIVITY, RECENT_POSTS, BOARD_RANKING, POPULAR_NOW, VISITORS, GROUP_REQUESTS, MEMBER_STATS,
                 RECENT_SIGNUPS, SANCTIONS, NICKNAME_IMAGES, HOTDEAL_STATUS, HOTDEAL_TOP, AD_SCHEDULE, GROUPBUY_PROGRESS, GROUPBUY_QNA, RECONCILE, POINT_SUMMARY,
                 POINTSHOP, BATCH_HEALTH, NOTIFICATION_HEALTH, AUDIT_RECENT, OPERATOR_SECURITY, MEMO, SHORTCUTS -> SHORT;
        };
    }

    /** 결과가 운영자 권한에 따라 달라지는 종류(제공자가 {@code query.actor()} 로 항목을 거른다) — 키에 그 종류 권한 중 가진 것을 넣는다 */
    static boolean scoped(DashboardGadgetType type) {
        return type == DashboardGadgetType.TODAY_SCHEDULE;
    }

    static Key key(DashboardGadgetType type, int limit, int days, Locale locale, AdminActor actor) {
        Set<String> scope = Set.of();
        if (scoped(type)) {
            Set<String> held = new TreeSet<>();
            for (Permission p : type.permissions()) {
                if (actor.can(p)) {
                    held.add(p.code());
                }
            }
            scope = Set.copyOf(held);
        }
        return new Key(type, limit, days, locale.getLanguage(), scope);
    }

    /** 수명 안의 끝난 값이 있으면 그것(계산 중이거나 없거나 만료면 빈 값 — 그때는 {@link #get} 으로 계산하거나 기다린다) */
    Optional<Computed> peek(Key key) {
        CompletableFuture<Computed> existing = entries.get(key);
        if (existing == null || !existing.isDone() || existing.isCompletedExceptionally()) {
            return Optional.empty();
        }
        Computed done = existing.join();
        return done.expiresAt().isAfter(clock.instant()) ? Optional.of(done) : Optional.empty();
    }

    /** 수명 안의 값이 있으면 그것, 아니면 지금 계산한다(같은 키를 계산 중인 요청이 있으면 그 결과를 기다린다). {@code compute} 는 계산 시각을 받는다 */
    Computed get(Key key, Function<Instant, DashboardGadgetData> compute) {
        while (true) {
            CompletableFuture<Computed> existing = entries.get(key);
            if (existing != null) {
                if (!existing.isDone()) {
                    Computed waited = await(existing);
                    if (waited != null) {
                        return waited;
                    }
                    return computeNow(key, compute); // 기다림이 너무 길다 — 캐시하지 않고 직접
                }
                Computed done = existing.isCompletedExceptionally() ? null : existing.join();
                if (done != null && done.expiresAt().isAfter(clock.instant())) {
                    return done;
                }
                entries.remove(key, existing); // 만료(또는 실패) — 다시 계산
                continue;
            }
            CompletableFuture<Computed> mine = new CompletableFuture<>();
            if (entries.putIfAbsent(key, mine) != null) {
                continue; // 다른 요청이 먼저 계산을 맡았다
            }
            try {
                Computed computed = computeNow(key, compute);
                mine.complete(computed);
                trim();
                return computed;
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

    private Computed computeNow(Key key, Function<Instant, DashboardGadgetData> compute) {
        Instant at = clock.instant();
        return new Computed(compute.apply(at), at, at.plus(ttl(key.type())));
    }

    /** 계산 중인 값을 기다린다 — 너무 길면 null. 계산이 실패했으면 그 오류를 그대로 던진다 */
    private static Computed await(CompletableFuture<Computed> future) {
        try {
            return future.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("가젯 계산을 기다리다 끊겼다", e);
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

    /** 1분에 한 번 또는 상한을 넘으면 만료된 항목을 지우고, 그래도 상한을 넘으면 끝난 항목을 모두 비운다(계산 중인 것은 둔다) */
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
