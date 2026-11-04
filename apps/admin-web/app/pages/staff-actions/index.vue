<template>
    <!--
        스텝 처리 감독 — 게시판 매니저·스텝이 맡은 게시판에서 한 글 숨기기·글 삭제·댓글 삭제를 모든 게시판에 걸쳐 보고, 필요하면 되돌린다. 계약 `docs/features/board-staff-action-contract.md` 「화면 > admin-web」.
        모양·구조는 게시판 제재 이력(`pages/board-sanctions/index.vue`)과 같다.
        - 조회 폼: 처리자 닉네임 검색 · 되돌림(전체·그대로·되돌림) · 게시판 · 종류 · 처리일 기간 · 가운데 검색·초기화.
          검색어·기간은 「검색」(Enter)을 눌러야, 되돌림·게시판·종류는 고르는 즉시 주소로 간다. **모두 주소 쿼리**(`?board=&kind=&restored=&q=&from=&to=&page=&size=&action=`).
          기간은 사이트 시간대 그날 시작·끝을 UTC ISO 로 보낸다(다른 화면과 같다).
        - 표: 게시판 · 종류 · 대상(글 제목/댓글 앞부분 + 작성자, 지금 상태) · 사유 코드 · 처리자(닉네임 + 매니저/스텝) · 시각 · 되돌림.
          줄을 누르면 서랍(`BoardStaffActionDetailDrawer`, `?action=` — replace, 목록을 다시 받지 않는다). 이전/다음은 지금 쪽의 줄 순서.
        - 되돌리기: 사유 1~200자(`MemberReasonDialog`), `keeper-action.restore`. `409 BOARD_STAFF_ACTION_RESTORED`·`POST_STATE_INVALID`·`POST_PURGED` 는 토스트(화면 문구) + 새로 받기.
        - 권한: 보기 `keeper-action.read`(메뉴). `keeper-action.restore` 가 없으면 읽기 전용 안내.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('staffAction.title')" :subtitle="t('staffAction.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('sanction.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('staffAction.searchLabel')" novalidate @submit.prevent="submitSearch">
            <div class="flex flex-wrap items-center gap-2">
                <InputText
                    v-model="searchText"
                    type="search"
                    :maxlength="UtilBoardStaffAction.SEARCH_MAX"
                    :placeholder="t('staffAction.searchPlaceholder')"
                    :aria-label="t('staffAction.searchLabel')"
                    :invalid="Boolean(searchMessage)"
                    autocomplete="off"
                    class="min-w-0 flex-1 basis-56"
                />
                <Button v-if="filters.q" type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" :aria-label="t('sanction.searchClear')" @click="clearSearch" />
            </div>
            <p v-if="searchMessage" role="alert" class="-mt-1 text-xs text-red-600 dark:text-red-400">{{ searchMessage }}</p>

            <FilterRow :label="t('staffAction.filterRestored')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.restored" :options="restoredOptions" :labelledby="labelId" @update:model-value="(v) => setRestored(v as BoardStaffActionRestoredFilter)" />
                </template>
            </FilterRow>
            <FilterRow :label="t('staffAction.filterBoard')" :label-for="`${uid}-board`">
                <Select :model-value="filters.board || ALL" :input-id="`${uid}-board`" :options="boardOptions" option-label="label" option-value="value" filter class="w-56" @update:model-value="setBoard" />
            </FilterRow>
            <FilterRow :label="t('staffAction.filterKind')" :label-for="`${uid}-kind`">
                <Select :model-value="filters.kind || ALL" :input-id="`${uid}-kind`" :options="kindOptions" option-label="label" option-value="value" class="w-48" @update:model-value="setKind" />
            </FilterRow>
            <FilterRow :label="t('staffAction.filterPeriod')" :error="fieldErrors.from || fieldErrors.to || ''">
                <DateRangeField v-model:from="dateFrom" v-model:to="dateTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('sanction.filterFrom')" :to-label="t('sanction.filterTo')" />
            </FilterRow>

            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy && !refreshing" />
                <Button type="button" :label="t('sanction.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
            </div>
        </form>

        <div class="flex min-w-0 flex-col gap-2">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p v-if="view" class="text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('sanction.total', { count: view.total.toLocaleString(locale) }) }}</p>
                <p class="text-xs text-muted-color">{{ t('staffAction.sortNote') }}</p>
                <p v-if="!canRestore" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('staffAction.readOnly') }}</p>
            </div>

            <div class="staff-action-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="items"
                    data-key="id"
                    lazy
                    paginator
                    :first="(filters.page - 1) * filters.size"
                    :rows="filters.size"
                    :total-records="view?.total ?? 0"
                    :rows-per-page-options="[...UtilSanction.PAGE_SIZES]"
                    :loading="busy && !refreshing"
                    :size="prefs.density === 'compact' ? 'small' : undefined"
                    row-hover
                    scrollable
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @row-click="(e: { data: AdminBoardStaffAction }) => openLine(e.data)"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('staffAction.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="hasConditions" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('sanction.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-user-edit" :title="t('staffAction.empty')" :description="t('staffAction.emptyHint')" />
                    </template>

                    <Column :header="t('staffAction.col.board')" style="min-width: 7rem">
                        <template #body="{ data }"><span class="block truncate text-[13px]">{{ localizedName(data.board.name, data.board.nameEn) }}</span></template>
                    </Column>
                    <Column :header="t('staffAction.col.kind')" style="min-width: 7.5rem">
                        <template #body="{ data }"><BoardStaffActionTag type="kind" :value="data.kind" /></template>
                    </Column>
                    <Column :header="t('staffAction.col.target')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 flex-col gap-0.5">
                                <button type="button" class="block max-w-full truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" :title="UtilBoardStaffActionMessages.targetTitle(t, data)" @click.stop="openLine(data)">
                                    <i v-if="data.kind === 'COMMENT_DELETE'" class="pi pi-comment mr-1 text-[10px] text-muted-color" aria-hidden="true" />{{ UtilBoardStaffActionMessages.targetTitle(t, data) }}
                                </button>
                                <span class="flex min-w-0 items-center gap-1.5 text-[11px] text-muted-color">
                                    <span class="truncate">{{ UtilBoardStaffActionMessages.author(t, data.target.author) }}</span>
                                    <BoardStaffActionTag type="state" :value="data.target.state" class="shrink-0" />
                                </span>
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('staffAction.col.reason')" style="min-width: 8rem">
                        <template #body="{ data }"><span class="block truncate text-[13px]">{{ UtilBoardStaffActionMessages.reasonName(t, data.reasonCode) }}</span></template>
                    </Column>
                    <Column :header="t('staffAction.col.actor')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="flex min-w-0 items-center gap-1.5 text-[13px]">
                                <span class="truncate">{{ UtilBoardStaffActionMessages.actorName(t, data.actor) }}</span>
                                <span v-if="data.actor" class="shrink-0 rounded border border-(--line) px-1 text-[11px] text-muted-color">{{ UtilBoardStaffActionMessages.roleName(t, data.actor.role) }}</span>
                            </span>
                        </template>
                    </Column>
                    <Column :header="t('staffAction.col.at')" style="min-width: 7rem">
                        <template #body="{ data }"><time :datetime="data.createdAt" :title="time.full(data.createdAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.createdAt) }}</time></template>
                    </Column>
                    <Column :header="t('staffAction.col.restored')" style="min-width: 6.5rem">
                        <template #body="{ data }">
                            <span v-if="data.restored" class="flex flex-col items-start gap-0.5" :title="UtilBoardStaffActionMessages.restoredBy(t, data.restored)">
                                <BoardStaffActionTag type="restored" restored />
                                <span class="text-[11px] text-muted-color">{{ t(`staffAction.restoredBy.${data.restored.by.kind}`) }}</span>
                            </span>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column style="width: 3rem">
                        <template #header><span class="sr-only">{{ t('staffAction.col.actions') }}</span></template>
                        <template #body="{ data }">
                            <Button
                                v-if="UtilBoardStaffAction.canRestore(data, canRestore)"
                                v-tooltip.left="t('staffAction.restore')"
                                type="button"
                                icon="pi pi-undo"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :disabled="acting"
                                :aria-label="t('staffAction.restoreRow', { kind: UtilBoardStaffActionMessages.kindName(t, data.kind) })"
                                @click.stop="openRestore(data)"
                            />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <MemberReasonDialog
            v-model:visible="restoreOpen"
            :title="t('staffAction.restoreTitle')"
            :label="t('staffAction.restoreReasonLabel')"
            :submit-label="t('staffAction.restore')"
            submit-icon="pi pi-undo"
            :notice="restoreTarget ? t(`staffAction.restoreNotice.${restoreTarget.kind}`) : ''"
            :min="UtilBoardStaffAction.REASON_MIN"
            :max="UtilBoardStaffAction.REASON_MAX"
            :busy="acting"
            :error="restoreError"
            :field-error="restoreFieldError"
            @submit="submitRestore"
        >
            <p v-if="restoreTarget" class="m-0 flex flex-wrap items-center gap-1.5 text-sm">
                <BoardStaffActionTag type="kind" :value="restoreTarget.kind" />
                <b class="min-w-0 truncate">{{ UtilBoardStaffActionMessages.targetTitle(t, restoreTarget) }}</b>
                <span class="text-xs text-muted-color">{{ localizedName(restoreTarget.board.name, restoreTarget.board.nameEn) }} · {{ UtilBoardStaffActionMessages.actor(t, restoreTarget.actor) }}</span>
            </p>
        </MemberReasonDialog>

        <BoardStaffActionDetailDrawer
            v-model:visible="drawerOpen"
            :action="drawerLine"
            :loading="drawerLoading"
            :error="drawerError"
            :can-restore="canRestore"
            :can-read-posts="canReadPosts"
            :busy="acting"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            @retry="openId && loadLine(openId)"
            @restore="openRestore"
            @prev="neighbors.prev && openLine(neighbors.prev)"
            @next="neighbors.next && openLine(neighbors.next)"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminBoardStaffAction, BoardStaffActionKind, BoardStaffActionListView, BoardStaffActionRestoredFilter } from '~/composables/sanction/api/useBoardStaffActionApi.types';
