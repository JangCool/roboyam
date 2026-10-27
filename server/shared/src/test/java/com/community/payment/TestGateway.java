package com.community.payment;

import com.community.payment.exception.PaymentGatewayUnavailableException;
import com.community.payment.exception.PaymentNotConfiguredException;
import com.community.payment.gateway.PaymentCheckoutInfo;
import com.community.payment.gateway.PaymentGateway;
import com.community.payment.gateway.PaymentGatewayKind;
import com.community.payment.gateway.PgCancelResult;
import com.community.payment.gateway.PgPayment;
import com.community.payment.gateway.PgPaymentPage;
import com.community.payment.gateway.WebhookVerification;
import com.community.payment.gateway.fake.FakePaymentGateway;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 결제 시험용 게이트웨이 — 가짜 PG({@link FakePaymentGateway}, DB 의 {@code fake_pg_payments})를 감싸 <b>포트원 장애·거절·접수만·「취소는 됐는데 응답이 끊김」</b>을 흉내 내고 호출을 센다.
 * 시험마다 {@link #reset} 한다.
 */
final class TestGateway implements PaymentGateway {

    /** 다음 취소 요청에 낼 결과 */
    enum CancelScript {
        /** 연결 실패(503) — 포트원은 아무것도 하지 않았다 */
        DOWN,
        /** 포트원이 거절(FAILED) */
        FAILED,
        /** 접수만(REQUESTED) — 포트원은 아직 취소하지 않았다 */
        REQUESTED,
        /** 포트원은 취소했는데 우리는 응답을 못 받았다(시간 초과) */
        DONE_BUT_TIMEOUT,
        /** 뜻밖의 예외(문구에 개인정보가 섞여 있다 — 가림 확인용) */
        BOOM
    }

    final FakePaymentGateway fake;
    final AtomicInteger getCalls = new AtomicInteger();
    final AtomicInteger cancelCalls = new AtomicInteger();
    final List<Long> cancelCurrentCancellable = new CopyOnWriteArrayList<>();
    final List<String> cancelReasons = new CopyOnWriteArrayList<>();
    private final Deque<CancelScript> cancelScript = new ArrayDeque<>();
    volatile boolean getDown;
    volatile boolean notConfigured;
    /** 다건 조회만 실패(대사 장애) */
    volatile boolean listDown;
    /** 조회만 실패하는 결제 ID(건별 장애) */
    final java.util.Set<String> downFor = java.util.concurrent.ConcurrentHashMap.newKeySet();

    TestGateway(FakePaymentGateway fake) {
        this.fake = fake;
    }

    synchronized void reset() {
        getCalls.set(0);
        cancelCalls.set(0);
        cancelCurrentCancellable.clear();
        cancelReasons.clear();
        cancelScript.clear();
        getDown = false;
        notConfigured = false;
        listDown = false;
        downFor.clear();
    }

    synchronized void script(CancelScript... next) {
        cancelScript.addAll(List.of(next));
    }

    private synchronized CancelScript nextScript() {
        return cancelScript.pollFirst();
    }

    @Override
    public PaymentGatewayKind kind() {
        return PaymentGatewayKind.FAKE;
    }

    @Override
    public boolean ready() {
        return !notConfigured;
    }

    @Override
    public PaymentCheckoutInfo checkoutInfo() {
        if (notConfigured) {
            throw new PaymentNotConfiguredException();
        }
        return fake.checkoutInfo();
    }

    @Override
    public boolean ownsStore(String storeId) {
        return fake.ownsStore(storeId);
    }

    @Override
    public PgPayment getPayment(String paymentId) {
        getCalls.incrementAndGet();
        if (getDown || downFor.contains(paymentId)) {
            throw new PaymentGatewayUnavailableException();
        }
        return fake.getPayment(paymentId);
    }

    @Override
    public PgCancelResult cancel(String paymentId, long amount, long currentCancellableAmount, String reason) {
        cancelCalls.incrementAndGet();
        cancelCurrentCancellable.add(currentCancellableAmount);
        cancelReasons.add(reason);
        CancelScript script = nextScript();
        if (script == null) {
            return fake.cancel(paymentId, amount, currentCancellableAmount, reason);
        }
        return switch (script) {
            case DOWN -> throw new PaymentGatewayUnavailableException();
            case FAILED -> PgCancelResult.failed(null, "PG_PROVIDER");
            case REQUESTED -> PgCancelResult.requested("req-" + UUID.randomUUID());
            case DONE_BUT_TIMEOUT -> {
                fake.cancel(paymentId, amount, currentCancellableAmount, reason);
                throw new PaymentGatewayUnavailableException();
            }
            case BOOM -> throw new IllegalStateException("socket closed while talking about kim@example.com");
        };
    }

    @Override
    public PgPaymentPage listPayments(Instant from, Instant until, int pageNumber, int pageSize) {
        if (listDown || getDown) {
            throw new PaymentGatewayUnavailableException();
        }
        return fake.listPayments(from, until, pageNumber, pageSize);
    }

    @Override
    public WebhookVerification verifyWebhook(String webhookId, String webhookTimestamp, String webhookSignature, byte[] body, Instant now) {
        if (notConfigured) {
            return WebhookVerification.NOT_CONFIGURED;
        }
        return fake.verifyWebhook(webhookId, webhookTimestamp, webhookSignature, body, now);
    }
}
