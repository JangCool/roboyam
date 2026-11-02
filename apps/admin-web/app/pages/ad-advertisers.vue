<template>
    <!--
        광고 › 광고주 관리(`/ad-advertisers`) — ad-contract 「후속」의 광고주 관리 화면. ⚠ 목업(`useAdAdvertiserApi` — 스위치 `?mock=adv-*`).
        · 광고주 = 직접 등록(운영자가 만든 계정 없는 광고주) + 사업자(사업자 신원 1:1 — 광고주 줄이 없어도 광고 건·광고 서비스가 있으면 나온다).
        · 거르기: 종류 탭(전체·사업자·직접 등록, 건수) · 상태(사용 중·보관·전체) · 찾기(이름·등록번호 끝자리). 정렬: 최근 광고일(기본)·누적 결제·이름.
        · 한 줄: 이름 + 종류 · 가린 연락처 · 진행/예약/대기 건 수 · 누적 결제 · 최근 광고일 · 상태(직접 등록 = 사용 중/보관, 사업자 = 신원·광고 서비스 상태). 평문 칸은 목록에 없다.
        · 줄을 누르면 상세 서랍(`?id=`): 연락처 보기(사유 → 10분, 열람 기록) · 광고 건(→ 일정표 서랍) · 성과(최근 30일 → 광고 성과 화면) · 결제·환불 합계 · 기록.
        · [새 광고주](직접 등록) · [고치기](사업자는 이름 = 상호라 못 고침) · [보관](끝나지 않은 건이 없을 때)/[보관 풀기] · [삭제](광고 건 0).
        권한: 화면 `ad.read` · 만들기·고치기·보관·삭제 `ad.manage` · 연락처 평문 `ad.contact.reveal`(서버 `Permission`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('adAdvertiser.title')" :subtitle="t('adAdvertiser.subtitle')">
            <template #actions>
                <Button v-if="canManage" type="button" :label="t('adAdvertiser.actCreate')" icon="pi pi-plus" size="small" @click="openCreate" />
            </template>
        </PageTitle>

        <Tabs :value="kind" scrollable class="min-w-0" @update:value="(v: string | number) => selectKind(String(v) as KindTab)">
            <TabList :aria-label="t('adAdvertiser.kindTabs')">
                <Tab v-for="k in KIND_TABS" :key="k" :value="k">
                    <span class="inline-flex items-center gap-1.5">{{ t(`adAdvertiser.kindTab.${k}`) }}<span class="text-[11px] font-normal tabular-nums text-muted-color">{{ counts ? counts[k] : '—' }}</span></span>
                </Tab>
            </TabList>
        </Tabs>

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('adAdvertiser.search')" @submit.prevent="search">
            <label class="sr-only" :for="stateId">{{ t('adAdvertiser.stateLabel') }}</label>
            <Select v-model="state" :input-id="stateId" :options="stateOptions" option-label="label" option-value="value" class="w-36" @change="search" />
            <label class="sr-only" :for="qId">{{ t('adAdvertiser.search') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('adAdvertiser.searchPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

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
                :sort-field="sort"
                :sort-order="dir === 'asc' ? 1 : -1"
                scrollable
                :row-class="(r: AdAdvertiserRow) => (r.state === 'ARCHIVED' ? 'cursor-pointer opacity-70' : 'cursor-pointer')"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                @page="onPage"
                @sort="onSort"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('adAdvertiser.loadFailed')" :description="loadError.message" :retry="true" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || kind !== 'ALL' || applied.state !== 'ACTIVE'" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-id-card" :title="t('adAdvertiser.empty')" :description="t('adAdvertiser.emptyHint')">
                        <Button v-if="canManage" type="button" :label="t('adAdvertiser.actCreate')" icon="pi pi-plus" size="small" @click="openCreate" />
                    </EmptyState>
                </template>

                <Column field="name" sortable :header="t('adAdvertiser.col.name')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <span class="flex items-center gap-1.5">
                            <span class="truncate text-[13px] font-medium">{{ data.name }}</span>
                            <Tag :value="t(`adAdvertiser.kind.${data.kind}`)" severity="secondary" />
                        </span>
                        <span class="text-[11px] tabular-nums text-muted-color">{{ data.business ? data.business.maskedRegNo : t('adAdvertiser.directShort') }}</span>
                    </template>
                </Column>
                <Column :header="t('adAdvertiser.col.contact')" style="min-width: 9rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums" :class="data.contactMasked ? '' : 'text-muted-color'">{{ data.contactMasked ?? '—' }}</span></template>
                </Column>
                <Column :header="t('adAdvertiser.col.bookings')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <span class="flex flex-wrap items-center gap-x-2 gap-y-0.5 text-[12px] tabular-nums">
                            <span :class="data.counts.running ? EMPH.ok : 'text-muted-color'">{{ t('adAdvertiser.runningN', { n: data.counts.running }) }}</span>
                            <span :class="data.counts.scheduled ? EMPH.date : 'text-muted-color'">{{ t('adAdvertiser.scheduledN', { n: data.counts.scheduled }) }}</span>
                            <span v-if="data.counts.waiting" :class="EMPH.fee">{{ t('adAdvertiser.waitingN', { n: data.counts.waiting }) }}</span>
                        </span>
                    </template>
                </Column>
                <Column field="paidTotal" sortable :header="t('adAdvertiser.col.paid')" style="min-width: 8rem" :pt="right">
                    <template #body="{ data }"><div class="text-right text-[13px] tabular-nums" :class="data.paidTotal ? EMPH.money : 'text-muted-color'">{{ data.paidTotal ? won(data.paidTotal) : '—' }}</div></template>
                </Column>
                <Column field="lastAdAt" sortable :header="t('adAdvertiser.col.lastAd')" style="min-width: 7rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums" :class="data.lastAdAt ? EMPH.date : 'text-muted-color'">{{ data.lastAdAt ? time.serviceDate(UtilBizApplication.kstDate(data.lastAdAt)) : t('adAdvertiser.noAdYet') }}</span></template>
                </Column>
                <Column :header="t('adAdvertiser.col.state')" style="min-width: 7rem">
                    <template #body="{ data }"><Tag :value="t(`adAdvertiser.badge.${UtilAdAdvertiser.badge(data)}`)" :severity="UtilAdAdvertiser.badgeSeverity(UtilAdAdvertiser.badge(data))" /></template>
                </Column>
            </DataTable>
        </div>

        <AdAdvertiserDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :busy="working !== null"
            :can-manage="canManage"
            :can-reveal="can('ad.contact.reveal')"
            :reveal="revealed"
            :reveal-busy="working === 'reveal'"
            :stats="drawerStats"
            :stats-range="statsRange"
            :stats-loading="statsLoading"
            :stats-error="statsError"
            @retry="loadDetail"
            @reveal="openReveal"
            @hide="revealed = null"
            @edit="openEdit"
            @archive="archive(true)"
            @unarchive="archive(false)"
            @remove="remove"
            @stats="loadStats(false)"
            @stats-retry="loadStats(true)"
        />

        <AdAdvertiserFormDialog
            v-model:visible="formOpen"
            :advertiser="formTarget"
            :busy="working === 'save'"
            :error="formError"
            :field-errors="formFields"
            @create="submitCreate"
            @update="submitUpdate"
        />

        <BizReasonDialog
            v-model:visible="revealOpen"
            :title="t('adAdvertiser.revealTitle')"
            :description="t('adAdvertiser.revealBody')"
            :confirm-label="t('adAdvertiser.reveal')"
            :busy="working === 'reveal'"
            :error="revealError"
            @submit="submitReveal"
        />
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent, DataTableSortEvent } from 'primevue/datatable';
import type { AdAdvertiserCreateBody, AdAdvertiserDetail, AdAdvertiserKind, AdAdvertiserPage, AdAdvertiserRow, AdAdvertiserSort, AdAdvertiserState, AdAdvertiserUpdateBody } from '~/composables/ad/api/useAdAdvertiserApi.types';
import type { AdAdvertiserRevealed, AdStatsOverview } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAdAdvertiser from '~/utils/util-ad-advertiser';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

type KindTab = 'ALL' | AdAdvertiserKind;
type Work = 'save' | 'reveal' | 'archive' | 'remove';

const KIND_TABS: readonly KindTab[] = ['ALL', 'BUSINESS', 'ADVERTISER'];
const SORTS: readonly AdAdvertiserSort[] = ['lastAdAt', 'paidTotal', 'name'];

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('adAdvertiser.title') });

const api = useAdAdvertiserApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const route = useRoute();
const router = useRouter();

const stateId = useId();
const qId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const right = { columnHeaderContent: { class: 'justify-end' } };
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
const EMPH = UtilBizApplication.EMPH;
const canManage = computed(() => can('ad.manage'));

// ── 거르기 ──
const kind = ref<KindTab>('ALL');
const state = ref<AdAdvertiserState | 'ALL'>('ACTIVE');
const q = ref('');
const applied = ref<{ state: AdAdvertiserState | 'ALL'; q?: string }>({ state: 'ACTIVE' });
const sort = ref<AdAdvertiserSort>('lastAdAt');
const dir = ref<'asc' | 'desc'>('desc');
const stateOptions = computed(() => [
    { value: 'ACTIVE', label: t('adAdvertiser.state.ACTIVE') },
    { value: 'ARCHIVED', label: t('adAdvertiser.state.ARCHIVED') },
    { value: 'ALL', label: t('adAdvertiser.state.ALL') },
]);

// ── 목록 ──
const rows = ref<AdAdvertiserRow[]>([]);
const total = ref(0);
const counts = ref<AdAdvertiserPage['counts'] | null>(null);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({
            kind: kind.value === 'ALL' ? undefined : kind.value,
            state: applied.value.state,
            q: applied.value.q,
            sort: sort.value,
            dir: dir.value,
            page: Math.floor(first.value / size.value),
            size: size.value,
        });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        counts.value = found.counts;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        total.value = 0;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function selectKind(value: KindTab): void {
    kind.value = value;
    first.value = 0;
    void reload();
}

function search(): void {
    applied.value = { state: state.value, q: q.value.trim() || undefined };
    first.value = 0;
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

/** 다른 칸을 처음 누르면 이름은 가나다(오름), 나머지는 큰 것·최근 먼저 — 같은 칸을 다시 누르면 방향을 바꾼다 */
function onSort(e: DataTableSortEvent): void {
    const field = typeof e.sortField === 'string' && (SORTS as readonly string[]).includes(e.sortField) ? (e.sortField as AdAdvertiserSort) : 'lastAdAt';
    if (field === sort.value) dir.value = e.sortOrder === 1 ? 'asc' : 'desc';
    else {
        sort.value = field;
        dir.value = field === 'name' ? 'asc' : 'desc';
    }
    first.value = 0;
    void reload();
}

// ── 상세 서랍 ──
const drawerOpen = ref(false);
const drawerId = ref<string | null>(null);
const drawerSummary = ref<AdAdvertiserRow | null>(null);
const drawerDetail = ref<AdAdvertiserDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);
const revealed = ref<AdAdvertiserRevealed | null>(null);

let dseq = 0;
async function loadDetail(): Promise<void> {
    const id = drawerId.value;
    if (!id) return;
    const mine = ++dseq;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.detail(id);
        if (mine === dseq) drawerDetail.value = found;
    } catch (e) {
        if (mine !== dseq) return;
        drawerDetail.value = null;
        drawerError.value = toApiError(e);
    } finally {
        if (mine === dseq) drawerLoading.value = false;
    }
}

