import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { SiteLogoAnniversaryRule } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import { SITE_LOGO_RULE_NAME_MAX } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogoRule, { type SiteLogoRuleProblems } from '~/utils/util-site-logo-rule';

/**
 * 로고 보이기 규칙에서 **계산이 낀 문구** — 문구 본문은 `i18n/locales/{ko,en}/siteLogo.ts` 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 번역 함수 `t` 를 첫 인자로 받는다(`.claude/guides/frontend/i18n.md`).
 */
export default class UtilSiteLogoRuleMessages {
    /**
     * 월·일(`MM-DD`)을 글자로 — 「12월 25일」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null} md - `MM-DD`.
     * @returns {string} 글자, 모양이 아니면 빈 글자.
     */
    static monthDay(t: Translate, md: string | null): string {
        const parsed = UtilSiteLogoRule.parseMonthDay(md);
        return parsed ? t('siteLogo.monthDay', { month: parsed.month, day: parsed.day }) : '';
    }

    /**
     * 기념일 줄의 날짜 요약 — 「매년 12월 24일 ~ 12월 25일」 · 「2026.12.24 ~ 2026.12.25」 · 「쉬는 날 표의 설날」.
     * @param {Translate} t - 번역 함수.
     * @param {SiteLogoAnniversaryRule} rule - 기념일 줄.
     * @param {(ymd: string) => string} formatDate - 달력 날짜(`YYYY-MM-DD`)를 표시 형식으로(`useDisplayTime().value.serviceDate`).
     * @returns {string} 한 줄.
     */
    static when(t: Translate, rule: SiteLogoAnniversaryRule, formatDate: (ymd: string) => string): string {
        if (rule.mode === 'HOLIDAY') return rule.holidayName ? t('siteLogo.whenHoliday', { name: rule.holidayName }) : t('siteLogo.whenUnset');
        if (!rule.startDate || !rule.endDate) return t('siteLogo.whenUnset');
        if (rule.yearly) {
            const from = UtilSiteLogoRuleMessages.monthDay(t, rule.startDate);
            if (rule.startDate === rule.endDate) return t('siteLogo.whenYearlyDay', { date: from });
            return t('siteLogo.whenYearlyRange', { from, to: UtilSiteLogoRuleMessages.monthDay(t, rule.endDate) });
        }
        if (rule.startDate === rule.endDate) return formatDate(rule.startDate);
        return t('siteLogo.whenRange', { from: formatDate(rule.startDate), to: formatDate(rule.endDate) });
    }

    /**
     * 미리 검사한 이름 문제를 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {SiteLogoRuleProblems['name']} problem - 문제.
     * @returns {string} 칸 아래 한 줄(문제가 없으면 빈 글자).
     */
    static nameProblem(t: Translate, problem: SiteLogoRuleProblems['name']): string {
        if (problem === 'REQUIRED') return t('siteLogo.ruleNameRequired');
        if (problem === 'LENGTH') return t('siteLogo.ruleNameLength', { max: SITE_LOGO_RULE_NAME_MAX });
        return '';
    }

    /**
     * 미리 검사한 날짜 문제를 문구로.
     * @param {Translate} t - 번역 함수.
     * @param {SiteLogoRuleProblems['dates']} problem - 문제.
     * @returns {string} 칸 아래 한 줄(문제가 없으면 빈 글자).
     */
    static datesProblem(t: Translate, problem: SiteLogoRuleProblems['dates']): string {
        if (problem === 'REQUIRED') return t('siteLogo.ruleDatesRequired');
        if (problem === 'ORDER') return t('siteLogo.ruleDatesOrder');
        return '';
    }
}
