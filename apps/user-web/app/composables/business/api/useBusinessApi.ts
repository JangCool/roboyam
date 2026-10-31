import type {
    AdApplyRequest,
    AdProduct,
    AvailabilityDay,
    AvailabilitySlot,
    BizApplicationBucket,
    BizApplicationCreated,
    BizApplicationDetail,
    BizApplicationPage,
    BizCreativeRequest,
    BizPaymentStart,
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
 *    `BUSINESS_NOT_EDITABLE`, 429 `BUSINESS_REAPPLY_LIMIT`, 400 `fields.*`
 *  - 상품·남은 칸(V112): `GET /api/business/ad-products` · `GET /api/business/deal-products` · `GET /api/business/availability?slot=&from=&to=`(92일까지) · `GET /api/business/deal-link-check?url=`
 *  - 광고 이미지: `POST /api/business/ad-images`(multipart `file`, JPG·PNG·GIF·WEBP 10MB — 등록증 `/files` 와 따로) — 400 `fields.file`, 413 `FILE_TOO_LARGE`
 *  - 신청 건: `POST /api/business/applications/{ads|deals}`(201 `{ id }`) · `GET /api/business/applications[?bucket=]` · `GET …/{id}` · `POST …/{id}/cancel` ·
 *    `PUT …/{id}/creative`(소재 바꾸기 → 결제 전이면 다시 심사) · `POST …/{id}/refund-request` `{ reason }`(1~300자)
 *  - 결제(온라인): `POST …/{id}/payment` → 결제창 정보(`payment` 모듈 `BIZ_APPLICATION`) — 완료는 공용 결제 완료 API(`usePaymentCheckout`) 뒤 상세를 다시 읽는다
 *  - 409 `BUSINESS_NOT_APPROVED`·`BUSINESS_RE_REVIEW`·`BIZ_SLOT_FULL`·`BIZ_PRODUCT_CLOSED`·`BIZ_APPLICATION_STATE_CHANGED`(화면이 다시 읽는다) · 404 `BIZ_APPLICATION_NOT_FOUND`
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
    application: (id: string) => `/api/business/applications/${encodeURIComponent(id)}`,
};

export function useBusinessApi() {
    const client = useApiClient();
    const paymentApi = usePaymentApi();
    const testApi = usePaymentTestApi();

    return {
        /** 내 사업자 신원 */
        profile: (): Promise<BusinessProfile> => client.get<BusinessProfile>(PATH.me),

        /** 사업자 신청(새로·반려 뒤 다시) — 심사 대기가 된다 */
        apply: (body: BusinessApplyRequest): Promise<BusinessProfile> => client.post<BusinessProfile>(PATH.me, body),

        /** 사업자 정보 수정 — 상호·번호를 바꾸면 다시 심사(F7) */
        update: (body: BusinessApplyRequest): Promise<BusinessProfile> => client.put<BusinessProfile>(PATH.me, body),

        /** 사업자등록증 사본 올리기(PDF·JPG·PNG 10MB) — 신청·수정이 `certificateFileId` 로 붙인다 */
        uploadCertificate: (file: File): Promise<BusinessFile> => client.upload<BusinessFile>(PATH.files, ((f) => (f.append('file', file), f))(new FormData())),

        /** 광고 이미지 올리기(JPG·PNG·GIF·WEBP 10MB) — 신청·소재 바꾸기가 `imageFileId` 로 붙인다 */
        uploadFile: (file: File): Promise<BusinessFile> => client.upload<BusinessFile>(PATH.adImages, ((f) => (f.append('file', file), f))(new FormData())),

        /** 광고 종류별 단가·한도·남은 칸 요약 */
        adProducts: (): Promise<AdProduct[]> => client.get<AdProduct[]>(PATH.adProducts),

        /** 사업자 핫딜 요금 */
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
    };
}
