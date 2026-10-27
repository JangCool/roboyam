package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentAnomaly;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.service.PaymentConfirmService.Outcome;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 늦은 결제(commerce.md 8-2 의 5·18절 P3b·P15 의 일반형, 계약 결정 4) — 우리 상태가 EXPIRED 면 무조건 늦음, READY 면 포트원 {@code paidAt > expires_at} 일 때만 늦음
 * (확인 요청이 늦게 도착한 것은 회원 탓이 아니다). 숨은 여유 시간은 없다. 늦은 결제는 처리기를 부르지 않고(도메인 확정 없음) 예외 없이 전액 자동 취소한다.
 */
class LatePaymentTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    private static void assertLateAndCanceled(PaymentTestKit.Created created) {
        OrderRow row = kit.order(created.paymentId());
        assertThat(row.anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
        assertThat(row.accepted()).isFalse();
        assertThat(kit.handler.cashOf(created.orderRefId())).as("도메인 확정 없음").isZero();
        assertThat(kit.cancelsOf(row.id())).singleElement().satisfies(c -> {
            assertThat(c.kind()).isEqualTo(CancelKind.AUTO_REJECT);
            assertThat(c.amount()).isEqualTo(created.amount());
            assertThat(c.status()).isEqualTo(CancelStatus.SUCCEEDED);
        });
        assertThat(row.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(kit.notices).as("늦은 결제는 자동 취소가 정상 경로").isEmpty();
    }

    @Test
    void 우리_상태가_EXPIRED_면_포트원_결제_시각과_상관없이_늦은_결제다() {
        long member = kit.newMember();
        long ref = kit.ids.nextId();
        PaymentTestKit.Created old = kit.createFor(member, ref, 8_000, Decision.ACCEPT);
        kit.pgPaid(old.paymentId(), 8_000); // 유효 시각 안에 결제했지만
        kit.createFor(member, ref, 8_000, Decision.ACCEPT); // 확인 전에 재결제로 닫혔다

        assertThat(kit.confirm.confirm(old.paymentId(), ConfirmedVia.WEBHOOK).outcome()).isEqualTo(Outcome.REJECTED);
        assertLateAndCanceled(old);
    }

    @Test
    void READY_인데_포트원_결제_시각이_유효_시각_뒤면_늦은_결제다_여유_시간_없음() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 8_000, Decision.ACCEPT);
        OrderRow row = kit.order(created.paymentId());
        kit.pgPaidAt(created.paymentId(), 8_000, row.expiresAt().plus(1, ChronoUnit.MICROS));
        kit.clock.advance(Duration.ofMinutes(6));

        assertThat(kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API).outcome()).isEqualTo(Outcome.REJECTED);
        assertLateAndCanceled(created);
    }

    @Test
    void READY_이고_유효_시각_안에_결제했으면_확인이_늦게_와도_받아들인다() {
        PaymentTestKit.Created created = kit.create(kit.newMember(), 8_000, Decision.ACCEPT);
        OrderRow row = kit.order(created.paymentId());
        kit.pgPaidAt(created.paymentId(), 8_000, row.expiresAt()); // 경계 — 유효 시각과 같으면 늦지 않다
        kit.clock.advance(Duration.ofHours(2)); // 웹훅·READY 정리가 한참 뒤에 확인

        assertThat(kit.confirm.confirm(created.paymentId(), ConfirmedVia.READY_CLEANUP).outcome()).isEqualTo(Outcome.ACCEPTED);
        OrderRow after = kit.order(created.paymentId());
        assertThat(after.anomaly()).isNull();
        assertThat(after.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(kit.handler.cashOf(created.orderRefId())).isEqualTo(8_000);
    }

    @Test
    void 재결제가_받아들여진_뒤_닫은_시도가_늦게_결제되면_정상_결제와_부딪치지_않고_자동_취소된다() {
        long member = kit.newMember();
        long ref = kit.ids.nextId();
        PaymentTestKit.Created first = kit.createFor(member, ref, 4_000, Decision.ACCEPT);
        PaymentTestKit.Created second = kit.createFor(member, ref, 4_000, Decision.ACCEPT);
        kit.pgPaid(second.paymentId(), 4_000);
        assertThat(kit.confirm.confirm(second.paymentId(), ConfirmedVia.COMPLETE_API).outcome()).isEqualTo(Outcome.ACCEPTED);

        kit.pgPaid(first.paymentId(), 4_000); // 회원이 예전 창에서도 결제했다
        assertThat(kit.confirm.confirm(first.paymentId(), ConfirmedVia.WEBHOOK).outcome()).as("받아들인 결제 하나 색인에 부딪혀 되돌아가지 않는다")
                .isEqualTo(Outcome.REJECTED);

        assertThat(kit.order(first.paymentId()).anomaly()).isEqualTo(PaymentAnomaly.LATE_PAYMENT);
        assertThat(kit.order(first.paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(kit.order(second.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
        assertThat(kit.order(second.paymentId()).accepted()).isTrue();
        assertThat(kit.handler.cashOf(ref)).as("현금은 받아들인 결제 하나뿐").isEqualTo(4_000);
    }
}
