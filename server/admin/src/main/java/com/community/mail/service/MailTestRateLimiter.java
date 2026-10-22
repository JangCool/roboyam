package com.community.mail.service;

import com.community.mail.exception.MailTestRateLimitedException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 메일 서버 시험 발송의 횟수 제한 — <b>운영자 한 명이 1분에 {@value #PER_MINUTE} 번</b>까지(알림 도구 연결 시험 {@code ChannelTestRateLimiter} 와 같은 방식).
 * 시험은 메일 서버에 실제로 접속해 메일을 보내므로 연타하거나 서버를 메일 발사대로 쓰지 못하게 한다. 이 서버 안에서 센다(서버가 여러 대면 대수만큼 늘 수 있다 — 남용 방지용이다).
 */
@Component
public class MailTestRateLimiter {

    static final int PER_MINUTE = 5;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<Long, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    /** 이번 시험을 센다. 한도를 넘었으면 429 */
    public void acquire(long operatorId) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> window = attempts.computeIfAbsent(operatorId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= PER_MINUTE) {
                long retry = Math.max(1, (WINDOW_MILLIS - (now - window.peekFirst()) + 999) / 1000);
                throw new MailTestRateLimitedException(retry);
            }
            window.addLast(now);
        }
        if (attempts.size() > 1000) {
            attempts.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= WINDOW_MILLIS;
                }
            });
        }
    }
}
