package com.community.pointshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.dto.AuditRetention;
import com.community.audit.dto.AuditRetentionClass;
import com.community.pointshop.dto.response.PointShopOperatorRefundView;
import com.community.pointshop.entity.EquipSlot;
import com.community.pointshop.exception.PointShopAlreadyRefundedException;
import com.community.pointshop.exception.PointShopPurchaseNotFoundException;
import com.community.pointshop.exception.PointShopRefundNotAllowedException;
import com.community.pointshop.exception.UserItemNotFoundException;
import com.community.zconfiguration.exception.CommonException;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 포인트 상점 환불(commerce.md 7-3 D6, 계약 「B」 환불·결정 1) — 셀프 환불(안 썼고 지금 설정의 기한 안)·예외 환불(조건 무시·사유·감사 돈 관련)·처리(회수·장착 해제·기본 아바타·재고 복원·
 * 원래 묶음으로 사용 취소·구매 REFUNDED)·이미 환불 409. 실제 PostgreSQL.
 */
class PointShopRefundTest extends PointShopTestBase {

    @Test
    void 셀프_환불은_원래_묶음으로_돌아가고_재고_복원_구매_REFUNDED_아이템_회수() {
        long item = kit.border(700, "OCEAN", 4);
        long member = kit.newMember();
        kit.grant(member, 500);
        long paidLot = kit.chargePaid(member, 500);
        long freeLot = kit.jdbc.queryForObject("SELECT id FROM point_lots WHERE user_id = ? AND kind = 'FREE'", Long.class, member);
        long userItem = kit.buyItem(member, item);
        assertThat(kit.stock(item)).isEqualTo(3);

        long refunded = kit.refundSelf(member, userItem);

        assertThat(refunded).isEqualTo(700);
        assertThat(kit.balance(member)).containsExactly(500, 500, 0, 1000);
        assertThat(kit.jdbc.queryForObject("SELECT remaining_amount FROM point_lots WHERE id = ?", Long.class, freeLot)).as("원래 무상 묶음으로").isEqualTo(500);
        assertThat(kit.jdbc.queryForObject("SELECT remaining_amount FROM point_lots WHERE id = ?", Long.class, paidLot)).as("원래 유상 묶음으로").isEqualTo(500);
        assertThat(kit.count("SELECT count(*) FROM point_lots WHERE user_id = ?", member)).as("새 묶음 없음").isEqualTo(2);
        assertThat(kit.stock(item)).as("재고 복원").isEqualTo(4);
        long purchase = kit.purchaseOf(userItem);
        Map<String, Object> row = kit.purchaseRow(purchase);
        assertThat(row).containsEntry("status", "REFUNDED");
        assertThat(row.get("refund_ledger_id")).isNotNull();
        assertThat(row.get("refunded_by_operator_id")).as("셀프 환불은 운영자 없음").isNull();
        assertThat(kit.jdbc.queryForMap("SELECT kind, reason, delta FROM point_ledger WHERE id = ?", row.get("refund_ledger_id"))).containsEntry("kind", "CANCEL_USE")
                .containsEntry("reason", "POINT_SHOP_REFUND").containsEntry("delta", 700);
        assertThat(kit.userItemRow(userItem)).containsEntry("status", "REVOKED");
        kit.assertInvariants(member);
    }

