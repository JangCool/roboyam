<template>
    <!--
        광고 성과 날짜별 추이 — 공유 레이어 차트(`StatsTrendChart`, `@roboyam/chart` — ECharts)에 이 앱의 문구·숫자 글자를 입히는 얇은 겹.
        막대 = 노출(왼쪽 축) · 선 = 클릭(오른쪽 축). 한 건 성과(일정표 서랍 「성과」 탭)는 그날 `state` 가 있어 정지·내림 띠를 깔고, 운영자 합계(`/ads/performance`)는 `state` 가 없어 띠가 없다.
        오늘 막대는 빗금·흐리게(집계 중), 화살표 툴팁·범례·숨긴 표는 레이어가 그린다. 사업자 화면(user-web `BusinessStatsChart`)과 같은 모양이다.
    -->
    <StatsTrendChart :days="days" :today="todayYmd" :labels="labels" :format="format" />
</template>

<script setup lang="ts">
import type { StatsTrendFormat, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { AdStatCounts, AdStatDayState } from '~/composables/ad/api/useAdApi.types';
import UtilAdMessages from '~/utils/util-ad-messages';
import UtilAdStats from '~/utils/util-ad-stats';
import UtilBizApplication from '~/utils/util-biz-application';

/** 차트 날짜 하나 — 한 건 성과는 `state` 가 있고(띠), 운영자 합계는 없다 */
type ChartDay = AdStatCounts & { date: string; state?: AdStatDayState };

interface Props {
    /** 오래된 차례, 빈 날 없이 */
    days: ChartDay[];
    /** 오늘(`YYYY-MM-DD` KST) — 비우면 지금 시각으로 계산 */
    today?: string;
}

const props = withDefaults(defineProps<Props>(), { today: '' });

const { t, locale } = useI18n();
const time = useDisplayTime();

const todayYmd = computed(() => props.today || UtilBizApplication.kstToday());

const labels = computed<StatsTrendLabels>(() => ({
    summary: UtilAdMessages.chartSummary(t, props.days, locale.value, (ymd) => time.value.serviceDate(ymd)),
    keys: t('ad.chartKeys'),
    impressions: t('ad.statImpressions'),
    clicks: t('ad.statClicks'),
    ctr: t('ad.statCtr'),
    date: t('ad.chartDate'),
    state: t('ad.chartStateLabel'),
    tableCaption: t('ad.chartTableCaption'),
    today: t('ad.chartToday'),
    states: { SHOWING: t('ad.chartState.SHOWING'), PAUSED: t('ad.chartState.PAUSED'), TAKEN_DOWN: t('ad.chartState.TAKEN_DOWN') },
    legend: t('ad.chartLegend'),
    legendImpressions: t('ad.chartLegendImpressions'),
    legendClicks: t('ad.chartLegendClicks'),
    legendPaused: t('ad.chartLegendPaused'),
    legendTakenDown: t('ad.chartLegendTakenDown'),
    legendToday: t('ad.chartLegendToday'),
}));

const format = computed<StatsTrendFormat>(() => {
    const lang = locale.value;
    const tv = time.value;
    return {
        number: (n) => UtilAdStats.num(n, lang),
        axis: (n) => UtilAdStats.compact(n, lang),
        date: (ymd) => tv.serviceDate(ymd),
        shortDate: (ymd) => UtilAdStats.shortDay(ymd),
        ctr: (c) => UtilAdStats.ctrText(c, lang),
    };
});
</script>
