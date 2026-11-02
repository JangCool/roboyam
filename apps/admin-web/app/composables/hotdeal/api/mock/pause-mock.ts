// MOCK: 사업자 핫딜 「일시 정지 / 중단」 — 관리자 핫딜 글(`/hotdeals`) 목업. `useHotdealApi` 의 `// MOCK:` 줄이 부른다.
// 이 화면은 이미 실제 API(`/api/admin/hotdeals`)에 붙어 있어서, **`?mock=pause-…` 스위치가 있을 때만** 핫딜 심사 목업 건(`bizapplication/api/mock/pause-mock.ts`)의 글을
// 실제 목록 1쪽 맨 위에 끼운다(스위치가 없으면 실제 응답 그대로). 글 번호는 99000010N, 상호 「로봇마켓」, [핫딜 심사에서 열기]는 그 목업 신청 건으로 간다.
// 판매자가 일시 정지·중단해 숨긴 글은 줄의 `sellerHidden` 으로 「판매자 정지 중」·「판매자 중단」 Tag, 서랍에 「{시각} {누가} 숨김(일시 정지/중단)」 — 글·종료 요청·신고는 그대로 보인다.
// 핫딜 심사에서 대신 정지·다시 켜기·중단하면 여기도 바뀐다(같은 메모리). 목업 글의 종료·재개는 메모리에서만(새로고침하면 처음으로).
// 서버가 줄·상세에 `sellerHidden` 을 내려 주면 이 파일과 MOCK 줄을 지운다.

