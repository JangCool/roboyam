package com.community.point;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.point.entity.PointReason;
import com.community.point.exception.PointFrozenException;
import com.community.point.exception.PointInsufficientException;
import com.community.point.service.PointService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 운영 조정(무상만 — P20 의 서비스 쪽), 사용(MANDATORY·동결 거절·잔액 부족 거절), 사용 취소(원래 묶음 / 만료면 30일 새 묶음, 멱등), 묶음 소멸(조건부·멱등) — commerce.md 5-4, 계약 「1-1」.
 */
class PointAdjustUseCancelTest extends PointTestBase {

    // ── 운영 조정 ────────────────────────────────────────────────────────────

    @Test
    void 지급_조정은_무상_1년_묶음이다() {
        long member = kit.newMember();

        PointService.Adjusted result = kit.adjust(member, 500);

        assertThat(result.applied()).isEqualTo(500);
        assertThat(result.balance()).isEqualTo(500);
        long first = kit.jdbc.queryForObject("SELECT min(id) FROM point_ledger WHERE user_id = ? AND reason = 'ADMIN_ADJUST'", Long.class, member);
        Map<String, Object> lot = kit.jdbc.queryForMap("SELECT kind, original_amount, expires_at, source_reason FROM point_lots WHERE source_ledger_id = ?", first);
        assertThat(lot).containsEntry("kind", "FREE").containsEntry("original_amount", 500L).containsEntry("source_reason", "ADMIN_ADJUST");
        assertThat(((Timestamp) lot.get("expires_at")).toInstant()).isEqualTo(kit.clock.instant().plus(Duration.ofDays(365)));
        assertThat(kit.jdbc.queryForMap("SELECT kind, free_delta, paid_delta, actor_operator_id, note FROM point_ledger WHERE id = ?", first)).containsEntry("kind", "EARN")
                .containsEntry("free_delta", 500L).containsEntry("paid_delta", 0L).containsEntry("actor_operator_id", kit.operator).containsEntry("note", "시험 조정");
        kit.assertInvariants(member);
    }

    @Test
    void 차감_조정은_무상에서만_0까지_유상은_그대로_빚을_만들지_않는다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        long paidLot = kit.chargePaid(member, 200);

        PointService.Adjusted result = kit.adjust(member, -300);

        assertThat(result.applied()).isEqualTo(-100);
        assertThat(result.balance()).isEqualTo(200);
        assertThat(kit.balance(member)).containsExactly(0, 200, 0, 200);
        assertThat(kit.remaining(paidLot)).isEqualTo(200);
        assertThat(kit.count("SELECT count(*) FROM point_revoke_shortfalls WHERE user_id = ?", member)).isZero();

