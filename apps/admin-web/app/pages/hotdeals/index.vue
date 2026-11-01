<template>
    <!--
        핫딜 › 핫딜 글(`/hotdeals`) — 회원 핫딜 글을 상태별로 보고 종료·재개한다. 계약 `docs/features/hotdeal-contract.md`, 설계 03-hotdeal 7-1(글 관리 `/posts` 와 같은 골격).
        서버 호출은 `useHotdealApi`(`/api/admin/hotdeals`) · 게시판 고르기는 `useHotdealBoards`.
        - 조건 카드: 상태 `SelectButton`(진행·의심·종료·기간 지남·전체 + 건수) → 게시판 `Select`(핫딜 게시판) → 차단 도메인 진행 중 체크(`blockedActive` + 건수) → 검색(제목·쇼핑몰·작성자, 2자 이상) → 등록일 기간.
          상태·게시판·차단은 고르는 즉시, 검색어·기간은 [검색](Enter)을 눌러야 주소로 간다. **모두 주소 쿼리**(`?view=&board=&blocked=1&q=&from=&to=&page=&size=&deal=`).
        - 표: 썸네일 · 제목(말머리 · 제휴 · 아래 줄에 「차단 도메인」·「링크 바뀜」·「가격 수정 N회」) · 게시판 · 쇼핑몰 · 딜가 · 정상가 · 할인율 · 상태 · 종료 요청(Badge) · 신고 · 작성자 · 등록 시각.
          줄을 누르면 서랍(`?deal=` — replace, 목록은 다시 받지 않는다). 서랍 「수정 이력」 탭은 처음 열 때 이력을 받는다(D3).
        - 조치: 서랍 바닥 [종료]/[재개] → 사유 대화창(선택, `HotdealReasonDialog`) → 토스트. 재개 창은 D2 안내(자동·운영진 종료는 운영진만, 작성자 종료는 24시간 안 작성자가 되돌릴 수 있음)와
          마감이 지난 딜이면 「작성자가 마감을 고쳐야 해요」. 409(그 사이 바뀜)·404 는 토스트 + 새로 받기.
        - 상태: 대기 Skeleton 5줄 · 빈 「핫딜 글이 없어요」 + 게시판 설정 링크(`board.read`) · 오류 `Message` + 다시 시도.
        - 권한: 보기 `hotdeal.read`(메뉴), 종료·재개 `hotdeal.manage`(없으면 읽기 전용 안내 + 단추 없음). 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('hotdeal.title')" :subtitle="t('hotdeal.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('hotdeal.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('hotdeal.searchLabel')" novalidate @submit.prevent="submitSearch">
            <FilterRow :label="t('hotdeal.filterView')">
                <template #default="{ labelId }">
                    <SelectButton :model-value="filters.view" :options="viewOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="labelId" @update:model-value="setView" />
                </template>
            </FilterRow>
            <FilterRow :label="t('hotdeal.filterBoard')" :label-for="`${uid}-board`">
                <Select :model-value="filters.board || ALL" :input-id="`${uid}-board`" :options="boardOptions" option-label="label" option-value="value" :loading="boardsLoading" class="w-56" @update:model-value="setBoard" />
                <p v-if="boardsError" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" role="alert">
                    {{ t('hotdeal.boardsFailed') }} {{ boardsError }}
                    <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="loadBoards(true)" />
                </p>
            </FilterRow>
            <FilterRow :label="t('hotdeal.filterSearch')" :label-for="`${uid}-q`" :error="searchError">
                <InputText
                    :id="`${uid}-q`"
                    v-model="searchText"
                    type="search"
                    :maxlength="UtilHotdeal.SEARCH_MAX"
                    :placeholder="t('hotdeal.searchPlaceholder')"
                    :invalid="Boolean(searchError)"
                    autocomplete="off"
                    class="min-w-0 flex-1 basis-64"
                />
            </FilterRow>
            <FilterRow :label="t('hotdeal.filterBlocked')">
                <div class="flex flex-wrap items-center gap-2">
                    <Checkbox :model-value="filters.blocked" binary :input-id="`${uid}-blocked`" @update:model-value="setBlocked" />
                    <label :for="`${uid}-blocked`" class="text-sm">{{ page ? t('hotdeal.blockedActiveCount', { n: page.blockedActiveCount }) : t('hotdeal.blockedActive') }}</label>
                    <span class="text-xs text-muted-color">{{ t('hotdeal.blockedActiveHelp') }}</span>
                </div>
            </FilterRow>
            <FilterRow :label="t('hotdeal.filterCreated')">
                <DateRangeField v-model:from="createdFrom" v-model:to="createdTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('hotdeal.filterCreatedFrom')" :to-label="t('hotdeal.filterCreatedTo')" />
            </FilterRow>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy && !refreshing" />
                <Button type="button" :label="t('hotdeal.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
            </div>
        </form>

        <div class="flex min-w-0 flex-col gap-2">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p v-if="page" class="text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('hotdeal.total', { count: page.total.toLocaleString(locale) }) }}</p>
                <p class="text-xs text-muted-color">{{ t('hotdeal.sortNote') }}</p>
                <p v-if="!canManage" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.readOnly') }}</p>
            </div>

            <Message v-if="loadError" severity="error" :closable="false" icon="pi pi-exclamation-circle">
                <span class="flex flex-wrap items-center gap-2">
                    {{ t('hotdeal.loadFailed') }} — {{ loadError.message }}
                    <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="load()" />
                </span>
            </Message>

            <div v-else class="hotdeal-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="items"
                    data-key="postId"
                    lazy
                    paginator
                    :first="(filters.page - 1) * filters.size"
                    :rows="filters.size"
                    :total-records="page?.total ?? 0"
                    :rows-per-page-options="[...UtilHotdeal.PAGE_SIZES]"
                    :loading="busy && !refreshing && page !== null"
                    :size="prefs.density === 'compact' ? 'small' : undefined"
                    row-hover
                    scrollable
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @row-click="(e) => openDeal(e.data)"
                >
                    <template #empty>
                        <div v-if="!page" class="flex flex-col gap-3 px-2 py-3" role="status" :aria-label="t('common.loading')" aria-busy="true">
                            <Skeleton v-for="n in 5" :key="n" height="2.25rem" />
                        </div>
                        <EmptyState v-else-if="page.counts.ALL === 0 && !hasConditions" icon="pi pi-ticket" :title="t('hotdeal.empty')" :description="t('hotdeal.emptyHint')">
                            <Button v-if="perm.canReadBoards.value" type="button" :label="t('hotdeal.boardSettings')" icon="pi pi-cog" size="small" severity="secondary" variant="outlined" @click="go('/boards')" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-filter-slash" :title="t('hotdeal.emptyFiltered')">
                            <Button v-if="filters.view !== 'ALL'" type="button" :label="t('hotdeal.showAll')" size="small" severity="secondary" variant="outlined" @click="setView('ALL')" />
                            <Button v-if="hasConditions" type="button" :label="t('hotdeal.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                    </template>

                    <Column style="width: 3.5rem">
                        <template #header><span class="sr-only">{{ t('hotdeal.col.image') }}</span></template>
                        <template #body="{ data }">
                            <HotdealThumb :url="data.deal.image?.url ?? null" :alt="t('hotdeal.imageAlt', { title: data.title })" :dim="isDim(data)" />
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.title')" style="min-width: 16rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                <span v-if="data.prefix" class="shrink-0 text-xs text-muted-color">[{{ data.prefix.name }}]</span>
                                <Tag v-if="data.deal.affiliate" :value="t('hotdeal.affiliate')" severity="info" class="text-[11px]!" />
                                <button type="button" class="min-w-0 text-left text-[13px] font-medium break-words [overflow-wrap:anywhere] hover:underline focus-visible:outline-2 focus-visible:outline-primary" :class="isDim(data) ? 'text-muted-color' : ''" @click.stop="openDeal(data)">
                                    {{ data.title }}
                                </button>
                            </div>
                            <div v-if="data.blockedHost || data.deal.revision.linkChanged || data.deal.revision.priceEdits > 0" class="mt-1 flex flex-wrap items-center gap-1">
                                <Tag v-if="data.blockedHost" v-tooltip.top="t('hotdeal.blockedHostTip', { host: data.blockedHost })" :value="t('hotdeal.blockedTag')" icon="pi pi-ban" severity="danger" class="text-[11px]!" />
                                <Tag v-if="data.deal.revision.linkChanged" :value="t('hotdeal.linkChanged')" icon="pi pi-link" severity="warn" class="text-[11px]!" />
                                <Tag v-if="data.deal.revision.priceEdits > 0" :value="t('hotdeal.priceEdits', { n: data.deal.revision.priceEdits })" severity="secondary" class="text-[11px]!" />
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.board')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]">{{ localizedName(data.board.name, data.board.nameEn) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.store')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]" :title="data.deal.productHost">{{ data.deal.storeName }}</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.dealPrice')" style="min-width: 6.5rem">
                        <template #body="{ data }">
                            <b class="text-[13px] tabular-nums">{{ UtilHotdealMessages.won(t, locale, data.deal.dealPrice) }}</b>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.normalPrice')" style="min-width: 6.5rem">
                        <template #body="{ data }">
                            <s v-if="data.deal.normalPrice !== null" class="text-[12px] tabular-nums text-muted-color">{{ UtilHotdealMessages.won(t, locale, data.deal.normalPrice) }}</s>
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.discount')" style="min-width: 4.5rem">
                        <template #body="{ data }">
                            <span v-if="data.deal.discountRate !== null" class="text-[12px] font-semibold tabular-nums text-red-600 dark:text-red-400">{{ t('hotdeal.discount', { n: data.deal.discountRate }) }}</span>
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.state')" style="min-width: 6.5rem">
                        <template #body="{ data }">
                            <HotdealViewTag :view="data.deal.view" />
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.endRequests')" style="min-width: 5.5rem">
                        <template #body="{ data }">
                            <Badge v-if="data.deal.endRequests.total > 0" v-tooltip.top="t('hotdeal.endRequestsLabel', { n: data.deal.endRequests.total })" :value="data.deal.endRequests.total" :severity="data.deal.view === 'FLAGGED' ? 'warn' : 'secondary'" />
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.reports')" style="min-width: 5rem">
                        <template #body="{ data }">
                            <NuxtLink v-if="data.openReports > 0 && perm.canReadReports.value" :to="UtilPost.reportsLink(data.postId)" class="inline-flex rounded-md focus-visible:outline-2 focus-visible:outline-primary" @click.stop>
                                <Tag :value="t('hotdeal.reportsPending', { n: data.openReports })" icon="pi pi-flag" severity="danger" class="cursor-pointer text-[11px]! hover:underline" />
                            </NuxtLink>
                            <Tag v-else-if="data.openReports > 0" :value="t('hotdeal.reportsPending', { n: data.openReports })" icon="pi pi-flag" severity="danger" class="text-[11px]!" />
                            <span v-else class="text-[12px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.author')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]" :class="data.author.withdrawn ? 'text-muted-color' : ''">{{ UtilHotdealMessages.author(t, data.author) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('hotdeal.col.createdAt')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <time :datetime="data.createdAt" :title="time.full(data.createdAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.createdAt) }}</time>
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <HotdealReasonDialog
            v-model:visible="reasonOpen"
            :kind="reasonKind"
            :target="reasonTarget?.title ?? ''"
            :subtitle="reasonTarget ? `${reasonTarget.deal.storeName} · #${reasonTarget.postId}` : ''"
            :expired="reasonTarget ? UtilHotdeal.endsAtPassed(reasonTarget.deal.endsAt ?? reasonTarget.deal.autoExpireAt) : false"
            :ended-by="reasonTarget?.deal.endedBy ?? null"
            :author-reopen-until="reasonTarget?.deal.reopenUntil ?? null"
            :busy="acting"
            :error="reasonError"
            :field-error="reasonFieldError"
            @submit="submitReason"
        />

        <HotdealDetailDrawer
            v-model:visible="drawerOpen"
            :item="drawerItem"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :busy="acting"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            :can-manage="canManage"
            :can-read-reports="perm.canReadReports.value"
            :can-read-members="perm.canReadMembers.value"
            :revisions="revisions"
            :revisions-loading="revisionsLoading"
            :revisions-error="revisionsError"
            @retry="loadDetail(openId)"
            @need-revisions="loadRevisions(openId)"
            @act="openAct"
            @prev="neighbors.prev && openDeal(neighbors.prev)"
            @next="neighbors.next && openDeal(neighbors.next)"
            @open-deal="openDealId"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminDealDetail, AdminDealPage, AdminDealRow, DealRevisionListView, DealViewFilter } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { HotdealReasonKind } from '~/components/hotdeal/HotdealReasonDialog.vue';
import UtilDateRange from '~/utils/util-date-range';
import UtilHotdeal, { type HotdealFilters } from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';
import UtilPost from '~/utils/util-post';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('hotdeal.title') });

