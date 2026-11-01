package com.community.hotdeal.service;

import com.community.hotdeal.exception.DealLinkCheckRateLimitException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 링크 판정(OG 가져오기) 회원 한도 — 1분에 {@value #PER_MINUTE}회(서버 규칙 1, 넘으면 {@code 429 DEAL_LINK_CHECK_RATE_LIMIT} + {@code Retry-After}).
 * 캐시에 든 주소도 센다(보수적으로). 서버 메모리라 서버마다 따로 센다.
 */
@Component
public class DealLinkCheckLimiter {

    public static final int PER_MINUTE = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int SWEEP_AT = 10_000;

    private final Map<Long, ArrayDeque<Instant>> windows = new ConcurrentHashMap<>();

    /** 한 번 쓴다 — 한도를 넘으면 429(이번 요청은 세지 않는다) */
    public void acquire(long memberId, Instant now) {
        if (windows.size() > SWEEP_AT) {
            sweep(now);
        }
        ArrayDeque<Instant> times = windows.computeIfAbsent(memberId, k -> new ArrayDeque<>());
        synchronized (times) {
            Instant from = now.minus(WINDOW);
            while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
                times.pollFirst();
            }
            if (times.size() >= PER_MINUTE) {
                long wait = Math.max(1, Duration.between(now, times.peekFirst().plus(WINDOW)).toMillis() / 1000 + 1);
                throw new DealLinkCheckRateLimitException(wait);
            }
            times.addLast(now);
        }
    }

    private void sweep(Instant now) {
        Instant from = now.minus(WINDOW);
        windows.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || !e.getValue().peekLast().isAfter(from);
            }
        });
    }
}