import UtilBoardStaffAction, { type BoardStaffActionFilters } from '~/utils/util-board-staff-action';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';
import UtilDateRange from '~/utils/util-date-range';
import UtilSanction from '~/utils/util-sanction';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('staffAction.title') });

const api = useBoardStaffActionApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const displayValues = useDisplayValues();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const uid = useId();
const { can } = useAdminPermission();
const canRestore = computed(() => can('keeper-action.restore'));
const canReadPosts = computed(() => can('post.read'));

/* ── 주소 ↔ 필터 ── */

const LIST_PATH = '/staff-actions';
const ALL = '__all__';
const filters = computed<BoardStaffActionFilters>(() => UtilBoardStaffAction.parse(route.query));

/** 필터를 주소에 싣는다(열린 서랍의 `action` 은 그대로). 쪽 이동은 push, 조건 변경은 replace */
function apply(next: BoardStaffActionFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilBoardStaffAction.key(next) === UtilBoardStaffAction.key(filters.value)) return;
    const open = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilBoardStaffAction.toQuery(next), ...(open ? { action: open } : {}) } });
}

function setRestored(restored: BoardStaffActionRestoredFilter): void {
    apply(UtilSanction.change(filters.value, { restored }));
}
function setBoard(value: string): void {
    apply(UtilSanction.change(filters.value, { board: value === ALL ? '' : value }));
}
function setKind(value: string): void {
    apply(UtilSanction.change(filters.value, { kind: value === ALL ? '' : (value as BoardStaffActionKind) }));
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilSanction.change(filters.value, { size: e.rows }));
    else apply(UtilSanction.change(filters.value, { page: e.page + 1 }), 'push');
}
function resetFilters(): void {
    searchText.value = '';
    dateFrom.value = '';
    dateTo.value = '';
    apply(UtilBoardStaffAction.clear(filters.value));
}
const hasConditions = computed(() => UtilBoardStaffAction.hasConditions(filters.value));

