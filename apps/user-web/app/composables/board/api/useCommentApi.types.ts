/**
 * 댓글 API 가 주고받는 모양 — 서버 `server/api/.../comment/dto` 와 같다(2026-10-22 연결). 답글은 1단계(`parentId` = 원댓글, `replyTo` = 답한 상대).
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 시각은 UTC ISO 글자.
 */
import type { ContentFormat, GuestCredentials, PostAuthor } from '~/composables/board/api/usePostApi.types';
import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';

/**
 * 댓글 상태 — 보임 · 지움(답글이 있어 자리만 남김) · 신고로 숨김(검토 대기) · 비회원에게 늦게 보임(공개 전).
 * `DELETED`·`HIDDEN`·`DELAYED` 는 `content` 가 null 이다.
 */
export type CommentState = 'VISIBLE' | 'DELETED' | 'HIDDEN' | 'DELAYED';

export interface CommentView {
    id: string;
    postId: string;
    /** 원댓글 ID — 원댓글이면 null */
    parentId: string | null;
    /** 답한 상대(답글의 답글이면 그 댓글쓴이) — 원댓글·원댓글에 바로 단 답글은 null 일 수 있다 */
    replyTo: { id: string; name: string } | null;
    author: PostAuthor;
    content: string | null;
    /** 본문 형식 — `HTML` 이면 서버가 정화한 HTML(`v-html` 로 그린다), 없으면(옛 응답) `TEXT` */
    contentFormat?: ContentFormat;
    state: CommentState;
    createdAt: string;
    updatedAt: string | null;
    likeCount: number;
    liked: boolean;
    /** 로그인한 내 댓글 */
    mine: boolean;
    /** 차단한 회원의 댓글(`USER`)·본문에 차단한 단어(`WORD`) — **서버 후보**(`member-social-contract.md`), 지금 서버 응답에는 없다. 접는 것은 화면의 일 */
    blocked?: BlockedBy | null;
}

/** 원댓글 + 그 아래 답글(오래된순) */
export interface CommentThread extends CommentView {
    replies: CommentView[];
}

/** 댓글 정렬 — 오래된순 · 최신순(원댓글 기준) */
export type CommentSort = 'OLDEST' | 'LATEST';

/** `GET /api/posts/{id}/comments?sort` */
export interface CommentListResponse {
    threads: CommentThread[];
    /** 베스트 댓글 — 추천 `bestCommentMinLikes` 이상, 추천 많은 순 최대 3개(가정) */
    best: CommentView[];
    /** 보이는 댓글 수(지운 것 제외) */
    totalCount: number;
}

/** `POST /api/posts/{id}/comments` — 응답은 새 댓글 */
export interface CommentWriteRequest {
    content: string;
    /** 게시판 댓글 에디터가 `NONE` 이면 `TEXT`, `SIMPLE` 이면 `HTML` */
    contentFormat: ContentFormat;
    /** 답글이면 원댓글 ID */
    parentId: string | null;
    /** 답글의 답글이면 그 댓글 ID(닉네임 표시용) */
    replyToId: string | null;
    guest: GuestCredentials | null;
}

/** `PUT /api/comments/{id}` — 비회원 댓글은 `password` 필수 */
export interface CommentUpdateRequest {
    content: string;
    contentFormat: ContentFormat;
    password: string | null;
}

/** `DELETE /api/comments/{id}` 본문 */
export interface CommentDeleteRequest {
    password: string | null;
}

/** `POST /api/comments/{id}/like` — 한 번만, 취소 없음 */
export interface CommentLikeResult {
    likeCount: number;
    liked: boolean;
}
