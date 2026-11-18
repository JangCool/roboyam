import type {
    SiteLogoAnniversaryRule,
    SiteLogoBundle,
    SiteLogoHolidayOption,
    SiteLogoRules,
    SiteLogoStyle,
} from '~/composables/site/api/useSiteLogoLibraryApi.types';
import { SITE_LOGO_RULE_NAME_MAX } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogo, { type SiteLogoSet } from '~/utils/util-site-logo';

/**
 * 로고 보이기 규칙 — 어느 규칙이 걸리는지·묶음이 어디에 쓰이는지·기념일 줄 검사를 하는 순수 함수 한 곳. 상태도 요청도 없다.
 * 우선순위: 기념일(위 줄이 먼저, 사용 켬인 줄만) → 성별(로그인한 회원만) → 기본 로고.
 * ⚠ 실제로 어떤 로고를 보일지는 서버·공개 사이트가 정한다(API 단계) — 여기 계산은 관리 화면의 「오늘 보이는 로고」 요약과 미리 검사용이다.
 */

/** 기념일 줄을 미리 검사한 문제 — 칸마다 하나 */
export interface SiteLogoRuleProblems {
    name?: 'REQUIRED' | 'LENGTH';
    dates?: 'REQUIRED' | 'ORDER';
    holiday?: 'REQUIRED';
    bundle?: 'REQUIRED';
}

/** 묶음이 쓰이는 곳 */
export interface SiteLogoBundleUsage {
    /** 기본 로고인가 */
    isDefault: boolean;
    /** 이 묶음을 가리키는 규칙 수(기념일 줄 + 성별 칸) */
    ruleCount: number;
}

/** 기념일 로고 한 날의 두 벌 — 캐릭터 벌(귀여운 버전)·글자 벌(일반 버전). 한쪽이 없으면 null */
export interface SiteLogoDayPair {
    dayKey: string;
    /** 날 이름 — 묶음 이름에서 벌 표시를 뗀 것 */
    name: string;
    character: SiteLogoBundle | null;
    wordmark: SiteLogoBundle | null;
}

/** 보관함의 세 묶음 */
export interface SiteLogoBundleGroups {
    usual: SiteLogoBundle[];
    days: SiteLogoDayPair[];
    uploaded: SiteLogoBundle[];
}

const YMD =/^\d{4}-\d{2}-\d{2}$/;
const MD = /^\d{2}-\d{2}$/;

function pad(n: number): string {
    return String(n).padStart(2, '0');
}

export default class UtilSiteLogoRule {
    /**
     * 채워진 칸 수(0~4).
     * @param {SiteLogoSet} logos - 4칸 값.
     * @returns {number} 그림이 있는 칸 수.
     */
    static filledCount(logos: SiteLogoSet): number {
        return UtilSiteLogo.VARIANTS.filter((variant) => Boolean(logos[variant])).length;
    }

    /**
     * 묶음 카드에 보일 그림 — 밝은 바탕 한글 칸, 비었으면 채워진 첫 칸.
     * @param {SiteLogoSet} logos - 4칸 값.
     * @returns {string | null} 그림 주소, 없으면 null.
     */
    static cardPreview(logos: SiteLogoSet): string | null {
        if (logos.koLight) return logos.koLight;
        for (const variant of UtilSiteLogo.VARIANTS) if (logos[variant]) return logos[variant];
        return null;
    }

    /**
     * 묶음이 쓰이는 곳을 센다.
     * @param {SiteLogoRules} rules - 보이기 규칙.
     * @param {string} bundleId - 묶음 id.
     * @returns {SiteLogoBundleUsage} 기본 로고 여부와 가리키는 규칙 수.
     */
    static usage(rules: SiteLogoRules, bundleId: string): SiteLogoBundleUsage {
        const anniversaries = rules.anniversaries.filter((rule) => rule.bundleId === bundleId).length;
        const gender = Number(rules.gender.male === bundleId) + Number(rules.gender.female === bundleId);
        return { isDefault: rules.defaultBundleId === bundleId, ruleCount: anniversaries + gender };
    }

    /**
     * `YYYY-MM-DD` → 월·일(`MM-DD`).
     * @param {string} ymd - 달력 날짜.
     * @returns {string} `MM-DD`, 모양이 아니면 빈 글자.
     */
    static monthDayOf(ymd: string): string {
        return YMD.test(ymd) ? ymd.slice(5) : '';
    }

