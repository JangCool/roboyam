<template>
    <!--
        공구 › 취소·반품 요청(`/group-buy-requests`) — 대기열(계약 결정 15·44·54·55·68·70·72, commerce.md 10-3·10-8).
        · 상태 탭(건수) · 종류(취소/반품) · 「기한 경고만」 · 찾기(주문 번호·닉네임·회원 번호). 오래 기다린 것이 위.
        · 한 줄: 요청 시각 · 주문 · 회원 · 종류(교환 표시) · 대표 사유 · 줄 수·수량 · 환불 예정 금액(`preview_amount`) · 경과 영업일(2영업일 경고 · 3영업일 심각 — 기한 시작은 취소 = 요청한 날, 반품 = 반환받은 날) · 추가 결제.
        · 줄을 누르면 상세 서랍(`?request=ID`): 승인(다시 계산한 값이 다르면 두 값 + 사유를 확인) · 거절(사유 코드 + 근거 사진) · 회수 송장 · 회수 확인(반환받은 날) · 반품 완료(줄마다 처리 방법·수량, 보상액)
          · 교환 재발송 · 교환할 물건 없음 → 환불로 바꾸기.
        권한: 화면 `commerce.order.read` · 처리 `commerce.order.manage` · 환불이 나가는 승인(발송 전 취소)·반품 완료 + `commerce.refund.approve` · 보상액 누계가 문턱 이상이면 `commerce.refund.large`.
        서버 호출은 `useGroupBuyRequestApi`(지금 목업 — `?mock=requests-fail`·`requests-empty`·`approve-changed` …).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.requestsTitle')" :subtitle="t('groupBuy.requestsSubtitle')" />

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('groupBuy.requestsSearch')" @submit.prevent="search">
            <div class="flex items-center gap-2">
                <span :id="typeLabelId" class="text-[13px] text-muted-color">{{ t('groupBuy.requestTypeLabel') }}</span>
                <FilterOptionGroup v-model="type" :options="typeOptions" :labelledby="typeLabelId" />
            </div>
            <div class="flex items-center gap-1.5">
                <Checkbox v-model="warnOnly" binary :input-id="warnId" />
                <label :for="warnId" class="text-[13px]">{{ t('groupBuy.warnOnly') }}</label>
            </div>
            <label class="sr-only" :for="qId">{{ t('groupBuy.filterQ') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('groupBuy.filterQPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="status" scrollable class="min-w-0" @update:value="(v: string | number) => selectStatus(String(v))">
            <TabList :aria-label="t('groupBuy.statusTabs')">
                <Tab v-for="tab in tabs" :key="tab.value" :value="tab.value">
                    <span class="inline-flex items-center gap-1.5">{{ tab.label }}<span class="text-[11px] font-normal tabular-nums text-muted-color">{{ tab.count }}</span></span>
                </Tab>
            </TabList>
        </Tabs>

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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuy.forbidden') : t('groupBuy.requestsLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || applied.type || applied.level || status !== 'REQUESTED'" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-check-circle" :title="t('groupBuy.requestsEmpty')" :description="t('groupBuy.requestsEmptyHint')" />
                </template>

                <Column :header="t('groupBuy.col.requestedAt')" style="min-width: 8.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.shortDateTime(data.createdAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.order')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <code class="block text-[12px]">{{ data.order.orderNo }}</code>
                        <span class="block truncate text-[11px] text-muted-color">{{ data.groupBuy.title }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.member')" style="min-width: 7rem"><template #body="{ data }"><PaymentMemberLabel :member="data.member" /></template></Column>
                <Column :header="t('groupBuy.col.type')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <div class="text-[13px]">{{ t(`groupBuy.requestType.${data.type}`) }}<span v-if="data.resolution === 'EXCHANGE'" class="text-muted-color"> · {{ t('groupBuy.resolutionExchange') }}</span></div>
                        <div class="text-[11px] text-muted-color">{{ t(`groupBuy.reasonCode.${data.mainReason}`) }} · {{ t('groupBuy.linesQty', { lines: data.lineCount, qty: data.totalQty }) }}</div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.preview')" style="min-width: 7rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right text-[13px] tabular-nums">{{ t('groupBuy.won', { amount: UtilGroupBuy.won(data.previewAmount, locale) }) }}</div>
                        <div v-if="data.extraPayment" class="text-right text-[11px] text-muted-color">{{ t('groupBuy.extraShort', { amount: UtilGroupBuy.won(data.extraPayment.amount, locale), status: t(`groupBuy.extraPaymentStatus.${data.extraPayment.status}`) }) }}</div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.status')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag :value="t(`groupBuy.requestStatus.${data.status}`)" :severity="UtilGroupBuy.requestSeverity(data.status)" />
                            <Tag v-if="(data.status === 'REQUESTED' || data.status === 'APPROVED') && data.deadlineLevel !== 'OK'" :value="t(`groupBuy.deadline.${data.deadlineLevel}`, { n: data.elapsedBusinessDays })" :severity="UtilGroupBuy.deadlineSeverity(data.deadlineLevel)" icon="pi pi-clock" />
                            <span v-else-if="data.status === 'REQUESTED' || data.status === 'APPROVED'" class="text-[11px] text-muted-color">{{ t('groupBuy.elapsedDays', { n: data.elapsedBusinessDays }) }}</span>
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <GroupBuyRequestDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :busy="working !== null"
            :can-manage="can('commerce.order.manage')"
            :can-refund="can('commerce.refund.approve')"
            @retry="loadDetail"
            @approve="approve"
            @reject="openReject"
            @pickup="openTracking('pickup')"
            @receive="openReceive"
            @complete="openComplete"
            @exchange-ship="openTracking('exchange')"
            @convert="openConvert"
        />

        <GroupBuyRejectDialog v-if="drawerDetail" v-model:visible="rejectOpen" :type="drawerDetail.type" :withdrawal-restricted="drawerDetail.withdrawalRestricted" :upload="uploadPhoto" :busy="working === 'reject'" :error="dialogError" :field-errors="dialogFields" @submit="submitReject" />
        <GroupBuyTrackingDialog
            v-model:visible="trackingOpen"
            :title="trackingKind === 'pickup' ? t('groupBuy.pickupTitle') : t('groupBuy.exchangeTitle')"
            :description="trackingKind === 'pickup' ? t('groupBuy.pickupBody') : t('groupBuy.exchangeBody')"
            :confirm-label="trackingKind === 'pickup' ? t('common.save') : t('groupBuy.actExchangeShip')"
            :carriers="carriers"
            :busy="working === 'tracking'"
            :error="dialogError"
            :field-errors="dialogFields"
            @submit="submitTracking"
        />
        <GroupBuyCompleteDialog v-model:visible="completeOpen" :request="drawerDetail" :busy="working === 'complete'" :error="dialogError" :field-errors="dialogFields" :can-large="can('commerce.refund.large')" @submit="submitComplete" />
        <PaymentReasonDialog v-model:visible="convertOpen" :title="t('groupBuy.convertTitle')" :description="t('groupBuy.convertBody')" :confirm-label="t('groupBuy.actConvert')" :busy="working === 'convert'" :error="dialogError" :field-error="dialogFields.reason ?? ''" @submit="submitConvert" />

        <!-- 회수 확인 — 반환받은 날 -->
        <Dialog v-model:visible="receiveOpen" modal :draggable="false" :closable="working !== 'receive'" :header="t('groupBuy.receiveTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }">
            <form :id="receiveFormId" class="flex flex-col gap-3" novalidate @submit.prevent="submitReceive">
                <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('groupBuy.receiveBody') }}</p>
                <LabeledField :label="t('groupBuy.receivedAt')" :error="dialogFields.receivedAt ?? (receiveSubmitted && !receiveDate ? t('groupBuy.receiveRequired') : '')">
                    <template #default="{ id, describedBy, invalid }">
                        <AppDatePicker v-model="receiveDate" :input-id="id" :max="UtilGroupBuy.kstToday()" :invalid="invalid" :described-by="describedBy" />
                    </template>
                </LabeledField>
                <Message v-if="dialogError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ dialogError }}</Message>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="working === 'receive'" @click="receiveOpen = false" />
                <Button type="submit" :form="receiveFormId" :label="t('groupBuy.actReceive')" :loading="working === 'receive'" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyCarrierChoice } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import type {
    GroupBuyCompleteRequest,
    GroupBuyEvidencePhoto,
    GroupBuyRejectRequest,
    GroupBuyRequestDetail,
    GroupBuyRequestQuery,
    GroupBuyRequestRow,
    GroupBuyRequestStatus,
    GroupBuyRequestType,
} from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import { GROUP_BUY_REQUEST_STATUSES } from '~/composables/groupbuy/api/useGroupBuyRequestApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

type Work = 'approve' | 'reject' | 'tracking' | 'receive' | 'complete' | 'convert';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('groupBuy.requestsTitle') });

