// MOCK: 사업자 핫딜 게시(2차) 목업 — `useBusinessApi` 의 `// MOCK:` 분기와 사업자 센터 화면의 `// MOCK:` 줄이 부른다. 서버가 붙으면 이 파일과 분기·줄을 지운다.
// 이 화면들은 이미 실제 API 에 연결돼 있어서 **`?mock=bizdeal-…` 스위치가 있을 때만**(신청 건은 목업 건 주소 `mock-bizdeal` 일 때만) 목업을 쓴다. 스위치가 없으면 지금 실제 동작 그대로다.
// 응답 문구는 서버 몫이라 번역 키로 옮기지 않는다(i18n.md 5절).
//
// 스위치(쉼표로 여러 개):
//  신청 화면 `/business/deals/new` · 센터 홈 `/business`
//    bizdeal-open        받기 켬(요금·게시판·노출 자리 목업, 이미지 올리기·제출 목업) — 아래 둘도 받기를 켠다
//    bizdeal-one-board   받는 게시판 1개(게시판 칸 숨김) · bizdeal-no-prefix  말머리 없는 게시판(말머리 칸 숨김, 말머리 상단 못 고름)
//    bizdeal-submit-fail 제출 500
//  신청 건 `/business/applications/mock-bizdeal` (상태 하나만 — 앞에 있는 것이 이긴다)
//    bizdeal-review(심사 중) · bizdeal-before(게시 전) · bizdeal-posted(게시됨 — 기본) · bizdeal-flagged(종료 요청 많음) ·
//    bizdeal-ended(종료 요청 자동 종료) · bizdeal-ended-me(사업자 종료) · bizdeal-ended-manager(운영진 종료) · bizdeal-expired(기간 종료) ·
//    bizdeal-failed(게시 못 함 — 게시판 닫힘, + bizdeal-fail-link · bizdeal-fail-suspended · bizdeal-fail-other 로 사유) ·
//    bizdeal-hidden(긴급 내림 → 글 숨김) · bizdeal-hidden-canceled(취소·환불 → 글 숨김)
//    덧붙임: bizdeal-no-placement(노출 자리 없이 글만) · bizdeal-end-error([품절로 종료] → 409 DEAL_ENDED)

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type {
    BizAmount,
    BizApplicationCreated,
    BizApplicationDetail,
    BizDeal,
    BizDealBoard,
    BizDealFailReason,
    BizDealPost,
    BizHistoryEntry,
    BusinessFile,
    DealApplyRequest,
    DealProduct,
} from '~/composables/business/api/useBusinessApi.types';
import UtilBusiness from '~/utils/util-business';

/** 목업 신청 건 ID — 실서버 ID(숫자)와 겹치지 않는다 */
export const MOCK_BIZDEAL_ID = 'mock-bizdeal';
/** 게시된 목업 글 — 핫딜 글 보기 목업(`hotdeal/api/mock/bizdeal-post-mock.ts`)과 같은 ID */
const MOCK_POST_ID = 'mock-bizdeal';

const POST_PRICE = 50_000;
const TOP_DAILY = 20_000;
const CATEGORY_DAILY = 10_000;

const BOARDS: readonly BizDealBoard[] = [
    {
        id: 'mock-board-hotdeal',
        slug: 'hotdeal',
        name: '핫딜',
        prefixes: [
            { id: 'mock-prefix-robot', name: '로봇' },
            { id: 'mock-prefix-parts', name: '부품' },
            { id: 'mock-prefix-ai', name: 'AI 기기' },
        ],
    },
    { id: 'mock-board-overseas', slug: 'hotdeal-overseas', name: '해외 핫딜', prefixes: [] },
];

/** 신청 화면 목업을 켜는 스위치 */
const OPEN_FLAGS = ['bizdeal-open', 'bizdeal-one-board', 'bizdeal-no-prefix'] as const;

type MockState = 'review' | 'before' | 'posted' | 'flagged' | 'ended' | 'ended-me' | 'ended-manager' | 'expired' | 'failed' | 'hidden' | 'hidden-canceled' | 'canceled';
const STATES: readonly Exclude<MockState, 'canceled'>[] = ['review', 'before', 'posted', 'flagged', 'ended', 'ended-me', 'ended-manager', 'expired', 'failed', 'hidden', 'hidden-canceled'];

