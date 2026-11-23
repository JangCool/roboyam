<template>
    <!--
        광고 › 광고 자리 관리(`/ads/slots`) — 계약 `docs/features/ad-slot-manager-contract.md`(시안 확정 2026-11-23). 서버 API 는 `useAdSlotApi`(admin `AdSlotAdminController`).
        · 미리보기(전체 폭): 실제 공개 화면 iframe(`AdSlotLiveFrame` — 테마·캐시 미리보기와 같은 방식). 위에 화면 고르기(홈 · 게시판 목록 · 글 보기 · 가젯 페이지 — 게시판·글·페이지는 하나 고르기)와 기기(데스크톱 · 모바일).
          광고 자리만 상자 + 이름표(종류 이름 · 진행 중 수), 빈 자리는 점선, 나머지 화면은 흐리게. 자리를 누르면 그 종류의 드로어가 열린다.
          미리보기 아래 종류 줄 — 이 화면에 없는 종류(모바일의 날개 등)도 열 수 있다. 이 화면에서 찾은 자리 수를 함께 적는다.
        · 드로어(`AdSlotDrawer`, 오른쪽 · 바탕을 막지 않음): 종류마다 같은 목록. 상세는 같은 드로어 안에서 바뀌고, 깊은 내용은 [기존 서랍에서 열기](`AdBookingDrawer` — 광고 일정표의 광고 건 서랍).
        · [광고 등록]: 기존 운영자 등록 서랍(`AdManualDrawer` — 광고 일정표와 같은 실제 API `useAdApi`, 광고 심사 대기로 간다).
        · 내리기: 사유 필수 5~200자 · 광고주 메일 선택(기존 긴급 내림 대화상자 `BizReasonDialog` 와 같은 규칙). 여러 건이면 사유 하나로 한 번에.
        권한: 화면 `ad.read` · 내리기 `ad.takedown`(없으면 단추 없음) · 등록 `ad.manage`. 메뉴 등록(서버 `AdminScreenRegistry`·`UtilAdminMenu`)은 서버 단계에서 — 지금은 주소로 연다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('adSlot.title')" :subtitle="t('adSlot.subtitle')" />

        <EmptyState v-if="!can('ad.read')" icon="pi pi-lock" :title="t('adSlot.noPermission')" />

        <template v-else>
            <!-- 화면 · 대상 · 기기 -->
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                <SelectButton :model-value="screen" :options="screenOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('adSlot.screenLabel')" @update:model-value="(v: AdSlotScreen) => setScreen(v)" />
                <template v-if="screen !== 'HOME'">
                    <label class="sr-only" :for="targetId">{{ targetLabel }}</label>
                    <Select
                        :input-id="targetId"
                        :model-value="target"
                        :options="targetOptions"
                        option-label="label"
                        option-value="value"
                        filter
                        size="small"
                        :loading="targetsLoading"
                        :placeholder="targetLabel"
                        :empty-message="targetsError ? t('adSlot.targetsFailed') : t('adSlot.targetsEmpty')"
                        class="w-64 max-w-full"
                        @update:model-value="(v: string) => (target = v)"
                    />
                </template>
                <SelectButton v-model="device" :options="deviceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('adSlot.deviceLabel')" class="ml-auto" />
            </div>

            <p class="m-0 text-xs text-muted-color">{{ t('adSlot.previewHint') }}</p>

            <AdSlotLiveFrame v-if="path" :path="path" :mobile="device === 'mobile'" :labels="labels" :active="drawerOpen ? openType : null" @pick="openSlot" @areas="onAreas" @reloading="onReloading" />
            <div v-else class="grid h-64 place-items-center rounded-lg border border-dashed border-(--line) px-6 text-center text-sm text-muted-color">{{ t('adSlot.pickTargetFirst') }}</div>

            <!-- 종류 줄 — 이 화면에 없는 종류도 연다 -->
            <section class="flex flex-col gap-2" :aria-labelledby="typesId">
                <div class="flex flex-wrap items-baseline gap-x-2">
                    <h2 :id="typesId" class="m-0 text-sm font-semibold">{{ t('adSlot.typesLabel') }}</h2>
                    <span class="text-xs text-muted-color">{{ t('adSlot.typesHint') }}</span>
                </div>
                <ErrorState v-if="summaryError" :title="t('adSlot.summaryFailed')" :description="summaryError.message" :retry="true" :retrying="summaryLoading" @retry="loadSummary" />
                <div v-else class="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
                    <button
                        v-for="s in summaryRows"
                        :key="s.type"
                        type="button"
                        class="flex min-w-0 flex-col items-start gap-0.5 rounded-lg border bg-(--p-content-background) px-3 py-2 text-left hover:bg-(--p-content-hover-background)"
                        :class="drawerOpen && openType === s.type ? 'border-(--p-primary-color)' : 'border-(--line)'"
                        @click="openSlot(s.type)"
                    >
                        <span class="w-full truncate text-[13px] font-medium">{{ t(`bizApplication.adType.${s.type}`) }}</span>
                        <span class="text-xs tabular-nums text-muted-color">{{ t('adSlot.runningOfLimit', { running: s.running, limit: s.limit }) }}</span>
                        <span class="text-[11px]" :class="(areaCounts[s.type] ?? 0) > 0 ? 'text-(--p-primary-color)' : 'text-muted-color'">{{ onScreenText(s.type) }}</span>
                    </button>
                </div>
            </section>
        </template>

        <AdSlotDrawer
            v-model:visible="drawerOpen"
            :type="openType"
            :summary="openSummary"
            :list="list"
            :loading="listLoading"
            :error="listError"
            :status="status"
            :busy="takingDown"
            :can-takedown="can('ad.takedown')"
            :can-register="openType !== null && can('ad.manage') && UtilAd.isManualType(openType)"
            @retry="loadList"
            @status="onStatus"
            @takedown="askTakedown"
            @register="manualOpen = true"
            @open-booking="openBooking"
        />

        <AdBookingDrawer
            v-model:visible="bookingOpen"
            :detail="bookingDetail"
            :loading="bookingLoading"
            :error="bookingError"
            :busy="takingDown"
            :can-takedown="can('ad.takedown')"
            :stats="bookingStats"
            :stats-loading="statsLoading"
            :stats-error="statsError"
            @retry="loadBooking"
            @stats="loadStats(false)"
            @stats-retry="loadStats(true)"
            @takedown="bookingDetail && askTakedown([bookingDetail.id])"
        />

        <AdManualDrawer
            v-if="openType"
            v-model:visible="manualOpen"
            :initial-type="openType"
            :search-advertisers="adApi.advertisers"
            :create-advertiser="adApi.createAdvertiser"
            :check-availability="adApi.availability"
            :upload-contract="adApi.uploadContract"
            :upload-image="adApi.uploadImage"
            :to-error="toApiError"
            :busy="manualBusy"
            :error="manualError"
            :field-errors="manualFields"
            @submit="submitManual"
        />

        <BizReasonDialog
            v-model:visible="takedownOpen"
            :title="takedownIds.length > 1 ? t('adSlot.takedownTitleMany', { n: takedownIds.length }) : t('ad.takedownTitle')"
            :description="takedownIds.length > 1 ? t('adSlot.takedownBodyMany') : t('ad.takedownBody')"
            :confirm-label="t('ad.actTakedown')"
            tone="danger"
            :busy="takingDown"
            :error="takedownError"
            @submit="submitTakedown"
        >
            <template #after>
                <div class="flex items-center gap-2">
                    <Checkbox v-model="notifyMail" binary :input-id="mailId" />
                    <label :for="mailId" class="text-[13px]">{{ t('ad.notifyMail') }}</label>
                </div>
                <Message severity="warn" :closable="false"><span class="text-[13px]">{{ t('ad.cacheNote', { s: UtilAd.CACHE_SECONDS }) }}</span></Message>
            </template>
        </BizReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { AdApplicationStats, AdBookingDetail, AdManualCreateRequest, AdType } from '~/composables/ad/api/useAdApi.types';
