import type { Role } from '~/composables/auth/api/useAuthApi.types';
import type { PostGroupPublicBy, PostGroupVisibility, PostRow, PostWriteRequest } from '~/composables/board/api/usePostApi.types';
import type { AuthorDecoration } from '~/composables/pointshop/api/usePointShopApi.types';

/**
 * 소모임 API 가 주고받는 모양 — 1단계(1·2·3·4·5(5-1 제외)·7절)는 서버 `server/api/.../group/dto/response/*` 와 칸 단위로 맞췄다(2026-10-26).
 * 2단계(8절 소모임 글)는 서버 `GroupPost*View`·`PostGroupView`·`GroupDetailView`(`postCount`·`newPostCount`)와 칸 단위로 맞췄다(2026-10-26).
 * 3단계(5-1 매니저)는 서버 `GroupManagerListView`·`GroupDetailView.ManagerInfo`·`Permissions` 와 맞췄다(community 5-1 과 같은 경로·칸 — `useGroupApi.ts` 머리말).
 * 4단계(9절 일정)는 서버 `GroupEventRowView`·`GroupEventDetailView`·`GroupEventPageView`·`GroupEventAttendeePageView`·`GroupEventResponseResultView`·`GroupEventRequest`·
 * `MyGroupsView.NextEvent` 와 칸 단위로 맞췄다(2026-10-26).
 * 5단계(11절 공개 범위·공개 요청)는 community `docs/api-groups.md` 11절과 같은 이름·칸이고, 서버 `GroupVisibilityViews`·`GroupDetailView`(`VisibilityInfo`·`Permissions`)와 대조했다(`useGroupApi.ts` 머리말).
 * 로보얌 관례: 응답은 `ApiResponse` 래퍼(`useApiClient` 가 벗겨 `data` 만 돌려준다), ID 는 모두 문자열, 시각은 ISO-8601 UTC 문자열(보이는 것은 `useDisplayTime`).
 * 원본과 다르게 둔 것은 `useGroupApi.ts` 머리말에 적는다.
 */

/**
 * 소모임 화면의 사람(모임장·멤버·신청자·신규 가입자) — 서버 `DmPartnerView`(쪽지 상대와 같은 모양)
 * `{ id|null, nickname, grade, gradeLabel, role: "USER", withdrawn, memo: null, decoration, avatarUrl }`.
 * **탈퇴한 회원은 `id: null`·`withdrawn: true`**(서버가 대체 이름을 `nickname` 에 넣는다 — 화면은 `withdrawn` 이면 「탈퇴한 회원」으로 보인다). 소모임 응답의 `memo` 는 늘 null 이다.
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

/** 소모임이 글을 담는 소모임 게시판(목록 줄 — 서버 `GroupBoardRefView`). `nameEn` 은 영어 이름(없으면 null) */
export interface GroupBoard {
    slug: string;
    name: string;
    nameEn?: string | null;
}

/** 분류 칩·만들기 화면의 분류 한 개 속 게시판 — 켜진 게시판만 */
export interface GroupCategoryBoard {
    slug: string;
    name: string;
    nameEn?: string | null;
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

/**
 * 공개 범위 — 공개 소모임(글이 쓰는 순간 모두에게 보인다) · 비공개 소모임(기본 — 공개 요청·직접 공개한 글만 모두에게 보인다).
 * 공개 범위 기능이 꺼져 있으면 비공개 소모임은 응답 칸이 `null` 로 온다 — 화면은 칩·라디오·구획을 그리지 않는다(`UtilGroupVisibility.of`). 이미 공개인 소모임은 늘 `PUBLIC`.
 */
export type GroupVisibility = 'PUBLIC' | 'PRIVATE';

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
    /** 공개 범위 — `null`(기능 꺼짐·비공개)이면 칩을 그리지 않는다 */
    visibility?: GroupVisibility | null;
    myState: GroupMyState | null;
}

