import type {
    GroupApplicationListResponse,
    GroupCategoriesResponse,
    GroupChatLinksRequest,
    GroupCreateRequest,
    GroupCreateResult,
    GroupCreationRules,
    GroupDetail,
    GroupEventAttendeePage,
    GroupEventDetail,
    GroupEventListQuery,
    GroupEventPage,
    GroupEventRequest,
    GroupEventResponse,
    GroupEventResponseResult,
    GroupJoinRequest,
    GroupJoinResult,
    GroupListQuery,
    GroupListResponse,
    GroupManagerListResponse,
    GroupManagerPermission,
    GroupMemberListQuery,
    GroupMemberListResponse,
    GroupPostListQuery,
    GroupPostPage,
    GroupPostVisibilityRequest,
    GroupPostVisibilityResult,
    GroupPostWriteRequest,
    GroupPostWriteResult,
    GroupPublishDecideResult,
    GroupPublishRejectResult,
    GroupPublishRequestBody,
    GroupPublishRequestPage,
    GroupPublishRequestQuery,
    GroupPublishRequestResult,
    GroupTrendingResponse,
    GroupUpdateRequest,
    GroupVisibility,
    GroupVisibilityChangeRequest,
    GroupVisibilityChangeResult,
    GroupVisibilityPreview,
    MyGroupsResponse,
} from '~/composables/group/api/useGroupApi.types';
import type { BoardDetail } from '~/composables/board/api/useBoardApi.types';

