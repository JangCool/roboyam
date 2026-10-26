import type {
    PointShopAdminItem,
    PointShopAdminItemList,
    PointShopAdminItemQuery,
    PointShopAvatarBulkResult,
    PointShopAvatarPriceRequest,
    PointShopAvatarSaleRequest,
    PointShopAvatarSummary,
    PointShopItemSaveRequest,
    PointShopPurchasePage,
    PointShopPurchaseQuery,
    PointShopRefundRejectRequest,
    PointShopRefundRequest,
    PointShopRefundRequestDecision,
    PointShopRefundResult,
    PointShopSettingsSaveRequest,
    PointShopSettingsView,
} from '~/composables/pointshop/api/usePointShopAdminApi.types';

/**
 * 포인트 상점 관리자 API — 서버 주소 문자열(`/api/admin/point-shop/…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-stage1-contract.md` B절 · 「1-2 화면 목업」 관리자 화면, 정책 `docs/domain/commerce.md` 7절 · 13-2 · 13-4. 서버 `server/admin` `pointshop.controller`.
 *
 * 권한: 조회 `pointshop.read`, 품목·설정·아바타 묶음 저장 `pointshop.manage`, 예외 환불 `pointshop.refund`(SENSITIVE). 없으면 `403 PERMISSION_DENIED`, 세션이 끝났으면 `401`.
 * 쓰기는 CSRF(`useApiClient`). 틀린 칸은 모아 `400 VALIDATION fields.<칸>` — 화면은 서버 문구를 그대로 보인다.
 *   - `GET  /api/admin/point-shop/items?kind=&excludeKind=` → `PointShopAdminItemList`(순서 → id). 「품목」 탭은 `excludeKind=PROFILE_AVATAR`, 「아바타」 탭은 `kind=PROFILE_AVATAR`(306줄). 모르는 이름은 `400 fields.kind|excludeKind`
 *   - `POST /api/admin/point-shop/items` `PointShopItemSaveRequest` → `201` + `PointShopAdminItem`. 품목 칸 전부 검사(종류별 칸 규칙 · 코드 유일 · 아바타는 만들 수 없어 `fields.kind`)
 *   - `PUT  /api/admin/point-shop/items/{id}` 같은 본문(`code`·`kind`·`avatarId` 는 그대로 — 바꾸면 `400 fields.code|kind|avatarId`) → `PointShopAdminItem`. 없으면 `404 POINT_SHOP_ITEM_NOT_FOUND`
 *       (`GET /api/admin/point-shop/items/{id}` 도 있지만 화면은 목록 줄로 서랍을 열어 쓰지 않는다.) 감사(일반 운영 1년). 색·테두리를 고치면 산 사람의 장식도 바로 바뀐다(가격·이름은 구매 사본).
 *   - `GET  /api/admin/point-shop/purchases?from&to&status&kind&q&page&size` → `PointShopPurchasePage`(구매 번호 내림차순). page 기본 0 · size 기본 20(상한 100).
 *       `from`·`to` 는 UTC ISO 순간이고 **`to` 는 미만**이다(화면은 끝 날의 다음 날 0시를 보낸다). 조건이 틀리면 `400 fields.from|to|status|kind|page|size`
 *       `refundRequest=PENDING|APPROVED|REJECTED` 면 그 상태의 환불 신청이 있는 구매만(모르는 값 `400 fields.refundRequest`). 줄마다 `refundRequest`(없으면 null).
 *   - `POST /api/admin/point-shop/purchases/{id}/refund` `{ reason }`(5~200자) → `PointShopRefundResult`. `409 POINT_SHOP_ALREADY_REFUNDED` · `400 fields.reason` · `404 POINT_SHOP_PURCHASE_NOT_FOUND`
 *       · 대기 중인 환불 신청이 있는 구매는 `409 POINT_SHOP_REFUND_PENDING`(신청 수락·거절로 처리한다)
 *   - 정지 중 환불 신청(계약 「1-2 정지 중 환불 신청」, 권한 `pointshop.refund`):
 *       `POST /api/admin/point-shop/refund-requests/{id}/approve` → `PointShopRefundRequestDecision`(신청 순간 판정으로 환불 — 지금 기한이 지났어도, 환불 종류 `REQUEST`)
 *       `POST /api/admin/point-shop/refund-requests/{id}/reject` `{ reason }`(5~200자) → 같은 모양(`refundedPoints` 0, 아이템은 다시 쓸 수 있다)
 *       오류 `409 POINT_SHOP_REFUND_REQUEST_NOT_PENDING`(이미 결정) · `404 POINT_SHOP_REFUND_REQUEST_NOT_FOUND` · `400 fields.reason`. 감사: 수락 `POINT_SHOP_REFUND` · 거절 `POINT_SHOP_REFUND_REJECT`(둘 다 돈 관련 5년).
 *       새 신청은 운영자 알림 `POINT_SHOP_REFUND_REQUESTED`(링크 `/point-shop/purchases?refundRequest=PENDING`).
 *       감사(돈 관련 5년): 서비스가 직접 남긴다. 아이템 REVOKED → 사용 취소(원래 묶음) → 재고 복원 → 구매 REFUNDED(한 트랜잭션).
 *   - `GET/PUT /api/admin/point-shop/settings` `{ refundDays }`(1~30) → `PointShopSettingsView`. `400 fields.refundDays`. 감사(일반 운영).
 *   - 아바타(결정 14 — 품목은 V86 시드, 새로 만들거나 지울 수 없다):
 *       `GET  /api/admin/point-shop/avatars/summary` → `PointShopAvatarSummary`(카테고리 14개, 카탈로그 차례 — 카테고리 목록·무료 판정은 이 응답과 품목의 `category` 가 정본)
 *       `PUT  /api/admin/point-shop/avatars/price` `{ scope, categories?, itemIds?, price }` · `POST /api/admin/point-shop/avatars/sale` `{ scope, categories?, itemIds?, active }` → `PointShopAvatarBulkResult { matched, changed }`
 *       `400 fields.scope|categories|itemIds|price|active` — 무료 카테고리·아바타 아닌 품목이 섞이면 `400 fields.categories|itemIds`, **없는 품목 번호가 섞이면 `404 POINT_SHOP_ITEM_NOT_FOUND`**(화면은 둘 다 서버 문구를 그대로 보인다). 아무것도 안 바뀐다. 한 트랜잭션, 감사(일반 운영 — 요청 한 건이 한 줄).
 * 응답 `Cache-Control: no-store`.
 */
