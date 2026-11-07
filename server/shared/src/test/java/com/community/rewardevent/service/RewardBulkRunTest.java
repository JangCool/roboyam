package com.community.rewardevent.service;

import static com.community.rewardevent.service.RewardEventTestKit.item;
import static com.community.rewardevent.service.RewardEventTestKit.points;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.rewardevent.dto.RewardBulkConfig;
import com.community.rewardevent.dto.request.RewardTargetPreviewRequest;
import com.community.rewardevent.dto.response.RewardEventAuditView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.exception.RewardLargeRequiredException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * 보상 이벤트 — 일괄 지급(event-admin-contract 「상태·방식」 BULK · 「보상 줄」 대량): 대상은 지급 실행 때 조건으로 · 한 덩어리 = 한 트랜잭션으로 나눠 처리 · 서버가 멈춰도 남은 덩어리를 이어 함 ·
 * 한 실행은 한 곳만(SKIP LOCKED) · 일괄에 포인트가 있으면 고액 권한 · 조건(등급 서열 · 가입일 · 본인인증 · 최근 로그인) · 정지·탈퇴·운영자 회원 계정 제외 · 감사(지급 돈 관련 5년) ·
 * 기록 탭.
 */
class RewardBulkRunTest extends RewardEventTestBase {

    /** 이 시험만의 가입일 — 일괄 조건이 다른 시험의 회원을 고르지 않게 */
    private LocalDate uniqueDay() {
        return LocalDate.of(1990, 1, 1).plusDays(Math.floorMod(kit.ids.nextId() >>> 22, 9000));
    }

    private Instant at(LocalDate day) {
        return day.atTime(12, 0).atZone(com.community.zconfiguration.time.ServiceTime.ZONE).toInstant();
    }

    private static RewardBulkConfig condition(LocalDate day) {
        return new RewardBulkConfig("CONDITION", null, day, day, false, null);
    }

    private long startedBulk(RewardBulkConfig bulk, com.community.rewardevent.dto.RewardLineDto... lines) {
        long id = kit.create(kit.bulk(bulk, lines));
        kit.edits.start(kit.op(), id);
        return id;
    }

    @Test
    void 일괄은_덩어리마다_한_트랜잭션으로_나눠_처리하고_남은_덩어리를_이어_하며_한_실행은_한_곳만_처리한다() throws Exception {
        LocalDate day = uniqueDay();
        List<Long> members = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            members.add(kit.member(at(day)));
        }
        long event = startedBulk(condition(day), points(10));
        kit.grants.chunks(3, 2, 0); // 대상 3명씩 · 지급 2명씩 · 요청 안에서는 처리하지 않는다

        RewardRunView run = kit.grants.grant(kit.op(), event, "bulk-" + event);

        assertThat(run.status()).isEqualTo("RUNNING");
        assertThat(run.targetCount()).isEqualTo(7); // 시작 때 조건으로 미리 센 수
        assertThat(kit.eventStatus(event)).isEqualTo("CLOSED"); // 진행 중에 일괄 지급을 시작하면 마감
        long runId = Long.parseLong(run.id());
        assertThat(kit.queries.detail(event).runningRun()).isNotNull();
        assertThat(kit.queries.detail(event).actions().grantBlockedReason()).isEqualTo("RUNNING");

        // 대상 만들기 한 덩어리 — 3명
        assertThat(kit.grants.drain(runId, 1)).isEqualTo(1);
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM reward_event_recipients WHERE event_id = ?", Integer.class, event)).isEqualTo(3);

