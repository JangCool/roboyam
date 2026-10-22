package com.community.notification.service;

import com.community.notification.config.NotificationProperties;
import com.community.notification.exception.NotificationChannelTestRateLimitedException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 알림 도구 연결 시험의 횟수 제한 — <b>운영자 한 명이 1분에 {@code roboyam.notification.channel-test-per-minute} 번</b>(기본 6)까지. 시험은 외부 서비스에 실제로 요청을 보내므로
 * 버튼을 연타하거나 도구를 돌려가며 눌러 서버를 외부 요청의 발사대로 쓰지 못하게 한다. 이 서버 안에서 센다(서버가 여러 대면 대수만큼 늘 수 있다 — 한도는 남용 방지용이다).
 */
@Component
public class ChannelTestRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int perMinute;
    private final Map<Long, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();

    public ChannelTestRateLimiter(NotificationProperties properties) {
        this.perMinute = Math.max(1, properties.channelTestPerMinute());
    }

    /** 이번 시험을 센다. 한도를 넘었으면 429 */
    public void acquire(long operatorId) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> window = attempts.computeIfAbsent(operatorId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                long retry = Math.max(1, (WINDOW_MILLIS - (now - window.peekFirst()) + 999) / 1000);
                throw new NotificationChannelTestRateLimitedException(retry);
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
