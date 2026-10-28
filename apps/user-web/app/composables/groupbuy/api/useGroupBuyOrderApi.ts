import type { GroupBuyPaymentStart } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import type {
    GroupBuyAddressUpdate,
    GroupBuyCancelPreview,
    GroupBuyCancelRequest,
    GroupBuyRequestCreate,
    GroupBuyRequestCreated,
    GroupBuyRequestPhoto,
    GroupBuyReturnPreview,
    GroupBuyReturnPreviewRequest,
    GroupBuySupplyChoiceRequest,
    MyGroupBuyAddress,
    MyGroupBuyOrderDetail,
    MyGroupBuyOrderPage,
} from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilIdempotency from '~/utils/util-idempotency';

/**
 * 내 주문(공구) API — 서버 주소 문자열(`/api/me/orders…`)이 있는 **유일한 곳**이다. 상태는 없다. 계약 `commerce-groupbuy-contract.md` 화면 목록 `/me/orders`. 모두 로그인(본인 주문만 — 남의 주문은 404).
 *  - `GET  /api/me/orders?page=` → `MyGroupBuyOrderPage`
 *  - `GET  /api/me/orders/{orderNo}` → `MyGroupBuyOrderDetail`(배송지는 가린 값) · `404 GROUP_BUY_ORDER_NOT_FOUND`
 *  - `POST /api/me/orders/{orderNo}/payment` → `GroupBuyPaymentStart` — 결제 다시 열기(선점을 늘리지 않는다, 남은 선점 1분 미만이면 `409 GROUP_BUY_HOLD_EXPIRED`, 유효한 READY 가 있으면 그것 — 결정 41·63)
 *  - `GET  /api/me/orders/{orderNo}/address` → `MyGroupBuyAddress`(전체 값 — 화면 메모리에만) · `PUT …/address` `GroupBuyAddressUpdate` → 상세 · `409 GROUP_BUY_ADDRESS_LOCKED`(기한 — 결정 30)
 *  - `POST /api/me/orders/{orderNo}/cancel/preview` `{ lines[{ skuId, qty }] }` → `GroupBuyCancelPreview`
 *  - `POST /api/me/orders/{orderNo}/cancel` + `Idempotency-Key` `{ lines[{ skuId, qty }] }` → 상세 — 본인 즉시 취소(단품·수량 단위, 결정 42·45·88) · `409 GROUP_BUY_ORDER_STATE` · `409 GROUP_BUY_ADDON_ONLY {skuIds}`(본 상품을 모두 취소하며 추가 구성상품을 남김 — 함께 다시 보낸다)
 *  - `POST /api/me/orders/{orderNo}/requests/preview` `{ items[{skuId, qty, reason}] }` → `GroupBuyReturnPreview`(회수 방식별 결과 — 결정 68)
 *  - `POST /api/me/orders/{orderNo}/requests` + `Idempotency-Key` `GroupBuyRequestCreate` → `GroupBuyRequestCreated`(부족분이면 결제창 대상) · `409 GROUP_BUY_REQUEST_OPEN|AMOUNT_CHANGED|RETURN_WINDOW_CLOSED`
 *  - `POST /api/me/orders/{orderNo}/requests/{id}/payment` → `GroupBuyPaymentStart` — 반품비 부족분 결제(다시 열기)
 *  - `POST /api/me/orders/{orderNo}/requests/{id}/withdraw` → 상세
 *  - `POST /api/me/orders/{orderNo}/delay-cancel` + `Idempotency-Key` → 상세 — 발송 지연 공지 뒤 즉시 전액 취소(결정 52)
 *  - `POST /api/me/orders/{orderNo}/supply-choice` `{ choice }` → 상세 — 공급 불가 「나머지만 받기 / 전체 취소」(결정 53)
 *  - `POST /api/me/orders/{orderNo}/photos`(multipart `file`) → `GroupBuyRequestPhoto` — 하자·오배송 사진(요청 전에 올리고 붙인다, 붙지 않으면 24시간 뒤 정리)
 *
 */