        // 다른 곳(다른 서버)이 이 실행을 잡고 있는 동안은 처리하지 않는다(SKIP LOCKED)
        ExecutorService pool = Executors.newSingleThreadExecutor();
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<Boolean> holder = pool.submit(() -> kit.tx.execute(s -> {
                boolean got = kit.eventStore.claimRun(runId);
                held.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return got;
            }));
            assertThat(held.await(30, TimeUnit.SECONDS)).isTrue();
            assertThat(kit.grants.drain(runId, 5)).isZero();
            release.countDown();
            assertThat(holder.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // 서버가 멈췄다 다시 떠도(새 워커) 남은 덩어리를 이어 한다
        int ticks = 0;
        while (!kit.grants.finished(runId) && ticks++ < 30) {
            kit.worker.runOnce();
        }
        assertThat(kit.grants.finished(runId)).isTrue();
        RewardRunView done = kit.queries.runs(event).get(0);
        assertThat(done.status()).isEqualTo("DONE");
        assertThat(done.targetCount()).isEqualTo(7);
        assertThat(done.progress().done()).isEqualTo(7);
        assertThat(done.success()).isEqualTo(7);
        assertThat(done.points()).isEqualTo(70);
        for (Long m : members) {
            assertThat(kit.recipientStatus(event, m)).isEqualTo("GRANTED");
            assertThat(kit.balance(m)[3]).isEqualTo(10);
        }
        assertThat(kit.jdbc.queryForObject("SELECT method FROM reward_event_recipients WHERE event_id = ? LIMIT 1", String.class, event)).isEqualTo("CONDITION");
        assertThat(kit.eventStatus(event)).isEqualTo("DONE");
        assertThat(kit.audits(event)).containsSubsequence(AuditActions.REWARD_EVENT_CREATE, AuditActions.REWARD_EVENT_START, AuditActions.REWARD_EVENT_GRANT,
                AuditActions.REWARD_EVENT_GRANT);
        kit.assertInvariants(members.stream().mapToLong(Long::longValue).toArray());
        // 일괄은 한 번만 지급한다
        assertThat(kit.queries.detail(event).actions().canGrant()).isFalse();
    }

    @Test
    void 일괄에_포인트가_있으면_금액과_상관없이_고액_권한이_필요하다_아이템만이면_필요_없다() {
        LocalDate day = uniqueDay();
        long m = kit.member(at(day));
        long withPoints = startedBulk(condition(day), points(1));
        assertThat(kit.queries.grantPreview(withPoints).largeRequired()).isTrue();
        assertThatThrownBy(() -> kit.grants.grant(kit.op(false), withPoints, "x")).isInstanceOf(RewardLargeRequiredException.class);
        assertThat(kit.largeDenied.get()).isEqualTo(1);
        assertThat(kit.eventStatus(withPoints)).isEqualTo("ACTIVE");
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM reward_event_runs WHERE event_id = ?", Integer.class, withPoints)).isZero();

        long itemsOnly = startedBulk(condition(day), item(kit.ticket(), 1));
        assertThat(kit.queries.grantPreview(itemsOnly).largeRequired()).isFalse();
        kit.grants.grant(kit.op(false), itemsOnly, "y");
        assertThat(kit.recipientStatus(itemsOnly, m)).isEqualTo("GRANTED");
    }

