package com.community.mfa;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 시험이 시각을 정하고 옮기는 시계 — 2차 인증 코드(TOTP 칸·2차 대기 5분·유예·신뢰 기기 기간·실패 잠금)만 이 시계를 쓴다 */
public class MutableClock extends Clock {

    private volatile Instant now = Instant.now();

    public void set(Instant at) {
        this.now = at;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
