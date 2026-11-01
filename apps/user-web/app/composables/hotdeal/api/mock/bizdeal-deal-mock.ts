// MOCK: 사업자 핫딜 글(2차 — `source=BUSINESS`)의 딜 칸·딜 동작 목업 — `useHotdealApi` 의 `// MOCK:` 분기와 게시판 목업(`board/api/mock/bizdeal-board-mock.ts`)이 부른다.
// 목업 글 ID(`mock-bizdeal`·`mock-bizdeal-2`)일 때만 돈다 — 실서버 글은 그대로. 서버가 붙으면 두 목업과 분기를 함께 지운다. 응답 문구는 서버 몫(i18n.md 5절).
//
// 글 `mock-bizdeal` 의 딜 상태 스위치(`?mock=`): bizdeal-flagged(종료 요청 많음) · bizdeal-ended(자동 종료) · bizdeal-ended-me(사업자 종료) · bizdeal-ended-manager(운영진 종료) ·
//   bizdeal-expired(기간 종료) · bizdeal-hidden(글 숨김 — 글 보기 403 POST_HIDDEN) · bizdeal-mine(보는 사람 = 그 사업자 — 「아직 진행 중이에요」·종료가 보인다). 기본은 진행 중.

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { DealEndReason, DealEndedBy, DealView, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';

/** 목업 사업자 글 하나 — 글 목업이 제목·본문·작성자를 만든다 */
export interface MockBizdealPostMeta {
    id: string;
    title: string;
    content: string;
    businessName: string;
    storeName: string;
    productUrl: string;
    normalPrice: number;
    dealPrice: number;
    stock: PostDeal['stockState'];
    /** 몇 시간 전에 올라갔나(게시 시각 = 신청 기간 시작) */
    hoursAgo: number;
    /** 딜 마감까지 남은 날(신청 기간 끝) */
    daysLeft: number;
    likeCount: number;
    viewCount: number;
}

export const MOCK_BIZDEAL_POSTS: readonly MockBizdealPostMeta[] = [
    {
        id: 'mock-bizdeal',
        title: 'MG996R 서보모터 10개 묶음 특가',
        content: '서보모터 MG996R 10개 묶음이에요.\n\n정상가 근거는 상품 상세의 정가 표시예요. 재고는 300세트 준비했어요. 사업자가 비용을 내고 올린 광고 글이에요(목업).',
        businessName: '로보파츠상회',
        storeName: '네이버 스마트스토어',
        productUrl: 'https://smartstore.naver.com/roboparts/products/1234567',
        normalPrice: 89_000,
        dealPrice: 59_000,
        stock: 'IN_STOCK',
        hoursAgo: 40,
        daysLeft: 5,
        likeCount: 12,
        viewCount: 820,
    },
    {
        id: 'mock-bizdeal-2',
        title: 'ESP32-S3 개발보드 5개 세트',
        content: 'ESP32-S3 N16R8 개발보드 5개 세트예요. 핀헤더 납땜 완료품이에요(목업).',
        businessName: '메이커랩',
        storeName: '메이커랩몰',
        productUrl: 'https://makerlab.example.com/goods/esp32-s3-5set',
        normalPrice: 45_000,
        dealPrice: 32_000,
        stock: 'LOW',
        hoursAgo: 6,
        daysLeft: 2,
        likeCount: 3,
        viewCount: 140,
    },
];

/** 끝난 뒤 판정(의심 문턱·유예) — 회원 핫딜 기본값과 같다 */
const THRESHOLD = 3;
const GRACE_HOURS = 12;
const KEEP_MAX = 2;

/** 목업 사업자 글 ID 인가 */
export function isMockBizdealPostId(id: string): boolean {
    return MOCK_BIZDEAL_POSTS.some((p) => p.id === id);
}

/** 보는 사람이 그 사업자(글쓴이)인가 — 스위치 `bizdeal-mine` */
export function mockBizdealMine(): boolean {
    return mockFlags().has('bizdeal-mine');
}

/** 글 보기를 가리는 스위치 — `bizdeal-hidden` 이면 글 `mock-bizdeal` 은 숨김(403) */
export function mockBizdealHidden(id: string): boolean {
    return id === MOCK_BIZDEAL_POSTS[0]!.id && mockFlags().has('bizdeal-hidden');
}

/* 화면을 오가는 동안만 남는 동작 결과(새로 고침하면 처음으로) */
const changed = new Map<string, PostDeal>();

function hoursFrom(now: number, hours: number): string {
    return new Date(now + hours * 3_600_000).toISOString();
}

function meta(id: string): MockBizdealPostMeta {
    const p = MOCK_BIZDEAL_POSTS.find((x) => x.id === id);
    if (!p) throw new ApiError({ status: 404, code: 'POST_NOT_FOUND', message: '글을 찾을 수 없어요.' });
    return p;
}

/** 스위치로 고른 첫 글의 딜 상태 */
function flagState(id: string): { view: DealView; endedBy: DealEndedBy | null } {
    if (id !== MOCK_BIZDEAL_POSTS[0]!.id) return { view: 'ACTIVE', endedBy: null };
    const flags = mockFlags();
    if (flags.has('bizdeal-flagged')) return { view: 'FLAGGED', endedBy: null };
    if (flags.has('bizdeal-ended')) return { view: 'ENDED', endedBy: 'AUTO' };
    if (flags.has('bizdeal-ended-me')) return { view: 'ENDED', endedBy: 'AUTHOR' };
    if (flags.has('bizdeal-ended-manager')) return { view: 'ENDED', endedBy: 'MANAGER' };
    if (flags.has('bizdeal-expired')) return { view: 'EXPIRED', endedBy: null };
    return { view: 'ACTIVE', endedBy: null };
}

function build(id: string, now: number): PostDeal {
    const p = meta(id);
    const { view, endedBy } = flagState(id);
    const flagged = view === 'FLAGGED';
    const ended = view === 'ENDED';
    const requests = flagged ? { total: 3, bySoldOut: 2, byPriceChanged: 1, byLinkDead: 0 } : endedBy === 'AUTO' ? { total: 4, bySoldOut: 3, byPriceChanged: 1, byLinkDead: 0 } : { total: 1, bySoldOut: 1, byPriceChanged: 0, byLinkDead: 0 };
    const endsAt = view === 'EXPIRED' ? hoursFrom(now, -5) : hoursFrom(now, p.daysLeft * 24);
    return {
        source: 'BUSINESS',
        business: { name: p.businessName },
        storeName: p.storeName,
        productUrl: p.productUrl,
        productHost: new URL(p.productUrl).hostname.replace(/^www\./, ''),
        finalHost: null,
        normalPrice: p.normalPrice,
        dealPrice: p.dealPrice,
        discountRate: Math.floor(((p.normalPrice - p.dealPrice) / p.normalPrice) * 100),
        shippingFee: 0,
        couponNote: null,
        priceBasisKind: 'LISTED',
        dutyIncluded: false,
        priceBasis: null,
        endsAt,
        autoExpireAt: null,
        stockState: ended && endedBy === 'AUTHOR' ? 'SOLD_OUT' : p.stock,
        affiliate: false,
        image: null,
        status: ended ? 'ENDED' : 'ACTIVE',
        view,
        endedBy,
        endedAt: ended ? hoursFrom(now, -3) : null,
        // 사업자 글은 되돌리기(D2)가 없다 — 종료하면 다시 열 수 없다
        reopenUntil: null,
        endRequests: { ...requests, mine: null },
        flaggedAt: flagged ? hoursFrom(now, -3) : null,
        autoEndAt: flagged ? hoursFrom(now, GRACE_HOURS - 3) : null,
        keepCount: 0,
        keepMax: KEEP_MAX,
        revision: { priceEdits: 0, linkChanged: false, lastEditedAt: null },
        // 사업자 글은 인기글·HOT 집계에서 빠진다(계약 서버 규칙 10)
        hot: false,
        sameDealCount: 0,
        // 목업은 서버 경유 주소(`/api/deals/{id}/go`)가 없어 상품 주소를 그대로 쓴다
        buyUrl: p.productUrl,
        affiliateKind: null,
        siteProgramName: null,
    };
}

/** 지금 딜(동작을 했으면 그 결과) */
export function mockBizdealDeal(id: string): PostDeal {
    return mockClone(changed.get(id) ?? build(id, Date.now()));
}

function save(id: string, deal: PostDeal): PostDeal {
    changed.set(id, deal);
    return mockClone(deal);
}

/** `POST /api/posts/{id}/deal/end-requests` — 사업자 글도 회원 글과 같게 받는다 */
export async function mockBizdealEndRequest(id: string, reason: DealEndReason): Promise<PostDeal> {
    await mockDelay();
    const deal = mockBizdealDeal(id);
    if (mockBizdealMine()) throw new ApiError({ status: 403, code: 'DEAL_END_REQUEST_OWN', message: '내 글에는 알릴 수 없어요.' });
    if (deal.view === 'ENDED' || deal.view === 'EXPIRED') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    if (deal.endRequests.mine) throw new ApiError({ status: 409, code: 'DEAL_END_REQUEST_EXISTS', message: '이미 알렸어요.' });
    const r = deal.endRequests;
    r.mine = reason;
    r.total += 1;
    if (reason === 'SOLD_OUT') r.bySoldOut += 1;
    if (reason === 'PRICE_CHANGED') r.byPriceChanged += 1;
    if (reason === 'LINK_DEAD') r.byLinkDead += 1;
    if (deal.view === 'ACTIVE' && r.total >= THRESHOLD) {
        const now = Date.now();
        deal.view = 'FLAGGED';
        deal.flaggedAt = new Date(now).toISOString();
        deal.autoEndAt = hoursFrom(now, GRACE_HOURS);
    }
    return save(id, deal);
}

/** `POST /api/posts/{id}/deal/end` — 사업자 종료(환불 없음, 노출도 멈춤 — 되돌리기 없음) */
export async function mockBizdealAuthorEnd(id: string): Promise<PostDeal> {
    await mockDelay();
    const deal = mockBizdealDeal(id);
    if (deal.view === 'ENDED' || deal.view === 'EXPIRED') throw new ApiError({ status: 409, code: 'DEAL_ENDED', message: '이미 끝난 딜이에요.' });
    return save(id, { ...deal, status: 'ENDED', view: 'ENDED', endedBy: 'AUTHOR', endedAt: new Date().toISOString(), reopenUntil: null, flaggedAt: null, autoEndAt: null });
}

/** `POST /api/posts/{id}/deal/keep` — 「아직 진행 중이에요」 */
export async function mockBizdealKeep(id: string): Promise<PostDeal> {
    await mockDelay();
    const deal = mockBizdealDeal(id);
    if (deal.view !== 'FLAGGED') throw new ApiError({ status: 409, code: 'DEAL_NOT_FLAGGED', message: '확인할 알림이 없어요.' });
    if (deal.keepCount >= deal.keepMax) throw new ApiError({ status: 409, code: 'DEAL_KEEP_LIMIT', message: '운영자가 확인하고 있어요.' });
    return save(id, { ...deal, view: 'ACTIVE', flaggedAt: null, autoEndAt: null, keepCount: deal.keepCount + 1 });
}

/** 되돌리기 — 사업자 글은 없다 */
export async function mockBizdealReopen(): Promise<PostDeal> {
    await mockDelay();
    throw new ApiError({ status: 409, code: 'DEAL_REOPEN_USED', message: '사업자 핫딜은 다시 열 수 없어요.' });
}

/** 게시판 관리 — 목업 글에는 매니저·스텝 자리가 없다 */
export async function mockBizdealModerate(): Promise<PostDeal> {
    await mockDelay();
    throw new ApiError({ status: 403, code: 'DEAL_MODERATE_FORBIDDEN', message: '이 게시판을 관리하는 회원이 아니에요.' });
}

/** 같은 딜 — 목업 사업자 글은 없음 */
export async function mockBizdealSame(): Promise<[]> {
    await mockDelay();
    return [];
}
