package com.community.bizapplication;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizPaymentMethod;
import com.community.bizapplication.entity.BizRefundStatus;
import com.community.bizapplication.entity.BizRefundType;
import com.community.bizapplication.repository.master.BizApplicationStore.AppRow;
import com.community.bizapplication.repository.master.BizRefundStore;
import com.community.bizapplication.repository.master.BizRefundStore.RefundRow;
import com.community.bizapplication.service.BizPeriods;
import com.community.bizapplication.service.BizPricing;
import com.community.payment.dto.PaymentCheckout;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * 취소·환불(H1) — 시작 전 = 전액 자동(온라인 PG 취소 / 수동 송금 대기) · 진행 중 = 일할 심사 대기(제안액, 노출 계속) · 끝남·거절 뒤 다시 = 409 · 운영자 승인(노출 중단 + 돈) · 거절 ·
 * 송금 완료 표시 · 운영자 취소(결제 전 · 시작 전 예약만).
 */
class BizApplicationRefundTest extends BizApplicationApiTestBase {

    private record Paid(long user, long id, PaymentCheckout checkout, BizPeriods.Period period) {
    }

    private Paid scheduledOnline() {
        long user = approvedBusiness();
        long id = submitTextLine(user, 0, 7, "ONLINE");
        BizPeriods.Period p = freshPeriod(7);
        approve(id, "ONLINE", p);
        PaymentCheckout checkout = pay(user, id);
        return new Paid(user, id, checkout, p);
    }

    private long scheduledManual(long user) {
        long id = submitTextLine(user, 0, 7, "MANUAL");
        approve(id, "MANUAL", freshPeriod(7));
        confirmManual(id);
        return id;
    }

