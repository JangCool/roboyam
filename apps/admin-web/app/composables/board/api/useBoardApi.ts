import type {
    BoardCreateRequest,
    BoardDefaultsResponse,
    BoardDefaultsSaveRequest,
    BoardDefaultsTab,
    BoardDefaultsTabResponse,
    BoardNotificationSettings,
    BoardPrivacyAxis,
    BoardPrivacyResponse,
    BoardPrivacyRunResult,
    BoardPrivacySettings,
    BoardPrivacyTarget,
    BoardReportSettings,
    BoardSecuritySettings,
    BoardDetail,
    BoardListItem,
    BoardListResponse,
    BoardOrderRequest,
    BoardSaveRequest,
    BoardStaffAppointRequest,
    BoardStaffCandidate,
    BoardStaffMember,
} from '~/composables/board/api/useBoardApi.types';

/**
 * 게시판 관리 API — 서버 주소 문자열(`/api/admin/boards…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 * 후보 계약(확정 전). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 응답 모양은 `useBoardApi.types.ts`.
 *   - `GET    /api/admin/boards/defaults` → 200 `BoardDefaultsResponse` (`board.read`)
 *   - `PUT    /api/admin/boards/defaults` `BoardDefaultsSaveRequest` → 200 `BoardDefaultsResponse` (칸 전부를 한 번에. 바뀐 칸에 따라 `board.settings`·`board.staff.limit`. 400 `VALIDATION` `fields.<칸>`)
 *   - `GET    /api/admin/boards` → 200 `BoardListResponse` (순서대로, 꺼진 것 포함. `board.read`)
 *   - `POST   /api/admin/boards` `BoardCreateRequest` → 201 `BoardListItem` (400 `VALIDATION` `fields.name|slug|description|layout|kind|groupCategoryId` · 409 `BOARD_SLUG_DUPLICATE` · `BOARD_NAME_DUPLICATE`. `board.manage`, `groupCategoryId` 를 실으면 + `group.settings`)
 *   - `GET    /api/admin/boards/by-slug/{slug}` → 200 `BoardDetail` (**화면 주소가 slug 라 더한 후보** — `GET /api/admin/boards/{id}` 와 같은 응답. `board.read`)
 *   - `PUT    /api/admin/boards/{id}` `BoardSaveRequest` → 200 `BoardDetail` (기본 정보 + 정책 + 말머리 한 트랜잭션. 400 `VALIDATION` `fields.name|policy.<칸>|prefixes[i].name…` ·
 *              409 `BOARD_NAME_DUPLICATE` · `BOARD_INVESTMENT_NOTICE_LOCKED` · `PREFIX_IN_USE`(글이 있는 말머리를 지움) · `PREFIX_LIMIT`. 바뀐 부분에 따라 `board.manage`·`board.settings`·`board.staff.limit`)
 *   - `PATCH  /api/admin/boards/{id}/active` `BoardActiveRequest` → 200 `BoardListItem` (`board.manage`)
 *   - `PUT    /api/admin/boards/order` `BoardOrderRequest` → 200 `BoardListResponse` (모든 게시판 ID 의 새 순서. `board.manage`)
 *   - `DELETE /api/admin/boards/{id}` → 204 (글이 있으면 409 `BOARD_HAS_POSTS`. `board.delete`)
 *   - `GET    /api/admin/boards/{id}/staff` → 200 `BoardStaffMember[]` (`board.read`)
 *   - `POST   /api/admin/boards/{id}/staff` `BoardStaffAppointRequest` → 200 `BoardStaffMember[]` (409 `BOARD_STAFF_LIMIT` · `BOARD_STAFF_INELIGIBLE` · `BOARD_STAFF_KIND`. `board.staff.manage`)
 *   - `DELETE /api/admin/boards/{id}/staff/{memberId}` → 200 `BoardStaffMember[]` (`board.staff.manage`)
 *   - `GET    /api/admin/boards/staff-candidates?q=&boardId=` → 200 `BoardStaffCandidate[]` (자격 미달은 사유와 함께. `board.staff.manage`)
 *   기본 설정 하위 화면(화면마다 자기 GET·PUT, 응답 `BoardDefaultsTabResponse<설정>`. 읽기 `board.read`, 저장 `board.settings`. 400 `VALIDATION` `fields.<칸>`):
 *   - `GET·PUT /api/admin/boards/defaults/report` `BoardReportSettings` · `…/security` `BoardSecuritySettings` · `…/notifications` `BoardNotificationSettings`
 *   - `GET·PUT /api/admin/boards/defaults/privacy` `BoardPrivacySettings` → `BoardPrivacyResponse`(축별 대상 수 포함. 감사 기록 `BOARD_PRIVACY_RETENTION_UPDATE`)
 *   - `GET    /api/admin/boards/defaults/privacy/targets?axis=` → 200 `BoardPrivacyTarget[]` (대상 보기 — 더한 후보)
 *   - `POST   /api/admin/boards/defaults/privacy/run` `{ axis }` → 200 `BoardPrivacyRunResult` (저장된 값으로 한 번 실행, 꺼져 있으면 409 `BOARD_PRIVACY_DISABLED` — 더한 후보. 감사 기록 `BOARD_PRIVACY_RUN`)
 *   공통 오류: 404 `BOARD_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사 기록 후보: `BOARD_DEFAULTS_UPDATE` · `BOARD_CREATE` · `BOARD_UPDATE` · `BOARD_ACTIVE` · `BOARD_ORDER` · `BOARD_DELETE` · `BOARD_STAFF_APPOINT` · `BOARD_STAFF_REMOVE`.
 *
 * 서버 연결됨(2026-10-22) — `server/admin` 의 `BoardController`·`BoardDefaultsController`. 목업과 달라진 점은 `docs/features/board-contract.md` 「서버 구현 결과」.
 * 커머스 1-3: 정책·기본 설정에 `pointMinLengthApplied`(적립 최소 글자 수 적용, `pointMode` 다음 칸)가 더해졌다.
 */
