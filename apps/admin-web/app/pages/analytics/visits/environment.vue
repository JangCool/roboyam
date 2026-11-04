<template>
    <!--
        방문자 통계 › 환경(`/analytics/visits/environment`, 2단계 2026-11-04) — 기기 비율 막대 | 일별 기기 비중(100% 쌓은 막대 `StackedBarChart`, 같은 색)
        → 브라우저 · OS · 화면 너비 구간 비율 막대 3장. **방문 기준**(세션 첫 페이지 하나 = 1) — 같은 사람이 여러 페이지를 봐도 한 번.
        한 줄 요약: 가장 많은 기기(비율) · 가장 많은 브라우저. 브라우저·OS·화면 너비는 사용자 브라우저가 분류값으로만 보내고(원본 UA 저장 안 함), 기기는 서버가 UA 의 표시로 가른다.
        환경 수집(`collect.environment`)이 꺼져 있으면 쪽 전체가 「수집이 꺼져 있어요」. 권한: `site.read`(메뉴 `visitEnvironment`).
        서버 호출은 `useVisitAnalyticsApi().environment`(`GET /api/admin/analytics/visits/environment`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitEnvironment')" :subtitle="t('visitAnalytics.env.subtitle')" :as-of="d?.asOf ?? null" :busy="env.loading.value" :range="allowed" :insights="insights" @refresh="load" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="env.error.value" :title="t('visitAnalytics.env.title')">
            <ErrorState :code="env.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.env.loadFailed')" :description="env.error.value.message" :retry="env.error.value.status !== 403" :retrying="env.loading.value" @retry="load" />
        </AppGadget>
        <div v-else-if="!d" class="grid gap-4 lg:grid-cols-5" role="status" :aria-label="t('common.loading')">
            <Skeleton height="14rem" border-radius="0.75rem" class="lg:col-span-2" />
            <Skeleton height="14rem" border-radius="0.75rem" class="lg:col-span-3" />
        </div>
        <AppGadget v-else-if="!d.collect.environment" :title="t('visitAnalytics.env.title')">
            <VisitCollectOff item="environment" :enabled="d.collect.enabled" />
        </AppGadget>
        <AppGadget v-else-if="!d.total" :title="t('visitAnalytics.env.title')">
            <EmptyState icon="pi pi-mobile" :title="t('visitAnalytics.env.empty')" :description="t('visitAnalytics.trendEmptyHint')" />
        </AppGadget>

        <template v-else>
            <div class="grid min-w-0 items-start gap-4 transition-opacity lg:grid-cols-5" :class="{ 'opacity-60': env.loading.value }" :aria-busy="env.loading.value">
                <AppGadget :title="t('visitAnalytics.env.devicesTitle')" class="lg:col-span-2">
                    <template #title-extra>
                        <span class="text-[12px] font-normal text-muted-color tabular-nums">{{ t('visitAnalytics.referrersTotal', { n: UtilVisitAnalytics.num(d.total, locale) }) }}</span>
                    </template>
                    <VisitBarList :rows="deviceRows" :total="d.total" />
                    <p class="m-0 mt-3 text-[12px] leading-relaxed text-muted-color">{{ t('visitAnalytics.env.basisHint') }}</p>
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.env.seriesTitle')" class="lg:col-span-3">
                    <StackedBarChart :categories="categories" :series="stacked" :labels="stackLabels" :format="stackFormat" :current="current" />
                </AppGadget>
            </div>

            <div class="grid min-w-0 items-start gap-4 lg:grid-cols-3">
                <AppGadget :title="t('visitAnalytics.env.browsersTitle')">
                    <VisitBarList :rows="browserRows" :total="d.total" />
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.env.osesTitle')">
                    <VisitBarList :rows="osRows" :total="d.total" />
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.env.screensTitle')">
                    <VisitBarList :rows="screenRows" :total="d.total" />
                    <p class="m-0 mt-3 text-[12px] leading-relaxed text-muted-color">{{ t('visitAnalytics.env.screensHint') }}</p>
                </AppGadget>
            </div>

            <p class="m-0 flex items-start gap-2 text-[12px] leading-relaxed text-muted-color">
                <i class="pi pi-shield mt-0.5 text-[11px]" aria-hidden="true" /><span>{{ t('visitAnalytics.env.privacyHint') }}</span>
            </p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { StackedBarFormat, StackedBarLabels, StackedBarSeries } from '#layers/chart/components/StackedBarChart.types';
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import { VISIT_DEVICES, type VisitShareRow } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitEnvironment')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const time = useDisplayTime();
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));

