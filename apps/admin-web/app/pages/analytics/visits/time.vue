<template>
    <!--
        방문자 통계 › 요일·시간대(`/analytics/visits/time`) — 요일(월~일) × 시(0~23) 히트맵(`WeekHourHeatmap`, 방문자/페이지뷰 전환) + 가장 붐비는·한산한 때 카드
        → 시간대별 평균(세로 막대 24) | 요일별 하루 평균(막대 7).
        평균 = 기간 안 같은 요일·시 칸의 합 ÷ 칸 수(`days`). 오늘의 지금 시간(집계 중)과 그 뒤는 서버가 칸에 넣지 않는다 — 덜 찬 칸이 평균을 끌어내리지 않게(`UtilVisitTime`).
        원천 `analytics_visit_hourly`(방문 수집) — 꺼져 있으면 「수집이 꺼져 있어요」. 권한: `site.read`(메뉴 `visitTime`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('visitAnalytics.timeTitle')" :subtitle="t('visitAnalytics.timeSubtitle')" :as-of="timeLoad.data.value?.asOf ?? s?.asOf ?? null" :busy="busy" :range="allowed" :insights="insights" @refresh="loadAll" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="summary.error.value || timeLoad.error.value" :title="t('visitAnalytics.heatmapTitle')">
            <ErrorState :title="summary.error.value ? t('visitAnalytics.summaryLoadFailed') : t('visitAnalytics.timeLoadFailed')" :description="(summary.error.value ?? timeLoad.error.value)?.message" retry :retrying="busy" @retry="loadAll" />
        </AppGadget>
        <Skeleton v-else-if="!s || (s.collect.visits && !cells)" height="20rem" border-radius="0.75rem" />
        <AppGadget v-else-if="!s.collect.visits || !cells" :title="t('visitAnalytics.heatmapTitle')">
            <VisitCollectOff item="visits" :enabled="s.collect.enabled" />
        </AppGadget>
        <AppGadget v-else-if="!busiest" :title="t('visitAnalytics.heatmapTitle')">
            <EmptyState icon="pi pi-clock" :title="t('visitAnalytics.timeEmpty')" :description="t('visitAnalytics.trendEmptyHint')" />
        </AppGadget>

        <template v-else>
            <div class="grid min-w-0 items-start gap-4 transition-opacity xl:grid-cols-4" :class="{ 'opacity-60': timeLoad.loading.value }" :aria-busy="timeLoad.loading.value">
                <AppGadget :title="t('visitAnalytics.heatmapTitle')" class="xl:col-span-3">
                    <template #actions>
                        <SelectButton v-model="metric" :options="metricOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('visitAnalytics.metricLabel')" />
                    </template>
                    <div class="flex flex-col gap-2">
                        <p class="m-0 text-[12px] text-muted-color">{{ t('visitAnalytics.heatmapHint') }}</p>
                        <WeekHourHeatmap :cells="heatCells" :labels="heatLabels" :format="decimalFormat" />
                    </div>
                </AppGadget>
                <div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-1">
                    <div class="flex flex-col gap-1 rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3.5">
                        <span class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-arrow-up-right text-[10px] text-primary" aria-hidden="true" />{{ t('visitAnalytics.busiest') }}</span>
                        <span class="text-lg font-semibold">{{ UtilVisitAnalyticsMessages.when(t, busiest.weekday, busiest.hour) }}</span>
                        <span class="text-[12px] text-muted-color tabular-nums">{{ heatLabels.value }} {{ UtilVisitAnalytics.decimal(busiest.value, locale) }}</span>
                    </div>
                    <div v-if="quietest" class="flex flex-col gap-1 rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3.5">
                        <span class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-arrow-down-right text-[10px]" aria-hidden="true" />{{ t('visitAnalytics.quietest') }}</span>
                        <span class="text-lg font-semibold">{{ UtilVisitAnalyticsMessages.when(t, quietest.weekday, quietest.hour) }}</span>
                        <span class="text-[12px] text-muted-color tabular-nums">{{ heatLabels.value }} {{ UtilVisitAnalytics.decimal(quietest.value, locale) }}</span>
                    </div>
                </div>
            </div>

            <!-- 두 카드는 같은 높이(사용자 결정 2026-11-04) — 시간대 카드 아래엔 하루를 넷으로 나눈 비중을 더해 빈 자리를 정보로 채운다 -->
            <div class="grid min-w-0 items-stretch gap-4 lg:grid-cols-5">
                <AppGadget :title="t('visitAnalytics.hourAvgTitle')" fill class="lg:col-span-3">
                    <div class="flex h-full flex-col gap-4">
                        <VisitColumnBars :items="hourItems" :summary="t('visitAnalytics.hourAvgSummary')" :format="decimalFormat.number" :none-text="t('visitAnalytics.heatNone')" :label-every="3" :height="140" />
                        <ul class="m-0 mt-auto grid list-none grid-cols-2 gap-2 p-0 sm:grid-cols-4" :aria-label="t('visitAnalytics.hourBandsLabel')">
                            <li v-for="b in hourBands" :key="b.key" class="flex flex-col gap-0.5 rounded-lg border px-3 py-2" :class="b.top ? 'border-primary-300 bg-primary-50/60 dark:border-primary-700 dark:bg-primary-950/30' : 'border-(--line)'">
                                <span class="text-[12px] text-muted-color">{{ t(`visitAnalytics.hourBand.${b.key}`) }}</span>
                                <span class="text-base font-semibold tabular-nums">{{ b.shareText }}</span>
                                <span class="text-[11px] text-muted-color tabular-nums">{{ t('visitAnalytics.hourBandAvg', { n: b.avgText }) }}</span>
                            </li>
                        </ul>
                    </div>
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.weekdayAvgTitle')" fill class="lg:col-span-2">
                    <VisitBarList :rows="weekdayRows" />
                </AppGadget>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';
import UtilVisitTime, { type VisitTimeMetric } from '~/utils/util-visit-time';

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('visitAnalytics.timeTitle')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const time = useDisplayTime();
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));

