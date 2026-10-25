/**
 * 댓글 API 가 주고받는 모양 — 서버 `server/api/.../comment/dto` 와 같다(2026-10-22 연결). 답글은 1단계(`parentId` = 원댓글, `replyTo` = 답한 상대).
 * ⚠ ID 는 모두 문자열이다(Snowflake 64비트). 시각은 UTC ISO 글자.
 */
import type { ContentFormat, GuestCredentials, PostAuthor } from '~/composables/board/api/usePostApi.types';
import type { BlockedBy } from '~/composables/block/api/useBlockApi.types';
import type { CommentDebate, CommentKind, DebateStance, PinRequestState } from '~/composables/board/api/useDebateApi.types';

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
    /** 찬반 토론의 입장 배지 PRO · CON · NEUTRAL(일반 댓글 null) — 입장을 바꾸면 그 회원의 기존 의견도 새 입장으로 함께 바뀐다(소급). 답글·지운 자리·숨긴 자리에도 남는다 */
    stance?: DebateStance | null;
    /**
     * 숨긴 까닭을 밝힐 때만 `STAFF`(운영자가 숨김) — `state` 가 HIDDEN 이고 그 게시판의 정책 `staffHiddenNotice` 가 켜졌을 때만 온다.
     * 그 밖(옵션이 꺼진 게시판·신고 숨김·숨기지 않은 댓글)은 null — 화면은 지금처럼 「신고로 숨겨진 댓글이에요」를 보인다.
     */
    hiddenBy?: 'STAFF' | null;
    /** 운영자가 고정했거나 고정 요청이 승인된, 지금 보이는 의견 — 토론 구분(`OPINION`·한 입장)에서는 맨 위에 온다 */
    pinned?: boolean;
    /** 비추천 수 · 내가 비추천했는가 — 게시판 비추천이 꺼져 있으면 null(목록의 `reactions.dislikeEnabled`). 추천(`liked`)과 동시에 참이 아니다 */
    dislikeCount?: number | null;
    disliked?: boolean | null;
    /** 내 의견 원댓글의 고정 요청 상태(대기·거절) — 그 밖은 null */
    pinRequest?: PinRequestState | null;
}

/** 원댓글 + 그 아래 답글(오래된순) */
export interface CommentThread extends CommentView {
    replies: CommentView[];
}

/** 댓글 정렬 — 오래된순 · 최신순(원댓글 기준) */
export type CommentSort = 'OLDEST' | 'LATEST';

/** `GET /api/posts/{id}/comments?sort&kind` */
export interface CommentListResponse {
    /** `kind` 로 거른 원댓글 묶음 */
    threads: CommentThread[];
    /** 베스트 댓글 — 추천 `bestCommentMinLikes` 이상, 추천 많은 순 최대 3개(가정). `kind` 가 ALL 이 아니면 그 구분의 원댓글 중에서만 */
    best: CommentView[];
    /** 보이는 댓글 수(지운 것 제외) — `kind` 로 걸러도 글 전체 수다 */
    totalCount: number;
    /** 이 응답의 구분 — 옛 응답에는 없을 수 있다 */
    kind?: CommentKind;
    /** 찬반 토론 칸 — 늘 있다(토론이 없으면 `enabled false`). 옛 응답에는 없을 수 있다 */
    debate?: CommentDebate;
    /** 이 게시판의 댓글 추천·비추천 표시 — 옛 응답에는 없을 수 있다(그때는 추천만, 기본 이름) */
    reactions?: CommentReactions;
}

/** 댓글 추천·비추천 표시(게시판 정책 유효값) — 이름이 null 이면 화면 기본 이름(「추천」·「비추천」) */
export interface CommentReactions {
    dislikeEnabled: boolean;
    likeLabel: string | null;
    dislikeLabel: string | null;
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
    /**
     * 토론 구획의 의견으로 쓴다(회원만) — 내 지금 입장이 배지로 붙는다. 표시(`opinion`·`stance`)가 없으면 입장을 가진 회원이 써도 일반 댓글.
     * 답글은 이 칸과 무관하게 원댓글을 따른다. 입장 없음 409 `STANCE_REQUIRED` · 닫힌 토론 409 `DEBATE_NOT_OPEN` · 비회원 400 `fields.stance`
     */
    opinion?: boolean;
    /** 의견 표시 + 내가 아는 지금 입장 — 서버의 지금 입장과 다르면(다른 창에서 바꿈) 409 `STANCE_LOCKED`. 입장을 정하지는 않는다(투표로만) */
    stance?: DebateStance | null;
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

/**
 * `POST /api/comments/{id}/like`(취소 없음) · `POST|DELETE /api/comments/{id}/dislike` — 누른 뒤의 값.
 * 추천과 비추천은 둘 중 하나만이라 하나를 누르면 다른 하나가 풀린 값이 함께 온다. 비추천이 꺼진 게시판은 비추천 칸 null
 */
export interface CommentLikeResult {
    likeCount: number;
    liked: boolean;
    dislikeCount: number | null;
    disliked: boolean | null;
}
