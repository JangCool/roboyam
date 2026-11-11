<template>
    <!--
        방문자 통계 › 월별 보기(`/analytics/visits/monthly`, 방문 통계 3단계 결정 ③ — ⚠ 목업 숫자). **메뉴에 없다** — 「방문자 통계 › 개요」 방문 추이 카드의 「월별 보기 →」로 들어온다(관리자 메뉴는 서버 DB 가 준다).
        일별은 13개월만 남으므로 긴 기간은 달 단위로 본다. 기간: 주소 `from`·`to` = `YYYY-MM`(양끝 포함, 최대 36개월, 기본 최근 24개월 — 다른 쪽의 일별 기간과 따로 간다).
        ① 머리(`VisitStatsHeader` — 기간 거르기 없이) + 달 기간 고르기(시작 달 · 끝 달 · 최근 12/24/36개월) · 한 줄 요약(가장 많이 본 페이지 · 방문자 일별 합이 가장 많았던 달 · 지난달 순방문자)
        ② 숫자 카드 4(방문자 일별 합 · 신규 · 페이지뷰 · 지난달 순방문자) ③ 월별 추이(막대 = 방문자 일별 합, 선 = 순방문자(MAU) | 신규 | 페이지뷰 — ⓘ 「일별 합」과 MAU 가 다름)
        ④ 달마다 표(최근 달이 위) ⑤ 기간 많이 본 페이지 10(묶음 주소는 화면 이름 — `UtilVisitPathMessages`) | 유입 분류 비율.
        수집 스위치는 다른 쪽과 같이 지금 값으로 가른다: 추이·카드·표·페이지 = `visits`, 유입 = `referrers`. 권한: `site.read`. 서버 호출은 `useVisitAnalyticsApi().monthly`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('visitAnalytics.monthly.title')" :subtitle="t('visitAnalytics.monthly.subtitle')" :as-of="m?.asOf ?? null" :busy="monthly.loading.value" :range="false" :insights="insights" @refresh="load" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />

        <template v-else>
            <!-- 달 기간 -->
            <form class="flex flex-wrap items-end gap-x-3 gap-y-2" role="search" :aria-label="t('visitAnalytics.monthly.filters')" @submit.prevent>
                <div class="flex flex-col gap-1">
                    <label :for="fromId" class="text-[12px] text-muted-color">{{ t('visitAnalytics.monthly.from') }}</label>
                    <Select :model-value="range.from" :input-id="fromId" :options="monthOptions" option-label="label" option-value="value" size="small" class="w-40" @update:model-value="(v: string) => apply(v, range.to)" />
                </div>
                <span class="pb-2 text-muted-color" aria-hidden="true">~</span>
                <div class="flex flex-col gap-1">
                    <label :for="toId" class="text-[12px] text-muted-color">{{ t('visitAnalytics.monthly.to') }}</label>
                    <Select :model-value="range.to" :input-id="toId" :options="monthOptions" option-label="label" option-value="value" size="small" class="w-40" @update:model-value="(v: string) => apply(range.from, v)" />
                </div>
                <div class="flex flex-wrap items-center gap-1" role="group" :aria-label="t('visitAnalytics.monthly.presets')">
                    <Button
                        v-for="p in presets"
                        :key="p.n"
                        type="button"
                        :label="t('visitAnalytics.monthly.preset', { n: p.n })"
                        size="small"
                        :severity="p.active ? undefined : 'secondary'"
                        :variant="p.active ? undefined : 'outlined'"
                        :aria-pressed="p.active"
                        @click="applyPreset(p.n)"
                    />
                </div>
                <span class="pb-2 text-[12px] text-muted-color">{{ t('visitAnalytics.monthly.maxMonths', { n: UtilVisitAnalytics.MAX_MONTHS }) }}</span>
                <NuxtLink :to="UtilVisitAnalytics.PAGES.overview" class="ml-auto inline-flex items-center gap-1 pb-2 text-[12px] font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                    <i class="pi pi-arrow-left text-[10px]" aria-hidden="true" />{{ t('visitAnalytics.monthly.back') }}
                </NuxtLink>
            </form>
            <p v-if="clamped" class="m-0 flex items-center gap-1.5 text-[12px] text-amber-700 dark:text-amber-400" role="status">
                <i class="pi pi-info-circle text-[11px]" aria-hidden="true" />{{ t('visitAnalytics.monthly.clamped', { n: UtilVisitAnalytics.MAX_MONTHS }) }}
            </p>

            <AppGadget v-if="monthly.error.value" :title="t('visitAnalytics.monthly.trendTitle')">
                <ErrorState :code="monthly.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.monthly.loadFailed')" :description="monthly.error.value.message" :retry="monthly.error.value.status !== 403" :retrying="monthly.loading.value" @retry="load" />
            </AppGadget>
            <template v-else-if="!m">
                <VisitStatCards :items="null" :skeletons="4" />
                <Skeleton height="18rem" border-radius="0.75rem" />
            </template>
            <AppGadget v-else-if="!m.collect.visits" :title="t('visitAnalytics.monthly.trendTitle')">
                <VisitCollectOff item="visits" :enabled="m.collect.enabled" />
            </AppGadget>

            <div v-else class="flex min-w-0 flex-col gap-4 transition-opacity" :class="{ 'opacity-60': monthly.loading.value }" :aria-busy="monthly.loading.value">
                <VisitStatCards :items="cards" />

                <AppGadget :title="t('visitAnalytics.monthly.trendTitle')">
                    <template #title-extra>
                        <button
                            v-tooltip.top="t('visitAnalytics.monthly.dailySumHelp')"
                            type="button"
                            class="inline-grid size-5 shrink-0 place-items-center rounded text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                            :aria-label="`${t('visitAnalytics.helpOf', { name: t('visitAnalytics.monthly.visitorsDaily') })} — ${t('visitAnalytics.monthly.dailySumHelp')}`"
                        >
                            <i class="pi pi-info-circle text-[12px]" aria-hidden="true" />
                        </button>
                    </template>
                    <template #actions>
                        <SelectButton v-model="line" :options="lineOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('visitAnalytics.monthly.lineLabel')" />
                    </template>
                    <EmptyState v-if="!m.totals.pageViews" icon="pi pi-chart-bar" :title="t('visitAnalytics.monthly.empty')" :description="t('visitAnalytics.monthly.emptyHint')" />
                    <VisitMonthlyTrendChart v-else :months="m.months" :line="line" :range="m.range" />
                </AppGadget>

                <div class="grid min-w-0 items-start gap-4 lg:grid-cols-2">
                    <AppGadget :title="t('visitAnalytics.monthly.pagesTitle')">
                        <template #title-extra><span class="hidden text-[12px] font-normal text-muted-color sm:inline">{{ t('visitAnalytics.monthly.pagesHint') }}</span></template>
                        <EmptyState v-if="!m.pages.length" icon="pi pi-desktop" :title="t('visitAnalytics.pagesEmpty')" />
                        <VisitBarList v-else :rows="pageRows" :total="m.pagesTotalViews" rank />
                    </AppGadget>
                    <AppGadget :title="t('visitAnalytics.monthly.groupsTitle')">
                        <VisitCollectOff v-if="!m.collect.referrers" item="referrers" :enabled="m.collect.enabled" />
                        <EmptyState v-else-if="!m.referrers.total" icon="pi pi-directions" :title="t('visitAnalytics.referrersEmpty')" />
                        <VisitBarList v-else :rows="groupRows" :total="m.referrers.total" />
                    </AppGadget>
                </div>

                <!-- 달마다 표 — 최근 달이 위 -->
                <AppGadget :title="t('visitAnalytics.monthly.tableTitle')">
                    <div class="min-w-0 overflow-x-auto">
                        <table class="w-full border-collapse text-[13px]">
                            <caption class="sr-only">{{ t('visitAnalytics.monthly.tableCaption') }}</caption>
                            <thead>
                                <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.monthly.chartMonth') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 text-right font-normal">{{ t('visitAnalytics.monthly.visitorsDaily') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 text-right font-normal">{{ t('visitAnalytics.monthly.uniqueVisitors') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 text-right font-normal">{{ t('visitAnalytics.newVisitors') }}</th>
                                    <th scope="col" class="py-1.5 text-right font-normal">{{ t('visitAnalytics.pageViews') }}</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-(--line)">
                                <tr v-for="row in tableRows" :key="row.month">
                                    <th scope="row" class="py-1.5 pr-2 text-left font-normal whitespace-nowrap">
                                        {{ UtilVisitAnalytics.monthLabel(row.month, locale) }}
                                        <span v-if="row.partial" class="ml-1 text-[11px] text-muted-color">· {{ t('visitAnalytics.chartNow') }}</span>
                                    </th>
                                    <td class="py-1.5 pr-2 text-right tabular-nums">{{ num(row.visitorsDailySum) }}</td>
                                    <td class="py-1.5 pr-2 text-right tabular-nums" :class="row.uniqueVisitors === null ? 'text-muted-color' : ''">{{ row.uniqueVisitors === null ? t('visitAnalytics.monthly.notYet') : num(row.uniqueVisitors) }}</td>
                                    <td class="py-1.5 pr-2 text-right tabular-nums">{{ num(row.newVisitors) }}</td>
                                    <td class="py-1.5 text-right tabular-nums">{{ num(row.pageViews) }}</td>
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
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import { VISIT_MONTHLY_LINES, type VisitMonthlyLine } from '~/components/analytics/VisitMonthlyTrendChart.types';
import type { VisitStatCard } from '~/components/analytics/VisitStatCards.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