import type { AdSlotListView, AdSlotStatusFilter, AdSlotSummary } from '~/composables/ad/api/useAdSlotApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdSlotScreen } from '~/utils/util-ad-slot';
import UtilAd from '~/utils/util-ad';
import UtilAdSlot, { AD_SLOT_SCREENS } from '~/utils/util-ad-slot';

const { t } = useI18n();
useSeoMeta({ title: () => t('adSlot.title') });

const api = useAdSlotApi();
const adApi = useAdApi();
const boardApi = useBoardApi();
const postApi = usePostApi();
const pageApi = useGadgetPageApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const { can } = useAdminPermission();

const targetId = useId();
const typesId = useId();
const mailId = useId();

// ── 화면 고르기 ──
const screen = ref<AdSlotScreen>('HOME');
/** 게시판 slug · 글 id · 가젯 페이지 공개 경로 */
const target = ref<string | null>(null);
const device = ref<'desktop' | 'mobile'>('desktop');
const screenOptions = computed(() => AD_SLOT_SCREENS.map((s) => ({ value: s, label: t(`adSlot.screen.${s}`) })));
const deviceOptions = computed(() => [
    { value: 'desktop', label: t('adSlot.device.desktop') },
    { value: 'mobile', label: t('adSlot.device.mobile') },
]);
const targetLabel = computed(() => (screen.value === 'BOARD' ? t('adSlot.pickBoard') : screen.value === 'POST' ? t('adSlot.pickPost') : t('adSlot.pickPage')));
const path = computed(() => UtilAdSlot.screenPath(screen.value, target.value));

