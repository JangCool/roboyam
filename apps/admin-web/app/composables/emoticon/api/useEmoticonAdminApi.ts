import type {
    EmoticonApproveRequest,
    EmoticonPriceChangeRequest,
    EmoticonPayoutAccountReveal,
    EmoticonSalePage,
    EmoticonSaleQuery,
    EmoticonSaleRefundResult,
    EmoticonSettlementBulkResult,
    EmoticonSettlementDetail,
    EmoticonSettlementList,
    EmoticonSettlementPaidRequest,
    EmoticonCreatorSummary,
    EmoticonCoverView,
    EmoticonItemView,
    EmoticonOfficialPackSaveRequest,
    EmoticonPackDetail,
    EmoticonPackList,
    EmoticonPackQuery,
    EmoticonReviewDetail,
    EmoticonReviewList,
    EmoticonReviewTab,
    EmoticonSettingsSaveRequest,
    EmoticonSettingsView,
} from '~/composables/emoticon/api/useEmoticonAdminApi.types';

/**
 * 로보티콘 운영 API — 서버 주소 문자열(`/api/admin/emoticons/**`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 후보: `docs/features/emoticon-contract.md` 「API 후보 — 관리자」(서버 단계에서 확정).
 *  - `GET  /api/admin/emoticons/reviews?status=PENDING|UPDATE|REJECTED|ALL` → `EmoticonReviewList`(탭 숫자 `counts` 포함). 권한 `emoticon.read`
 *  - `GET  /api/admin/emoticons/reviews/{id}` → `EmoticonReviewDetail`(새 판이면 지금 판 `current` 를 함께) · `404 EMOTICON_REVIEW_NOT_FOUND`
 *  - `POST /api/admin/emoticons/reviews/{id}/approve { price }`(0 = 무료, 유료는 가격 단계 중 하나 — `400 fields.price`) · `POST …/reject { reason }`(5~200자 — `400 fields.reason`) · 이미 처리됐으면 `409 EMOTICON_REVIEW_NOT_PENDING`. 권한 `emoticon.review`
 *  - `GET  /api/admin/emoticons/packs?source&status&q&creator` → `EmoticonPackList` · `GET …/packs/{id}` → `EmoticonPackDetail`
 *  - `POST /api/admin/emoticons/packs/{id}/suspend { reason }` · `POST …/resume` → `EmoticonPackDetail` · 상태가 안 맞으면 `409`. 권한 `emoticon.review`
 *  - 운영 팩: `POST /api/admin/emoticons/packs`(만들기) · `PUT …/packs/{id}`(이름·설명·태그·로보티콘 차례와 이름표) · `POST /api/admin/emoticons/uploads`(multipart, 그림 하나 → `EmoticonItemView`). 권한 `emoticon.review`(가정 — 따로 둘지 서버 단계에서)
 *  - `GET/PUT /api/admin/emoticons/settings` → `EmoticonSettingsView` · `400 fields.<칸>`. 권한 조회 `emoticon.read` · 저장 `emoticon.settings`
 *  - `GET  /api/admin/emoticons/creators/{memberNo}` → `EmoticonCreatorSummary | null`(회원 상세 한 줄). 권한 `emoticon.read`
 *  - 판매(유료 팩 실결제): `GET /api/admin/emoticons/sales?from&to&packId&status&page&size` → `EmoticonSalePage` · `POST …/sales/{id}/refund { reason }`(예외 환불 — 결제 취소, 이미 환불이면 `409`). 권한 `emoticon.sales`
 *  - 정산: `GET /api/admin/emoticons/settlements?month=YYYY-MM` → `EmoticonSettlementList` · `GET …/settlements/{id}` · `POST …/settlements/confirm { ids }`
 *      · `POST …/{id}/hold { reason }` · `…/unhold` · `…/paid { transferDate, memo }` 권한 `emoticon.settlement`
 *  - 정산 계좌 원문: `POST /api/admin/emoticons/settlements/{id}/account/reveal { reason }` 권한 `emoticon.payout.pii`(S4 정산 줄 단위 — 처리 기록 `ACCOUNT_VIEWED`).
 *      S2 작가 단위 `POST …/creators/{memberNo}/payout-account/reveal` 은 서버에 남아 있지만 화면은 부르지 않는다
 *
 * 실제 API(S1 — 서버 `server/admin/.../emoticon/controller/EmoticonAdminController`): 심사(목록·상세·승인·반려 — 승인·반려는 **본문 없는 204**, 부르는 쪽이 목록·상세를 다시 읽는다) ·
 * 팩(목록·상세·판매 중단·다시 판매) · 운영 팩(만들기 201·고치기·그림 올리기 201·대표 올리기 201) · 설정(조회·저장 — S2 부터 모든 칸 저장) ·
 * 작가 요약(작가가 아니면 응답 `data` 칸이 빠진다 → null). 그림 올리기 오류는 `400 VALIDATION` 의 `fields.file`·`fields.cover` 에 **이유 코드**
 * (`TYPE`·`COVER_TYPE`·`DIMENSION`·`TOO_LARGE`·`TOO_MANY_FRAMES`·`NOT_LOOP`), 문구는 `message` — 화면은 `UtilEmoticonMessages.uploadError` 로 이유 코드의 문구를 고른다.
 *
 * 실제 API(S2 — 계약 「S2 구현 결과」):
 *  - 승인 `{ price }` — 가격 없음 → 단계 밖 → 유료 판매 꺼짐 → 처음 신청이 무료 희망 → 작가 정산 정보 없음 → 지금 판 계약 동의 없음, 모두 `400 fields.price`(문구로 까닭을 가른다)
 *  - 가격 바꾸기 `POST …/packs/{id}/price { price, reason }`(`emoticon.review`) → `EmoticonPackDetail` · 사유 `400 fields.reason` → 404 → 판매 중 작가 팩이 아니면 `409 EMOTICON_PACK_NOT_ON_SALE` → `400 fields.price`
 *  - 심사 상세 `contractConsent`·`payoutRegistered`·`rules`, 팩 상세 `paidReady`·`priceEvents`(`reason`·`by` 는 null 일 수 있다)
 *  - 설정 PUT 은 모든 칸 저장(빠진 칸 `400 fields.<칸>`) · `priceTiersInUse`(판매 중단 팩 포함) · `contract`(게시본 없으면 null) · 기본값 `paidSalesEnabled=false`
 *  - 정산 계좌 원문 보기 `POST …/creators/{memberNo}/payout-account/reveal { reason }` → `EmoticonPayoutAccountReveal`(10분) · 사유 `400 fields.reason` · 등록 전 `404 EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND`
 *
 * 실제 API(S3 — 서버 `EmoticonSalesAdminController`, 권한 `emoticon.sales` 하나, `no-store`):
 *  - 판매 내역 `GET …/sales?from&to&packId&status&page&size` → `EmoticonSalePage` — 결제 완료 계열만, 최근 결제 먼저. `from`(이상)·`to`(미만) UTC ISO(틀리면 `400 fields.from|to`),
 *    `size` 1~100(기본 20). 줄의 `packName` 은 산 때 이름, `coverUrl` 은 지금 판, `creator`·`buyer.nickname` 은 프로필이 없으면 null, `refund.by` 는 운영자 이름(셀프·지워진 운영자 null),
 *    `settlementMonth` 는 그 판매가 들어간 확정된 정산 달(S4 — 확정 전이면 null)
 *  - 예외 환불 `POST …/sales/{id}/refund { reason }` → `{ sale }` — `400 fields.reason`(5~200자) → `404 EMOTICON_SALE_NOT_FOUND` → `409 EMOTICON_SALE_ALREADY_REFUNDED`.
 *    쓴 구매·기한 지난 구매도 환불한다(감사 `EMOTICON_SALE_REFUND`)
 *  - 팩 목록·상세의 `salesCount`·`salesAmount`(유료이거나 판매가 있었던 작가 팩만 — 아니면 null, 환불 뺀 값)·작가 요약 `salesAmount` 도 실제 값
 *
 * 실제 API(S4 — 서버 `EmoticonSettlementAdminController`, 권한 `emoticon.settlement`(계좌 원문만 `emoticon.payout.pii`), `no-store`):
 *  - 정산 줄 ID = `yyyyMM-작가회원번호` — 받은 값을 그대로 넘긴다
 *  - 목록 `?month=` — 달이 없거나 틀리거나 `months` 밖이면 지급을 앞둔 달(이번 달의 앞 달). `rules` 는 지금 설정, 굳힌 줄의 수수료율은 줄의 `feeRatePercent`
 *  - 상세 → 줄 + `packs` + `events` · `404 EMOTICON_SETTLEMENT_NOT_FOUND`
 *  - 확정 `{ ids }`(1~200개, 아니면 `400 fields.ids`) → `{ confirmed, skipped, skippedItems[{ id, reason }] }` — 까닭 `NOT_FOUND`·`NOT_ESTIMATED`·`NOT_CLOSED`·`PREVIOUS_OPEN`·`PAYOUT_ACCOUNT_MISSING`
 *  - 보류 `{ reason }`(5~200자 `400 fields.reason` → 404 → `409 EMOTICON_SETTLEMENT_NOT_HOLDABLE`) · 보류 풀기(`409 EMOTICON_SETTLEMENT_NOT_HELD` — **보류 전 상태로**: 굳힌 줄은 확정, 아니면 예정)
 *  - 지급 기록 `{ transferDate, memo }`(`400 fields.transferDate`(오늘 뒤 포함)·`fields.memo` → 404 → `409 EMOTICON_SETTLEMENT_NOT_CONFIRMED` → `409 EMOTICON_SETTLEMENT_NOTHING_TO_PAY`)
 *  - 보류·풀기·지급 기록은 모두 상세를 돌려준다
 */