import { mockPauseDetails } from '~/composables/bizapplication/api/mock/pause-mock';
import type { BizApplicationDetail } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import { MOCK_ERRORS, mockClone, mockDelay } from '~/composables/common/mock/mock-util';
import type { AdminDealDetail, AdminDealPage, AdminDealQuery, AdminDealRow, DealRevisionListView, DealView, DealViewFilter, PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import { DEAL_VIEW_FILTERS } from '~/composables/hotdeal/api/useHotdealApi.types';

const ME = '관리자(나)';
const DEAL_IMG = `data:image/svg+xml;utf8,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240"><rect width="240" height="240" fill="#e2e8f0"/><circle cx="120" cy="110" r="56" fill="#94a3b8"/><rect x="70" y="180" width="100" height="18" rx="9" fill="#64748b"/></svg>')}`;

/** 운영진 종료·재개로 바뀐 딜 상태 — 새로고침하면 처음으로 */
const views = new Map<string, { view: DealView; endedAt: string | null; reason: string | null }>();

/** 켠 핫딜 심사 목업 건 중 글이 있는 것 */
function cases(): BizApplicationDetail[] {
    return mockPauseDetails('HOTDEAL').filter((d) => d.post?.postId && d.content.type === 'HOTDEAL_POST');
}

function build(d: BizApplicationDetail): AdminDealDetail {
    const post = d.post!;
    const content = d.content as Extract<BizApplicationDetail['content'], { type: 'HOTDEAL_POST' }>;
    const postId = post.postId!;
    const changed = views.get(postId);
    const view: DealView = changed?.view ?? post.view ?? 'ACTIVE';
    const ended = view === 'ENDED';
    const er = post.endRequests ?? { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0 };
    const deal: PostDeal = {
        source: 'BUSINESS',
        storeName: content.mallName,
        productUrl: content.dealUrl,
        productHost: 'robotmarket.example',
        finalHost: null,
        normalPrice: content.originalPrice,
        dealPrice: content.price,
        discountRate: content.originalPrice && content.originalPrice > content.price ? Math.floor(((content.originalPrice - content.price) * 100) / content.originalPrice) : null,
        shippingFee: content.shippingFee ?? null,
        couponNote: null,
        priceBasisKind: 'LISTED',
        dutyIncluded: false,
        priceBasis: null,
        // 딜 마감 = 신청 기간 끝
        endsAt: d.period.endAt,
        autoExpireAt: null,
        stockState: 'IN_STOCK',
        affiliate: false,
        image: { url: content.imageUrl ?? DEAL_IMG, source: 'UPLOAD' },
        status: ended ? 'ENDED' : 'ACTIVE',
        view,
        endedBy: ended ? 'MANAGER' : null,
        endedAt: ended ? changed?.endedAt ?? null : null,
        reopenUntil: null,
        endRequests: { ...er, mine: null },
        flaggedAt: null,
        autoEndAt: null,
        keepCount: 0,
        keepMax: 2,
        revision: { priceEdits: 0, linkChanged: false, lastEditedAt: null },
        hot: false,
        sameDealCount: 0,
        affiliateKind: null,
        siteProgramName: null,
    };
    const t = d.takedown;
    const hidden = post.status === 'HIDDEN' && t && (t.cause === 'ADVERTISER_PAUSE' || t.cause === 'ADVERTISER_STOP');
    const row: AdminDealRow = {
        postId,
        title: content.title,
        board: { id: '901', slug: 'hotdeal', name: '핫딜', nameEn: 'Hot deals' },
        prefix: { id: '9011', name: '로봇' },
        author: { id: 'mock-biz-member-1', nickname: d.business.name, withdrawn: false },
        createdAt: post.publishAt,
        // 숨겨도 신고는 그대로 보인다(정지 중 건 하나에 신고 1)
        openReports: d.id.endsWith('-paused') ? 1 : 0,
        blockedHost: null,
        deal,
        source: 'BUSINESS',
        business: { name: d.business.name, applicationId: d.id },
        sellerHidden: hidden ? { cause: t.cause as 'ADVERTISER_PAUSE' | 'ADVERTISER_STOP', at: t.at, by: t.by ?? '—', byOperator: Boolean(t.channel) } : null,
    };
    return {
        row,
        endedByName: ended ? ME : null,
        endedByRole: ended ? 'OPERATOR' : null,
        endReason: changed?.reason ?? null,
        keptAt: null,
        publicUrl: null,
        sameDeals: [],
        affiliateMode: 'MEMBER',
    };
}

function find(postId: string): AdminDealDetail | null {
    const d = cases().find((x) => x.post?.postId === postId);
    return d ? build(d) : null;
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

/** 목록 — 스위치가 없으면 실제 응답 그대로. 있으면 1쪽 맨 위에 목업 사업자 글(출처 「회원」이면 그대로, 「사업자」면 목업 글만) */
export async function mockPauseDealList(query: AdminDealQuery, real: () => Promise<AdminDealPage>): Promise<AdminDealPage> {
    const all = cases().map(build);
    if (all.length === 0 || query.source === 'MEMBER') return real();
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
    const mine = all.filter((d) => matches(d, query, false));
    const counts = { ...found.counts };
    for (const d of mine) {
        counts[d.row.deal.view] = (counts[d.row.deal.view] ?? 0) + 1;
        counts.ALL = (counts.ALL ?? 0) + 1;
    }
    const here = mine.filter((d) => matches(d, query, true)).map((d) => d.row);
    return { ...found, total: found.total + here.length, items: query.page === 0 ? [...here, ...found.items] : found.items, counts };
}

/** 상세 — 켠 목업 글 번호면 목업, 아니면 null(실제 API 로) */
export function mockPauseDealDetail(postId: string): Promise<AdminDealDetail> | null {
    const d = find(postId);
    if (!d) return null;
    return mockDelay().then(() => mockClone(d));
}

/** 수정 이력 — 사업자 글은 게시 뒤 수정이 없다(빈 이력) */
export function mockPauseDealRevisions(postId: string): Promise<DealRevisionListView> | null {
    if (!find(postId)) return null;
    return mockDelay().then(() => ({ items: [], retentionMonths: 6 }));
}

/** 운영진 종료·재개 — 회원 글과 같은 규칙(종료는 진행·의심·기간 지남, 재개는 종료만). 목업 글이 아니면 null */
export function mockPauseDealAct(postId: string, kind: 'END' | 'REOPEN', reason?: string): Promise<void> | null {
    const d = find(postId);
    if (!d) return null;
    return mockDelay().then(() => {
        const view = d.row.deal.view;
        if ((kind === 'END' && view === 'ENDED') || (kind === 'REOPEN' && view !== 'ENDED')) {
            throw MOCK_ERRORS.conflict('HOTDEAL_STATE_CHANGED', '그 사이 다른 운영자가 처리했어요. 최신 상태로 다시 불러올게요.');
        }
        if (kind === 'END') views.set(postId, { view: 'ENDED', endedAt: new Date().toISOString(), reason: reason ?? null });
        else views.delete(postId);
    });
}
