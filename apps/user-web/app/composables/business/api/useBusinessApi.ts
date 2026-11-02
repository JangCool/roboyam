import { mockAdStats, mockAdStatsSummary } from '~/composables/business/api/mock/ad-stats-mock';
import type {
    AdApplicationStats,
    AdApplyRequest,
    AdProduct,
    AvailabilityDay,
    AvailabilitySlot,
    BizAdStatsSummary,
    BizApplicationBucket,
    BizApplicationCreated,
    BizApplicationDetail,
    BizApplicationPage,
    BizCreativeRequest,
    BizDealPost,
    BizPaymentStart,
    BizRefundPolicy,
    BusinessApplyRequest,
    BusinessFile,
    BusinessProfile,
    DealApplyRequest,
    DealLinkCheck,
    DealProduct,
} from '~/composables/business/api/useBusinessApi.types';
import type { PaymentCompleteRequest, PaymentFakeCheckoutRequest, PaymentResult } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 사업자 센터 API — 서버 주소 문자열(`/api/business/…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 필요(401).
 * 요구사항 정본 `docs/plans/biz-ad-hotdeal/00-overview.md` 「★ 확정」(사전 심사 → 결제, 온라인/수동 결제, 신청 건 상태). 계약 `docs/features/business-contract.md`·`biz-application-contract.md`.
 *  - 신원(V111): `GET/POST/PUT /api/business/me` · 사업자등록증 사본 `POST /api/business/files`(multipart, PDF·JPG·PNG 10MB) — 409 `BUSINESS_DUPLICATE`·`BUSINESS_ALREADY_APPLIED`·
 *    `BUSINESS_NOT_EDITABLE`, 429 `BUSINESS_REAPPLY_LIMIT`, 400 `fields.*` · 국세청 진위확인 불일치 400 `BUSINESS_NTS_MISMATCH`(`fields.companyName`·`ownerName`·`openedOn` — 화면 문구는 화면 i18n)
 *  - 상품·남은 칸(V112): `GET /api/business/ad-products` · `GET /api/business/deal-products` · `GET /api/business/availability?slot=&from=&to=`(92일까지) · `GET /api/business/deal-link-check?url=`
 *  - 광고 이미지: `POST /api/business/ad-images`(multipart `file`, JPG·PNG·GIF·WEBP 10MB — 등록증 `/files` 와 따로) — 400 `fields.file`, 413 `FILE_TOO_LARGE`
 *  - 신청 건: `POST /api/business/applications/{ads|deals}`(201 `{ id }`) · `GET /api/business/applications[?bucket=]` · `GET …/{id}` · `POST …/{id}/cancel` ·
 *    `PUT …/{id}/creative`(소재 바꾸기 → 결제 전이면 다시 심사) · `POST …/{id}/refund-request` `{ reason }`(1~300자)
 *  - 결제(온라인): `POST …/{id}/payment` → 결제창 정보(`payment` 모듈 `BIZ_APPLICATION`) — 완료는 공용 결제 완료 API(`usePaymentCheckout`) 뒤 상세를 다시 읽는다
 *  - 409 `BUSINESS_NOT_APPROVED`·`BUSINESS_RE_REVIEW`·`BIZ_SLOT_FULL`·`BIZ_PRODUCT_CLOSED`·`BIZ_APPLICATION_STATE_CHANGED`(화면이 다시 읽는다) · 404 `BIZ_APPLICATION_NOT_FOUND`
 *  - 사업자 핫딜 게시(2차 — `biz-hotdeal-contract.md`): `deal-products` 에 `boards`, 신청 본문 `deal.boardId`·`prefixId`·`imageFileId`·`stock`, 상세 `post`(`BizDealPost`) ·
 *    칸 오류 `fields."deal.boardId"`·`"deal.prefixId"`·`"deal.linkUrl"`(형식·차단·단축 주소)·`"deal.listPrice"`·`"deal.stock"`·`"deal.imageFileId"` ·
 *    사업자 종료 `POST …/{id}/deal/end` → 새 `BizDealPost`(환불 없음 · 노출도 멈춤) — 이미 끝남 409 `DEAL_ENDED` · 글이 없음(게시 전·실패·숨김) 409 `BIZ_APPLICATION_STATE_CHANGED`
 *  - 일시 정지·중단(광고주 요청 — 서버 연결 2026-11-02, `biz-pause-contract.md`): 진행 중(시작 뒤·끝 전, 핫딜 글은 게시되고 진행 중·의심) 건만.
 *    `POST …/{id}/pause`(바로 멈춤 · 자리 유지 · 한 건에 3번) · `POST …/{id}/resume`(승인 없이 바로 재개 · 끝 시각 그대로) · `POST …/{id}/stop` `{ reason }`(1~300자 — 바로 내림 · 되돌리기 없음 ·
 *    일할 − 중도 해지 수수료 환불 `STOP_PRORATED` 심사, 예상액 0원이면 환불 줄 없음) — 셋 다 → `BizApplicationDetail`(`takedownCause`·`pause`). 정지 = `TAKEN_DOWN` + `ADVERTISER_PAUSE`, 중단 = `TAKEN_DOWN` + `ADVERTISER_STOP`(승인 → `CANCELED`).
 *    진행 중 `refund-request` 도 서버가 중단과 같이 처리한다(화면은 [중단하고 환불 요청] = `stop`).
 *    오류: 횟수 초과 409 `BIZ_PAUSE_LIMIT` · 이미 끝남/상태 바뀜 409 `BIZ_APPLICATION_STATE_CHANGED`(알리고 다시 읽는다) · 정지된 사업자가 다시 켜기 409 `BUSINESS_NOT_APPROVED`
 *  - 서비스별 정지(2026-11-02): `GET /api/business/me` 의 `services[]`(`BusinessProfileService` — 서비스·상태·정지 사유, 신청 전이면 빈 목록 · 순서에 기대지 않는다).
 *    정지된 서비스로 새 신청하면 409 `BUSINESS_SERVICE_SUSPENDED`(문구는 서버 몫) — 신청 화면은 신원을 다시 읽어 막힘 안내를 보인다.
 *  - 광고 성과(목업 2026-11-02 — 서버 전, 장부는 실제 · 숫자만 `./mock/ad-stats-mock.ts`): `GET …/{id}/stats?from&to` → `AdApplicationStats`(본인 건만, 없으면 404) ·
 *    `GET /api/business/ads/stats-summary` → `BizAdStatsSummary`(진행 중·정지 중·내려진 건(광고주 중단 제외) 최대 5건 · 최근 7일). ⚠ MOCK: 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 *  - 환불 규정:`GET /api/business/refund-policy` → `{ version, stopFeeRate }`(신청 화면 동의 문구) · 신청 본문의 규정 버전이 지금과 다르면 400 `fields.agreedRefundPolicyVersion`
 */
const PATH = {
    me: '/api/business/me',
    files: '/api/business/files',
    adImages: '/api/business/ad-images',
    adProducts: '/api/business/ad-products',
    dealProducts: '/api/business/deal-products',
    availability: '/api/business/availability',
    dealLinkCheck: '/api/business/deal-link-check',
    applications: '/api/business/applications',
    refundPolicy: '/api/business/refund-policy',
    adStatsSummary: '/api/business/ads/stats-summary',
    application: (id: string) => `/api/business/applications/${encodeURIComponent(id)}`,
};

export function useBusinessApi() {
    const client = useApiClient();
    const paymentApi = usePaymentApi();
    const testApi = usePaymentTestApi();

    return {
        /** 내 사업자 신원 */
        profile: (): Promise<BusinessProfile> => client.get<BusinessProfile>(PATH.me),

        /** 사업자 신청(새로·반려 뒤 다시) — 심사 대기가 된다. 번호·상호·대표자명·개업일이 국세청 정보와 다르면 400 `BUSINESS_NTS_MISMATCH` */
        apply: (body: BusinessApplyRequest): Promise<BusinessProfile> => client.post<BusinessProfile>(PATH.me, body),

        /** 사업자 정보 수정 — 상호·번호를 바꾸면 다시 심사(F7). 번호·상호·대표자명·개업일 중 하나라도 바뀌면 진위확인을 다시 한다 */
        update: (body: BusinessApplyRequest): Promise<BusinessProfile> => client.put<BusinessProfile>(PATH.me, body),

        /** 사업자등록증 사본 올리기(PDF·JPG·PNG 10MB) — 신청·수정이 `certificateFileId` 로 붙인다 */
        uploadCertificate: (file: File): Promise<BusinessFile> => client.upload<BusinessFile>(PATH.files, ((f) => (f.append('file', file), f))(new FormData())),

        /** 광고 이미지 올리기(JPG·PNG·GIF·WEBP 10MB) — 신청·소재 바꾸기가 `imageFileId` 로 붙인다 */
        uploadFile: (file: File): Promise<BusinessFile> => client.upload<BusinessFile>(PATH.adImages, ((f) => (f.append('file', file), f))(new FormData())),

        /** 광고 종류별 단가·한도·남은 칸 요약 */
        adProducts: (): Promise<AdProduct[]> => client.get<AdProduct[]>(PATH.adProducts),

        /** 사업자 핫딜 요금 · 받는 게시판 */
        dealProduct: (): Promise<DealProduct> => client.get<DealProduct>(PATH.dealProducts),

        /** 날짜별 남은 칸 — 저장 때 서버가 다시 판정한다 */
        availability: (slot: AvailabilitySlot, from: string, to: string): Promise<AvailabilityDay[]> => client.get<AvailabilityDay[]>(PATH.availability, { query: { slot, from, to } }),

        /** 핫딜 상품 링크 검사 — 쇼핑몰 이름·제휴·차단 */
        checkDealLink: (url: string): Promise<DealLinkCheck> => client.get<DealLinkCheck>(PATH.dealLinkCheck, { query: { url } }),

        /** 광고 심사 신청 — 지금 결제하지 않는다(승인 뒤 결제) */
        submitAd: (body: AdApplyRequest): Promise<BizApplicationCreated> => client.post<BizApplicationCreated>(`${PATH.applications}/ads`, body),

        /** 사업자 핫딜 심사 신청 */
        submitDeal: (body: DealApplyRequest): Promise<BizApplicationCreated> => client.post<BizApplicationCreated>(`${PATH.applications}/deals`, body),

        /** 내 신청 건 목록 */
        applications: (bucket: BizApplicationBucket): Promise<BizApplicationPage> => client.get<BizApplicationPage>(PATH.applications, { query: { bucket } }),

        /** 신청 건 상세 */
        application: (id: string): Promise<BizApplicationDetail> => client.get<BizApplicationDetail>(PATH.application(id)),

        /** 결제 전 취소(심사 중·결제 대기·입금 확인 전 입금 대기) */
        cancel: (id: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(`${PATH.application(id)}/cancel`),

        /** 사업자 핫딜 종료(게시된 글 — 품절 등) — 환불 없음, 노출 자리도 같이 멈춘다. 이미 끝났으면 409 `DEAL_ENDED` */
        endDeal: (id: string): Promise<BizDealPost> => client.post<BizDealPost>(`${PATH.application(id)}/deal/end`),

        /** 소재 바꾸기 — 심사 중이면 새 버전, 승인 뒤 결제 전이면 다시 심사(자리 반납) */
        updateCreative: (id: string, body: BizCreativeRequest): Promise<BizApplicationDetail> => client.put<BizApplicationDetail>(`${PATH.application(id)}/creative`, body),

        /** 결제 시작 — 결제창 정보(열린 결제가 1분 넘게 남았으면 같은 것) */
        startPayment: (id: string): Promise<BizPaymentStart> => client.post<BizPaymentStart>(`${PATH.application(id)}/payment`),

        /** (결제창 공용 흐름) 가짜 결제창 결과 — 가짜 게이트웨이의 `fake-checkout` */
        fakeCheckout: (paymentId: string, body: PaymentFakeCheckoutRequest): Promise<void> => testApi.fakeCheckout(paymentId, body),

        /** (결제창 공용 흐름) 완료 요청 — 공용 결제 완료 API. 끝나면 화면이 상세를 다시 읽는다 */
        completePayment: (paymentId: string, body: PaymentCompleteRequest): Promise<PaymentResult> => paymentApi.complete(paymentId, body),

        /** 취소·환불 요청(예약·진행 중, 한 번만) */
        refundRequest: (id: string, reason: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(`${PATH.application(id)}/refund-request`, { reason }),

        /** 일시 정지(진행 중 — 바로 멈춤, 자리 유지, 한 건에 3번) — 다 썼으면 409 `BIZ_PAUSE_LIMIT` */
        pause: (id: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(`${PATH.application(id)}/pause`),

        /** 다시 켜기(정지 중 · 끝 시각 전) — 승인 없이 지금 승인된 소재로 바로 재개, 기간은 늘지 않는다 */
        resume: (id: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(`${PATH.application(id)}/resume`),

        /** 지금 환불 규정 버전 + 중도 해지 수수료율(신청 화면 동의 문구) */
        refundPolicy: (): Promise<BizRefundPolicy> => client.get<BizRefundPolicy>(PATH.refundPolicy),

        /** 중단하고 환불 요청(진행 중·정지 중) — 바로 내려가고 되돌릴 수 없다. 남은 기간 일할(정지 중이면 멈춘 시각부터) − 중도 해지 수수료를 운영팀이 확인 */
        stop: (id: string, reason: string): Promise<BizApplicationDetail> => client.post<BizApplicationDetail>(`${PATH.application(id)}/stop`, { reason }),

        /** 한 건의 광고 성과(노출·클릭 — 날짜·자리·소재 버전별, 정지·내림 구간) — 기간을 빼면 서버 기본(시작 날짜 ~ min(끝 날짜, 오늘)) */
        stats: (id: string, range?: { from: string; to: string }): Promise<AdApplicationStats> =>
            // MOCK: 실제 상세를 받아 종류·자리를 보고 숫자만 만든다
            mockAdStats(id, range ? { from: range.from, to: range.to } : undefined, () => client.get<BizApplicationDetail>(PATH.application(id))),
        // REAL: client.get<AdApplicationStats>(`${PATH.application(id)}/stats`, { query: range ? { from: range.from, to: range.to } : {} }),

        /** 사업자 센터 「진행 중 광고」 요약 — 진행 중·정지 중·내려진 건(광고주 중단 제외) 최대 5개, 최근 7일 */
        adStatsSummary: (): Promise<BizAdStatsSummary> =>
            // MOCK: 실제 진행 중 목록(`bucket=RUNNING`)에 숫자만 붙인다
            mockAdStatsSummary(() => client.get<BizApplicationPage>(PATH.applications, { query: { bucket: 'RUNNING' } })),
        // REAL: client.get<BizAdStatsSummary>(PATH.adStatsSummary),
    };
}
