// MOCK: 핫딜 게시판(`/boards/hotdeal`, kind DEAL)의 게시판·글 목록·글 상세·쓰기·고치기 목업 — `useBoardApi`·`usePostApi`·`useCommentApi`·`useHomeGadgetApi` 의 `// MOCK:` 분기가 부른다.
// 딜 글 저장소·핫딜 API 목업은 `composables/hotdeal/api/mock/hotdeal-mock.ts`(스위치 목록도 거기). 서버가 붙으면(게시판 응답에 `kind`, 글 줄·상세에 `deal`) 두 목업을 함께 지운다.
// 실제 서버에 같은 slug(`hotdeal`)의 게시판이 있어도 목업이 가린다 — 지울 때 함께 사라진다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardDetail, BoardLayout, BoardView, PrefixView } from '~/composables/board/api/useBoardApi.types';
import type { CommentListResponse } from '~/composables/board/api/useCommentApi.types';
import type {
    DealPostListQuery,
    PostDeleteResult,
    PostDetail,
    PostFeedRow,
    PostLikeResult,
    PostListResponse,
    PostRow,
    PostUpdateRequest,
    PostWriteRequest,
    PostWriteResult,
} from '~/composables/board/api/usePostApi.types';
import {
    MOCK_DEAL_PREFIXES,
    isMockDealPostId,
    mockDealAffiliateMode,
    mockDealDeleteRow,
    mockDealFailIfAsked,
    mockDealFieldErrors,
    mockDealInsert,
    mockDealPatch,
    mockDealPost,
    mockDealPostLimit,
    mockDealPostsAll,
    mockDealSaveWarnings,
    mockDealStored,
    mockDealView,
    type MockDealPost,
} from '~/composables/hotdeal/api/mock/hotdeal-mock';
import type { DealStoreChip, DealView } from '~/composables/hotdeal/api/useHotdealApi.types';

/** 목업 핫딜 게시판 slug */
export const MOCK_DEAL_BOARD_SLUG = 'hotdeal';
const BOARD_ID = 'mock-board-hotdeal';
const LIST_SIZE = 20;
/** 쇼핑몰 칩 수(상위 N몰) */
const STORE_CHIPS = 8;

/** 보는 사람 — 실제 로그인 상태 + 스위치(`deal-guest`·`deal-sprout`·`deal-unverified`) */
export interface MockDealViewer {
    loggedIn: boolean;
    name: string | null;
}

function guestOf(viewer: MockDealViewer): boolean {
    return !viewer.loggedIn || mockFlags().has('deal-guest');
}

/** 목업 핫딜 게시판인가 */
export function isMockDealBoard(slug: string): boolean {
    return slug === MOCK_DEAL_BOARD_SLUG;
}

export { isMockDealPostId };

function prefixes(): PrefixView[] {
    return MOCK_DEAL_PREFIXES.map((p) => ({ id: p.id, name: p.name, staffOnly: false, common: false, color: null }));
}

function layout(): BoardLayout {
    const flags = mockFlags();
    return flags.has('deal-card') ? 'CARD' : flags.has('deal-gallery') ? 'GALLERY' : 'LIST';
}

/** 메뉴·허브용 게시판 요약 */
export function mockDealBoardView(): BoardView {
    return {
        id: BOARD_ID,
        slug: MOCK_DEAL_BOARD_SLUG,
        name: '핫딜',
        nameEn: 'Hot deals',
        description: '로봇·AI·메이킹 부품과 기기 핫딜을 나눠요(목업)',
        investmentNotice: false,
        staffOnlyWrite: false,
        writeMin: 'REGULAR',
        commentMin: 'SEEDLING',
        writeLock: null,
        imageUploadAllowed: false,
        postEditor: 'NONE',
        commentEditor: 'NONE',
        layout: layout(),
        dealEnabled: true,
        kind: 'DEAL',
        promo: false,
        viewToggle: layout() !== 'CARD',
        allowAdult: false,
        prefixes: prefixes(),
        color: null,
    };
}

