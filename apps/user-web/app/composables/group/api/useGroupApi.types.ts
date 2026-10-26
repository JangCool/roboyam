import type { Role } from '~/composables/auth/api/useAuthApi.types';
import type { AuthorDecoration } from '~/composables/pointshop/api/usePointShopApi.types';

/**
 * 소모임 API 가 주고받는 모양 — community `docs/api-groups.md`(1·2·3·4·5(5-1 제외)·7절)와 같은 이름이다(서버 에이전트도 같은 원본을 따른다고 가정).
 * 로보얌 관례: 응답은 `ApiResponse` 래퍼(`useApiClient` 가 벗겨 `data` 만 돌려준다), ID 는 모두 문자열, 시각은 ISO-8601 UTC 문자열(보이는 것은 `useDisplayTime`).
 * 원본과 다르게 둔 것은 `useGroupApi.ts` 머리말에 적는다.
 */

/**
 * 소모임 화면의 사람(모임장·멤버·신청자·신규 가입자) — community `AuthorView` 중 화면이 쓰는 칸. 로보얌 쪽지 상대(`DmPartner`)와 같은 모양으로 가정했다.
 * **탈퇴한 회원은 `id: null`·`withdrawn: true`**(서버가 대체 이름을 `nickname` 에 넣을 수도 있다 — 화면은 `withdrawn` 이면 「탈퇴한 회원」으로 보인다).
 */
export interface GroupUser {
    id: string | null;
    nickname: string;
    /** 등급 키(`gradeKey`) — 없으면 null */
    grade: string | null;
    gradeLabel: string | null;
    role?: Role | null;
    withdrawn: boolean;
    /** 내가 남긴 이용자 메모(나에게만) — 칸이 없으면 메모 없음 */
    memo?: string | null;
    decoration?: AuthorDecoration | null;
    avatarUrl?: string | null;
}

/** 소모임의 분류 — 게시판이 할당된 분류에서 파생된다. 할당이 풀리면 null(태그를 그리지 않는다) */
export interface GroupCategory {
    id: string;
    name: string;
}

/** 소모임이 글을 담는 소모임 게시판(목록 줄) */
export interface GroupBoard {
    slug: string;
    name: string;
}

/** 분류 칩·만들기 화면의 분류 한 개 속 게시판 — 켜진 게시판만 */
export interface GroupCategoryBoard {
    slug: string;
    name: string;
    /** 투자 고지 게시판 — 만들기 화면의 추가 동의가 이 값을 쓴다 */
    investmentNotice: boolean;
}

/** `GET /api/groups/categories` 의 분류 한 개 — 켜진 게시판이 하나 이상 할당된 분류만, 관리자가 정한 순서로 */
export interface GroupCategoryItem {
    id: string;
    name: string;
    /** 그 분류의 열린(ACTIVE) 소모임 수 */
    groupCount: number;
    investmentNotice: boolean;
    boards: GroupCategoryBoard[];
}

export interface GroupCategoriesResponse {
    /** 열린 소모임 전체 수(분류 없는 소모임 포함) — 「전체」 칩의 숫자 */
    total: number;
    items: GroupCategoryItem[];
}

/** 가입 방식 — 자유 가입 · 신청제 */
export type GroupJoinType = 'OPEN' | 'APPROVAL';

/** 모집 상태 — 모집 중 · 정원 참 · 모임장이 마감 */
export type GroupRecruitState = 'OPEN' | 'FULL' | 'CLOSED';

export type GroupStatus = 'PENDING' | 'ACTIVE' | 'REJECTED' | 'INACTIVE' | 'DELETED';

/** 보는 사람의 상태 — 비회원은 null. ⚠ 매니저도 `MEMBER` 다(역할은 `myRole`) */
export type GroupMyState = 'NONE' | 'MEMBER' | 'LEADER' | 'APPLIED' | 'REJECTED' | 'KICKED';

/** 목록 정렬 — 인기순(기본) · 신규 · 모집중 · 내 소모임(로그인 필요) */
export type GroupSort = 'popular' | 'new' | 'recruiting' | 'mine';

/** 목록·검색·내 소모임의 한 줄 */
export interface GroupRow {
    id: string;
    name: string;
    /** 소개를 한 줄로 줄인 것 */
    summary: string;
    category: GroupCategory | null;
    board: GroupBoard;
    joinType: GroupJoinType;
    capacity: number;
    memberCount: number;
    recruiting: boolean;
    recruitState: GroupRecruitState;
    offline: boolean;
    /** 이 소모임의 게시판이 투자 고지 대상 — 「종목 추천 금지」를 붙인다 */
    stockNotice: boolean;
    myState: GroupMyState | null;
}

