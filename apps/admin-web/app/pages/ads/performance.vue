<template>
    <!--
        광고 › 광고 성과(`/ads/performance`) — 계약 `docs/features/ad-performance-contract.md`. 집계 원천 shared V118 `ad_daily_stats`.
        · 거르기(주소 쿼리에 남는다): 기간 `from`·`to`(`DateRangeField`, 기본 = 오늘 포함 최근 7일 — 오늘은 「집계 중」) · 유형 `type`(8개, `bizApplication.adType.*`) · 광고주 `advertiserId`(광고주 찾기 AutoComplete).
          건별 표 정렬 `sort`(impressions·clicks·ctr)·`dir`(asc·desc, 기본 노출 내림차순)도 주소에 남는다. 쪽 번호는 남기지 않는다(거르기를 바꾸면 첫 쪽).
        · 합계 띠(노출 · 클릭 · 클릭률 · 노출된 건) + asOf · 집계 기준 안내 → 날짜별 추이 차트(합계 — 띠 없음, 오늘 빗금) → 유형별 표 · 자리별 표(비중 막대)
          → 광고주 순위(노출 상위 10, 이름을 누르면 그 광고주로 거르기) → 건별 표(정렬·쪽 나눔은 서버 — 줄을 누르면 `/ads?type=&id=` 일정표 서랍).
        · CSV 내려받기는 후속(단추 없음).
        권한: 화면 `ad.read`(메뉴 `adPerformance` — 막는 것은 서버). 서버 호출은 `useAdApi().statsOverview`·`statsBookings`(`/api/admin/ads/stats`·`/stats/bookings`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('ad.perfTitle')" :subtitle="t('ad.perfSubtitle')" />

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('ad.perfFilters')" @submit.prevent>
            <DateRangeField v-model:from="draftFrom" v-model:to="draftTo" :presets="presets" :presets-label="t('ad.perfPresets')" :from-label="t('ad.perfFrom')" :to-label="t('ad.perfTo')" />
            <label class="sr-only" :for="typeId">{{ t('ad.perfTypeLabel') }}</label>
            <Select :model-value="filter.type ?? ''" :input-id="typeId" :options="typeOptions" option-label="label" option-value="value" class="w-44" @update:model-value="onType" />
            <div class="flex items-center gap-1">
                <label class="sr-only" :for="advId">{{ t('ad.perfAdvertiserLabel') }}</label>
                <AutoComplete
                    v-model="advertiser"
                    :input-id="advId"
                    :suggestions="suggestions"
                    option-label="name"
                    force-selection
                    :placeholder="t('ad.perfAdvertiserPlaceholder')"
                    class="w-60"
                    fluid
                    @complete="onComplete"
                    @option-select="onAdvertiserPick"
                >
                    <template #option="{ option }">
                        <span class="flex items-center gap-2 text-[13px]"><Tag :value="t(`ad.advertiserKind.${option.kind}`)" severity="secondary" />{{ option.name }}</span>
                    </template>
                </AutoComplete>
                <Button v-if="filter.advertiserId" type="button" icon="pi pi-times" severity="secondary" text rounded size="small" :aria-label="t('ad.perfAdvertiserClear')" @click="clearAdvertiser" />
            </div>
        </form>

        <!-- 기준 안내 -->
        <p class="m-0 flex flex-wrap items-start gap-x-2 gap-y-0.5 text-xs leading-relaxed text-muted-color">
            <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
            <span v-if="overview" class="tabular-nums">{{ t('ad.statAsOf', { at: time.shortDateTime(overview.asOf) }) }} ·</span>
            <span class="min-w-0 flex-1">{{ t('ad.statCountRule') }}</span>
        </p>

        <AdStatsTotals :counts="overview?.totals ?? null" :bookings="overview?.totals.bookings ?? 0" />

        <!-- 읽기 실패 -->
        <AppGadget v-if="overviewError" :title="t('ad.statDaily')">
            <ErrorState :code="overviewError.status === 403 ? '403' : '!'" :title="t('ad.perfLoadFailed')" :description="overviewError.message" :retry="overviewError.status !== 403" :retrying="overviewLoading" @retry="loadOverview" />
        </AppGadget>

        <!-- 처음 읽는 중 -->
        <template v-else-if="!overview">
            <AppGadget :title="t('ad.statDaily')">
                <div role="status" :aria-label="t('common.loading')"><Skeleton height="14rem" /></div>
            </AppGadget>
            <div class="grid gap-4 lg:grid-cols-2">
                <Skeleton v-for="n in 2" :key="n" height="12rem" border-radius="0.75rem" />
            </div>
        </template>

        <template v-else>
            <div class="flex min-w-0 flex-col gap-4 transition-opacity" :class="{ 'opacity-60': overviewLoading }" :aria-busy="overviewLoading">
                <AppGadget :title="t('ad.statDaily')">
                    <template #title-extra>
                        <span class="text-[12px] font-normal text-muted-color tabular-nums">{{ t('ad.statRange', { from: time.serviceDate(overview.range.from), to: time.serviceDate(overview.range.to) }) }}</span>
                    </template>
                    <EmptyState v-if="empty" icon="pi pi-chart-bar" :title="t('ad.perfEmpty')" :description="t('ad.perfEmptyHint')" />
                    <AdStatsChart v-else :days="overview.days" />
                </AppGadget>

                <template v-if="!empty">
                    <div class="grid items-start gap-4 lg:grid-cols-2">
                        <AppGadget :title="t('ad.statByType')">
                            <div class="-mx-1 min-w-0"><AdStatsTable :rows="typeRows" :label-header="t('ad.statCol.type')" show-bookings show-share :total="overview.totals.impressions" /></div>
                        </AppGadget>
                        <AppGadget :title="t('ad.statByPlacement')">
                            <div class="-mx-1 min-w-0"><AdStatsTable :rows="placementRows" :label-header="t('ad.statCol.placement')" show-share :total="overview.totals.impressions" /></div>
                        </AppGadget>
                    </div>

                    <AppGadget :title="t('ad.statTopAdvertisers')">
                        <template #title-extra><span class="text-[12px] font-normal text-muted-color">{{ t('ad.perfTopHint') }}</span></template>
                        <div class="-mx-1 min-w-0"><AdStatsTable :rows="advertiserRows" :label-header="t('ad.statCol.advertiser')" rank selectable show-bookings show-share :total="overview.totals.impressions" @select="pickAdvertiser" /></div>
                    </AppGadget>
                </template>
            </div>
        </template>

        <!-- 건별 -->
        <section class="flex min-w-0 flex-col gap-2" :aria-labelledby="listTitleId">
            <div class="flex flex-wrap items-baseline gap-x-2">
                <h2 :id="listTitleId" class="m-0 text-sm font-medium">{{ t('ad.statBookingList') }}</h2>
                <span class="text-[12px] text-muted-color">{{ t('ad.perfBookingsHint') }}</span>
            </div>
            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="rows"
                    data-key="id"
                    lazy
                    paginator
                    :first="first"
                    :rows="size"
                    :total-records="total"
                    :rows-per-page-options="[20, 50, 100]"
                    :loading="busy"
                    :sort-field="filter.sort"
                    :sort-order="filter.dir === 'asc' ? 1 : -1"
                    scrollable
                    :row-class="() => 'cursor-pointer'"
                    :current-page-report-template="totalTemplate"
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="onRowClick"
                >
                    <template #empty>
                        <ErrorState v-if="listError" :title="t('ad.perfBookingsFailed')" :description="listError.message" :retry="listError.status !== 403" :retrying="busy" @retry="loadBookings" />
                        <div v-else-if="busy" class="h-32" aria-hidden="true" />
                        <EmptyState v-else icon="pi pi-chart-bar" :title="t('ad.perfBookingsEmpty')" :description="t('ad.perfEmptyHint')" />
                    </template>

                    <Column :header="t('ad.col.advertiser')" style="min-width: 11rem">
                        <template #body="{ data }">
                            <span class="flex items-center gap-1.5">
                                <span class="truncate text-[13px] font-medium">{{ data.advertiser.name }}</span>
                                <Tag :value="t(`ad.advertiserKind.${data.advertiser.kind}`)" severity="secondary" />
                            </span>
                            <code v-if="data.applicationNo" class="text-[11px] text-muted-color">{{ data.applicationNo }}</code>
                            <span v-else class="text-[11px] text-muted-color">{{ t('ad.sourceKind.OPERATOR') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('ad.type')" style="min-width: 7rem">
                        <template #body="{ data }"><span class="text-[13px]">{{ t(`bizApplication.adType.${data.type}`) }}</span></template>
                    </Column>
                    <Column :header="t('ad.col.period')" style="min-width: 12rem">
                        <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ UtilBizApplicationMessages.period(t, time, data.period) }}</span></template>
                    </Column>
                    <Column :header="t('ad.col.status')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <Tag v-if="data.exposureStop" :value="t(`ad.exposureStop.${data.exposureStop}`)" severity="warn" />
                            <Tag v-else :value="t(UtilAd.statusKey(data))" :severity="UtilAd.rowSeverity(data)" />
                        </template>
                    </Column>
                    <Column field="impressions" sortable :header="t('ad.statImpressions')" style="min-width: 6rem" :pt="right">
                        <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ UtilAdStats.num(data.impressions, locale) }}</div></template>
                    </Column>
                    <Column field="clicks" sortable :header="t('ad.statClicks')" style="min-width: 5rem" :pt="right">
                        <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ UtilAdStats.num(data.clicks, locale) }}</div></template>
                    </Column>
                    <Column field="ctr" sortable :header="t('ad.statCtr')" style="min-width: 5rem" :pt="right">
                        <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ UtilAdStats.ctrText(data, locale) }}</div></template>
                    </Column>
                </DataTable>
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { AutoCompleteOptionSelectEvent } from 'primevue/autocomplete';
import type { DataTableRowClickEvent, DataTableSortEvent } from 'primevue/datatable';
import type { AdStatsTableRow } from '~/components/ad/AdStatsTable.types';
import type { AdAdvertiser, AdStatsBookingRow, AdStatsOverview, AdStatsSort, AdType } from '~/composables/ad/api/useAdApi.types';
import { AD_STATS_SORTS } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilAdStats, { type AdStatsFilter } from '~/utils/util-ad-stats';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

