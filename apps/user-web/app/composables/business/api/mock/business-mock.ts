// MOCK: 사업자 센터의 신청 건(광고/핫딜 신청 건·결제·환불 요청·광고 이미지)의 메모리 목업 — `useBusinessApi.ts` 가 부른다. 신원은 서버에 붙었다.
// 신청 건 백엔드가 붙으면 `business/api/mock/` 와 함께 지운다. 새로고침하면 처음 상태(목업 안의 승인된 사업자 + 신청 건 9개)로 돌아간다 — 서버 신원과 따로 판정한다.
//
// ── 목업 규칙(가정 — `docs/plans/biz-ad-hotdeal/00-overview.md` ★-6) ─
//   · 요금 = 종류 × 하루 단가 × 일수 + 부가세 10%. 운영자가 승인 때 확정한다(목업 승인은 예상 금액 그대로).
//   · 결제 기한 72시간 · 입금 기한 7일 · 첨부 10MB. 결제는 가짜 결제창(`PaymentFakeCheckoutDialog`)으로만 — 실제 PG 를 부르지 않는다.
//   · 「예약 → 진행 → 종료」는 기간과 오늘(브라우저 날짜)로 계산한다. 목업 운영자 처리 START·END 는 기간을 옮겨 단계를 바꾼다.
//   · 사업자 핫딜에 노출을 고르면 같은 신청 건(HOTDEAL_POST)에 묶어 한 번에 결제한다.
//   · 운영자 처리는 개발용(`mockOperate`)이다 — 서버에는 없다.

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay } from '~/composables/common/mock/mock-util';
import type {
    AdApplyRequest,
    AdProduct,
    AdType,
    AvailabilityDay,
    AvailabilitySlot,
    BizAmount,
    BizApplicationBucket,
    BizApplicationCreated,
    BizApplicationDetail,
    BizApplicationPage,
    BizApplicationSummary,
    BizHistoryEntry,
    BizMockOperatorAction,
    BizPaymentCompleteRequest,
    BizPaymentStart,
    BusinessFile,
    BusinessProfile,
    DealApplyRequest,
    DealLinkCheck,
    DealProduct,
} from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const PAYMENT_HOURS = 72;
const DEPOSIT_DAYS = 7;

const AD_PRODUCTS: AdProduct[] = [
    { type: 'WIDE', dailyPrice: 50_000, maxDays: 14, maxLeadDays: 60, capacity: 2, openDays: 0, accepting: true },
    { type: 'STRIP', dailyPrice: 30_000, maxDays: 14, maxLeadDays: 60, capacity: 3, openDays: 0, accepting: true },
    { type: 'SQUARE', dailyPrice: 20_000, maxDays: 30, maxLeadDays: 60, capacity: 4, openDays: 0, accepting: true },
    { type: 'SMALL', dailyPrice: 10_000, maxDays: 30, maxLeadDays: 60, capacity: 6, openDays: 0, accepting: false },
    { type: 'TEXT_LINE', dailyPrice: 5_000, maxDays: 30, maxLeadDays: 60, capacity: 5, openDays: 0, accepting: true },
];

const DEAL_PRODUCT: DealProduct = {
    postPrice: 30_000,
    placements: [
        { placement: 'HOME_TOP', dailyPrice: 20_000, capacity: 3, maxDays: 7 },
        { placement: 'CATEGORY_TOP', dailyPrice: 10_000, capacity: 2, maxDays: 7 },
    ],
};

const DEPOSIT_ACCOUNT = { bankName: '국민은행', accountNo: '123456-01-987654', accountHolder: '(주)로보얌' };

const profile: BusinessProfile = approvedProfile();
let apps: BizApplicationDetail[] = seedApplications();
let seq = 100;

/* ── 도구 ─────────────────────────────────────────────── */

function iso(offsetMs: number): string {
    return new Date(Date.now() + offsetMs).toISOString();
}

