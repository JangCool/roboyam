// MOCK: 조직 구성(팀) API(`GET·POST /api/admin/teams`, `PUT·DELETE …/{teamId}`, `PUT …/{teamId}/move`·`…/members`, `GET …/{teamId}/candidates`)의 메모리 목업 — `useTeamApi.ts` 가 이 파일의 함수를 부른다.
// 서버가 붙으면 `team/api/mock/` 과 함께 지운다. 응답 모양·오류 코드는 `useTeamApi.ts` 머리말(후보 계약)을 따른다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` (쉼표로 여러 개) ─
//   team-error         팀 목록 요청이 `500 INTERNAL` 로 실패(오류 상태와 다시 시도)
//   team-empty         팀이 하나도 없는 상태(빈 상태)
//   team-save-error    쓰기(만들기·고치기·지우기·옮기기·팀원 변경)가 `500 INTERNAL` 로 실패(폼의 오류 문구·토스트)
//   team-no-read       팀 목록 요청이 `403 PERMISSION_DENIED` — 알림 화면이 「팀 이름을 못 본다」로 물러서는지 확인
//
// ── 흉내 ─
//   팀 4개: 운영팀(팀원 3) · 개발팀(팀원 2) · 보안팀(팀원 없음) · 지원팀(팀원 1, 사용 안 함). 알림 이벤트의 추가 수신 팀 시작 값은 이벤트 전달 목업(`notification-event-delivery-mock.ts`)이 들고 있다.
//   팀원이 될 수 있는 운영자는 활성 운영자 6명(김관리·박운영·이배치 — 이전 운영자 목업의 사람들 — 과 최보안·강지원·문감사). 서버에서는 승인·잠기지 않음·사용 중인 운영자만 후보가 된다.
//   서버 규칙 흉내: 이름 필수·50자·설명 500자(400 VALIDATION + `fields.teamName|description`), 이름 중복은 대소문자 무시(409 `TEAM_NAME_DUPLICATED` + `fields.teamName`),
//   없는 팀 404 `TEAM_NOT_FOUND`, 팀원이 있는 팀 지우기 409 `TEAM_HAS_MEMBERS`(대신 「사용 안 함」), 없는 운영자 400 `TEAM_OPERATOR_NOT_FOUND`,
//   필요한 권한이 없으면 403 `PERMISSION_DENIED`(읽기 `team.read`, 쓰기 `team.manage`) — "나"는 로그인한 운영자(`can`)다. 바꾼 것은 메모리에만 남아 새로고침하면 처음으로 돌아간다.

import { ApiError } from '~/composables/common/useApiClient';
import { MOCK_ERRORS, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import { mockEventCountOfTeam, mockRemoveTeamFromDeliveries } from '~/composables/notification/api/mock/notification-event-delivery-mock';
import type { TeamCandidateView, TeamMemberView, TeamMembersRequest, TeamMoveRequest, TeamSaveRequest, TeamView } from '~/composables/team/api/useTeamApi.types';
import UtilTeam from '~/utils/util-team';

/** 활성 운영자들 — 팀원 후보 */
const OPERATORS: TeamMemberView[] = [
    { operatorId: '1001', operatorName: '김관리', email: 'admin@example.com' },
    { operatorId: '1002', operatorName: '박운영', email: 'staff@example.com' },
    { operatorId: '1003', operatorName: '이배치', email: 'batch@example.com' },
    { operatorId: '1009', operatorName: '최보안', email: 'security@example.com' },
    { operatorId: '1010', operatorName: '강지원', email: 'support@example.com' },
    { operatorId: '1011', operatorName: '문감사', email: 'audit@example.com' },
];

/** 팀 원본 — 팀원은 운영자 아이디만 들고 응답을 만들 때 붙인다 */
interface StoredTeam {
    teamId: string;
    teamName: string;
    description: string | null;
    enabled: boolean;
    operatorIds: string[];
}

/** 배열 순서가 곧 정렬 순서다 */
let teams: StoredTeam[] | null = null;
let nextId = 100;

function store(): StoredTeam[] {
    if (teams) return teams;
    teams = [
        { teamId: 't-ops', teamName: '운영팀', description: '사이트 운영 전반을 맡아요.', enabled: true, operatorIds: ['1001', '1002', '1003'] },
        { teamId: 't-dev', teamName: '개발팀', description: '배치와 시스템 점검을 맡아요.', enabled: true, operatorIds: ['1003', '1011'] },
        { teamId: 't-sec', teamName: '보안팀', description: '로그인 잠금·보안 알림을 확인해요.', enabled: true, operatorIds: [] },
        { teamId: 't-support', teamName: '지원팀', description: '문의 대응 팀. 지금은 쉬고 있어요.', enabled: false, operatorIds: ['1010'] },
    ];
    return teams;
}

function toView(team: StoredTeam, at: number): TeamView {
    return {
        teamId: team.teamId,
        teamName: team.teamName,
        description: team.description,
        sortOrder: at + 1,
        enabled: team.enabled,
        members: team.operatorIds.flatMap((id) => OPERATORS.find((operator) => operator.operatorId === id) ?? []),
        usedByEventCount: mockEventCountOfTeam(team.teamId),
    };
}

const viewList = (): TeamView[] => store().map(toView);

const notFound = (): ApiError => new ApiError({ status: 404, code: 'TEAM_NOT_FOUND', message: '팀을 찾을 수 없어요.' });
const denied = (): ApiError => new ApiError({ status: 403, code: 'PERMISSION_DENIED', message: '이 작업을 할 권한이 없어요.' });

function find(teamId: string): StoredTeam {
    const team = store().find((item) => item.teamId === teamId);
    if (!team) throw notFound();
    return team;
}

/** 이름·설명 규칙 — 서버 `TeamSaveRequest` 검사를 흉내 낸다(화면은 `UtilTeam` 으로 미리 같은 규칙을 본다) */
function check(body: TeamSaveRequest, self?: StoredTeam): void {
    const fields: Record<string, string> = {};
    const name = body.teamName.trim();
    if (!name) fields.teamName = '팀 이름을 입력해 주세요.';
    else if (name.length > UtilTeam.NAME_MAX) fields.teamName = `팀 이름은 ${UtilTeam.NAME_MAX}자 이하로 입력해 주세요.`;
    if ((body.description ?? '').length > UtilTeam.DESC_MAX) fields.description = `설명은 ${UtilTeam.DESC_MAX}자 이하로 입력해 주세요.`;
    if (Object.keys(fields).length) throw new ApiError({ status: 400, code: 'VALIDATION', message: '입력한 내용을 확인해 주세요.', fields });
    if (store().some((item) => item !== self && item.teamName.trim().toLowerCase() === name.toLowerCase())) {
        throw new ApiError({ status: 409, code: 'TEAM_NAME_DUPLICATED', message: '같은 이름의 팀이 이미 있어요.', fields: { teamName: '같은 이름의 팀이 이미 있어요.' } });
    }
}

async function write(can: (code: string) => boolean): Promise<void> {
    await mockDelay();
    if (!can('team.manage')) throw denied();
    if (mockFlags().has('team-save-error')) throw MOCK_ERRORS.internal();
}

// ── 다른 목업(이벤트 전달)이 부르는 집계 ─

/** 팀 id → 사용 여부 — 이벤트 전달 목업이 고를 수 있는 팀인지 가를 때 쓴다 */
export function mockTeamStates(): Map<string, boolean> {
    return new Map(store().map((team) => [team.teamId, team.enabled]));
}

// ── API ─

export async function mockTeamList(can: (code: string) => boolean): Promise<TeamView[]> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('team-no-read') || !can('team.read')) throw denied();
    if (flags.has('team-error')) throw MOCK_ERRORS.internal();
    if (flags.has('team-empty')) return [];
    return viewList();
}

