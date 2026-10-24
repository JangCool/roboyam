/**
 * 하이라이트·의견(토론) 관리 API 가 주고받는 모양 — 서버 `server/admin` 의 `highlight.dto.*` 와 같다.
 * 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」. ID 는 모두 문자열, 시각은 UTC ISO 글자.
 */
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';

/** 판정 차례: 글이 공개 아님(또는 글 없음) BLOCKED → 노출 끔 OFF → 시작 전 SCHEDULED → 끝남 EXPIRED → SHOWING */
export type HighlightStatus = 'SHOWING' | 'SCHEDULED' | 'EXPIRED' | 'OFF' | 'BLOCKED';

/** 입장 — 찬성 · 반대 · 중립 */
export type DebateStance = 'PRO' | 'CON' | 'NEUTRAL';

/** 입장을 고른 사람 수(투표 수 — 공개 `debate.votes` 와 같다) */
export interface HighlightVotes {
    pro: number;
    con: number;
    neutral: number;
    total: number;
}

export interface HighlightCommentCounts {
    /** 글의 보이는 댓글 수(답글 포함) */
    all: number;
    /** 지우거나 숨기지 않은 입장 없는 원댓글 수(일반 댓글) */
    general: number;
    /** 입장별 의견(원댓글) 수 — 지우거나 숨긴 것 제외 */
    pro: number;
    con: number;
    neutral: number;
    /** 입장을 고른 사람 수 — 의견 수와 따로 센다 */
    votes: HighlightVotes;
}

export interface HighlightOperator {
    id: string;
    name: string | null;
}

export interface HighlightItem {
    id: string;
    /** 1부터 */
    order: number;
    title: string | null;
    summary: string | null;
    imageFileId: string | null;
    /** `/media/…` 상대 주소 */
    imageUrl: string | null;
    visible: boolean;
    showFrom: string | null;
    showUntil: string | null;
    status: HighlightStatus;
    /** 「찬성·반대 의견 받기」 */
    debate: boolean;
    /** 「중립도 받기」 — 끄면 새로 중립을 고를 수 없다(이미 고른 중립 표·쓴 중립 의견은 남고 수에도 든다) */
    neutralEnabled: boolean;
    post: AdminPostRef | null;
    commentCounts: HighlightCommentCounts;
    createdAt: string;
    updatedAt: string;
    updatedBy: HighlightOperator | null;
}

/** `GET /api/admin/highlights` */
export interface HighlightListView {
    /** SHOWING 수 */
    visibleCount: number;
    total: number;
    items: HighlightItem[];
}

/** `GET /api/admin/highlights/candidates` */
export interface HighlightCandidatesView {
    days: number;
    minLikes: number;
    boardId: string | null;
    items: AdminPostRef[];
}

export interface HighlightCandidateQuery {
    /** 0~365(0 = 전체) */
    days: number;
    /** 1~1000 */
    minLikes: number;
    boardId: string | null;
}

/**
 * `POST /api/admin/highlights` · `PUT /api/admin/highlights/{id}` — 고칠 때는 **모든 칸을 보낸다**(`postId` 는 무시).
 * 고칠 때 `imageFileId` 가 null 이면 대표 이미지를 지운다 · 지금과 같으면 그대로 · 새 파일이면 바꾼다. `visible`·`debate`·`neutralEnabled` 가 null 이면 그대로(만들 때는 true·false·true).
 */
export interface HighlightSaveRequest {
    postId: string | null;
    title: string | null;
    summary: string | null;
    imageFileId: string | null;
    visible: boolean | null;
    showFrom: string | null;
    showUntil: string | null;
    debate: boolean | null;
    neutralEnabled: boolean | null;
}

// ── 의견 관리 `/api/admin/highlights/{id}/opinions` ──

/** 운영자 숨김 STAFF · 신고 숨김 REPORTED·ILLEGAL·RIGHTS */
export type OpinionHiddenReason = 'STAFF' | 'REPORTED' | 'ILLEGAL' | 'RIGHTS';

export interface HighlightOpinion {
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

export interface HighlightOpinionPage {
    total: number;
    page: number;
    size: number;
    hasNext: boolean;
    items: HighlightOpinion[];
}

/** `stance` 를 주면 그쪽만 `items` 가 차고 다른 쪽은 `total` 만 */
export interface HighlightOpinionListView {
    highlightId: string;
    postId: string;
    debate: boolean;
    /** 지금 토론이 열려 있다(노출 중 + 의견 받기 켬) */
    open: boolean;
    /** 「중립도 받기」 — 화면은 이것이 켜졌거나 `neutral.total > 0` 일 때 중립 탭을 보인다 */
    neutralEnabled: boolean;
    pro: HighlightOpinionPage;
    con: HighlightOpinionPage;
    neutral: HighlightOpinionPage;
}

export interface HighlightOpinionQuery {
    stance: DebateStance | null;
    /** 1부터 */
    page: number;
    /** 1~100 */
    size: number;
}

// ── 의견 고정 요청 `/api/admin/highlights/{id}/pin-requests` ──

export type PinRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 의견 고정 요청 한 줄(대기 목록·승인·거절 응답) */
export interface HighlightPinRequest {
    id: string;
    commentId: string;
    status: PinRequestStatus;
    /** 대상 의견(지금 모습 — 지운 의견이면 null) */
    opinion: HighlightOpinion | null;
    /** 요청한 회원(= 의견을 쓴 회원) */
    requesterId: string;
    rejectReason: string | null;
    createdAt: string;
    decidedAt: string | null;
}

/** `GET /api/admin/highlights/{id}/pin-requests` — 대기 중(오래된 것부터, 지우거나 숨긴 의견의 요청 제외) */
export interface HighlightPinRequestListView {
    highlightId: string;
    postId: string;
    items: HighlightPinRequest[];
}

/** `POST …/pin-requests/{requestId}/reject` — 사유 0~100자(선택, 앞뒤 공백을 떼고 비면 없음) */
export interface HighlightPinRejectRequest {
    reason: string | null;
}
