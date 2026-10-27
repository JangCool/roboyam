package com.community.payment.controller;

import com.community.payment.service.PaymentWebhookService;
import com.community.payment.service.PaymentWebhookService.WebhookOutcome;
import com.community.payment.service.PaymentWebhookService.WebhookRequest;
import com.community.zconfiguration.security.PaymentWebhookPaths;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 포트원 V2 웹훅 수신(docs/features/commerce-payment-contract.md 결정 5·「웹훅 처리 표」) — {@code POST /api/payments/webhooks/portone}. 로그인·CSRF 없이 받는다
 * ({@code SecurityConfig}), 서명은 <b>원본 바이트</b>로 검증하므로 본문을 객체로 바꾸지 않고 그대로 읽는다(한도 {@value PaymentWebhookService#MAX_BODY_BYTES}바이트 — 넘으면 400).
 * 응답은 상태 코드만(본문 없음): 200 처리·무시 · 400 거절(재전송해도 같다) · 503 다시 보내 달라(포트원은 실패하면 최대 5번 다시 보낸다) · 500 뜻밖의 오류.
 */
@RestController
public class PaymentWebhookController {

    private final PaymentWebhookService webhooks;

    public PaymentWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping(PaymentWebhookPaths.PORTONE)
    public ResponseEntity<Void> receive(HttpServletRequest request, @RequestHeader(name = "webhook-id", required = false) String webhookId,
                                        @RequestHeader(name = "webhook-timestamp", required = false) String webhookTimestamp,
                                        @RequestHeader(name = "webhook-signature", required = false) String webhookSignature) throws IOException {
        byte[] body = readLimited(request);
        WebhookOutcome outcome = body == null ? webhooks.rejectOversized()
                : webhooks.handle(new WebhookRequest(webhookId, webhookTimestamp, webhookSignature, body));
        return ResponseEntity.status(outcome.httpStatus()).build();
    }

    /** 본문을 한도까지 읽는다 — 넘으면 null(나머지는 읽지 않는다) */
    static byte[] readLimited(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > PaymentWebhookService.MAX_BODY_BYTES) {
            return null;
        }
        try (InputStream in = request.getInputStream()) {
            byte[] read = in.readNBytes(PaymentWebhookService.MAX_BODY_BYTES + 1);
            return read.length > PaymentWebhookService.MAX_BODY_BYTES ? null : read;
        }
    }
}