const env = useVisitLoad(api.environment);
const d = computed(() => env.data.value);

function load(): void {
    void env.load({ from: vr.range.value.from, to: vr.range.value.to });
}

vr.onRange(load, allowed);

/** 분류 줄 → 막대 줄(이름 키 `visitAnalytics.<묶음>.<값>`) */
function rowsOf(group: 'device' | 'browser' | 'os' | 'screen', rows: VisitShareRow<string>[], colors?: Readonly<Record<string, string>>): VisitBarRow[] {
    return rows.map((r) => ({ key: r.key, label: t(`visitAnalytics.${group}.${r.key}`), value: r.visits, color: colors?.[r.key] }));
}

const deviceRows = computed(() => rowsOf('device', d.value?.devices ?? [], UtilVisitAnalytics.DEVICE_COLORS));
const browserRows = computed(() => rowsOf('browser', d.value?.browsers ?? []));
const osRows = computed(() => rowsOf('os', d.value?.oses ?? []));
// 화면 너비는 구간 차례 그대로(서버가 작은 차례로 준다)
const screenRows = computed(() => rowsOf('screen', d.value?.screens ?? []));

// ── 일별 기기 비중(아래부터 많은 기기, 칸마다 합 100%) ──
const categories = computed(() => (d.value?.series ?? []).map((p) => p.date));
const current = computed(() => (d.value && UtilVisitAnalytics.includesToday(d.value.range) ? UtilVisitAnalytics.today() : ''));
const stacked = computed<StackedBarSeries[]>(() => {
    const series = d.value?.series ?? [];
    const order = deviceRows.value.map((r) => r.key);
    const devices = [...VISIT_DEVICES].sort((a, b) => order.indexOf(a) - order.indexOf(b));
    const shares = series.map((p) => UtilVisitAnalytics.wholeShares(devices.map((k) => p.devices[k] ?? 0)));
    return devices.map((key, i) => ({
        key,
        name: t(`visitAnalytics.device.${key}`),
        color: UtilVisitAnalytics.DEVICE_COLORS[key]!,
        values: shares.map((s) => s[i] ?? 0),
    }));
});
const stackLabels = computed<StackedBarLabels>(() => ({
    summary: t('visitAnalytics.env.seriesSummary', { range: d.value ? UtilVisitAnalyticsMessages.range(d.value.range, (x) => time.value.serviceDate(x)) : '', n: categories.value.length }),
    tableCaption: t('visitAnalytics.env.seriesTableCaption'),
    category: t('visitAnalytics.chartDate'),
    total: t('visitAnalytics.seriesTotal'),
    legend: t('visitAnalytics.env.devicesTitle'),
    current: t('visitAnalytics.chartNow'),
}));
const stackFormat = computed<StackedBarFormat>(() => {
    const tv = time.value;
    return {
        number: (n) => `${n}%`,
        axis: (n) => `${n}%`,
        category: (k) => tv.serviceDate(k),
        shortCategory: (k) => `${Number(k.slice(5, 7))}/${Number(k.slice(8, 10))}`,
    };
});

// ── 한 줄 요약 ──
const insights = computed(() => {
    const v = d.value;
    if (!v || !v.collect.environment || !v.total) return [];
    const lang = locale.value;
    const rate = (n: number) => UtilVisitAnalytics.percent(UtilVisitAnalytics.share(n, v.total), lang, 0);
    const out: string[] = [];
    const device = deviceRows.value[0];
    if (device) out.push(t('visitAnalytics.env.insightDevice', { device: device.label, rate: rate(device.value) }));
    const browser = browserRows.value[0];
    if (browser) out.push(t('visitAnalytics.env.insightBrowser', { browser: browser.label, rate: rate(browser.value) }));
    return out;
});
</script>
