import type { PaymentCheckout, PaymentCompleteRequest, PaymentFakeCheckoutRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 결제창 공용 컴포저블(`usePaymentCheckout`)의 타입 — 결제창 하나를 열고 끝까지 가는 데 필요한 값만.
 * 도메인 주문(시험 주문 `PAYMENT_TEST` · 공구 `GROUP_BUY` · 반품비 부족분 `GROUP_BUY_RETURN_FEE` …)은 이 모양을 넓혀 쓴다.
 */

/** 결제창을 열 대상 — 도메인의 결제 시작 API 가 준다 */
export interface PaymentCheckoutTarget {
    /** 결제 모듈의 주문 종류(완료 요청에 그대로 싣는다) */
    orderType: string;
    /** 그 도메인 주문의 번호(완료 요청에 그대로 싣는다) */
    orderRefId: string;
    /** 결제 유효 시각 */
    expiresAt: string;
    checkout: PaymentCheckout;
}

/** 완료 요청을 보내지 않고 끝난 까닭 — 가짜 결제창 실패(같은 결제를 다시 열 수 없음) · 창 닫기 · 포트원 실패(같은 결제 번호로 다시 열 수 있음) */
export type PaymentCheckoutNote = 'FAILED' | 'CLOSED' | 'PG_FAILED';

/** 포트원 SDK 에 넘기는 구매자 — KG이니시스는 이름·전화·이메일을 요구한다 */
export interface PaymentCheckoutCustomer {
    fullName: string;
    phoneNumber: string;
    email: string;
}

export interface PaymentCheckoutOptions<T extends PaymentCheckoutTarget> {
    /** 모바일 리디렉션 동안 대상을 둘 sessionStorage 키 — 화면(또는 업무)마다 다르게 */
    pendingKey: string;
    /** 모바일 리디렉션에서 돌아올 화면 경로(쿼리 없이) */
    redirectPath: (target: T) => string;
    /** 포트원 구매자 */
    customer: (target: T) => PaymentCheckoutCustomer;
    /** 가짜 결제창에서 고른 결과를 가짜 PG 에 남긴다 */
    fakeCheckout: (paymentId: string, body: PaymentFakeCheckoutRequest) => Promise<void>;
    /** 완료 요청 — 안 주면 공용 `usePaymentApi().complete` */
    complete?: (paymentId: string, body: PaymentCompleteRequest) => Promise<PaymentResult>;
    /** 서버가 포트원 상점 ID·채널 키를 주지 않았을 때의 한 줄 — 안 주면 `payment.portoneMissing` */
    missingConfigMessage?: () => string;
    /** 완료 요청이 결과를 돌려준 뒤(받아들였든 아니든) */
    onResult?: (result: PaymentResult, target: T) => void;
}
