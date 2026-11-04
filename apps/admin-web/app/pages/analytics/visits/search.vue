<template>
    <!--
        방문자 통계 › 검색어(`/analytics/visits/search`, 2단계 2026-11-04) — **우리 사이트 검색(`/search`)** 에서 쓴 검색어.
        숫자 카드 4(검색 · 결과 없음 비율 · 서로 다른 검색어 · 걸러진 검색) → 검색어 순위 표(검색 수 · 결과 없음 비율) | 결과 없는 검색어(콘텐츠를 보강하라는 신호)
        → 수집 규칙 안내(전화번호·이메일 모양·2글자 미만·50자 초과는 저장 안 함 · 외부 검색 엔진 검색어는 넘어오지 않아 없음).
        한 줄 요약: 가장 많이 찾은 검색어 · 결과 없이 끝난 검색 비율(+ 가장 많은 결과 없는 검색어).
        검색어 수집(`collect.searchTerms`)이 꺼져 있으면 쪽 전체가 「수집이 꺼져 있어요」. 권한: `site.read`(메뉴 `visitSearch`).
        서버 호출은 `useVisitAnalyticsApi().search`(`GET /api/admin/analytics/visits/search` — 사이트 검색 첫 쪽만 센다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitSearch')" :subtitle="t('visitAnalytics.search.subtitle')" :as-of="d?.asOf ?? null" :busy="search.loading.value" :range="allowed" :insights="insights" @refresh="load" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="search.error.value" :title="t('visitAnalytics.search.termsTitle')">
            <ErrorState :code="search.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.search.loadFailed')" :description="search.error.value.message" :retry="search.error.value.status !== 403" :retrying="search.loading.value" @retry="load" />
        </AppGadget>
        <template v-else-if="!d">
            <VisitStatCards />
            <Skeleton height="16rem" border-radius="0.75rem" />
        </template>
        <AppGadget v-else-if="!d.collect.searchTerms" :title="t('visitAnalytics.search.termsTitle')">
            <VisitCollectOff item="searchTerms" :enabled="d.collect.enabled" />
        </AppGadget>

        <template v-else>
            <div class="transition-opacity" :class="{ 'opacity-60': search.loading.value }" :aria-busy="search.loading.value">
                <VisitStatCards :items="cards" />
            </div>

            <AppGadget v-if="!d.totals.searches" :title="t('visitAnalytics.search.termsTitle')">
                <EmptyState icon="pi pi-search" :title="t('visitAnalytics.search.empty')" :description="t('visitAnalytics.trendEmptyHint')" />
            </AppGadget>

            <div v-else class="grid min-w-0 items-start gap-4 lg:grid-cols-5">
                <AppGadget :title="t('visitAnalytics.search.termsTitle')" class="lg:col-span-3">
                    <template #title-extra><span class="hidden text-[12px] font-normal text-muted-color sm:inline">{{ t('visitAnalytics.search.termsHint') }}</span></template>
                    <div class="min-w-0 overflow-x-auto">
                        <table class="w-full border-collapse text-[13px]">
                            <thead>
                                <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                    <th scope="col" class="w-10 py-1.5 text-center font-normal">{{ t('visitAnalytics.col.rank') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.search.colTerm') }}</th>
                                    <th scope="col" class="w-20 py-1.5 text-right font-normal">{{ t('visitAnalytics.search.colSearches') }}</th>
                                    <th scope="col" class="w-32 py-1.5 pl-3 font-normal">{{ t('visitAnalytics.search.colNoResult') }}</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-(--line)">
                                <tr v-for="(r, i) in d.terms" :key="r.term">
                                    <td class="py-2 text-center text-[12px] font-semibold tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">{{ i + 1 }}</td>
                                    <td class="max-w-56 truncate py-2 pr-2" :title="r.term">{{ r.term }}</td>
                                    <td class="py-2 text-right tabular-nums">{{ UtilVisitAnalytics.num(r.searches, locale) }}</td>
                                    <td class="py-2 pl-3">
                                        <span class="flex items-center gap-2" :title="t('visitAnalytics.search.noResultOf', { n: UtilVisitAnalytics.num(r.noResult, locale) })">
                                            <span class="h-1.5 min-w-0 flex-1 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                                                <span class="block h-full rounded-full" :class="missRate(r) >= 10 ? 'bg-amber-500' : 'bg-surface-400'" :style="{ width: `${missRate(r)}%` }" />
                                            </span>
                                            <span class="w-11 shrink-0 text-right text-[11px] tabular-nums" :class="missRate(r) >= 10 ? 'text-amber-700 dark:text-amber-400' : 'text-muted-color'">{{ UtilVisitAnalytics.percent(missRate(r), locale, 0) }}</span>
                                        </span>
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                    </div>
                </AppGadget>

                <AppGadget :title="t('visitAnalytics.search.missTitle')" class="lg:col-span-2">
                    <p class="m-0 mb-3 flex items-start gap-2 rounded-lg bg-amber-50 px-3 py-2 text-[12px] leading-relaxed text-amber-800 dark:bg-amber-950/40 dark:text-amber-300">
                        <i class="pi pi-lightbulb mt-0.5 text-[11px]" aria-hidden="true" /><span>{{ t('visitAnalytics.search.missHint') }}</span>
                    </p>
                    <p v-if="!d.noResultTerms.length" class="m-0 py-2 text-[13px] text-muted-color">{{ t('visitAnalytics.search.missEmpty') }}</p>
                    <VisitBarList v-else :rows="missRows" rank :rate="false" />
                </AppGadget>
            </div>

            <AppGadget :title="t('visitAnalytics.search.rulesTitle')">
                <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-[13px] leading-relaxed">
                    <li v-for="key in RULES" :key="key" class="flex items-start gap-2">
                        <i class="pi pi-info-circle mt-1 text-[11px] text-muted-color" aria-hidden="true" /><span>{{ t(`visitAnalytics.search.${key}`) }}</span>
                    </li>
                </ul>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import type { VisitStatCard } from '~/components/analytics/VisitStatCards.types';
import type { VisitSearchTerm } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

/** 수집 규칙 안내 줄 */
const RULES = ['ruleOurSite', 'rulePrivacy', 'ruleLength', 'ruleNormalize', 'ruleExternal'] as const;

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitSearch')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const time = useDisplayTime();
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));

const search = useVisitLoad(api.search);
const d = computed(() => search.data.value);

function load(): void {
    void search.load({ from: vr.range.value.from, to: vr.range.value.to });
}

vr.onRange(load, allowed);

/** 결과 없음 비율(%) */
function missRate(r: VisitSearchTerm): number {
    return UtilVisitAnalytics.share(r.noResult, r.searches);
}

const missRows = computed<VisitBarRow[]>(() =>
    (d.value?.noResultTerms ?? []).map((r) => ({ key: r.term, label: r.term, value: r.searches, sub: t('visitAnalytics.search.lastDay', { date: time.value.serviceDate(r.lastDay) }), color: 'var(--p-amber-500)' })),
);

const cards = computed<VisitStatCard[] | null>(() => {
    const v = d.value;
    if (!v) return null;
    const lang = locale.value;
    const num = (n: number) => UtilVisitAnalytics.num(n, lang);
    const miss = UtilVisitAnalytics.share(v.totals.noResult, v.totals.searches);
    return [
        { key: 'searches', label: t('visitAnalytics.search.cardSearches'), value: num(v.totals.searches), help: t('visitAnalytics.search.cardSearchesHelp') },
        {
            key: 'miss',
            label: t('visitAnalytics.search.cardNoResult'),
            value: v.totals.searches ? UtilVisitAnalytics.percent(miss, lang) : '—',
            sub: t('visitAnalytics.search.cardNoResultSub', { n: num(v.totals.noResult) }),
            tone: miss >= 10 ? 'warn' : undefined,
        },
        { key: 'terms', label: t('visitAnalytics.search.cardTerms'), value: num(v.totals.terms) },
        { key: 'filtered', label: t('visitAnalytics.search.cardFiltered'), value: num(v.totals.filtered), help: t('visitAnalytics.search.cardFilteredHelp') },
    ];
});

// ── 한 줄 요약 ──
const insights = computed(() => {
    const v = d.value;
    if (!v || !v.collect.searchTerms || !v.totals.searches) return [];
    const lang = locale.value;
    const out: string[] = [];
    const top = v.terms[0];
    if (top) out.push(t('visitAnalytics.search.insightTop', { term: top.term, n: UtilVisitAnalytics.num(top.searches, lang) }));
    const rate = UtilVisitAnalytics.percent(UtilVisitAnalytics.share(v.totals.noResult, v.totals.searches), lang, 0);
    const miss = v.noResultTerms[0];
    out.push(miss ? t('visitAnalytics.search.insightMiss', { rate, term: miss.term, n: UtilVisitAnalytics.num(miss.searches, lang) }) : t('visitAnalytics.search.insightMissRate', { rate }));
    return out;
});
</script>
