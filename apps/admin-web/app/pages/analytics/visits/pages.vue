<template>
    <!--
        방문자 통계 › 페이지(`/analytics/visits/pages`) — 페이지 순위(종류 거르기 · 경로·제목 찾기 · 20줄씩) + 줄을 누르면 오른쪽 서랍(`VisitPageDrawer` — 일별 조회 · 들어온/나간 곳 5 · 「이동 흐름에서 보기」).
        한 줄 요약: 가장 많이 본 페이지(거른 범위 조회 합 대비 — 전체 종류·찾기 없음·첫 쪽일 때만). 종류별 비율 문장은 서버의 종류별 합이 생기면 더한다(쪽 줄로 어림하지 않는다).
        원천은 `analytics_page_daily` — 3단계부터 「방문자·페이지뷰」(`visits`) 수집에 딸린다(원본 기록을 꺼도 쌓임). 꺼져 있으면 「수집이 꺼져 있어요」.
        묶음 주소(`/posts/{id}` …)는 서버가 제목을 주지 않아 화면 이름(`UtilVisitPathMessages`)으로 보인다. 서랍에는 `{id}` 가 든 주소를 그대로 넘긴다(쿼리 인코딩만). 서랍의 들어온/나간 곳은 이동 수집이 켜져 있어야 한다.
        권한: `site.read`(메뉴 `visitPages`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitPages')" :subtitle="t('visitAnalytics.pagesSubtitle')" :as-of="s?.asOf ?? null" :busy="busy" :range="allowed" :insights="insights" @refresh="loadAll" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="summary.error.value" :title="t('visitAnalytics.pagesTitle')">
            <ErrorState :code="summary.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.summaryLoadFailed')" :description="summary.error.value.message" :retry="summary.error.value.status !== 403" :retrying="busy" @retry="loadAll" />
        </AppGadget>
        <Skeleton v-else-if="!s" height="24rem" border-radius="0.75rem" />
        <AppGadget v-else-if="!s.collect.visits" :title="t('visitAnalytics.pagesTitle')">
            <VisitCollectOff item="visits" :enabled="s.collect.enabled" />
        </AppGadget>
        <VisitPageRanking
            v-else
            :rows="pages.data.value?.items ?? []"
            :total="pages.data.value?.total ?? 0"
            :total-views="pages.data.value?.totalViews ?? 0"
            :first="first"
            :size="UtilVisitAnalytics.PAGE_SIZE"
            :loading="pages.loading.value"
            :error="pages.error.value"
            :kind="kind"
            :selected-path="drawerOpen ? selected : null"
            searchable
            :q="q"
            @page="onPage"
            @kind="onKind"
            @search="onSearch"
            @select="openDrawer"
            @retry="loadPages"
        />

        <VisitPageDrawer
            v-if="s"
            v-model:visible="drawerOpen"
            :detail="detail.data.value"
            :fallback-title="selectedTitle"
            :loading="detail.loading.value"
            :error="detail.error.value"
            :collect="s.collect"
            :flows-link="vr.linkTo(UtilVisitAnalytics.PAGES.flows, selected ? { path: selected } : {})"
            @retry="loadDetail"
        />
    </div>
</template>

<script setup lang="ts">
import type { VisitPageKind } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitPages')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const allowed = computed(() => can('site.read'));

const summary = useVisitLoad(api.summary);
const pages = useVisitLoad(api.pages);
const detail = useVisitLoad(api.pageDetail);
const s = computed(() => summary.data.value);
const busy = computed(() => summary.loading.value || pages.loading.value);

const first = ref(0);
const kind = ref<VisitPageKind | null>(null);
const q = ref('');

function rangeArg() {
    return { from: vr.range.value.from, to: vr.range.value.to };
}

function loadPages(): void {
    void pages.load({ ...rangeArg(), kind: kind.value ?? undefined, q: q.value || undefined, page: Math.floor(first.value / UtilVisitAnalytics.PAGE_SIZE), size: UtilVisitAnalytics.PAGE_SIZE });
}

async function loadAll(): Promise<void> {
    first.value = 0;
    const found = await summary.load(rangeArg());
    if (!found) return;
    // 3단계: 페이지별 집계(`analytics_page_daily`)는 「방문자·페이지뷰」 스위치에 딸린다(원본 기록과 떨어짐)
    if (found.collect.visits) loadPages();
    else pages.clear();
    if (drawerOpen.value && selected.value) loadDetail();
}

vr.onRange(() => void loadAll(), allowed);

function onPage(next: number): void {
    first.value = next;
    loadPages();
}
function onKind(next: VisitPageKind | null): void {
    kind.value = next;
    first.value = 0;
    loadPages();
}
function onSearch(next: string): void {
    q.value = next;
    first.value = 0;
    loadPages();
}

// ── 서랍 ──
const drawerOpen = ref(false);
const selected = ref<string | null>(null);
const selectedTitle = computed(() => {
    const row = pages.data.value?.items.find((r) => r.path === selected.value);
    if (!row) return selected.value ? (UtilVisitPathMessages.routeName(t, selected.value) ?? selected.value) : '';
    return UtilVisitPathMessages.label(t, localizedName, row);
});

function loadDetail(): void {
    if (!selected.value) return;
    void detail.load({ ...rangeArg(), path: selected.value });
}

function openDrawer(path: string): void {
    if (selected.value !== path) detail.clear();
    selected.value = path;
    drawerOpen.value = true;
    loadDetail();
}

// ── 한 줄 요약 ──
const insights = computed(() => {
    const d = pages.data.value;
    if (!d || first.value !== 0 || kind.value || q.value) return [];
    const top = d.items[0];
    if (!top || d.totalViews <= 0) return [];
    const lang = locale.value;
    return [t('visitAnalytics.insightTopPage', { page: UtilVisitPathMessages.label(t, localizedName, top), rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.share(top.views, d.totalViews), lang, 0) })];
});
</script>
