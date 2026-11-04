<template>
    <!--
        방문자 통계 › 봇·크롤러(`/analytics/visits/bots`, 2단계 2026-11-04) — 숫자 카드 4(오늘 봇 요청 · 오늘 봇 비율 · 기간 봇 요청 · 기간 봇 비율)
        → 봇 종류별 막대(구글·네이버(Yeti)·빙·다음·기타 크롤러·도구) | 일별 추이(쌓은 막대, 같은 색) → 안내(봇은 방문 통계에서 빠져 있다 · 어떻게 가르는지).
        봇 비율 = 봇 요청 ÷ (봇 요청 + 사람 페이지뷰). 사람 페이지뷰는 방문자·페이지뷰 수집(`collect.visits`)이 꺼져 있으면 없어서 비율 칸은 「—」.
        한 줄 요약: 기간 요청의 봇 비율 · 가장 많은 봇. 봇 수집(`collect.bots`)이 꺼져 있으면 쪽 전체가 「수집이 꺼져 있어요」. 권한: `site.read`(메뉴 `visitBots`).
        서버 호출은 `useVisitAnalyticsApi().bots`(`GET /api/admin/analytics/visits/bots`). 봇은 공개 화면 HTML 요청에서 센다(`/api` 만 두드리는 도구·로컬 Nuxt 개발 서버로 온 봇은 안 잡힌다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitBots')" :subtitle="t('visitAnalytics.bots.subtitle')" :as-of="d?.asOf ?? null" :busy="bots.loading.value" :range="allowed" :insights="insights" @refresh="load" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="bots.error.value" :title="t('visitAnalytics.bots.kindsTitle')">
            <ErrorState :code="bots.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.bots.loadFailed')" :description="bots.error.value.message" :retry="bots.error.value.status !== 403" :retrying="bots.loading.value" @retry="load" />
        </AppGadget>
        <template v-else-if="!d">
            <VisitStatCards />
            <Skeleton height="16rem" border-radius="0.75rem" />
        </template>
        <AppGadget v-else-if="!d.collect.bots" :title="t('visitAnalytics.bots.kindsTitle')">
            <VisitCollectOff item="bots" :enabled="d.collect.enabled" />
        </AppGadget>

        <template v-else>
            <div class="transition-opacity" :class="{ 'opacity-60': bots.loading.value }" :aria-busy="bots.loading.value">
                <VisitStatCards :items="cards" />
            </div>

            <AppGadget v-if="!d.totals.bots" :title="t('visitAnalytics.bots.kindsTitle')">
                <EmptyState icon="pi pi-microchip-ai" :title="t('visitAnalytics.bots.empty')" :description="t('visitAnalytics.trendEmptyHint')" />
            </AppGadget>

            <div v-else class="grid min-w-0 items-start gap-4 transition-opacity lg:grid-cols-5" :class="{ 'opacity-60': bots.loading.value }">
                <AppGadget :title="t('visitAnalytics.bots.kindsTitle')" class="lg:col-span-2">
                    <template #title-extra>
                        <span class="text-[12px] font-normal text-muted-color tabular-nums">{{ t('visitAnalytics.bots.requestsN', { n: UtilVisitAnalytics.num(d.totals.bots, locale) }) }}</span>
                    </template>
                    <VisitBarList :rows="kindRows" :total="d.totals.bots" />
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.bots.seriesTitle')" class="lg:col-span-3">
                    <StackedBarChart :categories="categories" :series="stacked" :labels="stackLabels" :format="stackFormat" :current="current" />
                </AppGadget>
            </div>

            <AppGadget :title="t('visitAnalytics.bots.aboutTitle')">
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-[13px] leading-relaxed">
                    <li v-for="key in ABOUT" :key="key" class="flex items-start gap-2">
                        <i class="pi pi-info-circle mt-1 text-[11px] text-muted-color" aria-hidden="true" /><span>{{ t(`visitAnalytics.bots.${key}`) }}</span>
                    </li>
                </ul>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { StackedBarFormat, StackedBarLabels, StackedBarSeries } from '#layers/chart/components/StackedBarChart.types';
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import type { VisitStatCard } from '~/components/analytics/VisitStatCards.types';
import { VISIT_BOT_KINDS, type VisitBotCounts } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';

/** 안내 줄 */
const ABOUT = ['aboutExcluded', 'aboutHow', 'aboutRate', 'aboutNoStore'] as const;

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitBots')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const time = useDisplayTime();
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));