    /**
     * 월·일 숫자 → `MM-DD`.
     * @param {number} month - 월(1~12).
     * @param {number} day - 일(1~31).
     * @returns {string} `MM-DD`.
     */
    static monthDay(month: number, day: number): string {
        return `${pad(month)}-${pad(day)}`;
    }

    /**
     * `MM-DD` → 월·일 숫자.
     * @param {string | null} md - `MM-DD`.
     * @returns {{ month: number; day: number } | null} 모양이 아니면 null.
     */
    static parseMonthDay(md: string | null): { month: number; day: number } | null {
        if (!md || !MD.test(md)) return null;
        return { month: Number(md.slice(0, 2)), day: Number(md.slice(3)) };
    }

    /**
     * 그 달에 고를 수 있는 마지막 날(매년 반복이라 2월은 29일까지).
     * @param {number} month - 월(1~12).
     * @returns {number} 마지막 날.
     */
    static lastDayOf(month: number): number {
        if (month === 2) return 29;
        return [4, 6, 9, 11].includes(month) ? 30 : 31;
    }

    /**
     * 기념일 줄이 오늘 걸리는가(사용 켬·끔은 보지 않는다).
     * @param {SiteLogoAnniversaryRule} rule - 기념일 줄.
     * @param {string} today - 오늘(`YYYY-MM-DD`).
     * @param {readonly SiteLogoHolidayOption[]} holidays - 쉬는 날 표(이름과 날짜들).
     * @returns {boolean} 오늘이 그 줄의 날이면 true.
     */
    static matches(rule: SiteLogoAnniversaryRule, today: string, holidays: readonly SiteLogoHolidayOption[]): boolean {
        if (rule.mode === 'HOLIDAY') {
            if (!rule.holidayName) return false;
            return holidays.some((holiday) => holiday.name === rule.holidayName && holiday.dates.includes(today));
        }
        if (!rule.startDate || !rule.endDate) return false;
        if (rule.yearly) {
            const md = UtilSiteLogoRule.monthDayOf(today);
            // 끝이 시작보다 앞이면 해를 넘기는 기간(12-30 ~ 01-02)
            if (rule.startDate <= rule.endDate) return rule.startDate <= md && md <= rule.endDate;
            return md >= rule.startDate || md <= rule.endDate;
        }
        return rule.startDate <= today && today <= rule.endDate;
    }

    /**
     * 오늘 걸리는 기념일 줄 — 사용 켬인 줄 가운데 맨 위.
     * @param {SiteLogoRules} rules - 보이기 규칙.
     * @param {string} today - 오늘(`YYYY-MM-DD`).
     * @param {readonly SiteLogoHolidayOption[]} holidays - 쉬는 날 표.
     * @returns {SiteLogoAnniversaryRule | null} 걸린 줄, 없으면 null.
     */
    static todayRule(rules: SiteLogoRules, today: string, holidays: readonly SiteLogoHolidayOption[]): SiteLogoAnniversaryRule | null {
        return rules.anniversaries.find((rule) => rule.enabled && UtilSiteLogoRule.matches(rule, today, holidays)) ?? null;
    }

    /**
     * 기본 제공 기념일 로고에서 실제로 쓰이는 벌 — 「기념일 로고 유형」(`rules.holidayStyle`)을 따른다.
     * `CHARACTER`·`WORDMARK` 면 그 벌, `AUTO` 면 **그 사람이 평소에 보는 로고**(성별 로고가 걸리면 그것, 아니면 기본 로고)의 벌 —
     * 그 로고의 벌이 `WORDMARK` 면 글자 벌, 그 밖에는(캐릭터 로고·올린 묶음) 캐릭터 벌.
     * @param {SiteLogoRules} rules - 보이기 규칙(초안을 주면 저장 전 미리보기가 된다).
     * @param {readonly SiteLogoBundle[]} bundles - 보관함의 묶음.
     * @param {string | null} [baseBundleId] - 그 사람이 평소에 보는 로고(성별 로고)의 id. 비우면 기본 로고(로그인하지 않은 방문자).
     * @returns {SiteLogoStyle} 쓰이는 벌.
     */
    static holidayStyle(rules: SiteLogoRules, bundles: readonly SiteLogoBundle[], baseBundleId: string | null = null): SiteLogoStyle {
        if (rules.holidayStyle === 'CHARACTER' || rules.holidayStyle === 'WORDMARK') return rules.holidayStyle;
        const base = bundles.find((item) => item.id === (baseBundleId ?? rules.defaultBundleId)) ?? null;
        return base?.style === 'WORDMARK' ? 'WORDMARK' : 'CHARACTER';
    }