export interface GroupListQuery {
    /** 분류 id */
    category?: string;
    sort?: GroupSort;
    /** 이름·소개 검색어(1~30자) */
    q?: string;
    /** 1부터 */
    page?: number;
    size?: number;
    /** 뺄 소모임 ID(쉼표로 이은 최대 5개) — 「지금 뜨는 소모임」과 겹치지 않게. 모든 쪽에 같은 값을 보낸다 */
    exclude?: string;
}

export interface GroupListResponse {
    items: GroupRow[];
    /** 1부터 */
    page: number;
    hasNext: boolean;
    total: number;
}

/** `GET /api/groups/trending` — 지금 뜨는 소모임(최대 관리자 설정 개수, 기본 5). 후보가 없으면 빈 배열 */
export interface GroupTrendingResponse {
    items: GroupRow[];
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

/** 소모임 안의 역할 — 모임장 · 매니저(모임장이 맡긴 일만 한다 — 3단계) · 멤버 */
export type GroupRole = 'LEADER' | 'MANAGER' | 'MEMBER';

/**
 * 모임장·매니저의 쓰기가 멈춘 까닭 — 이메일 미인증(먼저) · 이용 제한 중 · 사업자 계정(서버 `GroupAccess.writeBlocked` 차례). 멈춘 동안에도 `can*` 는 그대로 true 다.
 * 사업자 계정(`BUSINESS_ACCOUNT_RESTRICTED`)도 쓰기만 막히고 나가기·신청 취소는 된다.
 */
export type GroupManageBlockedReason = 'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED' | 'BUSINESS_ACCOUNT_RESTRICTED';

/**
 * 보는 사람의 권한(서버 `GroupDetailView.Permissions`) — 역할·맡은 일만 반영한다(정지·미인증은 `manageBlockedReason`).
 * `canManage` 는 모임장 **또는 매니저**(관리 화면에 들어온다), `canEditGroup` 은 모임장 고정 일(정보·채팅·넘기기·해산·매니저 임명 계열),
 * `canAppointManager` 는 모임장이고 매니저 기능이 켜짐, 체크형 다섯(`canHandleApplications`·`canKickMembers`·`canSetRecruiting`·`canManageEvents`·`canUnpublishPosts`)은
 * 모임장이거나 그 일을 맡은 매니저. 칸이 없는 옛 응답은 `canManage`(그때는 모임장)로 채운다(`UtilGroup.permissionsOf`).
 * 5단계: `canChangeVisibility`(모임장 — 기능이 꺼져 있으면 공개 소모임의 모임장에게만 true, 공개→비공개) · `canDecidePublishRequests`(기능이 켜져 있고 모임장 또는 `UNPUBLISH` 매니저).
 * 두 칸은 칸이 없으면 false 다(`canManage` 로 채우지 않는다 — 기능이 꺼진 서버일 수 있다).
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
    canChangeVisibility?: boolean;
    canDecidePublishRequests?: boolean;
    manageBlockedReason?: GroupManageBlockedReason | null;
}

/** 칸을 모두 채운 권한(화면이 쓰는 모양) */
export type GroupResolvedPermissions = Required<Omit<GroupPermissions, 'manageBlockedReason'>> & { manageBlockedReason: GroupManageBlockedReason | null };

/** 모임장에게만 오는 공개 범위 변경 정보 — `canChangeToPublicAt`: 비공개→공개 쿨다운이 끝나는 시각(쿨다운 밖이면 null) */
export interface GroupVisibilityInfo {
    /** 마지막으로 바꾼 시각 — 개설 때 정한 값 그대로면 null */
    changedAt: string | null;
    /** 마지막으로 바꾼 모임장(서버 `DmPartnerView` = `GroupUser`) — 없으면 null */
    changedBy: GroupUser | null;
    canChangeToPublicAt: string | null;
}

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
    /** 일정 한도(4단계) */
    eventMaxDaysAhead?: number;
    eventMaxLengthDays?: number;
}

