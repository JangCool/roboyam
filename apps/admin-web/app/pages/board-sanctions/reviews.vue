<template>
    <!--
        게시판 제재 › 반복 검토 — 같은 회원이 같은 게시판에서 7일 안에 3건 이상(VOIDED 제외, 처리자가 달라도 합산) 받으면 서버가 연 검토 건을 보고 닫는다. 계약 `docs/features/board-sanction-contract.md` 결정 3.
        탭 「이력」은 `/board-sanctions`(`BoardSanctionTabs`). 모양은 이의제기 화면(`pages/appeals/index.vue`)과 같다.
        - 조회: 상태 라디오(열림(기본)·닫힘 — 건수는 거르기와 무관한 전체). **주소 쿼리**(`?status=&page=&size=&review=`). 열린 건은 오래된 것이 위, 닫힌 건은 최근 닫은 것이 위(서버가 정한다).
        - 표: 번호 · 회원 · 게시판 · 7일 안 건수 + 「여러 게시판」 배지(7일 안 2곳 이상) · 열린 시각 · 닫은 시각 · 닫은 결과.
        - 서랍 `?review=`(`BoardSanctionReviewDrawer`): 그 게시판 최근 제재(처리자 포함) · 다른 게시판 요약 · 사이트 제재 현황(`useSanctionPreview`) · 닫기 셋 + 메모(0~200자).
          「사이트 제재 부여」는 기존 제재 대화창(`SanctionIssueDialog` · `useSanctionIssue`, 회원 고정)을 열고, 부여에 성공하면 그 제재 번호로 `SITE_SANCTIONED` 닫기를 보낸다.
          닫기가 실패해도 준 사이트 제재는 남는다(토스트로 알린다). `409 BOARD_SANCTION_REVIEW_CLOSED` 는 서랍 안 「이미 닫혔어요 — 다시 불러오기」.
        - 권한: 보기 `sanction.read`(메뉴), 닫기 `sanction.manage`(사이트 제재 단계는 대화창이 `sanction.manage`·`sanction.ban` 으로 가른다).
        ⚠ MOCK: API 는 목업(`composables/sanction/api/mock/board-sanction-mock.ts`). 목업 회원의 사이트 제재 현황·부여도 목업이 답한다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('boardSanction.title')" :subtitle="t('boardSanction.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('sanction.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <BoardSanctionTabs active="reviews" :open-count="view ? view.counts.open : null" />

        <div class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
            <FilterRow :label="t('boardSanction.review.filterStatus')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.status" :options="statusOptions" :labelledby="labelId" @update:model-value="(v) => setStatus(v as BoardSanctionReviewStatus)" />
                </template>
            </FilterRow>
        </div>

        <div class="flex min-w-0 flex-col gap-2">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p v-if="view" class="text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('sanction.total', { count: view.total.toLocaleString(locale) }) }}</p>
                <p class="text-xs text-muted-color">{{ t(filters.status === 'OPEN' ? 'boardSanction.review.sortOpen' : 'boardSanction.review.sortClosed') }}</p>
                <p v-if="!canManage" class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('boardSanction.review.readOnly') }}</p>
            </div>

            <div class="review-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
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
                    @row-click="(e: { data: BoardSanctionReviewItem }) => openItem(e.data)"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('boardSanction.review.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="filters.status === 'OPEN'" icon="pi pi-check-circle" :title="t('boardSanction.review.emptyOpen')" :description="t('boardSanction.review.emptyOpenHint')" />
                        <EmptyState v-else icon="pi pi-inbox" :title="t('boardSanction.review.emptyClosed')" />
                    </template>

                    <Column :header="t('boardSanction.review.col.no')" style="min-width: 4.5rem">
                        <template #body="{ data }"><span class="text-[12px] tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, data) }}</span></template>
                    </Column>
                    <Column :header="t('boardSanction.review.col.member')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <button type="button" class="block max-w-full truncate text-left text-[13px] font-medium hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click.stop="openItem(data)">{{ data.user.nickname }}</button>
                        </template>
                    </Column>
                    <Column :header="t('boardSanction.review.col.board')" style="min-width: 7rem">
                        <template #body="{ data }"><span class="block truncate text-[13px]">{{ localizedName(data.board.name, data.board.nameEn) }}</span></template>
                    </Column>
                    <Column :header="t('boardSanction.review.col.recent')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span class="flex flex-wrap items-center gap-1.5">
                                <span class="text-[13px] font-semibold tabular-nums">{{ t('boardSanction.review.recentValue', { n: data.recentCount }) }}</span>
                                <span v-if="UtilBoardSanction.isMultiBoard(data.recentBoardCount)" :title="t('boardSanction.review.multiBoardHint', { n: data.recentBoardCount })" class="inline-flex items-center gap-1 rounded-full bg-red-100 px-1.5 py-0.5 text-[11px] font-medium text-red-800 dark:bg-red-950 dark:text-red-300">
                                    <i class="pi pi-clone text-[9px]" aria-hidden="true" />{{ t('boardSanction.review.multiBoard') }}
                                </span>
                            </span>
                        </template>
                    </Column>
                    <Column :header="t('boardSanction.review.col.openedAt')" style="min-width: 7rem">
                        <template #body="{ data }"><time :datetime="data.openedAt" :title="time.full(data.openedAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.openedAt) }}</time></template>
                    </Column>
                    <Column :header="t('boardSanction.review.col.closedAt')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <time v-if="data.closedAt" :datetime="data.closedAt" :title="time.full(data.closedAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.closedAt) }}</time>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>
                    <Column :header="t('boardSanction.review.col.result')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <BoardSanctionTag v-if="data.result" type="result" :status="data.result" />
                            <BoardSanctionTag v-else type="review" :status="data.status" />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <SanctionIssueDialog v-model:visible="issue.open.value" :view="issue.view.value" :perm="sitePerm" @search="issue.search" @pick="issue.pick" @unpick="issue.unpick" @retry-preview="issue.loadPreview" @submit="submitIssue" />

        <BoardSanctionReviewDrawer
            v-model:visible="drawerOpen"
            :item="drawerItem"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :site-preview="sitePreview.preview.value"
            :site-preview-loading="sitePreview.loading.value"
            :site-preview-error="sitePreview.error.value"
            :can-manage="canManage"
            :busy="acting"
            :closed="closed"
            :form-error="formError"
            :field-errors="formFieldErrors"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            @retry="openId && loadDetail(openId)"
            @retry-site="sitePreview.retry"
            @submit="submitClose"
            @prev="neighbors.prev && openItem(neighbors.prev)"
            @next="neighbors.next && openItem(neighbors.next)"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    BoardSanctionReviewCloseRequest,
    BoardSanctionReviewDetail,
    BoardSanctionReviewItem,
    BoardSanctionReviewListView,
    BoardSanctionReviewResult,
    BoardSanctionReviewStatus,
} from '~/composables/sanction/api/useBoardSanctionReviewApi.types';
import type { SanctionCreateRequest } from '~/composables/sanction/api/useSanctionApi.types';
import UtilBoardSanction, { type BoardSanctionReviewFilters } from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => `${t('boardSanction.tab.reviews')} · ${t('boardSanction.title')}` });

const api = useBoardSanctionReviewApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const permission = useSanctionPermission();
const canManage = computed(() => permission.canManage.value);
const sitePerm = computed(() => ({ canManage: permission.canManage.value, canBan: permission.canBan.value }));

/* ── 주소 ↔ 필터 ── */

const LIST_PATH = '/board-sanctions/reviews';
const filters = computed<BoardSanctionReviewFilters>(() => UtilBoardSanction.parseReview(route.query));

const toQuery = (f: BoardSanctionReviewFilters): Record<string, string> => UtilBoardSanction.reviewToQuery(f);

function apply(next: BoardSanctionReviewFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilBoardSanction.reviewKey(next) === UtilBoardSanction.reviewKey(filters.value)) return;
    const open = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...toQuery(next), ...(open ? { review: open } : {}) } });
}
function setStatus(status: BoardSanctionReviewStatus): void {
    apply(UtilSanction.change(filters.value, { status }));
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilSanction.change(filters.value, { size: e.rows }));
    else apply(UtilSanction.change(filters.value, { page: e.page + 1 }), 'push');
}

const statusOptions = computed(() => {
    const c = view.value?.counts;
    const n: Partial<Record<BoardSanctionReviewStatus, number>> = c ? { OPEN: c.open, CLOSED: c.closed } : {};
    return UtilBoardSanction.REVIEW_STATUSES.map((value) => {
        const name = UtilBoardSanctionMessages.reviewStatusName(t, value);
        return { value, label: n[value] === undefined ? name : t('sanction.scopeCount', { name, n: n[value]!.toLocaleString(locale.value) }) };
    });
});

/* ── 목록 ── */

const view = ref<BoardSanctionReviewListView | null>(null);
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
        const found = await api.list(UtilBoardSanction.reviewToApi(filters.value), abort.signal);
        if (mine !== seq) return;
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
const detail = ref<BoardSanctionReviewDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
const sitePreview = useSanctionPreview();
const acting = ref(false);
const closed = ref(false);
const formError = ref('');
const formFieldErrors = ref<Record<string, string>>({});
const neighbors = computed(() => UtilSanction.neighbors(items.value, openId.value));
const drawerItem = computed(() => (openId.value ? (items.value.find((r) => r.id === openId.value) ?? null) : null));
let detailTicket = 0;

function clearForm(): void {
    closed.value = false;
    formError.value = '';
    formFieldErrors.value = {};
}