/** 게시판 목록(`GET /api/boards`)·쓸 수 있는 게시판에 목업 게시판을 더한다(이미 같은 slug 가 있으면 목업으로 바꾼다) */
export function mockWithDealBoard(list: BoardView[], writableFor: MockDealViewer | null = null): BoardView[] {
    const rest = list.filter((b) => b.slug !== MOCK_DEAL_BOARD_SLUG);
    if (writableFor && !canWrite(writableFor).canWrite) return rest;
    return [...rest, mockDealBoardView()];
}

function canWrite(viewer: MockDealViewer): { canWrite: boolean; reason: BoardDetail['viewer']['writeBlockedReason'] } {
    const flags = mockFlags();
    if (guestOf(viewer)) return { canWrite: false, reason: 'LOGIN_REQUIRED' };
    if (flags.has('deal-unverified')) return { canWrite: false, reason: 'EMAIL_NOT_VERIFIED' };
    if (flags.has('deal-sprout')) return { canWrite: false, reason: 'GRADE_TOO_LOW' };
    return { canWrite: true, reason: null };
}

/** 게시판 하나(`GET /api/boards/hotdeal`) */
export async function mockDealBoard(viewer: MockDealViewer): Promise<BoardDetail> {
    await mockDelay();
    const guest = guestOf(viewer);
    const write = canWrite(viewer);
    const view = mockDealBoardView();
    return {
        ...view,
        description: view.description,
        writeMin: 'REGULAR',
        commentMin: 'SEEDLING',
        writeMinGradeLabel: '일반',
        commentMinGradeLabel: null,
        writeLock: null,
        viewToggle: view.viewToggle ?? false,
        allowAdult: false,
        adsHidden: false,
        imageUploadAllowed: false,
        introText: null,
        writeGuideText: '상품 링크를 먼저 붙여 주세요. 쇼핑몰 이름·이미지를 자동으로 채워요. 가격은 글을 쓰는 지금 기준으로 적어 주세요.',
        reportEnabled: true,
        // 제휴 링크 방식(D5 — `?mock=deal-site-affiliate` 면 SITE)
        dealAffiliateMode: mockDealAffiliateMode(),
        debateEnabled: false,
        pollEnabled: false,
        policy: {
            listSize: LIST_SIZE,
            pinnedMax: 3,
            commentProtectMin: 0,
            commentProtectEdit: false,
            commentProtectDelete: 'ALLOW',
            commentProtectDelayMinutes: 0,
            commentGuestDelayMinutes: 0,
            bestCommentMinLikes: 5,
        },
        guestRules: { captchaRequired: true, nameMin: 2, nameMax: 10, passwordMin: 4, passwordMax: 20, linkLimit: 0, imageUploadAllowed: false },
        newMemberRules: { applies: false, linkLimit: 2, imageLimit: 3 },
        viewer: {
            guest,
            staff: false,
            canWrite: write.canWrite,
            writeBlockedReason: write.reason,
            canComment: !guest,
            commentBlockedReason: guest ? 'LOGIN_REQUIRED' : null,
        },
    };
}

function authorOf(p: MockDealPost, viewer: MockDealViewer): PostRow['author'] {
    const mine = p.mine && !guestOf(viewer);
    return {
        kind: 'MEMBER',
        memberId: mine ? 'mock-member-me' : `mock-member-${p.id}`,
        name: p.mine ? (viewer.name ?? '나') : p.authorName,
        grade: 'REGULAR',
        gradeLabel: '일반',
        role: 'USER',
        ipMasked: null,
        avatarUrl: null,
    };
}

function prefixOf(id: string | null): PostRow['prefix'] {
    const p = MOCK_DEAL_PREFIXES.find((x) => x.id === id);
    return p ? { id: p.id, name: p.name, staffOnly: false, color: null } : null;
}

function rowOf(p: MockDealPost, viewer: MockDealViewer, now: number): PostRow {
    const deal = mockDealView(p, now);
    return {
        id: p.id,
        prefix: prefixOf(p.prefixId),
        title: p.title,
        commentCount: p.commentCount,
        hasImage: false,
        thumbnailUrl: deal.image?.url ?? null,
        author: authorOf(p, viewer),
        createdAt: p.createdAt,
        viewCount: p.viewCount,
        likeCount: p.likeCount,
        pinned: false,
        adult: false,
        hiddenReason: null,
        hasPoll: false,
        blocked: null,
        group: null,
        deal,
    };
}