/* 화면을 오가는 동안만 남는 목업 상태(새로 고침하면 처음으로) */
let submitted: DealApplyRequest | null = null;
let submittedImageUrl: string | null = null;
/** 목업으로 올린 이미지 ID → 미리보기 주소 */
const uploads = new Map<string, string>();
let override: MockState | null = null;
let refundAsked: { reason: string; at: string } | null = null;

/** 신청 화면 목업이 켜졌나(받기 켬) */
export function bizdealProductMockOn(): boolean {
    const flags = mockFlags();
    return OPEN_FLAGS.some((f) => flags.has(f));
}

/** 목업 신청 건 ID 인가 */
export function isMockBizdealId(id: string): boolean {
    return id === MOCK_BIZDEAL_ID;
}

/**
 * 지금 주소의 `?mock=` 을 다음 화면 링크에도 싣는다(목업 스위치가 화면을 건너도 이어지게) — 없으면 빈 객체.
 * 화면에서 바뀐 목업 상태(제출 → 심사 중, [품절로 종료] → 사업자 종료 등)가 있으면 상태 스위치를 그것 하나로 바꿔 싣는다(새로 고침·글 보기에서도 같은 상태).
 */
export function bizdealMockQuery(extra: string[] = []): Record<string, string> {
    let names = [...mockFlags(), ...extra];
    if (override && override !== 'canceled') names = [...names.filter((n) => !STATES.some((s) => n === `bizdeal-${s}`)), `bizdeal-${override}`];
    names = names.filter((v, i, all) => all.indexOf(v) === i);
    return names.length ? { mock: names.join(',') } : {};
}

function boardsOf(): BizDealBoard[] {
    const flags = mockFlags();
    let list = mockClone([...BOARDS]);
    if (flags.has('bizdeal-one-board')) list = list.slice(0, 1);
    if (flags.has('bizdeal-no-prefix')) list = list.map((b) => ({ ...b, prefixes: [] }));
    return list;
}

/** `GET /api/business/deal-products` — 받기 켬 + 받는 게시판 */
export async function mockBizdealProduct(): Promise<DealProduct> {
    await mockDelay();
    return {
        postPrice: POST_PRICE,
        accepting: true,
        placements: [
            { placement: 'HOME_TOP', dailyPrice: TOP_DAILY, capacity: 2, maxDays: 14, accepting: true },
            { placement: 'CATEGORY_TOP', dailyPrice: CATEGORY_DAILY, capacity: 2, maxDays: 14, accepting: true },
        ],
        boards: boardsOf(),
    };
}

/** `POST /api/business/ad-images` — 목업은 서버에 올리지 않고 `blob:` 미리보기만 */
export async function mockBizdealUpload(file: File): Promise<BusinessFile> {
    await mockDelay();
    const fileId = `mock-image-${Date.now()}`;
    const previewUrl = URL.createObjectURL(file);
    uploads.set(fileId, previewUrl);
    return { fileId, name: file.name, size: file.size, contentType: file.type, previewUrl };
}

/** `POST /api/business/applications/deals` — 받은 내용을 목업 건에 담아 두고 목업 건 ID 를 준다 */
export async function mockBizdealSubmit(body: DealApplyRequest): Promise<BizApplicationCreated> {
    await mockDelay();
    if (mockFlags().has('bizdeal-submit-fail')) throw MOCK_ERRORS.internal();
    const fields: Record<string, string> = {};
    const board = BOARDS.find((b) => b.id === body.deal.boardId);
    if (!board && boardsOf().length) fields['deal.boardId'] = '게시판을 골라 주세요.';
    if (board && board.prefixes.length && !mockFlags().has('bizdeal-no-prefix') && !body.deal.prefixId) fields['deal.prefixId'] = '말머리를 골라 주세요.';
    if (Object.keys(fields).length) throw MOCK_ERRORS.validation(fields);
    submitted = mockClone(body);
    submittedImageUrl = body.deal.imageFileId ? (uploads.get(body.deal.imageFileId) ?? null) : null;
    override = 'review';
    refundAsked = null;
    return { id: MOCK_BIZDEAL_ID };
}

function stateOf(): MockState {
    if (override) return override;
    const flags = mockFlags();
    return STATES.find((s) => flags.has(`bizdeal-${s}`)) ?? 'posted';
}