const api = useGroupBuyRequestApi();
const orderApi = useGroupBuyOrderApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const route = useRoute();
const router = useRouter();

const typeLabelId = useId();
const warnId = useId();
const qId = useId();
const receiveFormId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
/** 기본 탭은 「접수됨」(처리할 것) */
const status = ref<string>((GROUP_BUY_REQUEST_STATUSES as readonly string[]).includes(queryString('status')) || queryString('status') === 'ALL' ? queryString('status') : 'REQUESTED');
const type = ref<string>('');
const warnOnly = ref(queryString('level') === 'WARN');
const q = ref('');
const typeOptions = computed(() => [
    { value: '', label: t('common.all') },
    { value: 'CANCEL', label: t('groupBuy.requestType.CANCEL') },
    { value: 'RETURN', label: t('groupBuy.requestType.RETURN') },
]);
const applied = ref<Omit<GroupBuyRequestQuery, 'page' | 'size' | 'status'>>({});

const rows = ref<GroupBuyRequestRow[]>([]);
const counts = ref<Record<GroupBuyRequestStatus, number> | null>(null);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() => {
    const c = counts.value;
    const all = c ? GROUP_BUY_REQUEST_STATUSES.reduce((sum, s) => sum + c[s], 0) : '—';
    return [...GROUP_BUY_REQUEST_STATUSES.map((s) => ({ value: s, label: t(`groupBuy.requestStatus.${s}`), count: c ? c[s] : '—' })), { value: 'ALL', label: t('common.all'), count: all }];
});

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ ...applied.value, status: status.value === 'ALL' ? undefined : (status.value as GroupBuyRequestStatus), page: Math.floor(first.value / size.value), size: size.value });
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

