/**
 * 소모임 개설 승인·소모임 관리 API 가 주고받는 모양 — community `docs/api-groups.md` 6절(6-1 개설 승인 · 6-2 소모임 관리 · 11-9 공개 범위 읽기 칸)과 같은 이름이다.
 * ⚠ ID 는 모두 문자열. 시각은 UTC ISO 문자열 — 그리는 것은 `useDisplayTime()` 이다.
 * 서버(`server/admin`)가 이 모양 그대로 준다 — 서버 DTO 이름도 이 이름을 따른다(`server/admin/.../group/dto/response/*` 와 칸 단위 대조 2026-10-26).
 * `postCount`·`publicPostCount` 는 소모임 글(2단계)이 들어오면 서버가 채운다.
 */

/**
 * 사람 한 명(모임장·신청자·처리자·매니저) — 서버 `GroupPersonView`. 닉네임·등급 뱃지 외 개인정보는 싣지 않는다.
 * 서버는 공개 사이트와 같은 모양이라 `decoration`·`avatarUrl` 칸도 있지만 관리자 응답에서는 늘 null 이다(화면은 쓰지 않는다).
 */
export interface GroupPerson {
    /** 회원 ID(운영자면 운영자 ID). 탈퇴했으면 null */
    id: string | null;
    nickname: string;
    /** 탈퇴한 회원(또는 지워진 운영자) — 서버가 대체 이름을 `nickname` 에 넣는다 */
    withdrawn: boolean;
    /** 등급 뱃지(등급 설정의 키·이름·색·아이콘). 운영자·모르는 등급·탈퇴면 null */
    grade: { key?: string; label: string; color: string; icon: string | null } | null;
    /** 관리자 응답에서는 늘 null(공개 사이트용 칸) */
    decoration?: unknown;
    /** 관리자 응답에서는 늘 null(공개 사이트용 칸) */
    avatarUrl?: string | null;
    /** 관리자 응답에서는 늘 false(공개 사이트의 「사업자」 뱃지 칸 — 그리지 않는다) */
    business?: boolean;
}

/** 소모임 분류 — 분류 ID 와 이름(코드에 분류 목록을 박지 않는다) */
export interface GroupCategoryRef {
    id: string;
    name: string;
}

/** 소모임이 글을 담는 게시판(소모임 게시판, 게시판 종류 `GROUP`) — 분류에서 빠져도, 게시판이 꺼져도 그대로다 */
export interface GroupBoardRef {
    slug: string;
    name: string;
    /** 영어 이름(없으면 null) — 화면은 지금 쓰지 않는다 */
    nameEn?: string | null;
}

/** 가입 방식 — 자유 가입 · 신청제(모임장 승인) */
export type GroupJoinType = 'OPEN' | 'APPROVAL';

/** 공개 범위 — 공개 소모임(비멤버도 글을 읽는다) · 비공개 소모임. 관리자 화면은 읽기만 한다 */
export type GroupVisibility = 'PUBLIC' | 'PRIVATE';

/** 공개 범위 칩 — 전체(조건 없음) · 공개 · 비공개 */
export type GroupVisibilityFilter = 'ALL' | GroupVisibility;

/** 승인 대기(`PENDING`) 개설 신청 한 건 — `GET /api/admin/groups/requests` */
export interface GroupOpenRequest {
    id: string;
    name: string;
    description: string;
    category: GroupCategoryRef | null;
    /** 신청이 쓸 게시판 */
    board: GroupBoardRef;
    joinType: GroupJoinType;
    capacity: number;
    offline: boolean;
    requester: GroupPerson;
    requestedAt: string;
    /** 투자 고지 게시판에서 「공부·산업 해설 목적만」 추가 동의를 했는가 — 투자 고지 게시판일 때만 true 로 기록된다 */
    stockPurposeAgreed: boolean;
    /** 신청한 공개 범위 */
    visibility: GroupVisibility;
}

/** `GET /api/admin/groups/requests` — 오래된 신청이 위 */
export interface GroupOpenRequestList {
    items: GroupOpenRequest[];
}

/** 반려 본문 — 사유 1~200자(필수), 신청자에게 그대로 전달된다 */
export interface GroupRejectRequest {
    reason: string;
}

/** 관리 대상 상태 — 열림 · 비활성. 승인 대기·반려·해산은 이 목록에 나오지 않는다 */
export type GroupStatus = 'ACTIVE' | 'INACTIVE';

/** 상태 칩 — 전체 · 열림 · 비활성 */
export type GroupStatusFilter = 'ALL' | GroupStatus;

/** 모집 상태 — 모집 중 · 정원 참 · 모임장이 마감 */
export type GroupRecruitState = 'OPEN' | 'FULL' | 'CLOSED';

