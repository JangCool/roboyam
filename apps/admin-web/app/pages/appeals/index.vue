<template>
    <!--
        이의제기 — 회원이 제재에 낸 이의제기를 접수 차례대로 보고 인용·기각한다. 계약 `docs/features/member-sanction-contract.md` 정책 6.
        모양은 제재 화면(`pages/sanctions/index.vue`)·신고 관리와 같다.
        - 조회: 상태 라디오(대기(기본)·인용·기각·전체 — 건수는 거르기와 무관한 전체) · 정렬은 「접수」 머리(오래된 순 기본 · 최신 순). **모두 주소 쿼리**(`?status=&sort=&page=&size=&appeal=`).
        - 표: 접수번호 · 회원 · 종류(사이트 제재/게시판 제재 — board-sanction-contract 결정 4) · 제재(단계/상태) · 게시판 · 처리자(사이트 = 운영자, 게시판 = 닉네임 + 매니저/스텝) · 상태 · 접수 시각 · 대기 시간(대기 중 하루 넘으면 강조) · 처리 시각.
        - 서랍 `?appeal=`(`AppealDetailDrawer`): 이의 사유 전문 · 대상 제재 · 근거 신고 사건 · 회원 제재 이력 · 처리 폼(답변 1~500자 + [인용]/[기각], `appeal.manage` 없으면 읽기 전용).
          `409 APPEAL_CLOSED` 는 서랍 안 「이미 처리됐어요 — 다시 불러오기」. 처리하면 목록을 조용히 다시 받는다.
        - 권한: 보기 `sanction.read`(메뉴), 처리 `appeal.manage`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('appeal.title')" :subtitle="t('appeal.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('sanction.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <div class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
            <FilterRow :label="t('appeal.filterStatus')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.status" :options="statusOptions" :labelledby="labelId" @update:model-value="(v) => setStatus(v as AppealStatusFilter)" />
                </template>
            </FilterRow>
        </div>

        <div class="flex min-w-0 flex-col gap-2">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p v-if="view" class="text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('sanction.total', { count: view.total.toLocaleString(locale) }) }}</p>
                <p class="text-xs text-muted-color">{{ t('appeal.sortBy', { dir: t(filters.sort === 'newest' ? 'sanction.sortDesc' : 'sanction.sortAsc') }) }}</p>
                <p v-if="!permission.canAnswer.value" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('appeal.readOnly') }}</p>
            </div>

            <div class="appeal-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
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
                    :sort-order="filters.sort === 'newest' ? -1 : 1"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="(e: { data: AppealItem }) => openItem(e.data)"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('appeal.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="filters.status === 'PENDING'" icon="pi pi-check-circle" :title="t('appeal.emptyPending')" :description="t('appeal.emptyPendingHint')" />
                        <EmptyState v-else icon="pi pi-inbox" :title="t('appeal.empty')" />
                    </template>

                    <Column :header="t('appeal.col.no')" style="min-width: 5rem">
                        <template #body="{ data }"><span class="text-[12px] tabular-nums text-muted-color">{{ t('appeal.noValue', { no: data.no }) }}</span></template>
                    </Column>
                    <Column :header="t('appeal.col.member')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <button type="button" class="block max-w-full truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openItem(data)">{{ UtilSanctionMessages.userName(t, data.user) }}</button>
                        </template>
                    </Column>
                    <Column :header="t('appeal.col.kind')" style="min-width: 6.5rem">
                        <template #body="{ data }"><Tag :value="t(`boardSanction.appealKind.${kindOf(data)}`)" :severity="kindOf(data) === 'BOARD' ? 'info' : 'secondary'" class="text-[11px]!" /></template>
                    </Column>
                    <Column :header="t('appeal.col.sanction')" style="min-width: 11rem">
                        <template #body="{ data }">
                            <div v-if="data.boardSanction" class="flex flex-wrap items-center gap-1">
                                <BoardSanctionTag type="kind" :kind="data.boardSanction.kind" />
                                <BoardSanctionTag type="status" :kind="data.boardSanction.kind" :status="data.boardSanction.status" />
                            </div>
                            <div v-else-if="data.sanction" class="flex flex-wrap items-center gap-1">
                                <SanctionTag type="kind" :kind="data.sanction.kind" :warning-no="data.sanction.warningNo" />
                                <SanctionTag type="status" :kind="data.sanction.kind" :status="data.sanction.status" />
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('appeal.col.board')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <span v-if="data.boardSanction" class="block truncate text-[13px]">{{ localizedName(data.boardSanction.board.name, data.boardSanction.board.nameEn) }}</span>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('appeal.col.issuedBy')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span v-if="data.boardSanction" class="flex min-w-0 items-center gap-1.5 text-[13px]">
                                <span class="truncate">{{ data.boardSanction.issuedBy?.nickname ?? t('boardSanction.issuerGone') }}</span>
                                <span v-if="data.boardSanction.issuedBy" class="shrink-0 rounded border border-(--line) px-1 text-[11px] text-muted-color">{{ UtilBoardSanctionMessages.roleLabel(t, data.boardSanction.issuedBy) }}</span>
                            </span>
                            <span v-else-if="data.sanction?.issuedBy" class="block truncate text-[13px]">{{ UtilSanctionMessages.actor(t, data.sanction.issuedBy) }}</span>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('appeal.col.status')" style="min-width: 6rem">
                        <template #body="{ data }"><SanctionTag type="appeal" :status="data.status" /></template>
                    </Column>
                    <Column field="createdAt" sortable :header="t('appeal.col.createdAt')" style="min-width: 7rem">
                        <template #body="{ data }"><time :datetime="data.createdAt" :title="time.full(data.createdAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.createdAt) }}</time></template>
                    </Column>
                    <Column :header="t('appeal.col.wait')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <span class="inline-flex items-center gap-1 text-[12px] tabular-nums" :class="UtilSanction.isLongWait(data.createdAt, data.answeredAt, now) ? 'font-semibold text-red-600 dark:text-red-400' : ''">
                                <i v-if="UtilSanction.isLongWait(data.createdAt, data.answeredAt, now)" class="pi pi-exclamation-circle text-[10px]" aria-hidden="true" />
                                {{ UtilSanctionMessages.wait(t, UtilSanction.waitMs(data.createdAt, data.answeredAt, now)) }}
                            </span>
                        </template>
                    </Column>
                    <Column :header="t('appeal.col.answeredAt')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <time v-if="data.answeredAt" :datetime="data.answeredAt" :title="time.full(data.answeredAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.answeredAt) }}</time>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <AppealDetailDrawer
            v-model:visible="drawerOpen"
            :item="drawerItem"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :can-answer="permission.canAnswer.value"
            :busy="acting"
            :closed="closed"
            :form-error="formError"
            :answer-field-error="answerFieldError"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            @retry="openId && loadDetail(openId)"
            @answer="submitAnswer"
            @prev="neighbors.prev && openItem(neighbors.prev)"
            @next="neighbors.next && openItem(neighbors.next)"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AppealDetail, AppealItem, AppealListView, AppealStatusFilter } from '~/composables/sanction/api/useAppealApi.types';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction, { type AppealFilters } from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('appeal.title') });

