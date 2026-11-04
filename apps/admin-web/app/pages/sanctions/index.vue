<template>
    <!--
        제재 — 회원에게 준 사이트 제재(경고 → 경고 → 정지 3일 → 7일 → 30일 → 영구 정지)를 찾아보고, 주고(제재하기), 진행 중 정지를 푼다. 계약 `docs/features/member-sanction-contract.md`(화면 · 정책).
        모양·구조는 신고 관리(`pages/reports/index.vue`)와 같다.
        - 조회 폼: 검색 대상(닉네임·제재 번호) + 검색 칸 · 범위(전체·진행 중·끝남 — 건수는 거르기와 무관한 전체) · 종류 · 부여일 기간 · 가운데 검색·초기화.
          검색어·기간은 「검색」(Enter)을 눌러야, 범위·종류는 고르는 즉시 주소로 간다. **모두 주소 쿼리**(`?scope=&kind=&field=&q=&from=&to=&userId=&sort=&page=&size=&sanction=`).
          `userId` 는 회원 상세·신고 서랍의 「이 회원 제재 보기」가 싣는다(툴바에 풀기 칩).
        - 표: 번호 · 회원 · 종류 알약 · 상태 알약 · 기간 · 사유 앞부분 · 부여자 · 신고 사건(`/reports?report=`) · 이의제기 상태 · 부여(정렬 머리 — 최신·오래된) · ⋯(자세히 · 해제).
          줄을 누르면 서랍(`SanctionDetailDrawer`, `?sanction=` — replace, 목록을 다시 받지 않는다). 이전/다음은 지금 쪽의 줄 순서.
        - 상단 [제재하기](`sanction.manage`) → `SanctionIssueDialog`(회원 찾기 → 현황·단계 → 사유). 부여·해제 뒤 목록을 조용히 다시 받는다.
        - 해제: 사유 1~500자(`MemberReasonDialog`) — 기간 정지 `sanction.manage`, 영구 정지 `sanction.ban`. `409 SANCTION_NOT_LIFTABLE` 은 토스트(서버 문구) + 새로 받기.
        - 권한: 보기 `sanction.read`(메뉴). `sanction.manage` 가 없으면 읽기 전용 안내 + [제재하기] 없음.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('sanction.title')" :subtitle="t('sanction.subtitle')">
            <template #actions>
                <Button v-if="UtilSanction.canIssue(perm)" type="button" icon="pi pi-plus" :label="t('sanction.issue')" size="small" @click="openIssue()" />
                <Button type="button" icon="pi pi-refresh" :label="t('sanction.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('sanction.searchLabel')" novalidate @submit.prevent="submitSearch">
            <div class="flex flex-wrap items-center gap-2">
                <Select v-model="searchField" :options="searchFieldOptions" option-label="label" option-value="value" :aria-label="t('sanction.searchFieldLabel')" class="w-32 shrink-0" />
                <InputText
                    v-model="searchText"
                    type="search"
                    :maxlength="UtilSanction.SEARCH_MAX"
                    :placeholder="t(`sanction.searchPlaceholder.${searchField}`)"
                    :aria-label="t('sanction.searchLabel')"
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
            <FilterRow :label="t('sanction.filterKind')" :label-for="`${uid}-kind`">
                <Select :model-value="filters.kind || ALL" :input-id="`${uid}-kind`" :options="kindOptions" option-label="label" option-value="value" class="w-44" @update:model-value="setKind" />
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
                <p v-if="!UtilSanction.canIssue(perm)" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('sanction.readOnly') }}</p>
            </div>

            <div class="sanction-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
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
                    sort-field="createdAt"
                    :sort-order="filters.sort === 'oldest' ? 1 : -1"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="(e: { data: SanctionLine }) => openLine(e.data)"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('sanction.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="hasConditions" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('sanction.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-shield" :title="t('sanction.empty')" :description="t('sanction.emptyHint')" />
                    </template>

                    <Column :header="t('sanction.col.no')" style="min-width: 5rem">
                        <template #body="{ data }"><span class="text-[12px] tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, data) }}</span></template>
                    </Column>
                    <Column :header="t('sanction.col.member')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <button type="button" class="block max-w-full truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openLine(data)">{{ data.user.nickname }}</button>
                        </template>
                    </Column>
                    <Column :header="t('sanction.col.kind')" style="min-width: 7.5rem">
                        <template #body="{ data }">
                            <div class="flex flex-col items-start gap-0.5">
                                <SanctionTag type="kind" :kind="data.kind" :warning-no="data.warningNo" />
                                <span v-if="data.skipped" class="text-[11px] text-red-600 dark:text-red-400">{{ t('sanction.skipped') }}</span>
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('sanction.col.status')" style="min-width: 6.5rem">
                        <template #body="{ data }"><SanctionTag type="status" :kind="data.kind" :status="data.status" /></template>
                    </Column>
                    <Column :header="t('sanction.col.period')" style="min-width: 10rem">
                        <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ UtilSanctionMessages.shortPeriod(t, time, data) }}</span></template>
                    </Column>
                    <Column :header="t('sanction.col.reason')" style="min-width: 14rem">
                        <template #body="{ data }"><span class="text-[13px]" :class="data.status === 'VOIDED' ? 'text-muted-color line-through' : ''" :title="data.reason">{{ UtilSanction.reasonPreview(data.reason) }}</span></template>
                    </Column>
                    <Column :header="t('sanction.col.issuedBy')" style="min-width: 6rem">
                        <template #body="{ data }"><span class="block truncate text-[13px]">{{ UtilSanctionMessages.actor(t, data.issuedBy) }}</span></template>
                    </Column>
                    <Column :header="t('sanction.col.report')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <NuxtLink v-if="data.reportCase" :to="{ path: '/reports', query: { report: data.reportCase.id, status: 'ALL' } }" class="inline-flex items-center gap-1 text-[12px] text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop>
                                <i class="pi pi-flag text-[10px]" aria-hidden="true" />{{ t('sanction.reportCaseValue', { no: data.reportCase.caseNo }) }}
                            </NuxtLink>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('sanction.col.appeal')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <SanctionTag v-if="data.appeal" type="appeal" :status="data.appeal.status" />
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column field="createdAt" sortable :header="t('sanction.col.createdAt')" style="min-width: 7rem">
                        <template #body="{ data }"><time :datetime="data.createdAt" :title="time.full(data.createdAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.createdAt) }}</time></template>
                    </Column>
                    <Column style="width: 3rem">
                        <template #header><span class="sr-only">{{ t('sanction.col.actions') }}</span></template>
                        <template #body="{ data }">
                            <Button type="button" icon="pi pi-ellipsis-h" size="small" severity="secondary" variant="text" rounded :disabled="acting" :aria-label="t('sanction.actionsMenu', { no: data.no })" aria-haspopup="menu" @click.stop="openRowMenu($event, data)" />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <Menu ref="rowMenu" :model="rowMenuItems" popup />

        <SanctionIssueDialog v-model:visible="issue.open.value" :view="issue.view.value" :perm="perm" @search="issue.search" @pick="issue.pick" @unpick="issue.unpick" @retry-preview="issue.loadPreview" @submit="submitIssue" />

        <MemberReasonDialog
            v-model:visible="liftOpen"
            :title="t('sanction.liftTitle')"
            :label="t('sanction.liftReasonLabel')"
            :submit-label="t('sanction.lift')"
            submit-icon="pi pi-lock-open"
            :notice="t('sanction.liftNotice')"
            :min="1"
            :max="UtilSanction.TEXT_MAX"
            :busy="acting"
            :error="liftError"
            :field-error="liftFieldError"
            @submit="submitLift"
        >
            <p v-if="liftTarget" class="m-0 flex flex-wrap items-center gap-1.5 text-sm">
                <b>{{ liftTarget.user.nickname }}</b>
                <SanctionTag type="kind" :kind="liftTarget.kind" />
                <span v-if="liftTarget.endsAt" class="text-xs text-muted-color tabular-nums">{{ t('sanction.endsAt', { at: time.full(liftTarget.endsAt) }) }}</span>
            </p>
        </MemberReasonDialog>

        <SanctionDetailDrawer
            v-model:visible="drawerOpen"
            :line="drawerLine"
            :loading="drawerLoading"
            :error="drawerError"
            :preview="memberPreview.preview.value"
            :preview-loading="memberPreview.loading.value"
            :preview-error="memberPreview.error.value"
            :perm="perm"
            :busy="acting"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            @retry="openId && loadLine(openId)"
            @retry-preview="memberPreview.retry"
            @lift="openLift"
            @prev="neighbors.prev && openLine(neighbors.prev)"
            @next="neighbors.next && openLine(neighbors.next)"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import type { ApiError } from '~/composables/common/useApiClient';