export interface GroupListQuery {
    /** 분류 id */
    category?: string;
    sort?: GroupSort;
    /** 이름·소개 검색어(1~30자) */
    q?: string;
    page?: number;
    size?: number;
}

export interface GroupListResponse {
    items: GroupRow[];
    page: number;
    hasNext: boolean;
    total: number;
}

/** 가입·신청을 못 하는 이유 */
export type GroupJoinReason =
    | 'LOGIN_REQUIRED'
    | 'EMAIL_NOT_VERIFIED'
    | 'ACCOUNT_RESTRICTED'
    | 'BUSINESS_ACCOUNT_RESTRICTED'
    | 'ALREADY_MEMBER'
    | 'ALREADY_APPLIED'
    | 'CLOSED'
    | 'FULL'
    | 'REAPPLY_LATER';

/** 보는 사람이 지금 가입·신청할 수 있나 — `method`: JOIN(바로 가입) · APPLY(신청) */
export interface GroupJoinInfo {
    allowed: boolean;
    method: 'JOIN' | 'APPLY';
    reason: GroupJoinReason | null;
    /** `REAPPLY_LATER` 일 때만 */
    retryAfterSeconds: number | null;
}

export type GroupChatKind = 'KAKAO' | 'DISCORD';

export interface GroupChatLink {
    kind: GroupChatKind;
    url: string;
    label: string | null;
}

/** 소모임 안의 역할 — 매니저(`MANAGER`)는 3단계에서 화면을 연다 */
export type GroupRole = 'LEADER' | 'MANAGER' | 'MEMBER';

/** 모임장·매니저의 쓰기가 멈춘 까닭 — 이메일 미인증(먼저) · 이용 제한 중. 멈춘 동안에도 `can*` 는 그대로 true 다 */
export type GroupManageBlockedReason = 'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED';

/**
 * 보는 사람의 권한 — 역할만 반영한다. `canManage` 는 모임장(3단계부터 매니저 포함), `canEditGroup` 은 모임장 고정 일(정보·채팅·넘기기·해산).
 * 체크형 칸(`canHandleApplications` 등)이 없는 응답은 `canManage` 로 채운다(`UtilGroup.permissionsOf`).
 */
export interface GroupPermissions {
    canManage: boolean;
    canWritePost: boolean;
    canEditGroup?: boolean;
    canAppointManager?: boolean;
    canHandleApplications?: boolean;
    canKickMembers?: boolean;
    canSetRecruiting?: boolean;
    canManageEvents?: boolean;
    canUnpublishPosts?: boolean;
    manageBlockedReason?: GroupManageBlockedReason | null;
}

/** 칸을 모두 채운 권한(화면이 쓰는 모양) */
export type GroupResolvedPermissions = Required<Omit<GroupPermissions, 'manageBlockedReason'>> & { manageBlockedReason: GroupManageBlockedReason | null };

/** 상세 「모임 정보」의 신규 가입자 — 멤버에게만 */
export interface GroupNewcomers {
    windowDays: number;
    count: number;
    items: GroupUser[];
}

/** 지금 적용되는 운영 한도 — 칸이 없으면(옛 서버) 기본값 */
export interface GroupLimits {
    capacityMin: number;
    capacityMax: number;
    eventMaxDaysAhead?: number;
    eventMaxLengthDays?: number;
}

/**
 * 소모임 상세. **비활성(`INACTIVE`)이면 `id`·`name`·`status` 만 채워지고 나머지는 null(또는 없음)** 이다.
 * 비멤버는 `chat` 이 null 이고 `chatLocked` 가 true 다. 상세의 `board`(말머리·투자 고지)는 2단계(글쓰기)에서 읽는다.
 */
export interface GroupDetail {
    id: string;
    name: string;
    status: GroupStatus;
    description?: string | null;
    category?: GroupCategory | null;
    board?: (GroupBoard & { investmentNotice?: boolean }) | null;
    joinType?: GroupJoinType | null;
    capacity?: number | null;
    memberCount?: number | null;
    recruiting?: boolean | null;
    recruitState?: GroupRecruitState | null;
    offline?: boolean | null;
    openedAt?: string | null;
    leader?: GroupUser | null;
    stockNotice?: boolean | null;
    myState?: GroupMyState | null;
    join?: GroupJoinInfo | null;
    chat?: { links: GroupChatLink[] } | null;
    chatLocked?: boolean;
    permissions?: GroupPermissions | null;
    myRole?: GroupRole | null;
    newcomers?: GroupNewcomers | null;
    /** 모임장(3단계부터 `APPLICATIONS` 매니저)에게만 승인 대기 수 — 그 밖은 null(0 은 「없음」, null 은 「볼 수 없음」) */
    pendingApplicationCount?: number | null;
    limits?: GroupLimits | null;
}