    /**
     * 기념일 로고의 두 벌 가운데 고르기 — 서버 `SiteLogoResolver.matchStyle` 과 같은 규칙.
     * 기념일 규칙이 가리킨 묶음이 기본 제공 기념일 묶음(`dayKey`·`style` 이 있다)이면 **쓰이는 벌**(`holidayStyle`)에 맞는 짝으로 바꾼다.
     * 올린 묶음·기본 로고를 가리키면 그대로다. 짝이 없으면 그대로.
     * @param {SiteLogoBundle | null} bundle - 규칙이 가리킨 묶음.
     * @param {SiteLogoStyle} wanted - 쓰이는 벌(`holidayStyle` 의 답).
     * @param {readonly SiteLogoBundle[]} bundles - 보관함의 묶음.
     * @returns {SiteLogoBundle | null} 실제로 보일 묶음.
     */
    static matchStyle(bundle: SiteLogoBundle | null, wanted: SiteLogoStyle, bundles: readonly SiteLogoBundle[]): SiteLogoBundle | null {
        if (!bundle || !bundle.dayKey || !bundle.style) return bundle;
        if (bundle.style === wanted) return bundle;
        return bundles.find((item) => item.dayKey === bundle.dayKey && item.style === wanted) ?? bundle;
    }

    /**
     * 기념일 규칙 줄이 실제로 보일 묶음 — 가리킨 묶음에 `matchStyle` 을 먹인 것.
     * @param {SiteLogoRules} rules - 보이기 규칙(기념일 로고 유형·기본 로고를 여기서 읽는다 — 초안을 주면 저장 전 미리보기가 된다).
     * @param {string | null} bundleId - 줄이 가리킨 묶음 id.
     * @param {readonly SiteLogoBundle[]} bundles - 보관함의 묶음.
     * @param {string | null} [baseBundleId] - 보는 사람이 평소에 보는 로고(성별 로고)의 id. 비우면 기본 로고(로그인하지 않은 방문자).
     * @returns {SiteLogoBundle | null} 보일 묶음, 없는 id 면 null.
     */
    static shownBundle(rules: SiteLogoRules, bundleId: string | null, bundles: readonly SiteLogoBundle[], baseBundleId: string | null = null): SiteLogoBundle | null {
        const bundle = bundles.find((item) => item.id === bundleId) ?? null;
        return UtilSiteLogoRule.matchStyle(bundle, UtilSiteLogoRule.holidayStyle(rules, bundles, baseBundleId), bundles);
    }

    /**
     * 오늘 보이는 로고(로그인하지 않은 방문자 기준) — 걸린 기념일 줄이 있으면 그 줄의 보일 묶음, 없으면 기본 로고.
     * @param {SiteLogoRules} rules - 보이기 규칙.
     * @param {readonly SiteLogoBundle[]} bundles - 보관함의 묶음.
     * @param {string} today - 오늘(`YYYY-MM-DD`).
     * @param {readonly SiteLogoHolidayOption[]} holidays - 쉬는 날 표.
     * @returns {{ rule: SiteLogoAnniversaryRule | null; bundle: SiteLogoBundle | null }} 걸린 줄(없으면 null)과 보일 묶음.
     */
    static todayBundle(rules: SiteLogoRules, bundles: readonly SiteLogoBundle[], today: string, holidays: readonly SiteLogoHolidayOption[]): { rule: SiteLogoAnniversaryRule | null; bundle: SiteLogoBundle | null } {
        const rule = UtilSiteLogoRule.todayRule(rules, today, holidays);
        return { rule, bundle: UtilSiteLogoRule.shownBundle(rules, rule ? rule.bundleId : rules.defaultBundleId, bundles) };
    }

