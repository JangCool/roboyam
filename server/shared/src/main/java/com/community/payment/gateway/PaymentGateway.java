package com.community.payment.gateway;

import java.time.Instant;

/**
 * 결제 게이트웨이 — 포트원 V2 구현({@code PortOnePaymentGateway})과 가짜 구현({@code FakePaymentGateway}, local·시험 전용) 중 배포 설정이 하나를 고른다({@code PaymentConfig}).
 * 업무 규칙(확정·취소 누계·재시도)은 서비스가 지키고 여기는 「포트원에 묻고 결과를 옮기는 일」만 한다. <b>트랜잭션 밖에서 부른다</b>(외부 호출 — 취소만 결제 주문 행을 잠근 채 부른다, 확정 흐름 5).
 *
 * <p>포트원 연결 실패·5xx·응답 모양이 틀림은 {@link com.community.payment.exception.PaymentGatewayUnavailableException}(503), 설정이 비었으면
 * {@link com.community.payment.exception.PaymentNotConfiguredException}(503). 로그에 비밀키·응답 본문을 남기지 않는다.
 */
public interface PaymentGateway {

    PaymentGatewayKind kind();

    /** 결제에 필요한 설정이 모두 있는가(포트원: 상점 ID·API 비밀키·결제 채널 키·웹훅 비밀키 + 암호화 키) */
    boolean ready();

    /** 결제창 정보 — 설정이 비었으면 503 */
    PaymentCheckoutInfo checkoutInfo();

    /** 웹훅 본문의 {@code data.storeId} 가 우리 상점인가 */
    boolean ownsStore(String storeId);

    /** 결제 단건 조회 — 포트원에 없으면 {@link PgPayment#notFound}. 결과의 결제 ID·상점 ID 가 우리 것이 아니면 503(설정 오류로 본다) */
    PgPayment getPayment(String paymentId);

    /**
     * 결제 취소 — {@code currentCancellableAmount}(이 요청 전의 취소 가능 잔액)가 포트원 값과 같을 때만 취소된다(이중 취소 막기, commerce.md 8-3).
     * 포트원이 거절(4xx)하면 {@link PgCancelResult#failed}, 연결 실패·5xx 는 503 예외.
     */
    PgCancelResult cancel(String paymentId, long amount, long currentCancellableAmount, String reason);

    /** 결제 다건 조회 — 상태 업데이트 시점(STATUS_CHANGED_AT) 기준 [from, until] 의 {@code pageNumber} 쪽(0부터, 대사 2a-3) */
    PgPaymentPage listPayments(Instant from, Instant until, int pageNumber, int pageSize);

    /** 웹훅 서명 검증(Standard Webhooks) — 원본 바이트로 한다 */
    WebhookVerification verifyWebhook(String webhookId, String webhookTimestamp, String webhookSignature, byte[] body, Instant now);
}
