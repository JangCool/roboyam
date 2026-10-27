/**
 * 결제 API 의 요청·응답 모양 — 공용 완료 API(`POST /api/payments/{paymentId}/complete`, 계약 결정 6)와 시험 주문(`/api/dev/payments`, 결정 7 — local·dev 만).
 * 2a-1 화면 목업이 제안하는 모양이고, 서버는 2a-2(완료 API·시험 주문)가 만든다. 금액은 원(KRW) 정수, 시각은 ISO 순간.
 */

export type PaymentStatus = 'READY' | 'PAID' | 'PARTIAL_CANCELED' | 'CANCELED' | 'EXPIRED';
export type PaymentAnomaly = 'AMOUNT_MISMATCH' | 'DOMAIN_REJECTED' | 'LATE_PAYMENT';
export type PaymentCancelStatus = 'PENDING' | 'SENDING' | 'SUCCEEDED' | 'GAVE_UP' | 'MANUAL';
/** 결제창 구현 — 서버 배포 설정 `roboyam.payment.gateway`. FAKE 면 가짜 결제창, PORTONE 이면 포트원 SDK(2a-4) */
export type PaymentGateway = 'FAKE' | 'PORTONE';

/** 결제창을 여는 데 필요한 값 — 도메인의 결제 시작 API 가 같이 준다(포트원이면 상점 ID·채널 키) */
export interface PaymentCheckout {
    gateway: PaymentGateway;
    paymentId: string;
    orderName: string;
    amount: number;
    currency: 'KRW';
    storeId: string | null;
    channelKey: string | null;
}

/** `POST /api/dev/payments` — 시험 주문 만들기 */
export interface PaymentTestOrderRequest {
    /** 100 ~ 1,000,000원 */
    amount: number;
    /** 결제 유효 시간 1 ~ 60분 */
    validMinutes: number;
}

export interface PaymentTestOrder {
    orderType: 'PAYMENT_TEST';
    orderRefId: string;
    expiresAt: string;
    checkout: PaymentCheckout;
}

/** 가짜 결제창에서 고른 결과 — `POST /api/dev/payments/{paymentId}/fake-checkout`(가짜 게이트웨이일 때만) */
export interface PaymentFakeCheckoutRequest {
    outcome: 'PAID' | 'FAILED';
    /** 결제할 금액 — 주문 금액과 다르면 금액 불일치 시험 */
    amount: number;
    /** true 면 결제 시각을 유효 시각 1분 뒤로(늦은 결제 시험) */
    late: boolean;
}

/** `POST /api/payments/{paymentId}/complete` 요청 — 그 회원·그 주문의 결제가 아니면 409 `PAYMENT_ORDER_MISMATCH` */
export interface PaymentCompleteRequest {
    orderType: string;
    orderRefId: string;
}

/** 완료 API·시험 주문 다시 읽기의 응답 */
export interface PaymentResult {
    paymentId: string;
    status: PaymentStatus;
    anomaly: PaymentAnomaly | null;
    /** 받아들인 결제인가(PAID 계열 + 이상 없음) — 도메인 화면은 이것만 보고 「완료」를 보인다 */
    accepted: boolean;
    amount: number;
    paidAmount: number | null;
    canceledAmount: number;
    paidAt: string | null;
    /** 받지 않은 결제의 자동 취소(있으면) */
    cancel: { amount: number; status: PaymentCancelStatus } | null;
}
