package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.gateway.PgPaymentStatus;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.service.PaymentConfirmService.ConfirmResult;
import com.community.payment.service.PaymentConfirmService.Outcome;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 확정 코드 하나({@code PaymentConfirmService.confirm}, commerce.md 8-2 표 전부·계약 「확정 흐름」) — 실제 PostgreSQL, 가짜 PG.
 *
 * <ul>
 *   <li>포트원이 PAID 가 아니면 그대로 READY(완료 API 의 409 는 {@code PaymentCompletionTest}) · PAID + 금액 일치 → 처리기 ACCEPT 면 확정 · REJECT 면 DOMAIN_REJECTED + 전액 자동 취소</li>
 *   <li>금액·통화 불일치 → 처리기를 부르지 않고 전액 자동 취소 + 운영자 알림 · 포트원 장애 → 아무것도 바꾸지 않음(503) · 처리기 예외 → 전체 되돌림(READY, 취소 없음)</li>
 * </ul>
 */
class PaymentConfirmServiceTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    @Test
    void 포트원이_결제_완료가_아니면_READY_그대로다() {
        PaymentTestKit.Created none = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        assertThat(kit.confirm.confirm(none.paymentId(), ConfirmedVia.COMPLETE_API).outcome()).as("포트원에 없음").isEqualTo(Outcome.NOT_PAID);

        PaymentTestKit.Created failed = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        kit.pgStatus(failed.paymentId(), PgPaymentStatus.FAILED, 10_000);
        assertThat(kit.confirm.confirm(failed.paymentId(), ConfirmedVia.WEBHOOK).outcome()).as("결제 실패").isEqualTo(Outcome.NOT_PAID);

        PaymentTestKit.Created ready = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        kit.pgStatus(ready.paymentId(), PgPaymentStatus.READY, 10_000);
        assertThat(kit.confirm.confirm(ready.paymentId(), ConfirmedVia.WEBHOOK).outcome()).as("결제창 열림").isEqualTo(Outcome.NOT_PAID);

        for (PaymentTestKit.Created created : List.of(none, failed, ready)) {
            OrderRow row = kit.order(created.paymentId());
            assertThat(row.status()).isEqualTo(PaymentStatus.READY);
            assertThat(row.paidAmount()).isNull();
            assertThat(kit.handler.cashOf(created.orderRefId())).isZero();
        }
    }

    @Test
    void PAID_이고_금액이_맞고_처리기가_받아들이면_확정한다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 23_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 23_000);

        ConfirmResult result = kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);

        assertThat(result.outcome()).isEqualTo(Outcome.ACCEPTED);
        OrderRow row = kit.order(created.paymentId());
        assertThat(row.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(row.anomaly()).isNull();
        assertThat(row.accepted()).isTrue();
        assertThat(row.paidAmount()).isEqualTo(23_000);
        assertThat(row.paidAt()).isEqualTo(kit.now());
        assertThat(row.confirmedVia()).isEqualTo(ConfirmedVia.COMPLETE_API);
        assertThat(row.pgTxId()).isEqualTo("fake-tx-" + created.paymentId());
        assertThat(kit.handler.cashOf(created.orderRefId())).as("도메인 확정").isEqualTo(23_000);
        assertThat(kit.cancelsOf(row.id())).isEmpty();
        assertThat(kit.events(row.id(), "PAID")).isEqualTo(1);
        assertThat(kit.notices).isEmpty();
    }

    @Test
    void 이미_확정한_결제는_다시_조회하지_않고_같은_결과를_돌려준다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 5_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 5_000);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.WEBHOOK);
        int calls = kit.gateway.getCalls.get();

        ConfirmResult again = kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);

        assertThat(again.outcome()).isEqualTo(Outcome.ALREADY_DONE);
        assertThat(again.order().status()).isEqualTo(PaymentStatus.PAID);
        assertThat(again.order().confirmedVia()).as("먼저 온 쪽").isEqualTo(ConfirmedVia.WEBHOOK);
        assertThat(kit.gateway.getCalls.get()).isEqualTo(calls);
        assertThat(kit.handler.cashOf(created.orderRefId())).as("onPaid 한 번").isEqualTo(5_000);
        assertThat(kit.confirm.confirm("pay_" + "x".repeat(32), ConfirmedVia.WEBHOOK).outcome()).isEqualTo(Outcome.NOT_FOUND);
    }

    @Test
    void 처리기가_거절하면_DOMAIN_REJECTED_와_전액_자동_취소이고_커밋_뒤_바로_취소된다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 30_000, Decision.REJECT);
        kit.pgPaid(created.paymentId(), 30_000);

        ConfirmResult result = kit.confirm.confirm(created.paymentId(), ConfirmedVia.WEBHOOK);

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        OrderRow row = kit.order(created.paymentId());
        assertThat(row.anomaly()).isEqualTo(PaymentAnomaly.DOMAIN_REJECTED);
        assertThat(row.accepted()).isFalse();
        List<CancelRow> cancels = kit.cancelsOf(row.id());
        assertThat(cancels).hasSize(1);
        CancelRow cancel = cancels.get(0);
        assertThat(cancel.kind()).isEqualTo(CancelKind.AUTO_REJECT);
        assertThat(cancel.amount()).as("전액").isEqualTo(30_000);
        assertThat(cancel.requestKey()).isEqualTo("AUTO_REJECT:" + row.id());
        // 커밋 뒤 즉시 시도(D10) — 워커 없이 끝났다
        assertThat(cancel.status()).isEqualTo(CancelStatus.SUCCEEDED);
        assertThat(cancel.attempts()).isEqualTo(1);
        assertThat(row.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(row.canceledAmount()).isEqualTo(30_000);
        assertThat(kit.handler.cashOf(created.orderRefId())).isZero();
        assertThat(kit.handler.canceledCallsOf(cancel.id())).as("onCanceled 한 번").isEqualTo(1);
        assertThat(kit.notices).as("도메인 거절은 정상 경로 — 알리지 않는다").isEmpty();
        assertThat(kit.events(row.id(), "ANOMALY")).isEqualTo(1);
    }

    @Test
    void 금액이_다르면_처리기를_부르지_않고_전액_자동_취소와_운영자_알림() {
        PaymentTestKit.Created less = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        kit.pgPaid(less.paymentId(), 9_000);

        ConfirmResult result = kit.confirm.confirm(less.paymentId(), ConfirmedVia.COMPLETE_API);

        assertThat(result.outcome()).isEqualTo(Outcome.REJECTED);
        OrderRow row = kit.order(less.paymentId());
        assertThat(row.anomaly()).isEqualTo(PaymentAnomaly.AMOUNT_MISMATCH);
        assertThat(row.paidAmount()).as("포트원이 알려 준 금액").isEqualTo(9_000);
        assertThat(kit.handler.cashOf(less.orderRefId())).as("도메인 확정 없음 — onPaid 를 부르지 않았다").isZero();
        assertThat(kit.cancelsOf(row.id())).singleElement().satisfies(c -> {
            assertThat(c.amount()).isEqualTo(9_000);
            assertThat(c.status()).isEqualTo(CancelStatus.SUCCEEDED);
        });
        assertThat(row.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(kit.notices).containsExactly(AdminNotificationEvent.COMMERCE_PAYMENT_ANOMALY);

        PaymentTestKit.Created more = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        kit.pgPaid(more.paymentId(), 11_000);
        assertThat(kit.confirm.confirm(more.paymentId(), ConfirmedVia.WEBHOOK).order().anomaly()).isEqualTo(PaymentAnomaly.AMOUNT_MISMATCH);
        assertThat(kit.order(more.paymentId()).canceledAmount()).isEqualTo(11_000);
    }

    @Test
    void 통화가_다르면_금액_불일치와_같다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        kit.fakePg.pay(created.paymentId(), 10_000, "USD", kit.now());
        assertThat(kit.confirm.confirm(created.paymentId(), ConfirmedVia.WEBHOOK).order().anomaly()).isEqualTo(PaymentAnomaly.AMOUNT_MISMATCH);
        assertThat(kit.handler.cashOf(created.orderRefId())).isZero();
    }

    @Test
    void 포트원_장애는_아무것도_바꾸지_않고_503() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), 10_000);
        kit.gateway.getDown = true;

        assertThatThrownBy(() -> kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API)).isInstanceOf(PaymentGatewayUnavailableException.class)
                .satisfies(e -> assertThat(((PaymentGatewayUnavailableException) e).getStatus().value()).isEqualTo(503));
        OrderRow row = kit.order(created.paymentId());
        assertThat(row.status()).isEqualTo(PaymentStatus.READY);
        assertThat(kit.events(row.id(), "PAID")).isZero();

        kit.gateway.getDown = false;
        assertThat(kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API).outcome()).as("복구 뒤 다시").isEqualTo(Outcome.ACCEPTED);
    }

    @Test
    void 처리기_예외는_확정_전체를_되돌리고_다음_시도가_다시_한다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ERROR);
        kit.pgPaid(created.paymentId(), 10_000);

        assertThatThrownBy(() -> kit.confirm.confirm(created.paymentId(), ConfirmedVia.WEBHOOK)).isInstanceOf(IllegalStateException.class);

        OrderRow row = kit.order(created.paymentId());
        assertThat(row.status()).as("READY 그대로").isEqualTo(PaymentStatus.READY);
        assertThat(row.paidAmount()).isNull();
        assertThat(kit.cancelsOf(row.id())).as("취소 없음").isEmpty();
        assertThat(kit.events(row.id(), "PAID")).isZero();

        kit.handler.decide(created.orderRefId(), Decision.ACCEPT); // 도메인 쪽 문제가 풀렸다
        assertThat(kit.confirm.confirm(created.paymentId(), ConfirmedVia.READY_CLEANUP).outcome()).isEqualTo(Outcome.ACCEPTED);
        assertThat(kit.handler.cashOf(created.orderRefId())).isEqualTo(10_000);
    }
}