type AdvertiserPick = Pick<AdAdvertiser, 'id' | 'name' | 'kind'>;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('ad.perfTitle') });

const api = useAdApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const SELF = route.path;

const typeId = useId();
const advId = useId();
const listTitleId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const right = { columnHeaderContent: { class: 'justify-end' } };

// ── 거르기(주소 쿼리가 정본) ──
const filter = computed<AdStatsFilter>(() => UtilAdStats.filterOf(route.query));
function apply(patch: Partial<AdStatsFilter>): void {
    const next = UtilAdStats.queryOf({ ...filter.value, ...patch });
    const now = UtilAdStats.queryOf(filter.value);
    if (JSON.stringify(next) === JSON.stringify(now) && route.query.from) return;
    void router.replace({ query: next });
}

const presets = computed(() => (['TODAY', 'WEEK', 'MONTH1'] as const).map((kind) => ({ kind, label: t(`ad.perfPreset.${kind}`) })));
const typeOptions = computed(() => [{ value: '', label: t('ad.perfAllTypes') }, ...UtilAd.TYPES.map((value) => ({ value, label: t(`bizApplication.adType.${value}`) }))]);
function onType(value: string): void {
    apply({ type: (value || null) as AdType | null });
}

// 기간 — 입력 칸 값(draft)이 둘 다 맞는 날짜가 되면 주소로 올린다. 주소가 바뀌면(뒤로 가기 등) 칸을 따라 맞춘다
const draftFrom = ref(filter.value.from);
const draftTo = ref(filter.value.to);
watch([draftFrom, draftTo], ([from, to]) => {
    if (!from || !to) return;
    apply(from <= to ? { from, to } : { from: to, to: from });
});
watch(
    () => [filter.value.from, filter.value.to] as const,
    ([from, to]) => {
        if (route.path !== SELF) return;
        if (draftFrom.value !== from) draftFrom.value = from;
        if (draftTo.value !== to) draftTo.value = to;
    },
);

