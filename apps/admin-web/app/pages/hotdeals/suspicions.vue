<template>
    <!--
        핫딜 › 의심 후보(`/hotdeals/suspicions`) — 위장 홍보 의심 회원(같은 회원이 N일 안에 같은 판매처(대형몰 제외)를 M건 이상 — 설정 값). 03-hotdeal 7-1.
        **목업**(`useHotdealSuspicionApi` → `hotdeal/api/mock`) — 스위치 `?mock=empty|error|detail-error|slow|forbidden|stale`.
        - 상태 라디오(열림 · 무시 + 건수) → 표(회원 · 판매처 · 기간 안 건수 · 같은 판매처 다른 회원 수 · 처음 감지 · 마지막 글 · 무시한 경우 처리) → 줄을 누르면 서랍(`?suspicion=`).
        - 서랍: 그 회원의 그 판매처 글 최근 10개 · 회원·신고 바로 가기 · 탈퇴 회원 Tag → [무시] → 메모 대화창(선택) → 토스트. 자동 제재는 없다 — 제재는 신고·회원 화면 링크만.
        - 빈: 「의심 후보가 없어요」 · 오류: ErrorState 다시 시도 · 대기: Skeleton. 주소 `?status=&page=&suspicion=`.
        - 권한: 보기 `hotdeal.read`(메뉴), 무시 `hotdeal.manage`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('hotdeal.suspicion.title')" :subtitle="t('hotdeal.suspicion.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('hotdeal.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy && page !== null" @click="load(true)" />
            </template>
        </PageTitle>

        <div class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4">
            <FilterRow :label="t('hotdeal.suspicion.statusLabel')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.status" :options="statusOptions" :labelledby="labelId" @update:model-value="(v) => setStatus(v as DealSuspicionStatus)" />
                </template>
            </FilterRow>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('hotdeal.suspicion.lead') }}</p>
        </div>

        <div class="suspicion-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <ErrorState v-if="loadError" :title="t('hotdeal.suspicion.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
            <DataTable
                v-else
                :value="items"
                data-key="id"
                lazy
                paginator
                :first="(filters.page - 1) * PAGE_SIZE"
                :rows="PAGE_SIZE"
                :total-records="page?.total ?? 0"
                :always-show-paginator="false"
                :loading="busy && page !== null"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                row-hover
                scrollable
                :row-class="rowClass"
                class="min-w-0"
                @page="(e) => apply({ ...filters, page: e.page + 1 }, 'push')"
                @row-click="(e) => openItem(e.data)"
            >
                <template #empty>
                    <div v-if="!page" class="flex flex-col gap-3 px-2 py-3" role="status" :aria-label="t('common.loading')" aria-busy="true">
                        <Skeleton v-for="n in 5" :key="n" height="2.25rem" />
                    </div>
                    <EmptyState v-else icon="pi pi-shield" :title="t(filters.status === 'OPEN' ? 'hotdeal.suspicion.empty' : 'hotdeal.suspicion.emptyDismissed')" :description="filters.status === 'OPEN' ? t('hotdeal.suspicion.emptyHint') : ''" />
                </template>
                <Column :header="t('hotdeal.suspicion.col.member')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <span class="inline-flex flex-wrap items-center gap-1.5">
                            <span class="text-[13px] font-medium" :class="data.member.withdrawn ? 'text-muted-color' : ''">{{ data.member.nickname }}</span>
                            <Tag v-if="data.member.withdrawn" :value="t('hotdeal.withdrawn')" severity="secondary" class="text-[11px]!" />
                        </span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.suspicion.col.store')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="flex flex-col">
                            <span class="text-[13px]">{{ data.storeName }}</span>
                            <span class="font-mono text-[11px] text-muted-color">{{ data.domain }}</span>
                        </span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.suspicion.col.count')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <span class="text-[13px] font-semibold tabular-nums">{{ t('hotdeal.suspicion.countValue', { n: data.postCount, days: data.windowDays }) }}</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.suspicion.col.others')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <span class="text-[13px] tabular-nums" :class="data.othersCount === 0 ? 'font-semibold text-amber-700 dark:text-amber-400' : ''">{{ t('hotdeal.suspicion.othersValue', { n: data.othersCount }) }}</span>
                    </template>
                </Column>
                <Column :header="t('hotdeal.suspicion.col.firstDetected')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <time :datetime="data.firstDetectedAt" :title="time.full(data.firstDetectedAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.firstDetectedAt) }}</time>
                    </template>
                </Column>
                <Column :header="t('hotdeal.suspicion.col.lastPost')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <time :datetime="data.lastPostAt" :title="time.full(data.lastPostAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.lastPostAt) }}</time>
                    </template>
                </Column>
                <Column v-if="filters.status === 'DISMISSED'" :header="t('hotdeal.suspicion.col.dismissed')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="flex flex-col text-[12px]">
                            <span>{{ t('hotdeal.suspicion.dismissedValue', { name: data.dismissedBy ?? '—', at: time.listLabel(data.dismissedAt) }) }}</span>
                            <span v-if="data.note" class="truncate text-muted-color" :title="data.note">{{ data.note }}</span>
                        </span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <HotdealReasonDialog
            v-model:visible="dismissOpen"
            kind="DISMISS"
            :target="detail ? t('hotdeal.suspicion.drawerTitle', { name: detail.suspicion.member.nickname, store: detail.suspicion.storeName }) : ''"
            :subtitle="detail?.suspicion.domain ?? ''"
            :busy="acting"
            :error="dismissError"
            :field-error="dismissFieldError"
            @submit="submitDismiss"
        />

        <HotdealSuspicionDrawer
            v-model:visible="drawerOpen"
            :item="drawerItem"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :busy="acting"
            :can-manage="perm.canManage.value"
            :can-read-reports="perm.canReadReports.value"
            :can-read-members="perm.canReadMembers.value"
            @retry="loadDetail(openId)"
            @dismiss="openDismiss"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { DealSuspicion, DealSuspicionDetail, DealSuspicionPage, DealSuspicionStatus } from '~/composables/hotdeal/api/useHotdealSuspicionApi.types';