const api = useAppealApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const permission = useSanctionPermission();
/** 대기 시간의 「지금」 — 목록을 받을 때 정한다(줄마다 다른 지금이 되지 않게) */
const now = ref(Date.now());

/** 종류 — 서버가 늘 준다(SITE · BOARD) */
function kindOf(item: AppealItem): 'SITE' | 'BOARD' {
    return item.kind;
}

const LIST_PATH = '/appeals';
const filters = computed<AppealFilters>(() => UtilSanction.parseAppeal(route.query));

function apply(next: AppealFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilSanction.appealKey(next) === UtilSanction.appealKey(filters.value)) return;
    const appeal = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilSanction.appealToQuery(next), ...(appeal ? { appeal } : {}) } });
}
function setStatus(status: AppealStatusFilter): void {
    apply(UtilSanction.change(filters.value, { status }));
}
/** 「접수」 머리 — 오름 = 오래된 순(기본), 내림 = 최신 순 */
function onSort(e: { sortOrder?: number | null }): void {
    apply(UtilSanction.change(filters.value, { sort: e.sortOrder === -1 ? 'newest' : 'oldest' }));
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilSanction.change(filters.value, { size: e.rows }));
    else apply(UtilSanction.change(filters.value, { page: e.page + 1 }), 'push');
}

