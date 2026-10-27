package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.TestGateway.CancelScript;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.repository.master.PaymentOrderStore.OrderRow;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 결제 불변식(commerce.md 18절 「불변식 추가」, 계약 「규칙 → 테스트」) — 여러 결제에 받아들임·거절·금액 불일치·늦은 결제·부분 환불·포트원 장애·접수만·응답 끊김을 섞어 돌린 뒤,
 * 결제 주문마다 ① 취소 누계 = 성공한 취소 금액 합 ② 받아들인 결제는 결제 − 취소 = 도메인(시험 처리기)의 현금 ③ 이상 결제는 도메인 현금 0 이고 전액 취소된다.
 */
class PaymentInvariantTest {

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
    void 섞어_돌려도_취소_누계와_도메인_현금이_맞는다() {
        Random random = new Random(20261026L);
        List<PaymentTestKit.Created> all = new ArrayList<>();
        CancelScript[] faults = {CancelScript.DOWN, CancelScript.FAILED, CancelScript.REQUESTED, CancelScript.DONE_BUT_TIMEOUT};
        for (int i = 0; i < 30; i++) {
            long amount = 1_000L * (1 + random.nextInt(20));
            int kind = random.nextInt(5);
            Decision decision = kind == 1 ? Decision.REJECT : Decision.ACCEPT;
            PaymentTestKit.Created created = kit.create(kit.newMember(), amount, decision);
            all.add(created);
            if (random.nextInt(3) == 0) {
                kit.gateway.script(faults[random.nextInt(faults.length)]); // 자동 취소·첫 환불의 즉시 시도가 실패할 수 있다
            }
            switch (kind) {
                case 2 -> kit.pgPaid(created.paymentId(), amount + 500); // 금액 불일치
                case 3 -> kit.pgPaidAt(created.paymentId(), amount, kit.order(created.paymentId()).expiresAt().plusSeconds(1)); // 늦은 결제
                default -> kit.pgPaid(created.paymentId(), amount);
            }
            kit.confirm.confirm(created.paymentId(), random.nextBoolean() ? ConfirmedVia.WEBHOOK : ConfirmedVia.COMPLETE_API);
            OrderRow row = kit.order(created.paymentId());
            if (row.accepted() && random.nextBoolean()) {
                long part = Math.max(1, row.paidAmount() / (2 + random.nextInt(3)));
                kit.tx.executeWithoutResult(status -> kit.cancels.request(new PaymentCancelOrder(row.id(), part, CancelKind.REFUND, "부분 환불", "R:" + kit.ids.nextId(), null)));
            }
        }
        // 워커가 남은 취소를 모두 끝낼 때까지(재시도 간격만큼 시계를 옮긴다)
        for (int round = 0; round < 10; round++) {
            kit.clock.advance(Duration.ofMinutes(721));
            kit.worker.runOnce();
        }

        for (PaymentTestKit.Created created : all) {
            OrderRow row = kit.order(created.paymentId());
            long succeeded = kit.cancelStore.succeededAmount(row.id());
            assertThat(row.canceledAmount()).as("취소 누계 = SUCCEEDED 합 " + row.paymentId()).isEqualTo(succeeded);
            if (row.anomaly() == null) {
                assertThat(row.paidAmount() - row.canceledAmount()).as("결제 − 취소 = 도메인 현금 " + row.paymentId()).isEqualTo(kit.handler.cashOf(created.orderRefId()));
            } else {
                assertThat(kit.handler.cashOf(created.orderRefId())).as("이상 결제는 도메인 확정 없음").isZero();
                assertThat(row.canceledAmount()).as("이상 결제는 전액 취소 " + row.paymentId()).isEqualTo(row.paidAmount());
            }
            assertThat(kit.gateway.getPayment(row.paymentId()).cancelledAmount()).as("포트원 쪽 취소 = 우리 누계(이중 취소 없음)").isEqualTo(row.canceledAmount());
        }
    }
}
