package com.community.groupbuy;

import static com.community.groupbuy.entity.CollectionMethod.COMPANY_PICKUP;
import static com.community.groupbuy.entity.CollectionMethod.CUSTOMER_SEND;
import static com.community.groupbuy.entity.RequestReason.CHANGE_OF_MIND;
import static com.community.groupbuy.entity.RequestReason.DEFECT;
import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.entity.CollectionMethod;
import com.community.groupbuy.entity.RequestReason;
import com.community.groupbuy.entity.ReturnFeeMode;
import com.community.groupbuy.service.GroupBuyRefundCalculator;
import com.community.groupbuy.service.GroupBuyRefundCalculator.Held;
import com.community.groupbuy.service.GroupBuyRefundCalculator.Line;
import com.community.groupbuy.service.GroupBuyRefundCalculator.OrderView;
import com.community.groupbuy.service.GroupBuyRefundCalculator.PolicyView;
import com.community.groupbuy.service.GroupBuyRefundCalculator.Result;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 환불 계산기 한 곳(commerce.md 10-8 기준 표 · 18절 P36~P42, 계약 결정 33·42·43·44·68·71 · G7·G14·G31·G32) — 순수 계산. 실제 요청 흐름은 {@code GroupBuyRequestTest}.
 * 기본: 배송비 3,000 · 무료배송 50,000 · 반품비 3,000.
 */
class GroupBuyRefundCalculatorTest {

    private static final PolicyView ONE_WAY = new PolicyView(3_000, 50_000L, 3_000, ReturnFeeMode.ONE_WAY);
    private static final PolicyView ROUND_TRIP = new PolicyView(3_000, 50_000L, 3_000, ReturnFeeMode.ROUND_TRIP);

    /** 줄 하나(단품 하나) — 단가·가진 수량 */
    private static List<Held> one(long price, int held) {
        return List.of(new Held(1, 11, true, price, held, 0));
    }

    private static Result ret(OrderView o, PolicyView p, List<Held> held, int qty, RequestReason reason, CollectionMethod m) {
        return GroupBuyRefundCalculator.returnRefund(o, p, held, List.of(new Line(1, qty, reason)), m);
    }

    /** 발송된 주문 — 낸 배송비, 재부과 누계, 남은 결제액 */
    private static OrderView shipped(long fee, long reshipping, long remaining) {
        return new OrderView(fee, fee + reshipping, reshipping, remaining);
    }

