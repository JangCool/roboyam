// MOCK: 사업자 핫딜 글 보기 목업(일시 정지·중단 — 2026-11-02) — `usePostApi.post`·`useCommentApi.list` 의 `// MOCK:` 분기가 부른다. 서버가 붙으면 이 파일과 분기를 지운다.
// 목업 글 ID 일 때만 돈다(실서버 글은 그대로). 응답 문구는 서버 몫(i18n.md 5절).
//  - `/posts/mock-bizdeal` 진행 중 사업자 글(딜 카드) — `?mock=bizdeal-mine` 이면 보는 사람이 그 사업자(카드에 [일시 정지]·「중단·환불은 신청 건에서」, 신청 건 `mock-deal-pause`).
//    신청 건 목업(`business/api/mock/pause-mock.ts`)에서 정지·중단했으면 이 글도 아래 안내로 바뀐다.
//  - `/posts/mock-bizdeal-paused` 일시 정지된 글 · `/posts/mock-bizdeal-stopped` 중단된 글 — 403 `DEAL_SELLER_HIDDEN` + extras(제안 모양, `usePostApi` 머리말).
//  - 게시판은 `?board=` → `hotdeal`(실서버에 그 slug 의 핫딜 게시판이 있어야 글 화면이 선다 — 게시판 정보는 실제 API). 딜 동작(알리기·종료·keep)은 목업하지 않는다(실서버로 가서 404).

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { CommentListResponse } from '~/composables/board/api/useCommentApi.types';
import type { PostDetail } from '~/composables/board/api/usePostApi.types';
import type { PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import { MOCK_DEAL_PAUSE_ID, mockDealPauseState } from '~/composables/business/api/mock/pause-mock';

const LIVE_ID = 'mock-bizdeal';
const PAUSED_ID = 'mock-bizdeal-paused';
const STOPPED_ID = 'mock-bizdeal-stopped';
const IDS = [LIVE_ID, PAUSED_ID, STOPPED_ID];
const H = 3_600_000;

/** 목업 사업자 글 ID 인가 */
export function isMockBizdealPostId(id: string): boolean {
    return IDS.includes(id);
}

function mine(): boolean {
    return mockFlags().has('bizdeal-mine');
}

function boardSlug(): string {
    if (typeof window === 'undefined') return 'hotdeal';
    return new URLSearchParams(window.location.search).get('board') || 'hotdeal';
}

function dealOf(now: number): PostDeal {
    const productUrl = 'https://smartstore.naver.com/roboparts/products/1234567';
    return {
        source: 'BUSINESS',
        // 신청 건 ID 는 작성자(그 사업자)에게만 — 제안
        business: { name: '로보파츠상회', applicationId: mine() ? MOCK_DEAL_PAUSE_ID : null },
        storeName: '네이버 스마트스토어',
        productUrl,
        productHost: 'smartstore.naver.com',
        finalHost: null,
        normalPrice: 89_000,
        dealPrice: 59_000,
        discountRate: 33,
        shippingFee: 0,
        couponNote: null,
        priceBasisKind: 'LISTED',
        dutyIncluded: false,
        priceBasis: null,
        endsAt: new Date(now + 4 * 24 * H).toISOString(),
        autoExpireAt: null,
        stockState: 'IN_STOCK',
        affiliate: false,
        image: null,
        status: 'ACTIVE',
        view: 'ACTIVE',
        endedBy: null,
        endedAt: null,
        reopenUntil: null,
        endRequests: { total: 0, bySoldOut: 0, byPriceChanged: 0, byLinkDead: 0, mine: null },
        flaggedAt: null,
        autoEndAt: null,
        keepCount: 0,
        keepMax: 2,
        revision: { priceEdits: 0, linkChanged: false, lastEditedAt: null },
        hot: false,
        sameDealCount: 0,
        // 목업은 서버 경유 주소(`/api/deals/{id}/go`)가 없어 상품 주소를 그대로 쓴다
        buyUrl: productUrl,
        affiliateKind: null,
        siteProgramName: null,
    };
}

/** 판매자가 내린 글 — 404 대신 안내(제안: 403 `DEAL_SELLER_HIDDEN` + extras) */
function sellerHidden(state: 'PAUSED' | 'STOPPED'): ApiError {
    const slug = boardSlug();
    return new ApiError({
        status: 403,
        code: 'DEAL_SELLER_HIDDEN',
        message: state === 'PAUSED' ? '판매자가 잠시 내린 딜이에요.' : '판매자가 내린 딜이에요.',
        extras: { state, boardSlug: slug, boardName: '핫딜', boardNameEn: 'Hot deals', ...(mine() ? { applicationId: MOCK_DEAL_PAUSE_ID } : {}) },
    });
}

/** `GET /api/posts/mock-bizdeal*` */
export async function mockBizdealPostDetail(id: string): Promise<PostDetail> {
    await mockDelay();
    if (id === PAUSED_ID) throw sellerHidden('PAUSED');
    if (id === STOPPED_ID) throw sellerHidden('STOPPED');
    // 신청 건 목업에서 정지·중단했으면 이 글도 내려간다
    const state = mockDealPauseState();
    if (state === 'PAUSED' || state === 'STOPPED') throw sellerHidden(state);
    const now = Date.now();
    const slug = boardSlug();
    const createdAt = new Date(now - 40 * H).toISOString();
    return mockClone({
        id,
        board: { id: `mock-board-${slug}`, slug, name: '핫딜', nameEn: 'Hot deals' },
        prefix: null,
        title: 'MG996R 서보모터 10개 묶음 특가',
        content: '서보모터 MG996R 10개 묶음이에요.\n\n정상가 근거는 상품 상세의 정가 표시예요. 재고는 300세트 준비했어요. 사업자가 비용을 내고 올린 광고 글이에요(목업).',
        contentFormat: 'TEXT',
        // 서버는 회원(사업자 계정)으로 준다고 본다 — 화면은 `deal.business.name` 으로 상호를 그린다(`UtilHotdeal.authorOf`)
        author: { kind: 'MEMBER', memberId: 'mock-biz-member', name: '로보파츠상회', grade: null, gradeLabel: null, role: 'USER', ipMasked: null, avatarUrl: null, business: true },
        createdAt,
        updatedAt: null,
        viewCount: 820,
        likeCount: 12,
        liked: false,
        commentCount: 0,
        adult: false,
        mine: mine(),
        protection: { active: false, editBlocked: false, deleteMode: 'ALLOW', delayMinutes: 0, threshold: 0 },
        deleteScheduledAt: null,
        prev: null,
        next: null,
        externalLinkNofollow: true,
        blocked: null,
        poll: null,
        group: null,
        deal: dealOf(now),
        dealModeration: null,
    } satisfies PostDetail);
}

/** 목업 글의 댓글 — 두지 않는다(빈 목록) */
export async function mockBizdealComments(): Promise<CommentListResponse> {
    await mockDelay();
    return { threads: [], best: [], totalCount: 0 };
}
