import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilTeam, { type TeamProblem } from '~/utils/util-team';

/** 조직 구성 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('team.…')` 로 부른다. 키 규칙은 `i18n.md`. */
export default class UtilTeamMessages {
    /**
     * 입력 검사 문제를 사람이 읽을 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {TeamProblem} problem - 문제.
     * @returns {string} 문구.
     */
    static problem(t: Translate, problem: TeamProblem): string {
        if (problem === 'nameRequired') return t('team.nameRequired');
        if (problem === 'nameTooLong') return t('team.nameTooLong', { max: UtilTeam.NAME_MAX });
        return t('team.descTooLong', { max: UtilTeam.DESC_MAX });
    }

    /**
     * 팀 지우기 확인 문구 — 이 팀을 추가 수신 팀으로 쓰는 이벤트가 있으면 함께 빠진다고 알린다.
     * @param {Translate} t - 번역 함수.
     * @param {string} name - 팀 이름.
     * @param {number} eventCount - 이 팀을 고른 이벤트 수.
     * @returns {string} 문구.
     */
    static deleteConfirm(t: Translate, name: string, eventCount: number): string {
        return eventCount > 0 ? t('team.deleteConfirmUsed', { name, n: eventCount }) : t('team.deleteConfirm', { name });
    }
}
