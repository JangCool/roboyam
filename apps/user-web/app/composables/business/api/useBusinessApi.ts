import {
    mockAdProducts,
    mockApplication,
    mockApplications,
    mockApply,
    mockAvailability,
    mockCancel,
    mockCheckDealLink,
    mockCompletePayment,
    mockDealProduct,
    mockOperate,
    mockProfile,
    mockRefundRequest,
    mockSetProfileStatus,
    mockStartPayment,
    mockSubmitAd,
    mockSubmitDeal,
    mockUploadFile,
} from '~/composables/business/api/mock/business-mock';
import type {
    AdApplyRequest,
    AdProduct,
    AvailabilityDay,
    AvailabilitySlot,
    BizApplicationBucket,
    BizApplicationCreated,
    BizApplicationDetail,
    BizApplicationPage,
    BizMockOperatorAction,
    BizPaymentCompleteRequest,
    BizPaymentStart,
    BusinessApplyRequest,
    BusinessFile,
    BusinessProfile,
    BusinessProfileStatus,
    DealApplyRequest,
    DealLinkCheck,
    DealProduct,
} from '~/composables/business/api/useBusinessApi.types';

/**
 * 사업자 센터 API — 서버 주소 문자열(`/api/business/…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 필요(401).
 * 요구사항 정본 `docs/plans/biz-ad-hotdeal/00-overview.md` 「★ 확정」(사전 심사 → 결제, 온라인/수동 결제, 신청 건 상태). 계약 문서는 화면 확정 뒤 만든다.
 *  - 신원: `GET/POST/PUT /api/business/me` · 첨부 `POST /api/business/files`(multipart, 10MB)
 *  - 상품·남은 칸: `GET /api/business/ad-products` · `GET /api/business/deal-products` · `GET /api/business/availability`
 *  - 신청 건: `POST /api/business/applications/{ads|deals}` · `GET /api/business/applications[?bucket=]` · `GET …/{id}` · `POST …/{id}/cancel` · `POST …/{id}/refund-request`
 *  - 결제(온라인): `POST …/{id}/payment` → 결제창 정보(`payment` 모듈 `BIZ_APPLICATION`) — 완료는 `payment` 모듈 완료 API
 *  - 409 `BUSINESS_NOT_APPROVED`·`BUSINESS_RE_REVIEW`·`BIZ_SLOT_FULL`·`BIZ_APPLICATION_STATE_CHANGED`(화면이 다시 읽는다)
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/business-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 * `mockSetStatus`·`mockOperate` 는 개발용(서버에 없음) — 서버가 붙으면 쓰는 곳(목업 표시 상자)과 함께 지운다.
 */
const PATH = {
    me: '/api/business/me',
    files: '/api/business/files',
    adProducts: '/api/business/ad-products',
    dealProducts: '/api/business/deal-products',
    availability: '/api/business/availability',
    dealLinkCheck: '/api/business/deal-link-check',
    applications: '/api/business/applications',
    application: (id: string) => `/api/business/applications/${encodeURIComponent(id)}`,
};

