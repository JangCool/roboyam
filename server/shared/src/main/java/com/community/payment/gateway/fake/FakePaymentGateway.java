package com.community.payment.gateway.fake;

import com.community.payment.gateway.PaymentCheckoutInfo;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.PgCancelResult;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentPage;
import com.community.payment.gateway.WebhookVerification;
import com.community.payment.gateway.portone.StandardWebhookVerifier;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가짜 결제 게이트웨이 — <b>local·시험 전용</b>(dev·prod 프로필에서 고르면 기동을 막는다 — {@code PaymentConfig}). 결제 상태는 {@link FakePgStore}(DB)에 있고,
 * 화면의 가짜 결제창·시험이 「결제했다」를 쓴 뒤 완료 API·웹훅·READY 정리가 포트원 대신 여기를 조회한다. 취소는 포트원과 같은 잔액 검증을 한다.
 * 웹훅 서명은 공개된 고정 비밀키 {@link #WEBHOOK_SECRET} 로 한다(가짜 웹훅 흉내가 같은 키로 서명한다).
 */
public class FakePaymentGateway implements PaymentGateway {

    /** 가짜 상점 ID — 가짜 웹훅의 {@code data.storeId} */
    public static final String STORE_ID = "store-fake";

    /** 가짜 웹훅 비밀키(저장소에 공개된 값 — 가짜 구현에서만 쓴다) */
    public static final String WEBHOOK_SECRET = "whsec_cm9ib3lhbS1mYWtlLXdlYmhvb2stc2VjcmV0LWxvY2Fs";

    private final FakePgStore store;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public FakePaymentGateway(FakePgStore store, TransactionTemplate transaction, Clock clock) {
        this.store = store;
        this.transaction = transaction;
        this.clock = clock;
    }

    public FakePgStore store() {
        return store;
    }

    @Override
    public PaymentGatewayKind kind() {
        return PaymentGatewayKind.FAKE;
    }

    @Override
    public boolean ready() {
        return true;
    }

    @Override
    public PaymentCheckoutInfo checkoutInfo() {
        return new PaymentCheckoutInfo(PaymentGatewayKind.FAKE, null, null);
    }

    @Override
    public boolean ownsStore(String storeId) {
        return STORE_ID.equals(storeId);
    }

    @Override
    public PgPayment getPayment(String paymentId) {
        return store.find(paymentId, STORE_ID).orElseGet(() -> PgPayment.notFound(paymentId));
    }

    @Override
    public PgCancelResult cancel(String paymentId, long amount, long currentCancellableAmount, String reason) {
        String id = "fakecancel-" + UUID.randomUUID();
        FakePgStore.CancelOutcome outcome = transaction.execute(status -> store.cancel(paymentId, amount, currentCancellableAmount, reason, id, clock.instant()));
        if (outcome == null || outcome.errorType() != null) {
            return PgCancelResult.failed(null, outcome == null ? "?" : outcome.errorType());
        }
        return PgCancelResult.succeeded(outcome.cancellationId());
    }

    @Override
    public PgPaymentPage listPayments(Instant from, Instant until, int pageNumber, int pageSize) {
        List<PgPayment> items = store.list(from, until, pageNumber * pageSize, pageSize, STORE_ID);
        return new PgPaymentPage(items, pageNumber, pageSize, items.size());
    }

    @Override
    public WebhookVerification verifyWebhook(String webhookId, String webhookTimestamp, String webhookSignature, byte[] body, Instant now) {
        return StandardWebhookVerifier.verify(WEBHOOK_SECRET, webhookId, webhookTimestamp, webhookSignature, body, now);
    }
}
