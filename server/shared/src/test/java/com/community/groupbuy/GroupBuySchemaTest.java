package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.service.GroupBuyInvariantCheck.Violation;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

/**
 * V101 의 DB 방어선(계약 결정 23·38·66·76·77, commerce.md 10-1)과 불변식 검사가 어긋남을 잡는지(결정 28·65, G6) — 실제 PostgreSQL.
 */
class GroupBuySchemaTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private String policy() {
        return kit.policy(3_000, null, 3_000, "ONE_WAY");
    }

    @Test
    void 진행_중_주문은_공구_회원당_하나_부분_유일_색인() {
        long gb = kit.spec(policy()).open();
        long member = kit.newMember();
        JoinResult first = kit.order(member, gb, 1);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO group_buy_participations (id, order_no, group_buy_id, user_id, status, items_amount, shipping_fee, amount, "
                + "cash_amount, group_buy_revision, reserved_until, created_at, updated_at) SELECT ?, 'G2026010199999', group_buy_id, user_id, 'JOINED', items_amount, "
                + "shipping_fee, amount, cash_amount, 1, reserved_until, created_at, updated_at FROM group_buy_participations WHERE id = ?", kit.ids.nextId(),
                first.participationId())).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void 옵션_없는_줄은_주문당_하나_NULLS_NOT_DISTINCT_그리고_줄의_옵션은_같은_공구_것만_복합_FK() {
        long gb = kit.spec(policy()).open();
        long other = kit.spec(policy()).option("X", 0, 5).open();
        JoinResult joined = kit.order(kit.newMember(), gb, 1);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO group_buy_participation_items (id, participation_id, group_buy_id, option_id, qty, unit_price, item_amount) "
                + "VALUES (?, ?, ?, NULL, 1, 25000, 25000)", kit.ids.nextId(), joined.participationId(), gb)).isInstanceOf(DuplicateKeyException.class);
        assertThatThrownBy(() -> kit.jdbc.update("INSERT INTO group_buy_participation_items (id, participation_id, group_buy_id, option_id, qty, unit_price, item_amount) "
                + "VALUES (?, ?, ?, ?, 1, 25000, 25000)", kit.ids.nextId(), joined.participationId(), gb, kit.optionId(other, "X")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 환불_누계와_보상은_현금을_넘지_못하고_줄_수량_식이_지켜진다() {
        long gb = kit.spec(policy()).open();
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, 2);
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buy_participations SET refunded_amount = 50000, compensation_amount = 4000 WHERE id = ?",
                paid.participationId())).hasMessageContaining("ck_gbp_refunded");
        long item = kit.items(paid.participationId()).get(0).id();
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buy_participation_items SET canceled_qty = 1, returned_qty = 2 WHERE id = ?", item))
                .hasMessageContaining("ck_gbpi_qty");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buy_participation_items SET returned_qty = 1, restocked_qty = 2 WHERE id = ?", item))
                .hasMessageContaining("ck_gbpi_restocked");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buys SET reserved_qty = max_qty WHERE id = ?", gb)).hasMessageContaining("ck_group_buys_counts");
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buys SET min_qty = 1 WHERE id = ?", gb)).as("즉시 발송은 선착순만")
                .hasMessageContaining("ck_group_buys_fulfillment_min");
    }

    @Test
    void 모집을_시작한_공구는_배송비_사본_없이_있을_수_없다() {
        long gb = kit.spec(policy()).open();
        assertThatThrownBy(() -> kit.jdbc.update("UPDATE group_buys SET shipping_fee = NULL WHERE id = ?", gb)).hasMessageContaining("ck_group_buys_started_copy");
    }

    @Test
    void G6_불변식_검사는_판매_수량_식으로_맞추고_어긋나면_잡는다() {
        long gb = kit.spec(policy()).option("A", 0, 10).option("B", 0, null).open();
        long a = kit.optionId(gb, "A");
        JoinResult paid = kit.paidOrder(kit.newMember(), gb, lines(a, 3, kit.optionId(gb, "B"), 1));
        kit.order(kit.newMember(), gb, lines(a, 2));
        kit.assertInvariants(gb);

        // 반품 처리(2b-3)를 흉내: A 2개 반품 중 1개 다시 판매(RESTOCK — 확정 감소), 1개 폐기(판매 수량 그대로)
        long itemA = kit.items(paid.participationId()).stream().filter(i -> i.optionId() == a).findFirst().orElseThrow().id();
        kit.jdbc.update("UPDATE group_buy_participation_items SET returned_qty = 2, restocked_qty = 1 WHERE id = ?", itemA);
        List<Violation> before = kit.invariants.violations(List.of(gb));
        assertThat(before).extracting(Violation::check).contains("GROUP_CURRENT", "OPTION_CURRENT");
        kit.jdbc.update("UPDATE group_buys SET current_qty = current_qty - 1 WHERE id = ?", gb);
        kit.jdbc.update("UPDATE group_buy_options SET current_qty = current_qty - 1 WHERE id = ?", a);
        assertThat(kit.invariants.violations(List.of(gb))).extracting(Violation::check).as("수량 열은 맞지만 이동 줄이 없다")
                .containsOnly("LAST_MOVE_GROUP", "LAST_MOVE_OPTION");

        // 결제 금액 식(결정 39) — 환불 누계만 올리고 취소를 넣지 않으면 어긋난다
        kit.jdbc.update("UPDATE group_buy_participations SET refunded_amount = 1000 WHERE id = ?", paid.participationId());
        assertThat(kit.invariants.violations(List.of(gb))).extracting(Violation::check).contains("PAYMENT_BALANCE");
        kit.jdbc.update("UPDATE group_buy_participations SET refunded_amount = 0 WHERE id = ?", paid.participationId());
    }

    @Test
    void 주문_번호는_날마다_이어지는_다섯_자리() {
        long gb = kit.spec(policy()).qty(0, 1000).open();
        String first = kit.participation(kit.order(kit.newMember(), gb, 1).participationId()).orderNo();
        String second = kit.participation(kit.order(kit.newMember(), gb, 1).participationId()).orderNo();
        assertThat(first.substring(0, 9)).isEqualTo(second.substring(0, 9));
        assertThat(Long.parseLong(second.substring(9))).isEqualTo(Long.parseLong(first.substring(9)) + 1);
    }
}