function today(): string {
    return UtilBusiness.todayYmd();
}

function day(offset: number): string {
    return UtilBusiness.addDays(today(), offset);
}

function amountOf(supply: number): BizAmount {
    const vat = Math.floor(supply * UtilBusiness.VAT_RATE);
    return { supply, vat, total: supply + vat };
}

function notFound(): ApiError {
    return new ApiError({ status: 404, code: 'BIZ_APPLICATION_NOT_FOUND', message: '신청 건을 찾을 수 없어요.' });
}

function conflict(code: string, message: string): ApiError {
    return new ApiError({ status: 409, code, message });
}

function log(app: BizApplicationDetail, actor: BizHistoryEntry['actor'], action: BizHistoryEntry['action'], note: string | null = null, at: string = iso(0)): void {
    app.history.push({ at, actor, action, note });
}

function approvedProfile(): BusinessProfile {
    return {
        status: 'APPROVED',
        companyName: '로보얌상사',
        bizNoMasked: '220-81-*****',
        ownerName: '김로보',
        phoneMasked: '010-****-5678',
        email: 'biz@roboyam.test',
        address: { postcode: '06236', address1: '서울 강남구 테헤란로 123', address2: '4층' },
        certificate: { fileId: 'f-cert-1', name: '사업자등록증.pdf', size: 482_113 },
        submittedAt: iso(-20 * DAY),
        reviewedAt: iso(-18 * DAY),
        rejectCode: null,
        rejectReason: null,
        suspendReason: null,
        reReview: false,
    };
}

function base(id: string, partial: Partial<BizApplicationDetail> & Pick<BizApplicationDetail, 'kind' | 'title' | 'startDate' | 'endDate' | 'status'>): BizApplicationDetail {
    const estimate = partial.estimate ?? amountOf(10_000 * UtilBusiness.daysBetween(partial.startDate, partial.endDate));
    return {
        id,
        adType: null,
        placement: null,
        phase: null,
        paymentMethod: 'ONLINE',
        amount: estimate,
        amountConfirmed: false,
        paymentDueAt: null,
        depositDueAt: null,
        submittedAt: iso(-3 * DAY),
        refundRequested: false,
        estimate,
        confirmed: null,
        adjustReason: null,
        creative: null,
        deal: null,
        rejectCode: null,
        rejectReason: null,
        takedownReason: null,
        deposit: null,
        refund: null,
        history: [],
        ...partial,
    };
}

function banner(type: AdType, link = 'https://shop.roboyam.test/event'): BizApplicationDetail['creative'] {
    return { version: 1, imageUrl: null, imageName: `banner-${type.toLowerCase()}.png`, linkUrl: link, altText: '가을 특가 — 최대 50% 할인', text: null };
}

