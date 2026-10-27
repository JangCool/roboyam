package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.response.PaymentCompletionView;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotPaidException;
import com.community.payment.exception.PaymentOrderMismatchException;
import com.community.payment.exception.PaymentOrderNotFoundException;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import com.community.zconfiguration.exception.CommonException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 완료 API 의 업무 규칙(계약 결정 6·commerce.md 18절 P24) — 그 회원·그 주문의 결제가 아니면 <b>포트원 조회 없이</b> 거절, 아직 결제 전이면 409 {@code PAYMENT_NOT_PAID}(READY 그대로),
 * 포트원 장애 503, 웹훅이 먼저 확정했으면 같은 결과. HTTP 모양은 api 의 {@code PaymentCompletionHttpTest}.
 */
class PaymentCompletionTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    private static int status(CommonException e) {
        return e.getStatus().value();
    }

    @Test
    void 다른_회원의_결제는_404_이고_포트원을_조회하지_않는다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 7_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 7_000);

        assertThatThrownBy(() -> kit.completion.complete(kit.newMember(), created.paymentId(), PaymentOrderType.PAYMENT_TEST, created.orderRefId()))
                .isInstanceOf(PaymentOrderNotFoundException.class).satisfies(e -> assertThat(status((CommonException) e)).isEqualTo(404));
        assertThatThrownBy(() -> kit.completion.complete(created.userId(), "pay_" + "Q".repeat(32), PaymentOrderType.PAYMENT_TEST, created.orderRefId()))
                .as("없는 결제").isInstanceOf(PaymentOrderNotFoundException.class);
        assertThat(kit.gateway.getCalls.get()).isZero();
        assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 그_회원의_다른_주문_결제는_409_이고_포트원을_조회하지_않는다() {
        long member = kit.newMember();
        PaymentTestKit.Created mine = kit.create(member, 7_000, Decision.ACCEPT);
        PaymentTestKit.Created other = kit.create(member, 7_000, Decision.ACCEPT);
        kit.pgPaid(mine.paymentId(), 7_000);

        assertThatThrownBy(() -> kit.completion.complete(member, mine.paymentId(), PaymentOrderType.PAYMENT_TEST, other.orderRefId()))
                .isInstanceOf(PaymentOrderMismatchException.class).satisfies(e -> assertThat(status((CommonException) e)).isEqualTo(409));
        assertThatThrownBy(() -> kit.completion.complete(member, mine.paymentId(), PaymentOrderType.GROUP_BUY, mine.orderRefId()))
                .as("다른 종류").isInstanceOf(PaymentOrderMismatchException.class);
        assertThat(kit.gateway.getCalls.get()).isZero();
        assertThat(kit.order(mine.paymentId()).status()).isEqualTo(PaymentStatus.READY);
    }

    @Test
    void 아직_결제_전이면_409_PAYMENT_NOT_PAID_이고_다시_부를_수_있다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 7_000, Decision.ACCEPT);
        assertThatThrownBy(() -> kit.completion.complete(created.userId(), created.paymentId(), PaymentOrderType.PAYMENT_TEST, created.orderRefId()))
                .isInstanceOf(PaymentNotPaidException.class).satisfies(e -> {
                    assertThat(status((CommonException) e)).isEqualTo(409);
                    assertThat(((CommonException) e).getErrorCode()).isEqualTo("PAYMENT_NOT_PAID");
                });
        assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.READY);

        kit.pgPaid(created.paymentId(), 7_000);
        PaymentCompletionView view = kit.completion.complete(created.userId(), created.paymentId(), PaymentOrderType.PAYMENT_TEST, created.orderRefId());
        assertThat(view.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(view.accepted()).isTrue();
        assertThat(view.paidAmount()).isEqualTo(7_000);
        assertThat(view.orderRefId()).isEqualTo(Long.toString(created.orderRefId()));
        assertThat(kit.order(created.paymentId()).confirmedVia()).isEqualTo(ConfirmedVia.COMPLETE_API);
    }

    @Test
    void 포트원_장애는_503() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 7_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 7_000);
        kit.gateway.getDown = true;
        assertThatThrownBy(() -> kit.completion.complete(created.userId(), created.paymentId(), PaymentOrderType.PAYMENT_TEST, created.orderRefId()))
                .isInstanceOf(PaymentGatewayUnavailableException.class).satisfies(e -> assertThat(status((CommonException) e)).isEqualTo(503));
    }

    @Test
    void 웹훅이_먼저_확정했으면_같은_결과를_돌려주고_이상_결제는_accepted_false() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 7_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 7_000);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.WEBHOOK);
        PaymentCompletionView view = kit.completion.complete(created.userId(), created.paymentId(), PaymentOrderType.PAYMENT_TEST, created.orderRefId());
        assertThat(view.accepted()).isTrue();
        assertThat(kit.handler.cashOf(created.orderRefId())).isEqualTo(7_000);

        PaymentTestKit.Created rejected = kit.create(kit.newMember(), 7_000, Decision.REJECT);
        kit.pgPaid(rejected.paymentId(), 7_000);
        PaymentCompletionView rejectedView = kit.completion.complete(rejected.userId(), rejected.paymentId(), PaymentOrderType.PAYMENT_TEST, rejected.orderRefId());
        assertThat(rejectedView.accepted()).isFalse();
        assertThat(rejectedView.anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);
    }
}
