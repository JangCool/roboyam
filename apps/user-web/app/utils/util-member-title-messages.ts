import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { TitleTier } from '#layers/member-title/utils/util-title-icon';
import type { TitleApplicationStatus, TitleMethod } from '~/composables/title/api/useTitleApi.types';
import UtilMemberTitle from '~/utils/util-member-title';

/**
 * 회원 칭호 화면의 계산이 낀 문구 — 문구 본문은 `i18n/locales/{ko,en}/memberTitle.ts`. 칭호 이름·조건 글자·받는 방법 안내는 서버가 요청 언어로 준다(번역하지 않는다).
 */
export default class UtilMemberTitleMessages {
    /**
     * 「회원 중 0.4%」·「회원 중 0.1% 미만」.
     * @param {Translate} t - 번역 함수.
     * @param {number} ratio - 0~1.
     * @returns {string} 글자.
     */
    static ratio(t: Translate, ratio: number): string {
        const p = UtilMemberTitle.percent(ratio);
        return p.under ? t('memberTitle.ratioUnder', { percent: p.value }) : t('memberTitle.ratio', { percent: p.value });
    }

    /**
     * 받은 기간 한 칸 — 「2026년 10월」·「2025년」(모양이 다르면 그대로).
     * @param {Translate} t - 번역 함수.
     * @param {string} period - `YYYY-MM` 또는 `YYYY`.
     * @returns {string} 글자.
     */
    static period(t: Translate, period: string): string {
        const p = UtilMemberTitle.parsePeriod(period);
        if (!p) return period;
        return p.month ? t('memberTitle.periodMonth', { year: p.year, month: p.month }) : t('memberTitle.periodYear', { year: p.year });
    }

    /**
     * 단계 이름 — 동·은·금. 단계 없으면 빈 글자.
     * @param {Translate} t - 번역 함수.
     * @param {TitleTier | null} tier - 단계.
     * @returns {string} 글자.
     */
    static tier(t: Translate, tier: TitleTier | null): string {
        return tier ? t(`memberTitle.tier.${tier}`) : '';
    }

    /**
     * 받는 방법 이름 — 자동 · 신청 심사 · 운영자 수여.
     * @param {Translate} t - 번역 함수.
     * @param {TitleMethod} method - 받는 방법.
     * @returns {string} 글자.
     */
    static method(t: Translate, method: TitleMethod): string {
        return t(`memberTitle.method.${method}`);
    }

    /**
     * 신청 상태 이름 — 심사 중 · 승인 · 거절.
     * @param {Translate} t - 번역 함수.
     * @param {TitleApplicationStatus} status - 상태.
     * @returns {string} 글자.
     */
    static status(t: Translate, status: TitleApplicationStatus): string {
        return t(`memberTitle.status.${status}`);
    }

    /**
     * 진행 한 줄의 숫자 — 「47 / 100」.
     * @param {Translate} t - 번역 함수.
     * @param {number} current - 지금 값.
     * @param {number} target - 목표 값.
     * @returns {string} 글자.
     */
    static progress(t: Translate, current: number, target: number): string {
        return t('memberTitle.progressCount', { current: Math.min(current, target).toLocaleString(), target: target.toLocaleString() });
    }
}