function seedApplications(): BizApplicationDetail[] {
    const list: BizApplicationDetail[] = [];

    const a1 = base('ba-1', { kind: 'AD_BANNER', adType: 'WIDE', title: '가을 특가 넓은 배너', startDate: day(4), endDate: day(10), status: 'AWAITING_PAYMENT', estimate: amountOf(350_000), creative: banner('WIDE') });
    a1.confirmed = a1.estimate;
    a1.paymentDueAt = iso(50 * HOUR);
    log(a1, 'ME', 'SUBMITTED', null, iso(-2 * DAY));
    log(a1, 'OPERATOR', 'APPROVED_ONLINE', '소재·링크 확인 완료', iso(-22 * HOUR));
    list.push(a1);

    const a8 = base('ba-8', { kind: 'HOTDEAL_PLACEMENT', placement: 'HOME_TOP', title: '무선 청소기 핫딜 홈 상단 노출', startDate: day(2), endDate: day(4), status: 'AWAITING_PAYMENT', estimate: amountOf(60_000) });
    a8.confirmed = a8.estimate;
    a8.paymentDueAt = iso(5 * HOUR);
    log(a8, 'ME', 'SUBMITTED', null, iso(-4 * DAY));
    log(a8, 'OPERATOR', 'APPROVED_ONLINE', null, iso(-67 * HOUR));
    list.push(a8);

    const a2 = base('ba-2', {
        kind: 'HOTDEAL_POST',
        placement: 'CATEGORY_TOP',
        title: '[로보얌상사] 스테인리스 텀블러 1+1',
        startDate: day(3),
        endDate: day(5),
        status: 'AWAITING_DEPOSIT',
        paymentMethod: 'MANUAL',
        estimate: amountOf(60_000),
        deal: { linkUrl: 'https://smartstore.naver.com/roboyam/products/1', shopName: '네이버 스마트스토어', listPrice: 32_000, dealPrice: 15_900, shippingFee: 0, endsOn: day(10), stock: 'LIMITED', body: '1+1 행사 — 재고 소진 시 종료돼요.', placement: 'CATEGORY_TOP' },
    });
    a2.confirmed = a2.estimate;
    a2.depositDueAt = iso(6 * DAY);
    a2.deposit = { ...DEPOSIT_ACCOUNT, depositorName: '로보얌상사', contract: 'WAITING', depositConfirmed: false };
    log(a2, 'ME', 'SUBMITTED', null, iso(-2 * DAY));
    log(a2, 'OPERATOR', 'APPROVED_MANUAL', '계좌이체로 진행', iso(-1 * DAY));
    list.push(a2);

    const a3 = base('ba-3', { kind: 'AD_BANNER', adType: 'SQUARE', title: '신상품 네모 배너', startDate: day(-3), endDate: day(4), status: 'SCHEDULED', estimate: amountOf(160_000), creative: banner('SQUARE') });
    a3.confirmed = a3.estimate;
    log(a3, 'ME', 'SUBMITTED', null, iso(-9 * DAY));
    log(a3, 'OPERATOR', 'APPROVED_ONLINE', null, iso(-8 * DAY));
    log(a3, 'ME', 'PAID', null, iso(-7 * DAY));
    log(a3, 'SYSTEM', 'STARTED', null, iso(-3 * DAY));
    list.push(a3);

    const a4 = base('ba-4', { kind: 'AD_TEXT_LINE', adType: 'TEXT_LINE', title: '한 줄 광고 — 무료 배송 이벤트', startDate: day(6), endDate: day(12), status: 'SUBMITTED', estimate: amountOf(35_000) });
    a4.creative = { version: 1, imageUrl: null, imageName: null, linkUrl: 'https://shop.roboyam.test/free', altText: null, text: '지금 주문하면 전 상품 무료 배송!' };
    a4.submittedAt = iso(-5 * HOUR);
    log(a4, 'ME', 'SUBMITTED', null, iso(-5 * HOUR));
    list.push(a4);

    const a5 = base('ba-5', { kind: 'AD_BANNER', adType: 'STRIP', title: '블랙프라이데이 띠 배너', startDate: day(8), endDate: day(14), status: 'SCHEDULED', estimate: amountOf(210_000), creative: banner('STRIP') });
    a5.confirmed = a5.estimate;
    log(a5, 'ME', 'SUBMITTED', null, iso(-6 * DAY));
    log(a5, 'OPERATOR', 'APPROVED_ONLINE', null, iso(-5 * DAY));
    log(a5, 'ME', 'PAID', null, iso(-4 * DAY));
    list.push(a5);

    const a6 = base('ba-6', {
        kind: 'HOTDEAL_POST',
        placement: 'NONE',
        title: '[로보얌상사] 무선 이어폰 특가',
        startDate: day(-1),
        endDate: day(6),
        status: 'REJECTED',
        estimate: amountOf(30_000),
        rejectCode: 'POLICY',
        rejectReason: '정상가 근거(판매 이력)를 확인할 수 없어요. 최근 30일 판매가를 본문에 적어 다시 신청해 주세요.',
        deal: { linkUrl: 'https://www.coupang.com/vp/products/1', shopName: '쿠팡', listPrice: 129_000, dealPrice: 49_000, shippingFee: null, endsOn: null, stock: 'UNKNOWN', body: '역대 최저가!', placement: 'NONE' },
    });
    log(a6, 'ME', 'SUBMITTED', null, iso(-3 * DAY));
    log(a6, 'OPERATOR', 'REJECTED', a6.rejectReason, iso(-2 * DAY));
    list.push(a6);

    const a7 = base('ba-7', { kind: 'AD_BANNER', adType: 'SMALL', title: '여름 시즌 작은 배너', startDate: day(-40), endDate: day(-26), status: 'SCHEDULED', estimate: amountOf(150_000), creative: banner('SMALL') });
    a7.confirmed = a7.estimate;
    a7.submittedAt = iso(-45 * DAY);
    log(a7, 'ME', 'SUBMITTED', null, iso(-45 * DAY));
    log(a7, 'OPERATOR', 'APPROVED_ONLINE', null, iso(-44 * DAY));
    log(a7, 'ME', 'PAID', null, iso(-43 * DAY));
    log(a7, 'SYSTEM', 'STARTED', null, iso(-40 * DAY));
    log(a7, 'SYSTEM', 'ENDED', null, iso(-25 * DAY));
    list.push(a7);

    const a9 = base('ba-9', { kind: 'AD_BANNER', adType: 'WIDE', title: '추석 넓은 배너', startDate: day(-30), endDate: day(-24), status: 'EXPIRED', estimate: amountOf(350_000), creative: banner('WIDE') });
    a9.confirmed = a9.estimate;
    a9.submittedAt = iso(-38 * DAY);
    log(a9, 'ME', 'SUBMITTED', null, iso(-38 * DAY));
    log(a9, 'OPERATOR', 'APPROVED_ONLINE', null, iso(-37 * DAY));
    log(a9, 'SYSTEM', 'EXPIRED', '결제 기한(72시간)이 지나 자동 취소하고 자리를 반납했어요.', iso(-34 * DAY));
    list.push(a9);

    return list;
}

