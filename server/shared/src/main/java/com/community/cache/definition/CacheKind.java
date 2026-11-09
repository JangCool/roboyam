package com.community.cache.definition;

import java.time.Duration;

/**
 * 캐시 분류 — 공개 화면(공개 API 응답) · 가젯(가젯 데이터 — 유형별) · 설정 사본(설정 표를 읽어 둔 것) · 내부 규칙(서버가 판정에 쓰는 규칙표).
 * 허용 TTL 범위가 분류마다 다르다(cache-admin-contract 「세션 정리 결정」 4).
 */
public enum CacheKind {
    PUBLIC_VIEW(5, 3600),
    GADGET(5, 3600),
    SETTINGS_COPY(5, 600),
    INTERNAL_RULE(5, 300);

    private final int minTtlSeconds;
    private final int maxTtlSeconds;

    CacheKind(int minTtlSeconds, int maxTtlSeconds) {
        this.minTtlSeconds = minTtlSeconds;
        this.maxTtlSeconds = maxTtlSeconds;
    }

    public int minTtlSeconds() {
        return minTtlSeconds;
    }

    public int maxTtlSeconds() {
        return maxTtlSeconds;
    }

    public boolean allows(int ttlSeconds) {
        return ttlSeconds >= minTtlSeconds && ttlSeconds <= maxTtlSeconds;
    }

    public Duration clamp(int ttlSeconds) {
        return Duration.ofSeconds(Math.max(minTtlSeconds, Math.min(maxTtlSeconds, ttlSeconds)));
    }
}
