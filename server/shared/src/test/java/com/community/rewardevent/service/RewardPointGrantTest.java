package com.community.rewardevent.service;

import static com.community.rewardevent.service.RewardEventTestKit.item;
import static com.community.rewardevent.service.RewardEventTestKit.points;
import static com.community.rewardevent.service.RewardEventTestKit.tier;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.point.service.PointService;
import com.community.rewardevent.dto.response.RewardRecipientView;
import com.community.rewardevent.dto.response.RewardRunView;
import com.community.rewardevent.exception.RewardLargeRequiredException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

/**
 * 보상 이벤트 — 포인트 줄(event-admin-contract 「보상 줄」 POINT, commerce.md 5-4): 무상만 · 1년 묶음 · 원장 합 불변식 · 하루 한도 제외 · 같은 줄은 한 번 · 멱등 키 · 빚 상계 ·
 * 동결이면 그 줄만 실패(PARTIAL) · 재시도 · 회수는 무상만 0P 바닥(빚 없음, POINT_PARTIAL) · 고액 권한.
 */
class RewardPointGrantTest extends RewardEventTestBase {

    private static final UnaryOperator<String> RAW = UnaryOperator.identity();

    private RewardRunView grant(long eventId) {
        return kit.grants.grant(kit.op(), eventId, "key-" + eventId);
    }

