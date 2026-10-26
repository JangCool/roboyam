package com.community.payment;

import com.community.payment.gateway.fake.FakePaymentGateway;
import com.community.payment.gateway.portone.StandardWebhookVerifier;
import com.community.payment.service.PaymentWebhookService.WebhookRequest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/** 포트원 웹훅(2024-04-25 — {@code {type, timestamp, data:{storeId, paymentId, transactionId, cancellationId?}}}) 흉내 — 가짜 게이트웨이의 비밀키로 서명한다 */
final class TestWebhooks {

    private TestWebhooks() {
    }

    static String body(String type, String paymentId, String storeId, String cancellationId) {
        return "{\"type\":\"" + type + "\",\"timestamp\":\"2026-10-26T00:00:00Z\",\"data\":{\"storeId\":\"" + storeId + "\",\"paymentId\":\"" + paymentId
                + "\",\"transactionId\":\"tx-" + UUID.randomUUID() + "\"" + (cancellationId == null ? "" : ",\"cancellationId\":\"" + cancellationId + "\"") + "}}";
    }

    /** 서명한 요청(지금 시각, 새 webhook-id) */
    static WebhookRequest signed(String body, Instant now) {
        return signed("msg_" + UUID.randomUUID(), body, now);
    }

    static WebhookRequest signed(String webhookId, String body, Instant now) {
        byte[] raw = body.getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(now.getEpochSecond());
        return new WebhookRequest(webhookId, timestamp, StandardWebhookVerifier.signature(FakePaymentGateway.WEBHOOK_SECRET, webhookId, timestamp, raw), raw);
    }

    static WebhookRequest paid(String paymentId, Instant now) {
        return signed(body("Transaction.Paid", paymentId, FakePaymentGateway.STORE_ID, null), now);
    }
}