const statusOptions = computed(() => {
    const c = view.value?.counts;
    const n: Partial<Record<AppealStatusFilter, number>> = c ? { PENDING: c.pending, ACCEPTED: c.accepted, REJECTED: c.rejected, ALL: c.pending + c.accepted + c.rejected } : {};
    return UtilSanction.APPEAL_STATUS_FILTERS.map((value) => ({ value, label: n[value] === undefined ? t(`appeal.filter.${value}`) : t('sanction.scopeCount', { name: t(`appeal.filter.${value}`), n: n[value]!.toLocaleString(locale.value) }) }));
});

/* ── 목록 ── */

const view = ref<AppealListView | null>(null);
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
    if (!silent) loadError.value = null;
    try {
        const found = await api.list(UtilSanction.appealToApi(filters.value), abort.signal);
        if (mine !== seq) return;
        now.value = Date.now();
        view.value = found;
        loadError.value = null;
        if (found.items.length === 0 && found.total > 0 && filters.value.page > 1) apply(UtilSanction.change(filters.value, { page: UtilSanction.pageCount(found.total, found.size || filters.value.size) }));
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        if (silent && view.value) toast.error(t('sanction.refreshFailed', { reason: error.message }), 8000);
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

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const detail = ref<AppealDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
const acting = ref(false);
const closed = ref(false);
const formError = ref('');
const answerFieldError = ref('');
const neighbors = computed(() => UtilSanction.neighbors(items.value, openId.value));
const drawerItem = computed(() => (openId.value ? (items.value.find((a) => a.id === openId.value) ?? null) : null));
let detailTicket = 0;

async function loadDetail(id: string): Promise<void> {
    const mine = ++detailTicket;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const found = await api.get(id);
        if (mine !== detailTicket) return;
        detail.value = found;
        closed.value = false;
        formError.value = '';
        answerFieldError.value = '';
    } catch (e) {
        if (mine !== detailTicket) return;
        detail.value = null;
        detailError.value = errorOf(e);
    } finally {
        if (mine === detailTicket) detailLoading.value = false;
    }
}

function show(id: string): void {
    drawerOpen.value = true;
    if (openId.value === id && detail.value) return;
    openId.value = id;
    detail.value = null;
    closed.value = false;
    formError.value = '';
    answerFieldError.value = '';
    void loadDetail(id);
}

function setAppealQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilSanction.appealToQuery(filters.value), ...(id ? { appeal: id } : {}) } });
}

function openItem(item: AppealItem): void {
    show(item.id);
    setAppealQuery(item.id);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detailTicket++;
    openId.value = null;
    detail.value = null;
    detailError.value = null;
}

function rowClass(data: AppealItem): string {
    return drawerOpen.value && data.id === openId.value ? 'appeal-row-open cursor-pointer' : 'cursor-pointer';
}

/** 인용·기각 — 성공하면 새 상세로 바꾸고 목록을 조용히 다시 받는다 */
async function submitAnswer(payload: { accept: boolean; answer: string }): Promise<void> {
    const id = openId.value;
    if (!id || acting.value) return;
    acting.value = true;
    formError.value = '';
    answerFieldError.value = '';
    try {
        const next = payload.accept ? await api.accept(id, { answer: payload.answer }) : await api.reject(id, { answer: payload.answer });
        if (openId.value === id) detail.value = next;
        toast.success(t(payload.accept ? 'appeal.acceptedToast' : 'appeal.rejectedToast', { no: next.no }));
        void load(true);
    } catch (e) {
        const error = errorOf(e);
        if (error.code === 'APPEAL_CLOSED') {
            closed.value = true;
            void load(true);
            return;
        }
        answerFieldError.value = error.status === 400 ? (error.field('answer') ?? '') : '';
        formError.value = answerFieldError.value ? '' : error.message;
    } finally {
        acting.value = false;
    }
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilSanction.parseId(route.query, 'appeal') !== null) setAppealQuery(null);
});

watch(
    () => UtilSanction.parseId(route.query, 'appeal'),
    (id) => {
        if (id && (id !== openId.value || !drawerOpen.value)) show(id);
        else if (!id && drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);

// ⚠ 맨 끝에 둔다(바로 한 번 도는 감시)
watch(
    () => UtilSanction.appealKey(filters.value),
    () => void load(),
    { immediate: true },
);
</script>

<style scoped>
.appeal-table :deep(tr.appeal-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
