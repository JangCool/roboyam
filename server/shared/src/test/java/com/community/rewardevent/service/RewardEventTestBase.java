package com.community.rewardevent.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/** 보상 이벤트 시험 클래스의 바탕 — 시험 DB 위의 {@link RewardEventTestKit} 하나를 같이 쓰고, 시험마다 시계를 지금으로 되돌린다 */
abstract class RewardEventTestBase {

    protected static RewardEventTestKit kit;

    @BeforeAll
    static void kitUp() {
        if (kit == null) {
            kit = new RewardEventTestKit();
        }
    }

    @BeforeEach
    void reset() {
        kit.clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        kit.largeDenied.set(0);
        kit.grants.chunks(RewardGrantService.TARGET_CHUNK, RewardGrantService.LINE_CHUNK, RewardGrantService.INLINE_CHUNKS);
    }

    /** 진행 중인 선착순 이벤트를 남기지 않는다 — 다음 시험의 인증이 엉뚱한 이벤트를 받지 않게(시험 DB 를 같이 쓴다) */
    @AfterEach
    void closeFirstCome() {
        kit.jdbc.update("UPDATE reward_events SET status = 'CLOSED', closed_at = now() WHERE status = 'ACTIVE' AND mode = 'FIRST_COME'");
    }
}
