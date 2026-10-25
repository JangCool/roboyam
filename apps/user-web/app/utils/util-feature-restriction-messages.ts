import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { FeatureRestriction } from '~/utils/util-feature-restriction';

/**
 * 기능 제한 안내 문구 — 「공동구매 참여가 제한돼 있어요 · 10월 25일 14:00까지」(commerce.md 12-3). 본문은 `i18n/locales/{ko,en}/restriction.ts`.
 * 끝 시각은 표시 값(시간대·시간 형식)으로 그린다 — 올해면 월·일·시각, 다른 해면 연을 붙인다. 무기한이면 「해제될 때까지」.
 */
export default class UtilFeatureRestrictionMessages {
    /**
     * 언제까지 — 「10월 25일 14:00까지」 · 「해제될 때까지」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null} until - 끝나는 순간. null 은 무기한.
     * @param {DisplayTime} time - 표시 값.
     * @param {number} now - 지금(밀리초) — 연을 붙일지 판단한다.
     * @returns {string} 기간 글자.
     */
    static period(t: Translate, until: string | null, time: DisplayTime, now: number = Date.now()): string {
        const parts = time.parts(until);
        if (!until || !parts) return t('restriction.untilLifted');
        const thisYear = time.parts(new Date(now).toISOString())?.y;
        const clock = time.clock(until);
        if (parts.y !== thisYear) return t('restriction.untilYear', { year: parts.y, month: parts.mo, day: parts.d, clock });
        return t('restriction.until', { month: parts.mo, day: parts.d, clock });
    }

    /**
     * 안내 첫 줄 — 「공동구매 참여가 제한돼 있어요 · 10월 25일 14:00까지」.
     * @param {Translate} t - 번역 함수.
     * @param {FeatureRestriction} restriction - 제한.
     * @param {DisplayTime} time - 표시 값.
     * @returns {string} 첫 줄.
     */
    static title(t: Translate, restriction: FeatureRestriction, time: DisplayTime): string {
        return t('restriction.withPeriod', { title: t(`restriction.title_${restriction.type}`), period: UtilFeatureRestrictionMessages.period(t, restriction.until, time) });
    }
}
