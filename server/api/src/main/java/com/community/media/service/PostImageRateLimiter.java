package com.community.media.service;

import com.community.media.config.PostImageProperties;
import com.community.media.exception.ImageRateLimitedException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 글 이미지 올리기의 회원 단위 속도 한도 — 1분 창 안에 {@code roboyam.post-image.per-minute}(기본 20)장까지. 넘으면 {@code 429 IMAGE_RATE_LIMITED}(+{@code Retry-After}).
 * 이 서버 안에서 센다({@code SocialRateLimiter} 와 같은 방식) — 서버가 여러 대면 대수만큼 늘 수 있다(남용 방지용 한도이고, 하루 한도는 DB 가 센다).
 */
@Component
public class PostImageRateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    /** 기억하는 회원이 이보다 많아지면 끝난 창을 비운다 */
    private static final int SWEEP_THRESHOLD = 5000;

    private final int limit;
    private final Map<Long, ArrayDeque<Long>> windows = new ConcurrentHashMap<>();

    public PostImageRateLimiter(PostImageProperties props) {
        this.limit = Math.max(1, props.perMinute());
    }

    /** 이번 올리기를 센다. 한도를 넘었으면 429 */
    public void acquire(long userId) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> window = windows.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= WINDOW_MILLIS) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                long retry = Math.max(1, (WINDOW_MILLIS - (now - window.peekFirst()) + 999) / 1000);
                throw new ImageRateLimitedException(retry);
            }
            window.addLast(now);
        }
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= WINDOW_MILLIS;
                }
            });
        }
    }
}
