<template>
    <!--
        방문자 통계 › 개요(`/analytics/visits`) — 계약 `docs/features/site-analytics-contract.md`.
        머리(`VisitStatsHeader` — 기간은 쪽을 옮겨도 이어진다 · 한 줄 요약: 방문자 증감 · 가장 많이 본 페이지)
        → 요약 카드 5(방문자 — 여러 날이면 「일별 합」 · 신규 · 페이지뷰 · 1인당 · 지금 접속, 카드마다 증감 칩·미니 추이) → 방문 추이
        → 미리보기 3장(많이 본 페이지 5 · 유입 분류 · 요일·시간대 작은 히트맵) — 각 카드 「자세히 →」 = 그 쪽(같은 기간).
        읽는 차례: 요약(수집 스위치) → 켜진 미리보기만 같이(유입은 `detail=false` — 분류·사이트만). 요약이 실패하면 화면 오류 하나.
        권한: `site.read`(메뉴 `visitOverview`). 막는 것은 서버다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitOverview')" :subtitle="t('visitAnalytics.overviewSubtitle')" :as-of="summary.data.value?.asOf ?? null" :busy="busy" :range="allowed" :insights="insights" @refresh="loadAll" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />

        <AppGadget v-else-if="summary.error.value" :title="t('visitAnalytics.trendTitle')">
            <ErrorState :code="summary.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.summaryLoadFailed')" :description="summary.error.value.message" :retry="summary.error.value.status !== 403" :retrying="busy" @retry="loadAll" />
        </AppGadget>

        <template v-else>
            <template v-if="!s || s.collect.visits">
                <div class="transition-opacity" :class="{ 'opacity-60': summary.loading.value && s }" :aria-busy="summary.loading.value">
                    <VisitSummaryStrip :summary="s" />
                </div>
                <AppGadget :title="t('visitAnalytics.trendTitle')">
                    <template #title-extra>
                        <span v-if="s" class="text-[12px] font-normal text-muted-color tabular-nums">{{ s.granularity === 'HOUR' ? t('visitAnalytics.trendHourly') : t('visitAnalytics.trendDaily') }}</span>
                    </template>
                    <div v-if="!s" role="status" :aria-label="t('common.loading')"><Skeleton height="14rem" /></div>
                    <EmptyState v-else-if="!s.totals.pageViews" icon="pi pi-chart-bar" :title="t('visitAnalytics.trendEmpty')" :description="t('visitAnalytics.trendEmptyHint')" />
                    <VisitTrendChart v-else :series="s.series" :granularity="s.granularity" :range="s.range" />
                </AppGadget>
            </template>
            <AppGadget v-else :title="t('visitAnalytics.trendTitle')">
                <VisitCollectOff item="visits" :enabled="s.collect.enabled" />
            </AppGadget>

            <!-- 미리보기 -->
            <div v-if="s" class="grid min-w-0 items-stretch gap-4 lg:grid-cols-3">
                <AppGadget :title="t('visitAnalytics.topPagesTitle')">
                    <template #actions><VisitMoreLink :to="vr.linkTo(UtilVisitAnalytics.PAGES.pages)" /></template>
                    <VisitCollectOff v-if="!s.collect.rawEvents" item="rawEvents" :enabled="s.collect.enabled" />
                    <ErrorState v-else-if="pages.error.value" :title="t('visitAnalytics.previewFailed')" :description="pages.error.value.message" retry :retrying="pages.loading.value" @retry="loadPages" />
                    <div v-else-if="!pages.data.value" class="flex flex-col gap-3"><Skeleton v-for="n in 5" :key="n" height="1.5rem" /></div>
                    <EmptyState v-else-if="!pages.data.value.items.length" icon="pi pi-desktop" :title="t('visitAnalytics.pagesEmpty')" />
                    <VisitBarList v-else :rows="pageRows" :total="pages.data.value.totalViews" rank />
                </AppGadget>

                <AppGadget :title="t('visitAnalytics.groupsTitle')">
                    <template #actions><VisitMoreLink :to="vr.linkTo(UtilVisitAnalytics.PAGES.referrers)" /></template>
                    <VisitCollectOff v-if="!s.collect.referrers" item="referrers" :enabled="s.collect.enabled" />
                    <ErrorState v-else-if="referrers.error.value" :title="t('visitAnalytics.previewFailed')" :description="referrers.error.value.message" retry :retrying="referrers.loading.value" @retry="loadReferrers" />
                    <div v-else-if="!referrers.data.value" class="flex flex-col gap-3"><Skeleton v-for="n in 5" :key="n" height="1.5rem" /></div>
                    <EmptyState v-else-if="!referrers.data.value.total" icon="pi pi-directions" :title="t('visitAnalytics.referrersEmpty')" />
                    <VisitBarList v-else :rows="groupRows" :total="referrers.data.value.total" />
                </AppGadget>

                <AppGadget :title="t('visitAnalytics.timePreviewTitle')">
                    <template #actions><VisitMoreLink :to="vr.linkTo(UtilVisitAnalytics.PAGES.time)" /></template>
                    <VisitCollectOff v-if="!s.collect.visits" item="visits" :enabled="s.collect.enabled" />
                    <ErrorState v-else-if="timeLoad.error.value" :title="t('visitAnalytics.previewFailed')" :description="timeLoad.error.value.message" retry :retrying="timeLoad.loading.value" @retry="loadTime" />
                    <Skeleton v-else-if="!timeLoad.data.value" height="9rem" />
                    <EmptyState v-else-if="!busiest" icon="pi pi-clock" :title="t('visitAnalytics.timeEmpty')" />
                    <div v-else class="flex flex-col gap-2">
                        <WeekHourHeatmap :cells="heatCells" :labels="heatLabels" :format="heatFormat" compact />
                        <p class="m-0 text-[12px] text-muted-color">
                            {{ t('visitAnalytics.busiest') }} · <b class="font-medium text-color">{{ UtilVisitAnalyticsMessages.when(t, busiest.weekday, busiest.hour) }}</b>
                        </p>
                    </div>
                </AppGadget>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';