function failReasonOf(): BizDealFailReason {
    const flags = mockFlags();
    if (flags.has('bizdeal-fail-link')) return 'LINK_BLOCKED';
    if (flags.has('bizdeal-fail-suspended')) return 'BUSINESS_SUSPENDED';
    if (flags.has('bizdeal-fail-other')) return 'OTHER';
    return 'BOARD_CLOSED';
}

function amountOf(supply: number): BizAmount {
    const vat = Math.round(supply * UtilBusiness.VAT_RATE);
    return { supply, vat, total: supply + vat };
}

function hoursFrom(now: number, hours: number): string {
    return new Date(now + hours * 3_600_000).toISOString();
}

/** 상태별 기간 — 심사·게시 전은 앞으로, 진행 중 상태는 그제부터, 기간 종료는 어제까지 */
function periodOf(state: MockState, today: string): { start: string; end: string } {
    if (state === 'review') return { start: UtilBusiness.addDays(today, 1), end: UtilBusiness.addDays(today, 7) };
    if (state === 'before') return { start: UtilBusiness.addDays(today, 3), end: UtilBusiness.addDays(today, 9) };
    if (state === 'expired') return { start: UtilBusiness.addDays(today, -8), end: UtilBusiness.addDays(today, -1) };
    return { start: UtilBusiness.addDays(today, -2), end: UtilBusiness.addDays(today, 5) };
}

function dealOf(end: string): BizDeal {
    if (submitted) return { ...mockClone(submitted.deal), endsOn: submitted.deal.endsOn ?? end, imageUrl: submittedImageUrl };
    return {
        linkUrl: 'https://smartstore.naver.com/roboparts/products/1234567',
        shopName: '로보파츠상회',
        listPrice: 89_000,
        dealPrice: 59_000,
        shippingFee: 0,
        endsOn: end,
        stock: 'IN_STOCK',
        body: '서보모터 MG996R 10개 묶음이에요. 정상가 근거는 상품 상세의 정가 표시예요. 재고는 300세트 준비했어요.',
        placement: 'HOME_TOP',
        boardId: BOARDS[0]!.id,
        prefixId: BOARDS[0]!.prefixes[1]!.id,
        imageFileId: null,
        imageUrl: null,
    };
}