// ── 서랍 「성과」 탭 — 탭을 처음 열 때 그 광고주에 한 번(최근 30일) ──
const statsRange = UtilAdAdvertiser.recentRange();
const drawerStats = ref<AdStatsOverview | null>(null);
const statsLoading = ref(false);
const statsError = ref<ApiError | null>(null);
let statsFor: string | null = null;
let sseq = 0;
async function loadStats(force: boolean): Promise<void> {
    const id = drawerId.value;
    if (!id || (!force && statsFor === id && !statsError.value)) return;
    const mine = ++sseq;
    statsFor = id;
    statsLoading.value = true;
    statsError.value = null;
    drawerStats.value = null;
    try {
        const found = await api.stats(id, statsRange);
        if (mine === sseq) drawerStats.value = found;
    } catch (e) {
        if (mine === sseq) statsError.value = toApiError(e);
    } finally {
        if (mine === sseq) statsLoading.value = false;
    }
}

function show(id: string, row: AdAdvertiserRow | null): void {
    if (drawerId.value !== id) {
        drawerDetail.value = null;
        revealed.value = null;
        sseq++;
        statsFor = null;
        drawerStats.value = null;
        statsError.value = null;
        statsLoading.value = false;
    }
    drawerId.value = id;
    drawerSummary.value = row;
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as AdAdvertiserRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, id: row.id } });
}