/** 멤버 탭 한 줄 — 모임장 → (매니저) → 멤버, 같은 역할 안에서는 가입이 이른 순 */
export interface GroupMember {
    user: GroupUser;
    role: GroupRole;
    joinedAt: string;
}

export interface GroupMemberListQuery {
    page?: number;
    size?: number;
}

export interface GroupMemberListResponse {
    items: GroupMember[];
    page: number;
    hasNext: boolean;
    total: number;
}

/** 내 소모임 — 가입한 모임(`newPostCount` 는 2단계, `nextEvent` 는 4단계에서 채운다 — 1단계는 늘 0·null) */
export interface MyGroupRow extends GroupRow {
    role: GroupRole;
    joinedAt: string;
    newPostCount: number;
    nextEvent: unknown | null;
}

/** 내 소모임 — 승인 대기 중인 가입 신청 */
export interface MyApplicationRow extends GroupRow {
    appliedAt: string;
}

/** 내가 낸 개설 신청(처리 전·반려) */
export interface MyGroupRequest {
    id: string;
    name: string;
    category: GroupCategory | null;
    /** 신청이 쓴 게시판 — 반려된 신청을 다시 낼 때 고른 게시판을 되살린다 */
    board?: GroupBoard | null;
    joinType: GroupJoinType;
    capacity: number;
    description: string;
    offline: boolean;
    status: 'PENDING' | 'REJECTED';
    rejectReason: string | null;
    requestedAt: string;
    reviewedAt: string | null;
}

export interface MyGroupsResponse {
    joined: MyGroupRow[];
    applications: MyApplicationRow[];
    requests: MyGroupRequest[];
}

/** 개설 신청 본문 */
export interface GroupCreateRequest {
    name: string;
    description: string;
    categoryId: string;
    /** 그 분류 안에서 고른 게시판 — 화면은 늘 보낸다 */
    boardSlug: string;
    joinType: GroupJoinType;
    capacity: number;
    offline: boolean;
    agreeRules: boolean;
    agreeStock: boolean;
    agreeOffline: boolean;
}

/** 개설 방식 — 승인제 · 자유 개설 */
export type GroupCreationMode = 'APPROVAL' | 'OPEN';

/** `GET /api/groups/creation-rules` — 만들기 화면의 안내 재료. 로그인하면 `me` 가 붙는다 */
export interface GroupCreationRules {
    creationMode: GroupCreationMode;
    minGrade: string;
    minGradeLabel: string;
    maxPerOwner: number;
    capacityMin?: number;
    capacityMax?: number;
    me: {
        canCreate: boolean;
        reason: 'GRADE_TOO_LOW' | 'LIMIT_REACHED' | null;
        grade: string;
        ledCount: number;
    } | null;
}

/** 일반 회원은 `202 PENDING`(승인제), 자유 개설·운영자는 `201 ACTIVE` */
export interface GroupCreateResult {
    id: string;
    status: 'PENDING' | 'ACTIVE';
}

export interface GroupJoinRequest {
    /** 신청 한마디(선택, 100자) */
    message?: string;
    /** 오프라인 모임 개인 책임 고지 동의 */
    offlineAck?: boolean;
}

/** 바로 가입은 `200 JOINED`(+`memberCount`), 신청은 `202 APPLIED` */
export interface GroupJoinResult {
    result: 'JOINED' | 'APPLIED';
    memberCount?: number | null;
}

/** 정보 수정 본문 — 모든 칸(분류는 바꿀 수 없다) */
export interface GroupUpdateRequest {
    name: string;
    description: string;
    joinType: GroupJoinType;
    capacity: number;
    offline: boolean;
}

export interface GroupChatLinkInput {
    url: string;
    label?: string;
}

/** 채팅 링크 저장 본문 — null 이면 그 링크는 지워진다 */
export interface GroupChatLinksRequest {
    kakao: GroupChatLinkInput | null;
    discord: GroupChatLinkInput | null;
}

/** 가입 신청 한 건 — 오래된 신청이 위 */
export interface GroupApplication {
    user: GroupUser;
    message: string | null;
    appliedAt: string;
    /** 한 번 강퇴된 적이 있는 회원 */
    kickedBefore: boolean;
}

export interface GroupApplicationListResponse {
    items: GroupApplication[];
    capacity: number;
    memberCount: number;
}
