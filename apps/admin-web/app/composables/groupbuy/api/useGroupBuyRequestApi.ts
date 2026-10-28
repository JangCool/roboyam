import type {
    GroupBuyApproveRequest,
    GroupBuyCompleteRequest,
    GroupBuyEvidencePhoto,
    GroupBuyExchangeShipRequest,
    GroupBuyPickupRequest,
    GroupBuyReceiveRequest,
    GroupBuyRejectRequest,
    GroupBuyRequestDetail,
    GroupBuyRequestListResponse,
    GroupBuyRequestQuery,
} from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';

/**
 * 공구 취소·반품 요청 관리자 API — 서버 주소 문자열(`/api/admin/group-buy-requests…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-groupbuy-contract.md`(결정 15·44·54·55·68·70·72, commerce.md 10-8). 서버는 2b-3.
 *
 * 권한: 조회 `commerce.order.read` · 승인(계산기 값 그대로 — 금액과 관계없이)·거절·회수 송장·회수 확인·교환 재발송·환불로 바꾸기 `commerce.order.manage`(환불이 나가는 승인·완료는 + `commerce.refund.approve`)
 * · 처리 방법(다시 판매 포함) `commerce.order.manage` · 고객 택배비 보상액은 운영자가 정하는 금액이라 누계가 문턱 이상이면 `commerce.refund.large`(결정 70).
 *   - `GET  /api/admin/group-buy-requests?status=&type=&level=&q=&page=&size=` → `GroupBuyRequestListResponse`(오래 기다린 것 먼저)
 *   - `GET  /api/admin/group-buy-requests/{id}` → `GroupBuyRequestDetail`
 *   - `POST …/{id}/approve {expectedAmount}` · `…/reject {reasonCode, reason, photoIds}` · `…/pickup` · `…/receive {receivedAt}` · `…/complete` · `…/exchange-ship` · `…/convert-to-refund {reason}`
 *   - `POST /api/admin/group-buy-requests/photos`(multipart `file`) → `GroupBuyEvidencePhoto`(거절 근거 — 5년 보관)
 *   - 오류: `409 GROUP_BUY_REQUEST_NOT_OPEN`·`GROUP_BUY_REFUND_AMOUNT_CHANGED(extras.previewAmount·recalculatedAmount)`·`GROUP_BUY_NOT_RESELLABLE`·`GROUP_BUY_REJECT_REASON_NOT_ALLOWED`·`GROUP_BUY_PAYMENT_UNRECONCILED`
 *
 */
const PATH = {
    root: '/api/admin/group-buy-requests',
    photos: () => `${PATH.root}/photos`,
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
};

export function useGroupBuyRequestApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        list: (query: GroupBuyRequestQuery): Promise<GroupBuyRequestListResponse> =>
            client.get<GroupBuyRequestListResponse>(PATH.root, { query: { ...query } }),

        detail: (id: string): Promise<GroupBuyRequestDetail> =>
            client.get<GroupBuyRequestDetail>(PATH.one(id)),

        approve: (id: string, body: GroupBuyApproveRequest): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'approve'), body),

        reject: (id: string, body: GroupBuyRejectRequest): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'reject'), body),

        uploadPhoto: (file: File): Promise<GroupBuyEvidencePhoto> =>
            client.post<GroupBuyEvidencePhoto>(PATH.photos(), (() => { const form = new FormData(); form.append('file', file); return form; })()),

        setPickup: (id: string, body: GroupBuyPickupRequest): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'pickup'), body),

        receive: (id: string, body: GroupBuyReceiveRequest): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'receive'), body),

        complete: (id: string, body: GroupBuyCompleteRequest): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'complete'), body),

        exchangeShip: (id: string, body: GroupBuyExchangeShipRequest): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'exchange-ship'), body),

        convertToRefund: (id: string, reason: string): Promise<GroupBuyRequestDetail> =>
            client.post<GroupBuyRequestDetail>(PATH.sub(id, 'convert-to-refund'), { reason }),
    };
}
