<template>
    <!--
        게시판 제재 › 이력 — 게시판 매니저·스텝이 맡은 게시판에서 준 경고·쓰기 제한(1·3·7일)을 찾아보고, 진행 중 쓰기 제한을 풀거나 제재를 무효로 한다. 계약 `docs/features/board-sanction-contract.md` 「화면 > admin-web」.
        모양·구조는 사이트 제재 화면(`pages/sanctions/index.vue`)과 같다. 탭 「반복 검토」는 `/board-sanctions/reviews`(`BoardSanctionTabs` — 열린 건 배지).
        - 조회 폼: 검색 대상(닉네임·번호) + 검색 칸 · 범위(전체·진행 중·끝남 — 건수는 거르기와 무관한 전체) · 게시판 · 종류 · 부여일 기간 · 가운데 검색·초기화.
          검색어·기간은 「검색」(Enter)을 눌러야, 범위·게시판·종류는 고르는 즉시 주소로 간다. **모두 주소 쿼리**(`?scope=&board=&kind=&field=&q=&from=&to=&userId=&sort=&page=&size=&boardSanction=`).
          회원 상세의 [게시판 제재 이력 보기]는 `?q=<닉네임>`, 줄 메뉴 「이 회원의 게시판 제재만 보기」는 `?userId=`(툴바에 풀기 칩).
        - 표: 번호 · 회원 · 게시판 · 종류 · 사유 코드 · 처리자(닉네임 + 매니저/스텝) · 시작 · 끝 · 상태 · 이의 · ⋯(자세히 · 해제 · 무효 · 이의제기 · 이 회원만).
          줄을 누르면 서랍(`BoardSanctionDetailDrawer`, `?boardSanction=` — replace, 목록을 다시 받지 않는다). 이전/다음은 지금 쪽의 줄 순서.
        - 해제(진행 중 쓰기 제한 → LIFTED)·무효(→ VOIDED, 반복 검토·권장 수에서 빠짐): 사유 1~200자(결정 11)(`MemberReasonDialog`), `sanction.manage`.
          `409 BOARD_SANCTION_NOT_LIFTABLE`·`BOARD_SANCTION_ALREADY_VOIDED` 는 토스트(서버 문구) + 새로 받기.
        - 권한: 보기 `sanction.read`(메뉴). `sanction.manage` 가 없으면 읽기 전용 안내.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('boardSanction.title')" :subtitle="t('boardSanction.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('sanction.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="refreshAll" />
            </template>
        </PageTitle>

        <BoardSanctionTabs active="history" :open-count="openReviews" />

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('boardSanction.searchLabel')" novalidate @submit.prevent="submitSearch">
            <div class="flex flex-wrap items-center gap-2">
                <Select v-model="searchField" :options="searchFieldOptions" option-label="label" option-value="value" :aria-label="t('sanction.searchFieldLabel')" class="w-32 shrink-0" />
                <InputText
                    v-model="searchText"
                    type="search"
                    :maxlength="UtilSanction.SEARCH_MAX"
                    :placeholder="t(`boardSanction.searchPlaceholder.${searchField}`)"
                    :aria-label="t('boardSanction.searchLabel')"
                    :invalid="Boolean(searchMessage)"
                    autocomplete="off"
                    class="min-w-0 flex-1 basis-56"
                />
                <Button v-if="filters.q" type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" :aria-label="t('sanction.searchClear')" @click="clearSearch" />
            </div>
            <p v-if="searchMessage" role="alert" class="-mt-1 text-xs text-red-600 dark:text-red-400">{{ searchMessage }}</p>

            <FilterRow :label="t('sanction.filterScope')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.scope" :options="scopeOptions" :labelledby="labelId" @update:model-value="(v) => setScope(v as SanctionScope)" />
                </template>
            </FilterRow>
            <FilterRow :label="t('boardSanction.filterBoard')" :label-for="`${uid}-board`">
                <Select :model-value="filters.board || ALL" :input-id="`${uid}-board`" :options="boardOptions" option-label="label" option-value="value" filter class="w-56" @update:model-value="setBoard" />
            </FilterRow>
            <FilterRow :label="t('sanction.filterKind')" :label-for="`${uid}-kind`">
                <Select :model-value="filters.kind || ALL" :input-id="`${uid}-kind`" :options="kindOptions" option-label="label" option-value="value" class="w-48" @update:model-value="setKind" />
            </FilterRow>
            <FilterRow :label="t('sanction.filterIssued')" :error="fieldErrors.from || fieldErrors.to || ''">
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
                <p class="text-xs text-muted-color">{{ t('sanction.sortBy', { dir: t(filters.sort === 'oldest' ? 'sanction.sortAsc' : 'sanction.sortDesc') }) }}</p>
                <span v-if="filters.userId" class="inline-flex items-center gap-1 rounded-full border border-(--line) py-0.5 pr-0.5 pl-2.5 text-xs">
                    <i class="pi pi-filter text-[10px]" aria-hidden="true" />{{ t('sanction.userFilter', { name: userFilterName }) }}
                    <Button type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" rounded class="h-6! w-6!" :aria-label="t('sanction.userFilterClear')" @click="clearUserFilter" />
                </span>
                <p v-if="!canManage" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('boardSanction.readOnly') }}</p>
            </div>

            <div class="board-sanction-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
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
                    sort-field="startsAt"
                    :sort-order="filters.sort === 'oldest' ? 1 : -1"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="(e: { data: BoardSanctionLine }) => openLine(e.data)"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('boardSanction.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="hasConditions" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('sanction.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-list-check" :title="t('boardSanction.empty')" :description="t('boardSanction.emptyHint')" />
                    </template>

                    <Column :header="t('boardSanction.col.no')" style="min-width: 4.5rem">
                        <template #body="{ data }"><span class="text-[12px] tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, data) }}</span></template>
                    </Column>
                    <Column :header="t('boardSanction.col.member')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <button type="button" class="block max-w-full truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openLine(data)">{{ data.user.nickname }}</button>
                        </template>
                    </Column>
                    <Column :header="t('boardSanction.col.board')" style="min-width: 7rem">
                        <template #body="{ data }"><span class="block truncate text-[13px]">{{ localizedName(data.board.name, data.board.nameEn) }}</span></template>
                    </Column>
                    <Column :header="t('boardSanction.col.kind')" style="min-width: 8.5rem">
                        <template #body="{ data }"><BoardSanctionTag type="kind" :kind="data.kind" /></template>
                    </Column>
                    <Column :header="t('boardSanction.col.reason')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]" :class="data.status === 'VOIDED' ? 'text-muted-color line-through' : ''" :title="UtilBoardSanctionMessages.reasonLine(t, data)">{{ UtilBoardSanctionMessages.reasonName(t, data.reasonCode) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('boardSanction.col.issuedBy')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="flex min-w-0 items-center gap-1.5 text-[13px]">
                                <span class="truncate">{{ data.issuedBy?.nickname ?? t('boardSanction.issuerGone') }}</span>
                                <span v-if="data.issuedBy" class="shrink-0 rounded border border-(--line) px-1 text-[11px] text-muted-color">{{ UtilBoardSanctionMessages.roleLabel(t, data.issuedBy) }}</span>
                            </span>
                        </template>
                    </Column>
                    <Column field="startsAt" sortable :header="t('boardSanction.col.startsAt')" style="min-width: 7rem">
                        <template #body="{ data }"><time :datetime="data.startsAt" :title="time.full(data.startsAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.startsAt) }}</time></template>
                    </Column>
                    <Column :header="t('boardSanction.col.endsAt')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <time v-if="data.endsAt" :datetime="data.endsAt" :title="time.full(data.endsAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.endsAt) }}</time>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('boardSanction.col.status')" style="min-width: 6.5rem">
                        <template #body="{ data }"><BoardSanctionTag type="status" :kind="data.kind" :status="data.status" /></template>
                    </Column>
                    <Column :header="t('boardSanction.col.appeal')" style="min-width: 5.5rem">
                        <template #body="{ data }">
                            <SanctionTag v-if="data.appeal" type="appeal" :status="data.appeal.status" />
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column style="width: 3rem">
                        <template #header><span class="sr-only">{{ t('boardSanction.col.actions') }}</span></template>
                        <template #body="{ data }">
                            <Button type="button" icon="pi pi-ellipsis-h" size="small" severity="secondary" variant="text" rounded :disabled="acting" :aria-label="t('boardSanction.actionsMenu', { no: data.no })" aria-haspopup="menu" @click.stop="openRowMenu($event, data)" />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <Menu ref="rowMenu" :model="rowMenuItems" popup />

        <MemberReasonDialog
            v-model:visible="actOpen"
            :title="t(actKind === 'void' ? 'boardSanction.voidTitle' : 'boardSanction.liftTitle')"
            :label="t(actKind === 'void' ? 'boardSanction.voidReasonLabel' : 'boardSanction.liftReasonLabel')"
            :submit-label="t(actKind === 'void' ? 'boardSanction.void' : 'boardSanction.lift')"
            :submit-icon="actKind === 'void' ? 'pi pi-undo' : 'pi pi-lock-open'"
            :danger="actKind === 'void'"
            :notice="t(actKind === 'void' ? 'boardSanction.voidNotice' : 'boardSanction.liftNotice')"
            :min="1"
            :max="UtilBoardSanction.REASON_MAX"
            :busy="acting"
            :error="actError"
            :field-error="actFieldError"
            @submit="submitAct"
        >
            <p v-if="actTarget" class="m-0 flex flex-wrap items-center gap-1.5 text-sm">
                <b>{{ actTarget.user.nickname }}</b>
                <span class="text-xs text-muted-color">{{ localizedName(actTarget.board.name, actTarget.board.nameEn) }}</span>
                <BoardSanctionTag type="kind" :kind="actTarget.kind" />
                <span v-if="actTarget.endsAt" class="text-xs text-muted-color tabular-nums">{{ t('sanction.endsAt', { at: time.full(actTarget.endsAt) }) }}</span>
            </p>
        </MemberReasonDialog>

        <BoardSanctionDetailDrawer
            v-model:visible="drawerOpen"
            :line="drawerLine"
            :history="drawerHistory"
            :loading="drawerLoading"
            :error="drawerError"
            :can-manage="canManage"
            :busy="acting"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            @retry="openId && loadLine(openId)"
            @lift="(l) => openAct(l, 'lift')"
            @void="(l) => openAct(l, 'void')"
            @prev="neighbors.prev && openLine(neighbors.prev)"
            @next="neighbors.next && openLine(neighbors.next)"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import type { ApiError } from '~/composables/common/useApiClient';
import type { BoardSanctionDetail, BoardSanctionKind, BoardSanctionLine, BoardSanctionListView } from '~/composables/sanction/api/useBoardSanctionApi.types';
import type { SanctionScope } from '~/composables/sanction/api/useSanctionApi.types';
import UtilBoardSanction, { type BoardSanctionFilters } from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilDateRange from '~/utils/util-date-range';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('boardSanction.title') });