    /**
     * 기념일 묶음 이름에서 벌 표시를 뗀 날 이름 — 「삼일절 (캐릭터)」 → 「삼일절」. 날 이름의 원천은 서버가 준 묶음 이름이다.
     * @param {string} name - 묶음 이름.
     * @returns {string} 끝의 괄호 꼬리를 뗀 이름(꼬리가 없으면 그대로).
     */
    static dayName(name: string): string {
        return name.replace(/\s*[(（][^()（）]*[)）]\s*$/, '').trim() || name;
    }

    /**
     * 보관함을 세 묶음으로 나눈다 — 기본 로고(기본 제공, 날 키 없음) · 기념일 로고(날마다 캐릭터 벌·글자 벌 짝, 서버가 준 차례) · 올린 로고.
     * @param {readonly SiteLogoBundle[]} bundles - 보관함의 묶음.
     * @returns {SiteLogoBundleGroups} 세 묶음.
     */
    static groups(bundles: readonly SiteLogoBundle[]): SiteLogoBundleGroups {
        const usual: SiteLogoBundle[] = [];
        const uploaded: SiteLogoBundle[] = [];
        const days: SiteLogoDayPair[] = [];
        const byKey = new Map<string, SiteLogoDayPair>();
        for (const bundle of bundles) {
            if (!bundle.builtIn) {
                uploaded.push(bundle);
            } else if (!bundle.dayKey) {
                usual.push(bundle);
            } else {
                let pair = byKey.get(bundle.dayKey);
                if (!pair) {
                    pair = { dayKey: bundle.dayKey, name: UtilSiteLogoRule.dayName(bundle.name), character: null, wordmark: null };
                    byKey.set(bundle.dayKey, pair);
                    days.push(pair);
                }
                if (bundle.style === 'WORDMARK') pair.wordmark = bundle;
                else pair.character = bundle;
            }
        }
        return { usual, days, uploaded };
    }

    /**
     * 기념일 줄을 미리 검사한다(판정은 서버).
     * @param {SiteLogoAnniversaryRule} rule - 기념일 줄.
     * @param {readonly SiteLogoBundle[]} bundles - 보관함의 묶음.
     * @returns {SiteLogoRuleProblems} 칸별 문제 — 없으면 빈 객체.
     */
    static validate(rule: SiteLogoAnniversaryRule, bundles: readonly SiteLogoBundle[]): SiteLogoRuleProblems {
        const problems: SiteLogoRuleProblems = {};
        const name = rule.name.trim();
        if (!name) problems.name = 'REQUIRED';
        else if (name.length > SITE_LOGO_RULE_NAME_MAX) problems.name = 'LENGTH';
        if (rule.mode === 'HOLIDAY') {
            if (!rule.holidayName) problems.holiday = 'REQUIRED';
        } else if (!rule.startDate || !rule.endDate) {
            problems.dates = 'REQUIRED';
        } else if (!rule.yearly && rule.endDate < rule.startDate) {
            problems.dates = 'ORDER';
        }
        if (!bundles.some((bundle) => bundle.id === rule.bundleId)) problems.bundle = 'REQUIRED';
        return problems;
    }

    /**
     * 두 규칙 값이 같은가(저장할 것이 있는지).
     * @param {SiteLogoRules} a - 한쪽.
     * @param {SiteLogoRules} b - 다른 쪽.
     * @returns {boolean} 같으면 true.
     */
    static isSame(a: SiteLogoRules, b: SiteLogoRules): boolean {
        // 사본으로 열쇠 차례를 맞춘 뒤 견준다(서버 응답과 초안의 열쇠 차례가 달라도 같은 값이면 같다)
        return JSON.stringify(UtilSiteLogoRule.copy(a)) === JSON.stringify(UtilSiteLogoRule.copy(b));
    }

    /**
     * 규칙의 평범한 사본(반응형 껍데기 없이 — 서버로 보내거나 초안으로 쓴다).
     * @param {SiteLogoRules} rules - 보이기 규칙.
     * @returns {SiteLogoRules} 깊은 사본.
     */
    static copy(rules: SiteLogoRules): SiteLogoRules {
        return {
            defaultBundleId: rules.defaultBundleId,
            anniversaries: rules.anniversaries.map((rule) => ({ ...rule })),
            gender: { male: rules.gender.male, female: rules.gender.female },
            holidayStyle: rules.holidayStyle,
        };
    }
}