    @Test
    void 포인트는_무상_묶음_1년으로_원장_사유_EVENT_REWARD_대상_줄_결과이고_불변식이_맞다() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(1_000))), m);
        Instant before = kit.clock.instant();

        RewardRunView run = grant(event);

        assertThat(run.status()).isEqualTo("DONE");
        assertThat(run.success()).isEqualTo(1);
        assertThat(run.points()).isEqualTo(1_000);
        List<Map<String, Object>> rows = kit.ledger(m, "EVENT_REWARD");
        assertThat(rows).hasSize(1);
        Map<String, Object> line = rows.get(0);
        assertThat(line).containsEntry("kind", "EARN").containsEntry("delta", 1_000).containsEntry("free_delta", 1_000L).containsEntry("paid_delta", 0L)
                .containsEntry("ref_type", PointService.REF_REWARD_EVENT_RESULT);
        long resultId = (Long) kit.results(event, m).get(0).get("id");
        assertThat(line.get("ref_id")).isEqualTo(resultId);
        assertThat(line.get("note")).isEqualTo(kit.jdbc.queryForObject("SELECT name FROM reward_events WHERE id = ?", String.class, event));
        Map<String, Object> lot = kit.jdbc.queryForMap("SELECT kind, original_amount, expires_at FROM point_lots WHERE user_id = ? AND source_reason = 'EVENT_REWARD'", m);
        assertThat(lot.get("kind")).isEqualTo("FREE");
        assertThat(lot.get("original_amount")).isEqualTo(1_000L);
        Instant expires = ((Timestamp) lot.get("expires_at")).toInstant();
        assertThat(Duration.between(before, expires).toDays()).isEqualTo(kit.policy.lots().freeValidDays()).isEqualTo(365);
        assertThat(kit.balance(m)).containsExactly(1_000, 0, 0, 1_000);
        kit.assertInvariants(m);
        assertThat(kit.recipientStatus(event, m)).isEqualTo("GRANTED");
        assertThat(kit.eventStatus(event)).isEqualTo("DONE");
        // 회원 알림(끌 수 없는 처리 알림) — 이벤트 이름 · 받은 포인트
        assertThat(kit.jdbc.queryForObject("SELECT payload::text FROM member_notifications WHERE user_id = ? AND type = 'REWARD_EVENT_GRANTED'", String.class, m))
                .contains("\"points\": 1000").contains(Long.toString(event));
    }

    @Test
    void 이벤트_보상은_하루_활동_한도에_들지_않는다() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(5_000))), m);
        grant(event);
        assertThat(kit.inTx(() -> kit.points.earnedToday(m))).isZero();
        assertThat(PointService.DAILY_TOTAL_REASONS).doesNotContain(com.community.point.entity.PointReason.EVENT_REWARD);
    }

    @Test
    void 같은_줄은_두_번_지급되지_않는다_같은_멱등_키는_처음_실행을_돌려주고_원장_유일_색인이_끝까지_막는다() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(700))), m);
        RewardRunView first = kit.grants.grant(kit.op(), event, "same-key");
        RewardRunView again = kit.grants.grant(kit.op(), event, "same-key");
        assertThat(again.id()).isEqualTo(first.id());
        long resultId = (Long) kit.results(event, m).get(0).get("id");
        // 서비스 길로 다시 불러도 0(이미 지급) — 원장 줄은 하나
        assertThat(kit.inTx(() -> kit.points.eventReward(m, resultId, 700, "다시", null))).isZero();
        assertThat(kit.ledger(m, "EVENT_REWARD")).hasSize(1);
        assertThat(kit.balance(m)[3]).isEqualTo(700);
        // 같은 (사유, 대상) 줄을 직접 넣으면 DB 가 막는다
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO point_ledger (id, user_id, kind, reason, delta, free_delta, paid_delta, balance_after, ref_type, ref_id, created_at) "
                + "VALUES (?, ?, 'EARN', 'EVENT_REWARD', 700, 700, 0, 1400, 'REWARD_EVENT_RESULT', ?, now())", kit.ids.nextId(), m, resultId))
                .isInstanceOf(DuplicateKeyException.class);
        kit.assertInvariants(m);
    }

    @Test
    void 미회수_빚이_있으면_같은_트랜잭션에서_먼저_갚는다() {
        long m = kit.member();
        kit.debt(m, 300);
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(1_000))), m);
        grant(event);
        assertThat(kit.ledger(m, "REVOKE_DEBT_OFFSET")).hasSize(1).first().satisfies(r -> assertThat(r.get("delta")).isEqualTo(-300));
        assertThat(kit.balance(m)).containsExactly(700, 0, 0, 700);
        kit.assertInvariants(m);
    }

    @Test
    void 동결_회원은_포인트_줄만_실패하고_다른_줄은_성공해_일부_실패_재시도하면_실패_줄만_다시_지급한다() {
        long frozen = kit.member();
        long normal = kit.member();
        long ticket = kit.ticket();
        kit.freeze(frozen);
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(500), item(ticket, 2))), frozen, normal);

        RewardRunView run = grant(event);

        assertThat(run.failed()).isEqualTo(1);
        assertThat(run.success()).isEqualTo(1);
        assertThat(kit.recipientStatus(event, frozen)).isEqualTo("PARTIAL");
        assertThat(kit.recipientStatus(event, normal)).isEqualTo("GRANTED");
        List<Map<String, Object>> results = kit.results(event, frozen);
        assertThat(results.get(0)).containsEntry("status", "FAILED").containsEntry("reason", "POINT_FROZEN");
        assertThat(results.get(1)).containsEntry("status", "SUCCESS").containsEntry("items", 2);
        assertThat(kit.balance(frozen)[3]).isZero();
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM user_items WHERE user_id = ?", Integer.class, frozen)).isEqualTo(2);
        assertThat(kit.eventStatus(event)).isEqualTo("DONE"); // 실패가 남아도 지급 끝

        kit.unfreeze(frozen);
        RewardRunView retry = kit.grants.retry(kit.op(), event);

        assertThat(retry.kind()).isEqualTo("RETRY");
        assertThat(retry.targetCount()).isEqualTo(1);
        assertThat(retry.success()).isEqualTo(1);
        assertThat(kit.recipientStatus(event, frozen)).isEqualTo("GRANTED");
        assertThat(kit.balance(frozen)[3]).isEqualTo(500);
        // 성공 줄(아이템)은 다시 지급하지 않았다
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM user_items WHERE user_id = ?", Integer.class, frozen)).isEqualTo(2);
        assertThat(kit.ledger(normal, "EVENT_REWARD")).hasSize(1);
        kit.assertInvariants(frozen, normal);
    }

    @Test
    void 회수는_무상에서만_0P까지_빚을_만들지_않고_덜_회수하면_POINT_PARTIAL_유상은_그대로() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(1_000))), m);
        grant(event);
        // 무상 700 을 써 버렸다(운영 차감) · 충전(유상) 2,000
        kit.inTx(() -> kit.points.adjust(m, -700, "시험 차감", kit.operator));
        kit.chargePaid(m, 2_000);
        String rid = recipientId(event, m);

        RewardRunView run = kit.grants.revoke(kit.op(), event, List.of(rid), "중복 계정으로 확인됐어요");

        assertThat(run.kind()).isEqualTo("REVOKE");
        assertThat(run.points()).isEqualTo(300);
        Map<String, Object> result = kit.results(event, m).get(0);
        assertThat(result).containsEntry("status", "REVOKED").containsEntry("reason", "POINT_PARTIAL").containsEntry("revoked_points", 300);
        assertThat(kit.balance(m)).containsExactly(0, 2_000, 0, 2_000); // 무상 0 바닥 · 유상 그대로 · 빚 없음
        assertThat(kit.jdbc.queryForObject("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", Integer.class, m)).isZero();
        assertThat(kit.ledger(m, "EVENT_REWARD_REVOKE")).hasSize(1).first().satisfies(r -> {
            assertThat(r.get("kind")).isEqualTo("REVOKE");
            assertThat(r.get("paid_delta")).isEqualTo(0L);
        });
        assertThat(kit.jdbc.queryForObject("SELECT payload::text FROM member_notifications WHERE user_id = ? AND type = 'REWARD_EVENT_REVOKED'", String.class, m))
                .contains("중복 계정으로 확인됐어요");
        assertThat(kit.recipientStatus(event, m)).isEqualTo("REVOKED");
        RewardRecipientView view = kit.queries.recipients(event, null, null, null, 0, 20, RAW).items().get(0);
        assertThat(view.revokeReason()).isEqualTo("중복 계정으로 확인됐어요");
        assertThat(view.results().get(0).revokedPoints()).isEqualTo(300);
        kit.assertInvariants(m);
    }

    @Test
    void 받은_만큼_남아_있으면_전부_회수한다() {
        long m = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(400))), m);
        grant(event);
        kit.grants.revoke(kit.op(), event, List.of(recipientId(event, m)), "잘못 지급했어요");
        assertThat(kit.results(event, m).get(0)).containsEntry("status", "REVOKED").containsEntry("revoked_points", 400).containsEntry("reason", null);
        assertThat(kit.balance(m)[3]).isZero();
        kit.assertInvariants(m);
    }

    @Test
    void 일인_포인트_합이_고액_문턱을_넘으면_고액_권한이_없을_때_403_과_거부_감사_있으면_지급한다() {
        long m = kit.member();
        int threshold = kit.policy.lots().adjustLargeThreshold();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(threshold), points(1))), m);

        assertThatThrownBy(() -> kit.grants.grant(kit.op(false), event, "large-1")).isInstanceOf(RewardLargeRequiredException.class)
                .satisfies(e -> assertThat(((RewardLargeRequiredException) e).getErrorCode()).isEqualTo("POINT_ADJUST_LARGE_REQUIRED"));
        assertThat(kit.largeDenied.get()).isEqualTo(1);
        assertThat(kit.ledger(m, "EVENT_REWARD")).isEmpty();
        assertThat(kit.queries.grantPreview(event).largeRequired()).isTrue();

        kit.grants.grant(kit.op(true), event, "large-2");
        assertThat(kit.balance(m)[3]).isEqualTo(threshold + 1L);
    }

    @Test
    void 문턱_이하면_고액_권한이_없어도_지급한다() {
        long m = kit.member();
        int threshold = kit.policy.lots().adjustLargeThreshold();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(threshold))), m);
        assertThat(kit.queries.grantPreview(event).largeRequired()).isFalse();
        kit.grants.grant(kit.op(false), event, "small");
        assertThat(kit.largeDenied.get()).isZero();
        assertThat(kit.balance(m)[3]).isEqualTo(threshold);
    }

    @Test
    void 탈퇴_정지_회원은_모든_줄이_실패한다() {
        long gone = kit.member();
        long banned = kit.member();
        long event = kit.closedSelection(kit.selection(tier("1등", 5, points(100))), gone, banned);
        kit.status(gone, "WITHDRAWN");
        kit.status(banned, "SUSPENDED");
        grant(event);
        assertThat(kit.results(event, gone).get(0)).containsEntry("status", "FAILED").containsEntry("reason", "WITHDRAWN");
        assertThat(kit.results(event, banned).get(0)).containsEntry("status", "FAILED").containsEntry("reason", "SUSPENDED");
        assertThat(kit.recipientStatus(event, gone)).isEqualTo("FAILED");
        assertThat(kit.balance(gone)[3]).isZero();
    }

    private String recipientId(long event, long userId) {
        return Long.toString(kit.jdbc.queryForObject("SELECT id FROM reward_event_recipients WHERE event_id = ? AND user_id = ?", Long.class, event, userId));
    }

    static Instant at(Instant base, long minutes) {
        return base.plus(minutes, ChronoUnit.MINUTES);
    }
}
