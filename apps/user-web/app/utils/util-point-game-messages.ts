import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { RoulettePrize } from '~/composables/pointgame/api/usePointGameApi.types';
import type { LottoPickProblem } from '~/utils/util-point-game';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilPoint from '~/utils/util-point';

/**
 * 포인트 게임의 계산이 낀 문구(상품 이름 · 번호 조건 문제 · 오류 한 줄) — `t` 를 첫 인자로 받는다(`i18n.md` 3). 상수 문구는 화면이 `t('pointGame.*')` 를 직접 부른다.
 */
export default class UtilPointGameMessages {
    /**
     * 상품 이름 — 꽝 · `10P`
     * @param {Translate} t - 번역 함수.
     * @param {RoulettePrize} prize - 상품.
     * @returns {string} 이름.
     */
    static prize(t: Translate, prize: RoulettePrize): string {
        return prize.points > 0 ? UtilPoint.points(prize.points) : t('pointGame.miss');
    }

    /**
     * 번호 조건 문제 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {LottoPickProblem} problem - 문제.
     * @returns {string} 문구.
     */
    static pickProblem(t: Translate, problem: LottoPickProblem): string {
        if (problem === 'FIXED_REQUIRED') return t('pointGame.fixedRequired');
        return problem === 'FIXED_TOO_MANY' ? t('pointGame.fixedTooMany') : t('pointGame.poolTooSmall');
    }

    /**
     * 오류 한 줄 — 서버 문구 그대로(가공하지 않는다). 입력 오류(400)는 칸 문구가 까닭이라 첫 칸 문구를 쓴다
     * (로또 `fields.excluded` — 「서로 다른 N세트를 만들 수 없음」 등). 서버에 닿지 못했으면 연결 문구
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 던져진 것.
     * @returns {string} 문구.
     */
    static error(t: Translate, e: unknown): string {
        if (!(e instanceof ApiError)) return UtilAuthMessages.fallback(t, 0, 'NETWORK');
        const field = e.fields ? Object.values(e.fields).find((m) => Boolean(m)) : undefined;
        return field ?? e.message;
    }
}
