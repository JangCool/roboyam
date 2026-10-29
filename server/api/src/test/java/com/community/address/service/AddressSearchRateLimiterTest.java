package com.community.address.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.address.exception.AddressSearchRateLimitedException;
import org.junit.jupiter.api.Test;

/** 주소 검색 IP 한도 — 1분에 60번, 넘으면 429 + 다시 할 수 있기까지의 초, 창이 지나면 다시 된다 */
class AddressSearchRateLimiterTest {

    @Test
    void 한_IP_가_1분에_60번까지_그다음은_429() {
        AddressSearchRateLimiter limiter = new AddressSearchRateLimiter();
        long start = 1_000_000L;
        for (int i = 0; i < AddressSearchRateLimiter.PER_MINUTE; i++) {
            limiter.acquire("203.0.113.7", start + i * 100L);
        }

        assertThatThrownBy(() -> limiter.acquire("203.0.113.7", start + 10_000L))
                .isInstanceOfSatisfying(AddressSearchRateLimitedException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo("ADDRESS_SEARCH_RATE_LIMITED");
                    assertThat(e.getStatus().value()).isEqualTo(429);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(50L); // 첫 검색(start)에서 60초 뒤
                });
        assertThatCode(() -> limiter.acquire("198.51.100.1", start + 10_000L)).doesNotThrowAnyException(); // 다른 IP 는 따로 센다
    }

    @Test
    void 창이_지나면_다시_된다() {
        AddressSearchRateLimiter limiter = new AddressSearchRateLimiter();
        for (int i = 0; i < AddressSearchRateLimiter.PER_MINUTE; i++) {
            limiter.acquire("203.0.113.7", 0L);
        }
        assertThatCode(() -> limiter.acquire("203.0.113.7", 60_000L)).doesNotThrowAnyException();
    }
}