/** 읽을 때마다 시각에 따른 값(기한 지남·예약 단계·금액 요약)을 맞춘다 */
function refresh(app: BizApplicationDetail): void {
    if (app.status === 'AWAITING_PAYMENT' && app.paymentDueAt && Date.parse(app.paymentDueAt) <= Date.now()) {
        app.status = 'EXPIRED';
        log(app, 'SYSTEM', 'EXPIRED', '결제 기한이 지나 자동 취소하고 자리를 반납했어요.');
    }
    if (app.status === 'SCHEDULED') {
        const t = today();
        app.phase = t < app.startDate ? 'UPCOMING' : t > app.endDate ? 'ENDED' : 'RUNNING';
    } else {
        app.phase = null;
    }
    app.amount = app.confirmed ?? app.estimate;
    app.amountConfirmed = app.confirmed !== null;
    app.refundRequested = app.refund !== null;
}

function summaryOf(app: BizApplicationDetail): BizApplicationSummary {
    const { id, kind, title, adType, placement, startDate, endDate, status, phase, paymentMethod, amount, amountConfirmed, paymentDueAt, depositDueAt, submittedAt, refundRequested } = app;
    return { id, kind, title, adType, placement, startDate, endDate, status, phase, paymentMethod, amount, amountConfirmed, paymentDueAt, depositDueAt, submittedAt, refundRequested };
}

function find(id: string): BizApplicationDetail {
    const app = apps.find((a) => a.id === id);
    if (!app) throw notFound();
    refresh(app);
    return app;
}

function requireApproved(): void {
    if (profile.status !== 'APPROVED') throw conflict('BUSINESS_NOT_APPROVED', '승인된 사업자만 신청할 수 있어요.');
    if (profile.reReview) throw conflict('BUSINESS_RE_REVIEW', '사업자 정보를 다시 심사하는 동안에는 새 신청을 받을 수 없어요. 진행 중인 건은 그대로 나가요.');
}