const PATH = {
    list: '/api/me/orders',
    detail: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}`,
    payment: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/payment`,
    address: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/address`,
    cancelPreview: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/cancel/preview`,
    cancel: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/cancel`,
    requestPreview: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/requests/preview`,
    requests: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/requests`,
    requestPayment: (orderNo: string, id: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/requests/${id}/payment`,
    withdraw: (orderNo: string, id: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/requests/${id}/withdraw`,
    delayCancel: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/delay-cancel`,
    supplyChoice: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/supply-choice`,
    photos: (orderNo: string) => `/api/me/orders/${encodeURIComponent(orderNo)}/photos`,
};

export function useGroupBuyOrderApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (page = 0): Promise<MyGroupBuyOrderPage> =>
            client.get<MyGroupBuyOrderPage>(PATH.list, { query: { page } }),

        get: (orderNo: string): Promise<MyGroupBuyOrderDetail> =>
            client.get<MyGroupBuyOrderDetail>(PATH.detail(orderNo)),

        reopenPayment: (orderNo: string): Promise<GroupBuyPaymentStart> =>
            client.post<GroupBuyPaymentStart>(PATH.payment(orderNo), {}),

        revealAddress: (orderNo: string): Promise<MyGroupBuyAddress> =>
            client.get<MyGroupBuyAddress>(PATH.address(orderNo)),

        updateAddress: (orderNo: string, body: GroupBuyAddressUpdate): Promise<MyGroupBuyOrderDetail> =>
            client.put<MyGroupBuyOrderDetail>(PATH.address(orderNo), body),

        cancelPreview: (orderNo: string, body: GroupBuyCancelRequest): Promise<GroupBuyCancelPreview> =>
            client.post<GroupBuyCancelPreview>(PATH.cancelPreview(orderNo), body),

        cancelNow: (orderNo: string, body: GroupBuyCancelRequest, idempotencyKey: string = UtilIdempotency.newKey()): Promise<MyGroupBuyOrderDetail> =>
            client.post<MyGroupBuyOrderDetail>(PATH.cancel(orderNo), body, { headers: { 'Idempotency-Key': idempotencyKey } }),

        returnPreview: (orderNo: string, body: GroupBuyReturnPreviewRequest): Promise<GroupBuyReturnPreview> =>
            client.post<GroupBuyReturnPreview>(PATH.requestPreview(orderNo), body),

        createRequest: (orderNo: string, body: GroupBuyRequestCreate, idempotencyKey: string = UtilIdempotency.newKey()): Promise<GroupBuyRequestCreated> =>
            client.post<GroupBuyRequestCreated>(PATH.requests(orderNo), body, { headers: { 'Idempotency-Key': idempotencyKey } }),

        requestFeePayment: (orderNo: string, requestId: string): Promise<GroupBuyPaymentStart> =>
            client.post<GroupBuyPaymentStart>(PATH.requestPayment(orderNo, requestId), {}),

        withdrawRequest: (orderNo: string, requestId: string): Promise<MyGroupBuyOrderDetail> =>
            client.post<MyGroupBuyOrderDetail>(PATH.withdraw(orderNo, requestId), {}),

        delayCancel: (orderNo: string, idempotencyKey: string = UtilIdempotency.newKey()): Promise<MyGroupBuyOrderDetail> =>
            client.post<MyGroupBuyOrderDetail>(PATH.delayCancel(orderNo), {}, { headers: { 'Idempotency-Key': idempotencyKey } }),

        supplyChoice: (orderNo: string, body: GroupBuySupplyChoiceRequest): Promise<MyGroupBuyOrderDetail> =>
            client.post<MyGroupBuyOrderDetail>(PATH.supplyChoice(orderNo), body),

        uploadPhoto: (orderNo: string, file: File): Promise<GroupBuyRequestPhoto> =>
            client.upload<GroupBuyRequestPhoto>(PATH.photos(orderNo), (() => { const form = new FormData(); form.append('file', file); return form; })()),
    };
}