/**
 * 소모임 API — 서버 주소 문자열(`/api/groups…`·`/api/me/groups…`)이 있는 **유일한 곳**이다. 상태는 없다. 모두 실제 호출이다(목업 없음 — `// MOCK:` 줄이 없다).
 *
 * ## 1단계 — 서버(`server/api/.../group/controller/GroupController`·`MyGroupController`)와 맞춘 모양
 * - 응답: 로보얌 `ApiResponse` 래퍼(`{success, data, meta}`) — `useApiClient` 가 벗겨 `data` 만 준다. `204` 는 `undefined`. 오류는 `ApiError`(`code`·`message`·`fields`·`retryAfterSeconds`·`extras`).
 * - 사람 칸(`leader`·`members[].user`·`applications[].user`·`newcomers.items`): 서버 `DmPartnerView`
 *   `{ id|null, nickname, grade, gradeLabel, role: "USER", withdrawn, memo: null, decoration, avatarUrl }`(`GroupUser`) — 화면은 이것을 글쓴이 표시(`PostAuthorLabel`)로 바꿔 그린다.
 * - 목록·멤버의 `page` 는 1부터. 게시판 칸은 `{ slug, name, nameEn }`. 상세의 `board` 는 `{ slug, name, nameEn, color, investmentNotice, prefixes[] }`, `limits` 는 정원·일정 한도 넷.
 * - `GET /api/groups/{id}`: `permissions.manageBlockedReason`(`EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`·`BUSINESS_ACCOUNT_RESTRICTED`)이 오면 관리 화면 쓰기 단추를 끈다.
 *   `INACTIVE` 는 `{ id, name, status }` 만, `PENDING`·`REJECTED`·`DELETED`·없는 ID 는 `404 GROUP_NOT_FOUND`. 공개 범위 칸(`visibility`)은 5단계부터, `managerInfo` 는 3단계부터 읽는다.
 * - 모임장 동작의 권한 오류: `403 GROUP_LEADER_ONLY`·`GROUP_MANAGER_PERMISSION_DENIED`. 쓰기 자격은 공통 `403 EMAIL_NOT_VERIFIED` → `ACCOUNT_RESTRICTED` → `BUSINESS_ACCOUNT_RESTRICTED`.
 * - 가입·신청 오류: `409 GROUP_ALREADY_MEMBER|GROUP_ALREADY_APPLIED|GROUP_CLOSED|GROUP_FULL` · `429 GROUP_REAPPLY_LATER(retryAfterSeconds)` · `403 GROUP_INACTIVE` · `404 GROUP_NOT_FOUND` ·
 *   `400 VALIDATION fields.message|offlineAck` · `429 RATE_LIMITED`. 탈퇴: 모임장은 `409 GROUP_LEADER_CANNOT_LEAVE`.
 * - 개설 신청 오류: `403 GROUP_CREATE_NOT_PERMITTED`(회원 역할에 `group.create` 없음 — 로보얌) · `403 GROUP_GRADE_TOO_LOW` · `409 GROUP_LIMIT_REACHED` · `409 GROUP_NAME_TAKEN` ·
 *   `429 RATE_LIMITED` · `400 VALIDATION fields.name|description|categoryId|boardSlug|joinType|capacity|agreeRules|agreeStock|agreeOffline`.
 * - 신청 처리: `409 GROUP_FULL`(승인) · `404 GROUP_APPLICATION_NOT_FOUND`. 강퇴·넘기기: `404 GROUP_MEMBER_NOT_FOUND`. 해산: `400 VALIDATION fields.name`(이름이 다르다).
 * - 알림(7절)은 회원 알림(`useNotificationApi`)이 따로 받는다(서버 묶음 `system`) — 이 파일은 알림을 부르지 않는다.
 *
 * ## 2단계(소모임 글) — 서버(`GroupPostController`·`GroupPostService`)와 맞춘 모양: community `docs/api-groups.md` 8절과 같다(11절 공개 범위·공개 요청은 아래 5단계)
 * - `GET /api/groups/{id}/posts?cursor=&size=` → `GroupPostPage { items: PostRow(+group)[], hasNext, nextCursor, locked, privateCount }`. 멤버는 비공개 글까지, 비멤버·비회원은 공개 글만.
 *   열려 있지 않으면 `404 GROUP_NOT_FOUND`·`403 GROUP_INACTIVE`, 잘못된 커서 `400 VALIDATION fields.cursor`.
 * - `POST /api/groups/{id}/posts` 본문 `GroupPostWriteRequest { prefixId, title, content, contentFormat, adult, poll? }`(로보얌 글쓰기 본문에서 `boardSlug`·`guest`·`debate` 를 뺀 것) →
 *   `201 { id, pointsEarned }`(비공개 글, `pointsEarned` 는 늘 숫자 — 지급이 없으면 0). 오류: `403 GROUP_NOT_MEMBER` · `404 GROUP_NOT_FOUND` · `403 GROUP_INACTIVE` · 칸별 `400 VALIDATION`
 *   (`adult: true` 는 `fields.adult`, `debate` 는 `fields.debate`) · 쓰기 자격 `403 EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED|BUSINESS_ACCOUNT_RESTRICTED|BOARD_RESTRICTED` · 일반 글과 같은 작성 간격·하루 한도(`429`)·금지어.
 * - `PUT /api/groups/{id}/posts/{postId}/visibility` 본문 `{ visibility: PUBLIC|PRIVATE, acknowledged? }` → `200 { visibility }`.
 *   `PUBLIC`: 작성자만(`403 FORBIDDEN`)·지금도 멤버(`403 GROUP_NOT_MEMBER`)·`acknowledged: true` 필수(`400 VALIDATION fields.acknowledged`).
 *   `PRIVATE`: 작성자·모임장·`UNPUBLISH` 매니저(`403 GROUP_MANAGER_PERMISSION_DENIED`·`FORBIDDEN`), 남이 되돌리면 작성자에게 알림 `GROUP_POST_UNPUBLISHED`. 없는 글 `404 POST_NOT_FOUND`.
 * - `POST /api/groups/{id}/seen` → `204` — 내 마지막으로 본 시각을 지금으로(멤버가 아니면 아무 일도 없다, 비회원 `401`). 내 소모임의 `newPostCount` 가 이 시각 뒤의 남의 글 수다.
 * - 글 상세(`GET /api/posts/{id}`)의 `group`(`PostGroupInfo`)·글 줄의 `group`(`PostGroupRef`)은 `usePostApi.types.ts`. 비멤버가 비공개 글을 열면 `403 GROUP_POST_PRIVATE`.
 * - **로보얌 추가 `GET /api/groups/{id}/board` → `BoardDetail`**(`GET /api/boards/{slug}` 와 같은 모양, 보는 사람 판정은 소모임 기준) — 소모임 게시판은 공개 게시판 API 에서
 *   없는 게시판(`404 BOARD_NOT_FOUND`)이라, 소모임 글쓰기·글 상세(댓글)·글 고치기가 에디터·이미지·투표·댓글 설정을 여기서 받는다. 열린 소모임과 해산한 소모임(공개로 남은 글)은 누구나 읽는다.
 *   `viewer.writeBlockedReason`: 비회원 `LOGIN_REQUIRED` → 해산 `GROUP_DISSOLVED` → 비멤버 `GROUP_NOT_MEMBER` → 쓰기 자격. `viewer.commentBlockedReason` 은 계정 자격만(해산한 소모임의 공개 글도 댓글이 열려 있다).
 *   비활성 `403 GROUP_INACTIVE`, 신청 중·반려·없음 `404 GROUP_NOT_FOUND`.
 *
 * ## 3단계(매니저) — 서버(`GroupManagerController`·`GroupManagerService`)와 맞춘 모양: community `docs/api-groups.md` 5-1 과 같은 경로·칸
 * - 판정 차례: 401 → 소모임 404·`403 GROUP_INACTIVE` → 역할 `403 GROUP_LEADER_ONLY` → (임명·맡은 일 바꾸기만) `403 GROUP_MANAGERS_DISABLED` →
 *   쓰기 자격 `403 EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED|BUSINESS_ACCOUNT_RESTRICTED` → 입력 400 → 대상·상태.
 * - `PUT /api/groups/{id}/managers/{userId}` 본문 `{ permissions: [...] }`(1~5개, 고정 키) → `204` — 임명·맡은 일 바꾸기(같은 API, 모임장만). 같은 집합이면 기록·알림 없이 `204`.
 *   오류: `400 VALIDATION fields.permissions` · `404 GROUP_MEMBER_NOT_FOUND`(지금 멤버가 아님) · `409 GROUP_LEADER_CANNOT_LEAVE`(대상이 모임장) ·
 *   `409 GROUP_MANAGER_LIMIT_REACHED`(새 임명만, 최상위 `limit` → `ApiError.extras.limit`) · `403 GROUP_MANAGERS_DISABLED`.
 * - `DELETE /api/groups/{id}/managers/{userId}` → `204` 해임(모임장만, 이미 매니저가 아니면 멱등). 매니저 기능이 꺼져 있어도 된다.
 * - `DELETE /api/groups/{id}/managers/me` → `204` 사퇴(매니저 본인, 아니면 멱등). 정지·이메일 미인증은 막히고 **사업자 회원은 막지 않는다**(탈퇴와 같은 규칙). 모임장에게 알림 `GROUP_MANAGER_RESIGNED`.
 * - `PUT /api/groups/{id}/manager-limit` 본문 `{ limit }` → `204`(모임장만) — 1~`limitMax`, 지금 매니저 수 이상. 어기면 `400 fields.limit`(서버 문구 `group.manager.limit.range|overMax|belowCount`).
 * - `GET /api/groups/{id}/managers` → `{ items: [{ user, appointedAt, permissions|null }], count, limit, limitMax }`(멤버만 — 비멤버 `403 GROUP_NOT_MEMBER`). 임명이 이른 순.
 * - 체크형 일에 그 체크가 없는 매니저는 `403 GROUP_MANAGER_PERMISSION_DENIED`, 모임장·매니저가 아니면 `403 GROUP_LEADER_ONLY`. `KICK` 매니저가 모임장·매니저·자신을 강퇴하면 `403 GROUP_MANAGER_PROTECTED`.
 * - 알림 5종(`GROUP_MANAGER_APPOINTED`·`…_PERMISSIONS_CHANGED`·`…_REMOVED`·`…_RESIGNED`·`GROUP_KICKED_BY_MANAGER`)은 회원 알림(`useNotificationApi`)이 받는다.
 *
 * ## 4단계(일정) — 서버(`GroupEventController`·`GroupEventService`·`GroupEventRules`)와 맞춘 모양: community `docs/api-groups.md` 9절과 같은 경로·칸
 * - 모두 로그인 필요(비회원 `401`)·**멤버만**(비멤버 `403 GROUP_NOT_MEMBER`). 열려 있지 않은 소모임 `404 GROUP_NOT_FOUND`·`403 GROUP_INACTIVE`. 응답 `Cache-Control: no-store`.
 * - `GET /api/groups/{id}/events?scope=upcoming|past&page=&size=` → `GroupEventPage { items: GroupEventRow[], page(1부터), hasNext, total }`. `upcoming`(기본) 시작 전·진행 중을 가까운 순,
 *   `past` 끝남·취소를 최근 순. `size` 1~50(기본 20). 잘못된 범위 `400 fields.scope`.
 * - `POST /api/groups/{id}/events` 본문 `GroupEventRequest` → `201 GroupEventDetail`. 모임장·`EVENTS` 매니저(일반 멤버 `403 GROUP_LEADER_ONLY`, 체크가 없는 매니저 `403 GROUP_MANAGER_PERMISSION_DENIED`),
 *   쓰기 자격 `403 EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED|BUSINESS_ACCOUNT_RESTRICTED`. 칸 오류는 모아서 `400 VALIDATION fields.title|startsAt|endsAt|mode|place|description|offlineAck`
 *   (`startsAt` 은 지금 뒤 · 지금부터 `limits.eventMaxDaysAhead` 일 안, `endsAt` 은 `startsAt` 뒤 · `limits.eventMaxLengthDays` 일 안). 열린 일정 한도 `409 GROUP_EVENT_LIMIT_REACHED`(최상위 `limit` → `ApiError.extras.limit`).
 *   멤버 전원(만든 사람 제외)에게 알림 `GROUP_EVENT`(`CREATED`).
 * - `GET /api/groups/{id}/events/{eventId}` → `GroupEventDetail`(없는·다른 소모임의 일정 `404 GROUP_EVENT_NOT_FOUND`).
 * - `PUT /api/groups/{id}/events/{eventId}` 만들기와 같은 본문(모든 칸) → `200 GroupEventDetail`. 끝난·취소된 일정 `409 GROUP_EVENT_ENDED|GROUP_EVENT_CANCELED`,
 *   진행 중 일정의 시작 시각 변경 `400 fields.startsAt`, 진행 중 일정의 끝 시각을 지금 이전으로 `400 fields.endsAt`. `offlineAck` 는 온라인 → 오프라인일 때만. 바뀐 것이 있을 때만 알림(`UPDATED`).
 * - `POST /api/groups/{id}/events/{eventId}/cancel` 본문 `{ reason? }`(100자 이하 — `400 fields.reason`) → `204`. 이미 취소됐으면 멱등 `204`, 끝났으면 `409 GROUP_EVENT_ENDED`. 알림(`CANCELED`, 사유는 적었을 때만).
 * - `PUT /api/groups/{id}/events/{eventId}/response` 본문 `{ response }` → `{ myResponse, counts }`, `DELETE …/response` → `{ myResponse: null, counts }`(멱등).
 *   시작했거나 끝남 `409 GROUP_EVENT_CLOSED`, 취소됨 `409 GROUP_EVENT_CANCELED`.
 * - `GET /api/groups/{id}/events/{eventId}/attendees?page=&size=` → `{ items: [{ user, respondedAt, leader }], page, hasNext, total }` — 참석(GOING)한 지금 멤버, 응답이 최근인 순.
 * - 내 소모임(`GET /api/me/groups`)의 `joined[].nextEvent = { id, title, startsAt } | null`. 소모임 상세에는 다음 일정 칸이 없다.
 * - 알림 `GROUP_EVENT`(회원이 끌 수 있는 유일한 소모임 알림, 덧붙임 `eventAction` CREATED|UPDATED|CANCELED, `link` `/groups/{groupId}/events/{eventId}`)는 회원 알림이 받는다.
 *
 * ## 5단계(공개 범위·공개 요청) — community `docs/api-groups.md` 11절과 같은 경로·칸. 서버 5단계 코드(`GroupVisibilityController`·`GroupVisibilityViews`·`GroupVisibilityRequests`·
 *   `GroupPostVisibilityView`·`GroupDetailView`·`PostGroupView`·`CommentListResponse`·`CommentService` 의 `hiddenBy MEMBERS_ONLY`, 2026-10-26 작성 중이던 미커밋 코드)와 경로·칸 이름을 대조했다
 * - 읽기 칸: 목록·상세·내 소모임·개설 신청의 `visibility`(`PUBLIC`·`PRIVATE`·**기능이 꺼져 있고 비공개면 `null`** — 화면은 칩·구획을 그리지 않는다),
 *   상세 `permissions.canChangeVisibility`·`canDecidePublishRequests`, `pendingPublishRequestCount`(처리자만, 아니면 null), `visibilityInfo`(모임장만),
 *   개설 규칙 `visibilityChoice`, 글 상세 `group`(`PostGroupInfo` 11-7 칸 — `usePostApi.types.ts`), 댓글 목록 `membersOnlyCount`·가린 댓글 `hiddenBy: MEMBERS_ONLY`(로보얌 자리 — `useCommentApi.types.ts`).
 * - `POST /api/groups` 본문 `visibility?`(생략 = 비공개, 기능이 꺼진 서버는 무시, 두 값이 아니면 `400 fields.visibility`).
 * - `GET /api/groups/{id}/visibility-preview?to=PUBLIC|PRIVATE` → `GroupVisibilityPreview`(모임장만 — `403 GROUP_LEADER_ONLY`).
 * - `PUT /api/groups/{id}/visibility` 본문 `{ visibility, acknowledged }` → `GroupVisibilityChangeResult`. 비공개→공개는 `acknowledged: true` 필수(`400 fields.acknowledged`)·
 *   마지막 변경 7일 뒤부터(`409 GROUP_VISIBILITY_COOLDOWN` + `retryAfterSeconds`)·기능이 꺼져 있으면 `403 GROUP_VISIBILITY_DISABLED`. 공개→비공개는 언제든. 멤버 전원에게 알림 `GROUP_VISIBILITY_CHANGED`.
 * - `POST …/posts/{postId}/publish-request` 본문 `{ note?, acknowledged: true }` → `201 { status: PENDING, requestedAt }`(작성자). `DELETE …/publish-request` → `204`(멱등).
 *   오류: `409 GROUP_PUBLISH_REQUEST_NOT_NEEDED`(작성자가 처리자 — 직접 공개) · `GROUP_POST_ALREADY_PUBLIC` · `GROUP_POST_MEMBERS_ONLY` · `GROUP_PUBLISH_REQUEST_EXISTS` · `GROUP_PUBLISH_REQUEST_LIMIT` ·
 *   `429 GROUP_PUBLISH_REQUEST_RETRY_LATER`(`retryAfterSeconds`) · `429 RATE_LIMITED` · `400 fields.note|acknowledged` · `403 GROUP_NOT_MEMBER|FORBIDDEN` · `403 GROUP_VISIBILITY_DISABLED`.
 * - `GET /api/groups/{id}/publish-requests?status=PENDING&cursor=&size=` → `GroupPublishRequestPage`(모임장·`UNPUBLISH` 매니저 — 정지·미인증 중에도 열람. 오래된 요청이 위, 불투명 커서).
 * - `POST …/posts/{postId}/publish-request/approve` 본문 `{ acknowledged: true }` → `{ visibility: PUBLIC, publicBy: APPROVED, membersOnly: false }` ·
 *   `POST …/reject` 본문 `{ reason? }`(한도 `publishTextMax`) → `{ status: REJECTED, rejectCount }`. 오류: `404 GROUP_PUBLISH_REQUEST_NOT_FOUND`(취소됨) · `409 GROUP_PUBLISH_REQUEST_DECIDED` ·
 *   `404 POST_NOT_FOUND` · `403 GROUP_MANAGER_PERMISSION_DENIED|GROUP_LEADER_ONLY` · 쓰기 자격 `403 EMAIL_NOT_VERIFIED|ACCOUNT_RESTRICTED|BUSINESS_ACCOUNT_RESTRICTED`.
 * - `PUT …/posts/{postId}/visibility` 은 한 경로 네 가지 일(11-6 — 직접 공개·되돌리기·멤버 전용으로 돌리기·다시 공개) → `{ visibility, publicBy, membersOnly }`.
 *   작성자(일반 멤버)가 비공개 소모임에서 `PUBLIC` 이면 `409 GROUP_PUBLISH_REQUEST_REQUIRED`, 공개 소모임에서 `PRIVATE` 이면 `409 GROUP_POST_PUBLIC_BY_GROUP`.
 * - 알림 7종(`GROUP_PUBLISH_REQUESTED`·`…_APPROVED`·`…_REJECTED`·`GROUP_POST_PUBLISHED`·`GROUP_VISIBILITY_CHANGED`·`GROUP_POST_MEMBERS_ONLY`·`GROUP_POST_RELEASED`)은 회원 알림이 받는다
 *   (문장·`link` 는 서버가 준다 — 로보얌 회원 알림 관례).
 *
 * ## 6단계(홈·프로필·지금 뜨는 소모임) — community `docs/api-groups.md` 2절 trending·10절과 같은 경로·칸이라고 **가정**했다(서버 6단계 코드는 작성 중 — 생기면 대조한다)
 * - `GET /api/groups/trending` → `{ items: GroupRow[] }`(로그인 불필요, 최대 `trendingSize`개 — 관리자 설정, 기본 5). 모집 중·정원 남음·최근 `trendingActiveDays`일 안에 활동이 있는 소모임에서
 *   활동·규모로 가중한 무작위 — 요청마다 달라질 수 있어 화면은 한 번 받아 고정한다. 후보가 없으면 빈 배열(화면은 슬라이드 자리를 통째로 생략).
 * - `GET /api/groups?…&exclude=1,2,3` — 쉼표로 이은 소모임 ID 최대 20개(관리자 trendingSize 최대와 같다)를 뺀 집합 위에서 쪽을 나눈다(서버 `GroupQueryService.list` 에 이미 있다 — 틀리면 `400 fields.exclude`).
 *   화면은 「지금 뜨는 소모임」의 ID 를 **모든 쪽에 같은 값으로** 보낸다(첫 쪽에만 보내면 2쪽부터 겹치거나 건너뛴다).
 * - 회원 프로필의 「소모임」 탭 `GET /api/users/{id}/groups` 는 경로의 주인이 회원 프로필이라 `useUserProfileApi` 에 있다.
 */
