import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { WeekHourLabels } from '#layers/chart/components/WeekHourHeatmap.types';
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import { VISIT_REFERRER_GROUPS, type VisitRange, type VisitReferrerGroup, type VisitSummary } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import type { VisitTimeMetric, VisitTimePeak } from '~/utils/util-visit-time';

/** 날짜 글자 함수(표시 값의 날짜 형식 — `useDisplayTime().serviceDate`) */
type DateText = (ymd: string) => string;

/**
 * 방문 분석 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('visitAnalytics.*')` 를 부른다(`i18n.md` 3절).
 */
export default class UtilVisitAnalyticsMessages {
    /**
     * 기간 글자 — 하루면 그 날짜, 아니면 `시작 ~ 끝`.
     * @param {VisitRange} range - 기간.
     * @param {DateText} date - 날짜 글자 함수.
     * @returns {string} 글자.
     */
    static range(range: VisitRange, date: DateText): string {
        return range.from === range.to ? date(range.from) : `${date(range.from)} ~ ${date(range.to)}`;
    }

    /**
     * 증감 글자(스크린 리더·툴팁) — 「12.3% 늘었어요」 · 「변화 없음」 · 「비교할 값 없음」.
     * @param {Translate} t - 번역 함수.
     * @param {number | null} change - 증감(%) — null 이면 비교할 수 없음.
     * @param {string} locale - 화면 언어.
     * @returns {string} 글자.
     */
    static delta(t: Translate, change: number | null, locale: string): string {
        if (change === null) return t('visitAnalytics.deltaNone');
        const rate = UtilVisitAnalytics.percent(Math.abs(change), locale);
        if (rate === UtilVisitAnalytics.percent(0, locale)) return t('visitAnalytics.deltaSame');
        return t(change > 0 ? 'visitAnalytics.deltaUp' : 'visitAnalytics.deltaDown', { rate });
    }

    /**
     * 비교 기준 글자 — 「전날 대비」 · 「직전 7일 대비」(+ 오늘이 끼면 「· 같은 시각까지」).
     * @param {Translate} t - 번역 함수.
     * @param {VisitRange} range - 지금 기간.
     * @param {boolean} includesToday - 기간에 오늘이 끼나.
     * @returns {string} 글자.
     */
    static period(t: Translate, range: VisitRange): string {
        const preset = UtilVisitAnalytics.presetOf(range);
        return t(`visitAnalytics.period.${preset ?? 'CUSTOM'}`);
    }

    /**
     * 한 줄 요약 — 방문자 증감. 「최근 7일 방문자는 직전 7일보다 12.3% 늘었어요.」 · 1% 안이면 「비슷해요」 · 직전이 0 이면 숫자만 · 방문 0 이면 「아직 없어요」.
     * @param {Translate} t - 번역 함수.
     * @param {VisitSummary} s - 요약.
     * @param {string} locale - 화면 언어.
     * @returns {string} 문장.
     */
    static visitorsInsight(t: Translate, s: VisitSummary, locale: string): string {
        const period = UtilVisitAnalyticsMessages.period(t, s.range);
        if (!s.totals.visitors) return t('visitAnalytics.insightNone', { period });
        const change = UtilVisitAnalytics.change(s.totals.visitors, s.previous.visitors);
        if (change === null) return t('visitAnalytics.insightOnly', { period, n: UtilVisitAnalytics.num(s.totals.visitors, locale) });
        const days = UtilVisitAnalytics.days(s.range);
        const today = UtilVisitAnalytics.includesToday(s.range);
        const base = days === 1 ? (today ? t('visitAnalytics.baseSameTimeYesterday') : t('visitAnalytics.baseDayBefore')) : t('visitAnalytics.basePrevDays', { n: days });
        if (Math.abs(change) < 1) return t('visitAnalytics.insightFlat', { period, base });
        const rate = UtilVisitAnalytics.percent(Math.abs(change), locale, 0);
        return t(change > 0 ? 'visitAnalytics.insightUp' : 'visitAnalytics.insightDown', { period, base, rate });
    }

    /**
     * 「목요일 21시」
     * @param {Translate} t - 번역 함수.
     * @param {number} weekday - 1(월)~7(일).
     * @param {number} hour - 0~23.
     */
    static when(t: Translate, weekday: number, hour: number): string {
        return t('visitAnalytics.when', { weekday: t(`visitAnalytics.weekdayLong.${weekday}`), hour });
    }

    /**
     * 유입 분류 막대 줄 — 다섯 분류 늘 다(빠진 것은 0), 많은 차례, 분류마다 정해진 색.
     * @param {Translate} t - 번역 함수.
     * @param groups - 서버 분류 값.
     */
    static groupRows(t: Translate, groups: { group: VisitReferrerGroup; visits: number }[]): VisitBarRow[] {
        const by = new Map(groups.map((g) => [g.group, g.visits]));
        return VISIT_REFERRER_GROUPS.map((group) => ({ key: group, label: t(`dashboard.referrerGroup.${group}`), value: by.get(group) ?? 0, color: UtilVisitAnalytics.GROUP_COLORS[group] })).sort((a, b) => b.value - a.value);
    }

    /**
     * 히트맵 문구 묶음.
     * @param {Translate} t - 번역 함수.
     * @param {VisitTimeMetric} metric - 볼 숫자.
     * @param {string} rangeText - 기간 글자.
     * @param {VisitTimePeak | null} busiest - 가장 붐비는 칸.
     */
    static heatLabels(t: Translate, metric: VisitTimeMetric, rangeText: string, busiest: VisitTimePeak | null): WeekHourLabels {
        return {
            summary: t('visitAnalytics.heatSummary', { range: rangeText, when: busiest ? UtilVisitAnalyticsMessages.when(t, busiest.weekday, busiest.hour) : '—' }),
            tableCaption: t('visitAnalytics.heatTableCaption'),
            rows: [1, 2, 3, 4, 5, 6, 7].map((d) => t(`visitAnalytics.weekday.${d}`)),
            cols: Array.from({ length: 24 }, (_, h) => t('visitAnalytics.hourN', { n: h })),
            value: metric === 'visitors' ? t('visitAnalytics.heatValueVisitors') : t('visitAnalytics.heatValuePageViews'),
            none: t('visitAnalytics.heatNone'),
            low: t('visitAnalytics.heatLow'),
            high: t('visitAnalytics.heatHigh'),
        };
    }

    static compareBase(t: Translate, range: VisitRange, includesToday: boolean): string {
        const days = UtilVisitAnalytics.days(range);
        const base = days === 1 ? t('visitAnalytics.vsPreviousDay') : t('visitAnalytics.vsPrevious', { n: days });
        return includesToday ? `${base} · ${t('visitAnalytics.vsPreviousSameTime')}` : base;
    }
}