async function loadDetail(id: string): Promise<void> {
    const mine = ++detailTicket;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const found = await api.get(id);
        if (mine !== detailTicket) return;
        detail.value = found;
        clearForm();
        if (sitePreview.userId.value !== found.user.id) void sitePreview.open(found.user.id);
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
    clearForm();
    const known = items.value.find((r) => r.id === id);
    if (known && sitePreview.userId.value !== known.user.id) void sitePreview.open(known.user.id);
    void loadDetail(id);
}

function setReviewQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...toQuery(filters.value), ...(id ? { review: id } : {}) } });
}

function openItem(item: BoardSanctionReviewItem): void {
    show(item.id);
    setReviewQuery(item.id);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detailTicket++;
    openId.value = null;
    detail.value = null;
    detailError.value = null;
    sitePreview.clear();
}

function rowClass(data: BoardSanctionReviewItem): string {
    return drawerOpen.value && data.id === openId.value ? 'review-row-open cursor-pointer' : 'cursor-pointer';
}

/* ── 닫기 ── */

/** 사이트 제재 부여를 고르고 대화창을 연 동안 들고 있는 메모 — 부여에 성공하면 이 메모로 닫는다 */
const pendingSite = ref<{ reviewId: string; memo: string | null } | null>(null);
const issue = useSanctionIssue();

async function close(id: string, body: BoardSanctionReviewCloseRequest): Promise<boolean> {
    acting.value = true;
    formError.value = '';
    formFieldErrors.value = {};
    try {
        const next = await api.close(id, body);
        if (openId.value === id) detail.value = next;
        toast.success(t('boardSanction.review.closedToast', { no: next.no, result: UtilBoardSanctionMessages.reviewResultName(t, body.result) }));
        void load(true);
        return true;
    } catch (e) {
        const error = errorOf(e);
        if (error.code === 'BOARD_SANCTION_REVIEW_CLOSED') {
            closed.value = true;
            void load(true);
            return false;
        }
        const fields = error.status === 400 ? (error.fields ?? {}) : {};
        const mine = Object.fromEntries(Object.entries(fields).filter(([k]) => ['memo', 'voidSanctionIds', 'siteSanctionId'].includes(k)));
        formFieldErrors.value = mine;
        formError.value = Object.keys(mine).length > 0 && !mine.siteSanctionId ? '' : (mine.siteSanctionId ?? error.message);
        return false;
    } finally {
        acting.value = false;
    }
}

function submitClose(payload: { result: BoardSanctionReviewResult; memo: string | null; voidSanctionIds: string[] }): void {
    const id = openId.value;
    const current = detail.value;
    if (!id || !current || acting.value) return;
    if (payload.result === 'SITE_SANCTIONED') {
        // 기존 사이트 제재 대화창 — 회원 고정. 부여에 성공하면 `submitIssue` 가 닫는다
        pendingSite.value = { reviewId: id, memo: payload.memo };
        issue.start({ id: current.user.id, nickname: current.user.nickname });
        return;
    }
    void close(id, { result: payload.result, memo: payload.memo, siteSanctionId: null, voidSanctionIds: payload.voidSanctionIds });
}

async function submitIssue(body: Omit<SanctionCreateRequest, 'userId' | 'reportCaseId'>): Promise<void> {
    // ⚠ 먼저 붙잡는다 — 부여에 성공하면 대화창이 닫혀 아래 감시가 `pendingSite` 를 비운다
    const pending = pendingSite.value;
    const created = await issue.submit(body);
    if (!created) return;
    pendingSite.value = null;
    toast.success(UtilSanctionMessages.issued(t, created));
    void sitePreview.reload();
    if (!pending) return;
    const ok = await close(pending.reviewId, { result: 'SITE_SANCTIONED', memo: pending.memo, siteSanctionId: created.id, voidSanctionIds: [] });
    if (!ok && !closed.value) toast.error(t('boardSanction.review.siteIssuedCloseFailed', { no: created.no, reason: formError.value }), 10000);
}

// 대화창을 부여 없이 닫으면 들고 있던 닫기를 버린다
watch(
    () => issue.open.value,
    (isOpen) => {
        if (!isOpen && !issue.view.value.busy) pendingSite.value = null;
    },
);

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilBoardSanction.parseId(route.query, 'review') !== null) setReviewQuery(null);
});

watch(
    () => UtilBoardSanction.parseId(route.query, 'review'),
    (id) => {
        if (id && (id !== openId.value || !drawerOpen.value)) show(id);
        else if (!id && drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);

// ⚠ 맨 끝에 둔다(바로 한 번 도는 감시)
watch(
    () => UtilBoardSanction.reviewKey(filters.value),
    () => void load(),
    { immediate: true },
);
</script>

<style scoped>
.review-table :deep(tr.review-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