const api = useHotdealApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const displayValues = useDisplayValues();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const perm = useHotdealPermission();
const canManage = perm.canManage;
const uid = useId();
const { boards, loading: boardsLoading, error: boardsError, load: loadBoards } = useHotdealBoards();

const LIST_PATH = '/hotdeals';

function go(path: string): void {
    void navigateTo(path);
}
const ALL = '__all__';

/* ── 주소 ↔ 조건 ── */

const filters = computed<HotdealFilters>(() => UtilHotdeal.parse(route.query));
const hasConditions = computed(() => UtilHotdeal.hasConditions(filters.value));

/** 조건을 주소에 싣는다(열린 서랍의 `deal` 은 그대로). 쪽 이동은 push, 조건 변경은 replace */
function apply(next: HotdealFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilHotdeal.key(next) === UtilHotdeal.key(filters.value)) return;
    const deal = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilHotdeal.toQuery(next), ...(deal ? { deal } : {}) } });
}

function setView(value: DealViewFilter): void {
    apply(UtilHotdeal.change(filters.value, { view: value }));
}
function setBlocked(value: boolean): void {
    apply(UtilHotdeal.change(filters.value, { blocked: value }));
}
function setBoard(value: string): void {
    apply(UtilHotdeal.change(filters.value, { board: value === ALL ? '' : value }));
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilHotdeal.change(filters.value, { size: e.rows }));
    else apply(UtilHotdeal.change(filters.value, { page: e.page + 1 }), 'push');
}

