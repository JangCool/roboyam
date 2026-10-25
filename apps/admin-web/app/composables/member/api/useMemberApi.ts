import type { MemberDetail, MemberPage, MemberQuery, MemberRoleOption } from '~/composables/member/api/useMemberApi.types';

/**
 * 회원 조회 API — 서버 주소 문자열(`/api/admin/members…`)이 있는 **유일한 곳**이다. 상태는 없다. 읽기만 있다(상세는 읽기 전용, 조치는 후속).
 *
 * 계약(서버 `MemberController`, `docs/features/roles-contract.md` 2단계): 오류는 모두 `ApiError`(useApiClient)로 던져진다. 세 API 모두 `member.read` 가 필요하다.
 *   - `GET /api/admin/members?searchField&keyword&statuses&roleIds&joinedFrom&joinedTo&lastLoginFrom&lastLoginTo&noLoginHistory&grades&memberType&emailVerified&authMethods&dormantFrom&dormantTo&statusChangedKind&statusChangedFrom&statusChangedTo&pointMin&pointMax&boardRole&hasSanction&sort&page&size`
 *     → 200 `{ items: MemberRow[], total, page(0부터), size }` (빈 조건은 보내지 않는다. 목록 값(`statuses`·`roleIds`·`grades`·`authMethods`)은 같은 이름을 반복해 보낸다 — 서버는 반복과 `a,b` 모두 받는다.
 *     `sort` 는 `칸,asc|desc`, 기본 `joinedAt,desc`. 기간은 UTC ISO 순간 — 시작은 사이트 시간대 00:00:00.000, 끝은 23:59:59.999. 쪽 크기 1~100)
 *     · 403 `PERMISSION_DENIED`(`member.read` 없음)
 *     · 400 `VALIDATION`(`fields` 키는 조회 조건 이름 그대로: 알 수 없는 값·`page`·`size`·`sort` 등) · 400 `MEMBER_PERIOD_REVERSED`(`fields` 에 시작 칸 하나: `joinedFrom`·`lastLoginFrom`·`dormantFrom`·`statusChangedFrom`) · 400 `MEMBER_POINT_RANGE_REVERSED`(`fields.pointMin`)
 *     · 상태 `DORMANT` 로만 거르면 서버가 결과를 비운다(휴면 상태가 아직 없다).
 *     · 이메일은 `member.pii` 가 없으면 서버가 `ab***@gmail.com` 으로 가려서 준다(`emailMasked: true`) — 화면은 가공하지 않고 그대로 그린다. 그 경우 이메일 검색은 전체 주소가 같을 때만 맞는다.
 *   - `GET /api/admin/members/{memberId}` → 200 `MemberDetail` · 404 `MEMBER_NOT_FOUND`(번호 형식이 틀려도 같다) · 403 `PERMISSION_DENIED`. 이메일 마스킹 규칙은 목록과 같다. 상세를 열면 서버가 감사 기록 `MEMBER_VIEW` 를 남긴다.
 *     **서버가 가진 데이터만 채우고 나머지는 null/빈 배열이다** — 목록 한 줄의 칸 전부, `roles`(유형·부여 방식), `bio`, `social`, `withdrawnAt`, `consents.terms`/`ageConfirmedAt`, `piiMasked` 가 실제 값이다.
 *     `activity`(글·댓글·최근 7일 수와 `likesReceived`(보이는 글·댓글이 받은 추천 합)는 실제 집계, `popularCount`·`recentLoginDays` 는 아직 집계하지 않아 늘 0)와 `gradePoint`(`autoPromotion`·`next`·`gradeHistory`·`pointEntries`(원장 최근 10줄) 는 실제 값, `nicknameTicketCount` 는 교환 기능이 없어 0)도 실제 값이다 — 탈퇴 회원은 둘 다 null.
 *     `reports`(`{receivedCount, actionedCount, filedCount}`)도 실제 값이다 — 탈퇴 회원은 null.
 *     비는 칸: `statusReason`·`sanctionSummary`·`adultConfirmed`·`acceptDm`·`dormancy`·`dormantAt`·`settings`·`contact`·`profile`·`groupBuy`·`consents.privacy`(null),
 *     `sanctions`·`consents.optional`·`consents.history`·`roles[].boardNames`(빈 배열), `nicknameHistory`(빈 배열, `member.pii` 없으면 null), `statusChangedAt`(탈퇴 시각만), `sanctionCount`(늘 0). `point` 는 포인트 잔액(탈퇴 0)이고 목록의 `pointMin`·`pointMax`·`sort=point` 도 잔액으로 거르고 줄 세운다.
 *     화면은 비는 구획을 안내(EmptyState)로 보이고 데이터가 오면 그린다. 탈퇴 회원은 역할·소셜·동의·소개가 비고 `withdrawnAt` 만 채워진다.
 *     · `identity`(커머스 0-4 — `{ verified, verifiedAt, adult, provider }`): 미인증도 `{ verified:false, verifiedAt:null, adult:null, provider:null }` 로 오고, null 은 탈퇴 회원뿐이다.
 *   - `GET /api/admin/members/role-options` → 200 `[{ roleId, roleName }]` (역할 고르개 — 등급 역할이 서열 순서로 먼저, 그다음 회원 유형·게시판 단위·사용자 정의)
 *
 * `authMethod`: 비밀번호가 있으면 `EMAIL`, 아니면 가장 먼저 연결한 소셜이다.
 *
 * ⚠ 미정(서버 정책): 휴대폰·전화·주소·공동구매 배송지 가림 규칙(`member.pii`), 배송지 열람 사유·열람 기록, 관리자 메모 편집 권한, 정렬 가능한 칸의 범위, 목록 열람의 감사 기록(지금은 상세만).
 */
const PATH = {
    root: '/api/admin/members',
    roleOptions: () => `${PATH.root}/role-options`,
    one: (memberId: string) => `${PATH.root}/${encodeURIComponent(memberId)}`,
};

export function useMemberApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 거른 목록 한 쪽 */
        search: (query: MemberQuery): Promise<MemberPage> => client.get<MemberPage>(PATH.root, { query: { ...query } }),

        /** 회원 한 명의 상세 — 상세 Drawer 가 연다 */
        getMember: (memberId: string): Promise<MemberDetail> => client.get<MemberDetail>(PATH.one(memberId)),

        /** 역할 고르개가 쓸 목록 */
        loadRoleOptions: (): Promise<MemberRoleOption[]> => client.get<MemberRoleOption[]>(PATH.roleOptions()),
    };
}
