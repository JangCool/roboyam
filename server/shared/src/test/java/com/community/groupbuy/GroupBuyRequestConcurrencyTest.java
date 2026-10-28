package com.community.groupbuy;

import static com.community.groupbuy.GroupBuyTestKit.lines;
import static com.community.groupbuy.entity.CollectionMethod.COMPANY_PICKUP;
import static com.community.groupbuy.entity.RequestReason.DEFECT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.groupbuy.dto.JoinResult;
import com.community.groupbuy.entity.ParticipationStatus;
import com.community.groupbuy.entity.RequestStatus;
import com.community.groupbuy.entity.RequestType;
import com.community.groupbuy.entity.ReturnDisposition;
import com.community.groupbuy.exception.GroupBuyException;
import com.community.groupbuy.service.GroupBuyRequestService.Created;
import com.community.groupbuy.service.GroupBuyRequestService.DispositionInput;
import com.community.groupbuy.service.GroupBuyRequestService.LineInput;
import com.community.groupbuy.service.GroupBuyRequestService.RequestInput;
import com.community.payment.dto.CancelStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 요청 승인과 다른 환불이 같은 주문에서 동시에(계약 G17 — 2b-3 보강, 사용자 지시 2026-10-28). 연결 여럿으로 실제로 겹치게 하고, 이기는 쪽이 바뀌도록 여러 번 돌린다.
 * 어느 쪽이 이기든: 교착 없음 · 돈이 두 번 나가지 않음(환불 기록 합 = 환불 누계 = 성공한 PG 취소 합) · 남은 결제액을 넘지 않음 · 불변식.
 */
class GroupBuyRequestConcurrencyTest {

    private static final int ROUNDS = 4;

