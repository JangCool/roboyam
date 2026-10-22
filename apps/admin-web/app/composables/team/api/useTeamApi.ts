import type { TeamCandidateView, TeamMembersRequest, TeamMoveRequest, TeamSaveRequest, TeamView } from '~/composables/team/api/useTeamApi.types';

/**
 * 조직 구성(팀) API — 서버 주소 문자열(`/api/admin/teams…`)이 있는 **유일한 곳**이다. 상태는 없다(화면이 쓰는 상태는 `useTeams`).
 * 팀 = 이름·설명·사용 여부·정렬 순서, 팀원 = **관리자 앱 운영자 계정**. 알림 이벤트의 「추가 수신 팀」으로 쓴다(`docs/features/notification-contract.md` 「알림 도구·조직 구성」).
 *
 * 계약(서버 `TeamController`). 오류는 모두 `ApiError`(useApiClient)로 던져진다. 쓰기는 CSRF. 권한: 읽기 `team.read`, 쓰기·팀원 관리 `team.manage`.
 *   - `GET    /api/admin/teams` → 200 `TeamView[]` (정렬 순서대로, 팀원까지 담긴다. `team.read`)
 *   - `POST   /api/admin/teams` `TeamSaveRequest` → 201 `TeamView` (서버가 맨 뒤에 붙인다. 400 `VALIDATION` `fields.teamName|description` · 409 `TEAM_NAME_DUPLICATED` `fields.teamName` · 409 `TEAM_LIMIT_EXCEEDED`. `team.manage`)
 *   - `PUT    /api/admin/teams/{teamId}` `TeamSaveRequest` → 200 `TeamView` (이름·설명·사용 여부. 같은 검사. `team.manage`)
 *   - `DELETE /api/admin/teams/{teamId}` → 204 (팀원이 있으면 409 `TEAM_HAS_MEMBERS` — 대신 「사용 안 함」. 이 팀을 추가 수신 팀으로 쓰던 이벤트에서는 함께 빠진다. `team.manage`)
 *   - `PUT    /api/admin/teams/{teamId}/move` `TeamMoveRequest` → 200 `TeamView[]` (한 칸 옮기면 이웃의 순서도 바뀌므로 목록 전부를 돌려준다. `team.manage`)
 *   - `PUT    /api/admin/teams/{teamId}/members` `TeamMembersRequest` → 200 `TeamView` (팀원 전체 목록으로 바꾼다. 활성이 아니거나 없는 새 운영자는 400 `TEAM_OPERATOR_NOT_FOUND` `fields.operatorIds`. `team.manage`)
 *   - `GET    /api/admin/teams/{teamId}/candidates` → 200 `TeamCandidateView[]` (활성 운영자 + 이미 든 사람 표시 `alreadyIn`. `team.manage`)
 *   공통 오류: 404 `TEAM_NOT_FOUND` · 403 `PERMISSION_DENIED`. 감사: `TEAM_CREATE|UPDATE|DELETE|MOVE|MEMBERS_UPDATE`.
 */
const PATH = {
    teams: '/api/admin/teams',
    team: (teamId: string) => `${PATH.teams}/${encodeURIComponent(teamId)}`,
    move: (teamId: string) => `${PATH.team(teamId)}/move`,
    members: (teamId: string) => `${PATH.team(teamId)}/members`,
    candidates: (teamId: string) => `${PATH.team(teamId)}/candidates`,
};

export function useTeamApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 팀 목록 — 정렬 순서대로, 팀원까지 */
        list: (): Promise<TeamView[]> => client.get<TeamView[]>(PATH.teams),

        /** 팀을 만든다 — 서버가 맨 뒤에 붙인다 */
        create: (body: TeamSaveRequest): Promise<TeamView> => client.post<TeamView>(PATH.teams, body),

        /** 팀의 이름·설명·사용 여부를 고친다 */
        update: (teamId: string, body: TeamSaveRequest): Promise<TeamView> => client.put<TeamView>(PATH.team(teamId), body),

        /** 팀을 지운다 — 팀원이 있으면 서버가 거절한다 */
        remove: (teamId: string): Promise<void> => client.del<void>(PATH.team(teamId)),

        /** 한 칸 위·아래로 옮긴다 — 바뀐 목록 전부를 돌려받는다 */
        move: (teamId: string, body: TeamMoveRequest): Promise<TeamView[]> => client.put<TeamView[]>(PATH.move(teamId), body),

        /** 팀원 전체 목록을 바꾼다(추가·제거) */
        setMembers: (teamId: string, body: TeamMembersRequest): Promise<TeamView> => client.put<TeamView>(PATH.members(teamId), body),

        /** 팀에 넣을 수 있는 운영자 — 이미 든 사람도 표시해서 */
        candidates: (teamId: string): Promise<TeamCandidateView[]> => client.get<TeamCandidateView[]>(PATH.candidates(teamId)),
    };
}
