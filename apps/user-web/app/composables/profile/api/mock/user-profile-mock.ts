// MOCK: 공개 회원 프로필(`GET /api/users/{id}/profile`·`/posts`·`/comments`)의 목업 — `useUserProfileApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다.
// 작성글은 실제 공개 게시판 API 로 모은 글에서 그 회원(`author.memberId`)의 글을 고른다(`common/mock/board-feed-mock.ts`) — 목업 화면의 글이 실제 글로 열린다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   profile-notfound   없는·탈퇴한 회원(404 PROFILE_NOT_FOUND)     profile-view-fail   프로필 읽기 실패(500)
//   profile-posts-fail 작성글 읽기 실패(500)                         profile-comments-fail 댓글 읽기 실패(500)
//   profile-comments-empty 댓글이 없다
//
// ── 목업 규칙(가정 — 계약 「목업 가정」) ─
//   · 소통(`member-social-contract.md`): 가짜 회원 명부(`member-mock.ts`)의 회원이면 그 이름·등급·역할, `blockedByMe`·`memo` 는 차단 목업 상태.
//   · 회원 정보: 본인이면 로그인 정보, 아니면 모은 글에서 그 회원이 쓴 글의 닉네임·등급. 글이 없으면 「회원 {ID 끝 4자리}」·새싹으로 그린다(진짜 서버는 404 일 수도 있다).
//   · 가입일·한 줄 소개는 ID 로 정한 가짜 값이다(본인의 소개는 `/api/me/profile` 목업과 따로다).
//   · 수치: 글 = 모은 글 중 그 회원 글 수, 받은 추천 = 그 글들의 추천 합, 댓글 = 아래 가짜 댓글 수.
//   · 댓글: 그 회원 ID 로 정한 개수(0~23)의 가짜 댓글을 모은 글에 붙여 만든다(댓글 내용·추천은 가짜, 누르면 실제 글로 간다).

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { BoardFeedSource } from '~/composables/common/mock/board-feed-mock';
import { mockBoardFeed, mockCursorStart } from '~/composables/common/mock/board-feed-mock';
import { mockDirectoryMember } from '~/composables/common/mock/member-mock';
import { mockIsBlocked, mockMemoOf } from '~/composables/block/api/mock/block-mock';
import type { AuthUser } from '~/composables/auth/api/useAuthApi.types';
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { AuthorCommentPage, AuthorCommentRow, PublicProfile, UserFeedQuery, UserPostPage } from '~/composables/profile/api/useUserProfileApi.types';

const DAY = 24 * 60 * 60 * 1000;
const BIOS = ['로봇 만드는 걸 좋아해요', '', '산업 뉴스를 모아 읽는 중이에요', '질문 많이 하는 사람', ''];
const SNIPPETS = ['좋은 정보 감사합니다!', '저도 같은 생각이에요. 다음 글도 기대할게요.', '혹시 출처 알 수 있을까요?', '이건 조금 다르게 봐요 — 비용을 생각하면 아직 이른 것 같아요.', '정리 잘 해 주셨네요 👍'];

/** ID 로 정한 0 이상의 수 — 새로고침해도 같은 회원은 같은 가짜 값 */
function seedOf(id: string): number {
    let h = 0;
    for (const c of id) h = (h * 31 + c.charCodeAt(0)) >>> 0;
    return h;
}

function validId(id: string): boolean {
    return /^\d{1,20}$/.test(id);
}

function notFound(): ApiError {
    return new ApiError({ status: 404, code: 'PROFILE_NOT_FOUND', message: '탈퇴했거나 없는 회원이에요.' });
}

function postsOf(rows: readonly PostFeedRow[], id: string): PostFeedRow[] {
    return rows.filter((row) => row.author.kind === 'MEMBER' && row.author.memberId === id);
}

function commentsOf(rows: readonly PostFeedRow[], id: string): AuthorCommentRow[] {
    if (rows.length === 0 || mockFlags().has('profile-comments-empty')) return [];
    const seed = seedOf(id);
    const count = seed % 24;
    const out: AuthorCommentRow[] = [];
    for (let i = 0; i < count; i++) {
        const post = rows[(seed + i * 7) % rows.length]!;
        out.push({
            id: `${seed}${String(i).padStart(3, '0')}`,
            postId: post.id,
            postTitle: post.title,
            board: { slug: post.board.slug, name: post.board.name, nameEn: post.board.nameEn },
            bodyPreview: SNIPPETS[(seed + i) % SNIPPETS.length]!,
            likeCount: (seed + i * 3) % 6,
            reply: (seed + i) % 4 === 0,
            createdAt: new Date(Date.now() - (i * 9 + (seed % 9)) * 60 * 60 * 1000).toISOString(),
        });
    }
    return out;
}

function page<T extends { id: string }>(list: readonly T[], query: UserFeedQuery): { items: T[]; hasNext: boolean; nextCursor: string | null } {
    const start = mockCursorStart(query.cursor);
    const end = start + Math.min(50, Math.max(1, query.size ?? 20));
    const hasNext = end < list.length;
    return { items: list.slice(start, end), hasNext, nextCursor: hasNext ? String(end) : null };
}

export async function mockUserProfile(source: BoardFeedSource, id: string, me: AuthUser | null): Promise<PublicProfile> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('profile-view-fail')) throw MOCK_ERRORS.internal();
    if (flags.has('profile-notfound') || !validId(id)) throw notFound();
    const rows = (await mockBoardFeed(source)).rows;
    const mine = postsOf(rows, id);
    const seed = seedOf(id);
    const author = mine[0]?.author;
    const isMe = me?.id === id;
    // 소통 목업의 가짜 회원 명부(쪽지 상대·차단·메모)에 있는 회원이면 그 이름·등급으로 — 쪽지함에서 누른 상대의 프로필이 같은 이름으로 열린다
    const listed = mockDirectoryMember(id);
    return mockClone({
        id,
        nickname: isMe ? me!.nickname : (listed?.nickname ?? author?.name ?? `회원 ${id.slice(-4)}`),
        grade: isMe ? me!.grade : (listed?.grade ?? author?.grade ?? 'SEEDLING'),
        gradeLabel: isMe ? me!.gradeLabel : (listed?.gradeLabel ?? author?.gradeLabel ?? '새싹'),
        role: listed?.role ?? 'USER',
        blockedByMe: !isMe && Boolean(me) && mockIsBlocked(id),
        memo: !isMe && me ? mockMemoOf(id) : null,
        bio: BIOS[seed % BIOS.length] || null,
        joinedAt: new Date(Date.now() - (30 + (seed % 400)) * DAY).toISOString(),
        isMe,
        stats: { posts: mine.length, comments: commentsOf(rows, id).length, likesReceived: mine.reduce((sum, row) => sum + row.likeCount, 0) },
    });
}

export async function mockUserPosts(source: BoardFeedSource, id: string, query: UserFeedQuery): Promise<UserPostPage> {
    await mockDelay();
    if (mockFlags().has('profile-posts-fail')) throw MOCK_ERRORS.internal();
    if (!validId(id)) throw notFound();
    return mockClone(page(postsOf((await mockBoardFeed(source)).rows, id), query));
}

export async function mockUserComments(source: BoardFeedSource, id: string, query: UserFeedQuery): Promise<AuthorCommentPage> {
    await mockDelay();
    if (mockFlags().has('profile-comments-fail')) throw MOCK_ERRORS.internal();
    if (!validId(id)) throw notFound();
    return mockClone(page(commentsOf((await mockBoardFeed(source)).rows, id), query));
}