watch(
    () => route.query.id,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open) return;
    // 닫으면 평문은 바로 가린다
    revealed.value = null;
    if (!route.query.id) return;
    const { id: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 동작 ──
const working = ref<Work | null>(null);

/** 409(다른 운영자가 먼저 바꿈 · 끝나지 않은 건)는 서랍을 다시 읽고 문구를 토스트로 */
async function run(work: Work, call: () => Promise<AdAdvertiserDetail>, done: string): Promise<void> {
    working.value = work;
    try {
        drawerDetail.value = await call();
        toast.success(done);
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 409) void loadDetail();
        toast.error(failure.message);
    } finally {
        working.value = null;
    }
}

async function archive(archived: boolean): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    if (archived && !(await confirm({ title: t('adAdvertiser.archiveTitle', { name: d.name }), message: t('adAdvertiser.archiveBody'), confirmLabel: t('adAdvertiser.actArchive') }))) return;
    await run('archive', () => api.setArchived(d.id, archived), archived ? t('adAdvertiser.archived') : t('adAdvertiser.unarchived'));
}

async function remove(): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    if (!(await confirm({ title: t('adAdvertiser.removeTitle', { name: d.name }), message: t('adAdvertiser.removeBody'), confirmLabel: t('common.delete'), tone: 'danger' }))) return;
    working.value = 'remove';
    try {
        await api.remove(d.id);
        toast.success(t('adAdvertiser.removed'));
        drawerOpen.value = false;
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 409) void loadDetail();
        toast.error(failure.message);
    } finally {
        working.value = null;
    }
}

