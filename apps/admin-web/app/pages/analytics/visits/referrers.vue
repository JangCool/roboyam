<template>
    <!--
        방문자 통계 › 유입 경로(`/analytics/visits/referrers`) — 분류 비율 막대(색 = 분류) | 분류별 일별 방문(쌓은 막대 `StackedBarChart`, 같은 색) → 많이 들어온 사이트 10 | 캠페인 표.
        한 줄 요약: 가장 많은 유입 분류(비율) · 가장 많이 들어온 사이트. 방문 = 세션 첫 페이지.
        분류·사이트·일별은 `analytics_referrer_daily`, 캠페인은 일별 캠페인 집계 표 — 모두 유입 수집(`collect.referrers`)으로 쌓이고 원본 기록과 무관하다.
        유입 수집이 꺼져 있으면 쪽 전체가 「수집이 꺼져 있어요」. 권한: `site.read`(메뉴 `visitReferrers`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitReferrers')" :subtitle="t('visitAnalytics.referrersSubtitle')" :as-of="s?.asOf ?? null" :busy="busy" :range="allowed" :insights="insights" @refresh="loadAll" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="summary.error.value || referrers.error.value" :title="t('visitAnalytics.referrersTitle')">
            <ErrorState :title="summary.error.value ? t('visitAnalytics.summaryLoadFailed') : t('visitAnalytics.referrersLoadFailed')" :description="(summary.error.value ?? referrers.error.value)?.message" retry :retrying="busy" @retry="loadAll" />
        </AppGadget>
        <div v-else-if="!s || (s.collect.referrers && !r)" class="grid gap-4 lg:grid-cols-5">
            <Skeleton height="16rem" border-radius="0.75rem" class="lg:col-span-2" />
            <Skeleton height="16rem" border-radius="0.75rem" class="lg:col-span-3" />
        </div>
        <AppGadget v-else-if="!s.collect.referrers || !r" :title="t('visitAnalytics.referrersTitle')">
            <VisitCollectOff item="referrers" :enabled="s.collect.enabled" />
        </AppGadget>
        <AppGadget v-else-if="!r.total" :title="t('visitAnalytics.referrersTitle')">
            <EmptyState icon="pi pi-directions" :title="t('visitAnalytics.referrersEmpty')" />
        </AppGadget>

        <template v-else>
            <div class="grid min-w-0 items-start gap-4 transition-opacity lg:grid-cols-5" :class="{ 'opacity-60': referrers.loading.value }" :aria-busy="referrers.loading.value">
                <AppGadget :title="t('visitAnalytics.groupsTitle')" class="lg:col-span-2">
                    <template #title-extra>
                        <span class="text-[12px] font-normal text-muted-color tabular-nums">{{ t('visitAnalytics.referrersTotal', { n: UtilVisitAnalytics.num(r.total, locale) }) }}</span>
                    </template>
                    <VisitBarList :rows="groupRows" :total="r.total" />
                    <p class="m-0 mt-3 text-[12px] text-muted-color">{{ t('visitAnalytics.referrersHint') }}</p>
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.seriesTitle')" class="lg:col-span-3">
                    <StackedBarChart :categories="categories" :series="stacked" :labels="stackLabels" :format="stackFormat" :current="current" />
                </AppGadget>
            </div>

            <div class="grid min-w-0 items-start gap-4 lg:grid-cols-5">
                <AppGadget :title="t('visitAnalytics.domainsTitle')" class="lg:col-span-2">
                    <p v-if="!r.domains.length" class="m-0 py-2 text-[13px] text-muted-color">{{ t('visitAnalytics.domainsEmpty') }}</p>
                    <VisitBarList v-else :rows="domainRows" :total="r.total" rank />
                </AppGadget>

                <AppGadget :title="t('visitAnalytics.campaignsTitle')" class="lg:col-span-3">
                    <template #title-extra><span class="hidden text-[12px] font-normal text-muted-color sm:inline">{{ t('visitAnalytics.campaignsHint') }}</span></template>
                    <p v-if="!campaigns.length" class="m-0 py-2 text-[13px] text-muted-color">{{ t('visitAnalytics.campaignsEmpty') }}</p>
                    <div v-else class="min-w-0 overflow-x-auto">
                        <table class="w-full border-collapse text-[13px]">
                            <thead>
                                <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.col.source') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.col.medium') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.col.campaign') }}</th>
                                    <th scope="col" class="w-20 py-1.5 text-right font-normal">{{ t('visitAnalytics.col.visits') }}</th>
                                    <th scope="col" class="w-24 py-1.5 pl-3 font-normal">{{ t('visitAnalytics.col.share') }}</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-(--line)">
                                <tr v-for="(c, i) in campaigns" :key="i">
                                    <td class="max-w-32 truncate py-2 pr-2" :class="c.source ? '' : 'text-muted-color'" :title="c.source ?? undefined">{{ c.source ?? t('visitAnalytics.noValue') }}</td>
                                    <td class="max-w-28 truncate py-2 pr-2" :class="c.medium ? '' : 'text-muted-color'" :title="c.medium ?? undefined">{{ c.medium ?? t('visitAnalytics.noValue') }}</td>
                                    <td class="max-w-40 truncate py-2 pr-2" :class="c.campaign ? '' : 'text-muted-color'" :title="c.campaign ?? undefined">{{ c.campaign ?? t('visitAnalytics.noValue') }}</td>
                                    <td class="py-2 text-right tabular-nums">{{ UtilVisitAnalytics.num(c.visits, locale) }}</td>
                                    <td class="py-2 pl-3">
                                        <span class="block h-1.5 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                                            <span class="block h-full rounded-full" :style="{ width: `${UtilVisitAnalytics.share(c.visits, campaignTotal)}%`, background: UtilVisitAnalytics.GROUP_COLORS.CAMPAIGN }" />
                                        </span>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </AppGadget>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { StackedBarFormat, StackedBarLabels, StackedBarSeries } from '#layers/chart/components/StackedBarChart.types';
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import { VISIT_REFERRER_GROUPS } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitReferrers')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const time = useDisplayTime();
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));