const PATH = {
    root: '/api/admin/point-shop',
    items: () => `${PATH.root}/items`,
    item: (id: string) => `${PATH.root}/items/${encodeURIComponent(id)}`,
    purchases: () => `${PATH.root}/purchases`,
    refund: (id: string) => `${PATH.root}/purchases/${encodeURIComponent(id)}/refund`,
    settings: () => `${PATH.root}/settings`,
    approveRequest: (id: string) => `${PATH.root}/refund-requests/${encodeURIComponent(id)}/approve`,
    rejectRequest: (id: string) => `${PATH.root}/refund-requests/${encodeURIComponent(id)}/reject`,
    avatarSummary: () => `${PATH.root}/avatars/summary`,
    avatarPrice: () => `${PATH.root}/avatars/price`,
    avatarSale: () => `${PATH.root}/avatars/sale`,
};

export function usePointShopAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 품목 목록 — 종류로 거르거나(`kind`) 한 종류를 뺄 수 있다(`excludeKind`) */
        items: (query: PointShopAdminItemQuery = {}): Promise<PointShopAdminItemList> => client.get<PointShopAdminItemList>(PATH.items(), { query: { kind: query.kind, excludeKind: query.excludeKind } }),

        /** 품목 만들기(201) */
        createItem: (body: PointShopItemSaveRequest): Promise<PointShopAdminItem> => client.post<PointShopAdminItem>(PATH.items(), body),

        /** 품목 고치기 — `code`·`kind`·`avatarId` 는 그대로 싣는다 */
        updateItem: (id: string, body: PointShopItemSaveRequest): Promise<PointShopAdminItem> => client.put<PointShopAdminItem>(PATH.item(id), body),

        /** 구매 내역 한 쪽 — 구매 번호 내림차순 */
        purchases: (query: PointShopPurchaseQuery, signal?: AbortSignal): Promise<PointShopPurchasePage> => client.get<PointShopPurchasePage>(PATH.purchases(), { query: { ...query }, signal }),

        /** 예외 환불(셀프 환불 조건 밖) — 사유 필수 5~200자 */
        refund: (id: string, body: PointShopRefundRequest): Promise<PointShopRefundResult> => client.post<PointShopRefundResult>(PATH.refund(id), body),

        /** 정지 중 환불 신청 수락 — 신청 순간 판정으로 환불한다 */
        approveRefundRequest: (requestId: string): Promise<PointShopRefundRequestDecision> => client.post<PointShopRefundRequestDecision>(PATH.approveRequest(requestId)),

        /** 정지 중 환불 신청 거절 — 사유 필수 5~200자(회원 알림에 실린다) */
        rejectRefundRequest: (requestId: string, body: PointShopRefundRejectRequest): Promise<PointShopRefundRequestDecision> => client.post<PointShopRefundRequestDecision>(PATH.rejectRequest(requestId), body),

        /** 포인트 상점 설정(환불 기한) */
        settings: (): Promise<PointShopSettingsView> => client.get<PointShopSettingsView>(PATH.settings()),

        /** 포인트 상점 설정 저장 */
        saveSettings: (body: PointShopSettingsSaveRequest): Promise<PointShopSettingsView> => client.put<PointShopSettingsView>(PATH.settings(), body),

        /** 아바타 카테고리 요약(전체·판매 중·중단·가격 범위) */
        avatarSummary: (): Promise<PointShopAvatarSummary> => client.get<PointShopAvatarSummary>(PATH.avatarSummary()),

        /** 아바타 묶음 가격 — 이미 산 구매의 가격은 그대로 */
        avatarPrice: (body: PointShopAvatarPriceRequest): Promise<PointShopAvatarBulkResult> => client.put<PointShopAvatarBulkResult>(PATH.avatarPrice(), body),

        /** 아바타 묶음 판매 켜기·끄기 — 산 사람은 판매를 꺼도 계속 고른다 */
        avatarSale: (body: PointShopAvatarSaleRequest): Promise<PointShopAvatarBulkResult> => client.post<PointShopAvatarBulkResult>(PATH.avatarSale(), body),
    };
}
