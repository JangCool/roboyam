<template>
    <!--
        광고 › 일정표(`/ads`) — 02-ad.md 7-1, 00-overview.md ★-2(수동 결제)·F5(되돌리기 보상).
        · 유형 탭(넓은 배너·띠·네모·작은 배너·한 줄·핫딜 홈 상단·핫딜 말머리 상단) → 요약 띠(한도·진행·예약·결제 대기) → 두 달 달력(날짜별 남은 칸 점) → 광고 건 목록.
        · 한 줄: 광고주/사업자 · 기간(「~10/10 00:00」 반열림) · 상태 · 결제 방식(온라인/수동) · 비중. 줄을 누르면 상세 서랍(`?id=`).
        · [수동 등록]: 광고주 → 종류·기간(남은 칸) → 소재 → 계약서 사본(필수) → 입금 확인 → [예약 확정].
        · 긴급 내림(사유·메일·「최대 20초 남을 수 있어요」) · 되돌리기(중단 시간만큼 끝 시각 연장 / 일할 환불).
        권한: 화면 `ad.read` · 등록 `ad.manage` · 긴급 내림 `ad.takedown` · 되돌리기 `ad.restore`(서버 `Permission` 에 추가 예정 — 지금은 `ad.takedown` 으로 본다).
        서버 호출은 `useAdApi`(지금 목업 — `?mock=ads-fail`·`ads-empty`). 사업자 신청 심사에서 승인·확정한 광고 건도 이 장부에 나타난다(같은 세션 메모리).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('ad.title')" :subtitle="t('ad.subtitle')">
            <template #actions>
                <Button v-if="can('ad.manage')" type="button" :label="t('ad.actManual')" icon="pi pi-plus" size="small" @click="manualOpen = true" />
            </template>
        </PageTitle>

        <Tabs :value="type" scrollable class="min-w-0" @update:value="(v: string | number) => selectType(String(v) as AdType)">
            <TabList :aria-label="t('ad.typeTabs')">
                <Tab v-for="ty in UtilAd.TYPES" :key="ty" :value="ty">{{ t(`bizApplication.adType.${ty}`) }}</Tab>
            </TabList>
        </Tabs>

        <!-- 요약 띠 -->
        <dl class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-4">
            <div v-for="s in summaryCells" :key="s.key" class="flex flex-col gap-0.5 rounded-xl border border-(--line) bg-(--p-content-background) px-3 py-2.5">
                <dt class="text-xs text-muted-color">{{ s.label }}</dt>
                <dd class="m-0 text-lg font-semibold tabular-nums" :class="s.tone">{{ calendar ? s.value : '—' }}</dd>
            </div>
        </dl>

        <div class="min-w-0 rounded-xl border border-(--line) bg-(--p-content-background) p-3">
            <ErrorState v-if="calendarError" :title="t('ad.calendarFailed')" :description="calendarError.message" :retry="true" :retrying="calendarLoading" @retry="loadCalendar" />
            <AdCalendar
                v-else
                :month="month"
                :days="calendar?.days ?? []"
                :bookings="calendarBookings"
                :loading="calendarLoading"
                :compact="narrow"
                :can-pick="can('ad.manage')"
                @month="onMonth"
                @open="openFromCalendar"
                @pick="startManualOn"
            />
        </div>

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('ad.search')" @submit.prevent="search">
            <label class="sr-only" :for="statusId">{{ t('ad.statusLabel') }}</label>
            <Select v-model="status" :input-id="statusId" :options="statusOptions" option-label="label" option-value="value" class="w-44" @change="search" />
            <label class="sr-only" :for="qId">{{ t('ad.search') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('ad.searchPlaceholder')" class="w-full max-w-xs" />
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
                scrollable
                :row-class="() => 'cursor-pointer'"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                @page="onPage"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('ad.loadFailed')" :description="loadError.message" :retry="true" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || applied.status" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-megaphone" :title="t('ad.empty')">
                        <Button v-if="can('ad.manage')" type="button" :label="t('ad.firstManual')" icon="pi pi-plus" size="small" @click="manualOpen = true" />
                    </EmptyState>
                </template>

                <Column :header="t('ad.col.advertiser')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="flex items-center gap-1.5">
                            <span class="truncate text-[13px] font-medium">{{ data.advertiser.name }}</span>
                            <Tag :value="t(`ad.advertiserKind.${data.advertiser.kind}`)" severity="secondary" />
                        </span>
                        <code v-if="data.applicationNo" class="text-[11px] text-muted-color">{{ data.applicationNo }}</code>
                        <span v-else class="text-[11px] text-muted-color">{{ t('ad.sourceKind.OPERATOR') }}</span>
                    </template>
                </Column>
                <Column :header="t('ad.col.period')" style="min-width: 12rem">
                    <template #body="{ data }"><span class="text-[12px] tabular-nums">{{ UtilBizApplicationMessages.period(t, time, data.period) }}</span></template>
                </Column>
                <Column :header="t('ad.col.status')" style="min-width: 7rem">
                    <template #body="{ data }"><Tag :value="t(`ad.status.${data.status}`)" :severity="UtilAd.statusSeverity(data.status)" /></template>
                </Column>
                <Column :header="t('ad.col.method')" style="min-width: 6rem">
                    <template #body="{ data }"><Tag :value="t(`bizApplication.method.${data.paymentMethod}`)" :severity="data.paymentMethod === 'ONLINE' ? 'info' : 'secondary'" /></template>
                </Column>
                <Column :header="t('ad.col.weight')" style="min-width: 4rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }"><div class="text-right text-[13px] tabular-nums">{{ data.weight }}</div></template>
                </Column>
            </DataTable>
        </div>

        <AdBookingDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :busy="working !== null"
            :can-takedown="can('ad.takedown')"
            :can-restore="can('ad.takedown')"
            @retry="loadDetail"
            @takedown="openDialog('takedown')"
            @restore="openDialog('restore')"
        />

        <AdManualDrawer
            v-model:visible="manualOpen"
            :initial-type="type"
            :initial-start="manualStart"
            :search-advertisers="api.advertisers"
            :create-advertiser="api.createAdvertiser"
            :check-availability="api.availability"
            :upload-contract="api.uploadContract"
            :to-error="toApiError"
            :busy="working === 'manual'"
            :error="manualError"
            :field-errors="manualFields"
            @submit="submitManual"
        />

        <BizReasonDialog
            v-model:visible="dialogs.takedown"
            :title="t('ad.takedownTitle')"
            :description="t('ad.takedownBody')"
            :confirm-label="t('ad.actTakedown')"
            tone="danger"
            :busy="working === 'takedown'"
            :error="dialogError"
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

        <BizReasonDialog
            v-model:visible="dialogs.restore"
            :title="t('ad.restoreTitle')"
            :description="t('ad.restoreBody')"
            :confirm-label="t('ad.actRestore')"
            :busy="working === 'restore'"
            :error="dialogError"
            @submit="submitRestore"
        >
            <fieldset class="m-0 flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5">
                <legend class="px-1 text-[13px] font-semibold">{{ t('ad.compensation') }}</legend>
                <div v-for="opt in compensationOptions" :key="opt.value" class="flex items-start gap-2">
                    <RadioButton v-model="compensation" :input-id="`${mailId}-${opt.value}`" :name="`${mailId}-comp`" :value="opt.value" />
                    <label :for="`${mailId}-${opt.value}`" class="flex flex-col text-[13px] leading-snug">{{ opt.label }}<small class="text-xs text-muted-color">{{ opt.help }}</small></label>
                </div>
            </fieldset>
        </BizReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { AdBookingDetail, AdBookingRow, AdBookingStatus, AdCalendar, AdManualCreateRequest, AdRestoreCompensation, AdType } from '~/composables/ad/api/useAdApi.types';
import { AD_BOOKING_STATUSES } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

type Work = 'takedown' | 'restore' | 'manual';

const { t } = useI18n();
useSeoMeta({ title: () => t('ad.title') });

const api = useAdApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const statusId = useId();
const qId = useId();
const mailId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
const type = ref<AdType>((UtilAd.TYPES as readonly string[]).includes(queryString('type')) ? (queryString('type') as AdType) : 'WIDE_BANNER');
const status = ref<string>('');
const q = ref('');
const applied = ref<{ status?: AdBookingStatus; q?: string }>({});
const statusOptions = computed(() => [{ value: '', label: t('ad.allStatus') }, ...AD_BOOKING_STATUSES.map((s) => ({ value: s, label: t(`ad.status.${s}`) }))]);

// ── 달력·요약 ──
const month = ref(UtilBizApplication.kstToday().slice(0, 7));
const calendar = ref<AdCalendar | null>(null);
const calendarLoading = ref(false);
const calendarError = ref<ApiError | null>(null);
/** 달력 칸에 이름으로 그릴 이 유형의 광고 건(취소 제외) — 목록의 필터·쪽과 따로 받는다 */
const calendarBookings = ref<AdBookingRow[]>([]);
let cseq = 0;
async function loadCalendar(): Promise<void> {
    const mine = ++cseq;
    calendarLoading.value = true;
    calendarError.value = null;
    try {
        const [found, booked] = await Promise.all([api.calendar(type.value, month.value), api.list({ type: type.value, page: 0, size: 500 })]);
        if (mine !== cseq) return;
        calendar.value = found;
        calendarBookings.value = booked.items.filter((b) => b.status !== 'CANCELED');
    } catch (e) {
        if (mine === cseq) calendarError.value = toApiError(e);
    } finally {
        if (mine === cseq) calendarLoading.value = false;
    }
}
function onMonth(value: string): void {
    month.value = value;
    void loadCalendar();
}
const summaryCells = computed(() => {
    const s = calendar.value?.summary;
    return [
        { key: 'limit', label: t('ad.sumLimit'), value: s?.limit ?? 0, tone: '' },
        { key: 'running', label: t('ad.sumRunning'), value: s?.running ?? 0, tone: 'text-green-700 dark:text-green-400' },
        { key: 'scheduled', label: t('ad.sumScheduled'), value: s?.scheduled ?? 0, tone: '' },
        { key: 'awaiting', label: t('ad.sumAwaiting'), value: s?.awaitingPayment ?? 0, tone: (s?.awaitingPayment ?? 0) > 0 ? 'text-amber-700 dark:text-amber-400' : '' },
    ];
});

const narrow = ref(false);
let media: MediaQueryList | null = null;
const onMedia = () => (narrow.value = Boolean(media?.matches));

// ── 목록 ──
const rows = ref<AdBookingRow[]>([]);
const total = ref(0);
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
        const found = await api.list({ ...applied.value, type: type.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        total.value = 0;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function refreshAll(): void {
    void reload();
    void loadCalendar();
}

function selectType(value: AdType): void {
    type.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, type: value } });
    refreshAll();
}

function search(): void {
    applied.value = { status: (status.value || undefined) as AdBookingStatus | undefined, q: q.value.trim() || undefined };
    first.value = 0;
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

// ── 상세 서랍 ──
const drawerOpen = ref(false);
const drawerId = ref<string | null>(null);
const drawerSummary = ref<AdBookingRow | null>(null);
const drawerDetail = ref<AdBookingDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);

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

function show(id: string, row: AdBookingRow | null): void {
    if (drawerId.value !== id) drawerDetail.value = null;
    drawerId.value = id;
    drawerSummary.value = row;
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as AdBookingRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, id: row.id } });
}

