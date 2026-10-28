import {
    mockGroupBuyCancel,
    mockGroupBuyCancelPreview,
    mockGroupBuyCloseEarly,
    mockGroupBuyContent,
    mockGroupBuyCreate,
    mockGroupBuyCreateRound,
    mockGroupBuyDelayNotice,
    mockGroupBuyDetail,
    mockGroupBuyFormOptions,
    mockGroupBuyImageUpload,
    mockGroupBuyList,
    mockGroupBuyMarkOrdered,
    mockGroupBuyMovements,
    mockGroupBuyNoticeCatalog,
    mockGroupBuyRevisions,
    mockGroupBuyStart,
    mockGroupBuyStock,
    mockGroupBuyStockAdjust,
    mockGroupBuySupplyFailure,
    mockGroupBuySupplyPreview,
    mockGroupBuyUpdate,
} from '~/composables/groupbuy/api/mock/groupbuy-mock';
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
 * · 옵션 공급 불가 `commerce.order.manage` + `commerce.refund.approve`(환불이 나간다). 없으면 `403 PERMISSION_DENIED`.
 *   - `GET    /api/admin/group-buys?status=&q=&page=&size=` → `GroupBuyListResponse`(탭 건수 `counts`)
 *   - `GET    /api/admin/group-buys/form-options` → `GroupBuyFormOptions`(배송비 정책 고르기 · 기본값)
 *   - `POST   /api/admin/group-buys` · `PUT /api/admin/group-buys/{id}`(DRAFT 만) → `GroupBuyDetail` · `400 VALIDATION fields.*` · `409 GROUP_BUY_LOCKED_FIELD`·`GROUP_BUY_VERSION_CONFLICT`
 *   - `PATCH  /api/admin/group-buys/{id}/content`(모집 뒤 설명·이미지 + 고시 양식 `notices`) → `GroupBuyDetail` · 수정 이력 한 판(고시만 바뀌면 `NOTICE`)
 *   - `GET    /api/admin/group-buys/notice-catalog` → `NoticeCatalog`(상품정보제공고시 품목 40개·항목 — 서버 데이터 파일이 정본, 결정 87)
 *   - 고시 양식: 저장 `400 VALIDATION fields.notices[i].title|categoryCode|optionNames|values[j].unavailableReason|values[j].value|values[j].label`(label = 판매자 추가 항목, 2b-2 확정 전 가정) · 모집 시작·모집 뒤 저장 `409 GROUP_BUY_NOTICE_INCOMPLETE`(extras = `GroupBuyNoticeIncomplete`)
 *   - `POST   /api/admin/group-buys/images`(multipart `file`) → `GroupBuyImageUpload`
 *   - `GET    /api/admin/group-buys/{id}` · `…/revisions?page=&size=`
 *   - `POST   /api/admin/group-buys/{id}/start` · `…/close-early {reason}` · `…/cancel {reason}`(+ `GET …/cancel-preview`) · `…/ordered` · `…/rounds`
 *   - `POST   /api/admin/group-buys/{id}/delay-notices {newPlannedAt, reason}` · `GET …/supply-failure-preview?optionId=` · `POST …/supply-failures {optionId, reason}`
 *   - `GET    /api/admin/group-buys/{id}/stock` · `…/stock/movements?kind=&page=&size=` · `POST …/stock/adjust` · `409 GROUP_BUY_QTY_BELOW_FLOOR(extras.floor)`·`GROUP_BUY_NOT_RECRUITING`
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/groupbuy-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
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
    // REAL: const auth = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => auth.me() });
    void PATH;

    return {
        /** 공구 목록 */
        list: (query: GroupBuyQuery): Promise<GroupBuyListResponse> =>
            // MOCK:
            mockGroupBuyList(query),
        // REAL: client.get<GroupBuyListResponse>(PATH.root, { query: { ...query } }),

        /** 등록 화면의 고를 값 */
        formOptions: (): Promise<GroupBuyFormOptions> =>
            // MOCK:
            mockGroupBuyFormOptions(),
        // REAL: client.get<GroupBuyFormOptions>(PATH.formOptions()),

        /** 상품정보제공고시 카탈로그(품목·항목) */
        noticeCatalog: (): Promise<NoticeCatalog> =>
            // MOCK:
            mockGroupBuyNoticeCatalog(),
        // REAL: client.get<NoticeCatalog>(PATH.noticeCatalog()),

        /** 공구 상세 */
        detail: (id: string): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyDetail(id),
        // REAL: client.get<GroupBuyDetail>(PATH.one(id)),

        /** 새 공구(DRAFT) */
        create: (body: GroupBuySaveRequest): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyCreate(body),
        // REAL: client.post<GroupBuyDetail>(PATH.root, body),

        /** DRAFT 전부 고치기 */
        update: (id: string, body: GroupBuySaveRequest): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyUpdate(id, body),
        // REAL: client.put<GroupBuyDetail>(PATH.one(id), body),

        /** 모집 뒤 설명·이미지·고시 양식만 고치기 */
        updateContent: (id: string, body: GroupBuyContentRequest): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyContent(id, body),
        // REAL: client.patch<GroupBuyDetail>(PATH.sub(id, 'content'), body),

        /** 이미지 한 장 올리기(대표 이미지·설명 본문 공용) */
        uploadImage: (file: File): Promise<GroupBuyImageUpload> =>
            // MOCK:
            mockGroupBuyImageUpload(file),
        // REAL: client.post<GroupBuyImageUpload>(PATH.images(), (() => { const form = new FormData(); form.append('file', file); return form; })()),

        /** 수정 이력 */
        revisions: (id: string, page: number, size: number): Promise<GroupBuyPage<GroupBuyRevision>> =>
            // MOCK:
            mockGroupBuyRevisions(id, page, size),
        // REAL: client.get<GroupBuyPage<GroupBuyRevision>>(PATH.sub(id, 'revisions'), { query: { page, size } }),

        /** 모집 시작(공개) — 배송비 정책 사본 고정 */
        start: (id: string): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyStart(id),
        // REAL: client.post<GroupBuyDetail>(PATH.sub(id, 'start')),

        /** 조기 마감 */
        closeEarly: (id: string, reason: string): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyCloseEarly(id, reason),
        // REAL: client.post<GroupBuyDetail>(PATH.sub(id, 'close-early'), { reason }),

        /** 중단 확인 창 숫자 */
        cancelPreview: (id: string): Promise<GroupBuyCancelPreview> =>
            // MOCK:
            mockGroupBuyCancelPreview(id),
        // REAL: client.get<GroupBuyCancelPreview>(PATH.sub(id, 'cancel-preview')),

        /** 중단 — 발송 전 주문 전액 환불 */
        cancel: (id: string, reason: string): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyCancel(id, reason),
        // REAL: client.post<GroupBuyDetail>(PATH.sub(id, 'cancel'), { reason }),

        /** 공구 단위 발주 처리(CLOSED_SUCCESS → ORDERED) */
        markOrdered: (id: string): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyMarkOrdered(id),
        // REAL: client.post<GroupBuyDetail>(PATH.sub(id, 'ordered')),

        /** 추가 차수로 열기 — 새 DRAFT */
        createRound: (id: string): Promise<GroupBuyRoundCreated> =>
            // MOCK:
            mockGroupBuyCreateRound(id),
        // REAL: client.post<GroupBuyRoundCreated>(PATH.sub(id, 'rounds')),

        /** 발송 지연 공지 */
        delayNotice: (id: string, body: GroupBuyDelayNoticeRequest): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuyDelayNotice(id, body),
        // REAL: client.post<GroupBuyDetail>(PATH.sub(id, 'delay-notices'), body),

        /** 옵션 공급 불가 결과 미리 보기 */
        supplyFailurePreview: (id: string, optionId: string): Promise<GroupBuySupplyFailurePreview> =>
            // MOCK:
            mockGroupBuySupplyPreview(id, optionId),
        // REAL: client.get<GroupBuySupplyFailurePreview>(PATH.sub(id, 'supply-failure-preview'), { query: { optionId } }),

        /** 옵션 공급 불가 처리 */
        supplyFailure: (id: string, body: GroupBuySupplyFailureRequest): Promise<GroupBuyDetail> =>
            // MOCK:
            mockGroupBuySupplyFailure(id, body),
        // REAL: client.post<GroupBuyDetail>(PATH.sub(id, 'supply-failures'), body),

        /** 수량 화면 */
        stock: (id: string): Promise<GroupBuyStockView> =>
            // MOCK:
            mockGroupBuyStock(id),
        // REAL: client.get<GroupBuyStockView>(PATH.sub(id, 'stock')),

        /** 수량 변동 내역 */
        movements: (id: string, query: GroupBuyMovementQuery): Promise<GroupBuyPage<GroupBuyMovement>> =>
            // MOCK:
            mockGroupBuyMovements(id, query),
        // REAL: client.get<GroupBuyPage<GroupBuyMovement>>(PATH.sub(id, 'stock/movements'), { query: { ...query } }),

        /** 추가 입고·줄이기 */
        adjustStock: (id: string, body: GroupBuyStockAdjustRequest): Promise<GroupBuyStockView> =>
            // MOCK:
            mockGroupBuyStockAdjust(id, body),
        // REAL: client.post<GroupBuyStockView>(PATH.sub(id, 'stock/adjust'), body),
    };
}