export async function mockTeamCreate(can: (code: string) => boolean, body: TeamSaveRequest): Promise<TeamView> {
    await write(can);
    check(body);
    const team: StoredTeam = { teamId: `t-${nextId++}`, teamName: body.teamName.trim(), description: body.description?.trim() || null, enabled: body.enabled, operatorIds: [] };
    store().push(team);
    return toView(team, store().length - 1);
}

export async function mockTeamUpdate(can: (code: string) => boolean, teamId: string, body: TeamSaveRequest): Promise<TeamView> {
    await write(can);
    const team = find(teamId);
    check(body, team);
    team.teamName = body.teamName.trim();
    team.description = body.description?.trim() || null;
    team.enabled = body.enabled;
    return toView(team, store().indexOf(team));
}

export async function mockTeamRemove(can: (code: string) => boolean, teamId: string): Promise<void> {
    await write(can);
    const team = find(teamId);
    if (team.operatorIds.length > 0) {
        throw new ApiError({ status: 409, code: 'TEAM_HAS_MEMBERS', message: '팀원이 있는 팀은 지울 수 없어요. 대신 「사용 안 함」으로 멈춰 주세요.' });
    }
    teams = store().filter((item) => item !== team);
    mockRemoveTeamFromDeliveries(teamId);
}

export async function mockTeamMove(can: (code: string) => boolean, teamId: string, body: TeamMoveRequest): Promise<TeamView[]> {
    await write(can);
    const list = store();
    const at = list.indexOf(find(teamId));
    const to = body.direction === 'UP' ? at - 1 : at + 1;
    if (to >= 0 && to < list.length) {
        const [moved] = list.splice(at, 1);
        list.splice(to, 0, moved as StoredTeam);
    }
    return viewList();
}

export async function mockTeamSetMembers(can: (code: string) => boolean, teamId: string, body: TeamMembersRequest): Promise<TeamView> {
    await write(can);
    const team = find(teamId);
    if (body.operatorIds.some((id) => !OPERATORS.some((operator) => operator.operatorId === id))) {
        throw new ApiError({ status: 400, code: 'TEAM_OPERATOR_NOT_FOUND', message: '팀원으로 넣을 수 없는 운영자가 있어요. 목록을 다시 불러와 주세요.' });
    }
    team.operatorIds = [...new Set(body.operatorIds)];
    return toView(team, store().indexOf(team));
}

export async function mockTeamCandidates(can: (code: string) => boolean, teamId: string): Promise<TeamCandidateView[]> {
    await mockDelay();
    if (!can('team.manage')) throw denied();
    if (mockFlags().has('team-error')) throw MOCK_ERRORS.internal();
    const team = find(teamId);
    return OPERATORS.map((operator) => ({ ...operator, alreadyIn: team.operatorIds.includes(operator.operatorId) }));
}