    @Test
    void 이미_환불한_구매를_다시_환불하면_409_이고_포인트는_한_번만_돌아간다() {
        long item = kit.color(300, "#BE185D");
        long member = kit.newMember();
        kit.grant(member, 300);
        long userItem = kit.buyItem(member, item);
        kit.refundSelf(member, userItem);

        assertThatThrownBy(() -> kit.refundSelf(member, userItem)).isInstanceOf(PointShopAlreadyRefundedException.class)
                .extracting(e -> ((CommonException) e).getErrorCode()).isEqualTo("POINT_SHOP_ALREADY_REFUNDED");
        long purchase = kit.purchaseOf(userItem);
        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.refundByOperator(purchase, "다시 돌려 달라는 문의", kit.operator, null)))
                .isInstanceOf(PointShopAlreadyRefundedException.class);
        assertThat(kit.count("SELECT count(*) FROM point_ledger WHERE user_id = ? AND reason = 'POINT_SHOP_REFUND'", member)).isEqualTo(1);
        assertThat(kit.total(member)).isEqualTo(300);
        kit.assertInvariants(member);
    }

    @Test
    void 장착한_적이_있으면_풀어도_셀프_환불할_수_없다() {
        long item = kit.color(300, "#7C3AED");
        long member = kit.newMember();
        kit.grant(member, 300);
        long userItem = kit.buyItem(member, item);
        kit.inTx(() -> kit.userItems.equip(member, EquipSlot.NICKNAME_COLOR, userItem));
        kit.inTx(() -> kit.userItems.unequip(member, EquipSlot.NICKNAME_COLOR));

        assertThatThrownBy(() -> kit.refundSelf(member, userItem)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "USED"));
        var view = kit.userItems.myItems(member).items().get(0);
        assertThat(view.refundable()).isFalse();
        assertThat(view.firstUsedAt()).as("풀어도 남는다").isNotNull();
        assertThat(kit.total(member)).isZero();
    }

    @Test
    void 아바타를_프로필로_고른_뒤에는_셀프_환불할_수_없다() {
        long item = kit.avatarItem("job-doctor-m");
        long member = kit.newMember();
        kit.grant(member, 2000);
        long userItem = kit.buyItem(member, item);
        kit.inTx(() -> kit.profileAvatars.select(member, "job-doctor-m"));
        kit.inTx(() -> kit.profileAvatars.select(member, "basic-default"));

        assertThatThrownBy(() -> kit.refundSelf(member, userItem)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "USED"));
    }

    @Test
    void 기한이_지나면_셀프_환불할_수_없고_기한을_바꾸면_지금_값으로_판정한다() {
        long item = kit.color(200, "#0891B2");
        long member = kit.newMember();
        kit.grant(member, 200);
        long userItem = kit.buyItem(member, item);
        Instant bought = kit.clock.instant();

        kit.clock.set(bought.plus(Duration.ofDays(7)));
        assertThat(kit.userItems.myItems(member).items().get(0).refundable()).as("7일째 끝까지는 된다").isTrue();
        kit.clock.set(bought.plus(Duration.ofDays(7)).plusSeconds(1));
        assertThatThrownBy(() -> kit.refundSelf(member, userItem)).isInstanceOf(PointShopRefundNotAllowedException.class)
                .satisfies(e -> assertThat(((CommonException) e).getExtras()).containsEntry("blockedBy", "EXPIRED"));

        kit.withRefundDays(10, () -> {
            var view = kit.userItems.myItems(member).items().get(0);
            assertThat(view.refundable()).as("이미 산 구매도 지금 설정으로").isTrue();
            assertThat(view.refundUntil()).isEqualTo(bought.plus(Duration.ofDays(10)));
            assertThat(kit.refundSelf(member, userItem)).isEqualTo(200);
        });
        kit.assertInvariants(member);
    }

    @Test
    void 기한을_줄이면_아직_7일이_안_됐어도_셀프_환불할_수_없다() {
        long item = kit.color(200, "#0F766E");
        long member = kit.newMember();
        kit.grant(member, 200);
        long userItem = kit.buyItem(member, item);
        kit.clock.set(kit.clock.instant().plus(Duration.ofDays(2)));

        kit.withRefundDays(1, () -> assertThatThrownBy(() -> kit.refundSelf(member, userItem)).isInstanceOf(PointShopRefundNotAllowedException.class));
    }

    @Test
    void 안_쓴_변경권은_환불되고_쓴_변경권은_셀프_환불할_수_없다() {
        long ticket = kit.seedTicket();
        long member = kit.newMember();
        kit.grant(member, 5000);
        long unused = kit.buyItem(member, ticket);
        long used = kit.buyItem(member, ticket);
        assertThat(kit.inTx(() -> kit.tickets.useOne(member))).as("먼저 산 장부터 쓴다").isEqualTo(unused);

        assertThatThrownBy(() -> kit.refundSelf(member, unused)).isInstanceOf(PointShopRefundNotAllowedException.class);
        long price = kit.refundSelf(member, used);
        assertThat(price).isEqualTo(kit.tickets.price().longValue());
        assertThat(kit.tickets.count(member)).isZero();
        kit.assertInvariants(member);
    }

    @Test
    void 예외_환불은_조건을_무시하고_장착을_풀고_사유와_함께_돈_관련_감사를_남긴다() {
        long item = kit.border(900, "FOREST", null);
        long member = kit.newMember();
        kit.grant(member, 900);
        long userItem = kit.buyItem(member, item);
        kit.inTx(() -> kit.userItems.equip(member, EquipSlot.PROFILE_BORDER, userItem));
        kit.clock.set(kit.clock.instant().plus(Duration.ofDays(30)));
        long purchase = kit.purchaseOf(userItem);

        PointShopOperatorRefundView result = kit.inTx(() -> kit.refunds.refundByOperator(purchase, "  결제 실수 문의로 돌려 드려요  ", kit.operator, "10.0.0.1"));

        assertThat(result.refundedPoints()).isEqualTo(900);
        assertThat(kit.total(member)).isEqualTo(900);
        assertThat(kit.userItems.equipped(member).profileBorder()).as("장착 자리를 비웠다").isNull();
        assertThat(kit.count("SELECT count(*) FROM user_equipped WHERE user_item_id = ?", userItem)).isZero();
        assertThat(kit.purchaseRow(purchase)).containsEntry("status", "REFUNDED").containsEntry("refunded_by_operator_id", kit.operator)
                .containsEntry("refund_reason", "결제 실수 문의로 돌려 드려요");
        List<AuditEvent> events = kit.audits.stream().filter(e -> e.action().equals(AuditActions.POINT_SHOP_REFUND)).toList();
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.actorId()).isEqualTo(kit.operator);
        assertThat(event.ip()).isEqualTo("10.0.0.1");
        assertThat(event.targetType()).isEqualTo("POINT_SHOP_PURCHASE");
        assertThat(event.targetId()).isEqualTo(Long.toString(purchase));
        assertThat(event.detail()).containsEntry("refundedPoints", 900L).containsEntry("reason", "결제 실수 문의로 돌려 드려요").containsEntry("selfRefundable", false)
                .containsKey("firstUsedAt");
        assertThat(AuditRetention.classify(AuditActions.POINT_SHOP_REFUND)).as("돈 관련 5년").isEqualTo(AuditRetentionClass.MONEY);

        var rows = kit.purchases.adminPurchases(new com.community.pointshop.dto.request.PointShopPurchaseQuery(null, null, null, null, Long.toString(member), 0, 20, null)).items();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).refund().kind()).isEqualTo("EXCEPTION");
        assertThat(rows.get(0).refund().reason()).isEqualTo("결제 실수 문의로 돌려 드려요");
        assertThat(rows.get(0).selfRefund()).isNull();
        kit.assertInvariants(member);
    }

    @Test
    void 예외_환불한_아바타를_쓰고_있었으면_기본_아바타로_돌아간다() {
        long item = kit.avatarItem("job-nurse-f");
        long member = kit.newMember();
        kit.grant(member, 1000);
        long userItem = kit.buyItem(member, item);
        kit.inTx(() -> kit.profileAvatars.select(member, "job-nurse-f"));
        assertThat(kit.decorations.look(member).avatarUrl()).isEqualTo("/avatars/roboyam/job-nurse-f.svg");

        kit.inTx(() -> kit.refunds.refundByOperator(kit.purchaseOf(userItem), "아바타 환불 요청", kit.operator, null));

        assertThat(kit.jdbc.queryForObject("SELECT profile_image FROM profiles WHERE user_id = ?", String.class, member)).isNull();
        assertThat(kit.decorations.look(member).avatarUrl()).isEqualTo("/avatars/roboyam/basic-default.svg");
        assertThat(kit.profileAvatars.catalog(member).selectedId()).isNull();
        assertThat(kit.profileAvatars.catalog(member).items().stream().filter(a -> a.id().equals("job-nurse-f")).findFirst().orElseThrow().owned()).isFalse();
        kit.assertInvariants(member);
    }

    @Test
    void 예외_환불의_사유_길이_없는_구매_남의_아이템() {
        long item = kit.color(100, "#4D7C0F");
        long member = kit.newMember();
        long other = kit.newMember();
        kit.grant(member, 100);
        long userItem = kit.buyItem(member, item);

        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.refundByOperator(kit.purchaseOf(userItem), " 짧음 ", kit.operator, null)))
                .isInstanceOf(ValidationException.class).satisfies(e -> assertThat(((CommonException) e).getFields()).containsKey("reason"));
        assertThatThrownBy(() -> kit.inTx(() -> kit.refunds.refundByOperator(Long.MAX_VALUE, "없는 구매를 환불", kit.operator, null)))
                .isInstanceOf(PointShopPurchaseNotFoundException.class);
        assertThatThrownBy(() -> kit.refundSelf(other, userItem)).isInstanceOf(UserItemNotFoundException.class);
        assertThat(kit.total(member)).isZero();
    }
}