const PATH = {
    boards: '/api/admin/boards',
    defaults: '/api/admin/boards/defaults',
    defaultsTab: (tab: BoardDefaultsTab) => `${PATH.defaults}/${tab}`,
    privacyTargets: '/api/admin/boards/defaults/privacy/targets',
    privacyRun: '/api/admin/boards/defaults/privacy/run',
    order: '/api/admin/boards/order',
    candidates: '/api/admin/boards/staff-candidates',
    bySlug: (slug: string) => `${PATH.boards}/by-slug/${encodeURIComponent(slug)}`,
    board: (id: string) => `${PATH.boards}/${encodeURIComponent(id)}`,
    active: (id: string) => `${PATH.board(id)}/active`,
    staff: (id: string) => `${PATH.board(id)}/staff`,
    staffMember: (id: string, memberId: string) => `${PATH.staff(id)}/${encodeURIComponent(memberId)}`,
};

export function useBoardApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 기본 설정 + 기본값을 따르는/따로 정한 게시판 */
        defaults: (): Promise<BoardDefaultsResponse> =>
            client.get<BoardDefaultsResponse>(PATH.defaults),

        /** 기본 설정을 한 번에 저장한다 */
        saveDefaults: (body: BoardDefaultsSaveRequest): Promise<BoardDefaultsResponse> =>
            client.put<BoardDefaultsResponse>(PATH.defaults, body),

        /** 게시판 목록 — 순서대로, 꺼진 것 포함 */
        list: (): Promise<BoardListResponse> =>
            client.get<BoardListResponse>(PATH.boards),

        /** 게시판을 만든다 — 맨 뒤에 붙고 켜진 채 시작한다 */
        create: (body: BoardCreateRequest): Promise<BoardListItem> =>
            client.post<BoardListItem>(PATH.boards, body),

        /** 게시판 하나(설정 화면) — 화면 주소의 slug 로 */
        bySlug: (slug: string): Promise<BoardDetail> =>
            client.get<BoardDetail>(PATH.bySlug(slug)),

        /** 기본 정보 + 정책 + 말머리를 한 번에 저장한다 */
        save: (id: string, body: BoardSaveRequest): Promise<BoardDetail> =>
            client.put<BoardDetail>(PATH.board(id), body),

        /** 게시판을 켜고 끈다 */
        setActive: (id: string, active: boolean): Promise<BoardListItem> =>
            client.patch<BoardListItem>(PATH.active(id), { active }),

        /** 게시판 순서를 바꾼다 — 모든 ID 의 새 순서 */
        order: (body: BoardOrderRequest): Promise<BoardListResponse> =>
            client.put<BoardListResponse>(PATH.order, body),

        /** 게시판을 지운다 — 글이 0개일 때만 */
        remove: (id: string): Promise<void> =>
            client.del<void>(PATH.board(id)),

        /** 그 게시판의 매니저·스텝 */
        staff: (id: string): Promise<BoardStaffMember[]> =>
            client.get<BoardStaffMember[]>(PATH.staff(id)),

        /** 매니저·스텝을 지정한다 — 담당 전체를 돌려받는다 */
        appointStaff: (id: string, body: BoardStaffAppointRequest): Promise<BoardStaffMember[]> =>
            client.post<BoardStaffMember[]>(PATH.staff(id), body),

        /** 매니저·스텝을 해제한다 — 담당 전체를 돌려받는다 */
        removeStaff: (id: string, memberId: string): Promise<BoardStaffMember[]> =>
            client.del<BoardStaffMember[]>(PATH.staffMember(id, memberId)),

        /** 담당 후보 회원 검색 — 자격 미달은 사유와 함께 */
        staffCandidates: (boardId: string, q: string): Promise<BoardStaffCandidate[]> =>
            client.get<BoardStaffCandidate[]>(PATH.candidates, { query: { q, boardId } }),

        // ── 기본 설정 하위 화면 ─

        /** 신고 정책 */
        report: (): Promise<BoardDefaultsTabResponse<BoardReportSettings>> =>
            client.get<BoardDefaultsTabResponse<BoardReportSettings>>(PATH.defaultsTab('report')),

        saveReport: (body: BoardReportSettings): Promise<BoardDefaultsTabResponse<BoardReportSettings>> =>
            client.put<BoardDefaultsTabResponse<BoardReportSettings>>(PATH.defaultsTab('report'), body),

        /** 보안 */
        security: (): Promise<BoardDefaultsTabResponse<BoardSecuritySettings>> =>
            client.get<BoardDefaultsTabResponse<BoardSecuritySettings>>(PATH.defaultsTab('security')),

        saveSecurity: (body: BoardSecuritySettings): Promise<BoardDefaultsTabResponse<BoardSecuritySettings>> =>
            client.put<BoardDefaultsTabResponse<BoardSecuritySettings>>(PATH.defaultsTab('security'), body),

        /** 알림(회원이 받는 게시판 알림의 기본값) */
        notifications: (): Promise<BoardDefaultsTabResponse<BoardNotificationSettings>> =>
            client.get<BoardDefaultsTabResponse<BoardNotificationSettings>>(PATH.defaultsTab('notifications')),

        saveNotifications: (body: BoardNotificationSettings): Promise<BoardDefaultsTabResponse<BoardNotificationSettings>> =>
            client.put<BoardDefaultsTabResponse<BoardNotificationSettings>>(PATH.defaultsTab('notifications'), body),

        /** 개인정보 보유기간 + 축별 대상 수 */
        privacy: (): Promise<BoardPrivacyResponse> =>
            client.get<BoardPrivacyResponse>(PATH.defaultsTab('privacy')),

        savePrivacy: (body: BoardPrivacySettings): Promise<BoardPrivacyResponse> =>
            client.put<BoardPrivacyResponse>(PATH.defaultsTab('privacy'), body),

        /** 축 하나의 파기 대상(저장된 설정 기준, 앞의 몇 줄) */
        privacyTargets: (axis: BoardPrivacyAxis): Promise<BoardPrivacyTarget[]> =>
            client.get<BoardPrivacyTarget[]>(PATH.privacyTargets, { query: { axis } }),

        /** 축 하나를 저장된 값으로 지금 한 번 실행한다 */
        runPrivacy: (axis: BoardPrivacyAxis): Promise<BoardPrivacyRunResult> =>
            client.post<BoardPrivacyRunResult>(PATH.privacyRun, { axis }),
    };
}