function openFromCalendar(id: string): void {
    show(id, calendarBookings.value.find((b) => b.id === id) ?? null);
    void router.replace({ query: { ...route.query, id } });
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
    if (open || !route.query.id) return;
    const { id: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 동작 ──
const working = ref<Work | null>(null);
const dialogError = ref('');
const dialogs = reactive({ takedown: false, restore: false });
const notifyMail = ref(true);
const compensation = ref<AdRestoreCompensation>('EXTEND');
const compensationOptions = computed(() => [
    { value: 'EXTEND' as const, label: t('ad.compExtend'), help: t('ad.compExtendHelp') },
    { value: 'PRORATE_REFUND' as const, label: t('ad.compRefund'), help: t('ad.compRefundHelp') },
]);

function openDialog(name: keyof typeof dialogs): void {
    dialogError.value = '';
    notifyMail.value = true;
    compensation.value = 'EXTEND';
    dialogs[name] = true;
}

async function act(work: Work, call: () => Promise<AdBookingDetail>, done: string, dialog: keyof typeof dialogs): Promise<void> {
    working.value = work;
    dialogError.value = '';
    try {
        drawerDetail.value = await call();
        toast.success(done);
        dialogs[dialog] = false;
        refreshAll();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 409) void loadDetail();
        dialogError.value = failure.message;
    } finally {
        working.value = null;
    }
}

async function submitTakedown(reason: string): Promise<void> {
    const d = drawerDetail.value;
    const mail = notifyMail.value;
    if (d) await act('takedown', () => api.takedown(d.id, { reason, notifyMail: mail }), t('ad.takenDown'), 'takedown');
}

async function submitRestore(reason: string): Promise<void> {
    const d = drawerDetail.value;
    const comp = compensation.value;
    if (d) await act('restore', () => api.restore(d.id, { reason, compensation: comp }), t('ad.restored'), 'restore');
}

// ── 수동 등록 ──
const manualOpen = ref(false);
/** 달력에서 고른 시작 날짜(`YYYY-MM-DD`) — 없으면 서랍이 내일로 잡는다 */
const manualStart = ref<string | null>(null);
watch(manualOpen, (open) => {
    if (!open) manualStart.value = null;
});
function startManualOn(ymd: string): void {
    manualStart.value = ymd;
    manualOpen.value = true;
}
const manualError = ref('');
const manualFields = ref<Record<string, string>>({});
async function submitManual(body: AdManualCreateRequest): Promise<void> {
    working.value = 'manual';
    manualError.value = '';
    manualFields.value = {};
    try {
        const created = await api.createManual(body);
        toast.success(t('ad.manualDone'));
        manualOpen.value = false;
        if (created.type !== type.value) selectType(created.type);
        else refreshAll();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) manualFields.value = failure.fields;
        else manualError.value = failure.message;
    } finally {
        working.value = null;
    }
}

onMounted(() => {
    media = window.matchMedia('(max-width: 767px)');
    onMedia();
    media.addEventListener('change', onMedia);
    refreshAll();
});
onBeforeUnmount(() => media?.removeEventListener('change', onMedia));
</script>
