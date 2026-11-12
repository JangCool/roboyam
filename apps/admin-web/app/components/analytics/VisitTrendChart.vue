<template>
    <!--
        방문 추이 — 공유 레이어 차트(`StatsTrendChart`, `@irobit/chart` — 광고 성과와 같은 부품)에 방문 문구·숫자 글자를 입히는 얇은 겹.
        막대 = 방문자(왼쪽 축) · 선 = 페이지뷰(오른쪽 축) · 툴팁 셋째 줄 = 1인당 페이지뷰(레이어의 「클릭률」 칸 자리 — 선 ÷ 막대).
        하루 기간이면 시간별(칸 키 = 정시 ISO, 축 글자 「13시」 — 한국 시각), 여러 날이면 일별. 오늘(지금 시각의 칸 · 오늘 날짜)은 빗금·흐리게 「집계 중」. 상태 띠는 없다.
    -->
    <StatsTrendChart :days="days" :today="current" :labels="labels" :format="format" />
</template>

<script setup lang="ts">
import type { StatsTrendDay, StatsTrendFormat, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { VisitGranularity, VisitRange, VisitSeriesPoint } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';

interface Props {
    series: VisitSeriesPoint[];
    granularity: VisitGranularity;
    range: VisitRange;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const time = useDisplayTime();

const KST = 9 * 3_600_000;

/** 정시 ISO → 한국 날짜·시 */
function kstParts(iso: string): { ymd: string; hour: number } {
    const d = new Date(Date.parse(iso) + KST);
    return { ymd: d.toISOString().slice(0, 10), hour: d.getUTCHours() };
}

const days = computed<StatsTrendDay[]>(() => props.series.map((p) => ({ date: p.at, impressions: p.visitors, clicks: p.pageViews })));

/** 집계 중인 칸 — 시간별이면 오늘의 마지막 칸, 일별이면 오늘 날짜 */
const current = computed(() => {
    if (!UtilVisitAnalytics.includesToday(props.range)) return '';
    if (props.granularity === 'DAY') return UtilVisitAnalytics.today();
    return props.series[props.series.length - 1]?.at ?? '';
});

const labels = computed<StatsTrendLabels>(() => {
    const lang = locale.value;
    const visitors = props.series.reduce((a, p) => a + p.visitors, 0);
    const views = props.series.reduce((a, p) => a + p.pageViews, 0);
    return {
        summary: t('visitAnalytics.chartSummary', {
            range: UtilVisitAnalyticsMessages.range(props.range, (d) => time.value.serviceDate(d)),
            visitors: UtilVisitAnalytics.num(visitors, lang),
            views: UtilVisitAnalytics.num(views, lang),
            n: props.series.length,
        }),
        keys: t('visitAnalytics.chartKeys'),
        impressions: t('visitAnalytics.visitors'),
        clicks: t('visitAnalytics.pageViews'),
        ctr: t('visitAnalytics.perVisitor'),
        date: props.granularity === 'HOUR' ? t('visitAnalytics.chartTime') : t('visitAnalytics.chartDate'),
        state: '',
        tableCaption: t('visitAnalytics.chartTableCaption'),
        today: t('visitAnalytics.chartNow'),
        states: { SHOWING: '', PAUSED: '', TAKEN_DOWN: '' },
        legend: t('visitAnalytics.chartLegend'),
        legendImpressions: t('visitAnalytics.visitors'),
        legendClicks: t('visitAnalytics.pageViews'),
        legendPaused: '',
        legendTakenDown: '',
        legendToday: t('visitAnalytics.chartNow'),
    };
});

const format = computed<StatsTrendFormat>(() => {
    const lang = locale.value;
    const tv = time.value;
    const hourly = props.granularity === 'HOUR';
    return {
        number: (n) => UtilVisitAnalytics.num(n, lang),
        axis: (n) => UtilVisitAnalytics.compact(n, lang),
        date: (key) => {
            if (!hourly) return tv.serviceDate(key);
            const p = kstParts(key);
            return `${tv.serviceDate(p.ymd)} ${String(p.hour).padStart(2, '0')}:00`;
        },
        shortDate: (key) => {
            if (!hourly) return `${Number(key.slice(5, 7))}/${Number(key.slice(8, 10))}`;
            return t('visitAnalytics.hourN', { n: kstParts(key).hour });
        },
        ctr: (c) => UtilVisitAnalytics.decimal(c.impressions > 0 ? c.clicks / c.impressions : null, lang),
    };
});
</script>