/* ── 검색어·기간(「검색」을 눌러야 주소로 간다) ── */

const searchText = ref(filters.value.q);
const dateFrom = ref(filters.value.from);
const dateTo = ref(filters.value.to);
const fieldErrors = ref<Record<string, string>>({});
const searchMessage = computed(() => fieldErrors.value.q || '');

// ⚠ `computed` 다 — 언어를 바꾸면 다시 계산된다
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const restoredOptions = computed(() => UtilBoardStaffAction.RESTORED.map((value) => ({ value, label: t(`staffAction.restoredFilter.${value}`) })));
const kindOptions = computed(() => [{ value: ALL, label: t('staffAction.filterAll') }, ...UtilBoardStaffAction.KINDS.map((value) => ({ value, label: UtilBoardStaffActionMessages.kindName(t, value) }))]);
/** 게시판 고르개 — 목록 응답의 `boards`. 주소의 게시판이 아직 목록에 없으면 번호로 */
const boardOptions = computed(() => {
    const boards = view.value?.boards ?? [];
    const list = [{ value: ALL, label: t('staffAction.filterBoardAll') }, ...boards.map((b) => ({ value: b.id, label: localizedName(b.name, b.nameEn) }))];
    if (filters.value.board && !boards.some((b) => b.id === filters.value.board)) list.push({ value: filters.value.board, label: `#${filters.value.board}` });
    return list;
});

