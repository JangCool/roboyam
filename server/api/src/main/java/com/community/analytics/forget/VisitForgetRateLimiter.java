package com.community.analytics.forget;

import com.community.analytics.exception.VisitForgetRateLimitedException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 방문 기록 삭제 요청의 횟수 제한 — <b>IP 하나가 1시간에 {@value #PER_HOUR} 번</b>까지(로그인 없이 부르는 요청이라 IP 로 센다). 사람은 한두 번이면 되고, 요청 표를 무작위 ID 로
 * 채우는 것을 막는다. 이 서버 메모리에서만 센다 — IP 는 DB·로그에 남기지 않는다(서버가 여러 대면 대수만큼 늘 수 있다 — 남용 방지용, 주소 검색 제한과 같은 방식).
 */
@Component
public class VisitForgetRateLimiter {

    static final int PER_HOUR = 5;
    private static final long WINDOW_MILLIS = 60 * 60_000L;
    private static final int SWEEP_AT = 5000;

    private final Map<String, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    /** 이번 요청을 센다. 한도를 넘었으면 429 */
    public void acquire(String ip) {
        acquire(ip == null ? "" : ip, System.currentTimeMillis());
    }

    void acquire(String ip, long now) {
        ArrayDeque<Long> window = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= PER_HOUR) {
                long retry = Math.max(1, (WINDOW_MILLIS - (now - window.peekFirst()) + 999) / 1000);
                throw new VisitForgetRateLimitedException(retry);
            }
            window.addLast(now);
        }
        if (attempts.size() > SWEEP_AT) {
            attempts.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= WINDOW_MILLIS;
                }
            });
        }
    }
}