/** 고르기 목록 — 기존 관리자 API(게시판 목록 `board.read` · 최근 글 `post.read` · 가젯 페이지 목록)로 화면을 처음 고를 때 한 번 받는다 */
const targets = reactive<Record<Exclude<AdSlotScreen, 'HOME'>, { value: string; label: string }[] | null>>({ BOARD: null, POST: null, PAGE: null });
const targetsLoading = ref(false);
const targetsError = ref(false);
const targetOptions = computed(() => (screen.value === 'HOME' ? [] : (targets[screen.value] ?? [])));

async function fetchTargets(kind: Exclude<AdSlotScreen, 'HOME'>): Promise<{ value: string; label: string }[]> {
    if (kind === 'BOARD') return (await boardApi.list()).boards.filter((b) => b.active).map((b) => ({ value: b.slug, label: b.name }));
    if (kind === 'POST') {
        const found = await postApi.list({ page: 0, size: 30 });
        return found.items.filter((p) => p.state === 'PUBLIC' && p.title).map((p) => ({ value: p.id, label: `${p.title} · ${p.board.name}` }));
    }
    return (await pageApi.list()).pages.map((p) => ({ value: p.systemPage === 'TIPS' ? '/tips' : `/p/${p.key}`, label: p.name }));
}

async function setScreen(value: AdSlotScreen): Promise<void> {
    screen.value = value;
    target.value = null;
    if (value === 'HOME' || targets[value]) {
        target.value = value === 'HOME' ? null : (targets[value]?.[0]?.value ?? null);
        return;
    }
    targetsLoading.value = true;
    targetsError.value = false;
    try {
        const found = await fetchTargets(value);
        targets[value] = found;
        // 고르는 사이 다른 화면으로 바꿨으면 그대로 둔다
        if (screen.value === value) target.value = found[0]?.value ?? null;
    } catch {
        targetsError.value = true;
        targets[value] = [];
    } finally {
        targetsLoading.value = false;
    }
}

// ── 종류 요약(이름표) ──
const summary = ref<AdSlotSummary[]>([]);
const summaryLoading = ref(false);
const summaryError = ref<ApiError | null>(null);
async function loadSummary(): Promise<void> {
    summaryLoading.value = true;
    summaryError.value = null;
    try {
        summary.value = (await api.summary()).types;
    } catch (e) {
        summaryError.value = toApiError(e);
    } finally {
        summaryLoading.value = false;
    }
}
const summaryRows = computed(() => (summary.value.length ? summary.value : UtilAd.TYPES.map((type) => ({ type, running: 0, limit: 0, adsense: 'NA' as const, places: [] }))));
/** 미리보기 이름표 — 「종류 이름 · 진행 N」 */
const labels = computed<Record<string, string>>(() => Object.fromEntries(summary.value.map((s) => [s.type, t('adSlot.label', { name: t(`bizApplication.adType.${s.type}`), n: s.running })])));

// 미리보기가 찾은 자리 수(종류마다) · 빈 자리 수
const areaCounts = ref<Record<string, number>>({});
const areaEmpty = ref<Record<string, number>>({});
const areasKnown = ref(false);
function onAreas(counts: Record<string, number>, empty: Record<string, number>): void {
    areaCounts.value = counts;
    areaEmpty.value = empty;
    areasKnown.value = true;
}
function onReloading(): void {
    areasKnown.value = false;
    areaCounts.value = {};
    areaEmpty.value = {};
}
function onScreenText(type: AdType): string {
    if (!areasKnown.value) return ' ';
    const n = areaCounts.value[type] ?? 0;
    if (!n) return t('adSlot.notOnScreen');
    const empty = areaEmpty.value[type] ?? 0;
    return empty ? t('adSlot.onScreenEmpty', { n, empty }) : t('adSlot.onScreen', { n });
}

// ── 드로어(종류 목록) ──
const drawerOpen = ref(false);
const openType = ref<AdType | null>(null);
const openSummary = computed(() => summary.value.find((s) => s.type === openType.value) ?? null);
const status = ref<AdSlotStatusFilter | null>(null);
const list = ref<AdSlotListView | null>(null);
const listLoading = ref(false);
const listError = ref<ApiError | null>(null);

let lseq = 0;
async function loadList(): Promise<void> {
    const type = openType.value;
    if (!type) return;
    const mine = ++lseq;
    listLoading.value = true;
    listError.value = null;
    try {
        const found = await api.list(type, status.value);
        if (mine === lseq) list.value = found;
    } catch (e) {
        if (mine === lseq) listError.value = toApiError(e);
    } finally {
        if (mine === lseq) listLoading.value = false;
    }
}

