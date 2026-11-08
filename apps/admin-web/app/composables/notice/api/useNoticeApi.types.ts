/**
 * 공지 관리 API(`/api/admin/notices…`)가 주고받는 모양 — 계약 `docs/features/notice-admin-contract.md`(서버 admin `com.community.notice` 의 DTO 와 같은 칸 이름).
 * 공지 = 공개 사이트 공지 게시판(slug `notice`)의 글이다. 새 저장소가 아니라 그 게시판 글을 관리자 앱에서 쓰고·고치고·지우고·고정한다.
 * 글 한 줄은 게시글 관리의 `AdminPostItem`(usePostApi.types)과 같은 칸 이름 위주다(상태 알약 `PostStateTag` 를 같이 쓴다).
 * ID 는 모두 문자열, 시각은 UTC ISO 글자.
 */

import type { AdminPostPrefixRef, PostHiddenReason, PostState, PostStateFilter } from '~/composables/post/api/usePostApi.types';

/** 내 운영자 회원 계정 연결 상태 — 연결됨 · 연결 없음 · 연결됐지만 멈춤(운영자 정지·회원 정지 — operator-member-account-contract) */
export type NoticeWriterState = 'LINKED' | 'NOT_LINKED' | 'PAUSED';

/** 멈춤 까닭 — operator-member-account-contract 의 `pausedReasons` 와 같은 값 */
export type NoticeWriterPausedReason = 'OPERATOR_UNAVAILABLE' | 'MEMBER_SUSPENDED';

/** 공지 띠 내용 출처 — site-notice-bar-contract 의 `source` */
export type NoticeBarSource = 'BOARD' | 'CUSTOM';

/** 공지 게시판 — 없으면(slug `notice` 게시판이 없음) 컨텍스트의 `board` 가 null */
export interface NoticeBoard {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
    /** 켜져 있다 — 꺼져 있으면 화면은 목록 대신 안내를 보인다 */
    active: boolean;
    /** 고정 한도(게시판 정책 `pinnedMax`) */
    pinnedMax: number;
    /** 지금 고정 글 수(지우지 않은 고정 글 — 게시글 관리와 같은 셈) */
    pinnedCount: number;
    /** 켜진 말머리 */
    prefixes: AdminPostPrefixRef[];
    /** 공개 사이트의 이 게시판 주소(사이트 공개 주소 + `/boards/notice`). 공개 주소가 없으면 null */
    publicUrl: string | null;
}

/** 공지를 올릴 내 운영자 회원 계정 */
export interface NoticeWriter {
    state: NoticeWriterState;
    userId: string | null;
    nickname: string | null;
    pausedReasons: NoticeWriterPausedReason[];
}

/** 공지 띠 요약(설정 > 일반 `notice-bar`) */
export interface NoticeBarSummary {
    enabled: boolean;
    source: NoticeBarSource;
    /** 공지 게시판 최근 글 수(5·10) */
    boardCount: number;
}

/** 고정 범위 — 공지 게시판에만(기본) · 모든 게시판 맨 위에도(전체 공지, V174) */
export type NoticePinScope = 'NOTICE_ONLY' | 'ALL';

/** 뺄 게시판 고르기 칩 하나 */
export interface NoticeBoardRef {
    id: string;
    slug: string;
    name: string;
    nameEn: string | null;
}

/** 컨텍스트의 전체 공지 요약 · 게시판 고르기 목록 */
export interface NoticeGlobalContext {
    /** 지금 살아 있는 전체 공지 수(지운 · 숨긴 · 기간 지난 것 빼고) */
    count: number;
    /** 동시에 걸 수 있는 수(3) */
    max: number;
    /** 켜진 일반 게시판(공지 게시판 자신 · 소모임 게시판 빼고), 정렬 순서대로 */
    boards: NoticeBoardRef[];
    /** 켜진 소모임 게시판 */
    groupBoards: NoticeBoardRef[];
}

/** `GET /api/admin/notices/context` */
export interface NoticeContext {
    board: NoticeBoard | null;
    writer: NoticeWriter;
    noticeBar: NoticeBarSummary;
    global: NoticeGlobalContext;
}

/** 공지의 전체 공지 설정 — 고정 범위가 모든 게시판(ALL)인 고정 글만 실린다 */
export interface NoticeGlobal {
    excludedBoardIds: string[];
    /** 소모임 게시판 전체를 뺐다(새로 만들 소모임 게시판 포함) */
    excludeAllGroupBoards: boolean;
    /** 다른 게시판에서 내려갈 시각(null = 풀 때까지) */
    expiresAt: string | null;
    /** 기간이 지나 다른 게시판에서는 내려갔다(공지 게시판 고정은 그대로) */
    expired: boolean;
}

/** 작성자 — 운영자 회원 계정(회원). `operatorName` 은 그 계정에 이어진 운영자 이름(연결이 끊겼으면 null) */
export interface NoticeAuthor {
    kind: 'MEMBER' | 'GUEST';
    id: string | null;
    name: string;
    withdrawn: boolean;
    operatorName: string | null;
}

/** 공지 한 줄 — 상태 칸은 `AdminPostItem` 과 같다(`PostStateTag` 가 읽는다) */
export interface NoticeItem {
    id: string;
    prefix: AdminPostPrefixRef | null;
    title: string;
    author: NoticeAuthor;
    state: PostState;
    hiddenReason: PostHiddenReason | null;
    hiddenAt: string | null;
    hiddenUntil: string | null;
    deletedAt: string | null;
    deleteScheduledAt: string | null;
    purged: boolean;
    pinned: boolean;
    pinnedAt: string | null;
    viewCount: number;
    commentCount: number;
    createdAt: string;
    /** 마지막 수정 시각(고친 적 없으면 null) */
    editedAt: string | null;
    /** 공개 사이트의 글 주소(사이트 공개 주소 + `/posts/{id}`). 공개 주소가 없으면 null */
    publicUrl: string | null;
    /** 전체 공지 설정 — 범위가 모든 게시판이 아니면 null */
    global: NoticeGlobal | null;
}

/** `GET /api/admin/notices` — 고정 글이 먼저 */
export interface NoticeListView {
    total: number;
    /** 0부터 */
    page: number;
    size: number;
    items: NoticeItem[];
}

/** `GET /api/admin/notices` 조건 — 빈 값은 보내지 않는다 */
export interface NoticeQuery {
    /** 제목 검색어 */
    q?: string;
    state?: PostStateFilter;
    /** 말머리 ID */
    prefix?: string;
    /** 0부터 */
    page?: number;
    size?: number;
}

/** `GET /api/admin/notices/{id}` */
export interface NoticeDetail {
    notice: NoticeItem;
    content: { format: 'HTML'; body: string };
}

/** `POST /api/admin/notices` · `PUT /api/admin/notices/{id}` */
export interface NoticeSaveRequest {
    prefixId: string | null;
    title: string;
    /** 서식 HTML(서버가 정화한다) */
    body: string;
    pinned: boolean;
    /** 고정 범위(고정하지 않으면 무시) */
    scope: NoticePinScope;
    /** 전체 공지 — 뺄 게시판 ID */
    excludedBoardIds: string[];
    /** 전체 공지 — 소모임 게시판 전체 빼기 */
    excludeAllGroupBoards: boolean;
    /** 전체 공지 — 다른 게시판에서 내려갈 시각(UTC ISO, null = 풀 때까지) */
    expiresAt: string | null;
}

/** `POST /api/admin/notices` → 201 */
export interface NoticeCreated {
    id: string;
}
