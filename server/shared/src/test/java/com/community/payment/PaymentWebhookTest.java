package com.community.payment;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.payment.TestGateway.CancelScript;
import com.community.payment.dto.CancelKind;
import com.community.payment.dto.CancelStatus;
import com.community.payment.dto.ConfirmedVia;
import com.community.payment.dto.PaymentCancelOrder;
import com.community.payment.dto.PaymentStatus;
import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.repository.master.PaymentWebhookLogStore.Result;
import com.community.payment.service.PaymentWebhookService.WebhookOutcome;
import com.community.payment.service.PaymentWebhookService.WebhookRequest;
import com.community.payment.testorder.PaymentTestOrderHandler.Decision;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 포트원 웹훅(계약 「웹훅 처리 표」, commerce.md 8-4·18절 P18·P23) — 실제 PostgreSQL, 가짜 PG 와 그 비밀키로 서명한 웹훅.
 */
class PaymentWebhookTest {

    private static PaymentTestKit kit;

    @BeforeAll
    static void setUp() {
        kit = PaymentTestKit.get();
    }

    @BeforeEach
    void epoch() {
        kit.newEpoch();
    }

    private static WebhookOutcome send(WebhookRequest request) {
        return kit.webhooks.handle(request);
    }

    private static Map<String, Object> lastLog(String paymentId) {
        return kit.jdbc.queryForMap("SELECT * FROM payment_webhook_logs WHERE payment_id = ? ORDER BY id DESC LIMIT 1", paymentId);
    }

    private static PaymentTestKit.Created paidAndConfirmed(long amount) {
        PaymentTestKit.Created created = kit.create(kit.newMember(), amount, Decision.ACCEPT);
        kit.pgPaid(created.paymentId(), amount);
        kit.confirm.confirm(created.paymentId(), ConfirmedVia.COMPLETE_API);
        return created;
    }

    private static WebhookRequest cancelled(String paymentId, String cancellationId) {
        return TestWebhooks.signed(TestWebhooks.body("Transaction.Cancelled", paymentId, FakePaymentGateway.STORE_ID, cancellationId), kit.now());
    }

    @Nested
    class 서명_P23 {

        @Test
        void 서명이_틀리거나_머리글이_없거나_시각이_5분_넘게_다르면_400_이고_조회도_변경도_없다() {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
            kit.pgPaid(created.paymentId(), 10_000);
            WebhookRequest good = TestWebhooks.paid(created.paymentId(), kit.now());
            List<WebhookRequest> bad = List.of(
                    new WebhookRequest(good.webhookId(), good.webhookTimestamp(), "v1,AAAA" + good.webhookSignature().substring(7), good.body()),
                    new WebhookRequest(good.webhookId(), good.webhookTimestamp(), good.webhookSignature(),
                            new String(good.body(), StandardCharsets.UTF_8).replace("Transaction.Paid", "Transaction.Paid ").getBytes(StandardCharsets.UTF_8)),
                    new WebhookRequest(null, good.webhookTimestamp(), good.webhookSignature(), good.body()),
                    new WebhookRequest(good.webhookId(), null, good.webhookSignature(), good.body()),
                    new WebhookRequest(good.webhookId(), good.webhookTimestamp(), null, good.body()),
                    TestWebhooks.paid(created.paymentId(), kit.now().minus(Duration.ofMinutes(5)).minusSeconds(1)),
                    TestWebhooks.paid(created.paymentId(), kit.now().plus(Duration.ofMinutes(5)).plusSeconds(1)));
            int before = kit.count("SELECT count(*) FROM payment_webhook_logs WHERE result = 'REJECTED' AND received_at = ?", java.sql.Timestamp.from(kit.now()));
            for (WebhookRequest request : bad) {
                WebhookOutcome outcome = send(request);
                assertThat(outcome.httpStatus()).isEqualTo(400);
                assertThat(outcome.result()).isEqualTo(Result.REJECTED);
            }
            assertThat(kit.gateway.getCalls.get()).as("포트원 조회 없음").isZero();
            assertThat(kit.order(created.paymentId()).status()).as("상태 불변").isEqualTo(PaymentStatus.READY);
            assertThat(kit.count("SELECT count(*) FROM payment_webhook_logs WHERE result = 'REJECTED' AND received_at = ?", java.sql.Timestamp.from(kit.now())))
                    .isEqualTo(before + bad.size());
            assertThat(kit.count("SELECT count(*) FROM payment_webhook_logs WHERE result = 'REJECTED' AND received_at = ? AND error_code IN "
                    + "('BAD_SIGNATURE', 'MISSING_HEADERS', 'TIMESTAMP_SKEW')", java.sql.Timestamp.from(kit.now()))).isEqualTo(before + bad.size());
            // 5분 안이면 받는다
            assertThat(send(TestWebhooks.paid(created.paymentId(), kit.now().minus(Duration.ofMinutes(5)))).httpStatus()).isEqualTo(200);
        }

