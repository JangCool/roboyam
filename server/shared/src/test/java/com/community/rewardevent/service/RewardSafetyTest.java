package com.community.rewardevent.service;

import static com.community.rewardevent.service.RewardEventTestKit.points;
import static com.community.rewardevent.service.RewardEventTestKit.tier;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.rewardevent.dto.response.RewardRunView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 보상 이벤트 — 격리·멱등(event-admin-contract 「상태·방식」·「보상 줄」): 선착순 지급이 실패해도 기준 사건(인증) 트랜잭션은 커밋되고 잡은 자리는 되돌아간다(세이브포인트) ·
 * 같은 멱등 키로 동시에 눌러도 실행 하나·지급 한 번.
 */
class RewardSafetyTest extends RewardEventTestBase {

    @Test
    void 선착순_지급이_실패해도_인증_트랜잭션은_커밋되고_잡은_자리는_되돌아간다() {
        long event = kit.create(kit.firstCome("IDENTITY_VERIFIED", 5, points(100)));
        kit.edits.start(kit.op(), event);
        // 등수·보상 줄을 지워 지급 도중 실패하게 만든다(자리를 잡은 뒤 등수를 읽다 실패)
        kit.jdbc.update("DELETE FROM reward_event_lines WHERE event_id = ?", event);
        kit.jdbc.update("DELETE FROM reward_event_tiers WHERE event_id = ?", event);
        long m = kit.member();

        int got = kit.tx.execute(s -> {
            kit.jdbc.update("UPDATE users SET last_login_at = now() WHERE id = ?", m); // 인증 트랜잭션의 다른 쓰기
            return kit.firstCome.onIdentityVerified(m);
        });

        assertThat(got).isZero(); // 이 이벤트만 진행 중(시험마다 남은 선착순을 닫는다) — 실패해 0
        assertThat(kit.jdbc.queryForObject("SELECT last_login_at IS NOT NULL FROM users WHERE id = ?", Boolean.class, m)).isTrue();
        assertThat(kit.jdbc.queryForObject("SELECT first_come_count FROM reward_events WHERE id = ?", Integer.class, event)).isZero();
        assertThat(kit.recipientStatus(event, m)).isNull();
    }

    @Test
    void 같은_멱등_키로_동시에_지급해도_실행은_하나_지급은_한_번() throws Exception {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(250))), m);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<RewardRunView>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return kit.grants.grant(kit.op(), event, "double-click");
                }));
            }
            go.countDown();
            Set<String> runIds = new HashSet<>();
            for (Future<RewardRunView> f : futures) {
                runIds.add(f.get(60, TimeUnit.SECONDS).id());
            }
            assertThat(runIds).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM reward_event_runs WHERE event_id = ?", Integer.class, event)).isEqualTo(1);
        assertThat(kit.ledger(m, "EVENT_REWARD")).hasSize(1);
        assertThat(kit.balance(m)[3]).isEqualTo(250);
    }
}
