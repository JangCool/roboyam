import type {
    GroupBuyCancelPreview,
    GroupBuyContentRequest,
    GroupBuyDelayNoticeRequest,
    GroupBuyDetail,
    GroupBuyFormOptions,
    GroupBuyImageUpload,
    GroupBuyListResponse,
    GroupBuyMovement,
    GroupBuyMovementQuery,
    GroupBuyPage,
    GroupBuyQuery,
    GroupBuyRevision,
    GroupBuyRoundCreated,
    GroupBuySaveRequest,
    GroupBuyStockAdjustRequest,
    GroupBuyStockView,
    GroupBuySupplyFailurePreview,
    GroupBuySupplyFailureRequest,
    NoticeCatalog,
} from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';

/**
 * 공구 관리자 API(공구·수량·운영 동작) — 서버 주소 문자열(`/api/admin/group-buys…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약: `docs/features/commerce-groupbuy-contract.md`(2b-1 화면 목업이 정한 모양 — 서버는 2b-2·2b-3). 응답 `Cache-Control: no-store`.
 *
 * 권한(서버 `@RequirePermission` 후보): 조회 `commerce.product.read`(없으면 `commerce.product.manage`) · 등록·수정·모집 시작·조기 마감·추가 차수·발송 지연 공지 `commerce.product.manage`
 * · 가격·배송비 칸(DRAFT) `commerce.product.price` · 중단 `commerce.groupbuy.cancel` · 추가 입고·줄이기 `commerce.stock.adjust` · 발주 처리 `commerce.order.manage`
 * · 단품 공급 불가 `commerce.order.manage` + `commerce.refund.approve`(환불이 나간다). 없으면 `403 PERMISSION_DENIED`.
 *   - `GET    /api/admin/group-buys?status=&q=&page=&size=` → `GroupBuyListResponse`(탭 건수 `counts`)
 *   - `GET    /api/admin/group-buys/form-options` → `GroupBuyFormOptions`(배송비 정책 고르기 · 기본값)
 *   - `POST   /api/admin/group-buys` · `PUT /api/admin/group-buys/{id}`(DRAFT 만) → `GroupBuyDetail` · `400 VALIDATION fields.*` · `409 GROUP_BUY_LOCKED_FIELD`·`GROUP_BUY_VERSION_CONFLICT`
 *   - 옵션 축·단품(결정 88 — 서버 2b-2 `GroupBuyDraft` 모양): 저장 `axes[{name, values: string[]}]`(0~3, 값 1~30, 조합 ≤ 200) · `skus[{values, extraPrice, maxQty, onSale}]`
 *     (적지 않은 조합 = 추가금 0·한도 없음·판매, 판매 조합 1개 이상) · `addons[{name, price, maxQty, imageKey}]`(≤ 20, 조합 이름과도 겹치면 안 됨). `400 VALIDATION fields.axes|skus|addons`.
 *     모집 뒤 구성 변경은 `409 GROUP_BUY_LOCKED_FIELD`(field `options`) — 한도는 수량 화면(추가 입고·줄이기, `skuId`)에서 단품 단위로.
 *     회원 주문 쪽 단품 오류(관리자 화면은 쓰지 않는다): `GROUP_BUY_SKU_NOT_ON_SALE` · `GROUP_BUY_ADDON_ONLY {skuIds}` · `GROUP_BUY_SKU_SOLD_OUT {skuId, temporary}`
 *   - `PATCH  /api/admin/group-buys/{id}/content`(모집 뒤 — 공급가만 `supplyPrices`, 결정 89·2b-2) → `GroupBuyDetail` · 역마진 `409 GROUP_BUY_NEGATIVE_MARGIN {skuIds}` → `marginConfirmed`
 *   - 결정 89(2b-2 1d5b48ab): 저장 `GroupBuyDraft` = 상품 `productId` + 판매 조건 + `skus[{productSkuId, extraPrice, maxQty, onSale, supplyPrice}]` · `addons[{productSkuId, price, maxQty, supplyPrice}]` · `marginConfirmed`. 설명·이미지·고시·정상가·옵션 축은 상품 API
 *   - `GET    /api/admin/group-buys/notice-catalog` → `NoticeCatalog`(상품정보제공고시 품목 40개·항목 — 서버 데이터 파일이 정본, 결정 87)
 *   - 고시 양식: 저장 `400 VALIDATION fields.notices[i].title|categoryCode|skuNames(추가 구성상품을 섞음 포함)|values[j].unavailableReason|values[j].value|values[j].label`(label = 판매자 추가 항목, 2b-2 확정 전 가정) · 모집 시작·모집 뒤 저장 `409 GROUP_BUY_NOTICE_INCOMPLETE`(extras = `GroupBuyNoticeIncomplete`)
 *   - `POST   /api/admin/group-buys/images`(multipart `file`) → `GroupBuyImageUpload`
 *   - `GET    /api/admin/group-buys/{id}` · `…/revisions?page=&size=`
 *   - `POST   /api/admin/group-buys/{id}/start` · `…/close-early {reason}` · `…/cancel {reason}`(+ `GET …/cancel-preview`) · `…/ordered` · `…/rounds`
 *   - `POST   /api/admin/group-buys/{id}/delay-notices {newPlannedAt, reason}` · `GET …/supply-failure-preview?skuId=` · `POST …/supply-failures {skuId, reason}`
 *   - `GET    /api/admin/group-buys/{id}/stock` · `…/stock/movements?kind=&page=&size=` · `POST …/stock/adjust` · `409 GROUP_BUY_QTY_BELOW_FLOOR(extras.floor)`·`GROUP_BUY_NOT_RECRUITING`·`GROUP_BUY_SKU_UNLIMITED`·`GROUP_BUY_SKU_NOT_ON_SALE`
 *
 */