function search(): void {
    applied.value = { type: (type.value || undefined) as GroupBuyRequestType | undefined, level: warnOnly.value ? 'WARN' : undefined, q: q.value.trim() || undefined };
    first.value = 0;
    void router.replace({ query: { ...route.query, level: warnOnly.value ? 'WARN' : undefined } });
    void reload();
}

function selectStatus(value: string): void {
    status.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, status: value } });
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
const drawerSummary = ref<GroupBuyRequestRow | null>(null);
const drawerDetail = ref<GroupBuyRequestDetail | null>(null);
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

function show(id: string, row: GroupBuyRequestRow | null): void {
    if (drawerId.value !== id) drawerDetail.value = null;
    drawerId.value = id;
    drawerSummary.value = row;
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as GroupBuyRequestRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, request: row.id } });
}

watch(
    () => route.query.request,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open || !route.query.request) return;
    const { request: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 동작 ──
const working = ref<Work | null>(null);
const dialogError = ref('');
const dialogFields = ref<Record<string, string>>({});

function resetDialog(): void {
    dialogError.value = '';
    dialogFields.value = {};
}

/** 동작 하나 — 성공하면 서랍·목록을 새로, 실패는 대화상자 안(칸 오류 · 그 밖) 또는 토스트(대화상자 없는 동작) */
async function run(work: Work, call: () => Promise<GroupBuyRequestDetail>, done: string, close?: () => void): Promise<void> {
    working.value = work;
    resetDialog();
    try {
        drawerDetail.value = await call();
        toast.success(done);
        close?.();
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (!close) {
            toast.error(failure.message, 8000);
            if (failure.code === 'GROUP_BUY_REFUND_AMOUNT_CHANGED' || failure.code === 'GROUP_BUY_REQUEST_NOT_OPEN') void loadDetail();
            return;
        }
        if (failure.code === 'VALIDATION' && failure.fields) dialogFields.value = failure.fields;
        else dialogError.value = failure.message;
    } finally {
        working.value = null;
    }
}

async function approve(): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    const recalculated = d.recalculated?.breakdown.total;
    const amount = recalculated ?? d.previewAmount;
    const changed = recalculated !== undefined && recalculated !== d.previewAmount;
    const won = (n: number) => UtilGroupBuy.won(n, locale.value);
    const message = d.type === 'CANCEL' ? t('groupBuy.approveCancelBody', { amount: won(amount) }) : d.resolution === 'EXCHANGE' ? t('groupBuy.approveExchangeBody') : t('groupBuy.approveReturnBody', { amount: won(amount) });
    const ok = await confirm({
        title: changed ? t('groupBuy.approveChangedTitle', { before: won(d.previewAmount), after: won(amount) }) : t('groupBuy.approveTitle'),
        message: changed ? `${message} ${t('groupBuy.approveChangedNote')}` : message,
        confirmLabel: d.type === 'CANCEL' ? t('groupBuy.actApproveCancel') : t('groupBuy.actApprove'),
    });
    if (ok) await run('approve', () => api.approve(d.id, { expectedAmount: amount }), d.type === 'CANCEL' ? t('groupBuy.approvedCancel') : t('groupBuy.approved'));
}

