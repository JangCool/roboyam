/**
 * 글 토론 API(`/api/admin/debates/{postId}…`)가 주고받는 모양 — 서버 `server/admin` 의 `debate.dto.*` 와 같다.
 * 계약: `docs/features/post-debate-contract.md` 「API · 관리자」(찬반 토론은 **게시글 기능** — 하이라이트와 무관, 글 번호로 다룬다. 목록·글 찾기는 없다).
 * 의견·고정 요청의 모양은 예전 하이라이트 의견 관리(`popular-highlight-contract.md` 「하이라이트 의견(토론) 관리」)와 같고 `highlightId` 만 null 일 수 있다.
 * ID 는 모두 문자열, 시각은 UTC ISO 글자.
 */
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';

/** 입장 — 찬성 · 반대 · 중립 */
export type DebateStance = 'PRO' | 'CON' | 'NEUTRAL';

/** 토론을 연 쪽 — 글쓴이(회원) · 운영자(관리자·그 게시판 매니저) */
export type DebateOpenedBy = 'MEMBER' | 'OPERATOR';

/** 입장을 고른 사람 수(투표 수 — 공개 `debate.votes` 와 같다) */
export interface DebateVotes {
    pro: number;
    con: number;
    neutral: number;
    total: number;
}

/** 그 글의 열린 토론 — `GET /{postId}`·`PUT /{postId}` 응답 */
export interface DebateItem {
    postId: string;
    /** 글(지워졌으면 null) */
    post: AdminPostRef | null;
    /** 「중립도 받기」 */
    neutralEnabled: boolean;
    /** 「입장 바꾸기 허용」 — 끄면 공개 사이트에서 한 번 고른 입장을 바꿀 수 없다 */
    stanceChangeAllowed: boolean;
    openedBy: DebateOpenedBy;
    votes: DebateVotes;
    /** 의견(입장 있는 원댓글) 수 */
    opinions: number;
    /** 대기 중인 의견 고정 요청 수 */
    pendingPinRequests: number;
    /** 이 글의 하이라이트(없으면 null) */
    highlightId: string | null;
    /** 연 시각 */
    createdAt: string;
    updatedAt: string;
}

/** `PUT /api/admin/debates/{postId}` — 열 때 두 설정이 null 이면 기본(true). `enabled false` = 닫기(204) */
export interface DebateSaveRequest {
    enabled: boolean;
    neutralEnabled: boolean | null;
    stanceChangeAllowed: boolean | null;
}

// ── 의견 관리 `/api/admin/debates/{postId}/opinions` ──

/** 운영자 숨김 STAFF · 신고 숨김 REPORTED·ILLEGAL·RIGHTS */
export type OpinionHiddenReason = 'STAFF' | 'REPORTED' | 'ILLEGAL' | 'RIGHTS';

export interface DebateOpinion {
    commentId: string;
    stance: DebateStance;
    authorKind: 'MEMBER' | 'GUEST';
    authorId: string | null;
    authorName: string;
    bodyPreview: string;
    likeCount: number;
    replyCount: number;
    createdAt: string;
    pinned: boolean;
    hidden: boolean;
    hiddenReason: OpinionHiddenReason | null;
    /** 비추천 수 — 게시판의 비추천이 꺼져 있어도 쌓인 수 그대로 */
    dislikeCount: number;
    /** 대기 중인 고정 요청 ID(없으면 null) */
    pinRequestId: string | null;
}

export interface DebateOpinionPage {
    total: number;
    page: number;
    size: number;
    hasNext: boolean;
    items: DebateOpinion[];
}

/** `stance` 를 주면 그쪽만 `items` 가 차고 다른 쪽은 `total` 만 */
export interface DebateOpinionListView {
    /** 이 글의 하이라이트(없으면 null) */
    highlightId: string | null;
    postId: string;
    /** 지금 토론이 열려 있다 */
    debate: boolean;
    /** (예전 칸) 지금 토론이 열려 있다 — `debate` 와 같다. 옛 응답 호환으로만 둔다 */
    open?: boolean;
    /** 「중립도 받기」 — 화면은 이것이 켜졌거나 `neutral.total > 0` 일 때 중립 탭을 보인다 */
    neutralEnabled: boolean;
    pro: DebateOpinionPage;
    con: DebateOpinionPage;
    neutral: DebateOpinionPage;
}

export interface DebateOpinionQuery {
    stance: DebateStance | null;
    /** 1부터 */
    page: number;
    /** 1~100 */
    size: number;
}

// ── 의견 고정 요청 `/api/admin/debates/{postId}/pin-requests` ──

export type PinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 의견 고정 요청 한 줄(대기 목록·승인·거절 응답) */
export interface DebatePinRequest {
    id: string;
    commentId: string;
    status: PinRequestStatus;
    /** 대상 의견(지금 모습 — 지운 의견이면 null) */
    opinion: DebateOpinion | null;
    /** 요청한 회원(= 의견을 쓴 회원) */
    requesterId: string;
    rejectReason: string | null;
    createdAt: string;
    decidedAt: string | null;
}

/** `GET /api/admin/debates/{postId}/pin-requests` — 대기 중(오래된 것부터, 지우거나 숨긴 의견의 요청 제외) */
export interface DebatePinRequestListView {
    highlightId: string | null;
    postId: string;
    items: DebatePinRequest[];
}