const PATH = {
    root: '/api/admin/emoticons',
    reviews: () => `${PATH.root}/reviews`,
    review: (id: string) => `${PATH.root}/reviews/${encodeURIComponent(id)}`,
    approve: (id: string) => `${PATH.root}/reviews/${encodeURIComponent(id)}/approve`,
    reject: (id: string) => `${PATH.root}/reviews/${encodeURIComponent(id)}/reject`,
    packs: () => `${PATH.root}/packs`,
    pack: (id: string) => `${PATH.root}/packs/${encodeURIComponent(id)}`,
    suspend: (id: string) => `${PATH.root}/packs/${encodeURIComponent(id)}/suspend`,
    resume: (id: string) => `${PATH.root}/packs/${encodeURIComponent(id)}/resume`,
    price: (id: string) => `${PATH.root}/packs/${encodeURIComponent(id)}/price`,
    uploads: () => `${PATH.root}/uploads`,
    coverUploads: () => `${PATH.root}/uploads/cover`,
    settings: () => `${PATH.root}/settings`,
    creator: (memberNo: string) => `${PATH.root}/creators/${encodeURIComponent(memberNo)}`,
    sales: () => `${PATH.root}/sales`,
    saleRefund: (id: string) => `${PATH.root}/sales/${encodeURIComponent(id)}/refund`,
    settlements: () => `${PATH.root}/settlements`,
    settlement: (id: string) => `${PATH.root}/settlements/${encodeURIComponent(id)}`,
    settlementConfirm: () => `${PATH.root}/settlements/confirm`,
    settlementAction: (id: string, action: string) => `${PATH.root}/settlements/${encodeURIComponent(id)}/${action}`,
};

