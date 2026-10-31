package com.community.bizapplication.entity;

import java.time.Instant;

/** 예약(SCHEDULED) 안의 시각 단계 — 저장하지 않고 계산한다. 반열림 [시작, 끝): 시작 ≤ 지금 < 끝 = 진행, 끝 ≤ 지금 = 종료 */
public enum BizPhase {
    UPCOMING, RUNNING, ENDED;

    public static BizPhase of(Instant start, Instant end, Instant now) {
        if (!now.isBefore(end)) {
            return ENDED;
        }
        return now.isBefore(start) ? UPCOMING : RUNNING;
    }
}