const api = useBoardSanctionApi();
const reviewApi = useBoardSanctionReviewApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const displayValues = useDisplayValues();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const uid = useId();
const permission = useSanctionPermission();
const canManage = computed(() => permission.canManage.value);

/* ── 탭 배지(열린 반복 검토 수) ── */

const openReviews = ref<number | null>(null);
async function loadSummary(): Promise<void> {
    try {
        openReviews.value = (await reviewApi.summary()).open;
    } catch {
        // 배지만 못 그린다 — 화면은 그대로
        openReviews.value = null;
    }
}
void loadSummary();

/* ── 주소 ↔ 필터 ── */

const LIST_PATH = '/board-sanctions';
const ALL = '__all__';
const filters = computed<BoardSanctionFilters>(() => UtilBoardSanction.parse(route.query));

/** 필터를 주소에 싣는다(열린 서랍의 `boardSanction` 은 그대로). 쪽 이동은 push, 조건 변경은 replace */
function apply(next: BoardSanctionFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilBoardSanction.key(next) === UtilBoardSanction.key(filters.value)) return;
    const open = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilBoardSanction.toQuery(next), ...(open ? { boardSanction: open } : {}) } });
}

function setScope(scope: SanctionScope): void {
    apply(UtilSanction.change(filters.value, { scope }));
}
function setBoard(value: string): void {
    apply(UtilSanction.change(filters.value, { board: value === ALL ? '' : value }));
}
function setKind(value: string): void {
    apply(UtilSanction.change(filters.value, { kind: value === ALL ? '' : (value as BoardSanctionKind) }));
}
function clearUserFilter(): void {
    apply(UtilSanction.change(filters.value, { userId: '' }));
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilSanction.change(filters.value, { size: e.rows }));
    else apply(UtilSanction.change(filters.value, { page: e.page + 1 }), 'push');
}
function onSort(e: { sortOrder?: number | null }): void {
    apply(UtilSanction.change(filters.value, { sort: e.sortOrder === 1 ? 'oldest' : 'newest' }));
}
function resetFilters(): void {
    searchField.value = 'NICKNAME';
    searchText.value = '';
    dateFrom.value = '';
    dateTo.value = '';
    searchError.value = '';
    apply(UtilBoardSanction.clear(filters.value));
}
const hasConditions = computed(() => UtilBoardSanction.hasConditions(filters.value));

