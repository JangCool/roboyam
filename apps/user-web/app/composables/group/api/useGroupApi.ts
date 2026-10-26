import type {
    GroupApplicationListResponse,
    GroupCategoriesResponse,
    GroupChatLinksRequest,
    GroupCreateRequest,
    GroupCreateResult,
    GroupCreationRules,
    GroupDetail,
    GroupJoinRequest,
    GroupJoinResult,
    GroupListQuery,
    GroupListResponse,
    GroupMemberListQuery,
    GroupMemberListResponse,
    GroupUpdateRequest,
    MyGroupsResponse,
} from '~/composables/group/api/useGroupApi.types';

/**
 * 소모임 API — 서버 주소 문자열(`/api/groups…`·`/api/me/groups…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 모두 처음부터 실제 호출이다(목업 없음 — `// MOCK:` 줄이 없다) — 경로·칸 이름·오류 코드는 community `docs/api-groups.md`(1·2·3·4·5(5-1 매니저 제외)·7절)와 같다고 가정한다.
 * 서버와 어긋나는 곳은 연동 단계에서 맞춘다.
 *
 * ## 가정한 모양 (원본과 같지 않은 곳 위주)
 * - 응답: 로보얌 `ApiResponse` 래퍼(`{success, data, meta}`) — `useApiClient` 가 벗겨 `data` 만 준다. `204` 는 `undefined`. 오류는 `ApiError`(`code`·`message`·`fields`·`retryAfterSeconds`·`extras`).
 * - 사람 칸(`leader`·`members[].user`·`applications[].user`·`newcomers.items`): community `AuthorView` 대신 로보얌 쪽지 상대와 같은 모양
 *   `{ id|null, nickname, grade, gradeLabel, role?, withdrawn, memo?, decoration?, avatarUrl? }`(`GroupUser`) — 화면은 이것을 글쓴이 표시(`PostAuthorLabel`)로 바꿔 그린다.
 * - `GET /api/groups/categories`: 로보얌 서버에 공개 분류 API 가 없으면 서버 에이전트가 이번에 만든다고 가정 — `{ total, items[{ id, name, groupCount, investmentNotice, boards[{ slug, name, investmentNotice }] }] }`.
 * - `GET /api/groups`: 「지금 뜨는 소모임」(6단계)이 없으니 `exclude` 를 보내지 않는다. `?category=` 는 분류 id, 없는 분류는 `400 fields.category`.
 * - `GET /api/groups/{id}`: 매니저 칸(`managerInfo`·체크형 `permissions.*`)·공개 범위(`visibility*`)·글쓰기용 `board.prefixes` 는 없어도 된다(3·5·2단계). 체크형 권한 칸이 없으면 화면은 `canManage` 로 채운다.
 *   `permissions.manageBlockedReason`(`EMAIL_NOT_VERIFIED`·`ACCOUNT_RESTRICTED`)이 오면 관리 화면 쓰기 단추를 끈다. `limits` 가 없으면 정원 5~200 으로 본다.
 *   `INACTIVE` 는 `{ id, name, status }` 만, `PENDING`·`REJECTED`·`DELETED`·없는 ID 는 `404 GROUP_NOT_FOUND`.
 * - `GET /api/groups/{id}/members`: `managerPermissions` 칸은 읽지 않는다(3단계). 비멤버 `403 GROUP_NOT_MEMBER`, 비회원 `401`.
 * - 모임장 동작(정보 수정·모집·채팅 링크·신청 처리·강퇴·넘기기·해산)의 권한 오류: 모임장이 아니면 `403 GROUP_LEADER_ONLY`(로보얌 관리자 쪽의 `PERMISSION_DENIED` 가 아니라 원본 코드로 가정).
 *   정지·이메일 미인증은 공통 `403 ACCOUNT_RESTRICTED`·`403 EMAIL_NOT_VERIFIED`, 사업자 계정은 `403 BUSINESS_ACCOUNT_RESTRICTED`.
 * - 가입·신청 오류: `409 GROUP_ALREADY_MEMBER|GROUP_ALREADY_APPLIED|GROUP_CLOSED|GROUP_FULL` · `429 GROUP_REAPPLY_LATER(retryAfterSeconds)` · `403 GROUP_INACTIVE` · `404 GROUP_NOT_FOUND` ·
 *   `400 VALIDATION fields.message|offlineAck` · `429 RATE_LIMITED`. 탈퇴: 모임장은 `409 GROUP_LEADER_CANNOT_LEAVE`.
 * - 개설 신청 오류: `403 GROUP_GRADE_TOO_LOW` · `409 GROUP_LIMIT_REACHED`(+최상위 `max`) · `409 GROUP_NAME_TAKEN` · `429 RATE_LIMITED`(하루 1번) ·
 *   `400 VALIDATION fields.name|description|categoryId|boardSlug|joinType|capacity|agreeRules|agreeStock|agreeOffline`.
 * - 신청 처리: `409 GROUP_FULL`(승인) · `404 GROUP_APPLICATION_NOT_FOUND`. 강퇴·넘기기: `404 GROUP_MEMBER_NOT_FOUND`. 해산: `400 VALIDATION fields.name`(이름이 다르다).
 * - 알림(7절)은 회원 알림(`useNotificationApi`)이 따로 받는다 — 이 파일은 알림을 부르지 않는다.
 */
const PATH = {
    groups: '/api/groups',
    categories: '/api/groups/categories',
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
    };
}