const PATH = {
    groups: '/api/groups',
    categories: '/api/groups/categories',
    trending: '/api/groups/trending',
    creationRules: '/api/groups/creation-rules',
    group: (id: string) => `/api/groups/${encodeURIComponent(id)}`,
    members: (id: string) => `${PATH.group(id)}/members`,
    member: (id: string, userId: string) => `${PATH.group(id)}/members/${encodeURIComponent(userId)}`,
    join: (id: string) => `${PATH.group(id)}/join`,
    membership: (id: string) => `${PATH.group(id)}/membership`,
    recruiting: (id: string) => `${PATH.group(id)}/recruiting`,
    chatLinks: (id: string) => `${PATH.group(id)}/chat-links`,
    applications: (id: string) => `${PATH.group(id)}/applications`,
    application: (id: string, userId: string) => `${PATH.group(id)}/applications/${encodeURIComponent(userId)}`,
    leader: (id: string) => `${PATH.group(id)}/leader`,
    dissolve: (id: string) => `${PATH.group(id)}/dissolve`,
    posts: (id: string) => `${PATH.group(id)}/posts`,
    postVisibility: (id: string, postId: string) => `${PATH.group(id)}/posts/${encodeURIComponent(postId)}/visibility`,
    seen: (id: string) => `${PATH.group(id)}/seen`,
    board: (id: string) => `${PATH.group(id)}/board`,
    managers: (id: string) => `${PATH.group(id)}/managers`,
    manager: (id: string, userId: string) => `${PATH.group(id)}/managers/${encodeURIComponent(userId)}`,
    managerMe: (id: string) => `${PATH.group(id)}/managers/me`,
    managerLimit: (id: string) => `${PATH.group(id)}/manager-limit`,
    visibility: (id: string) => `${PATH.group(id)}/visibility`,
    visibilityPreview: (id: string) => `${PATH.group(id)}/visibility-preview`,
    publishRequests: (id: string) => `${PATH.group(id)}/publish-requests`,
    publishRequest: (id: string, postId: string) => `${PATH.group(id)}/posts/${encodeURIComponent(postId)}/publish-request`,
    events: (id: string) => `${PATH.group(id)}/events`,
    event: (id: string, eventId: string) => `${PATH.group(id)}/events/${encodeURIComponent(eventId)}`,
    mine: '/api/me/groups',
    myRequest: (id: string) => `/api/me/groups/requests/${encodeURIComponent(id)}`,
};