import type { SanctionCreateRequest, SanctionKind, SanctionLine, SanctionListView, SanctionScope } from '~/composables/sanction/api/useSanctionApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilSanction, { type SanctionFilters } from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('sanction.title') });

const api = useSanctionApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const displayValues = useDisplayValues();
const { prefs } = useLayoutPrefs();
const uid = useId();
const permission = useSanctionPermission();
const perm = computed(() => ({ canManage: permission.canManage.value, canBan: permission.canBan.value }));

/* ── 주소 ↔ 필터 ── */

const LIST_PATH = '/sanctions';
const ALL = '__all__';
const filters = computed<SanctionFilters>(() => UtilSanction.parse(route.query));

/** 필터를 주소에 싣는다(열린 서랍의 `sanction` 은 그대로). 쪽 이동은 push, 조건 변경은 replace */
function apply(next: SanctionFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilSanction.key(next) === UtilSanction.key(filters.value)) return;
    const sanction = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilSanction.toQuery(next), ...(sanction ? { sanction } : {}) } });
}

function setScope(scope: SanctionScope): void {
    apply(UtilSanction.change(filters.value, { scope }));
}
function setKind(value: string): void {
    apply(UtilSanction.change(filters.value, { kind: value === ALL ? '' : (value as SanctionKind) }));
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
    apply(UtilSanction.clear(filters.value));
}
const hasConditions = computed(() => UtilSanction.hasConditions(filters.value));

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
const kindOptions = computed(() => [{ value: ALL, label: t('sanction.filterAll') }, ...UtilSanction.LADDER.map((value) => ({ value, label: UtilSanctionMessages.kindName(t, value) }))]);
/** 범위 — 건수는 거르기와 무관한 전체 건수(목록을 받은 뒤) */
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
    if (UtilSanction.key(next) === UtilSanction.key(filters.value)) void load();
    else apply(next);
}
function clearSearch(): void {
    searchText.value = '';
    searchError.value = '';
    apply(UtilSanction.change(filters.value, { q: '' }));
}