export function useBusinessApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 내 사업자 신원 */
        profile: (): Promise<BusinessProfile> =>
            // MOCK:
            mockProfile(),
        // REAL: client.get<BusinessProfile>(PATH.me),

        /** 사업자 신청(새로) — 심사 대기가 된다 */
        apply: (body: BusinessApplyRequest): Promise<BusinessProfile> =>
            // MOCK:
            mockApply(body, false),
        // REAL: client.post<BusinessProfile>(PATH.me, body),

        /** 사업자 정보 수정 — 상호·번호를 바꾸면 다시 심사(F7) */
        update: (body: BusinessApplyRequest): Promise<BusinessProfile> =>
            // MOCK:
            mockApply(body, true),
        // REAL: client.put<BusinessProfile>(PATH.me, body),

        /** 첨부 올리기(사업자등록증 사본·광고 이미지) */
        uploadFile: (file: File): Promise<BusinessFile> =>
            // MOCK:
            mockUploadFile(file),
        // REAL: client.upload<BusinessFile>(PATH.files, ((f) => (f.append('file', file), f))(new FormData())),

        /** 광고 종류별 단가·한도·남은 칸 요약 */
        adProducts: (): Promise<AdProduct[]> =>
            // MOCK:
            mockAdProducts(),
        // REAL: client.get<AdProduct[]>(PATH.adProducts),

        /** 사업자 핫딜 요금 */
        dealProduct: (): Promise<DealProduct> =>
            // MOCK:
            mockDealProduct(),
        // REAL: client.get<DealProduct>(PATH.dealProducts),

        /** 날짜별 남은 칸 — 저장 때 서버가 다시 판정한다 */
        availability: (slot: AvailabilitySlot, from: string, to: string): Promise<AvailabilityDay[]> =>
            // MOCK:
            mockAvailability(slot, from, to),
        // REAL: client.get<AvailabilityDay[]>(PATH.availability, { query: { slot, from, to } }),

        /** 핫딜 상품 링크 검사 — 쇼핑몰 이름·제휴·차단 */
        checkDealLink: (url: string): Promise<DealLinkCheck> =>
            // MOCK:
            mockCheckDealLink(url),
        // REAL: client.get<DealLinkCheck>(PATH.dealLinkCheck, { query: { url } }),

        /** 광고 심사 신청 — 지금 결제하지 않는다(승인 뒤 결제) */
        submitAd: (body: AdApplyRequest): Promise<BizApplicationCreated> =>
            // MOCK:
            mockSubmitAd(body),
        // REAL: client.post<BizApplicationCreated>(`${PATH.applications}/ads`, body),

        /** 사업자 핫딜 심사 신청 */
        submitDeal: (body: DealApplyRequest): Promise<BizApplicationCreated> =>
            // MOCK:
            mockSubmitDeal(body),
        // REAL: client.post<BizApplicationCreated>(`${PATH.applications}/deals`, body),

        /** 내 신청 건 목록 */
        applications: (bucket: BizApplicationBucket): Promise<BizApplicationPage> =>
            // MOCK:
            mockApplications(bucket),
        // REAL: client.get<BizApplicationPage>(PATH.applications, { query: { bucket } }),

        /** 신청 건 상세 */
        application: (id: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockApplication(id),
        // REAL: client.get<BizApplicationDetail>(PATH.application(id)),

        /** 결제 전 취소(심사 중·결제 대기) */
        cancel: (id: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockCancel(id),
        // REAL: client.post<BizApplicationDetail>(`${PATH.application(id)}/cancel`),

        /** 결제 시작 — 결제창 정보 */
        startPayment: (id: string): Promise<BizPaymentStart> =>
            // MOCK:
            mockStartPayment(id),
        // REAL: client.post<BizPaymentStart>(`${PATH.application(id)}/payment`),

        /** 결제 완료(목업 결제창 결과) — 서버가 붙으면 `usePaymentCheckout` 의 완료 요청이 대신한다 */
        completePayment: (id: string, body: BizPaymentCompleteRequest): Promise<BizApplicationDetail> =>
            // MOCK:
            mockCompletePayment(id, body),
        // REAL: (void body, client.get<BizApplicationDetail>(PATH.application(id))),

        /** 취소·환불 요청(예약·진행 중) */
        refundRequest: (id: string, reason: string): Promise<BizApplicationDetail> =>
            // MOCK:
            mockRefundRequest(id, reason),
        // REAL: client.post<BizApplicationDetail>(`${PATH.application(id)}/refund-request`, { reason }),

        /** 개발용 — 목업 신원 상태 바꾸기 */
        mockSetStatus: (status: BusinessProfileStatus, reReview = false): Promise<BusinessProfile> => mockSetProfileStatus(status, reReview),

        /** 개발용 — 운영자 처리 흉내 */
        mockOperate: (id: string, action: BizMockOperatorAction): Promise<BizApplicationDetail> => mockOperate(id, action),
    };
}
