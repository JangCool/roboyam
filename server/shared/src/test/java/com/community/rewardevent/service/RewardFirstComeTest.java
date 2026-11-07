package com.community.rewardevent.service;

import static com.community.rewardevent.service.RewardEventTestKit.points;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.rewardevent.dto.request.RewardEventSaveRequest;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.exception.RewardLargeRequiredException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 보상 이벤트 — 선착순 자동 지급(event-admin-contract 「상태·방식」 FIRST_COME): 기준 사건 트랜잭션 안에서 자동 지급 · 인원 초과 없음(동시성) · 1인 1회 · 기간 밖 · 인원이 차면 자동 마감 +
 * 감사 · 정지·탈퇴·운영자 회원 계정 제외 · 기준 사건이 다르면 받지 않는다 · 날마다 지급 내역 한 줄 · 만들기·시작 때 고액 검사.
 */
class RewardFirstComeTest extends RewardEventTestBase {

    private long startedFirstCome(String trigger, int limit, int amount) {
        long id = kit.create(kit.firstCome(trigger, limit, points(amount)));
        kit.edits.start(kit.op(), id);
        return id;
    }

    private int verify(long userId) {
        return kit.tx.execute(s -> kit.firstCome.onIdentityVerified(userId));
    }

    private long count(long event) {
        return kit.jdbc.queryForObject("SELECT count(*) FROM reward_event_recipients WHERE event_id = ?", Long.class, event);
    }

    @Test
    void 본인인증_기준_이벤트는_인증_트랜잭션_안에서_바로_지급하고_날마다_지급_내역_한_줄에_더한다() {
        long event = startedFirstCome("IDENTITY_VERIFIED", 10, 1_000);
        long a = kit.member();
        long b = kit.member();
        verify(a);
        verify(b);
        assertThat(kit.recipientStatus(event, a)).isEqualTo("GRANTED");
        assertThat(kit.balance(a)[3]).isEqualTo(1_000);
        assertThat(kit.jdbc.queryForObject("SELECT method FROM reward_event_recipients WHERE event_id = ? AND user_id = ?", String.class, event, a)).isEqualTo("FIRST_COME");
        List<RewardRunView> runs = kit.queries.runs(event);
        assertThat(runs).hasSize(1);
        assertThat(runs.get(0).kind()).isEqualTo("AUTO");
        assertThat(runs.get(0).operator()).isNull();
        assertThat(runs.get(0).day()).isEqualTo(RewardEventViews.day(kit.clock.instant()));
        assertThat(runs.get(0).success()).isEqualTo(2);
        assertThat(runs.get(0).points()).isEqualTo(2_000);
        assertThat(kit.jdbc.queryForObject("SELECT first_come_count FROM reward_events WHERE id = ?", Integer.class, event)).isEqualTo(2);
        // 이메일 인증 기준 사건은 이 이벤트를 받지 않는다
        long c = kit.member();
        kit.tx.execute(s -> kit.firstCome.onEmailVerified(c));
        assertThat(kit.recipientStatus(event, c)).isNull();
        kit.assertInvariants(a, b);
    }

    @Test
    void 같은_회원은_한_번만_받는다() {
        long event = startedFirstCome("EMAIL_VERIFIED", 10, 300);
        long m = kit.member();
        kit.tx.execute(s -> kit.firstCome.onEmailVerified(m));
        kit.tx.execute(s -> kit.firstCome.onEmailVerified(m));
        assertThat(count(event)).isEqualTo(1);
        assertThat(kit.balance(m)[3]).isEqualTo(300);
        assertThat(kit.ledger(m, "EVENT_REWARD")).hasSize(1);
    }

