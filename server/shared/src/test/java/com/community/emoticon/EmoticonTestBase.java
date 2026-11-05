package com.community.emoticon;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** 로보티콘 시험 클래스의 바탕 — 시험 DB 위의 {@link EmoticonTestKit} 하나를 같이 쓰고, 시험마다 시계를 지금으로 되돌리고 모은 감사·알림을 비운다 */
abstract class EmoticonTestBase {

    protected static EmoticonTestKit kit;

    @BeforeAll
    static void kitUp() {
        if (kit == null) {
            kit = new EmoticonTestKit();
        }
    }

    @BeforeEach
    void reset() {
        kit.clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        kit.audits.clear();
        kit.operatorNotices.clear();
    }
}