const rejectOpen = ref(false);
function openReject(): void {
    resetDialog();
    rejectOpen.value = true;
}
function uploadPhoto(file: File): Promise<GroupBuyEvidencePhoto> {
    return api.uploadPhoto(file);
}
async function submitReject(body: GroupBuyRejectRequest): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('reject', () => api.reject(d.id, body), t('groupBuy.rejected'), () => (rejectOpen.value = false));
}

const carriers = ref<GroupBuyCarrierChoice[]>([]);
const trackingOpen = ref(false);
const trackingKind = ref<'pickup' | 'exchange'>('pickup');
async function openTracking(kind: 'pickup' | 'exchange'): Promise<void> {
    resetDialog();
    trackingKind.value = kind;
    trackingOpen.value = true;
    if (!carriers.value.length) {
        try {
            carriers.value = await orderApi.carriers();
        } catch (e) {
            dialogError.value = toApiError(e).message;
        }
    }
}
async function submitTracking(body: { carrierCode: string; trackingNo: string }): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    if (trackingKind.value === 'pickup') await run('tracking', () => api.setPickup(d.id, body), t('groupBuy.pickupSaved'), () => (trackingOpen.value = false));
    else await run('tracking', () => api.exchangeShip(d.id, body), t('groupBuy.exchangeShipped'), () => (trackingOpen.value = false));
}

const receiveOpen = ref(false);
const receiveDate = ref('');
const receiveSubmitted = ref(false);
function openReceive(): void {
    resetDialog();
    receiveDate.value = UtilGroupBuy.kstToday();
    receiveSubmitted.value = false;
    receiveOpen.value = true;
}
async function submitReceive(): Promise<void> {
    receiveSubmitted.value = true;
    const d = drawerDetail.value;
    if (!d || !receiveDate.value) return;
    const receivedAt = receiveDate.value;
    await run('receive', () => api.receive(d.id, { receivedAt }), t('groupBuy.receivedDone'), () => (receiveOpen.value = false));
}

const completeOpen = ref(false);
function openComplete(): void {
    resetDialog();
    completeOpen.value = true;
}
async function submitComplete(body: GroupBuyCompleteRequest): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('complete', () => api.complete(d.id, body), t('groupBuy.completed'), () => (completeOpen.value = false));
}

const convertOpen = ref(false);
function openConvert(): void {
    resetDialog();
    convertOpen.value = true;
}
async function submitConvert(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (d) await run('convert', () => api.convertToRefund(d.id, reason), t('groupBuy.converted'), () => (convertOpen.value = false));
}

onMounted(() => {
    applied.value = { level: warnOnly.value ? 'WARN' : undefined };
    void reload();
});
</script>
