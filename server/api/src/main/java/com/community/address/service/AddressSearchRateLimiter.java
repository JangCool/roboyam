package com.community.address.service;

import com.community.address.exception.AddressSearchRateLimitedException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 자체 주소 DB 검색의 횟수 제한 — <b>IP 하나가 1분에 {@value #PER_MINUTE} 번</b>까지(로그인 없이 부르는 검색이라 IP 로 센다). 화면은 입력을 0.25초 모아 부르므로
 * 사람이 치는 속도로는 닿지 않고, 주소 DB 를 긁어 가거나 넓은 검색어로 DB 를 두드리는 것을 막는다. 이 서버 메모리에서 센다(서버가 여러 대면 대수만큼 늘 수 있다 — 남용 방지용).
 */
@Component
public class AddressSearchRateLimiter {

    static final int PER_MINUTE = 60;
    private static final long WINDOW_MILLIS = 60_000L;
    private static final int SWEEP_AT = 5000;

    private final Map<String, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    /** 이번 검색을 센다. 한도를 넘었으면 429 */
    public void acquire(String ip) {
        acquire(ip, System.currentTimeMillis());
    }

    void acquire(String ip, long now) {
        ArrayDeque<Long> window = attempts.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= PER_MINUTE) {
                long retry = Math.max(1, (WINDOW_MILLIS - (now - window.peekFirst()) + 999) / 1000);
                throw new AddressSearchRateLimitedException(retry);
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