/* ── 검색어·기간(「검색」을 눌러야 주소로 간다) ── */

const searchField = ref(filters.value.field);
const searchText = ref(filters.value.q);
const dateFrom = ref(filters.value.from);
const dateTo = ref(filters.value.to);
const searchError = ref('');
const fieldErrors = ref<Record<string, string>>({});
const searchMessage = computed(() => searchError.value || fieldErrors.value.q || '');

// ⚠ `computed` 다 — 언어를 바꾸면 다시 계산된다
const searchFieldOptions = computed(() => UtilSanction.SEARCH_FIELDS.map((value) => ({ value, label: t(`sanction.searchField.${value}`) })));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const kindOptions = computed(() => [{ value: ALL, label: t('sanction.filterAll') }, ...UtilBoardSanction.KINDS.map((value) => ({ value, label: UtilBoardSanctionMessages.kindName(t, value) }))]);
/** 게시판 고르개 — 목록 응답의 `boards`(게시판 제재가 있는 게시판). 주소의 게시판이 아직 목록에 없으면 번호로 */
const boardOptions = computed(() => {
    const boards = view.value?.boards ?? [];
    const list = [{ value: ALL, label: t('boardSanction.filterBoardAll') }, ...boards.map((b) => ({ value: b.id, label: localizedName(b.name, b.nameEn) }))];
    if (filters.value.board && !boards.some((b) => b.id === filters.value.board)) list.push({ value: filters.value.board, label: `#${filters.value.board}` });
    return list;
});
const scopeOptions = computed(() => {
    const counts = view.value?.counts;
    const n = { ALL: counts?.all, ACTIVE: counts?.active, CLOSED: counts?.closed } as Record<SanctionScope, number | undefined>;
    return UtilSanction.SCOPES.map((value) => ({ value, label: n[value] === undefined ? t(`sanction.scope.${value}`) : t('sanction.scopeCount', { name: t(`sanction.scope.${value}`), n: n[value]!.toLocaleString(locale.value) }) }));
});

