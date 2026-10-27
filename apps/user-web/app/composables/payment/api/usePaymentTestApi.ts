import { mockCreateTestOrder, mockFakeCheckout, mockTestOrderResult } from '~/composables/payment/api/mock/payment-mock';
import type { PaymentFakeCheckoutRequest, PaymentResult, PaymentTestOrder, PaymentTestOrderRequest } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 시험 주문 API(`PAYMENT_TEST`) — 서버 주소 문자열(`/api/dev/payments…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 결정 7·8.
 * 서버가 `roboyam.payment.test-orders` 를 켠 환경(local·dev)에서만 있다 — 꺼져 있으면 404. 화면은 `/dev/payment`(개발 모드) 하나다.
 *  - `POST /api/dev/payments` `{ amount(100~1,000,000), validMinutes(1~60) }` → `PaymentTestOrder`(결제창 정보 포함) · `400 VALIDATION fields.amount|validMinutes` · `503 PAYMENT_NOT_CONFIGURED`
 *  - `POST /api/dev/payments/{paymentId}/fake-checkout` `{ outcome, amount, late }` → 204 — 가짜 PG(`fake_pg_payments`)에 결제 결과를 남긴다. 가짜 게이트웨이가 아니면 `409 PAYMENT_GATEWAY_NOT_FAKE`
 *  - `GET  /api/dev/payments/{paymentId}` → `PaymentResult`(자동 취소가 끝났는지 다시 볼 때)
 *
 * ⚠ MOCK: 서버(2a-2)가 붙기 전이라 `./mock/payment-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    orders: '/api/dev/payments',
    order: (paymentId: string) => `/api/dev/payments/${encodeURIComponent(paymentId)}`,
    fakeCheckout: (paymentId: string) => `/api/dev/payments/${encodeURIComponent(paymentId)}/fake-checkout`,
};

export function usePaymentTestApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH; // MOCK: 서버가 붙으면 이 줄을 지운다

    return {
        create: (body: PaymentTestOrderRequest): Promise<PaymentTestOrder> =>
            // MOCK:
            mockCreateTestOrder(body),
        // REAL: client.post<PaymentTestOrder>(PATH.orders, body),

        /** 가짜 결제창의 결과를 가짜 PG 에 남긴다 — 포트원 결제창이 포트원에 결제를 남기는 것과 같은 자리 */
        fakeCheckout: (paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> =>
            // MOCK:
            mockFakeCheckout(paymentId, body),
        // REAL: client.post<void>(PATH.fakeCheckout(paymentId), body),

        result: (paymentId: string): Promise<PaymentResult> =>
            // MOCK:
            mockTestOrderResult(paymentId),
        // REAL: client.get<PaymentResult>(PATH.order(paymentId)),
    };
}