import UtilVisitTime from '~/utils/util-visit-time';

const { t, te, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitOverview')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const allowed = computed(() => can('site.read'));

const summary = useVisitLoad(api.summary);
const pages = useVisitLoad(api.pages);
const referrers = useVisitLoad(api.referrers);
const timeLoad = useVisitLoad(api.time);
const s = computed(() => summary.data.value);
const busy = computed(() => summary.loading.value || pages.loading.value || referrers.loading.value || timeLoad.loading.value);

function rangeArg() {
    return { from: vr.range.value.from, to: vr.range.value.to };
}

const loadPages = () => void pages.load({ ...rangeArg(), page: 0, size: 5 });
const loadReferrers = () => void referrers.load({ ...rangeArg(), detail: false });
const loadTime = () => void timeLoad.load(rangeArg());

async function loadAll(): Promise<void> {
    const found = await summary.load(rangeArg());
    if (!found) return;
    const c = found.collect;
    if (c.rawEvents) loadPages();
    else pages.clear();
    if (c.referrers) loadReferrers();
    else referrers.clear();
    if (c.visits) loadTime();
    else timeLoad.clear();
}

vr.onRange(() => void loadAll(), allowed);

function kindName(kind: string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}

const pageRows = computed<VisitBarRow[]>(() =>
    (pages.data.value?.items ?? []).map((p) => ({ key: p.path, label: p.title ? localizedName(p.title, p.titleEn) : p.path, title: p.path, mono: !p.title, tag: kindName(p.kind), value: p.views })),
);

const groupRows = computed<VisitBarRow[]>(() => UtilVisitAnalyticsMessages.groupRows(t, referrers.data.value?.groups ?? []));

// ── 요일·시간대 미리보기 ──
const busiest = computed(() => UtilVisitTime.peak(timeLoad.data.value?.cells ?? [], 'visitors', 'max'));
const heatCells = computed(() => UtilVisitTime.heatCells(timeLoad.data.value?.cells ?? [], 'visitors'));
const heatLabels = computed(() => UtilVisitAnalyticsMessages.heatLabels(t, 'visitors', s.value ? UtilVisitAnalyticsMessages.range(s.value.range, (d) => time.value.serviceDate(d)) : '', busiest.value));
const heatFormat = computed(() => {
    const lang = locale.value;
    return { number: (n: number) => UtilVisitAnalytics.decimal(n, lang) };
});

// 한 줄 요약 — 방문자 증감 · 가장 많이 본 페이지
const insights = computed(() => {
    const out: string[] = [];
    const sum = s.value;
    if (sum?.collect.visits) out.push(UtilVisitAnalyticsMessages.visitorsInsight(t, sum, locale.value));
    const top = pages.data.value?.items[0];
    const total = pages.data.value?.totalViews ?? 0;
    if (top && total > 0) {
        out.push(t('visitAnalytics.insightTopPage', { page: top.title ? localizedName(top.title, top.titleEn) : top.path, rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.share(top.views, total), locale.value, 0) }));
    }
    return out;
});
</script>