import UtilHotdeal, { type SuspicionFilters } from '~/utils/util-hotdeal';

const { t } = useI18n();
useSeoMeta({ title: () => t('hotdeal.suspicion.title') });

const api = useHotdealSuspicionApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const perm = useHotdealPermission();

const LIST_PATH = '/hotdeals/suspicions';
const PAGE_SIZE = 20;

const filters = computed<SuspicionFilters>(() => UtilHotdeal.parseSuspicionFilters(route.query));
const filterKey = computed(() => JSON.stringify(UtilHotdeal.suspicionQuery(filters.value)));

function apply(next: SuspicionFilters, how: 'push' | 'replace' = 'replace'): void {
    const suspicion = drawerOpen.value ? openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilHotdeal.suspicionQuery(next), ...(suspicion ? { suspicion } : {}) } });
}

function setStatus(status: DealSuspicionStatus): void {
    if (status !== filters.value.status) apply({ status, page: 1 });
}

const statusOptions = computed(() =>
    UtilHotdeal.SUSPICION_STATUSES.map((value) => ({
        value,
        label: page.value ? t('hotdeal.viewCount', { name: t(`hotdeal.suspicion.status.${value}`), n: page.value.counts[value] }) : t(`hotdeal.suspicion.status.${value}`),
    })),
);

/* ── 목록 ── */

const page = ref<DealSuspicionPage | null>(null);
const items = computed(() => page.value?.items ?? []);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
let seq = 0;
onBeforeUnmount(() => seq++);

async function load(silent = false): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    if (!silent) loadError.value = null;
    try {
        const found = await api.list(filters.value.status, filters.value.page - 1, PAGE_SIZE);
        if (mine !== seq) return;
        page.value = found;
        loadError.value = null;
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        if (silent && page.value) toast.error(t('hotdeal.refreshFailed', { reason: error.message }), 8000);
        else {
            page.value = null;
            loadError.value = error;
        }
    } finally {
        if (mine === seq) busy.value = false;
    }
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const detail = ref<DealSuspicionDetail | null>(null);
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
        if (mine === detailSeq) detail.value = found;
    } catch (e) {
        if (mine === detailSeq) detailError.value = errorOf(e);
    } finally {
        if (mine === detailSeq) detailLoading.value = false;
    }
}

const drawerItem = computed<DealSuspicion | null>(() => (openId.value ? (detail.value?.suspicion ?? items.value.find((it) => it.id === openId.value) ?? null) : null));

function rowClass(data: DealSuspicion): string {
    return drawerOpen.value && data.id === openId.value ? 'suspicion-row-open cursor-pointer' : 'cursor-pointer';
}

function setSuspicionQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilHotdeal.suspicionQuery(filters.value), ...(id ? { suspicion: id } : {}) } });
}

function openItem(item: DealSuspicion): void {
    drawerOpen.value = true;
    if (openId.value !== item.id) {
        openId.value = item.id;
        void loadDetail(item.id);
    }
    setSuspicionQuery(item.id);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detailSeq++;
    openId.value = null;
    detail.value = null;
    detailError.value = null;
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilHotdeal.parseSuspicion(route.query) !== null) setSuspicionQuery(null);
});

watch(
    () => UtilHotdeal.parseSuspicion(route.query),
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

/* ── 무시 ── */

const acting = ref(false);
const dismissOpen = ref(false);
const dismissError = ref('');
const dismissFieldError = ref('');

function openDismiss(): void {
    if (!detail.value || acting.value || !perm.canManage.value) return;
    dismissError.value = '';
    dismissFieldError.value = '';
    dismissOpen.value = true;
}

async function submitDismiss(note: string): Promise<void> {
    const target = detail.value?.suspicion;
    if (!target || acting.value) return;
    acting.value = true;
    dismissError.value = '';
    dismissFieldError.value = '';
    try {
        await api.dismiss(target.id, note);
    } catch (e) {
        const error = errorOf(e);
        acting.value = false;
        if (error.code === 'HOTDEAL_STATE_CHANGED' || error.code === 'HOTDEAL_SUSPICION_NOT_FOUND') {
            dismissOpen.value = false;
            toast.error(t('hotdeal.staleFailed', { reason: error.message }), 8000);
            await load(true);
            await loadDetail(target.id, true);
            return;
        }
        if (error.fields?.note) dismissFieldError.value = error.fields.note;
        else dismissError.value = error.message;
        return;
    }
    acting.value = false;
    dismissOpen.value = false;
    toast.success(t('hotdeal.suspicion.dismissed', { name: target.member.nickname }));
    await load(true);
    await loadDetail(target.id, true);
}

// ⚠ 맨 끝 — 조건(상태·쪽)이 바뀌면 다시 받는다(처음 한 번 포함)
watch(filterKey, () => void load(), { immediate: true });
</script>

<style scoped>
.suspicion-table :deep(tr.suspicion-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>
