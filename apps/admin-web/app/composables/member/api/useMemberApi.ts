import { mockMemberGet, mockMemberRoleOptions, mockMemberSearch } from '~/composables/member/api/mock/member-mock';
import type { MemberDetail, MemberPage, MemberQuery, MemberRoleOption } from '~/composables/member/api/useMemberApi.types';

/**
 * 회원 조회 API — 서버 주소 문자열(`/api/admin/members…`)이 있는 **유일한 곳**이다. 상태는 없다. 읽기만 있다(상세는 읽기 전용, 조치는 후속).
 *
 * 후보 계약(확정 전): 오류는 모두 `ApiError`(useApiClient)로 던져진다.
 *   - `GET /api/admin/members?searchField&keyword&statuses[]&roleIds[]&joinedFrom&joinedTo&lastLoginFrom&lastLoginTo&noLoginHistory&grades[]&memberType&emailVerified&authMethods[]&dormantFrom&dormantTo&statusChangedKind&statusChangedFrom&statusChangedTo&pointMin&pointMax&boardRole&hasSanction&sort&page&size`
 *     → 200 `{ items: MemberRow[], total, page(0부터), size }` (빈 조건은 보내지 않는다. `sort` 는 `칸,asc|desc`, 기본 `joinedAt,desc`. 기간은 UTC ISO 순간 — 시작은 사이트 시간대 00:00:00.000, 끝은 23:59:59.999)
 *     · 403 `PERMISSION_DENIED`(`member.read` 없음) · 400 `VALIDATION`(쪽 크기 1~100 밖 등) · 400 `MEMBER_PERIOD_REVERSED`(기간 거꾸로) · 400 `MEMBER_POINT_RANGE_REVERSED`(포인트 범위 거꾸로)
 *     · 이메일은 `member.pii` 가 없으면 서버가 `ab***@gmail.com` 으로 가려서 준다(`emailMasked: true`) — 화면은 가공하지 않고 그대로 그린다.
 *   - `GET /api/admin/members/{memberId}` → 200 `MemberDetail`(목록 한 줄의 칸 + 보유 회원 역할(유형·게시판 이름 포함) · 활동 요약 · 제재 이력 · 상태 사유) · 404 `MEMBER_NOT_FOUND` · 403 `PERMISSION_DENIED`. 이메일 마스킹 규칙은 목록과 같다.
 *     · 상세 보강 칸(후보 계약, 확정 전 — 근거는 `useMemberApi.types.ts` 머리 주석): `bio`·`social[]`·`adultConfirmed`·`acceptDm`·`dormancy`·`withdrawnAt`·`nicknameHistory`(`member.pii` 없으면 null)·`piiMasked`·`activity.likesReceived/popularCount`·
 *       `sanctionSummary`·`reports`·`gradePoint`(자동 승급·다음 등급 조건·등급 변경 이력·변경권·포인트 내역)·`settings`(표시 형식·알림 종류별 on/off·푸시·차단/메모/알림함 건수)·`contact`·`profile`(서명·관리자 메모)·
 *       `consents`(필수 동의 시각·버전 + 선택 동의 + 변경·철회 이력)·`groupBuy`(건수 요약·최근 주문 5건·주문 단위 배송지). 탈퇴 회원은 이 칸들이 null/빈 값이다. 응답이 커지면 탭별 하위 경로로 나눌 수 있다(미정).
 *     · 본인 인증(방법·여부·성인인증·이력)은 아직 도입하지 않아 계약에 칸이 없다(「본인 인증」 탭은 준비 중 표시).
 *   - `GET /api/admin/members/role-options` → 200 `[{ roleId, roleName }]` (역할 고르개 — 회원 역할 이름으로 찾는다)
 *
 * ⚠ 미정(서버 정책): 휴대폰·전화·주소·공동구매 배송지 가림 규칙(`member.pii`), 배송지 열람 사유·열람 기록(community 는 사유 5~200자 + 기록), 관리자 메모 편집 권한, 마스킹 규칙, `member.pii` 가 없을 때 이메일 검색 방식(목업은 전체 주소가 같을 때만), 이메일 열람을 감사 로그에 남길지, 정렬 가능한 칸의 범위.
 *
 * ⚠ MOCK: 지금은 서버가 없어서 `./mock/member-mock.ts` 를 부른다(스위치는 그 파일 머리말). 서버가 붙으면 `// MOCK:` 줄을 지우고 `// REAL:` 줄을 되살린다.
 */
const PATH = {
    root: '/api/admin/members',
    roleOptions: () => `${PATH.root}/role-options`,
    one: (memberId: string) => `${PATH.root}/${encodeURIComponent(memberId)}`,
};

export function useMemberApi() {
    // REAL: const client = useApiClient();
    void PATH;

    return {
        /** 거른 목록 한 쪽 */
        search: (query: MemberQuery): Promise<MemberPage> =>
            // MOCK:
            mockMemberSearch(query),
        // REAL: client.get<MemberPage>(PATH.root, { query }),

        /** 회원 한 명의 상세 — 상세 Drawer 가 연다 */
        getMember: (memberId: string): Promise<MemberDetail> =>
            // MOCK:
            mockMemberGet(memberId),
        // REAL: client.get<MemberDetail>(PATH.one(memberId)),

        /** 역할 고르개가 쓸 목록 */
        loadRoleOptions: (): Promise<MemberRoleOption[]> =>
            // MOCK:
            mockMemberRoleOptions(),
        // REAL: client.get<MemberRoleOption[]>(PATH.roleOptions()),
    };
}