watch(
    () => [filters.value.q, filters.value.from, filters.value.to] as const,
    ([q, from, to]) => {
        searchText.value = q;
        dateFrom.value = from;
        dateTo.value = to;
    },
);

function submitSearch(): void {
    const next = UtilSanction.change(filters.value, { q: UtilBoardStaffAction.cleanSearch(searchText.value), from: dateFrom.value, to: dateTo.value });
    if (UtilBoardStaffAction.key(next) === UtilBoardStaffAction.key(filters.value)) void load();
    else apply(next);
}
function clearSearch(): void {
    searchText.value = '';
    apply(UtilSanction.change(filters.value, { q: '' }));
}

/* ── 목록 ── */

const view = ref<BoardStaffActionListView | null>(null);
const items = computed(() => view.value?.items ?? []);
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
    if (!silent) {
        loadError.value = null;
        fieldErrors.value = {};
    }
    try {
        const found = await api.list(UtilBoardStaffAction.toApi(filters.value, displayValues.value.zoneId), abort.signal);
        if (mine !== seq) return;
        view.value = found;
        loadError.value = null;
        fieldErrors.value = {};
        if (found.items.length === 0 && found.total > 0 && filters.value.page > 1) apply(UtilSanction.change(filters.value, { page: UtilSanction.pageCount(found.total, found.size || filters.value.size) }));
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        const fields = error.status === 400 ? (error.fields ?? {}) : {};
        if (['q', 'from', 'to'].some((key) => key in fields)) {
            fieldErrors.value = { ...fields };
            view.value = null;
        } else if (silent && view.value) toast.error(t('sanction.refreshFailed', { reason: error.message }), 8000);
        else {
            view.value = null;
            loadError.value = error;
        }
    } finally {
        if (mine === seq) {
            busy.value = false;
            refreshing.value = false;
        }
    }
}

/** 바뀐 처리로 목록 줄을 바꿔 끼운다 */
function patchRow(next: AdminBoardStaffAction): void {
    const list = view.value;
    if (!list) return;
    const at = list.items.findIndex((a) => a.id === next.id);
    if (at < 0) return;
    const copy = [...list.items];
    copy[at] = next;
    view.value = { ...list, items: copy };
}