// 광고주 — 고르면 거르고, 주소에 id 만 있으면 성과 응답의 `advertiser` 로 이름을 채운다
const advertiser = ref<AdvertiserPick | string | null>(null);
const suggestions = ref<AdAdvertiser[]>([]);
async function onComplete(event: { query: string }): Promise<void> {
    try {
        suggestions.value = await api.advertisers(event.query);
    } catch {
        suggestions.value = [];
    }
}
function onAdvertiserPick(event: AutoCompleteOptionSelectEvent): void {
    const picked = event.value as AdvertiserPick;
    apply({ advertiserId: picked.id });
}
function pickAdvertiser(id: string): void {
    const row = overview.value?.topAdvertisers.find((a) => a.advertiser.id === id);
    if (row) advertiser.value = { ...row.advertiser };
    apply({ advertiserId: id });
}
function clearAdvertiser(): void {
    advertiser.value = null;
    apply({ advertiserId: null });
}

// ── 성과(합계·차트·유형별·자리별·광고주 순위) ──
const overview = ref<AdStatsOverview | null>(null);
const overviewLoading = ref(false);
const overviewError = ref<ApiError | null>(null);
const empty = computed(() => Boolean(overview.value && UtilAdStats.isEmpty(overview.value.totals)));

let oseq = 0;
async function loadOverview(): Promise<void> {
    const mine = ++oseq;
    const f = filter.value;
    overviewLoading.value = true;
    overviewError.value = null;
    try {
        const found = await api.statsOverview({ from: f.from, to: f.to, type: f.type ?? undefined, advertiserId: f.advertiserId ?? undefined });
        if (mine !== oseq) return;
        overview.value = found;
        const current = advertiser.value;
        if (found.advertiser && (typeof current !== 'object' || current?.id !== found.advertiser.id)) advertiser.value = { ...found.advertiser };
        if (!f.advertiserId && typeof current === 'object' && current) advertiser.value = null;
    } catch (e) {
        if (mine !== oseq) return;
        overview.value = null;
        overviewError.value = toApiError(e);
    } finally {
        if (mine === oseq) overviewLoading.value = false;
    }
}