/** 정렬 무리 — 진행 중 → 의심 → 끝남(가정: 끝난 글은 보일 때 맨 아래) */
const VIEW_RANK: Readonly<Record<DealView, number>> = { ACTIVE: 0, FLAGGED: 1, EXPIRED: 2, ENDED: 2 };

/** 쇼핑몰 칩 — 글 수 많은 차례 상위 8(같으면 이름 차례) */
function storeChips(rows: PostRow[]): DealStoreChip[] {
    const counts = new Map<string, number>();
    for (const r of rows) if (r.deal) counts.set(r.deal.storeName, (counts.get(r.deal.storeName) ?? 0) + 1);
    return [...counts.entries()]
        .map(([name, count]) => ({ name, count }))
        .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name))
        .slice(0, STORE_CHIPS);
}

/** 핫딜 목록(`GET /api/boards/hotdeal/posts?sort&hideEnded&prefix&store&q&page`) — 쇼핑몰 칩은 쇼핑몰 거르기 전 글로 센다(가정) */
export async function mockDealPosts(query: DealPostListQuery, viewer: MockDealViewer): Promise<PostListResponse> {
    await mockDelay();
    mockDealFailIfAsked();
    const now = Date.now();
    const q = query.q.trim().toLowerCase();
    const scoped = mockDealPostsAll()
        .map((p) => rowOf(p, viewer, now))
        .filter((r) => !query.prefix || r.prefix?.id === query.prefix)
        .filter((r) => !q || r.title.toLowerCase().includes(q) || (r.deal?.storeName ?? '').toLowerCase().includes(q))
        .filter((r) => !query.hideEnded || (r.deal?.view !== 'ENDED' && r.deal?.view !== 'EXPIRED'));
    const stores = storeChips(scoped);
    const rows = scoped.filter((r) => !query.store || r.deal?.storeName === query.store);
    const key = (r: PostRow): number => {
        if (query.sort === 'POPULAR') return -r.likeCount;
        if (query.sort === 'ENDING_SOON') return r.deal?.endsAt && (r.deal.view === 'ACTIVE' || r.deal.view === 'FLAGGED') ? Date.parse(r.deal.endsAt) : Number.MAX_SAFE_INTEGER;
        return -Date.parse(r.createdAt);
    };
    rows.sort((a, b) => VIEW_RANK[a.deal?.view ?? 'ACTIVE'] - VIEW_RANK[b.deal?.view ?? 'ACTIVE'] || key(a) - key(b) || Date.parse(b.createdAt) - Date.parse(a.createdAt));
    const totalPages = Math.max(1, Math.ceil(rows.length / LIST_SIZE));
    const page = Math.min(query.page, totalPages);
    return mockClone({ pinned: [], items: rows.slice((page - 1) * LIST_SIZE, page * LIST_SIZE), page, totalPages, totalCount: rows.length, stores });
}

/** 홈 가젯 `HOT_DEALS` 줄 — 진행 중(의심 제외), 최신 N개(목업 가젯 설정 그대로) */
export function mockHotDealRows(viewer: MockDealViewer, count = 5): PostFeedRow[] {
    const now = Date.now();
    return mockDealPostsAll()
        .map((p) => rowOf(p, viewer, now))
        .filter((r) => r.deal?.view === 'ACTIVE')
        .sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
        .slice(0, count)
        .map((r) => ({ ...r, board: { slug: MOCK_DEAL_BOARD_SLUG, name: '핫딜', nameEn: 'Hot deals', color: null } }));
}

/** 글 하나(`GET /api/posts/mock-deal-*`) */
export async function mockDealPostDetail(id: string, viewer: MockDealViewer): Promise<PostDetail> {
    await mockDelay();
    mockDealFailIfAsked();
    const p = mockDealPost(id);
    p.viewCount += 1;
    const list = mockDealPostsAll();
    const i = list.indexOf(p);
    const neighbor = (x: MockDealPost | undefined) => (x ? { id: x.id, title: x.title } : null);
    const row = rowOf(p, viewer, Date.now());
    return mockClone({
        id: p.id,
        board: { id: BOARD_ID, slug: MOCK_DEAL_BOARD_SLUG, name: '핫딜', nameEn: 'Hot deals' },
        prefix: row.prefix,
        title: p.title,
        content: p.content,
        contentFormat: 'TEXT',
        author: row.author,
        createdAt: p.createdAt,
        updatedAt: p.updatedAt,
        viewCount: p.viewCount,
        likeCount: p.likeCount,
        liked: p.liked,
        commentCount: p.commentCount,
        adult: false,
        mine: p.mine && !guestOf(viewer),
        protection: { active: false, editBlocked: false, deleteMode: 'ALLOW', delayMinutes: 0, threshold: 0 },
        deleteScheduledAt: null,
        prev: neighbor(list[i + 1]),
        next: neighbor(list[i - 1]),
        externalLinkNofollow: true,
        blocked: null,
        poll: null,
        group: null,
        deal: row.deal ?? null,
    });
}