    @Test
    void 동시에_인증해도_인원을_넘지_않고_인원이_차면_자동_마감과_감사() throws Exception {
        int limit = 3;
        long event = startedFirstCome("IDENTITY_VERIFIED", limit, 100);
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            members.add(kit.member());
        }
        ExecutorService pool = Executors.newFixedThreadPool(members.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (Long m : members) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return verify(m);
                }));
            }
            go.countDown();
            int got = 0;
            for (Future<Integer> f : futures) {
                got += f.get(60, TimeUnit.SECONDS);
            }
            assertThat(got).isEqualTo(limit);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count(event)).isEqualTo(limit);
        assertThat(kit.jdbc.queryForObject("SELECT first_come_count FROM reward_events WHERE id = ?", Integer.class, event)).isEqualTo(limit);
        long paid = members.stream().filter(m -> kit.balance(m)[3] == 100).count();
        assertThat(paid).isEqualTo(limit);
        // 인원이 차 자동 마감 → 대기가 없어 지급 끝
        assertThat(kit.eventStatus(event)).isEqualTo("DONE");
        assertThat(kit.audits(event)).contains(AuditActions.REWARD_EVENT_AUTO_CLOSE);
        assertThat(kit.jdbc.queryForObject("SELECT detail FROM audit_logs WHERE target_id = ? AND action = ?", String.class, Long.toString(event),
                AuditActions.REWARD_EVENT_AUTO_CLOSE)).contains("FULL");
        // 다 찬 뒤 인증한 회원은 받지 않는다
        long late = kit.member();
        verify(late);
        assertThat(kit.recipientStatus(event, late)).isNull();
    }

    @Test
    void 기간_밖이면_받지_않는다() {
        RewardEventSaveRequest future = kit.firstCome("IDENTITY_VERIFIED", 5, points(100));
        future = new RewardEventSaveRequest(future.name(), "", kit.clock.instant().plus(Duration.ofDays(1)), kit.clock.instant().plus(Duration.ofDays(2)), "", "FIRST_COME",
                future.firstCome(), null, null, true, future.tiers());
        long notYet = kit.create(future);
        kit.edits.start(kit.op(), notYet);
        long m = kit.member();
        verify(m);
        assertThat(kit.recipientStatus(notYet, m)).isNull();

        long ended = startedFirstCome("IDENTITY_VERIFIED", 5, 100);
        kit.jdbc.update("UPDATE reward_events SET start_at = ?, end_at = ? WHERE id = ?", java.sql.Timestamp.from(kit.clock.instant().minus(Duration.ofDays(3))),
                java.sql.Timestamp.from(kit.clock.instant().minusSeconds(1)), ended);
        long n = kit.member();
        verify(n);
        assertThat(kit.recipientStatus(ended, n)).isNull();
        // 끝 시각이 지난 진행 중은 읽을 때 마감으로 본다(배치 없음)
        assertThat(kit.queries.detail(ended).status()).isEqualTo("CLOSED");
        assertThat(kit.eventStatus(ended)).isEqualTo("ACTIVE");
    }

    @Test
    void 준비_상태면_받지_않는다() {
        long draft = kit.create(kit.firstCome("IDENTITY_VERIFIED", 5, points(100)));
        long m = kit.member();
        verify(m);
        assertThat(kit.recipientStatus(draft, m)).isNull();
    }

    @Test
    void 정지_탈퇴_운영자_회원_계정은_받지_않는다() {
        long event = startedFirstCome("IDENTITY_VERIFIED", 10, 100);
        long suspended = kit.member();
        long withdrawn = kit.member();
        long operatorAccount = kit.member();
        kit.status(suspended, "SUSPENDED");
        kit.status(withdrawn, "WITHDRAWN");
        kit.operatorAccount(operatorAccount);
        verify(suspended);
        verify(withdrawn);
        verify(operatorAccount);
        assertThat(count(event)).isZero();
        assertThat(kit.jdbc.queryForObject("SELECT first_come_count FROM reward_events WHERE id = ?", Integer.class, event)).isZero();
    }

    @Test
    void 고액_선착순은_만들기와_시작_때_고액_권한이_없으면_403_거부_감사() {
        int threshold = kit.policy.lots().adjustLargeThreshold();
        assertThatThrownBy(() -> kit.edits.create(kit.op(false), kit.firstCome("IDENTITY_VERIFIED", 5, points(threshold + 1))))
                .isInstanceOf(RewardLargeRequiredException.class);
        assertThat(kit.largeDenied.get()).isEqualTo(1);
        long id = kit.create(kit.firstCome("IDENTITY_VERIFIED", 5, points(threshold + 1)));
        assertThatThrownBy(() -> kit.edits.start(kit.op(false), id)).isInstanceOf(RewardLargeRequiredException.class);
        assertThat(kit.largeDenied.get()).isEqualTo(2);
        assertThat(kit.eventStatus(id)).isEqualTo("DRAFT");
        kit.edits.start(kit.op(true), id);
        assertThat(kit.eventStatus(id)).isEqualTo("ACTIVE");
    }

    @Test
    void 받은_수_아래로_인원을_줄일_수_없다() {
        long event = startedFirstCome("IDENTITY_VERIFIED", 10, 100);
        verify(kit.member());
        verify(kit.member());
        var d = kit.queries.detail(event);
        RewardEventSaveRequest less = new RewardEventSaveRequest(d.name(), d.memo(), d.startAt(), d.endAt(), d.noticeUrl(), d.mode(),
                new com.community.rewardevent.dto.RewardFirstComeConfig("IDENTITY_VERIFIED", 1), null, null, d.excludeOperators(), d.tiers());
        assertThatThrownBy(() -> kit.edits.update(kit.op(), event, less)).isInstanceOf(com.community.zconfiguration.exception.ValidationException.class)
                .satisfies(e -> assertThat(((com.community.zconfiguration.exception.ValidationException) e).getFields()).containsKey("firstCome.limit"));
        RewardEventSaveRequest more = new RewardEventSaveRequest(d.name(), d.memo(), d.startAt(), d.endAt(), d.noticeUrl(), d.mode(),
                new com.community.rewardevent.dto.RewardFirstComeConfig("IDENTITY_VERIFIED", 2), null, null, d.excludeOperators(), d.tiers());
        assertThat(kit.edits.update(kit.op(), event, more).firstCome().limit()).isEqualTo(2);
    }
}