const searchText = ref(filters.value.q);
const createdFrom = ref(filters.value.from);
const createdTo = ref(filters.value.to);
const searchError = ref('');

watch(
    () => [filters.value.q, filters.value.from, filters.value.to] as const,
    ([q, from, to]) => {
        searchText.value = q;
        createdFrom.value = from;
        createdTo.value = to;
        searchError.value = '';
    },
);

function submitSearch(): void {
    if (UtilHotdeal.searchTooShort(searchText.value)) {
        searchError.value = t('hotdeal.searchTooShort');
        return;
    }
    searchError.value = '';
    const next = UtilHotdeal.change(filters.value, { q: searchText.value.trim(), from: createdFrom.value, to: createdTo.value });
    if (UtilHotdeal.key(next) === UtilHotdeal.key(filters.value)) void load();
    else apply(next);
}

function resetFilters(): void {
    searchText.value = '';
    createdFrom.value = '';
    createdTo.value = '';
    searchError.value = '';
    apply({ ...UtilHotdeal.emptyFilters(), view: filters.value.view, size: filters.value.size });
}

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const viewOptions = computed(() =>
    UtilHotdeal.VIEW_FILTERS.map((value) => ({ value, label: page.value ? t('hotdeal.viewCount', { name: t(`hotdeal.viewFilter.${value}`), n: page.value.counts[value] }) : t(`hotdeal.viewFilter.${value}`) })),
);
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const boardOptions = computed(() => {
    const list = [{ value: ALL, label: t('hotdeal.allBoards') }, ...boards.value.map((b) => ({ value: b.id, label: localizedName(b.name, b.nameEn) }))];
    const id = filters.value.board;
    if (id && !list.some((o) => o.value === id)) list.push({ value: id, label: `#${id}` });
    return list;
});