watch(
    () => [filters.value.field, filters.value.q, filters.value.from, filters.value.to] as const,
    ([field, q, from, to]) => {
        searchField.value = field;
        searchText.value = q;
        dateFrom.value = from;
        dateTo.value = to;
        searchError.value = '';
    },
);

function submitSearch(): void {
    const parsed = UtilSanction.parseSearch(searchField.value, searchText.value);
    if (parsed.kind === 'TOO_SHORT' || parsed.kind === 'NUMBER_INVALID') {
        searchError.value = UtilSanctionMessages.searchProblem(t, parsed.kind);
        return;
    }
    searchError.value = '';
    const next = UtilSanction.change(filters.value, { field: searchField.value, q: parsed.text, from: dateFrom.value, to: dateTo.value });
    if (UtilBoardSanction.key(next) === UtilBoardSanction.key(filters.value)) void load();
    else apply(next);
}
function clearSearch(): void {
    searchText.value = '';
    searchError.value = '';
    apply(UtilSanction.change(filters.value, { q: '' }));
}

/* ── 목록 ── */

const view = ref<BoardSanctionListView | null>(null);
const items = computed(() => view.value?.items ?? []);
const busy = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);
const userFilterName = computed(() => items.value.find((l) => l.user.id === filters.value.userId)?.user.nickname ?? `#${filters.value.userId}`);

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
        const found = await api.list(UtilBoardSanction.toApi(filters.value, displayValues.value.zoneId), abort.signal);
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

