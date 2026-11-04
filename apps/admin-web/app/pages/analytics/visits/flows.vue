<template>
    <!--
        방문자 통계 › 이동 흐름(`/analytics/visits/flows?from&to&path`) — 기준 페이지 하나를 가운데 둔 흐름 그림 + 상위 이동 10 + 기준 페이지 찾기(`VisitFlowPanel`).
        기준 페이지는 주소 `path` 가 정본(페이지 서랍의 「이동 흐름에서 보기」가 싣는다). 없으면 서버가 고른다(기간에 들어온 이동이 가장 많은 페이지) — 고르면 주소에 적는다.
        한 줄 요약: 기간 전체 가장 많은 이동 · 기준 페이지로 들어온 이동 중 「바로 들어옴」 비율. 이동 수집이 꺼져 있으면 「수집이 꺼져 있어요」.
        권한: `site.read`(메뉴 `visitFlows`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitFlows')" :subtitle="t('visitAnalytics.flowsSubtitle')" :as-of="s?.asOf ?? null" :busy="busy" :range="allowed" :insights="insights" @refresh="loadAll" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="summary.error.value" :title="t('visitAnalytics.flowsTitle')">
            <ErrorState :code="summary.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.summaryLoadFailed')" :description="summary.error.value.message" :retry="summary.error.value.status !== 403" :retrying="busy" @retry="loadAll" />
        </AppGadget>
        <Skeleton v-else-if="!s" height="24rem" border-radius="0.75rem" />
        <VisitFlowPanel
            v-else
            :data="flows.data.value"
            :loading="flows.loading.value"
            :error="flows.error.value"
            :collect="s.collect"
            :suggestions="suggestions"
            :searching="searching"
            @select="pickBase"
            @search="onSearch"
            @retry="loadFlows"
        />
    </div>
</template>

<script setup lang="ts">
import type { VisitPageRow } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitFlows')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const vr = useVisitRange();
const route = useRoute();
const router = useRouter();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const allowed = computed(() => can('site.read'));
const SELF = route.path;

const summary = useVisitLoad(api.summary);
const flows = useVisitLoad(api.flows);
const s = computed(() => summary.data.value);
const busy = computed(() => summary.loading.value || flows.loading.value);

/** 기준 페이지(주소 `path`) — 없으면 서버가 고른다 */
const basePath = computed(() => (typeof route.query.path === 'string' && route.query.path.startsWith('/') ? route.query.path : null));

function rangeArg() {
    return { from: vr.range.value.from, to: vr.range.value.to };
}

function loadFlows(): void {
    void flows.load({ ...rangeArg(), ...(basePath.value ? { path: basePath.value } : {}) });
}

async function loadAll(): Promise<void> {
    const found = await summary.load(rangeArg());
    if (!found) return;
    if (found.collect.flows) loadFlows();
    else flows.clear();
}

vr.onRange(() => void loadAll(), allowed);

// 주소의 기준이 바뀌면(고르기·뒤로 가기) 흐름만 다시
watch(basePath, () => {
    if (route.path !== SELF || !s.value?.collect.flows) return;
    loadFlows();
});

function pickBase(path: string): void {
    if (path === basePath.value) return;
    void router.replace({ query: { ...route.query, path } });
}

const suggestions = ref<VisitPageRow[]>([]);
const searching = ref(false);
let sseq = 0;
async function onSearch(q: string): Promise<void> {
    const mine = ++sseq;
    searching.value = true;
    try {
        const found = await api.pages({ ...rangeArg(), q: q.trim(), page: 0, size: 10 });
        if (mine === sseq) suggestions.value = found.items;
    } catch {
        if (mine === sseq) suggestions.value = [];
    } finally {
        if (mine === sseq) searching.value = false;
    }
}

// ── 한 줄 요약 ──
const insights = computed(() => {
    const d = flows.data.value;
    if (!d) return [];
    const lang = locale.value;
    const out: string[] = [];
    const top = d.top[0];
    if (top) out.push(t('visitAnalytics.insightTopMove', { from: top.from, to: top.to, n: UtilVisitAnalytics.num(top.moves, lang) }));
    const direct = d.incoming.find((e) => e.path === null);
    if (direct && d.incomingTotal > 0) {
        out.push(`${d.title ? localizedName(d.title, d.titleEn) : d.path} — ${t('visitAnalytics.insightDirectShare', { rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.share(direct.moves, d.incomingTotal), lang, 0) })}`);
    }
    return out;
});
</script>