/* ── 목록 ── */

const page = ref<AdminDealPage | null>(null);
const items = computed(() => page.value?.items ?? []);
const busy = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);

let seq = 0;
let abort: AbortController | null = null;
onBeforeUnmount(() => {
    seq++;
    abort?.abort();
});

async function load(silent = false): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    abort = new AbortController();
    busy.value = true;
    refreshing.value = silent;
    if (!silent) loadError.value = null;
    try {
        const found = await api.list(UtilHotdeal.toApi(filters.value, displayValues.value.zoneId), abort.signal);
        if (mine !== seq) return;
        page.value = found;
        loadError.value = null;
        if (found.items.length === 0 && found.total > 0 && filters.value.page > 1) apply(UtilHotdeal.change(filters.value, { page: UtilHotdeal.pageCount(found.total, found.size || filters.value.size) }));
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        if (silent && page.value) toast.error(t('hotdeal.refreshFailed', { reason: error.message }), 8000);
        else {
            page.value = null;
            loadError.value = error;
        }
    } finally {
        if (mine === seq) {
            busy.value = false;
            refreshing.value = false;
        }
    }
}

onMounted(() => void loadBoards());

function isDim(row: AdminDealRow): boolean {
    return row.deal.view === 'ENDED' || row.deal.view === 'EXPIRED';
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const detail = ref<AdminDealDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
let detailSeq = 0;

async function loadDetail(id: string | null, silent = false): Promise<void> {
    if (!id) return;
    const mine = ++detailSeq;
    detailLoading.value = true;
    if (!silent) {
        detail.value = null;
        detailError.value = null;
    }
    try {
        const found = await api.detail(id);
        if (mine !== detailSeq) return;
        detail.value = found;
        detailError.value = null;
    } catch (e) {
        if (mine !== detailSeq) return;
        detailError.value = errorOf(e);
    } finally {
        if (mine === detailSeq) detailLoading.value = false;
    }
}

/* 수정 이력 — 서랍 「수정 이력」 탭을 처음 열 때 받는다(글이 바뀌면 비운다) */
const revisions = ref<DealRevisionListView | null>(null);
const revisionsLoading = ref(false);
const revisionsError = ref<ApiError | null>(null);
let revisionsSeq = 0;

function resetRevisions(): void {
    revisionsSeq++;
    revisions.value = null;
    revisionsLoading.value = false;
    revisionsError.value = null;
}

async function loadRevisions(id: string | null): Promise<void> {
    if (!id) return;
    const mine = ++revisionsSeq;
    revisionsLoading.value = true;
    revisionsError.value = null;
    try {
        const found = await api.revisions(id);
        if (mine === revisionsSeq) revisions.value = found;
    } catch (e) {
        if (mine === revisionsSeq) revisionsError.value = errorOf(e);
    } finally {
        if (mine === revisionsSeq) revisionsLoading.value = false;
    }
}

watch(openId, () => resetRevisions());

const drawerItem = computed<AdminDealRow | null>(() => (openId.value ? (detail.value?.row ?? items.value.find((it) => it.postId === openId.value) ?? null) : null));
const neighbors = computed(() => UtilHotdeal.neighbors(items.value, openId.value));

function rowClass(data: AdminDealRow): string {
    return drawerOpen.value && data.postId === openId.value ? 'hotdeal-row-open cursor-pointer' : 'cursor-pointer';
}

function setDealQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilHotdeal.toQuery(filters.value), ...(id ? { deal: id } : {}) } });
}

