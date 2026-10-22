/**
 * 조직 구성(팀) API 가 주고받는 모양 — 서버 `TeamView`·`TeamMemberView`·`TeamCandidateView` (`docs/features/notification-contract.md` 「서버 구현 결과 (알림 도구·조직 구성)」).
 * 칸 이름은 hamonica3 `TeamView`·`TeamMemberView`(`useTeamApi.ts`)를 따르되 이 제품의 말로 바꿨다(`userId` → `operatorId`, `userName` → `operatorName`, `use` → `enabled`).
 * 팀원은 **관리자 앱 운영자 계정**이다(공개 사이트 회원이 아니다). ⚠ ID 는 문자열이다(Snowflake). 숫자로 바꾸지 않는다.
 */

/** 팀원 한 사람 — 운영자 계정의 요약 */
export interface TeamMemberView {
    operatorId: string;
    operatorName: string;
    email: string;
    /** 지금 로그인할 수 있는 운영자인가. false 면(사용 중지·잠금·기간 만료) 팀에는 남아 있지만 알림을 받지 않는다 — 화면이 딱지로 구분해 보인다 */
    active: boolean;
}

/** 팀 한 개 — 팀원까지 담겨 온다 */
export interface TeamView {
    teamId: string;
    teamName: string;
    description: string | null;
    /** 정렬 순서(1부터). 화면은 숫자를 적어 넣지 않고 위·아래 화살표로만 옮긴다 — 서버가 다시 매긴다 */
    sortOrder: number;
    /** 쓰는 팀인가. 팀원이 있으면 지우는 대신 멈춘다(「사용 안 함」). 멈춘 팀은 알림의 추가 수신 팀으로 새로 고를 수 없다 */
    enabled: boolean;
    members: TeamMemberView[];
    /** 이 팀을 「추가 수신 팀」으로 쓰는 알림 이벤트 수 — 지울 때 확인 문구에 쓴다 */
    usedByEventCount: number;
}

/** `POST /api/admin/teams` · `PUT /api/admin/teams/{teamId}` 의 요청 — 응답은 바뀐 한 줄(`TeamView`). 차례는 보내지 않는다(서버가 맨 뒤에 붙이고, 옮기는 것은 `move`) */
export interface TeamSaveRequest {
    teamName: string;
    description: string | null;
    enabled: boolean;
}

/** `PUT /api/admin/teams/{teamId}/move` 의 요청 — 한 칸 위·아래로 */
export interface TeamMoveRequest {
    direction: 'UP' | 'DOWN';
}

/** `PUT /api/admin/teams/{teamId}/members` 의 요청 — 팀원 **전체 목록**으로 바꾼다(추가·제거 모두 이 한 요청). 응답은 바뀐 한 줄(`TeamView`) */
export interface TeamMembersRequest {
    operatorIds: string[];
}

/** 팀에 넣을 수 있는 운영자 — 활성 운영자만 오므로 `active` 칸이 없다. 이미 든 사람도 함께 온다(화면이 짚어 주고 고르지 못하게 한다) */
export interface TeamCandidateView extends Omit<TeamMemberView, 'active'> {
    alreadyIn: boolean;
}
