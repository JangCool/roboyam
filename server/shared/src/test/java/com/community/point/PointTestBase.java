package com.community.point;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** 포인트 시험 클래스의 바탕 — 시험 DB 위의 {@link PointTestKit} 하나를 같이 쓰고, 시험마다 시계를 지금으로 되돌린다 */
abstract class PointTestBase {

    protected static PointTestKit kit;

    @BeforeAll
    static void kitUp() {
        if (kit == null) {
            kit = new PointTestKit();
        }
    }

    @BeforeEach
    void resetClock() {
        kit.clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
