import type { MyEmoticonPurchaseList, MyEmoticonRefundResult, MyEmoticons } from '~/composables/emoticon/api/useMyEmoticonsApi.types';
import UtilIdempotency from '~/utils/util-idempotency';

/**
 * 내 로보티콘 API — 서버 주소 문자열(`/api/me/emoticons…`·`/api/me/emoticon-purchases…`)이 있는 **유일한 곳**이다. 상태는 없다(공유 상태는 `useMyEmoticons` — 고르기 창이 읽는다). 로그인 필요(401).
 * 서버 `MyEmoticonController`(S1)·`MyEmoticonPurchaseController`(S3) — 계약 `docs/features/emoticon-contract.md` 「S3 구현 결과」.
 *  - `GET /api/me/emoticons` → `MyEmoticons`(최근 가진 것 먼저, 판매 중단 팩도). 산 팩은 `price` = 결제 금액 · `purchaseId`
 *  - `DELETE /api/me/emoticons/{packId}` → `204`(무료 팩만 「목록에서 빼기」 — 가지지 않았으면 할 일 없음). 산 팩은 `409 EMOTICON_PACK_PAID`(계약 가정 11) ·
 *    내가 만든 팩은 `409 EMOTICON_PACK_OWNED` + 최상위 `reason: "CREATOR"`(후속 결정 1)
 *  - `GET /api/me/emoticon-purchases` → `MyEmoticonPurchaseList`(결제 완료·환불만, 최근 결제 먼저, `no-store`)
 *  - `POST /api/me/emoticon-purchases/{id}/refund` + 머리글 `Idempotency-Key`(필수) → `MyEmoticonRefundResult`(결제 취소 — 환불 기한 안 · 한 번도 안 씀, 가정 3).
 *    서버 `MoneyRequestGuard`: 같은 키 다시 보내기 = 처음 응답(`Idempotent-Replayed: true`) · 같은 키 다른 구매 `409 IDEMPOTENCY_KEY_REUSED` · 키 없음 `400 IDEMPOTENCY_KEY_REQUIRED` ·
 *    1분 3번 넘으면 `429 REQUEST_RATE_LIMITED`. 오류 차례 `404 EMOTICON_PURCHASE_NOT_FOUND` → `409 EMOTICON_ALREADY_REFUNDED` → `409 EMOTICON_REFUND_NOT_ALLOWED`(최상위 `blockedBy` `USED|EXPIRED`).
 *    정지 회원도 환불은 된다.
 */
const PATH = {
    mine: '/api/me/emoticons',
    pack: (packId: string) => `${PATH.mine}/${encodeURIComponent(packId)}`,
    purchases: '/api/me/emoticon-purchases',
    refund: (id: string) => `${PATH.purchases}/${encodeURIComponent(id)}/refund`,
};

export function useMyEmoticonsApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 가진 팩 전부(로보티콘 포함) */
        list: (): Promise<MyEmoticons> => client.get<MyEmoticons>(PATH.mine),

        /** 목록에서 빼기(무료 팩) */
        remove: (packId: string): Promise<void> => client.del<void>(PATH.pack(packId)),

        /** 구매 내역 */
        purchases: (): Promise<MyEmoticonPurchaseList> => client.get<MyEmoticonPurchaseList>(PATH.purchases),

        /** 셀프 환불 — 같은 뜻의 다시 보내기는 같은 `idempotencyKey` 로 */
        refund: (id: string, idempotencyKey: string = UtilIdempotency.newKey()): Promise<MyEmoticonRefundResult> =>
            client.post<MyEmoticonRefundResult>(PATH.refund(id), undefined, { headers: { 'Idempotency-Key': idempotencyKey } }),
    };
}