/** 소모임 게시판의 켜진 말머리(순서대로) — 서버 `GroupDetailView.Prefix` */
export interface GroupBoardPrefix {
    id: string;
    name: string;
    /** 운영진 전용 말머리 */
    staffOnly: boolean;
    /** 공통 말머리 */
    common: boolean;
    color: string | null;
}

/** 상세의 소모임 게시판 — 서버 `GroupDetailView.Board`(색·투자 고지·켜진 말머리) */
export interface GroupDetailBoard extends GroupBoard {
    color?: string | null;
    investmentNotice?: boolean;
    prefixes?: GroupBoardPrefix[];
}

/** 멤버에게만 — 매니저 수와 상한(관리 메인의 매니저 줄·멤버 관리의 매니저 거르기·인원 설정·임명 시트의 인원 줄) */
export interface GroupManagerInfo {
    count: number;
    /** 유효 상한 = min(소모임 상한, 최대 인원) */
    limit: number;
    limitMax: number;
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
    board?: GroupDetailBoard | null;
    joinType?: GroupJoinType | null;
    capacity?: number | null;
    memberCount?: number | null;
    recruiting?: boolean | null;
    recruitState?: GroupRecruitState | null;
    offline?: boolean | null;
    /** 공개 범위 — `null`(기능 꺼짐·비공개)이면 칩·구획을 그리지 않는다 */
    visibility?: GroupVisibility | null;
    /** 모임장에게만 — 마지막 변경·쿨다운, 그 밖 null */
    visibilityInfo?: GroupVisibilityInfo | null;
    openedAt?: string | null;
    leader?: GroupUser | null;
    stockNotice?: boolean | null;
    myState?: GroupMyState | null;
    join?: GroupJoinInfo | null;
    chat?: { links: GroupChatLink[] } | null;
    chatLocked?: boolean;
    permissions?: GroupPermissions | null;
    myRole?: GroupRole | null;
    /** 멤버에게만 매니저 수·상한, 그 밖 null */
    managerInfo?: GroupManagerInfo | null;
    newcomers?: GroupNewcomers | null;
    /** 모임장·`APPLICATIONS` 매니저에게만 승인 대기 수 — 그 밖은 null(0 은 「없음」, null 은 「볼 수 없음」) */
    pendingApplicationCount?: number | null;
    /** `canDecidePublishRequests` 인 사람에게만 대기 중인 공개 요청 수(정지 중에도 온다) — 그 밖 null(0 은 「없음」, null 은 「볼 수 없음」) */
    pendingPublishRequestCount?: number | null;
    limits?: GroupLimits | null;
    /** 보는 사람에게 보이는 소모임 글 수 — 멤버는 보이는 글 전부, 비멤버·비회원은 공개로 전환된 글(지운·숨긴·나가서 가린 글 제외, 2단계). 비활성이면 null */
    postCount?: number | null;
    /** 멤버에게만 — 내가 글 탭을 마지막으로 본 뒤(본 적이 없으면 가입 뒤) 올라온 남의 글 수, 비멤버·비회원 null(2단계) */
    newPostCount?: number | null;
}

/** 멤버 탭 한 줄 — 모임장 → (매니저) → 멤버, 같은 역할 안에서는 가입이 이른 순 */
export interface GroupMember {
    user: GroupUser;
    role: GroupRole;
    joinedAt: string;
    /** 매니저 줄이고 보는 사람이 모임장·그 매니저 본인일 때만 맡은 일 키(고정 순서 — 모르는 키는 화면이 버린다), 그 밖 null */
    managerPermissions?: string[] | null;
}

// ── 매니저(3단계 — community `docs/api-groups.md` 5-1) ──

