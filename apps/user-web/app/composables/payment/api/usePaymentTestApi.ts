import type { PaymentFakeCheckoutRequest, PaymentResult, PaymentTestOrder, PaymentTestOrderRequest } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 시험 주문 API(`PAYMENT_TEST`) — 서버 주소 문자열(`/api/dev/payments…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 결정 7·8.
 * 서버가 `roboyam.payment.test-orders` 를 켠 환경(local·dev)에서만 있다 — 꺼져 있으면 404. 화면은 `/dev/payment`(개발 모드) 하나다.
 *  - `POST /api/dev/payments` `{ amount(100~1,000,000), validMinutes(1~60) }` → `PaymentTestOrder`(결제창 정보 포함) · `400 VALIDATION fields.amount|validMinutes` · `503 PAYMENT_NOT_CONFIGURED`
 *  - `POST /api/dev/payments/{paymentId}/fake-checkout` `{ outcome, amount, late }` → 204 — 가짜 PG(`fake_pg_payments`)에 결제 결과를 남긴다.
 *      가짜 게이트웨이가 아니면 `409 PAYMENT_GATEWAY_NOT_FAKE`, 이미 남겼으면 `409 FAKE_PG_ALREADY_DONE`
 *  - `GET  /api/dev/payments/{paymentId}` → `PaymentResult`(자동 취소가 끝났는지 다시 볼 때 — 서버는 `handlerCash`·`cancels[]` 도 주지만 화면은 쓰지 않는다)
 */
const PATH = {
    orders: '/api/dev/payments',
    order: (paymentId: string) => `/api/dev/payments/${encodeURIComponent(paymentId)}`,
    fakeCheckout: (paymentId: string) => `/api/dev/payments/${encodeURIComponent(paymentId)}/fake-checkout`,
};

export function usePaymentTestApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        create: (body: PaymentTestOrderRequest): Promise<PaymentTestOrder> => client.post<PaymentTestOrder>(PATH.orders, body),

        /** 가짜 결제창의 결과를 가짜 PG 에 남긴다 — 포트원 결제창이 포트원에 결제를 남기는 것과 같은 자리 */
        fakeCheckout: (paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> => client.post<void>(PATH.fakeCheckout(paymentId), body),

        result: (paymentId: string): Promise<PaymentResult> => client.get<PaymentResult>(PATH.order(paymentId)),
    };
}