function openSlot(type: string): void {
    if (!(UtilAd.TYPES as readonly string[]).includes(type)) return;
    const next = type as AdType;
    if (openType.value !== next) {
        openType.value = next;
        status.value = null;
        list.value = null;
        void loadList();
    } else if (!drawerOpen.value) void loadList();
    drawerOpen.value = true;
}

function onStatus(value: AdSlotStatusFilter | null): void {
    status.value = value;
    void loadList();
}

// ── 기존 광고 건 서랍 ──
const bookingOpen = ref(false);
const bookingId = ref<string | null>(null);
const bookingDetail = ref<AdBookingDetail | null>(null);
const bookingLoading = ref(false);
const bookingError = ref<ApiError | null>(null);
const bookingStats = ref<AdApplicationStats | null>(null);
const statsLoading = ref(false);
const statsError = ref<ApiError | null>(null);
let statsFor: string | null = null;

async function loadBooking(): Promise<void> {
    const id = bookingId.value;
    if (!id) return;
    bookingLoading.value = true;
    bookingError.value = null;
    try {
        const found = await api.detail(id);
        if (bookingId.value === id) bookingDetail.value = found;
    } catch (e) {
        if (bookingId.value === id) bookingError.value = toApiError(e);
    } finally {
        bookingLoading.value = false;
    }
}

async function loadStats(force: boolean): Promise<void> {
    const id = bookingId.value;
    if (!id || (!force && statsFor === id && !statsError.value)) return;
    statsFor = id;
    statsLoading.value = true;
    statsError.value = null;
    bookingStats.value = null;
    try {
        const found = await api.stats(id);
        if (bookingId.value === id) bookingStats.value = found;
    } catch (e) {
        if (bookingId.value === id) statsError.value = toApiError(e);
    } finally {
        statsLoading.value = false;
    }
}

function openBooking(id: string): void {
    if (bookingId.value !== id) {
        bookingDetail.value = null;
        bookingStats.value = null;
        statsFor = null;
    }
    bookingId.value = id;
    bookingOpen.value = true;
    void loadBooking();
}

// ── 내리기(한 건 · 여러 건) ──
const takedownOpen = ref(false);
const takedownIds = ref<string[]>([]);
const takedownError = ref('');
const takingDown = ref(false);
const notifyMail = ref(true);

function askTakedown(ids: string[]): void {
    if (!ids.length) return;
    takedownIds.value = [...ids];
    takedownError.value = '';
    notifyMail.value = true;
    takedownOpen.value = true;
}

async function submitTakedown(reason: string): Promise<void> {
    const body = { ids: [...takedownIds.value], reason, notifyAdvertiser: notifyMail.value };
    takingDown.value = true;
    takedownError.value = '';
    try {
        const result = await api.takedown(body);
        takedownOpen.value = false;
        if (result.failed.length) toast.error(t('adSlot.takedownPartial', { done: result.takenDown, failed: result.failed.length, reasons: [...new Set(result.failed.map((f) => f.message))].join(' · ') }));
        else toast.success(t('adSlot.takedownDone', { n: result.takenDown }));
        void loadList();
        void loadSummary();
        if (bookingOpen.value && bookingId.value && body.ids.includes(bookingId.value)) {
            void loadBooking();
            if (statsFor) void loadStats(true);
        }
    } catch (e) {
        takedownError.value = toApiError(e).message;
    } finally {
        takingDown.value = false;
    }
}

// ── 광고 등록(기존 운영자 등록 서랍 — 실제 API) ──
const manualOpen = ref(false);
const manualBusy = ref(false);
const manualError = ref('');
const manualFields = ref<Record<string, string>>({});
watch(manualOpen, (open) => {
    if (open) return;
    manualError.value = '';
    manualFields.value = {};
});
async function submitManual(body: AdManualCreateRequest): Promise<void> {
    manualBusy.value = true;
    manualError.value = '';
    manualFields.value = {};
    try {
        await adApi.createManual(body);
        toast.success(t('ad.manualDone'));
        manualOpen.value = false;
        void loadList();
        void loadSummary();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) {
            manualFields.value = failure.fields;
            const loose = Object.entries(failure.fields).filter(([k]) => !UtilAd.MANUAL_FIELDS.includes(k));
            if (loose.length) manualError.value = loose.map(([, v]) => v).join(' · ');
        } else manualError.value = failure.message;
    } finally {
        manualBusy.value = false;
    }
}

onMounted(() => {
    if (can('ad.read')) void loadSummary();
});
</script>
