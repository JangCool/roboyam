/**
 * 하이라이트 토론(찬성·반대·중립 투표와 의견)이 주고받는 모양 — 서버 `server/api/.../debate/dto` 와 같다(docs/features/popular-highlight-contract.md 「서버 구현 결과」 V78 갱신).
 * 글 상세의 `debate`·댓글 목록의 `debate`·댓글의 `stance` 도 여기 타입을 쓴다. ⚠ ID 는 모두 문자열이다.
 */
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';

/** 입장 — 찬성 · 반대 · 중립 */
export type DebateStance = 'PRO' | 'CON' | 'NEUTRAL';

/**
 * 댓글 목록 구분 — 원댓글의 입장으로 가른다(답글은 원댓글을 따라간다).
 * 화면은 댓글 구획을 늘 `GENERAL`, 토론 구획을 `OPINION`(또는 한 입장)으로 부른다. `ALL` 은 서버 기본값(예전 호출 호환)
 */
export type CommentKind = 'ALL' | 'GENERAL' | 'OPINION' | 'PRO' | 'CON' | 'NEUTRAL';

/** 토론 구획의 거르기 칩 — 의견 전체 · 찬성 · 반대 · 중립 */
export type OpinionKind = Extract<CommentKind, 'OPINION' | 'PRO' | 'CON' | 'NEUTRAL'>;

/** 입장을 고른 사람 수(투표 수 — 비율 막대·투표 단추) */
export interface DebateVotes {
    pro: number;
    con: number;
    neutral: number;
    total: number;
}

/** 글 상세(`GET /api/posts/{id}`)의 토론 요약 — 늘 있다(하이라이트가 아니면 `enabled false`) */
export interface DebateSummary {
    /** 지금 이 글이 의견을 받는가(노출 중인 하이라이트 + 「찬성·반대 의견 받기」 켬) */
    enabled: boolean;
    /** 내가 이 글에서 고른 입장 — 고르지 않았거나 비로그인이면 null. 하이라이트가 끝나도 남는다 */
    myStance: DebateStance | null;
    /** 입장 의견의 글자 수 한도 */
    opinionMax: number;
    /** 하이라이트의 「중립도 받기」 — false 면 새로 중립을 고를 수 없다(이미 고른 중립 표·쓴 의견은 남고 수에도 든다). 옛 응답에는 없을 수 있다 */
    neutralEnabled?: boolean;
    /** 입장을 고른 사람 수 — 옛 응답에는 없을 수 있다 */
    votes?: DebateVotes;
    /** 내가 확인할 대기 고정 요청 수(게시판 정책의 글쓴이·게시판 매니저일 때만 숫자, 아니면 null) */
    pendingPinRequests?: number | null;
    /** 이 글의 게시판이 의견 고정 요청을 받는가(유효 정책 `pinRequestReviewers` 가 비어 있지 않음 — `PIN_REQUEST_OFF` 와 같은 기준). 옛 응답에는 없을 수 있다 */
    pinRequestEnabled?: boolean;
}

/** 글의 댓글 수 구분 — 모두 지우거나 숨긴 것 제외, `kind` 와 무관하게 글 전체 */
export interface DebateCounts {
    /** 보이는 댓글 전부(답글 포함) */
    all: number;
    /** 일반 원댓글 수 */
    general: number;
    /** 댓글 구획 전체(일반 원댓글 + 그 답글) — 「댓글 N」 */
    generalTotal: number;
    /** 의견 원댓글 수(`pro + con + neutral`) — 「의견 전체 N」 칩 */
    opinions: number;
    /** 토론 구획 전체(의견 원댓글 + 그 답글) */
    opinionsTotal: number;
    pro: number;
    con: number;
    neutral: number;
}

/**
 * 댓글 목록의 토론 칸 — 늘 있다. 화면은 `enabled || votes.total > 0 || counts.opinions > 0` 일 때 토론 구획을 보인다(끝난 하이라이트는 읽기 전용),
 * 중립 칸은 `neutralEnabled || votes.neutral > 0 || counts.neutral > 0` 일 때(`UtilDebate`). 막대·투표 단추 = `votes`(사람 수), 칩 = `counts`(의견 수)
 */
export interface CommentDebate {
    enabled: boolean;
    myStance: DebateStance | null;
    opinionMax: number;
    neutralEnabled: boolean;
    votes: DebateVotes;
    counts: DebateCounts;
    /** 내가 쓴 지금 입장의 의견 수(원댓글·답글, 숨긴 것 포함, 지운 것 제외) — 입장 바꾸기 확인창의 「N개도 바뀌어요」 */
    myOpinionCount: number;
    /** 내가 확인할 대기 고정 요청 수 — 확인 권한자가 아니면 null */
    pendingPinRequests: number | null;
    /** 이 글의 게시판이 의견 고정 요청을 받는가(글 상세와 같다) — false 면 [고정 요청] 단추를 처음부터 숨긴다(이미 보낸 요청의 대기·거절 상태는 보인다) */
    pinRequestEnabled: boolean;
}

/** `PUT /api/posts/{postId}/stance` 요청 — 처음 고르기(투표)와 바꾸기 모두 */
export interface StanceBody {
    stance: DebateStance;
}

/** `PUT /api/posts/{postId}/stance` 응답 — 고른 입장 + 함께 옮긴 내 의견 수(처음 고르기·같은 값이면 0) */
export interface StanceChangeResult {
    stance: DebateStance;
    moved: number;
}

/** @ 지정 후보 한 명 — 고르면 `parentId`·`replyToId` 로 그 회원의 최근 의견에 답글(= 의견)을 쓴다 */
export interface Commenter {
    author: PostAuthor;
    parentId: string;
    replyToId: string;
}

/** `GET /api/posts/{postId}/commenters?q=` — 최대 10명, 가장 최근에 의견을 쓴 회원 먼저 */
export interface CommenterList {
    items: Commenter[];
}

/** 의견 고정 요청 상태 */
export type PinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 댓글 줄의 `pinRequest` — 내가 쓴 의견 원댓글의 가장 최근 요청이 대기·거절일 때만 */
export interface PinRequestState {
    requestId: string;
    status: Extract<PinRequestStatus, 'PENDING' | 'REJECTED'>;
    rejectReason: string | null;
}

/** 고정 요청 한 건 — 보내기·승인·거절 응답 */
export interface PinRequest {
    id: string;
    commentId: string;
    postId: string;
    status: PinRequestStatus;
    rejectReason: string | null;
    createdAt: string;
    decidedAt: string | null;
}

/** 확인할 고정 요청 한 줄(`GET /api/posts/{postId}/pin-requests`) */
export interface PinRequestReviewItem {
    id: string;
    commentId: string;
    stance: DebateStance;
    /** 의견을 쓴(요청한) 회원 */
    author: PostAuthor;
    /** 의견 본문 앞 200자(HTML 은 보이는 글자) */
    excerpt: string;
    likeCount: number;
    commentCreatedAt: string;
    requestedAt: string;
}

/** `GET /api/posts/{postId}/pin-requests` — 대기 중(오래된 것부터, 내 요청·지우거나 숨긴 의견 제외) */
export interface PinRequestReviewList {
    items: PinRequestReviewItem[];
}

/** `POST /api/pin-requests/{id}/reject` 본문 — 사유 0~100자(선택) */
export interface PinRequestRejectBody {
    reason: string | null;
}
