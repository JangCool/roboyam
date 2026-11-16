import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { PointGameCheckUrlProblem, PointGameProviderNameProblem } from '~/utils/util-point-game';
import UtilPointGame from '~/utils/util-point-game';

/**
 * 포인트 게임 관리자 설정의 계산이 낀 문구(칸 검사 문제) — `t` 를 첫 인자로 받는다(`i18n.md` 3). 상수 문구는 화면이 `t('pointGame.*')` 를 직접 부른다.
 */
export default class UtilPointGameMessages {
    /**
     * 사업자 이름 문제 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {PointGameProviderNameProblem} problem - 문제.
     * @returns {string} 문구.
     */
    static providerName(t: Translate, problem: PointGameProviderNameProblem): string {
        return problem === 'REQUIRED' ? t('pointGame.providerNameRequired') : t('pointGame.providerNameTooLong', { max: UtilPointGame.PROVIDER_NAME_MAX });
    }

    /**
     * 링크 문제 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {PointGameCheckUrlProblem} problem - 문제.
     * @returns {string} 문구.
     */
    static checkUrl(t: Translate, problem: PointGameCheckUrlProblem): string {
        switch (problem) {
            case 'REQUIRED':
                return t('pointGame.checkUrlRequired');
            case 'NOT_HTTPS':
                return t('pointGame.checkUrlNotHttps');
            case 'TOO_LONG':
                return t('pointGame.checkUrlTooLong', { max: UtilPointGame.CHECK_URL_MAX });
            default:
                return t('pointGame.checkUrlInvalid');
        }
    }
}