/* ── 되돌리기 ── */

const acting = ref(false);
const restoreOpen = ref(false);
const restoreTarget = ref<AdminBoardStaffAction | null>(null);
const restoreError = ref('');
const restoreFieldError = ref('');

function openRestore(action: AdminBoardStaffAction): void {
    if (!UtilBoardStaffAction.canRestore(action, canRestore.value)) return;
    restoreTarget.value = action;
    restoreError.value = '';
    restoreFieldError.value = '';
    restoreOpen.value = true;
}

async function submitRestore(reason: string): Promise<void> {
    const target = restoreTarget.value;
    if (!target || acting.value) return;
    acting.value = true;
    restoreError.value = '';
    restoreFieldError.value = '';
    try {
        const next = await api.restore(target.id, reason);
        restoreOpen.value = false;
        toast.success(t(`staffAction.restored.${next.kind}`));
        patchRow(next);
        if (drawerLine.value?.id === next.id) drawerLine.value = next;
        void load(true);
    } catch (e) {
        const error = errorOf(e);
        if (['BOARD_STAFF_ACTION_RESTORED', 'POST_STATE_INVALID', 'POST_PURGED', 'BOARD_STAFF_ACTION_NOT_FOUND'].includes(error.code)) {
            restoreOpen.value = false;
            toast.error(UtilBoardStaffActionMessages.restoreProblem(t, error.code, error.message), 8000);
            void load(true);
            if (openId.value) void loadLine(openId.value);
            return;
        }
        restoreFieldError.value = error.status === 400 ? (error.field('reason') ?? '') : '';
        restoreError.value = restoreFieldError.value ? '' : error.message;
    } finally {
        acting.value = false;
    }
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const drawerLine = ref<AdminBoardStaffAction | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);
const neighbors = computed(() => UtilSanction.neighbors(items.value, openId.value));
let lineTicket = 0;

/** 처리 하나를 받는다 — 목록에 있던 줄이면 그 줄을 먼저 보이고 새 값으로 바꾼다 */
async function loadLine(id: string): Promise<void> {
    const mine = ++lineTicket;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.get(id);
        if (mine !== lineTicket) return;
        drawerLine.value = found;
    } catch (e) {
        if (mine !== lineTicket) return;
        drawerLine.value = null;
        drawerError.value = errorOf(e);
    } finally {
        if (mine === lineTicket) drawerLoading.value = false;
    }
}

function show(id: string): void {
    drawerOpen.value = true;
    if (openId.value === id && drawerLine.value) return;
    openId.value = id;
    drawerError.value = null;
    drawerLine.value = items.value.find((a) => a.id === id) ?? null;
    void loadLine(id);
}

function setDrawerQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilBoardStaffAction.toQuery(filters.value), ...(id ? { action: id } : {}) } });
}

function openLine(action: AdminBoardStaffAction): void {
    show(action.id);
    setDrawerQuery(action.id);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    lineTicket++;
    openId.value = null;
    drawerLine.value = null;
    drawerError.value = null;
}

function rowClass(data: AdminBoardStaffAction): string {
    return drawerOpen.value && data.id === openId.value ? 'staff-action-row-open cursor-pointer' : 'cursor-pointer';
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilBoardStaffAction.parseId(route.query) !== null) setDrawerQuery(null);
});

// 주소의 `action`(새로고침·뒤로 가기·다른 화면의 링크)을 따라 서랍을 연다
watch(
    () => UtilBoardStaffAction.parseId(route.query),
    (id) => {
        if (id && (id !== openId.value || !drawerOpen.value)) show(id);
        else if (!id && drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);

// 필터가 바뀌면 다시 받는다. 처음 한 번도 여기서(`action` 은 열쇠에 없다). ⚠ 맨 끝에 둔다(바로 한 번 도는 감시)
watch(
    () => UtilBoardStaffAction.key(filters.value),
    () => void load(),
    { immediate: true },
);
</script>

<style scoped>
.staff-action-table :deep(tr.staff-action-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