    @Test
    void P36_P37_무료배송_단순_변심_전체_업체회수는_편도_47000_왕복_44000() {
        OrderView order = shipped(0, 0, 50_000);
        assertThat(ret(order, ONE_WAY, one(50_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP).total()).isEqualTo(47_000);
        assertThat(ret(order, ROUND_TRIP, one(50_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP).total()).isEqualTo(44_000);
    }

    @Test
    void P37b_P37c_낸_주문_단순_변심_전체는_업체회수_36000_고객발송_39000_편도_왕복_같음() {
        OrderView order = shipped(3_000, 0, 42_000);
        for (PolicyView p : List.of(ONE_WAY, ROUND_TRIP)) {
            assertThat(ret(order, p, one(39_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP).total()).isEqualTo(36_000);
            assertThat(ret(order, p, one(39_000, 1), 1, CHANGE_OF_MIND, CUSTOMER_SEND).total()).isEqualTo(39_000);
        }
    }

    @Test
    void P37d_무료배송_단순_변심_전체_고객발송은_왕복이어도_50000_전액() {
        assertThat(ret(shipped(0, 0, 50_000), ROUND_TRIP, one(50_000, 1), 1, CHANGE_OF_MIND, CUSTOMER_SEND).total()).isEqualTo(50_000);
    }

    @Test
    void P38_P38b_P39_무료배송_부분_반품은_남은_상품_기준으로_재부과() {
        Result p38 = ret(shipped(0, 0, 50_000), ONE_WAY, one(25_000, 2), 1, CHANGE_OF_MIND, COMPANY_PICKUP);
        assertThat(p38.total()).isEqualTo(19_000);
        assertThat(p38.reshippingFee()).isEqualTo(3_000);
        assertThat(p38.returnFee()).isEqualTo(3_000);
        assertThat(ret(shipped(0, 0, 50_000), ONE_WAY, one(25_000, 2), 1, CHANGE_OF_MIND, CUSTOMER_SEND).total()).as("P38b").isEqualTo(22_000);
        assertThat(ret(shipped(0, 0, 75_000), ONE_WAY, one(25_000, 3), 1, CHANGE_OF_MIND, COMPANY_PICKUP).total()).as("P39").isEqualTo(22_000);
    }

    @Test
    void P40_P42_하자_부분_반품은_재계산_반품비_없이_상품_금액만() {
        assertThat(ret(shipped(0, 0, 50_000), ONE_WAY, one(25_000, 2), 1, DEFECT, COMPANY_PICKUP).total()).as("P40").isEqualTo(25_000);
        Result p42 = ret(shipped(3_000, 0, 45_000), ONE_WAY, one(21_000, 2), 1, DEFECT, COMPANY_PICKUP);
        assertThat(p42.total()).as("P42 처음 배송비 그대로").isEqualTo(21_000);
        assertThat(p42.shippingRefund()).isZero();
    }

    @Test
    void P41_낸_주문_하자_전체_반품은_처음_배송비까지_결제액_전액() {
        Result r = ret(shipped(3_000, 0, 42_000), ONE_WAY, one(39_000, 1), 1, DEFECT, CUSTOMER_SEND);
        assertThat(r.total()).isEqualTo(42_000);
        assertThat(r.shippingRefund()).isEqualTo(3_000);
        assertThat(r.sellerFault()).isTrue();
    }

    @Test
    void G7_25000x3_무료배송_1개씩_세_번_반품은_재부과를_두_번_빼지_않고_마지막은_전체_반품_규칙() {
        // 1개 반품 22,000(남은 50,000 ≥ 기준)
        Result first = ret(shipped(0, 0, 75_000), ONE_WAY, one(25_000, 3), 1, CHANGE_OF_MIND, COMPANY_PICKUP);
        assertThat(first.total()).isEqualTo(22_000);
        // 1개 반품 19,000(재부과 3,000 + 반품비)
        Result second = ret(shipped(0, 0, 53_000), ONE_WAY, one(25_000, 2), 1, CHANGE_OF_MIND, COMPANY_PICKUP);
        assertThat(second.total()).isEqualTo(19_000);
        assertThat(second.reshippingDelta()).isEqualTo(3_000);
        // 마지막 1개 25,000 − 3,000 + 3,000(재부과 돌려줌) = 25,000
        Result last = ret(new OrderView(0, 0L, 3_000, 34_000), ONE_WAY, one(25_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP);
        assertThat(last.total()).isEqualTo(25_000);
        assertThat(last.reshippingRefund()).isEqualTo(3_000);
        assertThat(last.whole()).isTrue();
        assertThat(first.total() + second.total() + last.total()).as("75,000 − 반품비 3,000 × 3").isEqualTo(66_000);
        // 같은 마지막 반품을 재부과 한 번 더 겹쳐 빼지 않는다(두 번째에 이미 3,000 을 재부과 — 차액만)
        Result again = ret(new OrderView(0, 0L, 3_000, 31_000), ONE_WAY, List.of(new Held(1, 11, true, 25_000, 2, 0)), 1, CHANGE_OF_MIND, COMPANY_PICKUP);
        assertThat(again.reshippingFee()).as("남은 25,000 기준 배송비 3,000 은 이미 재부과").isZero();
    }

    @Test
    void G7_왕복이면_마지막_전체_반품에서_처음_배송비를_한_번만_받은_것으로() {
        Result last = ret(new OrderView(0, 0L, 3_000, 34_000), ROUND_TRIP, one(25_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP);
        assertThat(last.reshippingFee()).isZero();
        assertThat(last.reshippingRefund()).isZero();
        assertThat(last.total()).isEqualTo(22_000);
    }

    @Test
    void G14_한_요청에_하자_줄_변심_줄이면_반품비는_운영자_부담이고_줄별_금액() {
        List<Held> held = List.of(new Held(1, 11, true, 20_000, 1, 0), new Held(2, 12, true, 20_000, 2, 0));
        Result r = GroupBuyRefundCalculator.returnRefund(shipped(0, 0, 60_000), ONE_WAY, held, List.of(new Line(1, 1, DEFECT), new Line(2, 1, CHANGE_OF_MIND)),
                COMPANY_PICKUP);
        assertThat(r.returnFee()).isZero();
        assertThat(r.sellerFault()).isTrue();
        assertThat(r.reshippingFee()).as("업체 부담 요청 — 재계산 없음").isZero();
        assertThat(r.lines()).extracting(GroupBuyRefundCalculator.LineAmount::amount).containsExactly(20_000L, 20_000L);
        // 하자 A 먼저(부분) → 변심 B 마지막(전체) — 결정 43: 편도면 처음 배송비 0, 왕복이면 한 번
        List<Held> afterA = List.of(new Held(1, 11, true, 20_000, 0, 0), new Held(2, 12, true, 20_000, 2, 0));
        Result oneWay = GroupBuyRefundCalculator.returnRefund(shipped(0, 0, 40_000), ONE_WAY, afterA, List.of(new Line(2, 2, CHANGE_OF_MIND)), COMPANY_PICKUP);
        assertThat(oneWay.total()).isEqualTo(37_000);
        Result roundTrip = GroupBuyRefundCalculator.returnRefund(shipped(0, 0, 40_000), ROUND_TRIP, afterA, List.of(new Line(2, 2, CHANGE_OF_MIND)), COMPANY_PICKUP);
        assertThat(roundTrip.total()).isEqualTo(34_000);
    }

    @Test
    void G31_47000_5000_무료배송에서_B_변심_반품은_업체회수_마이너스_1000_고객발송_2000() {
        List<Held> held = List.of(new Held(1, 11, true, 47_000, 1, 0), new Held(2, 12, true, 5_000, 1, 0));
        Result pickup = GroupBuyRefundCalculator.returnRefund(shipped(0, 0, 52_000), ONE_WAY, held, List.of(new Line(2, 1, CHANGE_OF_MIND)), COMPANY_PICKUP);
        assertThat(pickup.total()).isEqualTo(-1_000);
        Result send = GroupBuyRefundCalculator.returnRefund(shipped(0, 0, 52_000), ONE_WAY, held, List.of(new Line(2, 1, CHANGE_OF_MIND)), CUSTOMER_SEND);
        assertThat(send.total()).isEqualTo(2_000);
    }

    @Test
    void G32_발송_전_2개_취소_뒤_남은_1개는_처음부터_1개를_산_회원과_같다() {
        // 25,000 × 3 무료배송 → 발송 전 2개 취소(재부과 3,000 — 47,000 환불) → 발송 때 처음 배송비 3,000 으로 고정
        OrderView order = new OrderView(0, 3_000L, 3_000, 28_000);
        assertThat(ret(order, ONE_WAY, one(25_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP).total()).isEqualTo(22_000);
        assertThat(ret(order, ROUND_TRIP, one(25_000, 1), 1, CHANGE_OF_MIND, COMPANY_PICKUP).total()).isEqualTo(22_000);
        assertThat(ret(order, ONE_WAY, one(25_000, 1), 1, CHANGE_OF_MIND, CUSTOMER_SEND).total()).isEqualTo(25_000);
        assertThat(ret(order, ONE_WAY, one(25_000, 1), 1, DEFECT, COMPANY_PICKUP).total()).as("하자 — 처음 배송비까지").isEqualTo(28_000);
    }

    @Test
    void 결정42_발송_전_일부_취소_재부과_결정53_사업자_귀책은_재계산_없음() {
        OrderView order = new OrderView(0, null, 0, 75_000);
        List<Line> two = List.of(new Line(1, 2, null));
        assertThat(GroupBuyRefundCalculator.cancel(order, ONE_WAY, one(25_000, 3), two, true).total()).isEqualTo(47_000);
        assertThat(GroupBuyRefundCalculator.cancel(order, ONE_WAY, one(25_000, 3), two, false).total()).isEqualTo(50_000);
        Result whole = GroupBuyRefundCalculator.cancel(new OrderView(0, null, 3_000, 28_000), ONE_WAY, one(25_000, 1), List.of(new Line(1, 1, null)), true);
        assertThat(whole.whole()).isTrue();
        assertThat(whole.total()).as("전체면 남은 결제액 전부(재부과 돌려줌 — 결정 69)").isEqualTo(28_000);
    }

    @Test
    void 결정71_금액만_돌려준_운영_환불은_그_줄의_반품_환불에서_뺀다_최종은_남은_결제액_이하() {
        List<Held> held = List.of(new Held(1, 11, true, 30_000, 1, 10_000));
        Result r = GroupBuyRefundCalculator.returnRefund(shipped(3_000, 0, 23_000), ONE_WAY, held, List.of(new Line(1, 1, DEFECT)), CUSTOMER_SEND);
        assertThat(r.lines().getFirst().priorOffset()).isEqualTo(10_000);
        assertThat(r.total()).as("30,000 − 10,000 + 배송비 3,000, 남은 결제액 23,000").isEqualTo(23_000);
    }
}