/** 날짜별 남은 칸 — 날짜·자리로 정해지는 가짜 값(같은 날은 늘 같다). 7의 배수 날은 꽉 참 */
function remainingOn(slot: AvailabilitySlot, ymd: string, capacity: number): number {
    let h = 0;
    for (const ch of `${slot}${ymd}`) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    if (h % 7 === 0) return 0;
    return Math.max(0, capacity - (h % (capacity + 1)));
}

function capacityOf(slot: AvailabilitySlot): number {
    const ad = AD_PRODUCTS.find((p) => p.type === slot);
    if (ad) return ad.capacity;
    return DEAL_PRODUCT.placements.find((p) => p.placement === slot)?.capacity ?? 1;
}

function checkPeriodOpen(slot: AvailabilitySlot, start: string, end: string): void {
    const cap = capacityOf(slot);
    for (let d = start; d <= end; d = UtilBusiness.addDays(d, 1)) {
        if (remainingOn(slot, d, cap) <= 0) throw conflict('BIZ_SLOT_FULL', `${d} 은 이미 자리가 찼어요. 다른 기간을 골라 주세요.`);
    }
}

/* ── 첨부(광고 이미지) ───────────────────────────────── */

export async function mockUploadFile(file: File): Promise<BusinessFile> {
    await mockDelay();
    if (file.size > UtilBusiness.FILE_MAX_BYTES) throw new ApiError({ status: 400, code: 'FILE_TOO_LARGE', message: '파일은 한 장에 10MB 까지 올릴 수 있어요.' });
    return {
        fileId: `f-${++seq}`,
        name: file.name,
        size: file.size,
        contentType: file.type,
        previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : null,
    };
}

/* ── 상품·남은 칸 ─────────────────────────────────────── */

export async function mockAdProducts(): Promise<AdProduct[]> {
    await mockDelay();
    return AD_PRODUCTS.map((p) => {
        let open = 0;
        for (let i = 1; i <= 30; i++) if (remainingOn(p.type, day(i), p.capacity) > 0) open++;
        return { ...p, openDays: open };
    });
}

export async function mockDealProduct(): Promise<DealProduct> {
    await mockDelay();
    return mockClone(DEAL_PRODUCT);
}

export async function mockAvailability(slot: AvailabilitySlot, from: string, to: string): Promise<AvailabilityDay[]> {
    await mockDelay();
    const cap = capacityOf(slot);
    const out: AvailabilityDay[] = [];
    for (let d = from; d <= to; d = UtilBusiness.addDays(d, 1)) out.push({ date: d, remaining: remainingOn(slot, d, cap), capacity: cap });
    return out;
}

const SHOPS: Record<string, string> = {
    'coupang.com': '쿠팡',
    'link.coupang.com': '쿠팡',
    '11st.co.kr': '11번가',
    'gmarket.co.kr': 'G마켓',
    'auction.co.kr': '옥션',
    'smartstore.naver.com': '네이버 스마트스토어',
    'brand.naver.com': '네이버 브랜드스토어',
    'ssg.com': 'SSG닷컴',
};
const SHORTENERS = ['bit.ly', 'han.gl', 't.ly', 'me2.do', 'url.kr', 'tinyurl.com'];

export async function mockCheckDealLink(url: string): Promise<DealLinkCheck> {
    await mockDelay();
    let host: string;
    try {
        host = new URL(url.trim()).hostname.replace(/^www\./, '');
    } catch {
        throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 값을 확인해 주세요.', fields: { linkUrl: '링크 형식이 올바르지 않아요.' } });
    }
    if (SHORTENERS.includes(host)) return { host, shopName: null, blocked: true, blockedReason: '단축 URL 은 올릴 수 없어요. 쇼핑몰의 원래 주소를 붙여 넣어 주세요.', affiliate: false };
    const key = Object.keys(SHOPS).find((k) => host === k || host.endsWith(`.${k}`));
    return { host, shopName: key ? SHOPS[key]! : null, blocked: false, blockedReason: null, affiliate: host === 'link.coupang.com' };
}