/** 지금 상태의 목업 신청 건 */
function buildApplication(): BizApplicationDetail {
    const now = Date.now();
    const today = UtilBusiness.todayYmd(now);
    const state = stateOf();
    const { start, end } = periodOf(state, today);
    const deal = dealOf(end);
    if (mockFlags().has('bizdeal-no-placement')) deal.placement = 'NONE';
    const board = BOARDS.find((b) => b.id === deal.boardId) ?? BOARDS[0]!;
    const prefixName = board.prefixes.find((p) => p.id === deal.prefixId)?.name ?? null;
    const days = UtilBusiness.daysBetween(start, end);
    const daily = deal.placement === 'HOME_TOP' ? TOP_DAILY : deal.placement === 'CATEGORY_TOP' ? CATEGORY_DAILY : 0;
    const amount = amountOf(POST_PRICE + daily * days);
    const publishAt = UtilBusiness.kstStartOf(start);
    const submittedAt = hoursFrom(Date.parse(publishAt), -72);
    const review = state === 'review' || state === 'canceled';
    const hiddenByCancel = state === 'hidden-canceled' || (refundAsked !== null && state === 'before');

    const history: BizHistoryEntry[] = [{ at: submittedAt, actor: 'ME', action: 'SUBMITTED', note: null }];
    if (!review) {
        history.push(
            { at: hoursFrom(Date.parse(submittedAt), 20), actor: 'OPERATOR', action: 'APPROVED_ONLINE', note: null },
            { at: hoursFrom(Date.parse(submittedAt), 22), actor: 'ME', action: 'PAID', note: null },
            { at: hoursFrom(Date.parse(submittedAt), 22), actor: 'SYSTEM', action: 'SCHEDULED', note: null },
        );
    }
    if (state === 'canceled') history.push({ at: hoursFrom(now, -0.01), actor: 'ME', action: 'CANCELED', note: null });

    const placementProduct = deal.placement === 'CATEGORY_TOP' ? 'HOTDEAL_CATEGORY' : 'HOTDEAL_TOP';
    const placement = (status: NonNullable<BizDealPost['placement']>['status'], stopReason: NonNullable<BizDealPost['placement']>['stopReason'] = null): BizDealPost['placement'] =>
        deal.placement === 'NONE' ? null : { product: placementProduct, status, stopReason };
    const base: Omit<BizDealPost, 'status'> = {
        postId: MOCK_POST_ID,
        boardSlug: board.slug,
        boardName: board.name,
        prefixName,
        publishAt,
        view: 'ACTIVE',
        endedBy: null,
        endRequests: { total: 1, bySoldOut: 1, byPriceChanged: 0, byLinkDead: 0 },
        failReason: null,
        placement: placement('SHOWING'),
        autoEndAt: null,
        endedAt: null,
    };
    const waiting: BizDealPost = { ...base, status: 'WAITING', postId: null, view: null, endRequests: null, placement: placement('WAITING') };
    const posted = (): void => void history.push({ at: publishAt, actor: 'SYSTEM', action: 'POSTED', note: null });

    let post: BizDealPost | null = null;
    let status: BizApplicationDetail['status'] = 'SCHEDULED';
    let takedownReason: string | null = null;
    switch (state) {
        case 'review':
            status = 'SUBMITTED';
            break;
        case 'canceled':
            status = 'CANCELED';
            break;
        case 'before':
            post = hiddenByCancel ? { ...waiting, status: 'HIDDEN', placement: placement('STOPPED', 'POST_HIDDEN') } : waiting;
            break;
        case 'posted':
            posted();
            post = { ...base, status: 'POSTED' };
            break;
        case 'flagged':
            posted();
            post = { ...base, status: 'POSTED', view: 'FLAGGED', endRequests: { total: 3, bySoldOut: 2, byPriceChanged: 1, byLinkDead: 0 }, autoEndAt: hoursFrom(now, 9) };
            break;
        case 'ended':
        case 'ended-me':
        case 'ended-manager': {
            posted();
            const endedAt = override === 'ended-me' ? hoursFrom(now, -0.01) : hoursFrom(now, -3);
            const endedBy = state === 'ended' ? 'AUTO' : state === 'ended-me' ? 'AUTHOR' : 'MANAGER';
            if (endedBy === 'AUTO') history.push({ at: endedAt, actor: 'SYSTEM', action: 'DEAL_AUTO_ENDED', note: null });
            if (endedBy === 'AUTHOR') history.push({ at: endedAt, actor: 'ME', action: 'DEAL_ENDED_BY_BUSINESS', note: null });
            post = {
                ...base,
                status: 'POSTED',
                view: 'ENDED',
                endedBy,
                endedAt,
                endRequests: endedBy === 'AUTO' ? { total: 4, bySoldOut: 3, byPriceChanged: 1, byLinkDead: 0 } : base.endRequests,
                placement: placement('STOPPED', 'DEAL_ENDED'),
            };
            break;
        }
        case 'expired':
            posted();
            post = { ...base, status: 'POSTED', view: 'EXPIRED', placement: placement('ENDED') };
            break;
        case 'failed': {
            const reason = failReasonOf();
            history.push({ at: publishAt, actor: 'SYSTEM', action: 'POST_FAILED', note: reason });
            post = { ...waiting, status: 'FAILED', failReason: reason };
            break;
        }
        case 'hidden':
            posted();
            status = 'TAKEN_DOWN';
            takedownReason = '상품 링크가 다른 상품으로 바뀌어 노출을 멈췄어요. 링크를 확인한 뒤 되돌릴게요.';
            history.push(
                { at: hoursFrom(now, -5), actor: 'OPERATOR', action: 'TAKEN_DOWN', note: takedownReason },
                { at: hoursFrom(now, -5), actor: 'SYSTEM', action: 'POST_HIDDEN', note: null },
            );
            post = { ...base, status: 'HIDDEN', placement: placement('STOPPED', 'POST_HIDDEN') };
            break;
        case 'hidden-canceled':
            posted();
            status = 'CANCELED';
            history.push(
                { at: hoursFrom(now, -6), actor: 'ME', action: 'REFUND_REQUESTED', note: '행사가 일찍 끝났어요.' },
                { at: hoursFrom(now, -4), actor: 'OPERATOR', action: 'REFUND_APPROVED', note: String(Math.floor(amount.total * 0.6)) },
                { at: hoursFrom(now, -4), actor: 'SYSTEM', action: 'POST_HIDDEN', note: null },
            );
            post = { ...base, status: 'HIDDEN', placement: placement('STOPPED', 'POST_HIDDEN') };
            break;
    }

    // [취소·환불 요청]을 눌렀다 — 시작 전이면 바로 전액 취소(글은 만들지 않고 숨김), 진행 중이면 운영팀 확인
    let refund: BizApplicationDetail['refund'] = null;
    if (state === 'hidden-canceled') {
        refund = { type: 'PRORATED_REVIEW', status: 'APPROVED', reason: '행사가 일찍 끝났어요.', requestedAt: hoursFrom(now, -6), proposedAmount: Math.floor(amount.total * 0.6), refundAmount: Math.floor(amount.total * 0.6), decisionReason: null };
    } else if (refundAsked) {
        const upcoming = state === 'before';
        if (upcoming) status = 'CANCELED';
        history.push({ at: refundAsked.at, actor: 'ME', action: 'REFUND_REQUESTED', note: refundAsked.reason });
        if (upcoming) history.push({ at: refundAsked.at, actor: 'SYSTEM', action: 'REFUNDED', note: String(amount.total) });
        refund = {
            type: upcoming ? 'FULL_AUTO' : 'PRORATED_REVIEW',
            status: upcoming ? 'APPROVED' : 'REQUESTED',
            reason: refundAsked.reason,
            requestedAt: refundAsked.at,
            proposedAmount: upcoming ? amount.total : Math.floor(amount.total * 0.5),
            refundAmount: upcoming ? amount.total : null,
            decisionReason: null,
        };
    }

    const phase = status === 'SCHEDULED' ? (state === 'before' ? 'UPCOMING' : state === 'expired' ? 'ENDED' : 'RUNNING') : null;
    return {
        id: MOCK_BIZDEAL_ID,
        kind: 'HOTDEAL_POST',
        title: submitted?.title ?? 'MG996R 서보모터 10개 묶음 특가',
        adType: null,
        placement: deal.placement,
        startDate: start,
        endDate: end,
        status,
        phase,
        paymentMethod: 'ONLINE',
        amount,
        amountConfirmed: !review,
        paymentDueAt: null,
        depositDueAt: null,
        submittedAt,
        refundRequested: refund !== null,
        estimate: amount,
        confirmed: review ? null : amount,
        adjustReason: null,
        creative: null,
        deal,
        rejectCode: null,
        rejectReason: null,
        takedownReason,
        deposit: null,
        refund,
        history: history.sort((a, b) => Date.parse(a.at) - Date.parse(b.at)),
        post,
    };
}

