/**
 * 게시글 관리 API(`/api/admin/posts…`)가 주고받는 모양 — 계약 `docs/features/post-admin-contract.md` 「API」 그대로(서버 `server/admin` 의 post 관리 DTO 와 같은 칸 이름).
 * ID 는 모두 문자열, 시각은 UTC ISO 글자. 인기글·하이라이트가 쓰는 짧은 글 모양(`AdminPostRef`, `usePopularApi.types`)과는 다른 모양이다(이 화면은 게시판·말머리·작성자를 객체로 받는다).
 */

import type { BoardKind } from '~/composables/board/api/useBoardApi.types';

/** 글의 지금 상태 — 지움이 숨김을 이긴다(지운 글이 숨김 까닭을 가질 수 있다) */
export type PostState = 'PUBLIC' | 'HIDDEN' | 'DELETED';

/** 상태 탭 — `ALL`(기본)은 상태 조건 없음 */
export type PostStateFilter = 'ALL' | PostState;

/** 검색 대상 — 제목(기본) · 작성자 · 글 번호. 본문 검색은 없다 */
export type PostSearchField = 'TITLE' | 'AUTHOR' | 'ID';

/** 정렬 — 최신순(기본) · 오래된순 */
export type PostSort = 'newest' | 'oldest';

/** 숨김 까닭 — 신고 숨김(REPORTED·ILLEGAL·RIGHTS) · 운영자 숨김(STAFF) */
export type PostHiddenReason = 'REPORTED' | 'ILLEGAL' | 'RIGHTS' | 'STAFF';

export interface AdminPostBoardRef {
    id: string;
    name: string;
    nameEn: string | null;
    slug: string;
}

export interface AdminPostPrefixRef {
    id: string;
    name: string;
}

/** 작성자 — 비회원 글은 `id` 가 null 이고 `name` 이 비회원 이름. 탈퇴한 회원이면 `withdrawn` */
export interface AdminPostAuthor {
    kind: 'MEMBER' | 'GUEST';
    id: string | null;
    name: string;
    withdrawn: boolean;
}

/** 목록·상세의 글 한 줄 */
export interface AdminPostItem {
    id: string;
    board: AdminPostBoardRef;
    prefix: AdminPostPrefixRef | null;
    title: string;
    author: AdminPostAuthor;
    state: PostState;
    hiddenReason: PostHiddenReason | null;
    hiddenAt: string | null;
    /** 기간 숨김이면 풀리는 시각 */
    hiddenUntil: string | null;
    deletedAt: string | null;
    /** 본인 삭제 예약(유예 중) */
    deleteScheduledAt: string | null;
    /** 원문을 파기했다 — 복구 불가(`409 POST_PURGED`) */
    purged: boolean;
    pinned: boolean;
    /** 말머리가 공통 「공지」 */
    notice: boolean;
    adult: boolean;
    /** 찬반 토론이 열려 있다 */
    debate: boolean;
    /** 토론 의견(입장 있는 원댓글) 수 — 토론이 닫혀도 남는다 */
    opinionCount: number;
    /** 대기 중인 의견 고정 요청 수 — 토론이 닫혀도 남는다 */
    pendingPinRequests: number;
    /** 투표가 붙어 있다(post-poll-contract). 옛 응답에는 없을 수 있다 */
    poll?: boolean;
    likeCount: number;
    commentCount: number;
    viewCount: number;
    /** 처리 대기 중인 신고 수 */
    openReports: number;
    createdAt: string;
}

/** `GET /api/admin/posts` */
export interface AdminPostListView {
    total: number;
    /** 0부터 */
    page: number;
    size: number;
    items: AdminPostItem[];
}

/** `GET /api/admin/posts` 조건 — 빈 값은 보내지 않는다 */
export interface AdminPostQuery {
    /** 검색어 — 제목·작성자는 2자 이상, 글 번호는 숫자만(`searchField` 와 함께 보낸다) */
    q?: string;
    /** 검색 대상 — `q` 가 있을 때만 보낸다 */
    searchField?: PostSearchField;
    /** 작성일 시작 순간(UTC ISO — 사이트 시간대 그날 00:00:00.000) */
    createdFrom?: string;
    /** 작성일 끝 순간(UTC ISO — 사이트 시간대 그날 23:59:59.999) */
    createdTo?: string;
    /** 게시판 ID */
    board?: string;
    /** 말머리 ID(게시판을 고른 뒤) */
    prefix?: string;
    state?: PostStateFilter;
    sort?: PostSort;
    /** 0부터 */
    page?: number;
    /** 기본 20, 최대 50 */
    size?: number;
}