// ── 연락처 보기 ──
const revealOpen = ref(false);
const revealError = ref('');
function openReveal(): void {
    revealError.value = '';
    revealOpen.value = true;
}
async function submitReveal(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    working.value = 'reveal';
    revealError.value = '';
    try {
        revealed.value = await api.reveal(d.id, reason);
        revealOpen.value = false;
        // 열람 기록에 방금 줄이 생겼다
        void loadDetail();
    } catch (e) {
        revealError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

// ── 만들기·고치기 ──
const formOpen = ref(false);
const formTarget = ref<AdAdvertiserDetail | null>(null);
const formError = ref('');
const formFields = ref<Record<string, string>>({});
function openCreate(): void {
    formTarget.value = null;
    formError.value = '';
    formFields.value = {};
    formOpen.value = true;
}
function openEdit(): void {
    formTarget.value = drawerDetail.value;
    formError.value = '';
    formFields.value = {};
    formOpen.value = true;
}

async function save(call: () => Promise<AdAdvertiserDetail>, done: string): Promise<AdAdvertiserDetail | null> {
    working.value = 'save';
    formError.value = '';
    formFields.value = {};
    try {
        const saved = await call();
        toast.success(done);
        formOpen.value = false;
        void reload();
        return saved;
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) formFields.value = failure.fields;
        else formError.value = failure.message;
        return null;
    } finally {
        working.value = null;
    }
}

async function submitCreate(body: AdAdvertiserCreateBody): Promise<void> {
    const created = await save(() => api.create(body), t('adAdvertiser.created'));
    if (!created) return;
    show(created.id, null);
    drawerDetail.value = created;
    void router.replace({ query: { ...route.query, id: created.id } });
}

async function submitUpdate(body: AdAdvertiserUpdateBody): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    if (!Object.keys(body).length) {
        formOpen.value = false;
        return;
    }
    const saved = await save(() => api.update(d.id, body), t('adAdvertiser.saved'));
    if (saved) {
        drawerDetail.value = saved;
        // 연락처를 바꿨으면 보이던 평문은 옛 값이다
        if (body.contactName !== undefined || body.contactPhone !== undefined || body.email !== undefined) revealed.value = null;
    }
}

onMounted(() => void reload());
</script>
