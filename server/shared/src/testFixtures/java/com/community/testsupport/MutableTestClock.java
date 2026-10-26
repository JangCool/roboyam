package com.community.testsupport;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 커머스 시험의 옮길 수 있는 시계(UTC) — 1분 창·24시간·재시도 간격처럼 시각에 걸린 규칙을 기다리지 않고 확인한다 */
public final class MutableTestClock extends Clock {

    private volatile Instant now;

    public MutableTestClock(Instant start) {
        this.now = start;
    }

    public void set(Instant instant) {
        this.now = instant;
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