/* ── 목록 ── */

const view = ref<SanctionListView | null>(null);
const items = computed(() => view.value?.items ?? []);
const busy = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);
/** 회원 거르기 칩의 이름 — 받은 줄의 닉네임(없으면 번호) */
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
        const found = await api.list(UtilSanction.toApi(filters.value, displayValues.value.zoneId), abort.signal);
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

/** 바뀐 제재로 목록 줄을 바꿔 끼운다 */
function patchRow(next: SanctionLine): void {
    const list = view.value;
    if (!list) return;
    const at = list.items.findIndex((l) => l.id === next.id);
    if (at < 0) return;
    const copy = [...list.items];
    copy[at] = next;
    view.value = { ...list, items: copy };
}

/* ── 제재하기 ── */

const issue = useSanctionIssue();

function openIssue(): void {
    issue.start(null);
}

async function submitIssue(body: Omit<SanctionCreateRequest, 'userId' | 'reportCaseId'>): Promise<void> {
    const created = await issue.submit(body);
    if (!created) return;
    toast.success(UtilSanctionMessages.issued(t, created));
    await load(true);
    if (drawerOpen.value && memberPreview.userId.value === created.user.id) void memberPreview.reload();
}

/* ── 해제 ── */

const acting = ref(false);
const liftOpen = ref(false);
const liftTarget = ref<SanctionLine | null>(null);
const liftError = ref('');
const liftFieldError = ref('');

function openLift(line: SanctionLine): void {
    if (!UtilSanction.canLift(line, perm.value)) return;
    liftTarget.value = line;
    liftError.value = '';
    liftFieldError.value = '';
    liftOpen.value = true;
}

