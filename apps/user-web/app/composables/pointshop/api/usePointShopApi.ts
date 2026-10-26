import type { PointShopItemList, PointShopItemQuery, PointShopItemView, PointShopPurchaseRequest, PointShopPurchaseResult } from '~/composables/pointshop/api/usePointShopApi.types';
import UtilIdempotency from '~/utils/util-idempotency';

/**
 * 포인트 상점 API — 서버 주소 문자열(`/api/point-shop/…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 필요(401, 화면은 `auth` 미들웨어). **서버 연결됨**(server/api `pointshop.controller`).
 * 계약: `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」(API 후보, 회원·api 서버).
 *  - `GET /api/point-shop/items?kind=` → `PointShopItemList`(`refundDays`·`balance`·품목들 — 아바타 품목은 섞이지 않는다, `GET /api/avatars`)
 *  - `GET /api/point-shop/items/{id}` → `PointShopItemView` · 없는 품목·형식이 틀린 ID `404 POINT_SHOP_ITEM_NOT_FOUND`
 *  - `POST /api/point-shop/purchases` `{ itemId, agreeRefundNotice: true }` + 머리글 `Idempotency-Key`(필수) → `PointShopPurchaseResult`. 같은 키로 다시 보내면 처음 결과(머리글 `Idempotent-Replayed: true`).
 *    오류는 `usePointShopApi.types.ts` 의 `PointShopPurchaseResult` 주석.
 *
 * 멱등 키: 구매 한 번의 뜻(확인 대화상자 한 번)마다 `UtilIdempotency.newKey()` 를 만들어 보낸다 — 네트워크가 끊겨 다시 누르면 **같은 키**로 다시 보내 두 번 사지 않는다(서버 `MoneyRequestGuard`).
 */
const PATH = {
    items: '/api/point-shop/items',
    item: (id: string) => `${PATH.items}/${encodeURIComponent(id)}`,
    purchases: '/api/point-shop/purchases',
};

export function usePointShopApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 품목 목록 + 내 포인트 + 환불 날 수 — 빈 `kind` 는 보내지 않는다(전체) */
        list: (query: PointShopItemQuery = {}): Promise<PointShopItemList> => client.get<PointShopItemList>(PATH.items, { query: { kind: query.kind } }),

        /** 품목 하나 */
        get: (id: string): Promise<PointShopItemView> => client.get<PointShopItemView>(PATH.item(id)),

        /** 산다 — 같은 뜻의 다시 보내기는 같은 `idempotencyKey` 로 */
        purchase: (body: PointShopPurchaseRequest, idempotencyKey: string = UtilIdempotency.newKey()): Promise<PointShopPurchaseResult> =>
            client.post<PointShopPurchaseResult>(PATH.purchases, body, { headers: { 'Idempotency-Key': idempotencyKey } }),
    };
}