/** 매니저가 맡을 수 있는 일 — 이 5개가 전부이고 서버 배열은 늘 이 순서다(서버 `GroupManagerPermission`) */
export type GroupManagerPermission = 'APPLICATIONS' | 'KICK' | 'RECRUITING' | 'EVENTS' | 'UNPUBLISH';

/** `GET /api/groups/{id}/managers` 의 한 줄 — 임명이 이른 순 */
export interface GroupManagerItem {
    user: GroupUser;
    appointedAt: string;
    /** 보는 사람이 모임장이거나 그 매니저 본인일 때만 키 배열, 그 밖 null */
    permissions: string[] | null;
}

/** `GET /api/groups/{id}/managers` — 멤버만(비멤버 `403 GROUP_NOT_MEMBER`). `limit` 은 유효 상한 = min(소모임 상한, 최대 인원) */
export interface GroupManagerListResponse {
    items: GroupManagerItem[];
    count: number;
    limit: number;
    limitMax: number;
}

/** `PUT /api/groups/{id}/managers/{userId}` 본문 — 임명·맡은 일 바꾸기(같은 API, 1~5개·중복 없음) */
export interface GroupManagerSetRequest {
    permissions: GroupManagerPermission[];
}

/** `PUT /api/groups/{id}/manager-limit` 본문 — 1 이상 `limitMax` 이하, 지금 매니저 수 이상 */
export interface GroupManagerLimitRequest {
    limit: number;
}

export interface GroupMemberListQuery {
    page?: number;
    size?: number;
}

export interface GroupMemberListResponse {
    items: GroupMember[];
    /** 1부터 */
    page: number;
    hasNext: boolean;
    total: number;
}

/**
 * 내 소모임 — 가입한 모임(서버 `MyGroupsView.Joined` — `GroupRow` 칸을 펼친 것 + 칸, 가입이 최근인 순).
 * `newPostCount`: 내가 글 탭을 마지막으로 본 뒤(`POST /api/groups/{id}/seen`) 올라온 남의 글 수(2단계, 지운·숨긴 글 제외).
 * `nextEvent`: 그 소모임의 가장 가까운 시작 전(UPCOMING) 일정(4단계, 서버 `MyGroupsView.NextEvent`), 없으면 null.
 */
export interface MyGroupRow extends GroupRow {
    role: GroupRole;
    joinedAt: string;
    newPostCount: number;
    nextEvent: MyGroupNextEvent | null;
}

/** 내 소모임 줄의 다음 일정 — 취소·시작한 일정은 빠진다 */
export interface MyGroupNextEvent {
    id: string;
    title: string;
    startsAt: string;
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
    /** 신청한 공개 범위 — `null`(기능 꺼짐·비공개)이면 그리지 않는다 */
    visibility?: GroupVisibility | null;
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
    /** 공개 범위 — 개설 규칙의 `visibilityChoice` 가 true 일 때만 보낸다(생략 = 비공개, 기능이 꺼진 서버는 무시) */
    visibility?: GroupVisibility;
}