    @Test
    void 일괄_조건은_등급_서열_가입일_본인인증_최근_로그인이고_정지_탈퇴_운영자_회원_계정은_늘_뺀다() {
        LocalDate day = uniqueDay();
        List<Object[]> grades = kit.jdbc.query("SELECT grade_key, level FROM grades ORDER BY level", (rs, i) -> new Object[] {rs.getString(1), rs.getInt(2)});
        String high = (String) grades.get(1)[0];
        int highLevel = (Integer) grades.get(1)[1];
        long ok = kit.member(at(day));
        long lowGrade = kit.member(at(day));
        long notVerified = kit.member(at(day));
        long idle = kit.member(at(day));
        long suspended = kit.member(at(day));
        long operatorAccount = kit.member(at(day));
        long otherDay = kit.member(at(day.plusDays(1)));
        for (long u : new long[] {ok, notVerified, idle, suspended, operatorAccount, otherDay}) {
            kit.jdbc.update("UPDATE profiles SET grade_key = ? WHERE user_id = ?", high, u);
        }
        for (long u : new long[] {ok, lowGrade, idle, suspended, operatorAccount, otherDay}) {
            kit.jdbc.update("INSERT INTO identity_verifications (user_id, di_hash, birth_date, provider, provider_tx_id, verified_at) VALUES (?, ?, '1990-01-01', 'FAKE', ?, now())",
                    u, HexFormat.of().formatHex(java.nio.ByteBuffer.allocate(32).putLong(u).putLong(kit.ids.nextId()).array()), "tx-" + u);
        }
        for (long u : new long[] {ok, lowGrade, notVerified, suspended, operatorAccount, otherDay}) {
            kit.jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", Timestamp.from(kit.clock.instant().minus(Duration.ofDays(1))), u);
        }
        kit.jdbc.update("UPDATE users SET last_login_at = ? WHERE id = ?", Timestamp.from(kit.clock.instant().minus(Duration.ofDays(30))), idle);
        kit.status(suspended, "SUSPENDED");
        kit.operatorAccount(operatorAccount);
        RewardBulkConfig c = new RewardBulkConfig("CONDITION", highLevel, day, day, true, 7);

        assertThat(kit.selection.previewTargets(new RewardTargetPreviewRequest(c, true)).count()).isEqualTo(1);
        assertThat(kit.selection.previewTargets(new RewardTargetPreviewRequest(c, false)).count()).isEqualTo(2); // 운영자 회원 계정을 빼지 않으면 들어온다
        assertThat(kit.selection.previewTargets(new RewardTargetPreviewRequest(new RewardBulkConfig("CONDITION", null, day, day, false, null), true)).count())
                .isEqualTo(4); // ok · lowGrade · notVerified · idle

        long event = startedBulk(c, item(kit.ticket(), 1));
        kit.grants.grant(kit.op(), event, "cond");
        assertThat(kit.jdbc.queryForList("SELECT user_id FROM reward_event_recipients WHERE event_id = ?", Long.class, event)).containsExactly(ok);
    }

    @Test
    void 지급_재시도_회수는_돈_관련_감사_5년이고_기록_탭은_이_이벤트의_감사를_요청_언어로_보인다() {
        assertThat(AuditRetention.declared(AuditActions.REWARD_EVENT_GRANT)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.declared(AuditActions.REWARD_EVENT_RETRY)).contains(AuditRetentionClass.MONEY);
        assertThat(AuditRetention.declared(AuditActions.REWARD_EVENT_REVOKE)).contains(AuditRetentionClass.MONEY);
        for (String a : List.of(AuditActions.REWARD_EVENT_CREATE, AuditActions.REWARD_EVENT_UPDATE, AuditActions.REWARD_EVENT_START, AuditActions.REWARD_EVENT_CLOSE,
                AuditActions.REWARD_EVENT_AUTO_CLOSE, AuditActions.REWARD_EVENT_DRAW, AuditActions.REWARD_EVENT_RECIPIENT_ADD, AuditActions.REWARD_EVENT_RECIPIENT_REMOVE,
                AuditActions.REWARD_EVENT_DELIVERED, AuditActions.REWARD_EVENT_CANCEL)) {
            assertThat(AuditRetention.declared(a)).as(a).contains(AuditRetentionClass.GENERAL);
        }
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(RewardEventTestKit.tier("1등", 5, points(100))), m);
        kit.grants.grant(kit.op(), event, "audit-" + event);
        String rid = Long.toString(kit.jdbc.queryForObject("SELECT id FROM reward_event_recipients WHERE event_id = ?", Long.class, event));
        kit.grants.revoke(kit.op(), event, List.of(rid), "중복 계정이에요");

        List<RewardEventAuditView> audit = kit.queries.audit(event);
        assertThat(audit).extracting(RewardEventAuditView::action).containsSubsequence("REVOKE", "GRANT", "GRANT", "CLOSE", "RECIPIENT_ADD", "START", "CREATE");
        assertThat(audit).allSatisfy(a -> assertThat(a.detail()).isNotBlank());
        assertThat(audit.get(0).detail()).contains("중복 계정이에요");
        assertThat(audit.get(0).operator().id()).isEqualTo(Long.toString(kit.operator));
        assertThat(kit.jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE target_id = ? AND action = ?", String.class, Long.toString(event),
                AuditActions.REWARD_EVENT_REVOKE)).isEqualTo("MONEY");
    }
}
