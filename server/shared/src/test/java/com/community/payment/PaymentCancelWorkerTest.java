package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.community.notification.entity.AdminNotificationEvent;
import com.community.payment.TestGateway.CancelScript;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.dto.PgCancelStatus;
import com.community.payment.exception.PaymentCancelExceedsException;
import com.community.payment.repository.master.PaymentCancelStore.CancelRow;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 취소 대기열·워커(commerce.md 8-3·D10·18절 P17, 계약 「확정 흐름」 4·5·결정 2·18) — 실제 PostgreSQL, 가짜 PG(장애·거절·접수만·응답 끊김을 흉내).
 * 워커는 시계보다 이른 「차례가 된 줄」만 집는다 — 시험마다 시계를 앞 시험보다 이른 과거로 옮겨 다른 시험의 줄을 집지 않는다.
 */
class PaymentCancelWorkerTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    /** 받아들인 결제 하나(PAID) */
    private static PaymentTestKit.Created paid(long amount) {
        PaymentTestKit.Created created = kit.create(kit.newMember(), amount, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), amount);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);
        return created;
    }

    /** 취소를 넣는다(업무 트랜잭션 안 — 커밋 뒤 즉시 시도가 돈다) */
    private static long request(PaymentTestKit.Created created, long amount, String key) {
        return kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), amount, CancelKind.REFUND, "시험 환불", key, null)));
    }

    private static CancelRow cancel(long id) {
        return kit.cancelStore.findById(id).orElseThrow();
    }

    /** 다음 시도 시각까지 시계를 옮기고 워커 한 바퀴 */
    private static int runWorkerAt(CancelRow row) {
        kit.clock.set(row.nextAttemptAt());
        return kit.worker.runOnce();
    }

    @Nested
    class 넣기 {

        @Test
        void 업무_트랜잭션_밖에서는_넣을_수_없다_MANDATORY() {
            PaymentTestKit.Created created = paid(10_000);
            assertThatThrownBy(() -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 1_000, CancelKind.REFUND, "x", "R:" + kit.ids.nextId(), null)))
                    .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
        }

        @Test
        void 남은_취소_가능_금액을_넘는_취소는_거절하고_진행_중인_취소도_센다() {
            PaymentTestKit.Created created = paid(10_000);
            kit.gateway.script(CancelScript.DOWN); // 첫 취소는 진행 중(PENDING)으로 남는다
            request(created, 6_000, "R:" + kit.ids.nextId());
            assertThatThrownBy(() -> request(created, 4_001, "R:" + kit.ids.nextId())).isInstanceOf(PaymentCancelExceedsException.class)
                    .satisfies(e -> assertThat(((PaymentCancelExceedsException) e).getStatus().value()).isEqualTo(409));
            long second = request(created, 4_000, "R:" + kit.ids.nextId());
            assertThat(cancel(second).status()).isEqualTo(CancelStatus.SUCCEEDED);
            // 결제되지 않은 주문
            PaymentTestKit.Created ready = kit.create(kit.newMember(), 1_000, Decision.ACCEPT);
            assertThatThrownBy(() -> request(ready, 1, "R:" + kit.ids.nextId())).isInstanceOf(PaymentCancelExceedsException.class);
        }

        @Test
        void 같은_요청_키는_한_번만_들어가고_처음_줄을_돌려준다() {
            PaymentTestKit.Created created = paid(10_000);
            String key = "R:" + kit.ids.nextId();
            long first = request(created, 1_000, key);
            long again = request(created, 1_000, key);
            assertThat(again).isEqualTo(first);
            assertThat(kit.cancelsOf(created.orderId())).hasSize(1);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(1_000);
        }

        @Test
        void 업무가_되돌려지면_취소도_없고_즉시_시도도_하지_않는다() {
            PaymentTestKit.Created created = paid(10_000);
            String key = "R:" + kit.ids.nextId();
            assertThatThrownBy(() -> kit.tx.executeWithoutResult(status -> {
                kit.cancels.request(new PaymentCancelOrder(created.orderId(), 1_000, CancelKind.REFUND, "x", key, null));
                throw new IllegalStateException("업무 실패");
            })).isInstanceOf(IllegalStateException.class);
            assertThat(kit.cancelStore.findByRequestKey(key)).isEmpty();
            assertThat(kit.gateway.cancelCalls.get()).isZero();
        }
    }

    @Nested
    class 실행 {

        @Test
        void 커밋_뒤_즉시_시도가_currentCancellableAmount_로_보내고_누계를_늘린다_부분_뒤_전액() {
            PaymentTestKit.Created created = paid(10_000);
            long first = request(created, 3_000, "R:" + kit.ids.nextId());
            assertThat(kit.gateway.cancelCurrentCancellable).containsExactly(10_000L);
            assertThat(cancel(first).status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(cancel(first).pgStatus()).isEqualTo(PgCancelStatus.SUCCEEDED);
            assertThat(cancel(first).pgCancelId()).startsWith("fakecancel-");
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(3_000);
            assertThat(kit.handler.cashOf(created.orderRefId())).isEqualTo(7_000);

            long second = request(created, 7_000, "R:" + kit.ids.nextId());
            assertThat(kit.gateway.cancelCurrentCancellable).containsExactly(10_000L, 7_000L);
            assertThat(cancel(second).status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(10_000);
            assertThat(kit.handler.cashOf(created.orderRefId())).isZero();
            assertThat(kit.handler.canceledCallsOf(first)).isEqualTo(1);
            assertThat(kit.handler.canceledCallsOf(second)).isEqualTo(1);
            assertThat(kit.gateway.cancelReasons).allSatisfy(reason -> assertThat(reason).startsWith("roboyam REFUND rc:"));
        }

        @Test
        void 취소_누계는_결제_금액을_넘지_않는다_조건부_증가() {
            PaymentTestKit.Created created = paid(5_000);
            assertThat(kit.orderStore.addCanceled(created.orderId(), 5_001, kit.now())).isFalse();
            assertThat(kit.orderStore.addCanceled(created.orderId(), 5_000, kit.now())).isTrue();
            assertThat(kit.orderStore.addCanceled(created.orderId(), 1, kit.now())).as("이미 전액").isFalse();
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(5_000);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
        }

        @Test
        void 실패하면_간격_1_5_30_120_720분으로_다시_하고_재시도를_다_쓰면_GAVE_UP_결제_이상_알림_P17() {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 20_000, Decision.REJECT);
            kit.pgPaid(created.paymentId(), 20_000);
            kit.gateway.script(CancelScript.FAILED, CancelScript.DOWN, CancelScript.FAILED, CancelScript.DOWN, CancelScript.FAILED, CancelScript.FAILED);

            kit.confirm.confirm(created.paymentId(), ConfirmedVia.WEBHOOK); // 도메인 거절 → 자동 취소 → 즉시 시도 실패(1회)
            CancelRow row = kit.cancelsOf(created.orderId()).get(0);
            List<Duration> gaps = new ArrayList<>();
            assertThat(row.status()).isEqualTo(CancelStatus.PENDING);
            assertThat(row.attempts()).isEqualTo(1);
            for (int retry = 1; retry <= 5; retry++) {
                CancelRow before = cancel(row.id());
                gaps.add(Duration.between(kit.now(), before.nextAttemptAt()));
                kit.clock.set(before.nextAttemptAt().minusSeconds(1));
                assertThat(kit.worker.runOnce()).as("차례 전에는 집지 않는다").isZero();
                assertThat(runWorkerAt(before)).isEqualTo(1);
            }
            assertThat(gaps).containsExactly(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofMinutes(120), Duration.ofMinutes(720));
            CancelRow last = cancel(row.id());
            assertThat(last.status()).isEqualTo(CancelStatus.GAVE_UP);
            assertThat(last.attempts()).as("즉시 1 + 재시도 5").isEqualTo(6);
            assertThat(last.lastError()).isNotBlank();
            assertThat(kit.gateway.cancelCalls.get()).isEqualTo(6);
            assertThat(kit.notices).containsExactly(AdminNotificationEvent.COMMERCE_PAYMENT_ANOMALY);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isZero();
            assertThat(kit.handler.cashOf(created.orderRefId())).as("도메인 확정 없음").isZero();
            assertThat(kit.events(created.orderId(), "CANCEL_GAVE_UP")).isEqualTo(1);
            assertThat(kit.count("SELECT count(*) FROM payment_cancels WHERE id = ? AND status = 'GAVE_UP'", row.id())).isEqualTo(1);
            // 멈춘 줄은 다시 집지 않는다
            kit.clock.advance(Duration.ofDays(1));
            assertThat(kit.worker.runOnce()).isZero();
        }

        @Test
        void 재시도_전에는_먼저_조회해_이미_취소됐으면_보내지_않고_성공으로_맞춘다() {
            PaymentTestKit.Created created = paid(10_000);
            kit.gateway.script(CancelScript.DONE_BUT_TIMEOUT); // 포트원은 취소했는데 응답이 끊겼다
            long id = request(created, 4_000, "R:" + kit.ids.nextId());
            assertThat(cancel(id).status()).isEqualTo(CancelStatus.PENDING);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isZero();

            runWorkerAt(cancel(id));

            assertThat(kit.gateway.cancelCalls.get()).as("두 번 보내지 않았다").isEqualTo(1);
            assertThat(cancel(id).status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(cancel(id).pgCancelId()).startsWith("fakecancel-");
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(4_000);
            assertThat(kit.gateway.getPayment(created.paymentId()).cancelledAmount()).as("포트원 쪽도 한 번").isEqualTo(4_000);
            assertThat(kit.handler.canceledCallsOf(id)).isEqualTo(1);
        }

        @Test
        void 접수만_REQUESTED_은_끝난_것이_아니라_다시_조회해_확인하고_접수_중이면_다시_보내지_않는다() {
            PaymentTestKit.Created created = paid(10_000);
            kit.gateway.script(CancelScript.REQUESTED);
            long id = request(created, 2_000, "R:" + kit.ids.nextId());
            CancelRow row = cancel(id);
            assertThat(row.status()).isEqualTo(CancelStatus.PENDING);
            assertThat(row.pgStatus()).isEqualTo(PgCancelStatus.REQUESTED);
            assertThat(row.pgCancelId()).startsWith("req-");
            // 포트원 목록에 그 접수 건이 없다(가짜 PG 는 접수만을 기억하지 않는다) → 다시 보내 끝낸다
            runWorkerAt(row);
            assertThat(cancel(id).status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(2_000);
        }

        @Test
        void 죽은_워커의_오래된_SENDING_은_되살려_다시_조회부터_한다() {
            PaymentTestKit.Created created = paid(10_000);
            kit.gateway.script(CancelScript.DOWN);
            long id = request(created, 1_000, "R:" + kit.ids.nextId());
            // 다른 서버가 집고 죽었다
            kit.jdbc.update("UPDATE payment_cancels SET status = 'SENDING', locked_by = 999, locked_at = ?, attempts = attempts + 1 WHERE id = ?",
                    Timestamp.from(kit.now()), id);
            kit.clock.advance(Duration.ofMinutes(9));
            assertThat(kit.worker.runOnce()).as("10분 전에는 그대로").isZero();
            assertThat(cancel(id).status()).isEqualTo(CancelStatus.SENDING);
            kit.clock.advance(Duration.ofMinutes(2));
            assertThat(kit.worker.runOnce()).isEqualTo(1);
            assertThat(cancel(id).status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(1_000);
        }

        @Test
        void 한_바퀴에_20건까지_집고_건_사이에_간격을_둔다() {
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < 23; i++) {
                PaymentTestKit.Created created = paid(1_000);
                kit.gateway.script(CancelScript.DOWN);
                ids.add(request(created, 1_000, "R:" + kit.ids.nextId()));
            }
            kit.clock.advance(Duration.ofMinutes(1));
            kit.pauses.clear();
            assertThat(kit.worker.runOnce()).isEqualTo(20);
            assertThat(kit.pauses).hasSize(19).allSatisfy(gap -> assertThat(gap).isEqualTo(Duration.ofMillis(200)));
            assertThat(kit.worker.runOnce()).isEqualTo(3);
            assertThat(ids).allSatisfy(id -> assertThat(cancel(id).status()).isEqualTo(CancelStatus.SUCCEEDED));
        }

        @Test
        void 즉시_시도와_워커가_부딪쳐도_한쪽만_집는다() {
            PaymentTestKit.Created created = paid(10_000);
            kit.gateway.script(CancelScript.DOWN);
            long id = request(created, 1_000, "R:" + kit.ids.nextId());
            kit.clock.set(cancel(id).nextAttemptAt());
            assertThat(kit.cancelStore.claimOne(id, 777, kit.now())).as("다른 서버가 먼저 집음").isPresent();
            assertThat(kit.executor.attemptNow(id)).isFalse();
            assertThat(kit.worker.runOnce()).isZero();
            assertThat(kit.gateway.cancelCalls.get()).isEqualTo(1);
        }
    }
}