/** 목업 글의 댓글 — 목업은 댓글을 두지 않는다(빈 목록) */
export async function mockDealComments(): Promise<CommentListResponse> {
    await mockDelay();
    return { threads: [], best: [], totalCount: 0 };
}

function textErrors(title: string, content: string): Record<string, string> {
    const out: Record<string, string> = {};
    if (!title.trim() || title.trim().length > 100) out.title = '제목을 1~100자로 적어 주세요.';
    if (content.trim().length < 10) out.content = '본문을 10자 이상 적어 주세요.';
    return out;
}

/** 글쓰기(`POST /api/posts` — 핫딜 게시판) — 하루 글 한도·같은 몰 간격(429, 스위치) 뒤 글 칸·딜 칸 오류를 함께 모아 `400 VALIDATION` */
export async function mockDealCreate(body: PostWriteRequest, viewer: MockDealViewer): Promise<PostWriteResult> {
    await mockDelay();
    const write = canWrite(viewer);
    if (write.reason === 'LOGIN_REQUIRED') throw new ApiError({ status: 401, code: 'UNAUTHENTICATED', message: '로그인이 필요해요.' });
    if (!write.canWrite) throw new ApiError({ status: 403, code: write.reason ?? 'FORBIDDEN', message: '일반 등급부터 핫딜을 올릴 수 있어요.' });
    mockDealPostLimit();
    const fields = { ...textErrors(body.title, body.content), ...mockDealFieldErrors(body.deal) };
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    const created = mockDealInsert({ title: body.title.trim(), content: body.content.trim(), prefixId: body.prefixId, deal: body.deal! });
    return { id: created.id };
}

/**
 * 고치기(`PUT /api/posts/mock-deal-*`) — 딜 칸은 보냈을 때만. 저장 뒤 지금 규칙으로 링크를 다시 판정해 차단이면 저장은 하고 `warnings[DEAL_LINK_BLOCKED]`
 * (링크를 차단 도메인으로 바꾸면 칸 오류 — 가정). 응답은 글 상세 + `warnings`
 */
export async function mockDealUpdate(id: string, body: PostUpdateRequest, viewer: MockDealViewer): Promise<PostDetail> {
    await mockDelay();
    const fields = { ...textErrors(body.title, body.content), ...(body.deal ? mockDealFieldErrors(body.deal, mockDealStored(id)) : {}) };
    if (Object.keys(fields).length > 0) throw MOCK_ERRORS.validation(fields);
    const patched = mockDealPatch(id, { title: body.title.trim(), content: body.content.trim(), prefixId: body.prefixId, deal: body.deal });
    const warnings = patched.deal.status === 'ENDED' ? [] : mockDealSaveWarnings(id);
    return { ...(await mockDealPostDetail(id, viewer)), warnings };
}

export async function mockDealLike(id: string): Promise<PostLikeResult> {
    await mockDelay();
    const p = mockDealPost(id);
    if (p.mine) throw new ApiError({ status: 400, code: 'CANNOT_LIKE_OWN', message: '내 글은 추천할 수 없어요.' });
    if (p.liked) throw new ApiError({ status: 409, code: 'ALREADY_LIKED', message: '이미 추천했어요.' });
    p.liked = true;
    p.likeCount += 1;
    return { likeCount: p.likeCount, liked: true };
}

export async function mockDealRemove(id: string): Promise<PostDeleteResult> {
    await mockDelay();
    mockDealPost(id);
    mockDealDeleteRow(id);
    return { scheduledAt: null };
}