        @Test
        void 웹훅_비밀키가_없으면_검증할_수_없어_503_RETRY() {
            kit.gateway.notConfigured = true;
            WebhookOutcome outcome = send(TestWebhooks.paid("pay_" + "w".repeat(32), kit.now()));
            assertThat(outcome.httpStatus()).isEqualTo(503);
            assertThat(outcome.result()).isEqualTo(Result.RETRY);
            assertThat(kit.gateway.getCalls.get()).isZero();
        }
    }

    @Nested
    class 결제 {

        @Test
        void 결제_웹훅은_본문을_믿지_않고_조회해_확정한다() {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
            kit.pgPaid(created.paymentId(), 10_000);
            WebhookOutcome outcome = send(TestWebhooks.paid(created.paymentId(), kit.now()));
            assertThat(outcome.httpStatus()).isEqualTo(200);
            assertThat(outcome.result()).isEqualTo(Result.PROCESSED);
            assertThat(kit.gateway.getCalls.get()).isEqualTo(1);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
            assertThat(kit.order(created.paymentId()).confirmedVia()).isEqualTo(ConfirmedVia.WEBHOOK);
            Map<String, Object> log = lastLog(created.paymentId());
            assertThat(log.get("result")).isEqualTo("PROCESSED");
            assertThat(log.get("event_type")).isEqualTo("Transaction.Paid");
            assertThat(log).doesNotContainKey("body");
        }

        @Test
        void 같은_webhook_id_는_한_번만_처리하고_이력_줄도_하나() {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
            kit.pgPaid(created.paymentId(), 10_000);
            String id = "msg_" + UUID.randomUUID();
            String body = TestWebhooks.body("Transaction.Paid", created.paymentId(), FakePaymentGateway.STORE_ID, null);
            assertThat(send(TestWebhooks.signed(id, body, kit.now())).httpStatus()).isEqualTo(200);
            WebhookOutcome again = send(TestWebhooks.signed(id, body, kit.now()));
            assertThat(again.httpStatus()).isEqualTo(200);
            assertThat(again.result()).isEqualTo(Result.PROCESSED);
            assertThat(again.errorCode()).isEqualTo("DUPLICATE");
            assertThat(kit.gateway.getCalls.get()).as("두 번째는 조회하지 않는다").isEqualTo(1);
            assertThat(kit.count("SELECT count(*) FROM payment_events WHERE provider_event_id = ?", id)).isEqualTo(1);
            assertThat(kit.handler.cashOf(created.orderRefId())).isEqualTo(10_000);
        }

        @Test
        void 아직_결제_전이거나_포트원_장애면_503_RETRY_이고_다시_보내면_처리한다() {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
            String id = "msg_" + UUID.randomUUID();
            String body = TestWebhooks.body("Transaction.Paid", created.paymentId(), FakePaymentGateway.STORE_ID, null);

            WebhookOutcome notPaid = send(TestWebhooks.signed(id, body, kit.now())); // 본문은 Paid 라지만 포트원은 아직 아니다
            assertThat(notPaid.httpStatus()).isEqualTo(503);
            assertThat(notPaid.result()).isEqualTo(Result.RETRY);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.READY);

            kit.pgPaid(created.paymentId(), 10_000);
            kit.gateway.getDown = true;
            WebhookOutcome down = send(TestWebhooks.signed(id, body, kit.now()));
            assertThat(down.httpStatus()).isEqualTo(503);
            assertThat(down.errorCode()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");

            kit.gateway.getDown = false;
            assertThat(send(TestWebhooks.signed(id, body, kit.now())).httpStatus()).as("포트원 재전송").isEqualTo(200);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.PAID);
        }

