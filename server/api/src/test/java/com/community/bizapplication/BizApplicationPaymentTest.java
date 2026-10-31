package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.handler.BizApplicationPaymentHandler;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaidPayment;
import com.community.payment.dto.PaymentCheckout;
import com.community.payment.dto.PaymentDecision;
import com.community.payment.dto.PaymentOrderType;
import com.community.payment.service.PaymentConfirmService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 온라인 결제(설계 5절 · commerce.md 8-2·8-6) — 결제 확정 → 예약 · 늦은 결제·금액 불일치·상태 아님은 거절(예외 없이 REJECT → 결제 모듈이 자동 취소 대기열에 넣는다) · 처리기는 결제 확정 트랜잭션 안.
 */
class BizApplicationPaymentTest extends BizApplicationApiTestBase {

    @Autowired
    private BizApplicationPaymentHandler handler;

    private long awaitingPayment(long user) {
        long id = submitTextLine(user, 0, 3, "ONLINE");
        approve(id, "ONLINE", freshPeriod(3));
        return id;
    }

    private int autoRejects(String paymentId) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_cancels c JOIN payment_orders o ON o.id = c.payment_order_id WHERE o.payment_id = ? AND c.kind = 'AUTO_REJECT'",
                Integer.class, paymentId);
    }

    @Test
    void 결제가_확정되면_예약과_결제_시각_결제_주문이_남고_사업자_알림() {
        long user = approvedBusiness();
        long id = awaitingPayment(user);
        PaymentCheckout checkout = pay(user, id);
        AppRow a = app(id);
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(a.paidAt()).isNotNull();
        assertThat(a.paymentOrderId()).isEqualTo(checkout.paymentOrderId());
        assertThat(store.events(id)).extracting(e -> e.action()).contains("PAID", "SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_notifications WHERE user_id = ? AND payload->>'kind' = 'PAID'", Integer.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT order_type FROM payment_orders WHERE id = ?", String.class, checkout.paymentOrderId())).isEqualTo("BIZ_APPLICATION");
    }

    @Test
    void 결제_유효_시각이_지난_결제는_늦은_결제로_자동_취소되고_신청_건은_그대로() {
        long user = approvedBusiness();
        long id = awaitingPayment(user);
        PaymentCheckout checkout = members.startPayment(user, id);
        fakePg.pay(checkout.paymentId(), checkout.amount(), "KRW", checkout.expiresAt().plusSeconds(1));
        PaymentConfirmService.ConfirmResult result = confirm.confirm(checkout.paymentId(), ConfirmedVia.WEBHOOK);
        assertThat(result.outcome()).isEqualTo(PaymentConfirmService.Outcome.REJECTED);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(autoRejects(checkout.paymentId())).isEqualTo(1);
    }

    @Test
    void PG_금액이_다르면_결제_모듈이_금액_불일치로_자동_취소한다() {
        long user = approvedBusiness();
        long id = awaitingPayment(user);
        PaymentCheckout checkout = members.startPayment(user, id);
        fakePg.pay(checkout.paymentId(), checkout.amount() - 1, "KRW", clock.instant());
        assertThat(confirm.confirm(checkout.paymentId(), ConfirmedVia.COMPLETE_API).outcome()).isEqualTo(PaymentConfirmService.Outcome.REJECTED);
        assertThat(jdbc.queryForObject("SELECT anomaly FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("AMOUNT_MISMATCH");
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        assertThat(autoRejects(checkout.paymentId())).isEqualTo(1);
    }

    @Test
    void 처리기는_금액_불일치_결제_기한_뒤_결제_대기_아님_다른_회원_없는_건을_예외_없이_거절한다() {
        long user = approvedBusiness();
        long id = awaitingPayment(user);
        AppRow a = app(id);
        long total = a.approved().total();
        Instant ok = clock.instant();
        assertThat(onPaid(id, user, total - 1, ok)).isEqualTo(PaymentDecision.reject(BizApplicationPaymentHandler.REJECT_AMOUNT));
        assertThat(onPaid(id, user, total, a.paymentDeadlineAt().plusSeconds(1))).isEqualTo(PaymentDecision.reject(BizApplicationPaymentHandler.REJECT_LATE));
        assertThat(onPaid(id, user + 1, total, ok)).isEqualTo(PaymentDecision.reject(BizApplicationPaymentHandler.REJECT_OTHER_MEMBER));
        assertThat(onPaid(-1, user, total, ok)).isEqualTo(PaymentDecision.reject(BizApplicationPaymentHandler.REJECT_NOT_FOUND));
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.AWAITING_PAYMENT);
        review.switchToManual(operator, id, clock.instant().plus(Duration.ofDays(3)).toString(), "계좌이체");
        assertThat(onPaid(id, user, total, ok)).isEqualTo(PaymentDecision.reject(BizApplicationPaymentHandler.REJECT_NOT_AWAITING));
    }

    @Test
    void 처리기가_거절하면_도메인_거절로_자동_취소_대기열에_들어간다() {
        long user = approvedBusiness();
        long id = awaitingPayment(user);
        PaymentCheckout checkout = members.startPayment(user, id);
        // 결제창이 열린 사이 운영자가 수동으로 바꾸지 않고 상태만 바뀐 경우를 흉내 — 신청 건이 다른 길로 결제 대기가 아니게 됐다
        jdbc.update("UPDATE biz_applications SET status = 'CANCELED' WHERE id = ?", id);
        fakePg.pay(checkout.paymentId(), checkout.amount(), "KRW", clock.instant());
        assertThat(confirm.confirm(checkout.paymentId(), ConfirmedVia.WEBHOOK).outcome()).isEqualTo(PaymentConfirmService.Outcome.REJECTED);
        assertThat(jdbc.queryForObject("SELECT anomaly FROM payment_orders WHERE payment_id = ?", String.class, checkout.paymentId())).isEqualTo("DOMAIN_REJECTED");
        assertThat(autoRejects(checkout.paymentId())).isEqualTo(1);
        assertThat(app(id).paidAt()).isNull();
    }

    @Test
    void 결제_기한이_지나_만료된_건에_늦게_결제가_와도_예약되지_않는다() {
        long user = approvedBusiness();
        long id = awaitingPayment(user);
        PaymentCheckout checkout = members.startPayment(user, id);
        clock.set(app(id).paymentDeadlineAt().plusSeconds(1));
        assertThat(schedules.expire(id)).isTrue();
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.EXPIRED);
        fakePg.pay(checkout.paymentId(), checkout.amount(), "KRW", clock.instant());
        assertThat(confirm.confirm(checkout.paymentId(), ConfirmedVia.WEBHOOK).outcome()).isEqualTo(PaymentConfirmService.Outcome.REJECTED);
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.EXPIRED);
        assertThat(autoRejects(checkout.paymentId())).isEqualTo(1);
    }

    private PaymentDecision onPaid(long id, long user, long amount, Instant paidAt) {
        return tx.execute(status -> handler.onPaid(new PaidPayment(1L, "pay_test", user, PaymentOrderType.BIZ_APPLICATION, id, amount, paidAt, ConfirmedVia.WEBHOOK)));
    }
}