/** 고를 수 있는 달 — 이번 달부터 이만큼 앞까지(월별 표는 기한 없이 남지만 고르는 칸은 5년으로 둔다) */
const MONTH_CHOICES = 60;

const { t, te, locale } = useI18n();
useSeoMeta({ title: () => `${t('visitAnalytics.monthly.title')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const route = useRoute();
const router = useRouter();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const allowed = computed(() => can('site.read'));
const SELF = route.path;
const fromId = useId();
const toId = useId();

const monthly = useVisitLoad(api.monthly);
const m = computed(() => monthly.data.value);

// ── 기간(주소 `from`·`to` = `YYYY-MM` 가 정본) ──
const range = computed(() => UtilVisitAnalytics.monthFilterOf(route.query));
/** 길이(36개월) 때문에 시작 달을 당겼나 */
const clamped = ref(false);

const monthOptions = computed(() => {
    const lang = locale.value;
    const current = UtilVisitAnalytics.thisMonth();
    return Array.from({ length: MONTH_CHOICES }, (_, i) => {
        const ym = UtilVisitAnalytics.addMonths(current, -i);
        return { value: ym, label: UtilVisitAnalytics.monthLabel(ym, lang) };
    });
});

const presets = computed(() =>
    UtilVisitAnalytics.MONTH_PRESETS.map((n) => {
        const r = UtilVisitAnalytics.lastMonths(n);
        return { n, active: r.from === range.value.from && r.to === range.value.to };
    }),
);

function apply(from: string, to: string): void {
    const fixed = UtilVisitAnalytics.normalizeMonths(from, to);
    clamped.value = fixed.clamped;
    if (fixed.range.from === route.query.from && fixed.range.to === route.query.to) return;
    void router.replace({ query: { ...route.query, from: fixed.range.from, to: fixed.range.to } });
}

function applyPreset(n: number): void {
    const r = UtilVisitAnalytics.lastMonths(n);
    apply(r.from, r.to);
}

function load(): void {
    void monthly.load({ from: range.value.from, to: range.value.to });
}

// 기간이 바뀌면(고르기·뒤로 가기) 다시 읽는다 — 첫 읽기는 onMounted
watch(
    () => `${range.value.from}|${range.value.to}`,
    () => {
        if (route.path !== SELF || !allowed.value) return;
        load();
    },
);

onMounted(async () => {
    if (!allowed.value) return;
    const from = typeof route.query.from === 'string' ? route.query.from : '';
    const to = typeof route.query.to === 'string' ? route.query.to : '';
    if (from && to) clamped.value = UtilVisitAnalytics.normalizeMonths(from, to).clamped;
    // 주소에 고친 기간을 적는다 — 값이 같으면 기간 감시가 다시 부르지 않으므로 여기서 한 번 읽는다
    if (route.query.from !== range.value.from || route.query.to !== range.value.to) await router.replace({ query: { ...route.query, ...range.value } });
    load();
});

// ── 보이는 값 ──
const line = ref<VisitMonthlyLine>('UNIQUE');
const lineOptions = computed(() => VISIT_MONTHLY_LINES.map((value) => ({ value, label: t(`visitAnalytics.monthly.line.${value}`) })));

function num(n: number): string {
    return UtilVisitAnalytics.num(n, locale.value);
}

/** 끝난 달 중 가장 최근(진행 중인 이번 달은 뺀다) */
const lastComplete = computed(() => {
    const list = m.value?.months ?? [];
    for (let i = list.length - 1; i >= 0; i--) if (!list[i]!.partial) return list[i]!;
    return null;
});

const cards = computed<VisitStatCard[] | null>(() => {
    const d = m.value;
    if (!d) return null;
    const lang = locale.value;
    const n = Math.max(1, d.months.length);
    const avg = (v: number) => t('visitAnalytics.monthly.totalsMonthlyAvg', { n: UtilVisitAnalytics.num(Math.round(v / n), lang) });
    const last = lastComplete.value;
    return [
        { key: 'visitors', label: t('visitAnalytics.monthly.totalsVisitors'), value: num(d.totals.visitorsDailySum), sub: avg(d.totals.visitorsDailySum), help: t('visitAnalytics.monthly.dailySumHelp') },
        {
            key: 'new',
            label: t('visitAnalytics.monthly.totalsNew'),
            value: num(d.totals.newVisitors),
            sub: t('visitAnalytics.newRate', { rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.newRate({ visitors: d.totals.visitorsDailySum, newVisitors: d.totals.newVisitors }), lang) }),
            help: t('visitAnalytics.newHelp'),
        },
        { key: 'views', label: t('visitAnalytics.monthly.totalsViews'), value: num(d.totals.pageViews), sub: avg(d.totals.pageViews), help: t('visitAnalytics.pageViewsHelp') },
        {
            key: 'mau',
            label: t('visitAnalytics.monthly.uniqueVisitors'),
            value: last?.uniqueVisitors == null ? '—' : num(last.uniqueVisitors),
            sub: last ? `${UtilVisitAnalytics.monthLabel(last.month, lang)}${last.uniqueVisitors == null ? ` · ${t('visitAnalytics.monthly.notYet')}` : ''}` : '',
            help: t('visitAnalytics.uniques.helpVisitor'),
        },
    ];
});

const tableRows = computed(() => [...(m.value?.months ?? [])].reverse());

function kindName(kind: string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}

const pageRows = computed<VisitBarRow[]>(() =>
    (m.value?.pages ?? []).map((p) => ({ key: p.path, label: UtilVisitPathMessages.label(t, localizedName, p), title: p.path, mono: !UtilVisitPathMessages.named(p), tag: kindName(p.kind), value: p.views })),
);

const groupRows = computed<VisitBarRow[]>(() => UtilVisitAnalyticsMessages.groupRows(t, m.value?.referrers.groups ?? []));

// 한 줄 요약 — 가장 많이 본 페이지 · 방문자 일별 합이 가장 많았던 달 · 지난달 순방문자
const insights = computed(() => {
    const d = m.value;
    if (!d || !d.collect.visits || !d.totals.pageViews) return [];
    const lang = locale.value;
    const out: string[] = [];
    const top = d.pages[0];
    if (top && d.pagesTotalViews > 0) {
        out.push(t('visitAnalytics.monthly.insightTop', { page: UtilVisitPathMessages.label(t, localizedName, top), rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.share(top.views, d.pagesTotalViews), lang, 0) }));
    }
    const best = d.months.filter((x) => !x.partial).reduce((a, x) => (x.visitorsDailySum > (a?.visitorsDailySum ?? 0) ? x : a), null as (typeof d.months)[number] | null);
    if (best) out.push(t('visitAnalytics.monthly.insightBestMonth', { month: UtilVisitAnalytics.monthLabel(best.month, lang) }));
    const last = lastComplete.value;
    if (last?.uniqueVisitors != null) out.push(t('visitAnalytics.monthly.insightMau', { month: UtilVisitAnalytics.monthLabel(last.month, lang), n: num(last.uniqueVisitors) }));
    return out;
});
</script>