const summary = useVisitLoad(api.summary);
const referrers = useVisitLoad(api.referrers);
const s = computed(() => summary.data.value);
const r = computed(() => referrers.data.value);
const busy = computed(() => summary.loading.value || referrers.loading.value);

async function loadAll(): Promise<void> {
    const range = { from: vr.range.value.from, to: vr.range.value.to };
    const found = await summary.load(range);
    if (!found) return;
    if (found.collect.referrers) await referrers.load({ ...range, detail: true });
    else referrers.clear();
}

vr.onRange(() => void loadAll(), allowed);

const groupRows = computed<VisitBarRow[]>(() => UtilVisitAnalyticsMessages.groupRows(t, r.value?.groups ?? []));
const domainRows = computed<VisitBarRow[]>(() => (r.value?.domains ?? []).map((d) => ({ key: d.domain, label: d.domain, value: d.visits })));
const campaigns = computed(() => r.value?.campaigns ?? []);
const campaignTotal = computed(() => campaigns.value.reduce((a, c) => a + c.visits, 0));

// ── 쌓은 막대(아래부터 많은 분류) ──
const categories = computed(() => (r.value?.series ?? []).map((p) => p.date));
const current = computed(() => (s.value && UtilVisitAnalytics.includesToday(s.value.range) ? UtilVisitAnalytics.today() : ''));
const stacked = computed<StackedBarSeries[]>(() => {
    const order = groupRows.value.map((g) => g.key);
    return [...VISIT_REFERRER_GROUPS]
        .sort((a, b) => order.indexOf(a) - order.indexOf(b))
        .map((group) => ({
            key: group,
            name: t(`dashboard.referrerGroup.${group}`),
            color: UtilVisitAnalytics.GROUP_COLORS[group]!,
            values: (r.value?.series ?? []).map((p) => p.groups[group] ?? 0),
        }));
});
const stackLabels = computed<StackedBarLabels>(() => ({
    summary: t('visitAnalytics.seriesSummary', { range: s.value ? UtilVisitAnalyticsMessages.range(s.value.range, (d) => time.value.serviceDate(d)) : '', n: categories.value.length }),
    tableCaption: t('visitAnalytics.seriesTableCaption'),
    category: t('visitAnalytics.chartDate'),
    total: t('visitAnalytics.seriesTotal'),
    legend: t('visitAnalytics.seriesLegend'),
    current: t('visitAnalytics.chartNow'),
}));
const stackFormat = computed<StackedBarFormat>(() => {
    const lang = locale.value;
    const tv = time.value;
    return {
        number: (n) => UtilVisitAnalytics.num(n, lang),
        axis: (n) => UtilVisitAnalytics.compact(n, lang),
        category: (k) => tv.serviceDate(k),
        shortCategory: (k) => `${Number(k.slice(5, 7))}/${Number(k.slice(8, 10))}`,
    };
});

// ── 한 줄 요약 ──
const insights = computed(() => {
    const d = r.value;
    if (!d || !d.total) return [];
    const lang = locale.value;
    const out: string[] = [];
    const top = groupRows.value[0];
    if (top && top.value > 0) out.push(t('visitAnalytics.insightTopGroup', { group: top.label, rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.share(top.value, d.total), lang, 0) }));
    const dom = d.domains[0];
    if (dom) out.push(t('visitAnalytics.insightTopDomain', { domain: dom.domain, n: UtilVisitAnalytics.num(dom.visits, lang) }));
    return out;
});
</script>