    @Test
    void 시작_전_온라인_예약의_환불_요청은_전액_자동_취소와_PG_취소_대기열() {
        Paid p = scheduledOnline();
        members.requestRefund(p.user(), p.id(), "행사가 취소됐어요");
        AppRow a = app(p.id());
        assertThat(a.status()).isEqualTo(BizApplicationStatus.CANCELED);
        RefundRow r = refunds.latest(p.id()).orElseThrow();
        assertThat(r.type()).isEqualTo(BizRefundType.FULL_AUTO);
        assertThat(r.status()).isEqualTo(BizRefundStatus.APPROVED);
        assertThat(r.method()).isEqualTo(BizPaymentMethod.ONLINE);
        assertThat(r.approvedAmount()).isEqualTo(a.approved().total());
        assertThat(r.paymentCancelId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT kind || ':' || amount || ':' || request_key FROM payment_cancels WHERE id = ?", String.class, r.paymentCancelId()))
                .isEqualTo("REFUND:" + a.approved().total() + ":BIZ_REFUND:" + r.id());
        // 커밋 뒤 바로 한 번 시도(가짜 PG) → 처리기 onCanceled 가 환불 완료를 적는다
        assertThat(refunds.latest(p.id()).orElseThrow().completedAt()).isNotNull();
        assertThat(store.events(p.id())).extracting(e -> e.action()).contains("REFUND_REQUESTED", "CANCELED", "REFUND_APPROVED", "REFUNDED");
    }

    @Test
    void 시작_전_수동_예약의_환불은_송금_대기로_남고_운영자가_송금_완료를_표시한다() {
        long user = approvedBusiness();
        long id = scheduledManual(user);
        members.requestRefund(user, id, "취소합니다");
        RefundRow r = refunds.latest(id).orElseThrow();
        assertThat(app(id).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(r.method()).isEqualTo(BizPaymentMethod.MANUAL);
        assertThat(r.transferStatus()).isEqualTo(BizRefundStore.TRANSFER_PENDING);
        assertThat(r.approvedAmount()).isEqualTo(store.deposit(id).orElseThrow().amount());
        assertThat(r.completedAt()).isNull();
        review.markTransferred(operator, id, "국민은행 이체");
        RefundRow done = refunds.latest(id).orElseThrow();
        assertThat(done.transferStatus()).isEqualTo(BizRefundStore.TRANSFER_DONE);
        assertThat(done.completedAt()).isNotNull();
        assertThat(codeOf(() -> review.markTransferred(operator, id, null))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(jdbc.queryForObject("SELECT retention_class FROM audit_logs WHERE target_id = ? AND action = 'BIZ_APP_REFUND_TRANSFER'", String.class, Long.toString(id)))
                .isEqualTo("MONEY");
    }

    @Test
    void 진행_중_환불_요청은_일할_심사_대기와_제안액이고_노출은_계속된다() {
        Paid p = scheduledOnline();
        clock.set(p.period().start().plus(Duration.ofDays(2)).plus(Duration.ofHours(3))); // 7일 중 3일째(오늘 포함 쓴 날 3)
        members.requestRefund(p.user(), p.id(), "남은 기간 중단");
        AppRow a = app(p.id());
        assertThat(a.status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        RefundRow r = refunds.latest(p.id()).orElseThrow();
        assertThat(r.status()).isEqualTo(BizRefundStatus.REQUESTED);
        assertThat(r.type()).isEqualTo(BizRefundType.PRORATED);
        assertThat(r.totalDays()).isEqualTo(7);
        assertThat(r.usedDays()).isEqualTo(3);
        assertThat(r.proposedAmount()).isEqualTo(Math.floorDiv(a.approved().total() * 4, 7));
        assertThat(r.proposedAmount()).isEqualTo(BizPricing.prorate(a.approved().total(), a.startAt(), a.endAt(), clock.instant()).proposed());
        // 열린 환불이 있으면 다시 요청할 수 없다
        assertThat(codeOf(() -> members.requestRefund(p.user(), p.id(), "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 운영자_환불_승인은_노출을_멈추고_승인_금액만_PG_취소한다_범위_밖_금액은_400() {
        Paid p = scheduledOnline();
        clock.set(p.period().start().plus(Duration.ofDays(1)));
        members.requestRefund(p.user(), p.id(), "중단");
        long total = app(p.id()).approved().total();
        assertThat(fieldsOf(() -> review.approveRefund(operator, p.id(), total + 1, "승인"))).containsExactly("amount");
        assertThat(fieldsOf(() -> review.approveRefund(operator, p.id(), -1L, "승인"))).containsExactly("amount");
        review.approveRefund(operator, p.id(), 10_000L, "남은 기간 일부");
        assertThat(app(p.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        RefundRow r = refunds.latest(p.id()).orElseThrow();
        assertThat(r.status()).isEqualTo(BizRefundStatus.APPROVED);
        assertThat(r.approvedAmount()).isEqualTo(10_000);
        assertThat(jdbc.queryForObject("SELECT amount FROM payment_cancels WHERE id = ?", Long.class, r.paymentCancelId())).isEqualTo(10_000);
        assertThat(codeOf(() -> review.approveRefund(operator, p.id(), 1L, "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 환불_거절은_상태를_그대로_두고_거절된_뒤_다시_요청은_막는다() {
        Paid p = scheduledOnline();
        clock.set(p.period().start().plus(Duration.ofDays(1)));
        members.requestRefund(p.user(), p.id(), "중단");
        review.denyRefund(operator, p.id(), "정책상 환불 불가");
        assertThat(app(p.id()).status()).isEqualTo(BizApplicationStatus.SCHEDULED);
        assertThat(refunds.latest(p.id()).orElseThrow().status()).isEqualTo(BizRefundStatus.DENIED);
        assertThat(codeOf(() -> members.requestRefund(p.user(), p.id(), "다시"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(codeOf(() -> review.denyRefund(operator, p.id(), "또"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
    }

    @Test
    void 끝난_예약과_예약이_아닌_건은_환불_요청할_수_없다() {
        Paid p = scheduledOnline();
        clock.set(p.period().end());
        assertThat(codeOf(() -> members.requestRefund(p.user(), p.id(), "끝났지만"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        long user = approvedBusiness();
        long submitted = submitTextLine(user, 0, 3, "ONLINE");
        assertThat(codeOf(() -> members.requestRefund(user, submitted, "아직"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(fieldsOf(() -> members.requestRefund(user, submitted, " "))).containsExactly("reason");
    }

    @Test
    void 운영자_취소는_결제_전과_시작_전_예약만_입금_기록이_있으면_수동_환불_송금_대기() {
        long user = approvedBusiness();
        long manual = submitTextLine(user, 0, 3, "MANUAL");
        approve(manual, "MANUAL", freshPeriod(3));
        review.saveDeposit(operator, manual, app(manual).approved().total(), today().toString(), "시험상사", null);
        review.cancel(operator, manual, "광고주 요청");
        assertThat(app(manual).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(manual).orElseThrow().transferStatus()).isEqualTo(BizRefundStore.TRANSFER_PENDING);

        long submitted = submitTextLine(user, 0, 3, "ONLINE");
        assertThat(codeOf(() -> review.cancel(operator, submitted, "심사 대기는 반려로"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");

        Paid running = scheduledOnline();
        Paid upcoming = scheduledOnline();
        review.cancel(operator, upcoming.id(), "운영 사유");
        assertThat(app(upcoming.id()).status()).isEqualTo(BizApplicationStatus.CANCELED);
        assertThat(refunds.latest(upcoming.id()).orElseThrow().type()).isEqualTo(BizRefundType.FULL_AUTO);
        clock.set(running.period().start().plusSeconds(60));
        assertThat(codeOf(() -> review.cancel(operator, running.id(), "진행 중은 환불 승인으로"))).isEqualTo("BIZ_APPLICATION_STATE_CHANGED");
        assertThat(fieldsOf(() -> review.cancel(operator, running.id(), ""))).containsExactly("reason");
    }
}