const PATH = {
    root: '/api/admin/group-buys',
    formOptions: () => `${PATH.root}/form-options`,
    noticeCatalog: () => `${PATH.root}/notice-catalog`,
    images: () => `${PATH.root}/images`,
    one: (id: string) => `${PATH.root}/${encodeURIComponent(id)}`,
    sub: (id: string, tail: string) => `${PATH.one(id)}/${tail}`,
};

export function useGroupBuyAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 공구 목록 */
        list: (query: GroupBuyQuery): Promise<GroupBuyListResponse> =>
            client.get<GroupBuyListResponse>(PATH.root, { query: { ...query } }),

        /** 등록 화면의 고를 값 */
        formOptions: (): Promise<GroupBuyFormOptions> =>
            client.get<GroupBuyFormOptions>(PATH.formOptions()),

        /** 상품정보제공고시 카탈로그(품목·항목) */
        noticeCatalog: (): Promise<NoticeCatalog> =>
            client.get<NoticeCatalog>(PATH.noticeCatalog()),

        /** 공구 상세 */
        detail: (id: string): Promise<GroupBuyDetail> =>
            client.get<GroupBuyDetail>(PATH.one(id)),

        /** 새 공구(DRAFT) */
        create: (body: GroupBuySaveRequest): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.root, body),

        /** DRAFT 전부 고치기 */
        update: (id: string, body: GroupBuySaveRequest): Promise<GroupBuyDetail> =>
            client.put<GroupBuyDetail>(PATH.one(id), body),

        /** 모집 뒤 설명·이미지·고시 양식만 고치기 */
        updateContent: (id: string, body: GroupBuyContentRequest): Promise<GroupBuyDetail> =>
            client.patch<GroupBuyDetail>(PATH.sub(id, 'content'), body),

        /** 이미지 한 장 올리기(대표 이미지·설명 본문 공용) */
        uploadImage: (file: File): Promise<GroupBuyImageUpload> =>
            client.post<GroupBuyImageUpload>(PATH.images(), (() => { const form = new FormData(); form.append('file', file); return form; })()),

        /** 수정 이력 */
        revisions: (id: string, page: number, size: number): Promise<GroupBuyPage<GroupBuyRevision>> =>
            client.get<GroupBuyPage<GroupBuyRevision>>(PATH.sub(id, 'revisions'), { query: { page, size } }),

        /** 모집 시작(공개) — 배송비 정책 사본 고정 */
        /** 모집 시작 — 역마진이면 `409 GROUP_BUY_NEGATIVE_MARGIN` 뒤 `marginConfirmed` 로 다시(목업 가정: 시작에도 같은 확인), 공급가 빈 값 `409 GROUP_BUY_SUPPLY_PRICE_MISSING {skuIds}` */
        start: (id: string, marginConfirmed = false): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.sub(id, 'start'), { marginConfirmed }),

        /** 조기 마감 */
        closeEarly: (id: string, reason: string): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.sub(id, 'close-early'), { reason }),

        /** 중단 확인 창 숫자 */
        cancelPreview: (id: string): Promise<GroupBuyCancelPreview> =>
            client.get<GroupBuyCancelPreview>(PATH.sub(id, 'cancel-preview')),

        /** 중단 — 발송 전 주문 전액 환불 */
        cancel: (id: string, reason: string): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.sub(id, 'cancel'), { reason }),

        /** 공구 단위 발주 처리(CLOSED_SUCCESS → ORDERED) */
        markOrdered: (id: string): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.sub(id, 'ordered')),

        /** 추가 차수로 열기 — 새 DRAFT */
        createRound: (id: string): Promise<GroupBuyRoundCreated> =>
            client.post<GroupBuyRoundCreated>(PATH.sub(id, 'rounds')),

        /** 발송 지연 공지 */
        delayNotice: (id: string, body: GroupBuyDelayNoticeRequest): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.sub(id, 'delay-notices'), body),

        /** 단품 공급 불가 결과 미리 보기 */
        supplyFailurePreview: (id: string, skuId: string): Promise<GroupBuySupplyFailurePreview> =>
            client.get<GroupBuySupplyFailurePreview>(PATH.sub(id, 'supply-failure-preview'), { query: { skuId } }),

        /** 단품 공급 불가 처리 */
        supplyFailure: (id: string, body: GroupBuySupplyFailureRequest): Promise<GroupBuyDetail> =>
            client.post<GroupBuyDetail>(PATH.sub(id, 'supply-failures'), body),

        /** 수량 화면 */
        stock: (id: string): Promise<GroupBuyStockView> =>
            client.get<GroupBuyStockView>(PATH.sub(id, 'stock')),

        /** 수량 변동 내역 */
        movements: (id: string, query: GroupBuyMovementQuery): Promise<GroupBuyPage<GroupBuyMovement>> =>
            client.get<GroupBuyPage<GroupBuyMovement>>(PATH.sub(id, 'stock/movements'), { query: { ...query } }),

        /** 추가 입고·줄이기 */
        adjustStock: (id: string, body: GroupBuyStockAdjustRequest): Promise<GroupBuyStockView> =>
            client.post<GroupBuyStockView>(PATH.sub(id, 'stock/adjust'), body),
    };
}
