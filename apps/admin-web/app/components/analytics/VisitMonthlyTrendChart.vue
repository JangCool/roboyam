<template>
    <!--
        월별 추이 — 공유 레이어 차트(`StatsTrendChart`, `@roboyam/chart` — 방문 추이 `VisitTrendChart` 와 같은 부품)에 월별 문구·숫자 글자를 입히는 얇은 겹(방문 통계 3단계 「월별 보기」).
        막대 = 방문자(일별 합 — 왼쪽 축) · 선 = 고른 숫자(오른쪽 축): 순방문자(MAU) · 신규 방문자 · 페이지뷰. 툴팁 셋째 줄(레이어의 「클릭률」 자리)은 선에 맞춘 비율 —
        순방문자면 평균 방문 일수(일별 합 ÷ MAU), 신규면 신규 비율, 페이지뷰면 1인당 페이지뷰.
        순방문자가 「집계 전」(null)인 달은 선에 빈 칸으로 둔다(NaN — ECharts 가 빈 값으로 다룬다, 글자는 「—」). 진행 중인 달은 빗금·흐리게 「집계 중」.
    -->
    <StatsTrendChart :days="days" :today="current" :labels="labels" :format="format" />
</template>

<script setup lang="ts">
import type { StatsTrendDay, StatsTrendFormat, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { VisitMonthlyLine } from '~/components/analytics/VisitMonthlyTrendChart.types';
import type { VisitMonthlyPoint, VisitMonthRange } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

interface Props {
    months: VisitMonthlyPoint[];
    line: VisitMonthlyLine;
    range: VisitMonthRange;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();

function lineValue(m: VisitMonthlyPoint): number {
    if (props.line === 'NEW') return m.newVisitors;
    if (props.line === 'VIEWS') return m.pageViews;
    return m.uniqueVisitors ?? Number.NaN;
}

const days = computed<StatsTrendDay[]>(() => props.months.map((m) => ({ date: m.month, impressions: m.visitorsDailySum, clicks: lineValue(m) })));

/** 진행 중인 달(이번 달) */
const current = computed(() => props.months.find((m) => m.partial)?.month ?? '');

const labels = computed<StatsTrendLabels>(() => {
    const lang = locale.value;
    const lineName = t(`visitAnalytics.monthly.line.${props.line}`);
    const ratio = props.line === 'UNIQUE' ? t('visitAnalytics.monthly.visitDays') : props.line === 'NEW' ? t('visitAnalytics.monthly.newRate') : t('visitAnalytics.monthly.perVisitor');
    const visitors = props.months.reduce((a, m) => a + m.visitorsDailySum, 0);
    return {
        summary: t('visitAnalytics.monthly.chartSummary', {
            range: `${UtilVisitAnalytics.monthLabel(props.range.from, lang)} ~ ${UtilVisitAnalytics.monthLabel(props.range.to, lang)}`,
            visitors: UtilVisitAnalytics.num(visitors, lang),
            n: props.months.length,
        }),
        keys: t('visitAnalytics.monthly.chartKeys'),
        impressions: t('visitAnalytics.monthly.visitorsDaily'),
        clicks: lineName,
        ctr: ratio,
        date: t('visitAnalytics.monthly.chartMonth'),
        state: '',
        tableCaption: t('visitAnalytics.monthly.chartTableCaption'),
        today: t('visitAnalytics.chartNow'),
        states: { SHOWING: '', PAUSED: '', TAKEN_DOWN: '' },
        legend: t('visitAnalytics.chartLegend'),
        legendImpressions: t('visitAnalytics.monthly.visitorsDaily'),
        legendClicks: lineName,
        legendPaused: '',
        legendTakenDown: '',
        legendToday: t('visitAnalytics.chartNow'),
    };
});

const format = computed<StatsTrendFormat>(() => {
    const lang = locale.value;
    const line = props.line;
    return {
        number: (n) => (Number.isFinite(n) ? UtilVisitAnalytics.num(n, lang) : '—'),
        axis: (n) => UtilVisitAnalytics.compact(n, lang),
        date: (key) => UtilVisitAnalytics.monthLabel(key, lang),
        shortDate: (key) => UtilVisitAnalytics.monthShort(key, lang),
        ctr: (c) => {
            if (!Number.isFinite(c.clicks)) return '—';
            if (line === 'UNIQUE') return UtilVisitAnalytics.decimal(c.clicks > 0 ? c.impressions / c.clicks : null, lang);
            if (line === 'NEW') return UtilVisitAnalytics.percent(c.impressions > 0 ? (c.clicks / c.impressions) * 100 : null, lang);
            return UtilVisitAnalytics.decimal(c.impressions > 0 ? c.clicks / c.impressions : null, lang);
        },
    };
});
</script>
