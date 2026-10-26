package com.community.commerce;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** 시험이 시각을 정하고 옮기는 시계 — 본인인증(30분 만료·만 19세)·기능 제한(기간)·탈퇴 대비(30일) 서비스가 이 시계를 쓴다 */
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
