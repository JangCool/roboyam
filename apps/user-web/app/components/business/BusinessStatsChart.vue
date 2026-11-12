<template>
    <!--
        광고 성과 날짜별 추이 — 공유 레이어 차트(`StatsTrendChart`, `@irobit/chart` — ECharts)에 이 앱의 문구·숫자 글자를 입히는 얇은 겹.
        신청 건 상세 「성과」 칸(큰 모양 — 막대 = 노출 왼쪽 축 · 선 = 클릭 오른쪽 축, 정지·내림 띠, 오늘 빗금, 범례, 화살표 툴팁, 숨긴 표)과
        사업자 센터 「진행 중 광고」 줄의 최근 7일 노출 미니 막대(`compact`)가 같이 쓴다. 운영자 화면(admin-web `AdStatsChart`)과 같은 모양이다.
    -->
    <StatsTrendChart :days="days" :today="today" :compact="compact" :labels="labels" :format="format" data-testid="business-stats-chart" />
</template>

<script setup lang="ts">
import type { StatsTrendFormat, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { AdStatDay } from '~/composables/business/api/useBusinessApi.types';
import UtilAdStats from '~/utils/util-ad-stats';

interface Props {
    /** 날짜별 숫자(오래된 차례, 기간 안 모든 날) */
    days: AdStatDay[];
    /** 오늘(한국 날짜) — 이 날 막대는 빗금·흐리게. 기간 밖이면 빈 글자 */
    today?: string;
    /** 미니 막대(사업자 센터 줄) — 축·툴팁·범례·초점 없음 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { today: '', compact: false });

const { t, locale } = useI18n();
const time = useDisplayTime();
const display = useDisplayValues();

/** 요약(aria-label) — 큰 모양은 기간·합계·노출이 가장 많은 날, 미니는 7일 노출 합계·가장 많은 날 */
const summary = computed(() => {
    const days = props.days;
    if (!days.length) return t('business.stats.empty');
    const sum = UtilAdStats.sum(days);
    const peak = days.reduce((best, d) => (d.impressions > best.impressions ? d : best), days[0]!);
    if (props.compact) return t('business.stats.sparkSummary', { total: UtilAdStats.count(sum.impressions), peak: UtilAdStats.count(peak.impressions) });
    return t('business.stats.chartSummary', {
        from: time.value.serviceDate(days[0]!.date),
        to: time.value.serviceDate(days[days.length - 1]!.date),
        impressions: UtilAdStats.count(sum.impressions),
        clicks: UtilAdStats.count(sum.clicks),
        peakDate: time.value.serviceDate(peak.date),
        peak: UtilAdStats.count(peak.impressions),
    });
});

const labels = computed<StatsTrendLabels>(() => ({
    summary: summary.value,
    keys: t('business.stats.chartKeyboard'),
    impressions: t('business.stats.impressions'),
    clicks: t('business.stats.clicks'),
    ctr: t('business.stats.ctr'),
    date: t('business.stats.date'),
    state: t('business.stats.state'),
    tableCaption: t('business.stats.trendTitle'),
    today: t('business.stats.todayCounting'),
    states: { SHOWING: t('business.stats.dayState.SHOWING'), PAUSED: t('business.stats.dayState.PAUSED'), TAKEN_DOWN: t('business.stats.dayState.TAKEN_DOWN') },
    legend: t('business.stats.legend'),
    legendImpressions: t('business.stats.legendImpressions'),
    legendClicks: t('business.stats.legendClicks'),
    legendPaused: t('business.stats.legendPaused'),
    legendTakenDown: t('business.stats.legendTakenDown'),
    legendToday: t('business.stats.legendToday'),
}));

const format = computed<StatsTrendFormat>(() => {
    const axis = new Intl.NumberFormat(locale.value, { notation: 'compact', maximumFractionDigits: 1 });
    const values = display.value;
    const tv = time.value;
    return {
        number: (n) => UtilAdStats.count(n),
        axis: (n) => axis.format(n),
        date: (ymd) => tv.serviceDate(ymd),
        shortDate: (ymd) => UtilAdStats.shortDay(ymd, values),
        ctr: (c) => UtilAdStats.ctrText(c),
    };
});
</script>