        @Test
        void 우리_결제가_아니거나_모르는_이벤트_읽을_수_없는_본문은_200_IGNORED() {
            PaymentTestKit.Created created = kit.create(kit.newMember(), 10_000, Decision.ACCEPT);
            kit.pgPaid(created.paymentId(), 10_000);
            Map<String, WebhookRequest> cases = Map.of(
                    "FOREIGN_STORE", TestWebhooks.signed(TestWebhooks.body("Transaction.Paid", created.paymentId(), "store-other", null), kit.now()),
                    "UNKNOWN_PAYMENT", TestWebhooks.paid("pay_" + "Z".repeat(32), kit.now()),
                    "UNKNOWN_EVENT", TestWebhooks.signed(TestWebhooks.body("Transaction.Ready", created.paymentId(), FakePaymentGateway.STORE_ID, null), kit.now()),
                    "UNREADABLE", TestWebhooks.signed("not json at all", kit.now()));
            cases.forEach((code, request) -> {
                WebhookOutcome outcome = send(request);
                assertThat(outcome.httpStatus()).as(code).isEqualTo(200);
                assertThat(outcome.result()).as(code).isEqualTo(Result.IGNORED);
                assertThat(outcome.errorCode()).as(code).isEqualTo(code);
            });
            assertThat(kit.gateway.getCalls.get()).isZero();
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.READY);
        }
    }

    @Nested
    class 취소 {

        @Test
        void 콘솔에서_직접_취소하면_상태는_그대로이고_대사_이슈를_남긴다_P18() {
            PaymentTestKit.Created created = paidAndConfirmed(10_000);
            String pgCancel = kit.fakePg.externalCancel(created.paymentId(), 10_000, "console-" + UUID.randomUUID(), kit.now());

            WebhookOutcome outcome = send(cancelled(created.paymentId(), pgCancel));

            assertThat(outcome.httpStatus()).isEqualTo(200);
            assertThat(outcome.result()).isEqualTo(Result.PROCESSED);
            assertThat(kit.order(created.paymentId()).status()).as("상태 불변").isEqualTo(PaymentStatus.PAID);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isZero();
            assertThat(kit.handler.cashOf(created.orderRefId())).as("도메인도 그대로").isEqualTo(10_000);
            assertThat(kit.count("SELECT count(*) FROM reconciliation_issues WHERE payment_id = ? AND type = 'CANCEL_MISMATCH' AND status = 'OPEN'",
                    created.paymentId())).isEqualTo(1);
            assertThat(kit.events(created.orderId(), "EXTERNAL_CANCEL")).isEqualTo(1);

            send(cancelled(created.paymentId(), pgCancel)); // 같은 취소가 다음 웹훅에도 실려 온다
            assertThat(kit.count("SELECT count(*) FROM reconciliation_issues WHERE payment_id = ? AND status = 'OPEN'", created.paymentId())).isEqualTo(1);
            assertThat(kit.events(created.orderId(), "EXTERNAL_CANCEL")).isEqualTo(1);
        }

        @Test
        void 우리_취소의_웹훅은_그_줄을_SUCCEEDED_로_맞춘다() {
            PaymentTestKit.Created created = paidAndConfirmed(10_000);
            kit.gateway.script(CancelScript.DONE_BUT_TIMEOUT); // 포트원은 취소했지만 우리는 응답을 못 받았다(취소 내역 아이디 모름)
            long cancelId = kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 3_000, CancelKind.REFUND, "시험", "R:" + kit.ids.nextId(),
                    null)));
            assertThat(kit.cancelStore.findById(cancelId).orElseThrow().status()).isEqualTo(CancelStatus.PENDING);

            String pgCancel = kit.gateway.getPayment(created.paymentId()).cancellations().get(0).id();
            assertThat(send(cancelled(created.paymentId(), pgCancel)).httpStatus()).isEqualTo(200);

            assertThat(kit.cancelStore.findById(cancelId).orElseThrow().status()).isEqualTo(CancelStatus.SUCCEEDED);
            assertThat(kit.cancelStore.findById(cancelId).orElseThrow().pgCancelId()).isEqualTo(pgCancel);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(3_000);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
            assertThat(kit.handler.canceledCallsOf(cancelId)).isEqualTo(1);
            assertThat(kit.count("SELECT count(*) FROM reconciliation_issues WHERE payment_id = ?", created.paymentId())).as("우리 취소는 이슈가 아니다").isZero();

            // 이미 끝낸 취소의 웹훅이 또 와도 두 번 반영하지 않는다
            send(cancelled(created.paymentId(), pgCancel));
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(3_000);
            assertThat(kit.handler.canceledCallsOf(cancelId)).isEqualTo(1);
        }

        @Test
        void 즉시_시도로_끝난_취소의_웹훅은_아무것도_바꾸지_않는다() {
            PaymentTestKit.Created created = paidAndConfirmed(10_000);
            long cancelId = kit.tx.execute(status -> kit.cancels.request(new PaymentCancelOrder(created.orderId(), 10_000, CancelKind.REFUND, "시험", "R:" + kit.ids.nextId(),
                    null)));
            String pgCancel = kit.cancelStore.findById(cancelId).orElseThrow().pgCancelId();
            assertThat(send(cancelled(created.paymentId(), pgCancel)).httpStatus()).isEqualTo(200);
            assertThat(kit.order(created.paymentId()).status()).isEqualTo(PaymentStatus.CANCELED);
            assertThat(kit.order(created.paymentId()).canceledAmount()).isEqualTo(10_000);
            assertThat(kit.handler.canceledCallsOf(cancelId)).isEqualTo(1);
            assertThat(kit.count("SELECT count(*) FROM reconciliation_issues WHERE payment_id = ?", created.paymentId())).isZero();
        }
    }
}
