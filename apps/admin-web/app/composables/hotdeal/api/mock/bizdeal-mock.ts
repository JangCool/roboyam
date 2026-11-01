// MOCK: 사업자 핫딜 게시(2차) — 관리자 핫딜 글(`/hotdeals`) 화면 목업. `useHotdealApi` 의 `// MOCK:` 줄이 부른다.
// 이 화면은 이미 실제 API(`/api/admin/hotdeals`)에 붙어 있어서, **`?mock=bizdeal-board` 일 때만** 목업 사업자 글을 실제 목록 맨 위(1쪽)에 끼운다(스위치가 없으면 실제 응답 그대로).
// 목업 글 번호는 99000000N — 상세·종료·재개·수정 이력은 `bizdeal-` 스위치가 하나라도 있을 때 그 번호면 목업이 받는다(핫딜 심사의 [핫딜 글에서 열기]가 스위치를 싣고 온다).
// 서버가 출처 거르기(`source`)·줄의 `source`·`business` 를 내려 주면 이 파일과 MOCK 줄을 지운다.
//
// ── 개발 전용 스위치 ─
//   bizdeal-board   목업 사업자 글 4줄(진행 · 사업자 종료 · 자동 종료 · 기간 지남) — 상호 「로봇마켓」, [핫딜 심사에서 열기]는 목업 신청 건(`mock-bizdeal-…`)으로 간다
//   bizdeal-all     위와 같음(핫딜 심사 목업 전부와 같이 켠다)
// 출처 「사업자」를 고르면 목업 글만 보인다(실서버가 아직 `source` 를 모른다). 「회원」이면 실제 목록 그대로.
// 목업 글의 종료·재개는 메모리에서만 바뀐다(새로고침하면 처음으로). 실제 목록을 못 받으면(서버 꺼짐) 목업 글만 보인다.

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { AdminDealDetail, AdminDealPage, AdminDealQuery, AdminDealRow, DealRevisionListView, DealView, DealViewFilter, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import { DEAL_VIEW_FILTERS } from '~/composables/hotdeal/api/useHotdealApi.types';

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;
const ago = (ms: number) => new Date(Date.now() - ms).toISOString();
const later = (ms: number) => new Date(Date.now() + ms).toISOString();
const ME = '관리자(나)';

/** 목업 사업자 글을 목록에 끼우는가 */
export function mockBizDealBoardOn(): boolean {
    const on = mockFlags();
    return on.has('bizdeal-board') || on.has('bizdeal-all');
}

/** 목업 글 번호를 받아 주는가 — `bizdeal-` 스위치가 하나라도 */
function anyOn(): boolean {
    return [...mockFlags()].some((f) => f.startsWith('bizdeal-'));
}

interface Seed {
    postId: string;
    applicationId: string;
    title: string;
    view: DealView;
    endedBy: PostDeal['endedBy'];
    requests: [number, number, number];
    createdAgo: number;
    stock: PostDeal['stockState'];
}

const SEEDS: Seed[] = [
    { postId: '990000001', applicationId: 'mock-bizdeal-posted', title: '[로봇마켓] 로봇청소기 R7 한정 수량 특가', view: 'ACTIVE', endedBy: null, requests: [1, 0, 0], createdAgo: 2 * DAY, stock: 'LOW' },
    { postId: '990000002', applicationId: 'mock-bizdeal-ended', title: '[로봇마켓] 서보 모터 20개 묶음 특가', view: 'ENDED', endedBy: 'AUTHOR', requests: [0, 0, 0], createdAgo: 2 * DAY + HOUR, stock: 'IN_STOCK' },
    { postId: '990000003', applicationId: 'mock-bizdeal-auto-ended', title: '[로봇마켓] 3D 프린터 필라멘트 1+1', view: 'ENDED', endedBy: 'AUTO', requests: [2, 1, 0], createdAgo: 2 * DAY + 2 * HOUR, stock: 'IN_STOCK' },
    { postId: '990000004', applicationId: 'mock-bizdeal-expired', title: '[로봇마켓] 아두이노 호환 보드 세트', view: 'EXPIRED', endedBy: null, requests: [0, 0, 0], createdAgo: 9 * DAY, stock: 'LOW' },
];

/** 종료·재개로 바뀐 상태 — 새로고침하면 처음으로 */
const views = new Map<string, { view: DealView; endedBy: PostDeal['endedBy']; endedAt: string | null; reason: string | null }>();

function build(s: Seed): AdminDealDetail {
    const changed = views.get(s.postId);
    const view = changed?.view ?? s.view;
    const endedBy = changed ? changed.endedBy : s.endedBy;
    const createdAt = ago(s.createdAgo);
    const ended = view === 'ENDED';
    const total = s.requests[0] + s.requests[1] + s.requests[2];
    const deal: PostDeal = {
        source: 'BUSINESS',
        storeName: '로봇마켓',
        productUrl: `https://robotmarket.example/deal/${s.postId}`,
        productHost: 'robotmarket.example',
        finalHost: null,
        normalPrice: 259_000,
        dealPrice: 189_000,
        discountRate: 27,
        shippingFee: 0,
        couponNote: null,
        priceBasisKind: 'LISTED',
        dutyIncluded: false,
        priceBasis: null,
        // 딜 마감 = 신청 기간 끝
        endsAt: s.view === 'EXPIRED' ? ago(2 * DAY) : later(5 * DAY),
        autoExpireAt: null,
        stockState: s.stock,
        affiliate: false,
        image: { url: `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240"><rect width="240" height="240" fill="#e2e8f0"/><circle cx="120" cy="110" r="56" fill="#94a3b8"/><rect x="70" y="180" width="100" height="18" rx="9" fill="#64748b"/></svg>')}`, source: 'UPLOAD' },
        status: ended ? 'ENDED' : 'ACTIVE',
        view,
        endedBy: ended ? endedBy : null,
        endedAt: ended ? (changed?.endedAt ?? ago(5 * HOUR)) : null,
        reopenUntil: null,
        endRequests: { total, bySoldOut: s.requests[0], byPriceChanged: s.requests[1], byLinkDead: s.requests[2], mine: null },
        flaggedAt: endedBy === 'AUTO' && ended ? ago(15 * HOUR) : null,
        autoEndAt: null,
        keepCount: 0,
        keepMax: 2,
        revision: { priceEdits: 0, linkChanged: false, lastEditedAt: null },
        hot: false,
        sameDealCount: 0,
        affiliateKind: null,
        siteProgramName: null,
    };
    const row: AdminDealRow = {
        postId: s.postId,
        title: s.title,
        board: { id: '901', slug: 'hotdeal', name: '핫딜', nameEn: 'Hot deals' },
        prefix: { id: '9011', name: '로봇' },
        author: { id: 'mock-biz-member-1', nickname: '로봇마켓', withdrawn: false },
        createdAt,
        openReports: 0,
        blockedHost: null,
        deal,
        source: 'BUSINESS',
        business: { name: '로봇마켓', applicationId: s.applicationId },
    };
    return {
        row,
        endedByName: ended ? (endedBy === 'MANAGER' ? ME : endedBy === 'AUTHOR' ? '로봇마켓' : null) : null,
        endedByRole: ended ? (endedBy === 'MANAGER' ? 'OPERATOR' : endedBy === 'AUTHOR' ? 'AUTHOR' : null) : null,
        endReason: changed?.reason ?? (endedBy === 'AUTHOR' && ended ? '품절' : null),
        keptAt: null,
        publicUrl: null,
        sameDeals: [],
        affiliateMode: 'MEMBER',
    };
}

function seedOf(postId: string): Seed | null {
    return SEEDS.find((s) => s.postId === postId) ?? null;
}

function matches(d: AdminDealDetail, query: AdminDealQuery, withView: boolean): boolean {
    const r = d.row;
    if (withView && query.view !== 'ALL' && r.deal.view !== query.view) return false;
    if (query.board && query.board !== r.board.id) return false;
    if (query.blockedActive) return false;
    const q = query.q?.trim().toLowerCase();
    if (q && !`${r.title} ${r.deal.storeName} ${r.author.nickname}`.toLowerCase().includes(q)) return false;
    return true;
}

/** 목록 — 스위치가 없으면 실제 응답 그대로. 있으면 1쪽 맨 위에 목업 사업자 글(출처 「사업자」면 목업 글만) */
export async function mockBizDealBoardList(query: AdminDealQuery, real: () => Promise<AdminDealPage>): Promise<AdminDealPage> {
    if (!mockBizDealBoardOn() || query.source === 'MEMBER') return real();
    const emptyCounts = () => Object.fromEntries(DEAL_VIEW_FILTERS.map((k) => [k, 0])) as Record<DealViewFilter, number>;
    let found: AdminDealPage;
    if (query.source === 'BUSINESS') {
        await mockDelay();
        found = { total: 0, page: query.page, size: query.size, items: [], counts: emptyCounts(), blockedActiveCount: 0 };
    } else {
        try {
            found = await real();
        } catch {
            found = { total: 0, page: query.page, size: query.size, items: [], counts: emptyCounts(), blockedActiveCount: 0 };
        }
    }
    const all = SEEDS.map(build).filter((d) => matches(d, query, false));
    const counts = { ...found.counts };
    for (const d of all) {
        counts[d.row.deal.view] = (counts[d.row.deal.view] ?? 0) + 1;
        counts.ALL = (counts.ALL ?? 0) + 1;
    }
    const here = all.filter((d) => matches(d, query, true)).map((d) => d.row);
    return { ...found, total: found.total + here.length, items: query.page === 0 ? [...here, ...found.items] : found.items, counts };
}

/** 상세 — 목업 글 번호이고 `bizdeal-` 스위치가 있으면 목업, 아니면 null(실제 API 로) */
export function mockBizDealBoardDetail(postId: string): Promise<AdminDealDetail> | null {
    const s = anyOn() ? seedOf(postId) : null;
    if (!s) return null;
    return mockDelay().then(() => mockClone(build(s)));
}

/** 수정 이력 — 사업자 글은 게시 뒤 수정이 없다(빈 이력) */
export function mockBizDealBoardRevisions(postId: string): Promise<DealRevisionListView> | null {
    if (!anyOn() || !seedOf(postId)) return null;
    return mockDelay().then(() => ({ items: [], retentionMonths: 6 }));
}

/** 운영진 종료·재개 — 회원 글과 같은 규칙(종료는 진행·의심·기간 지남, 재개는 종료만). 목업 글이 아니면 null */
export function mockBizDealBoardAct(postId: string, kind: 'END' | 'REOPEN', reason?: string): Promise<void> | null {
    const s = anyOn() ? seedOf(postId) : null;
    if (!s) return null;
    return mockDelay().then(() => {
        const view = build(s).row.deal.view;
        if ((kind === 'END' && view === 'ENDED') || (kind === 'REOPEN' && view !== 'ENDED')) {
            throw MOCK_ERRORS.conflict('HOTDEAL_STATE_CHANGED', '그 사이 다른 운영자가 처리했어요. 최신 상태로 다시 불러올게요.');
        }
        if (kind === 'END') views.set(s.postId, { view: 'ENDED', endedBy: 'MANAGER', endedAt: new Date().toISOString(), reason: reason ?? null });
        else views.set(s.postId, { view: s.view === 'EXPIRED' ? 'EXPIRED' : 'ACTIVE', endedBy: null, endedAt: null, reason: null });
    });
}