const summary = useVisitLoad(api.summary);
const timeLoad = useVisitLoad(api.time);
const s = computed(() => summary.data.value);
const cells = computed(() => timeLoad.data.value?.cells ?? null);
const busy = computed(() => summary.loading.value || timeLoad.loading.value);

async function loadAll(): Promise<void> {
    const range = { from: vr.range.value.from, to: vr.range.value.to };
    const found = await summary.load(range);
    if (!found) return;
    if (found.collect.visits) await timeLoad.load(range);
    else timeLoad.clear();
}

vr.onRange(() => void loadAll(), allowed);

const metric = ref<VisitTimeMetric>('visitors');
const metricOptions = computed(() => [
    { value: 'visitors', label: t('visitAnalytics.visitors') },
    { value: 'pageViews', label: t('visitAnalytics.pageViews') },
]);

const busiest = computed(() => UtilVisitTime.peak(cells.value ?? [], metric.value, 'max'));
const quietest = computed(() => UtilVisitTime.peak(cells.value ?? [], metric.value, 'min'));
const heatCells = computed(() => UtilVisitTime.heatCells(cells.value ?? [], metric.value));
const heatLabels = computed(() => UtilVisitAnalyticsMessages.heatLabels(t, metric.value, s.value ? UtilVisitAnalyticsMessages.range(s.value.range, (d) => time.value.serviceDate(d)) : '', busiest.value));
const decimalFormat = computed(() => {
    const lang = locale.value;
    return { number: (n: number) => UtilVisitAnalytics.decimal(n, lang) };
});

const hourItems = computed(() =>
    UtilVisitTime.hourAverages(cells.value ?? [], metric.value).map((value, hour) => ({ key: `h${hour}`, label: t('visitAnalytics.hourN', { n: hour }), value })),
);

/** 하루를 넷(새벽 0~6 · 오전 6~12 · 오후 12~18 · 저녁 18~24시)으로 — 시간대 평균 합에서 차지하는 비중과 그 띠의 시간당 평균. 가장 큰 띠를 강조 */
const hourBands = computed(() => {
    const values = hourItems.value.map((i) => i.value ?? 0);
    const total = values.reduce((a, b) => a + b, 0);
    const lang = locale.value;
    const bands = (['dawn', 'morning', 'afternoon', 'evening'] as const).map((key, i) => {
        const sum = values.slice(i * 6, i * 6 + 6).reduce((a, b) => a + b, 0);
        return { key, sum, shareText: total > 0 ? `${Math.round((sum / total) * 100)}%` : '—', avgText: UtilVisitAnalytics.decimal(sum / 6, lang) };
    });
    const top = Math.max(...bands.map((b) => b.sum));
    return bands.map((b) => ({ ...b, top: total > 0 && b.sum === top }));
});

const weekdayRows = computed<VisitBarRow[]>(() => {
    const avgs = UtilVisitTime.weekdayAverages(cells.value ?? [], metric.value);
    const best = Math.max(...avgs.map((v) => v ?? 0));
    return avgs.map((v, i) => ({
        key: String(i + 1),
        label: t(`visitAnalytics.weekdayLong.${i + 1}`),
        value: v ?? 0,
        valueText: UtilVisitAnalytics.decimal(v, locale.value),
        highlight: v !== null && v === best,
        muted: v === null,
    }));
});

// ── 한 줄 요약 ──
const insights = computed(() => {
    const b = busiest.value;
    if (!b) return [];
    const lang = locale.value;
    const out = [t('visitAnalytics.insightBusiest', { when: UtilVisitAnalyticsMessages.when(t, b.weekday, b.hour), n: UtilVisitAnalytics.decimal(b.value, lang) })];
    const q = quietest.value;
    if (q) out.push(t('visitAnalytics.insightQuietest', { when: UtilVisitAnalyticsMessages.when(t, q.weekday, q.hour) }));
    return out;
});
</script>
