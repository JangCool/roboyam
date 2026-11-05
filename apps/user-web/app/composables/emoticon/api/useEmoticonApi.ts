import { ApiError } from '~/composables/common/useApiClient';
import type {
    EmoticonAcquireResult,
    EmoticonCatalog,
    EmoticonCatalogFetch,
    EmoticonPackDetail,
    EmoticonPackList,
    EmoticonPackQuery,
    EmoticonPurchaseRequest,
    EmoticonPurchaseStart,
    EmoticonRules,
} from '~/composables/emoticon/api/useEmoticonApi.types';
import type { PaymentCompleteRequest, PaymentFakeCheckoutRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 로보티콘 공개 API(코드 이름 emoticon) — 서버 주소 문자열(`/api/emoticons/…`)이 있는 **유일한 곳**이다. 상태는 없다(카탈로그 공유는 `useEmoticonCatalog`).
 * 서버 `server/api/.../emoticon/controller/EmoticonController`(계약 `docs/features/emoticon-contract.md` 「S1 구현 결과」). 읽기는 로그인 불필요, 받기는 로그인(401)·CSRF.
 *  - `GET /api/emoticons/catalog` → `EmoticonCatalog` — `Cache-Control: public, max-age=300` + `ETag`. 가진 사본의 `ETag` 를 `If-None-Match` 로 보내 `304` 면 그 사본을 그대로 쓴다
 *  - `GET /api/emoticons/rules` → `EmoticonRules`(`no-cache`)
 *  - `GET /api/emoticons/packs?source&price&sort&q&cursor` → `EmoticonPackList`(판매 중인 팩만, 키셋 쪽 — `nextCursor`) · 잘못된 커서 `400 VALIDATION fields.cursor`
 *  - `GET /api/emoticons/packs/{id}` → `EmoticonPackDetail` · `404 EMOTICON_PACK_NOT_FOUND`
 *  - `POST /api/emoticons/packs/{id}/acquire`(무료) → `EmoticonAcquireResult` · `409 EMOTICON_PACK_SUSPENDED`·`EMOTICON_PACK_PAID`
 *  - `POST /api/emoticons/packs/{id}/purchase { agreeRefundNotice: true }`(유료 — 결제 준비, S3) → `EmoticonPurchaseStart`(결제 모듈 주문 `ROBOTIKON_PACK`, 유효 30분 · `no-store`).
 *    오류 차례: 401 → `403 ACCOUNT_RESTRICTED` → `403 EMAIL_NOT_VERIFIED` → `403 IDENTITY_REQUIRED`·`IDENTITY_UNDERAGE`(+`adultFrom`) → `400 VALIDATION fields.agreeRefundNotice`
 *    → `404 EMOTICON_PACK_NOT_FOUND` → `409 EMOTICON_PACK_SUSPENDED` → `409 EMOTICON_PACK_FREE` → `409 EMOTICON_PAID_SALES_CLOSED` → `409 EMOTICON_PACK_OWNED`(무료일 때 받았다 뺀 회원 — 받기로 다시 넣는다).
 *    다시 누르면 서버가 앞의 결제 대기를 닫는다(멱등 키 없음).
 *  - 그 뒤 결제창 공용 흐름 — 가짜 결제창 결과 `usePaymentTestApi().fakeCheckout`(가짜 게이트웨이 전용) · 완료 공용 `usePaymentApi().complete`(`POST /api/payments/{paymentId}/complete`).
 *    도메인 거절(그새 가졌음·판매 중 아님·그새 무료가 됨)은 결과 `anomaly: DOMAIN_REJECTED` + 자동 취소 — 주소는 각 파일에 있다.
 */
const PATH = {
    catalog: '/api/emoticons/catalog',
    rules: '/api/emoticons/rules',
    packs: '/api/emoticons/packs',
    pack: (id: string) => `${PATH.packs}/${encodeURIComponent(id)}`,
    acquire: (id: string) => `${PATH.pack(id)}/acquire`,
    purchase: (id: string) => `${PATH.pack(id)}/purchase`,
};

export function useEmoticonApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const paymentApi = usePaymentApi();
    const testApi = usePaymentTestApi();

    return {
        /**
         * 카탈로그 — 가진 사본의 `etag` 를 주면 조건부로 묻는다. 바뀌지 않았으면(`304`) `catalog: null`(가진 사본을 그대로 쓴다).
         * @param etag - 가진 사본의 `ETag`(없으면 null — 처음 받기)
         */
        catalog: async (etag: string | null = null): Promise<EmoticonCatalogFetch> => {
            let next: string | null = null;
            try {
                const catalog = await client.get<EmoticonCatalog>(PATH.catalog, {
                    headers: etag ? { 'If-None-Match': etag } : undefined,
                    onHeaders: (get) => {
                        next = get('etag');
                    },
                });
                return { catalog, etag: next };
            } catch (e) {
                if (etag && e instanceof ApiError && e.status === 304) return { catalog: null, etag };
                throw e;
            }
        },

        /** 규격·보상 설정(가격·수수료·정산·환불 칸은 S2~S4 기본값) */
        rules: (): Promise<EmoticonRules> => client.get<EmoticonRules>(PATH.rules),

        /** 상점 목록 — 빈 거르기 값은 보내지 않는다 */
        packs: (query: EmoticonPackQuery = {}): Promise<EmoticonPackList> =>
            client.get<EmoticonPackList>(PATH.packs, { query: { source: query.source, price: query.price, sort: query.sort, q: query.q?.trim() || undefined, cursor: query.cursor || undefined } }),

        /** 팩 하나 */
        pack: (id: string): Promise<EmoticonPackDetail> => client.get<EmoticonPackDetail>(PATH.pack(id)),

        /** 받기(무료 팩, 멱등) */
        acquire: (id: string): Promise<EmoticonAcquireResult> => client.post<EmoticonAcquireResult>(PATH.acquire(id)),

        /** 구매 — 결제 준비(유료 팩, S3) */
        purchase: (id: string, body: EmoticonPurchaseRequest): Promise<EmoticonPurchaseStart> =>
            client.post<EmoticonPurchaseStart>(PATH.purchase(id), body),

        /** (결제창 공용 흐름) 가짜 결제창 결과 — 실제는 가짜 게이트웨이의 `fake-checkout` */
        fakeCheckout: (paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> =>
            testApi.fakeCheckout(paymentId, body),

        /** (결제창 공용 흐름) 완료 요청 — 실제는 공용 결제 완료 API */
        completePayment: (paymentId: string, body: PaymentCompleteRequest): Promise<PaymentResult> =>
            paymentApi.complete(paymentId, body),
    };
}
