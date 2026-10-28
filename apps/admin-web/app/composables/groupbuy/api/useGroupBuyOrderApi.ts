import {
    mockOrderCarriers,
    mockOrderDeliver,
    mockOrderDetail,
    mockOrderExport,
    mockOrderFixTracking,
    mockOrderList,
    mockOrderMemo,
    mockOrderPrepare,
    mockOrderRefund,
    mockOrderRefundPreview,
    mockOrderReveal,
    mockOrderShip,
} from '~/composables/groupbuy/api/mock/groupbuy-mock';
import type {
    GroupBuyAdminRefundRequest,
    GroupBuyAdminRefundResult,
    GroupBuyBulkResult,
    GroupBuyCarrierChoice,
    GroupBuyExportRequest,
    GroupBuyExportResult,
    GroupBuyOrderDetail,
    GroupBuyOrderListResponse,
    GroupBuyOrderQuery,
    GroupBuyRefundPreview,
    GroupBuyRevealedAddress,
    GroupBuyShipRow,
    GroupBuyTrackingFixRequest,
} from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';

/**
 * 공구 주문 관리자 API(주문·발송·운영 환불·배송지·명단) — 서버 주소 문자열(`/api/admin/group-buy-orders…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-groupbuy-contract.md`(결정 13·16·29·30·32·47·70, commerce.md 10-9). 서버는 2b-3.
 *
 * 권한: 조회 `commerce.order.read` · 준비중·발송·송장 정정·배송완료·메모 `commerce.order.manage` · 운영 환불 `commerce.refund.approve`(누계가 문턱 이상이면 `commerce.refund.large`, 없으면 승인 대기)
 * · 배송지 열람 `commerce.address.reveal`(사유 5~200자, 감사 `GROUPBUY_ADDRESS_REVEAL`) · 명단 내보내기 `commerce.export` + `commerce.address.reveal`(감사 `GROUPBUY_SHIPPING_EXPORT`).
 *   - `GET  /api/admin/group-buy-orders?status=&groupBuyId=&alert=&q=&from=&to=&page=&size=` → `GroupBuyOrderListResponse`
 *   - `GET  /api/admin/group-buy-orders/{id}` → `GroupBuyOrderDetail`(감사 `GROUPBUY_ORDER_VIEW`)
 *   - `POST /api/admin/group-buy-orders/{id}/address-reveal {reason}` → `GroupBuyRevealedAddress`
 *   - `PUT  /api/admin/group-buy-orders/{id}/memo {memo}` → `GroupBuyOrderDetail`
 *   - `POST /api/admin/group-buy-orders/prepare {orderIds}` · `…/ship {rows}` · `…/deliver {orderIds}` → `GroupBuyBulkResult`(최대 200건, 줄 단위 부분 성공)
 *   - `POST /api/admin/group-buy-orders/{id}/tracking {carrierCode, trackingNo, reason}` → `GroupBuyOrderDetail`
 *   - `POST /api/admin/group-buy-orders/{id}/refund-preview` · `…/refunds` → `GroupBuyRefundPreview` · `GroupBuyAdminRefundResult` · `409 GROUP_BUY_REFUND_SHIPPED`·`GROUP_BUY_PAYMENT_UNRECONCILED`
 *   - `GET  /api/admin/group-buy-orders/carriers` → `GroupBuyCarrierChoice[]`(켜진 택배사)
 *   - `POST /api/admin/group-buy-orders/export {groupBuyId, reason}` → `GroupBuyExportResult`(한 번 쓰는 내려받기 주소) · `409 GROUP_BUY_EXPORT_NOT_READY`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/groupbuy-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    root: '/api/admin/group-buy-orders',
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
    bulk: (action: 'prepare' | 'ship' | 'deliver' | 'export' | 'carriers') => `${PATH.root}/${action}`,
};

export function useGroupBuyOrderApi() {
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 주문 목록 */
        list: (query: GroupBuyOrderQuery): Promise<GroupBuyOrderListResponse> =>
            // MOCK:
            mockOrderList(query),
        // REAL: client.get<GroupBuyOrderListResponse>(PATH.root, { query: { ...query } }),

        /** 주문 상세 */
        detail: (id: string): Promise<GroupBuyOrderDetail> =>
            // MOCK:
            mockOrderDetail(id),
        // REAL: client.get<GroupBuyOrderDetail>(PATH.one(id)),

        /** 배송지 열람(사유 필수) */
        revealAddress: (id: string, reason: string): Promise<GroupBuyRevealedAddress> =>
            // MOCK:
            mockOrderReveal(id, reason),
        // REAL: client.post<GroupBuyRevealedAddress>(PATH.sub(id, 'address-reveal'), { reason }),

        /** 운영 메모 저장 */
        saveMemo: (id: string, memo: string): Promise<GroupBuyOrderDetail> =>
            // MOCK:
            mockOrderMemo(id, memo),
        // REAL: client.put<GroupBuyOrderDetail>(PATH.sub(id, 'memo'), { memo }),

        /** 발송에 고를 택배사(켜진 것) */
        carriers: (): Promise<GroupBuyCarrierChoice[]> =>
            // MOCK:
            mockOrderCarriers(),
        // REAL: client.get<GroupBuyCarrierChoice[]>(PATH.bulk('carriers')),

        /** 준비중(일괄) */
        prepare: (orderIds: string[]): Promise<GroupBuyBulkResult> =>
            // MOCK:
            mockOrderPrepare(orderIds),
        // REAL: client.post<GroupBuyBulkResult>(PATH.bulk('prepare'), { orderIds }),

        /** 발송(일괄 — 택배사·송장) */
        ship: (rows: GroupBuyShipRow[]): Promise<GroupBuyBulkResult> =>
            // MOCK:
            mockOrderShip(rows),
        // REAL: client.post<GroupBuyBulkResult>(PATH.bulk('ship'), { rows }),

        /** 배송완료(일괄) */
        deliver: (orderIds: string[]): Promise<GroupBuyBulkResult> =>
            // MOCK:
            mockOrderDeliver(orderIds),
        // REAL: client.post<GroupBuyBulkResult>(PATH.bulk('deliver'), { orderIds }),

        /** 송장 정정 */
        fixTracking: (id: string, body: GroupBuyTrackingFixRequest): Promise<GroupBuyOrderDetail> =>
            // MOCK:
            mockOrderFixTracking(id, body),
        // REAL: client.post<GroupBuyOrderDetail>(PATH.sub(id, 'tracking'), body),

        /** 운영 환불 미리 보기 */
        refundPreview: (id: string, body: GroupBuyAdminRefundRequest): Promise<GroupBuyRefundPreview> =>
            // MOCK:
            mockOrderRefundPreview(id, body),
        // REAL: client.post<GroupBuyRefundPreview>(PATH.sub(id, 'refund-preview'), body),

        /** 운영 환불 */
        refund: (id: string, body: GroupBuyAdminRefundRequest): Promise<GroupBuyAdminRefundResult> =>
            // MOCK:
            mockOrderRefund(id, body),
        // REAL: client.post<GroupBuyAdminRefundResult>(PATH.sub(id, 'refunds'), body),

        /** 배송 명단 내보내기 */
        exportShipping: (body: GroupBuyExportRequest): Promise<GroupBuyExportResult> =>
            // MOCK:
            mockOrderExport(body),
        // REAL: client.post<GroupBuyExportResult>(PATH.bulk('export'), body),
    };
}
