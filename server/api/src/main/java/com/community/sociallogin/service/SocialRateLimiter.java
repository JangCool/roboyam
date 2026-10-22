package com.community.sociallogin.service;

import com.community.sociallogin.config.SocialLoginProperties;
import com.community.sociallogin.exception.SocialRateLimitedException;
import com.community.zconfiguration.crypto.Digests;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인의 IP 기준 요청 한도 — 창({@code roboyam.social.rate-window}, 기본 10분) 안에 시작(로그인·연결) {@code start-per-window}(30)번,
 * 가입 완료 {@code complete-per-window}(10)번까지. 넘으면 {@code 429 SOCIAL_RATE_LIMITED}(+{@code Retry-After}).
 *
 * <p>시작은 세션 행을 만들고 공급자로 보내는 일이라, 버튼 연타·스크립트로 세션 표와 외부 요청을 늘리지 못하게 한다. 키는 {@code sha256(구분|IP)} 이라 IP 원문을 메모리에도 두지 않는다.
 * 이 서버 안에서 센다({@code ChannelTestRateLimiter} 와 같은 방식) — 서버가 여러 대면 대수만큼 늘 수 있다(남용 방지용 한도다).
 */
@Component
public class SocialRateLimiter {

    /** 기억하는 키가 이보다 많아지면 끝난 창을 비운다 */
    private static final int SWEEP_THRESHOLD = 5000;

    public enum Bucket {
        START,
        COMPLETE
    }

    private final long windowMillis;
    private final int startLimit;
    private final int completeLimit;
    private final Map<String, ArrayDeque<Long>> windows = new ConcurrentHashMap<>();

    public SocialRateLimiter(SocialLoginProperties props) {
        this.windowMillis = props.rateWindow().toMillis();
        this.startLimit = props.startPerWindow();
        this.completeLimit = props.completePerWindow();
    }

    /** 이번 요청을 센다. 한도를 넘었으면 429 */
    public void acquire(Bucket bucket, String clientIp) {
        int limit = bucket == Bucket.START ? startLimit : completeLimit;
        long now = System.currentTimeMillis();
        String key = Digests.sha256Hex(bucket.name() + "|" + (clientIp == null ? "" : clientIp));
        ArrayDeque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
                window.pollFirst();
            }
            if (window.size() >= limit) {
                long retry = Math.max(1, (windowMillis - (now - window.peekFirst()) + 999) / 1000);
                throw new SocialRateLimitedException(retry);
            }
            window.addLast(now);
        }
        if (windows.size() > SWEEP_THRESHOLD) {
            windows.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty() || now - entry.getValue().peekLast() >= windowMillis;
                }
            });
        }
    }
}