async function submitLift(reason: string): Promise<void> {
    const target = liftTarget.value;
    if (!target || acting.value) return;
    acting.value = true;
    liftError.value = '';
    liftFieldError.value = '';
    try {
        const lifted = await api.lift(target.id, reason);
        liftOpen.value = false;
        toast.success(t('sanction.lifted', { name: lifted.user.nickname }));
        patchRow(lifted);
        if (drawerLine.value?.id === lifted.id) drawerLine.value = lifted;
        void memberPreview.reload();
        void load(true);
    } catch (e) {
        const error = errorOf(e);
        if (error.code === 'SANCTION_NOT_LIFTABLE' || error.code === 'SANCTION_NOT_FOUND') {
            liftOpen.value = false;
            toast.error(t('sanction.staleFailed', { reason: error.message }), 8000);
            void load(true);
            if (openId.value) void loadLine(openId.value);
            void memberPreview.reload();
            return;
        }
        liftFieldError.value = error.status === 400 ? (error.field('reason') ?? '') : '';
        liftError.value = liftFieldError.value ? '' : error.message;
    } finally {
        acting.value = false;
    }
}

/* ── 줄 메뉴 ── */

const rowMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const rowMenuTarget = ref<SanctionLine | null>(null);
const rowMenuItems = computed<MenuItem[]>(() => {
    const line = rowMenuTarget.value;
    if (!line) return [];
    const menu: MenuItem[] = [{ label: t('sanction.openDetail'), icon: 'pi pi-window-maximize', command: () => openLine(line) }];
    if (UtilSanction.canLift(line, perm.value)) menu.push({ label: t('sanction.lift'), icon: 'pi pi-lock-open', command: () => openLift(line) });
    if (line.appeal) menu.push({ label: t('sanction.appealOpen', { no: line.appeal.no }), icon: 'pi pi-comments', command: () => void navigateTo({ path: '/appeals', query: { appeal: line.appeal!.id, status: 'ALL' } }) });
    menu.push({ label: t('sanction.memberOnly'), icon: 'pi pi-filter', command: () => apply(UtilSanction.change(filters.value, { userId: line.user.id })) });
    return menu;
});
function openRowMenu(event: Event, line: SanctionLine): void {
    rowMenuTarget.value = line;
    rowMenu.value?.toggle(event);
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const drawerLine = ref<SanctionLine | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);
const memberPreview = useSanctionPreview();
const neighbors = computed(() => UtilSanction.neighbors(items.value, openId.value));
let lineTicket = 0;

/** 지금 쪽에 없는 제재(주소로 바로 들어옴)는 하나를 받는다 */
async function loadLine(id: string): Promise<void> {
    const mine = ++lineTicket;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.get(id);
        if (mine !== lineTicket) return;
        drawerLine.value = found;
        void memberPreview.open(found.user.id);
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
    const known = items.value.find((l) => l.id === id) ?? null;
    lineTicket++;
    drawerError.value = null;
    drawerLine.value = known;
    if (known) {
        drawerLoading.value = false;
        if (memberPreview.userId.value !== known.user.id) void memberPreview.open(known.user.id);
    } else void loadLine(id);
}

function setSanctionQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilSanction.toQuery(filters.value), ...(id ? { sanction: id } : {}) } });
}

function openLine(line: SanctionLine): void {
    show(line.id);
    setSanctionQuery(line.id);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    lineTicket++;
    openId.value = null;
    drawerLine.value = null;
    drawerError.value = null;
    memberPreview.clear();
}

function rowClass(data: SanctionLine): string {
    return drawerOpen.value && data.id === openId.value ? 'sanction-row-open cursor-pointer' : 'cursor-pointer';
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilSanction.parseId(route.query, 'sanction') !== null) setSanctionQuery(null);
});

// 주소의 `sanction`(새로고침·뒤로 가기·다른 화면의 링크)을 따라 서랍을 연다
watch(
    () => UtilSanction.parseId(route.query, 'sanction'),
    (id) => {
        if (id && (id !== openId.value || !drawerOpen.value)) show(id);
        else if (!id && drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);

// 필터가 바뀌면 다시 받는다. 처음 한 번도 여기서(`sanction` 은 열쇠에 없다). ⚠ 맨 끝에 둔다(바로 한 번 도는 감시)
watch(
    () => UtilSanction.key(filters.value),
    () => void load(),
    { immediate: true },
);
</script>

<style scoped>
.sanction-table :deep(tr.sanction-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
