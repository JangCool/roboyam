<template>
    <!--
        월별 추이 — 공유 레이어 차트(`StatsTrendChart`, `@irobit/chart` — 방문 추이 `VisitTrendChart` 와 같은 부품)에 월별 문구·숫자 글자를 입히는 얇은 겹(방문 통계 3단계 「월별 보기」).
        막대 = 방문자(일별 합 — 왼쪽 축) · 선 = 고른 숫자(오른쪽 축): 순방문자(MAU) · 신규 방문자 · 페이지뷰. 툴팁 셋째 줄(레이어의 「클릭률」 자리)은 선에 맞춘 비율 —
        순방문자면 평균 방문 일수(일별 합 ÷ MAU — 회원으로 보면 로그인 회원 비율 = 회원 MAU ÷ 방문자 MAU), 신규면 신규 비율, 페이지뷰면 1인당 페이지뷰.
        순방문자 선은 `audience`(방문자 `uniqueVisitors` / 회원 `uniqueMembers` — 결정 ④ 화면 확정 ⓑ)를 따른다.
        순방문자가 「집계 전」(null)인 달은 선에 빈 칸으로 둔다(NaN — ECharts 가 빈 값으로 다룬다, 글자는 「—」). 진행 중인 달은 빗금·흐리게 「집계 중」.
    -->
    <StatsTrendChart :days="days" :today="current" :labels="labels" :format="format" />
</template>

<script setup lang="ts">
import type { StatsTrendDay, StatsTrendFormat, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { VisitMonthlyLine } from '~/components/analytics/VisitMonthlyTrendChart.types';
import type { VisitAudience, VisitMonthlyPoint, VisitMonthRange } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

interface Props {
    months: VisitMonthlyPoint[];
    line: VisitMonthlyLine;
    range: VisitMonthRange;
    /** 순방문자 선의 대상 — 방문자 / 회원 */
    audience?: VisitAudience;
}

const props = withDefaults(defineProps<Props>(), { audience: 'VISITOR' });

const members = computed(() => props.audience === 'MEMBER');

const { t, locale } = useI18n();

function lineValue(m: VisitMonthlyPoint): number {
    if (props.line === 'NEW') return m.newVisitors;
    if (props.line === 'VIEWS') return m.pageViews;
    return (members.value ? m.uniqueMembers : m.uniqueVisitors) ?? Number.NaN;
}

/** 달 → 방문자 MAU(회원으로 볼 때 로그인 회원 비율의 분모) */
const visitorMau = computed(() => new Map(props.months.map((m) => [m.month, m.uniqueVisitors])));

const days = computed<StatsTrendDay[]>(() => props.months.map((m) => ({ date: m.month, impressions: m.visitorsDailySum, clicks: lineValue(m) })));

/** 진행 중인 달(이번 달) */
const current = computed(() => props.months.find((m) => m.partial)?.month ?? '');

const labels = computed<StatsTrendLabels>(() => {
    const lang = locale.value;
    const lineName = props.line === 'UNIQUE' && members.value ? t('visitAnalytics.monthly.uniqueMembers') : t(`visitAnalytics.monthly.line.${props.line}`);
    const ratio = props.line === 'UNIQUE' ? t(members.value ? 'visitAnalytics.monthly.memberShare' : 'visitAnalytics.monthly.visitDays') : props.line === 'NEW' ? t('visitAnalytics.monthly.newRate') : t('visitAnalytics.monthly.perVisitor');
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
    const byMember = members.value;
    const mau = visitorMau.value;
    return {
        number: (n) => (Number.isFinite(n) ? UtilVisitAnalytics.num(n, lang) : '—'),
        axis: (n) => UtilVisitAnalytics.compact(n, lang),
        date: (key) => UtilVisitAnalytics.monthLabel(key, lang),
        shortDate: (key) => UtilVisitAnalytics.monthShort(key, lang),
        ctr: (c) => {
            if (!Number.isFinite(c.clicks)) return '—';
            if (line === 'UNIQUE' && byMember) {
                // 차트 레이어는 그 칸(`StatsTrendDay`)을 그대로 넘긴다 — 달 글자가 없으면 「—」
                const month = 'date' in c && typeof c.date === 'string' ? c.date : '';
                const visitors = mau.get(month) ?? null;
                return UtilVisitAnalytics.percent(visitors ? (c.clicks / visitors) * 100 : null, lang);
            }
            if (line === 'UNIQUE') return UtilVisitAnalytics.decimal(c.clicks > 0 ? c.impressions / c.clicks : null, lang);
            if (line === 'NEW') return UtilVisitAnalytics.percent(c.impressions > 0 ? (c.clicks / c.impressions) * 100 : null, lang);
            return UtilVisitAnalytics.decimal(c.impressions > 0 ? c.clicks / c.impressions : null, lang);
        },
    };
});
</script>
