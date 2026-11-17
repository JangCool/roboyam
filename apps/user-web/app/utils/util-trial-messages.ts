import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { TrialBlockedReason, TrialForm } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';

/**
 * 체험단 화면의 계산이 낀 문구(trial-group-contract) — 금액·인원·막힌 까닭. 상수 문구는 화면이 `t('trial.…')` 를 바로 부른다.
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

    /** 형태 한 줄 — 「제공형 — 받은 제품을 가져요」 */
    static formLine(t: Translate, form: TrialForm): string {
        return form === 'GIVE' ? t('trial.formGiveLine') : t('trial.formLendLine');
    }

    /** 리뷰어 몫 — 「5명 — 리뷰어 3명 · 일반 2명」 */
    static quotaLine(t: Translate, recruitCount: number, reviewerShare: number): string {
        const s = UtilTrial.split(recruitCount, reviewerShare);
        return t('trial.quotaLine', { total: recruitCount, reviewer: s.reviewer, general: s.general });
    }

    /** 신청을 막는 까닭(서버 `blockedReason`) — 내 신청 창 전이면 그 시각 */
    static blocked(t: Translate, reason: TrialBlockedReason, applyOpenAt: string, dateText: (iso: string) => string): string {
        switch (reason) {
            case 'APPLIED':
                return t('trial.blockedApplied');
            case 'IDENTITY_REQUIRED':
                return t('trial.blockedIdentity');
            case 'EXCLUDED':
                return t('trial.blockedAccount');
            case 'RESTRICTED':
                return t('trial.blockedRestricted');
            case 'NOT_OPEN':
                return t('trial.blockedNotOpen', { date: dateText(applyOpenAt) });
            case 'NOT_RECRUITING':
                return t('trial.blockedClosed');
        }
    }
}