/** 개설 규칙으로 못 내는 까닭 */
export type GroupCreationBlockedReason = 'NOT_PERMITTED' | 'GRADE_TOO_LOW' | 'LIMIT_REACHED';

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
    /** 공개 범위를 고를 수 있나(공개 범위 기능) — true 일 때만 만들기 화면에 라디오가 있다 */
    visibilityChoice?: boolean;
    /**
     * 로그인했을 때만 — 개설 규칙만 본다(이메일 인증·정지는 신청할 때 403).
     * `reason`: 서버 `creationBlocked` 차례로 `NOT_PERMITTED`(회원 역할에 `group.create` 권한 없음 — 로보얌 추가) → `GRADE_TOO_LOW` → `LIMIT_REACHED`
     */
    me: {
        canCreate: boolean;
        reason: GroupCreationBlockedReason | null;
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

// ── 소모임 글(2단계 — community `docs/api-groups.md` 8절. 11절 공개 범위·공개 요청은 아래 5단계 절) ──

/** `GET /api/groups/{id}/posts?cursor=&size=` 의 조건 — `size` 1~50(기본 20) */
export interface GroupPostListQuery {
    cursor?: string;
    size?: number;
}

/**
 * 글 탭 한 쪽 — 최신이 위, 커서로 더보기. 멤버는 비공개 글까지, 비멤버·비회원은 공개로 전환된 글만. 지운 글·숨긴 글(나간 멤버의 글 포함)은 없다.
 * 줄은 게시판 글 줄(`PostRow`)에 `group`(`PostGroupRef`)이 붙은 모양이다.
 */
export interface GroupPostPage {
    items: PostRow[];
    hasNext: boolean;
    nextCursor: string | null;
    /** 내가 멤버가 아니라 비공개 글이 잠겨 있다 */
    locked: boolean;
    /** 잠긴 글 수(멤버는 0) — 「멤버에게만 보이는 글 N개」 줄 */
    privateCount: number;
}

/**
 * `POST /api/groups/{id}/posts` 본문 — 로보얌 글쓰기 본문(`PostWriteRequest`)에서 게시판(`boardSlug` — 소모임 게시판으로 정해진다)·비회원(`guest` — 회원만)·찬반 토론(`debate`)을 뺀 것.
 * 말머리는 소모임 게시판의 말머리. 임시저장·예약 발행은 없다. 글은 **비공개**로 만들어진다. `adult` 는 늘 false(`true` 면 서버 `400 fields.adult` — 소모임 글은 19금이 될 수 없다).
 */
export type GroupPostWriteRequest = Omit<PostWriteRequest, 'boardSlug' | 'guest' | 'debate'>;

/** `POST /api/groups/{id}/posts` 응답(201, 서버 `GroupPostWriteResultView`) — `pointsEarned` 는 이 글로 받은 포인트(지급이 없으면 0, 늘 숫자) */
export interface GroupPostWriteResult {
    id: string;
    pointsEarned: number;
}

/**
 * `PUT /api/groups/{id}/posts/{postId}/visibility` 본문 — 한 경로, 네 가지 일(11-6): 비공개 소모임 `PUBLIC` 직접 공개 · `PRIVATE` 되돌리기 /
 * 공개 소모임 `PRIVATE` 멤버 전용으로 돌리기 · `PUBLIC` 다시 공개. 공개하는 쪽은 `acknowledged: true`(개인정보 확인 동의)가 있어야 한다.
 */
export interface GroupPostVisibilityRequest {
    visibility: PostGroupVisibility;
    acknowledged?: boolean;
}

/** 글 공개 범위 바꾸기의 응답 — `publicBy`·`membersOnly` 는 5단계 칸(옛 서버는 없다) */
export interface GroupPostVisibilityResult {
    visibility: PostGroupVisibility;
    publicBy?: PostGroupPublicBy | null;
    membersOnly?: boolean;
}

// ── 공개 범위·공개 요청(5단계 — community `docs/api-groups.md` 11절) ──

/** `GET /api/groups/{id}/visibility-preview?to=` — 바꾸면 영향을 받는 글·댓글 수(모임장만, 지금 값과 같으면 모두 0) */
export interface GroupVisibilityPreview {
    to: GroupVisibility;
    /** `PUBLIC`: 비멤버에게 보이게 될 글(멤버 전용으로 돌린 글 제외) · `PRIVATE`: 지금 비멤버에게 보이는(닫히는) 글 */
    postCount: number;
    commentCount: number;
    /** 그중 지금 개별 공개(승인·직접 공개)된 글 수 */
    individuallyPublicCount: number;
    /** 멤버 전용으로 돌려 둔 채 남는 글 수(`postCount` 에 들어 있지 않다) */
    membersOnlyCount: number;
    /** 이 변경으로 닫히는 대기 요청 수(`PUBLIC` 일 때) */
    pendingRequestCount: number;
    /** 쿨다운이 끝나는 시각 — 없으면 null(`PRIVATE` 는 늘 null) */
    canChangeAt: string | null;
}

/** `PUT /api/groups/{id}/visibility` 본문 — 비공개→공개는 `acknowledged: true` 필수 */
export interface GroupVisibilityChangeRequest {
    visibility: GroupVisibility;
    acknowledged: boolean;
}

/** `PUT /api/groups/{id}/visibility` 응답 — `affected`: 바뀐 글·댓글·멤버 전용으로 남은 글 수 */
export interface GroupVisibilityChangeResult {
    visibility: GroupVisibility;
    changedAt: string | null;
    affected: { posts: number; comments: number; membersOnly: number };
}

/** `POST …/posts/{postId}/publish-request` 본문 — 한마디는 선택(한도 `publishTextMax`, 기본 100자) */
export interface GroupPublishRequestBody {
    note?: string;
    acknowledged: true;
}

/** 공개 요청 결과(201) */
export interface GroupPublishRequestResult {
    status: 'PENDING';
    requestedAt: string;
}

/** 승인 결과 — 늘 `{ visibility: PUBLIC, publicBy: APPROVED, membersOnly: false }` */
export interface GroupPublishDecideResult {
    visibility: PostGroupVisibility;
    publicBy: PostGroupPublicBy | null;
    membersOnly: boolean;
}

/** 거절 결과 — 같은 글의 거절 누적 수 */
export interface GroupPublishRejectResult {
    status: 'REJECTED';
    rejectCount: number;
}

/**
 * 공개 요청 한 줄(`GET …/publish-requests`, 서버 `GroupVisibilityViews.RequestRow`) — 오래된 요청이 위. `author` 는 소모임의 다른 사람 칸과 같은 `GroupUser`(서버 `DmPartnerView`, community 는 `AuthorView`).
 * `editedAfterRequest`: 요청한 뒤 글을 고쳤다 · `authorStillMember`: 강퇴된 작성자면 false(승인은 막지 않는다) · `rejectCount`: 이전 거절 수.
 */
export interface GroupPublishRequestRow {
    post: { id: string; title: string; createdAt: string; editedAt: string | null };
    author: GroupUser;
    note: string | null;
    requestedAt: string;
    editedAfterRequest: boolean;
    authorStillMember: boolean;
    rejectCount: number;
}

/** `GET /api/groups/{id}/publish-requests?status=PENDING&cursor=&size=` — `nextCursor` 는 불투명 문자열(그대로 다음 `cursor` 로 보낸다) */
export interface GroupPublishRequestPage {
    items: GroupPublishRequestRow[];
    hasNext: boolean;
    nextCursor: string | null;
    total: number;
    /** 지금 적용되는 거절 사유 글자 수 한도(관리자 설정 10~100) — 칸이 없으면 100 */
    publishTextMax?: number;
}

/** 공개 요청 목록의 조건 — `status` 는 늘 `PENDING`(API 컴포저블이 붙인다), `size` 1~50 */
export interface GroupPublishRequestQuery {
    cursor?: string;
    size?: number;
}

// ── 일정(4단계 — community `docs/api-groups.md` 9절, 서버 `GroupEvent*View`) ──

/** 진행 방식 — 온라인(모임 링크는 채팅 탭) · 오프라인(장소 필수, 개인 책임 고지) */
export type GroupEventMode = 'ONLINE' | 'OFFLINE';

/** 일정 상태 — 서버가 지금 시각으로 정한다. 시작 전(응답 가능) · 진행 중(응답 닫힘) · 끝남(끝 시각이 없으면 시작 2시간 뒤) · 취소 */
export type GroupEventState = 'UPCOMING' | 'ONGOING' | 'ENDED' | 'CANCELED';

/** 참석 응답 — 참석 · 불참 · 미정 */
export type GroupEventResponse = 'GOING' | 'NOT_GOING' | 'MAYBE';

/** 일정 범위 — 다가오는(시작 전·진행 중, 가까운 순) · 지난(끝남·취소, 최근 순) */
export type GroupEventScope = 'upcoming' | 'past';

/** 응답별 인원(서버 `GroupEventCountsView`) — 지금 멤버의 응답만 센다 */
export interface GroupEventCounts {
    going: number;
    notGoing: number;
    maybe: number;
}

/** 일정 목록 한 줄(서버 `GroupEventRowView`) — 상세의 머리와 같다. 시각은 ISO-8601 UTC */
export interface GroupEventRow {
    id: string;
    title: string;
    startsAt: string;
    endsAt: string | null;
    mode: GroupEventMode;
    /** 장소(온라인이면 null) */
    place: string | null;
    state: GroupEventState;
    /** 지금 멤버의 참석 응답 수 */
    goingCount: number;
    myResponse: GroupEventResponse | null;
}

/** 일정 상세(서버 `GroupEventDetailView`) = 목록 한 줄 + 칸 */
export interface GroupEventDetail extends GroupEventRow {
    description: string | null;
    counts: GroupEventCounts;
    /** 오프라인 일정이거나 오프라인 모임이면 true — 개인 책임 고지를 늘 보인다 */
    offlineNotice: boolean;
    /** 만든 사람(탈퇴했으면 `id: null`·`withdrawn: true`) */
    creator: GroupUser | null;
    /** 멤버이고 시작 전 */
    canRespond: boolean;
    /** 모임장 또는 `EVENTS` 매니저이고 시작 전·진행 중 — 수정·취소(정지·미인증은 소모임 상세의 `permissions.manageBlockedReason`) */
    canManage: boolean;
    cancelReason: string | null;
    canceledAt: string | null;
}

/** `GET /api/groups/{id}/events?scope=&page=&size=` 의 조건 — `page` 1부터, `size` 1~50(기본 20) */
export interface GroupEventListQuery {
    scope?: GroupEventScope;
    page?: number;
    size?: number;
}

/** 일정 목록 한 쪽(서버 `GroupEventPageView`) — `total` 은 그 범위의 전체 수 */
export interface GroupEventPage {
    items: GroupEventRow[];
    page: number;
    hasNext: boolean;
    total: number;
}

/**
 * 일정 만들기·수정 본문(서버 `GroupEventRequest`) — 수정도 모든 칸을 보낸다(비운 끝 시각·설명은 지워진다).
 * 제목 1~60자 · 설명 1000자 이하 · 장소 100자 이하(오프라인 필수, 온라인이면 무시). `offlineAck` 는 오프라인을 새로 만들 때(수정은 온라인 → 오프라인일 때만) true 필수.
 */
export interface GroupEventRequest {
    title: string;
    startsAt: string;
    endsAt: string | null;
    mode: GroupEventMode;
    place: string | null;
    description: string | null;
    offlineAck: boolean;
}

/** 참석 응답 바꾸기·지우기의 결과(서버 `GroupEventResponseResultView`) — 내 응답(지웠으면 null)과 바뀐 뒤 응답별 인원 */
export interface GroupEventResponseResult {
    myResponse: GroupEventResponse | null;
    counts: GroupEventCounts;
}

/** 참석자 한 줄 — 참석(GOING)으로 응답한 지금 멤버(닉네임·등급만 — 장식·아바타는 싣지 않는다) */
export interface GroupEventAttendee {
    user: GroupUser;
    respondedAt: string;
    leader: boolean;
}

/** `GET /api/groups/{id}/events/{eventId}/attendees`(서버 `GroupEventAttendeePageView`) — 응답이 최근인 순, `total` 은 참석 인원 */
export interface GroupEventAttendeePage {
    items: GroupEventAttendee[];
    page: number;
    hasNext: boolean;
    total: number;
}