/** `GET /api/business/applications/mock-bizdeal` */
export async function mockBizdealApplication(): Promise<BizApplicationDetail> {
    await mockDelay();
    return mockClone(buildApplication());
}

/** `POST /api/business/applications/mock-bizdeal/deal/end` — 사업자 종료(환불 없음, 노출도 멈춤) → 새 `BizDealPost` */
export async function mockBizdealEnd(): Promise<BizDealPost> {
    await mockDelay();
    if (mockFlags().has('bizdeal-end-error')) {
        // 그새 종료 요청으로 자동 종료됐다 — 다시 읽으면 종료로 보인다
        override = 'ended';
        throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    }
    const state = stateOf();
    if (state !== 'posted' && state !== 'flagged') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    override = 'ended-me';
    return mockClone(buildApplication().post!);
}

/** `POST …/mock-bizdeal/cancel` — 심사 중 취소 */
export async function mockBizdealCancel(): Promise<BizApplicationDetail> {
    await mockDelay();
    if (stateOf() !== 'review') throw new ApiError({ status: 409, code: 'BIZ_APPLICATION_STATE_CHANGED', message: '이미 처리 상태가 바뀌었어요.' });
    override = 'canceled';
    return mockClone(buildApplication());
}

/** `POST …/mock-bizdeal/refund-request` — 시작 전이면 전액 자동 취소(글 숨김), 진행 중이면 운영팀 확인 */
export async function mockBizdealRefund(reason: string): Promise<BizApplicationDetail> {
    await mockDelay();
    if (refundAsked) throw new ApiError({ status: 409, code: 'BIZ_APPLICATION_STATE_CHANGED', message: '이미 환불을 요청했어요.' });
    override = stateOf();
    refundAsked = { reason, at: new Date().toISOString() };
    return mockClone(buildApplication());
}