/** 그림 하나 올리기 본문(multipart `file`) */
function fileForm(file: File): FormData {
    const form = new FormData();
    form.append('file', file);
    return form;
}

export function useEmoticonAdminApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 심사 목록 — 탭 하나 + 탭 숫자 */
        reviews: (status: EmoticonReviewTab): Promise<EmoticonReviewList> =>
            client.get<EmoticonReviewList>(PATH.reviews(), { query: { status } }),

        /** 심사 건 상세 */
        review: (id: string): Promise<EmoticonReviewDetail> =>
            client.get<EmoticonReviewDetail>(PATH.review(id)),

        /** 승인(본문 없는 204) — 가격(원, 0 = 무료 또는 가격 단계)을 함께 정한다(카카오 방식 — 걸리면 `400 fields.price`). 처음 신청은 판매 시작, 새 판은 내용 교체(판매 중단 팩의 새 판이면 다시 판매) */
        approve: (id: string, body: EmoticonApproveRequest): Promise<void> =>
            client.post<void>(PATH.approve(id), body),

        /** 반려(본문 없는 204) — 사유는 작가에게 그대로 간다. 판매 중단 팩의 새 판 반려면 판매 중단 그대로 */
        reject: (id: string, reason: string): Promise<void> =>
            client.post<void>(PATH.reject(id), { reason }),

        /** 팩 목록 */
        packs: (query: EmoticonPackQuery = {}): Promise<EmoticonPackList> =>
            client.get<EmoticonPackList>(PATH.packs(), { query: { ...query } }),

        /** 팩 상세 */
        pack: (id: string): Promise<EmoticonPackDetail> =>
            client.get<EmoticonPackDetail>(PATH.pack(id)),

        /** 판매 중단 — 사유 5~200자(작가에게 보인다) */
        suspend: (id: string, reason: string): Promise<EmoticonPackDetail> =>
            client.post<EmoticonPackDetail>(PATH.suspend(id), { reason }),

        /** 다시 판매 */
        resume: (id: string): Promise<EmoticonPackDetail> =>
            client.post<EmoticonPackDetail>(PATH.resume(id)),

        /** 가격 바꾸기(판매 중 작가 팩 — 0 = 무료로, 그 밖은 단계 중 하나 + 사유). 이미 산 사람의 금액은 그대로, 서버가 작가에게 알린다 */
        changePrice: (id: string, body: EmoticonPriceChangeRequest): Promise<EmoticonPackDetail> =>
            client.post<EmoticonPackDetail>(PATH.price(id), body),

        /** 운영 팩 로보티콘 그림 올리기(하나씩) — 저장 전까지는 팩에 붙지 않는다 */
        upload: (file: File): Promise<EmoticonItemView> =>
            client.post<EmoticonItemView>(PATH.uploads(), fileForm(file)),

        /** 운영 팩 대표 이미지 올리기 — 200×200 PNG · 1MB(서버가 파일을 읽어 검사 — `400 fields.cover`). 저장 때 `coverId` 로 붙인다 */
        uploadCover: (file: File): Promise<EmoticonCoverView> =>
            client.post<EmoticonCoverView>(PATH.coverUploads(), fileForm(file)),

        /** 운영 팩 만들기 */
        createOfficial: (body: EmoticonOfficialPackSaveRequest): Promise<EmoticonPackDetail> =>
            client.post<EmoticonPackDetail>(PATH.packs(), body),

        /** 운영 팩 고치기 */
        saveOfficial: (id: string, body: EmoticonOfficialPackSaveRequest): Promise<EmoticonPackDetail> =>
            client.put<EmoticonPackDetail>(PATH.pack(id), body),

        /** 설정 */
        settings: (): Promise<EmoticonSettingsView> =>
            client.get<EmoticonSettingsView>(PATH.settings()),

        /** 설정 저장 */
        saveSettings: (body: EmoticonSettingsSaveRequest): Promise<EmoticonSettingsView> =>
            client.put<EmoticonSettingsView>(PATH.settings(), body),

        /** 회원 한 명의 작가 요약 — 작가가 아니면 null(서버는 `data` 칸을 빼고 준다 → `undefined` 를 null 로) */
        creator: (memberNo: string): Promise<EmoticonCreatorSummary | null> =>
            client.get<EmoticonCreatorSummary | null | undefined>(PATH.creator(memberNo)).then((summary) => summary ?? null),

        /** 판매 내역 한 쪽 — 결제 시각 내림차순 */
        sales: (query: EmoticonSaleQuery): Promise<EmoticonSalePage> => client.get<EmoticonSalePage>(PATH.sales(), { query: { ...query } }),

        /** 운영자 예외 환불 — 사유 5~200자. 서버가 결제를 취소하고 보유를 지운다 */
        refundSale: (id: string, reason: string): Promise<EmoticonSaleRefundResult> => client.post<EmoticonSaleRefundResult>(PATH.saleRefund(id), { reason }),

        /** 그 달(판매 달 `YYYY-MM`, 없으면 지급을 앞둔 달) 작가 정산 */
        settlements: (month: string | null): Promise<EmoticonSettlementList> =>
            client.get<EmoticonSettlementList>(PATH.settlements(), { query: { month: month ?? undefined } }),

        /** 정산 한 줄 상세(팩별 내역·처리 기록) */
        settlement: (id: string): Promise<EmoticonSettlementDetail> =>
            client.get<EmoticonSettlementDetail>(PATH.settlement(id)),

        /** 확정(예정 → 확정, 여러 줄) — 맞지 않는 줄은 까닭과 함께 건너뛴다 */
        confirmSettlements: (ids: string[]): Promise<EmoticonSettlementBulkResult> =>
            client.post<EmoticonSettlementBulkResult>(PATH.settlementConfirm(), { ids }),

        /** 보류(사유) */
        holdSettlement: (id: string, reason: string): Promise<EmoticonSettlementDetail> =>
            client.post<EmoticonSettlementDetail>(PATH.settlementAction(id, 'hold'), { reason }),

        /** 보류 풀기 — 보류 전 상태로 돌아간다(굳힌 줄은 확정, 아니면 예정) */
        unholdSettlement: (id: string): Promise<EmoticonSettlementDetail> =>
            client.post<EmoticonSettlementDetail>(PATH.settlementAction(id, 'unhold')),

        /** 지급 완료 기록(실제 이체는 사이트 밖) */
        markSettlementPaid: (id: string, body: EmoticonSettlementPaidRequest): Promise<EmoticonSettlementDetail> =>
            client.post<EmoticonSettlementDetail>(PATH.settlementAction(id, 'paid'), body),

        /**
         * 정산 줄의 작가 계좌 원문 보기(정산 줄 ID · 사유 5~200자) — 서버가 감사 `EMOTICON_PAYOUT_REVEAL`(덧붙임 정산 줄 ID)과 처리 기록 `ACCOUNT_VIEWED` 를 남긴다.
         * `400 fields.reason` → 줄 `404 EMOTICON_SETTLEMENT_NOT_FOUND` → 계좌 `404 EMOTICON_PAYOUT_ACCOUNT_NOT_FOUND`. 값은 화면 메모리에만 두고 `expiresAt` 에 지운다
         */
        revealSettlementAccount: (id: string, reason: string): Promise<EmoticonPayoutAccountReveal> =>
            client.post<EmoticonPayoutAccountReveal>(PATH.settlementAction(id, 'account/reveal'), { reason }),
    };
}