export function useGroupApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        // ── 읽기 ──

        /** 목록·검색 — 열린(ACTIVE) 소모임만. `sort=mine` 은 로그인 필요(비회원 401) */
        list: (query: GroupListQuery = {}, signal?: AbortSignal) => client.get<GroupListResponse>(PATH.groups, { query: { ...query }, signal }),

        /** 소모임 분류 — 목록 칩과 만들기 화면의 분류 고르기가 같이 쓴다(로그인 불필요) */
        categories: (signal?: AbortSignal) => client.get<GroupCategoriesResponse>(PATH.categories, { signal }),

        /** 지금 뜨는 소모임 — 가중 무작위라 요청마다 다를 수 있다(화면은 한 번 받아 고정). 로그인 불필요 */
        trending: (signal?: AbortSignal) => client.get<GroupTrendingResponse>(PATH.trending, { signal }),

        /** 상세 — 보는 사람에 따라 `myState`·`join`·`chat`·`permissions` 가 달라진다 */
        detail: (id: string, signal?: AbortSignal) => client.get<GroupDetail>(PATH.group(id), { signal }),

        /** 멤버 탭 — 멤버만(비멤버 `403 GROUP_NOT_MEMBER`) */
        members: (id: string, query: GroupMemberListQuery = {}, signal?: AbortSignal) =>
            client.get<GroupMemberListResponse>(PATH.members(id), { query: { ...query }, signal }),

        /** 내 소모임 — 가입한 모임·승인 대기·내 개설 신청(로그인 필요) */
        mine: (signal?: AbortSignal) => client.get<MyGroupsResponse>(PATH.mine, { signal }),

        /** 개설 규칙 안내 — 로그인하면 `me` 가 붙는다 */
        creationRules: (signal?: AbortSignal) => client.get<GroupCreationRules>(PATH.creationRules, { signal }),

        // ── 개설 신청 ──

        /** 개설 신청 — 승인제는 `202 PENDING`, 자유 개설·운영자는 `201 ACTIVE` */
        create: (body: GroupCreateRequest) => client.post<GroupCreateResult>(PATH.groups, body),

        /** 내 승인 대기 개설 신청 취소(204). 이미 처리됐으면 `404 GROUP_NOT_FOUND` */
        cancelRequest: (id: string) => client.del<void>(PATH.myRequest(id)),

        // ── 가입·신청·탈퇴 ──

        /** 가입·신청 — 자유 가입은 `JOINED`, 신청제·강퇴됐던 회원은 `APPLIED` */
        join: (id: string, body: GroupJoinRequest = {}) => client.post<GroupJoinResult>(PATH.join(id), body),

        /** 내 승인 대기 신청을 거둔다(204, 멱등) */
        cancelApplication: (id: string) => client.del<void>(PATH.join(id)),

        /** 탈퇴(204, 멱등). 모임장은 `409 GROUP_LEADER_CANNOT_LEAVE` */
        leave: (id: string) => client.del<void>(PATH.membership(id)),

        // ── 모임장 ──

        /** 정보 수정 — 모든 칸. 응답은 바뀐 상세. 정원은 지금 멤버 수보다 작게 줄일 수 없다(`fields.capacity`) */
        update: (id: string, body: GroupUpdateRequest) => client.put<GroupDetail>(PATH.group(id), body),

        /** 모집 켜기·끄기(204) */
        setRecruiting: (id: string, recruiting: boolean) => client.put<void>(PATH.recruiting(id), { recruiting }),

        /** 채팅 링크 저장(204) — 카카오 오픈채팅·디스코드 초대 주소만(`fields.kakao`·`fields.discord`). null 인 링크는 지워진다 */
        setChatLinks: (id: string, body: GroupChatLinksRequest) => client.put<void>(PATH.chatLinks(id), body),

        /** 가입 신청 목록(오래된 신청이 위) */
        applications: (id: string, signal?: AbortSignal) => client.get<GroupApplicationListResponse>(PATH.applications(id), { signal }),

        /** 신청 승인 — 정원을 확인한다(`409 GROUP_FULL`) */
        approve: (id: string, userId: string) => client.post<{ memberCount: number }>(`${PATH.application(id, userId)}/approve`),

        /** 신청 거절(204) — 사유는 선택(100자), 적었을 때만 신청자에게 전달된다 */
        reject: (id: string, userId: string, reason?: string) => client.post<void>(`${PATH.application(id, userId)}/reject`, reason ? { reason } : {}),

        /** 강퇴(204). 모임장은 강퇴할 수 없다(`409 GROUP_LEADER_CANNOT_LEAVE`) */
        kick: (id: string, userId: string) => client.del<void>(PATH.member(id, userId)),

        /** 모임장 넘기기(204) — 대상은 내가 아닌 멤버 */
        transferLeader: (id: string, userId: string) => client.post<void>(PATH.leader(id), { userId }),

        /** 해산(204) — 소모임 이름을 그대로 입력해야 한다(`fields.name`) */
        dissolve: (id: string, name: string) => client.post<void>(PATH.dissolve(id), { name }),

        // ── 소모임 글(2단계) ──

        /** 글 탭 — 최신이 위, 커서로 더보기. 비멤버는 공개 글만 + 잠긴 글 수 */
        posts: (id: string, query: GroupPostListQuery = {}, signal?: AbortSignal) => client.get<GroupPostPage>(PATH.posts(id), { query: { ...query }, signal }),

        /** 소모임 글쓰기 — 멤버만, 비공개 글로 만들어진다 */
        writePost: (id: string, body: GroupPostWriteRequest) => client.post<GroupPostWriteResult>(PATH.posts(id), body),

        /** 글 공개 범위 — 한 경로 네 가지 일(직접 공개·되돌리기·멤버 전용으로 돌리기·다시 공개, 기능이 꺼진 서버는 옛 작성자 전환). 공개하는 쪽은 `acknowledged` 필수 */
        setPostVisibility:(id: string, postId: string, body: GroupPostVisibilityRequest) => client.put<GroupPostVisibilityResult>(PATH.postVisibility(id, postId), body),

        /** 글 탭을 봤다(204) — 내 소모임의 새 글 수가 이 시각 뒤의 남의 글을 센다 */
        markSeen: (id: string) => client.post<void>(PATH.seen(id)),

        /** 소모임 게시판 정보(`BoardDetail` 모양, 로보얌 추가 후보) — 소모임 글쓰기·글 상세·글 고치기의 에디터·말머리·댓글 설정 */
        board: (id: string, signal?: AbortSignal) => client.get<BoardDetail>(PATH.board(id), { signal }),

        // ── 매니저(3단계) ──

        /** 매니저 목록 — 멤버만. 맡은 일은 모임장·그 매니저 본인에게만 온다 */
        managers: (id: string, signal?: AbortSignal) => client.get<GroupManagerListResponse>(PATH.managers(id), { signal }),

        /** 임명·맡은 일 바꾸기(204) — 같은 API, 모임장만 */
        setManager: (id: string, userId: string, permissions: GroupManagerPermission[]) => client.put<void>(PATH.manager(id, userId), { permissions }),

        /** 해임(204, 멱등) — 모임장만 */
        removeManager: (id: string, userId: string) => client.del<void>(PATH.manager(id, userId)),

        /** 매니저에서 물러나기(204, 멱등) — 매니저 본인 */
        resignManager: (id: string) => client.del<void>(PATH.managerMe(id)),

        /** 매니저 인원 설정(204) — 모임장만 */
        setManagerLimit: (id: string, limit: number) => client.put<void>(PATH.managerLimit(id), { limit }),

        // ── 공개 범위·공개 요청(5단계) ──

        /** 공개 범위를 바꾸면 영향을 받는 글·댓글 수 — 모임장만 */
        visibilityPreview: (id: string, to: GroupVisibility, signal?: AbortSignal) =>
            client.get<GroupVisibilityPreview>(PATH.visibilityPreview(id), { query: { to }, signal }),

        /** 공개 범위 바꾸기 — 모임장만, 비공개→공개는 `acknowledged` 필수·쿨다운 */
        setVisibility: (id: string, body: GroupVisibilityChangeRequest) => client.put<GroupVisibilityChangeResult>(PATH.visibility(id), body),

        /** 공개 요청(201) — 작성자, 개인정보 확인 동의 필수 */
        requestPublish: (id: string, postId: string, body: GroupPublishRequestBody) => client.post<GroupPublishRequestResult>(PATH.publishRequest(id, postId), body),

        /** 공개 요청 취소(204, 멱등) — 작성자 */
        cancelPublishRequest: (id: string, postId: string) => client.del<void>(PATH.publishRequest(id, postId)),

        /** 처리 대기 공개 요청 — 모임장·`UNPUBLISH` 매니저, 오래된 요청이 위, 불투명 커서 */
        publishRequests: (id: string, query: GroupPublishRequestQuery = {}, signal?: AbortSignal) =>
            client.get<GroupPublishRequestPage>(PATH.publishRequests(id), { query: { status: 'PENDING', ...query }, signal }),

        /** 공개 요청 승인 — 개인정보 확인 동의 필수 */
        approvePublishRequest: (id: string, postId: string) => client.post<GroupPublishDecideResult>(`${PATH.publishRequest(id, postId)}/approve`, { acknowledged: true }),

        /** 공개 요청 거절 — 사유는 선택(적었을 때만 작성자에게 전달된다) */
        rejectPublishRequest: (id: string, postId: string, reason?: string) =>
            client.post<GroupPublishRejectResult>(`${PATH.publishRequest(id, postId)}/reject`, reason ? { reason } : {}),

        // ── 일정(4단계) ──

        /** 일정 목록 — 멤버만. `upcoming`(기본) 가까운 순 · `past` 최근 순, 쪽 번호 1부터 */
        events: (id: string, query: GroupEventListQuery = {}, signal?: AbortSignal) => client.get<GroupEventPage>(PATH.events(id), { query: { ...query }, signal }),

        /** 일정 상세 — 멤버만 */
        event: (id: string, eventId: string, signal?: AbortSignal) => client.get<GroupEventDetail>(PATH.event(id, eventId), { signal }),

        /** 일정 만들기(201) — 모임장·`EVENTS` 매니저 */
        createEvent: (id: string, body: GroupEventRequest) => client.post<GroupEventDetail>(PATH.events(id), body),

        /** 일정 수정 — 모든 칸을 보낸다. 끝나지 않은 일정만 */
        updateEvent: (id: string, eventId: string, body: GroupEventRequest) => client.put<GroupEventDetail>(PATH.event(id, eventId), body),

        /** 일정 취소(204, 멱등) — 사유는 선택(100자) */
        cancelEvent: (id: string, eventId: string, reason?: string) => client.post<void>(`${PATH.event(id, eventId)}/cancel`, reason ? { reason } : {}),

        /** 참석 응답 — 시작 전까지만 */
        respondEvent: (id: string, eventId: string, response: GroupEventResponse) => client.put<GroupEventResponseResult>(`${PATH.event(id, eventId)}/response`, { response }),

        /** 참석 응답 지우기(멱등) */
        clearEventResponse: (id: string, eventId: string) => client.del<GroupEventResponseResult>(`${PATH.event(id, eventId)}/response`),

        /** 참석자 — 참석으로 응답한 지금 멤버, 최근 순 */
        eventAttendees: (id: string, eventId: string, query: { page?: number; size?: number } = {}, signal?: AbortSignal) =>
            client.get<GroupEventAttendeePage>(`${PATH.event(id, eventId)}/attendees`, { query: { ...query }, signal }),
    };
}