/* ── 신청 건 ─────────────────────────────────────────── */

export async function mockSubmitAd(body: AdApplyRequest): Promise<BizApplicationCreated> {
    await mockDelay();
    requireApproved();
    const product = AD_PRODUCTS.find((p) => p.type === body.adType);
    if (!product?.accepting) throw conflict('AD_TYPE_CLOSED', '이 광고 종류는 지금 신청을 받지 않아요.');
    checkPeriodOpen(body.adType, body.startDate, body.endDate);
    const days = UtilBusiness.daysBetween(body.startDate, body.endDate);
    const app = base(`ba-${++seq}`, {
        kind: body.adType === 'TEXT_LINE' ? 'AD_TEXT_LINE' : 'AD_BANNER',
        adType: body.adType,
        title: body.adType === 'TEXT_LINE' ? (body.text ?? '한 줄 광고') : (body.altText ?? '배너 광고'),
        startDate: body.startDate,
        endDate: body.endDate,
        status: 'SUBMITTED',
        paymentMethod: body.paymentMethod,
        estimate: amountOf(product.dailyPrice * days),
        submittedAt: iso(0),
        creative: { version: 1, imageUrl: null, imageName: body.imageFileId ? '올린 이미지' : null, linkUrl: body.linkUrl, altText: body.altText, text: body.text },
    });
    log(app, 'ME', 'SUBMITTED');
    apps.unshift(app);
    return { id: app.id };
}

export async function mockSubmitDeal(body: DealApplyRequest): Promise<BizApplicationCreated> {
    await mockDelay();
    requireApproved();
    let supply = DEAL_PRODUCT.postPrice;
    const placement = body.deal.placement;
    let start = today();
    let end = body.deal.endsOn ?? UtilBusiness.addDays(start, 6);
    if (placement !== 'NONE' && body.placementStartDate && body.placementEndDate) {
        checkPeriodOpen(placement, body.placementStartDate, body.placementEndDate);
        const p = DEAL_PRODUCT.placements.find((x) => x.placement === placement)!;
        supply += p.dailyPrice * UtilBusiness.daysBetween(body.placementStartDate, body.placementEndDate);
        start = body.placementStartDate;
        end = body.placementEndDate;
    }
    const app = base(`ba-${++seq}`, {
        kind: 'HOTDEAL_POST',
        placement,
        title: body.title,
        startDate: start,
        endDate: end,
        status: 'SUBMITTED',
        paymentMethod: body.paymentMethod,
        estimate: amountOf(supply),
        submittedAt: iso(0),
        deal: mockClone(body.deal),
    });
    log(app, 'ME', 'SUBMITTED');
    apps.unshift(app);
    return { id: app.id };
}

export async function mockApplications(bucket: BizApplicationBucket): Promise<BizApplicationPage> {
    await mockDelay();
    apps.forEach(refresh);
    const counts = Object.fromEntries(UtilBusiness.BUCKETS.map((b) => [b, 0])) as Record<BizApplicationBucket, number>;
    for (const a of apps) {
        counts.ALL++;
        counts[UtilBusiness.bucketOf(a.status, a.phase)]++;
    }
    const items = apps.filter((a) => bucket === 'ALL' || UtilBusiness.bucketOf(a.status, a.phase) === bucket).map(summaryOf);
    return mockClone({ items, total: items.length, counts });
}

export async function mockApplication(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    return mockClone(find(id));
}

