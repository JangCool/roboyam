import type { TeamMemberView, TeamView } from '~/composables/team/api/useTeamApi.types';

/** 팀 이름·설명 검사 결과 — 문구는 `UtilTeamMessages.problem` 이 만든다 */
export type TeamProblem = 'nameRequired' | 'nameTooLong' | 'descTooLong';

/**
 * 조직 구성(팀) 화면의 순수 규칙 — 입력 검사, 팀 목록 걸러내기, 위·아래 이동 가능 여부, 알림 이벤트의 추가 수신 팀 선택지.
 * 서버 규칙 이름: 이름 필수·50자·설명 500자는 후보 계약 `TeamSaveRequest` 의 검사이고(서버가 같은 규칙으로 다시 본다), 이름 중복은 서버만 안다(409 `TEAM_NAME_DUPLICATED`). 서버 규칙은 목업 단계의 가정이다.
 */
export default class UtilTeam {
    /** 팀 이름 최대 글자 수 */
    static readonly NAME_MAX = 50;
    /** 팀 설명 최대 글자 수 */
    static readonly DESC_MAX = 500;

    /**
     * 팀 이름의 문제.
     * @param {string} name - 입력한 이름.
     * @returns {TeamProblem | null} 문제가 없으면 null.
     */
    static nameProblem(name: string): TeamProblem | null {
        const trimmed = name.trim();
        if (!trimmed) return 'nameRequired';
        return trimmed.length > UtilTeam.NAME_MAX ? 'nameTooLong' : null;
    }

    /**
     * 팀 설명의 문제.
     * @param {string} description - 입력한 설명.
     * @returns {TeamProblem | null} 문제가 없으면 null.
     */
    static descProblem(description: string): TeamProblem | null {
        return description.trim().length > UtilTeam.DESC_MAX ? 'descTooLong' : null;
    }

    /**
     * 팀 목록을 이름·설명으로 거른다(대소문자 무시).
     * @param {readonly TeamView[]} teams - 팀들.
     * @param {string} keyword - 검색어. 비면 전부.
     * @returns {TeamView[]} 걸러낸 팀들(원래 차례).
     */
    static filterTeams(teams: readonly TeamView[], keyword: string): TeamView[] {
        const needle = keyword.trim().toLowerCase();
        if (!needle) return [...teams];
        return teams.filter((team) => team.teamName.toLowerCase().includes(needle) || (team.description ?? '').toLowerCase().includes(needle));
    }

    /**
     * 팀원·후보를 이름·이메일로 거른다(대소문자 무시).
     * @param {readonly T[]} members - 사람들.
     * @param {string} keyword - 검색어. 비면 전부.
     * @returns {T[]} 걸러낸 사람들(원래 차례).
     */
    static filterMembers<T extends TeamMemberView>(members: readonly T[], keyword: string): T[] {
        const needle = keyword.trim().toLowerCase();
        if (!needle) return [...members];
        return members.filter((member) => member.operatorName.toLowerCase().includes(needle) || member.email.toLowerCase().includes(needle));
    }

    /**
     * 위·아래로 한 칸 옮길 수 있는가 — **걸러진 목록이 아니라 전체 목록**에서 끝을 본다(검색 중에도 실제로 옮기는 것은 전체 목록의 자리다).
     * @param {readonly TeamView[]} teams - 전체 팀 목록(정렬 순서대로).
     * @param {string} teamId - 옮길 팀.
     * @param {'UP' | 'DOWN'} direction - 방향.
     * @returns {boolean} 옮길 수 있으면 true.
     */
    static canMove(teams: readonly TeamView[], teamId: string, direction: 'UP' | 'DOWN'): boolean {
        const at = teams.findIndex((team) => team.teamId === teamId);
        if (at < 0) return false;
        return direction === 'UP' ? at > 0 : at < teams.length - 1;
    }
}
