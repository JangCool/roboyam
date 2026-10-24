/**
 * 하이라이트·의견(토론) 관리 API 가 주고받는 모양 — 서버 `server/admin` 의 `highlight.dto.*` 와 같다.
 * 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」. ID 는 모두 문자열, 시각은 UTC ISO 글자.
 */
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';

/** 판정 차례: 글이 공개 아님(또는 글 없음) BLOCKED → 노출 끔 OFF → 시작 전 SCHEDULED → 끝남 EXPIRED → SHOWING */
export type HighlightStatus = 'SHOWING' | 'SCHEDULED' | 'EXPIRED' | 'OFF' | 'BLOCKED';

/** 입장 — 찬성 · 반대 */
export type DebateStance = 'PRO' | 'CON';

export interface HighlightCommentCounts {
    /** 글의 보이는 댓글 수(답글 포함) */
    all: number;
    /** 지우거나 숨기지 않은 원댓글의 입장별 수 */
    general: number;
    pro: number;
    con: number;
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
 * 고칠 때 `imageFileId` 가 null 이면 대표 이미지를 지운다 · 지금과 같으면 그대로 · 새 파일이면 바꾼다. `visible`·`debate` 가 null 이면 그대로.
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
    pro: HighlightOpinionPage;
    con: HighlightOpinionPage;
}

export interface HighlightOpinionQuery {
    stance: DebateStance | null;
    /** 1부터 */
    page: number;
    /** 1~100 */
    size: number;
}