    private static GroupBuyTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = GroupBuyTestKit.get();
    }

    @BeforeEach
    void reset() {
        kit.reset();
    }

    private static RequestInput cancelOne(long skuId) {
        return new RequestInput(RequestType.CANCEL, null, null, List.of(new LineInput(skuId, 1, null)), null, null, null);
    }

    @Test
    void G17_발송_전_취소_요청_승인과_운영_환불이_동시면_한쪽만_남은_결제액만큼() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
            long sku = kit.mainSku(gb);
            long member = kit.newMember();
            JoinResult order = kit.paidOrder(member, gb, lines(sku, 3)); // 75,000 무료배송
            Created request = kit.requests.create(member, order.participationId(), cancelOne(sku));

            List<Object> results = GroupBuyTestKit.runTogether(List.of(
                    () -> {
                        kit.requests.approve(request.requestId(), null, kit.operator, "ip");
                        return "APPROVED";
                    },
                    () -> kit.adminRefunds.refund(order.participationId(), null, null, true, "사업자 귀책 전체 취소", true, kit.operator, "ip")));

            assertThat(results).as("교착·인프라 오류 없음 " + results).noneMatch(r -> r instanceof Exception e && !(e instanceof com.community.zconfiguration.exception.CommonException));
            assertThat(results.getFirst()).as("승인은 늘 된다 — 운영 환불은 열린 요청이 있으면 받지 않는다").isEqualTo("APPROVED");
            assertThat(requestStatus(request.requestId())).isEqualTo(RequestStatus.COMPLETED);
            var p = kit.participation(order.participationId());
            if (results.get(1) instanceof GroupBuyException e) {
                assertThat(e.getErrorCode()).as("운영 환불이 먼저 주문을 잡았다 — 열린 요청").isEqualTo(GroupBuyException.REQUEST_OPEN);
                assertThat(p.status()).isEqualTo(ParticipationStatus.CONFIRMED);
                assertThat(p.refundedAmount()).as("1개만").isEqualTo(25_000);
            } else {
                assertThat(p.status()).as("승인 커밋 뒤 운영 전체 취소 — 남은 2개만").isEqualTo(ParticipationStatus.CANCELED);
                assertThat(p.refundedAmount()).as("1개 + 나머지 = 전액 한 번").isEqualTo(75_000);
            }
            assertMoney(gb, order, null);
        }
    }

    @Test
    void G17_반품_완료와_운영_환불이_동시면_한쪽이_기다리고_남은_결제액을_넘지_않는다() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
            long sku = kit.mainSku(gb);
            long member = kit.newMember();
            JoinResult order = kit.delivered(member, gb, lines(sku, 2)); // 50,000 무료배송
            Created request = kit.requests.create(member, order.participationId(),
                    new RequestInput(RequestType.RETURN, COMPANY_PICKUP, null, List.of(new LineInput(sku, 1, DEFECT)), null, null, null));
            kit.requests.approve(request.requestId(), null, kit.operator, "ip");
            kit.requests.receive(request.requestId(), kit.operator);

            List<Object> results = GroupBuyTestKit.runTogether(List.of(
                    () -> {
                        kit.requests.complete(request.requestId(), List.of(new DispositionInput(sku, ReturnDisposition.DISCARD, 1)), 0, null, false, kit.operator, "ip");
                        return "COMPLETED";
                    },
                    () -> kit.adminRefunds.refund(order.participationId(), null, 20_000L, false, "가격 조정 보상", true, kit.operator, "ip")));

            assertThat(results).as("교착·인프라 오류 없음 " + results).noneMatch(r -> r instanceof Exception e && !(e instanceof com.community.zconfiguration.exception.CommonException));
            assertThat(results.getFirst()).isEqualTo("COMPLETED");
            long refunded = kit.participation(order.participationId()).refundedAmount();
            if (results.get(1) instanceof GroupBuyException e) {
                assertThat(e.getErrorCode()).as("운영 환불이 먼저 주문을 잡았다 — 열린 요청").isEqualTo(GroupBuyException.REQUEST_OPEN);
                assertThat(refunded).isEqualTo(25_000);
            } else {
                assertThat(refunded).as("반품 25,000 뒤 남은 결제액 25,000 안에서 20,000").isEqualTo(45_000);
            }
            assertMoney(gb, order, null);
            assertThatThrownBy(() -> kit.adminRefunds.refund(order.participationId(), null, 50_000L - refunded + 1, false, "가격 조정 보상", true, kit.operator, "ip"))
                    .as("남은 결제액 + 1 은 400").isInstanceOf(com.community.zconfiguration.exception.ValidationException.class);
            assertMoney(gb, order, refunded);
        }
    }

    @Test
    void G17_발송_전_취소_요청_승인과_공구_중단_환불_워커가_동시면_전액이_한_번만() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            long gb = kit.spec(kit.policy(3_000, 50_000L, 3_000, "ONE_WAY")).open();
            long sku = kit.mainSku(gb);
            long member = kit.newMember();
            JoinResult order = kit.paidOrder(member, gb, lines(sku, 3));
            Created request = kit.requests.create(member, order.participationId(), cancelOne(sku));
            kit.admin.cancel(gb, "공급처 사정으로 중단", kit.operator, "ip"); // ① 공구만 — 주문 환불은 워커(②)

            List<Object> results = GroupBuyTestKit.runTogether(List.of(
                    () -> {
                        kit.requests.approve(request.requestId(), null, kit.operator, "ip");
                        return "APPROVED";
                    },
                    () -> kit.refundWorker.runOnce()));

            assertThat(results).as("교착·인프라 오류 없음 " + results).noneMatch(r -> r instanceof Exception e && !(e instanceof com.community.zconfiguration.exception.CommonException));
            kit.runRefundWorker(); // 잠겨서 건너뛴 주문은 다음 바퀴
            assertMoney(gb, order, 75_000L);
            assertThat(kit.participation(order.participationId()).status()).isIn(ParticipationStatus.REFUNDED, ParticipationStatus.CANCELED);
            assertThat(requestStatus(request.requestId())).isIn(RequestStatus.COMPLETED, RequestStatus.SUPERSEDED);
        }
    }

    private RequestStatus requestStatus(long requestId) {
        return RequestStatus.valueOf(kit.jdbc.queryForObject("SELECT status FROM group_buy_requests WHERE id = ?", String.class, requestId));
    }

    /** 돈이 두 번 나가지 않았다 — 환불 기록 합 = 환불 누계 = 성공한 PG 취소 합 ≤ 결제액. {@code expected} 가 있으면 누계가 그 값 */
    private void assertMoney(long gb, JoinResult order, Long expected) {
        kit.settleCancels();
        var p = kit.participation(order.participationId());
        var payment = kit.payment(order.checkout().paymentId());
        Long recorded = kit.jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM group_buy_refunds WHERE participation_id = ?", Long.class, p.id());
        long cancelled = kit.paymentCancels.findByOrder(payment.id()).stream().filter(c -> c.status() == CancelStatus.SUCCEEDED).mapToLong(c -> c.amount()).sum();
        assertThat(p.refundedAmount()).as("남은 결제액을 넘지 않는다").isLessThanOrEqualTo(p.cashAmount());
        assertThat(recorded).as("환불 기록 합 = 누계").isEqualTo(p.refundedAmount());
        assertThat(cancelled).as("PG 취소 합 = 누계(두 번 나가지 않음)").isEqualTo(p.refundedAmount());
        if (expected != null) {
            assertThat(p.refundedAmount()).isEqualTo(expected);
        }
        kit.assertInvariants(gb);
    }
}
