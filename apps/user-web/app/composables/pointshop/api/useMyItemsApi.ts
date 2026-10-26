import type { EquipSlot, MyEquipped, MyItemList, MyItemRefundOutcome } from '~/composables/pointshop/api/useMyItemsApi.types';
import UtilIdempotency from '~/utils/util-idempotency';

/**
 * 내 아이템 API — 서버 주소 문자열(`/api/me/items…`·`/api/me/equipped/…`)이 있는 **유일한 곳**이다. 상태는 없다(장착한 내 장식의 겹치기는 `useMyDecoration`). 로그인 필요(401). **서버 연결됨**.
 * 계약: `docs/features/commerce-stage1-contract.md` 「1-2 화면 목업」.
 *  - `GET /api/me/items` → `MyItemList`(회수된 줄도 온다 — 화면이 「환불됨」 구획에 접는다)
 *  - `PUT /api/me/equipped/{slot}` `{ userItemId }` → `MyEquipped` · 장착하면 `firstUsedAt` 이 채워진다(환불 불가). `409 USER_ITEM_EXPIRED`·`USER_ITEM_NOT_EQUIPPABLE` · `404 USER_ITEM_NOT_FOUND`
 *  - `DELETE /api/me/equipped/{slot}` → `MyEquipped`
 *  - `POST /api/me/items/{id}/refund` + 머리글 `Idempotency-Key`(필수) → `MyItemRefundResult` · `409 POINT_SHOP_REFUND_NOT_ALLOWED`(최상위 `blockedBy` `USED|EXPIRED`)·`POINT_SHOP_ALREADY_REFUNDED`·`POINT_SHOP_REFUND_PENDING`·`POINT_SHOP_REFUND_REJECTED` · `429 REQUEST_RATE_LIMITED` · 정지 중이면 `202` 환불 신청 `{ requestId, status, requestedAt }`
 */
const PATH = {
    items: '/api/me/items',
    refund: (id: string) => `${PATH.items}/${encodeURIComponent(id)}/refund`,
    equipped: (slot: EquipSlot) => `/api/me/equipped/${slot}`,
};

export function useMyItemsApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 아이템 전부(한 줄 = 보유 한 줄) */
        list: (): Promise<MyItemList> => client.get<MyItemList>(PATH.items),

        /** 그 자리에 장착한다 */
        equip: (slot: EquipSlot, userItemId: string): Promise<MyEquipped> => client.put<MyEquipped>(PATH.equipped(slot), { userItemId }),

        /** 그 자리를 비운다 */
        unequip: (slot: EquipSlot): Promise<MyEquipped> => client.del<MyEquipped>(PATH.equipped(slot)),

        /** 셀프 환불 — 정지 중이면 환불 신청(202, `requestId`)이 된다. 같은 뜻의 다시 보내기는 같은 `idempotencyKey` 로 */
        refund: (userItemId: string, idempotencyKey: string = UtilIdempotency.newKey()): Promise<MyItemRefundOutcome> =>
            client.post<MyItemRefundOutcome>(PATH.refund(userItemId), undefined, { headers: { 'Idempotency-Key': idempotencyKey } }),
    };
}