        PointService.Adjusted none = kit.adjust(member, -10); // 무상이 없으면 반영 0 — 줄 없음
        assertThat(none.applied()).isZero();
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'REVOKE'", member)).isEqualTo(1);
        kit.assertInvariants(member);
    }

    @Test
    void 지급_조정도_미회수를_먼저_갚는다() {
        long member = kit.newMember();
        long postId = kit.ids.nextId();
        kit.earnPost(member, postId);
        kit.use(member, 20);
        kit.revokePost(member, postId); // 빚 20

        PointService.Adjusted result = kit.adjust(member, 50);

        assertThat(result.applied()).isEqualTo(50);
        assertThat(result.balance()).isEqualTo(30);
        assertThat(kit.debt(member)).isZero();
        long grant = kit.jdbc.queryForObject("SELECT id FROM point_ledger WHERE user_id = ? AND reason = 'ADMIN_ADJUST'", Long.class, member);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE reason = 'REVOKE_DEBT_OFFSET' AND ref_type = 'LEDGER' AND ref_id = ? AND delta = -20", grant)).isEqualTo(1);
        kit.assertInvariants(member);
    }

    // ── 사용 ────────────────────────────────────────────────────────────────

    @Test
    void 사용은_원장_줄_ID를_돌려주고_사유와_대상을_남긴다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        long purchase = kit.ids.nextId();

        long line = kit.inTx(() -> kit.points.use(member, 30, PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchase));

        assertThat(kit.jdbc.queryForMap("SELECT user_id, kind, reason, delta, ref_type, ref_id, balance_after FROM point_ledger WHERE id = ?", line))
                .containsEntry("user_id", member).containsEntry("kind", "USE").containsEntry("reason", "POINT_SHOP_PURCHASE").containsEntry("delta", -30)
                .containsEntry("ref_type", "POINT_SHOP_PURCHASE").containsEntry("ref_id", purchase).containsEntry("balance_after", 70L);
        // 같은 대상에 두 번 쓰지 않는다(사유 + 대상 유일)
        assertThatThrownBy(() -> kit.inTx(() -> kit.points.use(member, 30, PointReason.POINT_SHOP_PURCHASE, PointService.REF_POINT_SHOP_PURCHASE, purchase)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(kit.free(member)).isEqualTo(70);
        kit.assertInvariants(member);
    }

    @Test
    void 잔액이_모자라면_거절하고_아무것도_바꾸지_않는다() {
        long member = kit.newMember();
        kit.adjust(member, 50);
        kit.chargePaid(member, 20);

        assertThatThrownBy(() -> kit.use(member, 71)).isInstanceOf(PointInsufficientException.class);

        assertThat(kit.balance(member)).containsExactly(50, 20, 0, 70);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'USE'", member)).isZero();
        kit.use(member, 70); // 정확히 다 쓰는 것은 된다
        assertThat(kit.balance(member)).containsExactly(0, 0, 0, 0);
        kit.assertInvariants(member);
    }

    @Test
    void 동결_중이면_사용을_거절한다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        kit.freeze(member);

        assertThatThrownBy(() -> kit.use(member, 10)).isInstanceOf(PointFrozenException.class).extracting("errorCode").isEqualTo("POINT_FROZEN");

        assertThat(kit.free(member)).isEqualTo(100);
    }

    // ── 사용 취소 ────────────────────────────────────────────────────────────

    @Test
    void 사용_취소는_원래_묶음으로_되돌린다() {
        long member = kit.newMember();
        long free = kit.lotOf(kit.grantLine(member, 50));
        long paid = kit.chargePaid(member, 100);
        long use = kit.use(member, 80); // 무상 50 + 유상 30

        long cancel = kit.cancel(use);

        assertThat(kit.entryLots(cancel)).containsExactlyInAnyOrderEntriesOf(Map.of(free, 50L, paid, 30L));
        assertThat(kit.jdbc.queryForMap("SELECT kind, reason, delta, free_delta, paid_delta FROM point_ledger WHERE id = ?", cancel)).containsEntry("kind", "CANCEL_USE")
                .containsEntry("reason", "POINT_SHOP_REFUND").containsEntry("delta", 80).containsEntry("free_delta", 50L).containsEntry("paid_delta", 30L);
        assertThat(kit.remaining(free)).isEqualTo(50);
        assertThat(kit.remaining(paid)).isEqualTo(100);
        assertThat(kit.balance(member)).containsExactly(50, 100, 0, 150);
        kit.assertInvariants(member);
    }

    @Test
    void 만료된_묶음이면_같은_종류_30일_새_묶음으로_준다_유상은_원래_묶음() {
        long member = kit.newMember();
        Instant start = kit.clock.instant();
        long free = kit.lotOf(kit.grantLine(member, 100));
        long paid = kit.chargePaid(member, 50);
        long use = kit.use(member, 120); // 무상 100 + 유상 20
        kit.clock.set(start.plus(Duration.ofDays(400)));

        long cancel = kit.cancel(use);

        Map<String, Object> restored = kit.jdbc.queryForMap("SELECT id, kind, original_amount, expires_at, restored_from_lot_id, source_reason FROM point_lots "
                + "WHERE restored_from_lot_id = ?", free);
        long restoredId = ((Number) restored.get("id")).longValue();
        assertThat(restored).containsEntry("kind", "FREE").containsEntry("original_amount", 100L).containsEntry("source_reason", "POINT_SHOP_REFUND");
        assertThat(((Timestamp) restored.get("expires_at")).toInstant()).isEqualTo(kit.clock.instant().plus(Duration.ofDays(30)));
        assertThat(kit.entryLots(cancel)).containsExactlyInAnyOrderEntriesOf(Map.of(restoredId, 100L, paid, 20L));
        assertThat(kit.remaining(free)).isZero(); // 원래 묶음은 그대로(소멸일이 지나 더 쓰지 않는다)
        assertThat(kit.remaining(paid)).isEqualTo(50);
        kit.assertInvariants(member);
    }

    @Test
    void 사용_취소는_멱등이고_동결_중에도_된다() {
        long member = kit.newMember();
        kit.adjust(member, 100);
        long use = kit.use(member, 40);
        kit.freeze(member);

        long first = kit.cancel(use);
        long second = kit.cancel(use);

        assertThat(second).isEqualTo(first);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'CANCEL_USE'", member)).isEqualTo(1);
        assertThat(kit.free(member)).isEqualTo(100);
        kit.assertInvariants(member);
    }

    // ── 소멸 ────────────────────────────────────────────────────────────────

    @Test
    void 묶음_소멸은_소멸일이_지난_무상의_남은_양만_한_번() {
        long member = kit.newMember();
        Instant start = kit.clock.instant();
        long lot = kit.lotOf(kit.grantLine(member, 100));
        long paid = kit.chargePaid(member, 70);
        kit.use(member, 30);

        assertThat(kit.expireLot(lot)).as("소멸일 전").isZero();
        kit.clock.set(start.plus(Duration.ofDays(365)));
        assertThat(kit.expireLot(lot)).isEqualTo(70);
        assertThat(kit.expireLot(lot)).as("두 번째").isZero();
        assertThat(kit.expireLot(paid)).as("유상은 소멸 없음").isZero();

        long line = kit.entryId(member, "EXPIRE", "LOT", lot);
        assertThat(kit.jdbc.queryForMap("SELECT kind, delta, free_delta, paid_delta FROM point_ledger WHERE id = ?", line)).containsEntry("kind", "EXPIRE")
                .containsEntry("delta", -70).containsEntry("free_delta", -70L).containsEntry("paid_delta", 0L);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND kind = 'EXPIRE'", member)).isEqualTo(1);
        assertThat(kit.balance(member)).containsExactly(0, 70, 0, 70);
        kit.assertInvariants(member);
    }
}