export async function mockCancel(id: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const app = find(id);
    if (app.status !== 'SUBMITTED' && app.status !== 'AWAITING_PAYMENT') throw conflict('BIZ_APPLICATION_STATE_CHANGED', '이미 처리 상태가 바뀌었어요. 화면을 다시 불러왔어요.');
    app.status = 'CANCELED';
    app.paymentDueAt = null;
    log(app, 'ME', 'CANCELED');
    refresh(app);
    return mockClone(app);
}

export async function mockStartPayment(id: string): Promise<BizPaymentStart> {
    await mockDelay();
    const app = find(id);
    if (app.status !== 'AWAITING_PAYMENT' || !app.confirmed || !app.paymentDueAt) throw conflict('BIZ_APPLICATION_STATE_CHANGED', '결제할 수 있는 상태가 아니에요.');
    return {
        orderType: 'BIZ_APPLICATION',
        orderRefId: app.id,
        expiresAt: app.paymentDueAt,
        checkout: { gateway: 'FAKE', paymentId: `pay-${app.id}-${++seq}`, orderName: app.title, amount: app.confirmed.total, currency: 'KRW', storeId: null, channelKey: null },
    };
}

export async function mockCompletePayment(id: string, body: BizPaymentCompleteRequest): Promise<BizApplicationDetail> {
    await mockDelay();
    const app = find(id);
    if (app.status !== 'AWAITING_PAYMENT' || !app.confirmed) throw conflict('BIZ_APPLICATION_STATE_CHANGED', '결제할 수 있는 상태가 아니에요.');
    if (body.outcome === 'FAILED') throw new ApiError({ status: 402, code: 'PAYMENT_FAILED', message: '결제가 완료되지 않았어요. 다시 시도해 주세요.' });
    if (body.late) throw new ApiError({ status: 409, code: 'PAYMENT_LATE', message: '결제 유효 시각이 지나 결제를 자동 취소했어요. 다시 결제해 주세요.' });
    if (body.amount !== app.confirmed.total) throw new ApiError({ status: 409, code: 'PAYMENT_AMOUNT_MISMATCH', message: '결제 금액이 확정 금액과 달라 자동 취소했어요.' });
    app.status = 'SCHEDULED';
    app.paymentDueAt = null;
    log(app, 'ME', 'PAID');
    log(app, 'SYSTEM', 'SCHEDULED');
    refresh(app);
    return mockClone(app);
}

export async function mockRefundRequest(id: string, reason: string): Promise<BizApplicationDetail> {
    await mockDelay();
    const app = find(id);
    if (app.status !== 'SCHEDULED' || app.phase === 'ENDED' || app.refund) throw conflict('BIZ_APPLICATION_STATE_CHANGED', '이미 처리 상태가 바뀌었어요. 화면을 다시 불러왔어요.');
    const total = app.confirmed?.total ?? 0;
    if (app.phase === 'UPCOMING') {
        const auto = app.paymentMethod === 'ONLINE';
        app.refund = { type: 'FULL_AUTO', reason, requestedAt: iso(0), status: auto ? 'REFUNDED' : 'REQUESTED', refundAmount: total };
        app.status = 'CANCELED';
        log(app, 'ME', 'REFUND_REQUESTED', reason);
        if (auto) log(app, 'SYSTEM', 'REFUNDED', `전액 ${total.toLocaleString('en-US')}원 환불`);
    } else {
        app.refund = { type: 'PRORATED_REVIEW', reason, requestedAt: iso(0), status: 'REQUESTED', refundAmount: UtilBusiness.proratedRefund(total, app.startDate, app.endDate, today()) };
        log(app, 'ME', 'REFUND_REQUESTED', reason);
    }
    refresh(app);
    return mockClone(app);
}

