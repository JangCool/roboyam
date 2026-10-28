package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.service.ParticipationCancelService.CancelResult;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.PaymentStatus;
import com.community.zconfiguration.exception.ValidationException;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 본인 즉시 취소(줄·수량 단위) — commerce.md 10-3 표·D11, 계약 결정 38·42·45·46·52·69, G13·G19·G37.
 */
class ParticipationCancelTest {

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    @Test
    void G13_25000원_3개_무료배송에서_발송_전_2개_취소면_배송비를_다시_계산해_47000원_환불() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 3);
        assertThat(paid.amount()).isEqualTo(75_000);

        CancelResult result = kit.cancels.cancelByMember(member, paid.participationId(), lines(null, 2));

        assertThat(result.refundAmount()).isEqualTo(47_000);
        assertThat(result.reshippingFee()).isEqualTo(3_000);
        assertThat(result.wholeOrder()).isFalse();
        var p = kit.participation(paid.participationId());
        assertThat(p.status()).as("일부 줄만 — 상태 그대로(결정 46)").isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(p.reshippingFeeCharged()).isEqualTo(3_000);
        assertThat(p.refundedAmount()).as("취소를 넣을 때 올린다(결정 38)").isEqualTo(47_000);
        assertThat(kit.items(p.id()).get(0).canceledQty()).isEqualTo(2);
        var payment = kit.payment(paid.checkout().paymentId());
        assertThat(payment.canceledAmount()).isEqualTo(47_000);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(kit.paymentCancels.findByOrder(payment.id())).singleElement().satisfies(c -> assertThat(c.kind()).isEqualTo(CancelKind.USER_CANCEL));
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE participation_id = ? AND cause = 'USER_CANCEL' AND reshipping_fee = 3000 AND item_refund = 50000 "
                + "AND amount = 47000 AND status = 'COMPLETED'", p.id())).isEqualTo(1);
        assertThat(kit.group(gb).currentQty()).isEqualTo(1);

        // 남은 하나도 취소 — 주문 전체가 되면 남은 결제액 전부(앞서 뺀 배송비 포함)
        CancelResult rest = kit.cancels.cancelByMember(member, paid.participationId(), null);
        assertThat(rest.refundAmount()).isEqualTo(28_000);
        assertThat(rest.status()).isEqualTo(ParticipationStatus.CANCELED);
        assertThat(kit.participation(p.id()).refundedAmount()).isEqualTo(75_000);
        assertThat(kit.payment(paid.checkout().paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
        kit.assertInvariants(gb);
    }

    @Test
    void 처음부터_배송비를_낸_주문은_일부_취소해도_재부과가_없다() {
        String policy = kit.policy(3_000, 100_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 2); // 50,000 + 3,000
        CancelResult result = kit.cancels.cancelByMember(member, paid.participationId(), lines(null, 1));
        assertThat(result.refundAmount()).isEqualTo(25_000);
        assertThat(result.reshippingFee()).isZero();
        kit.assertInvariants(gb);
    }

    @Test
    void 여러_옵션_주문에서_한_줄만_취소하고_남은_상품이_기준_이상이면_배송비_재부과_없음() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).option("A", 0, null).option("B", 5_000, null).open();
        long a = kit.optionId(gb, "A");
        long b = kit.optionId(gb, "B");
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, lines(a, 2, b, 1)); // 50,000 + 30,000 = 80,000 무료
        CancelResult result = kit.cancels.cancelByMember(member, paid.participationId(), lines(b, 1));
        assertThat(result.refundAmount()).isEqualTo(30_000);
        assertThat(kit.option(gb, b).currentQty()).isZero();
        assertThat(kit.option(gb, a).currentQty()).isEqualTo(2);
        kit.assertInvariants(gb);
    }

    @Test
    void 성사형_JOINED_는_모집_중_즉시_취소_성사_뒤에는_요청으로() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).qty(1, 10).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        long other = kit.newMember();
        JoinResult kept = kit.paidOrder(other, gb, 1);
        assertThat(kit.cancels.cancelByMember(member, paid.participationId(), null).refundAmount()).isEqualTo(28_000);

        kit.advance(Duration.ofMinutes(11));
        kit.runStateWorker();
        assertThat(kit.participation(kept.participationId()).status()).isEqualTo(ParticipationStatus.CONFIRMED);
        assertThatThrownBy(() -> kit.cancels.cancelByMember(other, kept.participationId(), null))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.CANCEL_NOT_ALLOWED));
        kit.assertInvariants(gb);
    }

    @Test
    void 즉시_발송은_준비중_전까지만_즉시_취소() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'PREPARING' WHERE id = ?", paid.participationId());
        assertThatThrownBy(() -> kit.cancels.cancelByMember(member, paid.participationId(), null))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.CANCEL_NOT_ALLOWED));
    }

    @Test
    void 남의_주문은_404_가진_수량보다_많이_취소하면_400() {
        String policy = kit.policy(3_000, null, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 1);
        assertThatThrownBy(() -> kit.cancels.cancelByMember(kit.newMember(), paid.participationId(), null))
                .isInstanceOfSatisfying(GroupBuyException.class, e -> assertThat(e.getErrorCode()).isEqualTo(GroupBuyException.ORDER_NOT_FOUND));
        assertThatThrownBy(() -> kit.cancels.cancelByMember(member, paid.participationId(), lines(null, 2))).isInstanceOf(ValidationException.class);
        assertThat(kit.participation(paid.participationId()).refundedAmount()).isZero();
    }

    @Test
    void G37_발송_지연_공지_뒤에는_준비중_주문도_승인_없이_재부과_없이_전액_취소() {
        String policy = kit.policy(3_000, 50_000L, 3_000, "ONE_WAY");
        long gb = kit.spec(policy).qty(1, 10).period(kit.now().minus(Duration.ofMinutes(1)), kit.now().plus(Duration.ofMinutes(10))).open();
        long member = kit.newMember();
        JoinResult paid = kit.paidOrder(member, gb, 3);
        kit.advance(Duration.ofMinutes(11));
        kit.runStateWorker();
        kit.jdbc.update("UPDATE group_buy_participations SET status = 'PREPARING' WHERE id = ?", paid.participationId());
        assertThatThrownBy(() -> kit.cancels.cancelByMember(member, paid.participationId(), null)).isInstanceOf(GroupBuyException.class);

        kit.admin.announceDelay(gb, LocalDate.now().plusDays(30), "공급처 입고 지연", kit.operator, "ip");
        CancelResult result = kit.cancels.cancelByMember(member, paid.participationId(), lines(null, 1)); // 줄을 골라도 주문 전체
        assertThat(result.wholeOrder()).isTrue();
        assertThat(result.refundAmount()).isEqualTo(75_000);
        assertThat(result.reshippingFee()).isZero();
        assertThat(kit.count("SELECT count(*) FROM group_buy_refunds WHERE participation_id = ? AND cause = 'DELAY'", paid.participationId())).isEqualTo(1);
        assertThat(kit.count("SELECT count(*) FROM group_buy_delay_notices WHERE group_buy_id = ?", gb)).isEqualTo(1);
        assertThat(kit.group(gb).shipPlannedAt()).isEqualTo(LocalDate.now().plusDays(30));
        kit.assertInvariants(gb);
    }
}