/** 소모임 관리 목록의 한 줄 — `GET /api/admin/groups` */
export interface GroupRow {
    id: string;
    name: string;
    /** 분류 — 쓰는 게시판이 어느 분류에도 없으면 null(「분류 없음」, 소모임은 그대로 열려 있다) */
    category: GroupCategoryRef | null;
    board: GroupBoardRef;
    leader: GroupPerson;
    memberCount: number;
    capacity: number;
    status: GroupStatus;
    recruitState: GroupRecruitState;
    joinType: GroupJoinType;
    offline: boolean;
    /** 투자 고지 게시판에서 추가 동의를 받은 소모임 */
    stockNotice: boolean;
    visibility: GroupVisibility;
    openedAt: string | null;
    lastActivityAt: string | null;
    /** 지금 비활성일 때만 값이 있다 */
    deactivatedAt: string | null;
}

/** 칩 숫자 — 검색어만 적용하고 상태·성질·공개 범위 필터는 적용하지 않는다 */
export interface GroupCounts {
    all: number;
    active: number;
    inactive: number;
    closed: number;
    offline: number;
}

/** `GET /api/admin/groups` 응답 */
export interface GroupListPage {
    items: GroupRow[];
    /** 0부터 */
    page: number;
    size: number;
    hasNext: boolean;
    /** 지금 조건에 맞는 전체 개수 */
    total: number;
    counts: GroupCounts;
    /** 승인 대기 개설 신청 수(조건과 무관) */
    pendingRequests: number;
}

/** 목록 요청의 쿼리 — `GET /api/admin/groups?q=&status=&closed=&offline=&visibility=&page=&size=` */
export interface GroupListQuery {
    q?: string;
    status?: GroupStatus;
    closed?: boolean;
    offline?: boolean;
    visibility?: GroupVisibility;
    /** 0부터 */
    page?: number;
    /** 기본 20, 최대 100 */
    size?: number;
}

/** 마지막으로 비활성이 된 때의 기록 — 다시 열어도 남는다 */
export interface GroupDeactivation {
    at: string | null;
    /** 처리한 운영자. 기록이 없으면 null */
    by: GroupPerson | null;
    /** 모임장에게 전달한 사유 */
    reason: string | null;
    /** 신고 처리로 비활성이 됐을 때 그 사건(서랍이 `/reports?report={id}` 로 잇는다 — `report.read` 가 없으면 번호만) */
    reportCase: { id: string; no: number } | null;
    reactivatedAt: string | null;
    reactivatedBy: GroupPerson | null;
    /** 다시 열 때의 메모 — 운영자만 본다 */
    memo: string | null;
}

/** 모임장이 가진 소모임 수 — 신청 중·열림·비활성 합(개설 상한 판단과 같다) */
export interface GroupLeaderLoad {
    total: number;
    active: number;
    inactive: number;
    max: number;
}

/** 소모임 매니저가 맡을 수 있는 일 — 5개가 전부이고 서버 배열은 늘 이 순서다 */
export type GroupManagerPermission = 'APPLICATIONS' | 'KICK' | 'RECRUITING' | 'EVENTS' | 'UNPUBLISH';

/** 매니저 한 줄(읽기 전용) */
export interface GroupManagerRow {
    user: GroupPerson;
    appointedAt: string | null;
    /** 맡은 일 — 모르는 값은 화면이 그리지 않는다 */
    permissions: string[];
}

/** 소모임 상세 — `GET /api/admin/groups/{id}`(읽기 전용 정보 + 비활성 기록) */
export interface GroupDetail {
    id: string;
    name: string;
    description: string;
    category: GroupCategoryRef | null;
    board: GroupBoardRef;
    status: GroupStatus;
    joinType: GroupJoinType;
    recruiting: boolean;
    recruitState: GroupRecruitState;
    capacity: number;
    memberCount: number;
    offline: boolean;
    stockNotice: boolean;
    /** 채팅 링크가 있는지만(주소는 싣지 않는다) */
    chat: { kakao: boolean; discord: boolean };
    leader: GroupPerson;
    creator: GroupPerson | null;
    /** 개설을 승인한 사람(관리자가 직접 만든 소모임이면 만든 사람) */
    reviewer: GroupPerson | null;
    requestedAt: string | null;
    openedAt: string | null;
    lastActivityAt: string | null;
    leaderLoad: GroupLeaderLoad;
    postCount: number;
    /** 비멤버에게 보이는 글 수 */
    publicPostCount: number;
    reportCaseCount: number;
    deactivation: GroupDeactivation | null;
    managers: GroupManagerRow[];
    /** 이 소모임에 정해 둔 매니저 인원 상한 */
    managerLimit: number;
    /** 매니저 규칙의 최대 인원 */
    managerLimitMax: number;
    visibility: GroupVisibility;
    /** 마지막으로 공개 범위를 바꾼 시각 — 개설 때 정한 값이면 null */
    visibilityChangedAt: string | null;
    visibilityChangedBy: GroupPerson | null;
}

/** 비활성화 본문 — 사유 1~200자(필수), 모임장에게 그대로 전달된다 */
export interface GroupDeactivateRequest {
    reason: string;
}

/** 다시 열기 본문 — 메모 0~200자(선택), 운영자만 본다 */
export interface GroupReactivateRequest {
    memo: string;
}
