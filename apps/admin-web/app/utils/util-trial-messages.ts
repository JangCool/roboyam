import type { Translate } from '#layers/i18n/utils/util-i18n';

/**
 * 체험단 관리 화면의 계산이 낀 문구(trial-group-contract — 관리자). 상수 문구는 화면이 `t('trial.…')` 를 바로 부른다.
 */
export default class UtilTrialMessages {
    /** 숫자 글자(천 단위 쉼표) */
    static number(n: number, locale: string): string {
        return new Intl.NumberFormat(locale === 'en' ? 'en-US' : 'ko-KR').format(n);
    }

    /** 「39,000원」 */
    static won(t: Translate, n: number, locale: string): string {
        return t('trial.won', { n: UtilTrialMessages.number(n, locale) });
    }

    /** 약속 지킨 비율 — 0~1 → 「90%」, 없으면 「—」 */
    static rate(rate: number | null): string {
        return rate === null ? '—' : `${Math.round(rate * 100)}%`;
    }
}
