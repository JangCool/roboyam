// MOCK: 사업자 핫딜 글(2차 — `source=BUSINESS`)을 핫딜 게시판 목록·글 보기에 끼워 보는 목업 — `useBoardApi`·`usePostApi`·`useCommentApi` 의 `// MOCK:` 분기가 부른다.
// 딜 칸·딜 동작은 `hotdeal/api/mock/bizdeal-deal-mock.ts`(스위치 목록도 거기). 서버가 붙으면 두 목업과 분기를 함께 지운다.
//  - `?mock=bizdeal-board`: 실제 핫딜 게시판 목록 첫 쪽(검색·말머리·쇼핑몰 거르기 없음)에 목업 사업자 글 2줄을 맨 위에 끼운다(실제 글은 그대로).
//  - 목업 글 보기 `/posts/mock-bizdeal`·`/posts/mock-bizdeal-2` — 게시판은 `?board=` → 마지막으로 목업을 본 핫딜 게시판(목록·노출 자리) → `hotdeal`
//    (실서버에 그 slug 의 핫딜 게시판이 있어야 화면이 선다 — 게시판 정보는 실제 API).

import { ApiError } from '~/composables/common/useApiClient';
import { mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { CommentListResponse } from '~/composables/board/api/useCommentApi.types';
import type { DealPostListQuery, PostDetail, PostLikeResult, PostListResponse, PostRow } from '~/composables/board/api/usePostApi.types';
import { MOCK_BIZDEAL_POSTS, isMockBizdealPostId, mockBizdealDeal, mockBizdealHidden, mockBizdealMine, type MockBizdealPostMeta } from '~/composables/hotdeal/api/mock/bizdeal-deal-mock';
import UtilHotdeal from '~/utils/util-hotdeal';

const DEFAULT_SLUG = 'hotdeal';
/** 마지막으로 목업 줄을 끼운 핫딜 게시판 — 목업 글 보기가 그 게시판으로 선다 */
let lastSlug = '';
const liked = new Set<string>();

export { isMockBizdealPostId };

/** 목록에 끼우는 스위치가 켜졌나 */
function boardMockOn(): boolean {
    return mockFlags().has('bizdeal-board');
}

/** 노출 자리 목업(`ad/api/mock/bizdeal-ad-mock.ts`)과 같은 창 저장소 키 — 마지막으로 목업을 본 핫딜 게시판 */
const MOCK_BOARD_KEY = 'roboyam-mock-bizdeal-board';

function remember(slug: string): void {
    lastSlug = slug;
    try {
        sessionStorage.setItem(MOCK_BOARD_KEY, slug);
    } catch {
        // 저장소를 못 쓰면 이 창의 메모리만
    }
}

/** 목업 글 보기가 설 게시판 — `?board=` → 마지막으로 목업을 본 게시판 → `hotdeal` */
function slugOf(): string {
    if (typeof window === 'undefined') return lastSlug || DEFAULT_SLUG;
    const fromQuery = new URLSearchParams(window.location.search).get('board');
    if (fromQuery) return fromQuery;
    let stored: string | null = null;
    try {
        stored = sessionStorage.getItem(MOCK_BOARD_KEY);
    } catch {
        stored = null;
    }
    return stored || lastSlug || DEFAULT_SLUG;
}

function authorOf(p: MockBizdealPostMeta): PostRow['author'] {
    // 서버는 회원(사업자 계정)으로 준다고 본다 — 화면은 `deal.business.name` 으로 상호를 그린다(`UtilHotdeal.authorOf`)
    return { kind: 'MEMBER', memberId: `mock-biz-member-${p.id}`, name: p.businessName, grade: null, gradeLabel: null, role: 'USER', ipMasked: null, avatarUrl: null, business: true };
}

function createdAt(p: MockBizdealPostMeta): string {
    return new Date(Date.now() - p.hoursAgo * 3_600_000).toISOString();
}

function rowOf(p: MockBizdealPostMeta): PostRow {
    const deal = mockBizdealDeal(p.id);
    return {
        id: p.id,
        prefix: null,
        title: p.title,
        commentCount: 0,
        hasImage: false,
        thumbnailUrl: deal.image?.url ?? null,
        author: authorOf(p),
        createdAt: createdAt(p),
        viewCount: p.viewCount,
        likeCount: p.likeCount + (liked.has(p.id) ? 1 : 0),
        pinned: false,
        adult: false,
        hiddenReason: null,
        hasPoll: false,
        blocked: null,
        group: null,
        deal,
    };
}

/**
 * 핫딜 게시판 목록 응답에 목업 사업자 글을 끼운다 — 스위치가 꺼졌거나 첫 쪽이 아니거나 검색·말머리·쇼핑몰로 걸렀으면 응답 그대로.
 * 종료 숨기기면 끝난 목업 글은 뺀다.
 */
export function mockBizdealWithRows(slug: string, query: DealPostListQuery, response: PostListResponse): PostListResponse {
    if (!boardMockOn() || query.page !== 1 || query.q.trim() || query.prefix || query.store) return response;
    remember(slug);
    const rows = MOCK_BIZDEAL_POSTS.filter((p) => !mockBizdealHidden(p.id))
        .map(rowOf)
        .filter((r) => !query.hideEnded || !r.deal || !UtilHotdeal.isOver(r.deal.view));
    return { ...response, items: [...rows, ...response.items], totalCount: response.totalCount + rows.length };
}

/** 글 하나(`GET /api/posts/mock-bizdeal*`) — 사업자 글(작성자에게도 고치기 없음은 화면이 정한다) */
export async function mockBizdealPostDetail(id: string): Promise<PostDetail> {
    await mockDelay();
    if (mockBizdealHidden(id)) throw new ApiError({ status: 403, code: 'POST_HIDDEN', message: '숨겨진 글이에요.' });
    const p = MOCK_BIZDEAL_POSTS.find((x) => x.id === id);
    if (!p) throw new ApiError({ status: 404, code: 'POST_NOT_FOUND', message: '글을 찾을 수 없어요.' });
    const row = rowOf(p);
    const slug = slugOf();
    return mockClone({
        id: p.id,
        board: { id: `mock-board-${slug}`, slug, name: '핫딜', nameEn: 'Hot deals' },
        prefix: null,
        title: p.title,
        content: p.content,
        contentFormat: 'TEXT',
        author: row.author,
        createdAt: row.createdAt,
        updatedAt: null,
        viewCount: p.viewCount,
        likeCount: row.likeCount,
        liked: liked.has(p.id),
        commentCount: 0,
        adult: false,
        mine: mockBizdealMine(),
        protection: { active: false, editBlocked: false, deleteMode: 'ALLOW', delayMinutes: 0, threshold: 0 },
        deleteScheduledAt: null,
        prev: null,
        next: null,
        externalLinkNofollow: true,
        blocked: null,
        poll: null,
        group: null,
        deal: row.deal ?? null,
        dealModeration: null,
    });
}

/** 목업 글의 댓글 — 두지 않는다(빈 목록) */
export async function mockBizdealComments(): Promise<CommentListResponse> {
    await mockDelay();
    return { threads: [], best: [], totalCount: 0 };
}

export async function mockBizdealLike(id: string): Promise<PostLikeResult> {
    await mockDelay();
    if (mockBizdealMine()) throw new ApiError({ status: 400, code: 'CANNOT_LIKE_OWN', message: '내 글은 추천할 수 없어요.' });
    if (liked.has(id)) throw new ApiError({ status: 409, code: 'ALREADY_LIKED', message: '이미 추천했어요.' });
    liked.add(id);
    const p = MOCK_BIZDEAL_POSTS.find((x) => x.id === id);
    return { likeCount: (p?.likeCount ?? 0) + 1, liked: true };
}
