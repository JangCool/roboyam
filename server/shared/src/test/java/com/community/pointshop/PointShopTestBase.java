package com.community.pointshop;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** 포인트 상점 시험 클래스의 바탕 — 시험 DB 위의 {@link PointShopTestKit} 하나를 같이 쓰고, 시험마다 시계를 지금으로 되돌리고 모은 감사를 비운다 */
abstract class PointShopTestBase {

    protected static PointShopTestKit kit;

    @BeforeAll
    static void kitUp() {
        if (kit == null) {
            kit = new PointShopTestKit();
        }
    }

    @BeforeEach
    void reset() {
        kit.clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        kit.audits.clear();
        kit.operatorNotices.clear();
        kit.memberEmailEvents.clear();
    }
}
