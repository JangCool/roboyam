import type { PaymentCompleteRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 결제 완료 API(공용) — 서버 주소 문자열(`/api/payments…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 `commerce-payment-contract.md` 결정 6.
 * 결제창이 끝나면 도메인 화면(충전·공구·시험 주문)이 부른다. 결제 시작 API 는 도메인마다 따로다.
 *  - `POST /api/payments/{paymentId}/complete` `{ orderType, orderRefId }` → `PaymentResult` (회원 로그인)
 *      · `409 PAYMENT_NOT_PAID`(포트원이 아직 결제 전) · `409 PAYMENT_ORDER_MISMATCH`(그 회원·그 주문의 결제가 아님 — 포트원 조회 없음) · `404 PAYMENT_ORDER_NOT_FOUND`
 *      · `503 PAYMENT_GATEWAY_UNAVAILABLE`(포트원 조회 실패 — 웹훅·READY 정리가 이어 받는다). 속도 제한·멱등 키 없음(같은 요청을 다시 보내도 결과가 같다).
 */
const PATH = {
    complete: (paymentId: string) => `/api/payments/${encodeURIComponent(paymentId)}/complete`,
};

export function usePaymentApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 결제창이 끝난 뒤 서버에 확정을 맡긴다 — 금액·상태는 서버가 포트원에 다시 물어 판단한다(브라우저 값을 믿지 않는다) */
        complete: (paymentId: string, body: PaymentCompleteRequest): Promise<PaymentResult> => client.post<PaymentResult>(PATH.complete(paymentId), body),
    };
}