/** 게시판 선택지의 말머리 */
export interface AdminPostPrefixOption {
    id: string;
    name: string;
    /** 운영진만 쓸 수 있는 말머리 */
    staffOnly: boolean;
    /** 공통 말머리(공지 등 — 모든 게시판에 있다) */
    common: boolean;
}

/** 켜진 게시판 하나 — 필터·이동 대화창이 쓴다 */
export interface AdminPostBoardOption {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
    /** 게시판 종류 — 글 옮기기 후보는 소모임(`GROUP`)을 뺀다(서버도 400 `fields.boardId`) */
    kind: BoardKind;
    /** 고정 한도 — 0 이면 고정 불가 */
    pinnedMax: number;
    prefixes: AdminPostPrefixOption[];
}

/** `GET /api/admin/posts/board-options` */
export interface AdminPostBoardOptionsView {
    boards: AdminPostBoardOption[];
}

export type PostContentFormat = 'TEXT' | 'HTML';

/** 본문 — 파기됨이면 `body` 가 null. HTML 은 서버가 저장할 때 정화한 것 */
export interface AdminPostContent {
    format: PostContentFormat;
    body: string | null;
    editedAt: string | null;
}

/** 조치 기록 종류 — `REPORT_HIDDEN` 은 신고 자동 숨김(시스템) */
export type PostModerationEventType = 'HIDDEN' | 'UNHIDDEN' | 'DELETED' | 'RESTORED' | 'MOVED' | 'NOTICE' | 'PINNED' | 'UNPINNED' | 'REPORT_HIDDEN' | 'POLL_DELETED';

export interface PostModerationActor {
    kind: 'OPERATOR' | 'SYSTEM';
    id: string | null;
    /** 운영자 이름(없어진 운영자·시스템이면 null) */
    name: string | null;
}

export interface PostModerationEvent {
    id: string;
    type: PostModerationEventType;
    actor: PostModerationActor;
    reason: string | null;
    /** 종류별 덧붙임(이동 전후 게시판 등) — 모양은 서버가 정한다 */
    detail: Record<string, unknown> | null;
    at: string;
}

/** `GET /api/admin/posts/{id}` */
export interface AdminPostDetail {
    post: AdminPostItem;
    content: AdminPostContent;
    /** 조치 기록(최근 것부터) */
    events: PostModerationEvent[];
    /** 공개 사이트의 그 글 절대 주소 — 사이트 공개 주소를 정하지 않았으면 null */
    publicUrl: string | null;
    /** 글에 붙은 투표(post-poll-contract) — 없으면 null. 옛 응답에는 없을 수 있다 */
    poll?: AdminPollView | null;
}

/** 투표 선택지 하나와 그 득표 수 — 누가 골랐는지는 없다(익명) */
export interface AdminPollOptionView {
    id: string;
    label: string;
    votes: number;
}

/**
 * 게시글 관리 상세의 투표(서버 `AdminPollView`) — **집계만**: 마감 전에도 참여 수·선택지별 득표를 보이고, 누가 무엇을 골랐는지는 없다(계약 「API」 관리자).
 */
export interface AdminPollView {
    id: string;
    question: string;
    multiple: boolean;
    changeAllowed: boolean;
    /** 마감 시각 — null 이면 마감 없음 */
    closesAt: string | null;
    /** 마감 시각이 지났다 */
    closed: boolean;
    /** 참여한 사람 수(복수 선택이어도 한 사람은 한 번) */
    voterCount: number;
    /** 선택지 차례대로 — 득표 포함 */
    options: AdminPollOptionView[];
    createdAt: string;
}

/** 사유만 받는 조치(숨김 해제·삭제·복구·공지·고정·고정 해제) */
export interface PostReasonRequest {
    reason: string;
}

export interface PostHideRequest {
    reason: string;
    /** 작성자에게 `POST_HIDDEN` 알림(사유 포함) */
    notifyAuthor: boolean;
}

export interface PostMoveRequest {
    boardId: string;
    prefixId: string | null;
    reason: string;
}

/** `DELETE /api/admin/posts/{id}/poll` 본문 — 사유 1~200자 */
export interface PostPollDeleteRequest {
    reason: string;
}

export type PostBulkAction = 'HIDE' | 'UNHIDE' | 'DELETE';

/** `POST /api/admin/posts/bulk` — 글마다 따로 처리한다 */
export interface PostBulkRequest {
    action: PostBulkAction;
    /** 1~50 */
    ids: string[];
    reason: string;
    notifyAuthor: boolean;
}

export interface PostBulkFailure {
    id: string;
    code: string;
}

export interface PostBulkResult {
    done: string[];
    failed: PostBulkFailure[];
}