function openDealId(id: string): void {
    drawerOpen.value = true;
    if (openId.value !== id) {
        openId.value = id;
        void loadDetail(id);
    }
    setDealQuery(id);
}

function openDeal(row: AdminDealRow): void {
    openDealId(row.postId);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detailSeq++;
    openId.value = null;
    detail.value = null;
    detailError.value = null;
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilHotdeal.parseDeal(route.query) !== null) setDealQuery(null);
});

// 주소의 `deal`(새로고침·뒤로 가기·같은 딜 링크)을 따라 서랍을 연다
watch(
    () => UtilHotdeal.parseDeal(route.query),
    (id) => {
        if (id && (id !== openId.value || !drawerOpen.value)) {
            drawerOpen.value = true;
            if (id !== openId.value) {
                openId.value = id;
                void loadDetail(id);
            }
        } else if (!id && drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);

/* ── 종료·재개 ── */

const acting = ref(false);
const reasonOpen = ref(false);
const reasonKind = ref<HotdealReasonKind | null>(null);
const reasonTarget = ref<AdminDealRow | null>(null);
const reasonError = ref('');
const reasonFieldError = ref('');

/** 그 사이 다른 운영자가 바꿨거나 글이 없어졌다 — 알리고 새로 받는다 */
const STALE_CODES = new Set(['HOTDEAL_STATE_CHANGED', 'POST_NOT_FOUND']);

function openAct(kind: 'END' | 'REOPEN'): void {
    const row = detail.value?.row;
    if (!row || acting.value || !canManage.value) return;
    reasonTarget.value = row;
    reasonKind.value = kind;
    reasonError.value = '';
    reasonFieldError.value = '';
    reasonOpen.value = true;
}

async function submitReason(reason: string): Promise<void> {
    const row = reasonTarget.value;
    const kind = reasonKind.value;
    if (!row || !kind || acting.value) return;
    acting.value = true;
    reasonError.value = '';
    reasonFieldError.value = '';
    const body = reason ? { reason } : {};
    try {
        if (kind === 'END') await api.end(row.postId, body);
        else await api.reopen(row.postId, body);
    } catch (e) {
        const error = errorOf(e);
        acting.value = false;
        if (STALE_CODES.has(error.code)) {
            reasonOpen.value = false;
            toast.error(t('hotdeal.staleFailed', { reason: error.message }), 8000);
            await refreshAfter(row.postId);
            return;
        }
        if (error.fields?.reason) reasonFieldError.value = error.fields.reason;
        else reasonError.value = error.message;
        return;
    }
    acting.value = false;
    reasonOpen.value = false;
    const expired = kind === 'REOPEN' && UtilHotdeal.endsAtPassed(row.deal.endsAt ?? row.deal.autoExpireAt);
    toast.success(t(kind === 'END' ? 'hotdeal.endedToast' : expired ? 'hotdeal.reopenedExpiredToast' : 'hotdeal.reopenedToast'), expired ? 6000 : undefined);
    await refreshAfter(row.postId);
}

async function refreshAfter(id: string): Promise<void> {
    await load(true);
    if (openId.value === id) {
        await loadDetail(id, true);
        if (revisions.value) void loadRevisions(id);
    }
}

// 조건이 바뀌면 다시 받는다(처음 한 번 포함, `deal` 은 열쇠에 없다). ⚠ 맨 끝 — 위의 상태가 모두 만들어진 뒤
watch(
    () => UtilHotdeal.key(filters.value),
    () => void load(),
    { immediate: true },
);
</script>

<style scoped>
.hotdeal-table :deep(tr.hotdeal-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