/** 개발용 — 운영자가 할 처리를 흉내 낸다(서버가 붙으면 관리자 화면이 한다) */
export async function mockOperate(id: string, action: BizMockOperatorAction): Promise<BizApplicationDetail> {
    await mockDelay();
    const app = find(id);
    const length = UtilBusiness.daysBetween(app.startDate, app.endDate);
    const shiftIfPast = () => {
        // F6 — 심사 중 희망 시작일이 지났으면 같은 길이로 내일부터
        if (app.startDate <= today()) {
            app.startDate = day(1);
            app.endDate = day(length);
            return '희망 시작일이 지나 같은 길이로 내일부터 옮겼어요.';
        }
        return null;
    };
    switch (action) {
        case 'APPROVE_ONLINE':
        case 'APPROVE_MANUAL': {
            if (app.status !== 'SUBMITTED') throw conflict('BIZ_APPLICATION_STATE_CHANGED', '심사 중인 건만 승인할 수 있어요.');
            const moved = shiftIfPast();
            app.confirmed = { ...app.estimate };
            if (action === 'APPROVE_ONLINE') {
                app.status = 'AWAITING_PAYMENT';
                app.paymentMethod = 'ONLINE';
                app.paymentDueAt = iso(PAYMENT_HOURS * HOUR);
            } else {
                app.status = 'AWAITING_DEPOSIT';
                app.paymentMethod = 'MANUAL';
                app.depositDueAt = iso(DEPOSIT_DAYS * DAY);
                app.deposit = { ...DEPOSIT_ACCOUNT, depositorName: profile.companyName ?? '사업자 상호', contract: 'WAITING', depositConfirmed: false };
            }
            log(app, 'OPERATOR', action === 'APPROVE_ONLINE' ? 'APPROVED_ONLINE' : 'APPROVED_MANUAL', moved);
            break;
        }
        case 'REJECT':
            if (app.status !== 'SUBMITTED') throw conflict('BIZ_APPLICATION_STATE_CHANGED', '심사 중인 건만 반려할 수 있어요.');
            app.status = 'REJECTED';
            app.rejectCode = 'POLICY';
            app.rejectReason = '광고 이미지에 근거 없는 「최저가」 표현이 있어요. 표현을 고쳐 다시 신청해 주세요.';
            log(app, 'OPERATOR', 'REJECTED', app.rejectReason);
            break;
        case 'CONTRACT_AND_DEPOSIT':
            if (app.status !== 'AWAITING_DEPOSIT' || !app.deposit) throw conflict('BIZ_APPLICATION_STATE_CHANGED', '입금 대기 건만 확정할 수 있어요.');
            app.deposit.contract = 'RECEIVED';
            log(app, 'OPERATOR', 'CONTRACT_RECEIVED');
            app.deposit.depositConfirmed = true;
            log(app, 'OPERATOR', 'DEPOSIT_CONFIRMED', `${(app.confirmed?.total ?? 0).toLocaleString('en-US')}원 · 입금자 ${app.deposit.depositorName}`);
            app.status = 'SCHEDULED';
            app.depositDueAt = null;
            log(app, 'OPERATOR', 'SCHEDULED');
            break;
        case 'START':
            if (app.status !== 'SCHEDULED') throw conflict('BIZ_APPLICATION_STATE_CHANGED', '예약된 건만 시작할 수 있어요.');
            app.startDate = today();
            app.endDate = day(Math.max(1, length - 1));
            log(app, 'SYSTEM', 'STARTED');
            break;
        case 'END':
            if (app.status !== 'SCHEDULED') throw conflict('BIZ_APPLICATION_STATE_CHANGED', '예약된 건만 끝낼 수 있어요.');
            app.endDate = day(-1);
            if (app.startDate > app.endDate) app.startDate = day(-length);
            log(app, 'SYSTEM', 'ENDED');
            break;
        case 'NEAR_DUE':
            if (app.status !== 'AWAITING_PAYMENT') throw conflict('BIZ_APPLICATION_STATE_CHANGED', '결제 대기 건만 기한을 당길 수 있어요.');
            app.paymentDueAt = iso(3 * HOUR);
            break;
    }
    refresh(app);
    return mockClone(app);
}