const bots = useVisitLoad(api.bots);
const d = computed(() => bots.data.value);

function load(): void {
    void bots.load({ from: vr.range.value.from, to: vr.range.value.to });
}

vr.onRange(load, allowed);

/** 봇 비율(%) — 사람 페이지뷰를 모르면 null */
function botRate(c: VisitBotCounts): number | null {
    if (c.humans === null) return null;
    const all = c.bots + c.humans;
    return all > 0 ? (c.bots / all) * 100 : null;
}

const kindRows = computed<VisitBarRow[]>(() => (d.value?.kinds ?? []).map((k) => ({ key: k.kind, label: t(`visitAnalytics.botKind.${k.kind}`), value: k.requests, color: UtilVisitAnalytics.BOT_COLORS[k.kind] })));

const cards = computed<VisitStatCard[] | null>(() => {
    const v = d.value;
    if (!v) return null;
    const lang = locale.value;
    const num = (n: number) => UtilVisitAnalytics.num(n, lang);
    const rateCard = (key: string, label: string, c: VisitBotCounts): VisitStatCard => ({
        key,
        label,
        value: UtilVisitAnalytics.percent(botRate(c), lang),
        sub: c.humans === null ? t('visitAnalytics.bots.noHumans') : t('visitAnalytics.bots.humansSub', { n: num(c.humans) }),
        help: t('visitAnalytics.bots.rateHelp'),
    });
    return [
        { key: 'today', label: t('visitAnalytics.bots.cardToday'), value: num(v.today.bots), sub: t('visitAnalytics.bots.cardTodaySub') },
        rateCard('todayRate', t('visitAnalytics.bots.cardTodayRate'), v.today),
        { key: 'range', label: t('visitAnalytics.bots.cardRange'), value: num(v.totals.bots), sub: t('visitAnalytics.dailyAverageN', { n: num(Math.round(v.totals.bots / Math.max(1, UtilVisitAnalytics.days(v.range)))) }) },
        rateCard('rangeRate', t('visitAnalytics.bots.cardRangeRate'), v.totals),
    ];
});

// ── 일별 추이(아래부터 많은 종류) ──
const categories = computed(() => (d.value?.series ?? []).map((p) => p.date));
const current = computed(() => (d.value && UtilVisitAnalytics.includesToday(d.value.range) ? UtilVisitAnalytics.today() : ''));
const stacked = computed<StackedBarSeries[]>(() => {
    const order = kindRows.value.map((r) => r.key);
    return [...VISIT_BOT_KINDS]
        .sort((a, b) => order.indexOf(a) - order.indexOf(b))
        .map((kind) => ({
            key: kind,
            name: t(`visitAnalytics.botKind.${kind}`),
            color: UtilVisitAnalytics.BOT_COLORS[kind]!,
            values: (d.value?.series ?? []).map((p) => p.kinds[kind] ?? 0),
        }));
});
const stackLabels = computed<StackedBarLabels>(() => ({
    summary: t('visitAnalytics.bots.seriesSummary', { range: d.value ? UtilVisitAnalyticsMessages.range(d.value.range, (x) => time.value.serviceDate(x)) : '', n: categories.value.length }),
    tableCaption: t('visitAnalytics.bots.seriesTableCaption'),
    category: t('visitAnalytics.chartDate'),
    total: t('visitAnalytics.seriesTotal'),
    legend: t('visitAnalytics.bots.kindsTitle'),
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
    const v = d.value;
    if (!v || !v.collect.bots || !v.totals.bots) return [];
    const lang = locale.value;
    const out: string[] = [];
    const rate = botRate(v.totals);
    out.push(rate === null ? t('visitAnalytics.bots.insightCount', { n: UtilVisitAnalytics.num(v.totals.bots, lang) }) : t('visitAnalytics.bots.insightRate', { rate: UtilVisitAnalytics.percent(rate, lang, 0) }));
    const top = kindRows.value[0];
    if (top && top.value > 0) out.push(t('visitAnalytics.bots.insightTop', { kind: top.label, rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.share(top.value, v.totals.bots), lang, 0) }));
    return out;
});
</script>