function refreshAll(): void {
    void load(true);
    void loadSummary();
}

/** 바뀐 제재로 목록 줄을 바꿔 끼운다 */
function patchRow(next: BoardSanctionLine): void {
    const list = view.value;
    if (!list) return;
    const at = list.items.findIndex((l) => l.id === next.id);
    if (at < 0) return;
    const copy = [...list.items];
    copy[at] = next;
    view.value = { ...list, items: copy };
}

/* ── 해제·무효 ── */

const acting = ref(false);
const actOpen = ref(false);
const actKind = ref<'lift' | 'void'>('lift');
const actTarget = ref<BoardSanctionLine | null>(null);
const actError = ref('');
const actFieldError = ref('');

function openAct(line: BoardSanctionLine, kind: 'lift' | 'void'): void {
    const allowed = kind === 'lift' ? UtilBoardSanction.canLift(line, canManage.value) : UtilBoardSanction.canVoid(line, canManage.value);
    if (!allowed) return;
    actKind.value = kind;
    actTarget.value = line;
    actError.value = '';
    actFieldError.value = '';
    actOpen.value = true;
}

async function submitAct(reason: string): Promise<void> {
    const target = actTarget.value;
    if (!target || acting.value) return;
    acting.value = true;
    actError.value = '';
    actFieldError.value = '';
    try {
        const next = actKind.value === 'lift' ? await api.lift(target.id, reason) : await api.void(target.id, reason);
        actOpen.value = false;
        toast.success(actKind.value === 'lift' ? t('boardSanction.lifted', { name: next.sanction.user.nickname }) : t('boardSanction.voided', { no: next.sanction.no }));
        patchRow(next.sanction);
        if (drawerLine.value && drawerLine.value.user.id === next.sanction.user.id) applyDetail(next, drawerLine.value.id === next.sanction.id);
        void load(true);
        if (actKind.value === 'void') void loadSummary();
    } catch (e) {
        const error = errorOf(e);
        if (['BOARD_SANCTION_NOT_LIFTABLE', 'BOARD_SANCTION_ALREADY_VOIDED', 'BOARD_SANCTION_NOT_FOUND'].includes(error.code)) {
            actOpen.value = false;
            toast.error(t('sanction.staleFailed', { reason: error.message }), 8000);
            void load(true);
            if (openId.value) void loadLine(openId.value);
            return;
        }
        actFieldError.value = error.status === 400 ? (error.field('reason') ?? '') : '';
        actError.value = actFieldError.value ? '' : error.message;
    } finally {
        acting.value = false;
    }
}

/* ── 줄 메뉴 ── */

const rowMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const rowMenuTarget = ref<BoardSanctionLine | null>(null);
const rowMenuItems = computed<MenuItem[]>(() => {
    const line = rowMenuTarget.value;
    if (!line) return [];
    const menu: MenuItem[] = [{ label: t('sanction.openDetail'), icon: 'pi pi-window-maximize', command: () => openLine(line) }];
    if (UtilBoardSanction.canLift(line, canManage.value)) menu.push({ label: t('boardSanction.lift'), icon: 'pi pi-lock-open', command: () => openAct(line, 'lift') });
    if (UtilBoardSanction.canVoid(line, canManage.value)) menu.push({ label: t('boardSanction.void'), icon: 'pi pi-undo', command: () => openAct(line, 'void') });
    if (line.appeal) menu.push({ label: t('sanction.appealOpen', { no: line.appeal.no }), icon: 'pi pi-comments', command: () => void navigateTo({ path: '/appeals', query: { appeal: line.appeal!.id, status: 'ALL' } }) });
    menu.push({ label: t('boardSanction.memberOnly'), icon: 'pi pi-filter', command: () => apply(UtilSanction.change(filters.value, { userId: line.user.id })) });
    return menu;
});
function openRowMenu(event: Event, line: BoardSanctionLine): void {
    rowMenuTarget.value = line;
    rowMenu.value?.toggle(event);
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const drawerLine = ref<BoardSanctionLine | null>(null);
const drawerHistory = ref<BoardSanctionLine[] | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);
const neighbors = computed(() => UtilSanction.neighbors(items.value, openId.value));
let lineTicket = 0;

/** 받은 상세를 서랍에 — `asCurrent` 면 서랍의 제재도 바꾼다(이력만 새로 받은 경우는 이력만) */
function applyDetail(found: BoardSanctionDetail, asCurrent = true): void {
    if (asCurrent) drawerLine.value = found.sanction;
    drawerHistory.value = found.history;
}

/** 상세(그 회원의 이력 포함)를 받는다 — 목록에 있던 줄이면 그 줄을 먼저 보이고 이력만 기다린다 */
async function loadLine(id: string): Promise<void> {
    const mine = ++lineTicket;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.get(id);
        if (mine !== lineTicket) return;
        applyDetail(found);
    } catch (e) {
        if (mine !== lineTicket) return;
        drawerLine.value = null;
        drawerHistory.value = null;
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
    drawerLine.value = items.value.find((l) => l.id === id) ?? null;
    drawerHistory.value = null;
    void loadLine(id);
}

function setDrawerQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilBoardSanction.toQuery(filters.value), ...(id ? { boardSanction: id } : {}) } });
}

function openLine(line: BoardSanctionLine): void {
    show(line.id);
    setDrawerQuery(line.id);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    lineTicket++;
    openId.value = null;
    drawerLine.value = null;
    drawerHistory.value = null;
    drawerError.value = null;
}

function rowClass(data: BoardSanctionLine): string {
    return drawerOpen.value && data.id === openId.value ? 'board-sanction-row-open cursor-pointer' : 'cursor-pointer';
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilBoardSanction.parseId(route.query, 'boardSanction') !== null) setDrawerQuery(null);
});

// 주소의 `boardSanction`(새로고침·뒤로 가기·다른 화면의 링크)을 따라 서랍을 연다
watch(
    () => UtilBoardSanction.parseId(route.query, 'boardSanction'),
    (id) => {
        if (id && (id !== openId.value || !drawerOpen.value)) show(id);
        else if (!id && drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);

// 필터가 바뀌면 다시 받는다. 처음 한 번도 여기서(`boardSanction` 은 열쇠에 없다). ⚠ 맨 끝에 둔다(바로 한 번 도는 감시)
watch(
    () => UtilBoardSanction.key(filters.value),
    () => void load(),
    { immediate: true },
);
</script>

<style scoped>
.board-sanction-table :deep(tr.board-sanction-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
