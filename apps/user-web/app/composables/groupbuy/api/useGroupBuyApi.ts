import {
    mockGroupBuyCompletePayment,
    mockGroupBuyDetail,
    mockGroupBuyFakeCheckout,
    mockGroupBuyList,
    mockGroupBuyOrder,
    mockGroupBuyQuote,
    mockGroupBuyWatch,
} from '~/composables/groupbuy/api/mock/groupbuy-mock';
import type {
    GroupBuyDetail,
    GroupBuyListPage,
    GroupBuyOrderCreated,
    GroupBuyOrderRequest,
    GroupBuyPhase,
    GroupBuyQuote,
    GroupBuyQuoteRequest,
    GroupBuyWatchRequest,
} from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type { PaymentCompleteRequest, PaymentFakeCheckoutRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';
import UtilIdempotency from '~/utils/util-idempotency';

/**
 * 공구(회원) API — 서버 주소 문자열(`/api/group-buys…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 `docs/features/commerce-groupbuy-contract.md`.
 *  - `GET  /api/group-buys?phase=UPCOMING|ONGOING|ENDED&page=` → `GroupBuyListPage` (로그인 없이)
 *  - `GET  /api/group-buys/{id}` → `GroupBuyDetail` · `404 GROUP_BUY_NOT_FOUND` (로그인 없이 — 로그인했으면 `purchase`·`watches`·`myOrderNo` 가 그 회원 기준)
 *  - `POST /api/group-buys/{id}/quote` `{ items[{ skuId, qty }] }` → `GroupBuyQuote` · `400 VALIDATION fields.items|items[i].qty|items[i].skuId`
 *      · `409 GROUP_BUY_SKU_NOT_ON_SALE|ADDON_ONLY|SKU_SOLD_OUT` (선점하지 않는다 — 결정 88, 2b-2 확정 전 가정)
 *  - `POST /api/group-buys/{id}/orders` + 머리글 `Idempotency-Key` `{ items[{ skuId, qty }], address, agreements, expectedAmount }` → `GroupBuyOrderCreated`(주문 PENDING_PAYMENT + 선점 + 결제창 정보)
 *      · `400 VALIDATION fields.items…|address.*|agreements.*` · `409 GROUP_BUY_AMOUNT_CHANGED`(extras `expectedAmount`·`currentAmount` — 결정 72)
 *      · `409 GROUP_BUY_NOT_RECRUITING|SOLD_OUT|SKU_SOLD_OUT|SKU_NOT_ON_SALE|ADDON_ONLY|LIMIT_EXCEEDED|ALREADY_JOINED` · `403 GROUP_BUY_SALES_CLOSED|MEMBER_RESTRICTED(GROUP_BUY_BAN)|IDENTITY_REQUIRED` · `429` · `503 DATA_KEY_MISSING`
 *  - `PUT  /api/group-buys/{id}/watches` `{ kind, skuId }` → 204 · `DELETE /api/group-buys/{id}/watches?kind=&skuId=` → 204 (로그인)
 *  - 결제창: 가짜 결과는 `usePaymentTestApi().fakeCheckout`(가짜 게이트웨이 전용), 완료는 공용 `usePaymentApi().complete` — 주소는 각 파일에 있다.
 *
 * ⚠ MOCK: 서버에 API 가 없어(2b-2) `./mock/groupbuy-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    list: '/api/group-buys',
    detail: (id: number) => `/api/group-buys/${id}`,
    quote: (id: number) => `/api/group-buys/${id}/quote`,
    orders: (id: number) => `/api/group-buys/${id}/orders`,
    watches: (id: number) => `/api/group-buys/${id}/watches`,
};

export function useGroupBuyApi() {
    // REAL: const auth = useAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    // REAL: const paymentApi = usePaymentApi();
    // REAL: const testApi = usePaymentTestApi();
    void PATH;

    return {
        list: (phase: GroupBuyPhase, page = 0): Promise<GroupBuyListPage> =>
            // MOCK:
            mockGroupBuyList(phase, page),
        // REAL: client.get<GroupBuyListPage>(PATH.list, { query: { phase, page } }),

        get: (id: number): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyDetail(id),
        // REAL: client.get<GroupBuyDetail>(PATH.detail(id)),

        quote: (id: number, body: GroupBuyQuoteRequest): Promise<GroupBuyQuote> =>
            // MOCK:
            mockGroupBuyQuote(id, body),
        // REAL: client.post<GroupBuyQuote>(PATH.quote(id), body),

        /** 주문 — 결제하기 한 번의 뜻마다 멱등 키 하나(네트워크 실패는 같은 키로 다시, 서버가 4xx 로 거절하면 새 키) */
        order: (id: number, body: GroupBuyOrderRequest, idempotencyKey: string = UtilIdempotency.newKey()): Promise<GroupBuyOrderCreated> =>
            // MOCK:
            (void idempotencyKey, mockGroupBuyOrder(id, body)),
        // REAL: client.post<GroupBuyOrderCreated>(PATH.orders(id), body, { headers: { 'Idempotency-Key': idempotencyKey } }),

        watch: (id: number, body: GroupBuyWatchRequest): Promise<void> =>
            // MOCK:
            mockGroupBuyWatch(id, body, true),
        // REAL: client.put<void>(PATH.watches(id), body),

        unwatch: (id: number, body: GroupBuyWatchRequest): Promise<void> =>
            // MOCK:
            mockGroupBuyWatch(id, body, false),
        // REAL: client.delete<void>(PATH.watches(id), { query: { kind: body.kind, skuId: body.skuId ?? undefined } }),

        /** (결제창 공용 흐름) 가짜 결제창 결과 — 실제는 가짜 게이트웨이의 `fake-checkout` */
        fakeCheckout: (paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> =>
            // MOCK:
            mockGroupBuyFakeCheckout(paymentId, body),
        // REAL: testApi.fakeCheckout(paymentId, body),

        /** (결제창 공용 흐름) 완료 요청 — 실제는 공용 결제 완료 API */
        completePayment: (paymentId: string, body: PaymentCompleteRequest): Promise<PaymentResult> =>
            // MOCK:
            mockGroupBuyCompletePayment(paymentId, body),
        // REAL: paymentApi.complete(paymentId, body),
    };
}
