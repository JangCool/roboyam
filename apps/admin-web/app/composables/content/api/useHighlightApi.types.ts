/**
 * 하이라이트 API 가 주고받는 모양 — 서버 `server/admin` 의 `highlight.dto.*` 와 같다.
 * 계약: `docs/features/popular-highlight-contract.md` 「서버 구현 결과」. 찬반 토론은 글의 기능이 되어(`post-debate-contract.md`) 토론 칸은 **그 글의 토론**을 뜻하고,
 * 의견·고정 요청 관리는 토론 관리 API(`useDebateApi` — 글 번호)로 옮겼다. ID 는 모두 문자열, 시각은 UTC ISO 글자.
 */
import type { DebateOpenedBy } from '~/composables/content/api/useDebateApi.types';
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';

/** 판정 차례: 글이 공개 아님(또는 글 없음) BLOCKED → 노출 끔 OFF → 시작 전 SCHEDULED → 끝남 EXPIRED → SHOWING */
export type HighlightStatus = 'SHOWING' | 'SCHEDULED' | 'EXPIRED' | 'OFF' | 'BLOCKED';

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
    /** 그 글에 찬반 토론이 열려 있다(`post_debates` — 하이라이트를 내리거나 지워도 토론은 그대로) */
    debate: boolean;
    /** 그 글 토론의 「중립도 받기」(토론이 없으면 true) — 끄면 새로 중립을 고를 수 없다(이미 고른 중립 표·쓴 중립 의견은 남고 수에도 든다) */
    neutralEnabled: boolean;
    /** 그 글 토론의 「입장 바꾸기 허용」(토론이 없으면 true) — 끄면 한 번 고른 입장을 바꿀 수 없다 */
    stanceChangeAllowed: boolean;
    /** 그 글의 토론을 연 쪽 — 글쓴이(MEMBER)·운영자(OPERATOR), 토론이 없으면 null */
    debateOpenedBy: DebateOpenedBy | null;
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
 * 고칠 때 `imageFileId` 가 null 이면 대표 이미지를 지운다 · 지금과 같으면 그대로 · 새 파일이면 바꾼다. `visible` 이 null 이면 그대로(만들 때 true).
 * `debate`·`neutralEnabled`·`stanceChangeAllowed` 는 **그 글의 토론**을 연다·닫는다·바꾼다 — null 이면 그대로(만들 때 `debate` null = 글의 토론 그대로). `debate false` 면 글의 토론을 닫는다(투표가 있어도).
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
    stanceChangeAllowed: boolean | null;
}