const typeRows = computed<AdStatsTableRow[]>(() => (overview.value?.byType ?? []).map((r) => ({ key: r.type, label: t(`bizApplication.adType.${r.type}`), bookings: r.bookings, impressions: r.impressions, clicks: r.clicks })));
const placementRows = computed<AdStatsTableRow[]>(() => (overview.value?.byPlacement ?? []).map((r) => ({ key: r.placement, label: t(`ad.statPlacement.${r.placement}`), impressions: r.impressions, clicks: r.clicks })));
const advertiserRows = computed<AdStatsTableRow[]>(() =>
    (overview.value?.topAdvertisers ?? []).map((r) => ({ key: r.advertiser.id, label: r.advertiser.name, sub: t(`ad.advertiserKind.${r.advertiser.kind}`), bookings: r.bookings, impressions: r.impressions, clicks: r.clicks })),
);

// ── 건별 ──
const rows = ref<AdStatsBookingRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const listError = ref<ApiError | null>(null);

let lseq = 0;
async function loadBookings(): Promise<void> {
    const mine = ++lseq;
    const f = filter.value;
    busy.value = true;
    listError.value = null;
    try {
        const found = await api.statsBookings({
            from: f.from,
            to: f.to,
            type: f.type ?? undefined,
            advertiserId: f.advertiserId ?? undefined,
            sort: f.sort,
            dir: f.dir,
            page: Math.floor(first.value / size.value),
            size: size.value,
        });
        if (mine !== lseq) return;
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== lseq) return;
        rows.value = [];
        total.value = 0;
        listError.value = toApiError(e);
    } finally {
        if (mine === lseq) busy.value = false;
    }
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void loadBookings();
}

/** 다른 칸을 처음 누르면 내림차순(많은 것 먼저), 같은 칸을 다시 누르면 방향을 바꾼다 */
function onSort(e: DataTableSortEvent): void {
    const field = typeof e.sortField === 'string' && (AD_STATS_SORTS as readonly string[]).includes(e.sortField) ? (e.sortField as AdStatsSort) : 'impressions';
    apply(field === filter.value.sort ? { dir: e.sortOrder === 1 ? 'asc' : 'desc' } : { sort: field, dir: 'desc' });
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as AdStatsBookingRow;
    void router.push({ path: '/ads', query: { type: row.type, id: row.id } });
}

// 기간·유형·광고주가 바뀌면 성과와 건별 첫 쪽을, 정렬만 바뀌면 건별 첫 쪽을 다시 받는다
const scopeKey = computed(() => [filter.value.from, filter.value.to, filter.value.type ?? '', filter.value.advertiserId ?? ''].join('|'));
const sortKey = computed(() => `${filter.value.sort}|${filter.value.dir}`);
watch(scopeKey, () => {
    if (route.path !== SELF) return;
    first.value = 0;
    void loadOverview();
    void loadBookings();
});
watch(sortKey, () => {
    if (route.path !== SELF) return;
    first.value = 0;
    void loadBookings();
});

onMounted(() => {
    // 처음 들어오면 기본 기간을 주소에 적어 둔다(새로 고침·공유해도 같은 기간)
    if (!route.query.from || !route.query.to) void router.replace({ query: { ...route.query, ...UtilAdStats.queryOf(filter.value) } });
    void loadOverview();
    void loadBookings();
});
</script>
