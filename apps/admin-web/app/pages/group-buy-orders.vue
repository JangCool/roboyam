<template>
    <!--
        공구 › 주문(`/group-buy-orders`) — 공구 주문(한 주문에 옵션 여러 줄) 목록·처리(계약 결정 13·23·29·30·32·47·52·53·70, commerce.md 10-3·10-9).
        · 찾기: 주문 번호(`G2026102800012`)·닉네임·회원 번호 — 배송지는 암호문이라 찾지 못한다는 안내를 칸 아래에 둔다. 공구 거르기 · 기간 · 상태 탭(건수 — 서버 `counts`).
          대시보드 링크의 `?alert=SHIP_OVERDUE`(발송 예정일·기한 지난 발송 전) · `?alert=DELIVERY_STALE`(발송 뒤 N일 지난 SHIPPED)는 지울 수 있는 조건 칩으로 보인다.
        · 고른 주문 일괄: 준비중 · 발송(화면 안 표로 택배사·송장, 200건) · 배송완료 — 결과는 줄 단위(부분 성공) 대화상자. 공구를 거르면 그 공구의 발주 처리(공구 단위 ORDERED)·배송 명단 내보내기.
        · 줄을 누르면 상세 서랍(`?order=ID`): 배송지 열람(사유) · 운영 메모 · 송장 정정(사유) · 운영 환불(줄·수량 / 금액, 고액 문턱 안내).
        권한: 화면 `commerce.order.read` · 처리 `commerce.order.manage` · 운영 환불 `commerce.refund.approve`(+ `commerce.refund.large`) · 배송지 열람 `commerce.address.reveal` · 명단 `commerce.export` + `commerce.address.reveal`.
        서버 호출은 `useGroupBuyOrderApi`·`useGroupBuyAdminApi`(지금 목업 — 스위치는 `groupbuy-mock.ts` 머리).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuy.ordersTitle')" :subtitle="t('groupBuy.ordersSubtitle')" />

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('groupBuy.ordersSearch')" @submit.prevent="search">
            <FilterRow v-if="groupChoices.length" :label="t('groupBuy.filterGroupBuy')" :label-for="ids.gb">
                <Select :input-id="ids.gb" v-model="filter.groupBuyId" :options="groupChoices" option-label="label" option-value="value" filter show-clear :placeholder="t('common.all')" class="w-full max-w-md" />
            </FilterRow>
            <FilterRow :label="t('groupBuy.filterPeriod')" :error="errorOf('from', 'to')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('payment.filter.from')" :to-label="t('payment.filter.to')" />
            </FilterRow>
            <FilterRow :label="t('groupBuy.filterQ')" :label-for="ids.q" :error="errorOf('q')">
                <div class="flex w-full flex-col gap-1">
                    <InputText :id="ids.q" v-model="filter.q" type="search" maxlength="100" autocomplete="off" :placeholder="t('groupBuy.filterQPlaceholder')" :aria-describedby="`${ids.q}-help`" class="w-full max-w-sm" />
                    <p :id="`${ids.q}-help`" class="m-0 text-xs text-muted-color">{{ t('groupBuy.filterQHelp') }}</p>
                </div>
            </FilterRow>
            <div v-if="alert" class="flex flex-wrap items-center gap-2">
                <Chip :label="t(`groupBuy.alert.${alert}`)" removable @remove="clearAlert" />
            </div>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('payment.filter.reset')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
        </form>

        <Tabs :value="status" scrollable class="min-w-0" @update:value="(v: string | number) => selectStatus(String(v))">
            <TabList :aria-label="t('groupBuy.statusTabs')">
                <Tab v-for="tab in tabs" :key="tab.value" :value="tab.value">
                    <span class="inline-flex items-center gap-1.5">{{ tab.label }}<span class="text-[11px] font-normal tabular-nums text-muted-color">{{ tab.count }}</span></span>
                </Tab>
            </TabList>
        </Tabs>

        <!-- 일괄 처리 줄 -->
        <div v-if="canManage || canExport" class="flex flex-wrap items-center gap-2">
            <span class="text-[13px] text-muted-color">{{ t('groupBuy.selectedCount', { n: selection.length }) }}</span>
            <template v-if="canManage">
                <Button type="button" :label="t('groupBuy.actPrepare')" size="small" severity="secondary" variant="outlined" :disabled="!selection.length || working !== null" :loading="working === 'prepare'" @click="bulkPrepare" />
                <Button type="button" :label="t('groupBuy.actShip')" icon="pi pi-send" size="small" :disabled="!selection.length || working !== null" @click="openShip(selection)" />
                <Button type="button" :label="t('groupBuy.actDeliver')" icon="pi pi-check" size="small" severity="secondary" variant="outlined" :disabled="!selection.length || working !== null" :loading="working === 'deliver'" @click="bulkDeliver" />
            </template>
            <span class="mx-1 hidden h-5 w-px bg-(--line) sm:inline-block" aria-hidden="true" />
            <Button v-if="canManage && selectedGroup?.status === 'CLOSED_SUCCESS' && selectedGroup.fulfillment === 'AFTER_CLOSE'" type="button" :label="t('groupBuy.actMarkOrdered')" icon="pi pi-truck" size="small" severity="secondary" variant="outlined" :loading="working === 'ordered'" @click="markOrdered" />
            <Button v-if="canExport" type="button" :label="t('groupBuy.actExport')" icon="pi pi-download" size="small" severity="secondary" variant="outlined" :disabled="!applied.groupBuyId" v-tooltip.top="applied.groupBuyId ? undefined : t('groupBuy.exportNeedGroup')" @click="openExport" />
            <span v-if="selection.length > UtilGroupBuy.BULK_MAX" class="text-xs text-red-600 dark:text-red-400">{{ t('groupBuy.bulkTooMany', { max: UtilGroupBuy.BULK_MAX }) }}</span>
        </div>
        <Message v-if="bulkError" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ bulkError }}</Message>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                v-model:selection="selection"
                :value="rows"
                data-key="id"
                lazy
                paginator
                :first="first"
                :rows="size"
                :total-records="total"
                :rows-per-page-options="[20, 50, 100, 200]"
                :loading="busy"
                scrollable
                :row-class="() => 'cursor-pointer'"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                @page="onPage"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuy.forbidden') : t('groupBuy.ordersLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="hasApplied" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-list" :title="t('groupBuy.ordersEmpty')" :description="t('groupBuy.ordersEmptyHint')" />
                </template>

                <Column v-if="canManage" selection-mode="multiple" header-style="width: 3rem" />
                <Column :header="t('groupBuy.col.orderNo')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <code class="block text-[12px]">{{ data.orderNo }}</code>
                        <span class="text-[11px] tabular-nums text-muted-color">{{ time.shortDateTime(data.createdAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.member')" style="min-width: 7rem"><template #body="{ data }"><PaymentMemberLabel :member="data.member" /></template></Column>
                <Column :header="t('groupBuy.col.groupBuy')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <div class="truncate text-[13px]">{{ data.groupBuy.title }}<span v-if="data.groupBuy.roundNo > 1" class="text-muted-color"> · {{ t('groupBuy.roundTag', { n: data.groupBuy.roundNo }) }}</span></div>
                        <div class="truncate text-[11px] text-muted-color">{{ data.itemSummary }}</div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.amount')" style="min-width: 7.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right tabular-nums">
                            <div class="text-[13px]">{{ t('groupBuy.won', { amount: UtilGroupBuy.won(data.amount, locale) }) }}</div>
                            <div v-if="data.refundedAmount > 0" class="text-[11px] text-orange-600 dark:text-orange-400">{{ t('groupBuy.refundedLine', { amount: UtilGroupBuy.won(data.refundedAmount, locale) }) }}</div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.status')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag :value="t(`groupBuy.orderStatus.${data.status}`)" :severity="UtilGroupBuy.orderSeverity(data.status)" />
                            <Tag v-if="data.openRequest" :value="t(`groupBuy.openRequest.${data.openRequest.type}`)" severity="warn" />
                            <span v-if="data.supplyChoicePending" class="text-[11px] text-orange-600 dark:text-orange-400">{{ t('groupBuy.supplyChoiceShort') }}</span>
                            <span v-if="data.delayCancelable" class="text-[11px] text-muted-color">{{ t('groupBuy.delayCancelableShort') }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuy.col.shipment')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div v-if="data.trackingNo" class="text-[12px]">{{ data.carrierCode }} <code class="text-[11px]">{{ data.trackingNo }}</code></div>
                        <div v-if="data.shippedAt" class="text-[11px] text-muted-color">{{ t('groupBuy.shippedAt', { at: time.shortDate(data.shippedAt) }) }}</div>
                        <span v-if="!data.trackingNo && !data.shippedAt" class="text-[11px] text-muted-color">—</span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <GroupBuyOrderDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :revealed="revealed"
            :reveal-busy="revealBusy"
            :memo-busy="memoBusy"
            :busy="working !== null"
            :can-manage="canManage"
            :can-refund="can('commerce.refund.approve')"
            :can-reveal="can('commerce.address.reveal')"
            @retry="loadDetail"
            @reveal="openReveal"
            @save-memo="saveMemo"
            @prepare="drawerDetail && runBulk('prepare', [drawerDetail.id])"
            @ship="drawerDetail && openShip([drawerDetail])"
            @deliver="drawerDetail && runBulk('deliver', [drawerDetail.id])"
            @fix-tracking="openFix"
            @refund="openRefund"
        />

        <GroupBuyShipDialog v-model:visible="shipOpen" :orders="shipTargets" :carriers="carriers" :busy="working === 'ship'" :error="shipError" @submit="submitShip" />
        <GroupBuyBulkResultDialog v-model:visible="resultOpen" :title="resultTitle" :result="result" />
        <GroupBuyRefundDialog v-model:visible="refundOpen" :order="drawerDetail" :preview="refundPreview" :previewing="refundPreviewing" :preview-error="refundPreviewError" :busy="working === 'refund'" :error="refundError" :field-errors="refundFields" :can-large="can('commerce.refund.large')" @preview="previewRefund" @submit="submitRefund" />

        <!-- 배송지 열람 -->
        <PaymentReasonDialog v-model:visible="revealOpen" :title="t('groupBuy.revealTitle')" :description="t('groupBuy.revealBody')" :placeholder="t('groupBuy.revealPlaceholder')" :confirm-label="t('groupBuy.revealSubmit')" :busy="revealBusy" :error="revealError" :field-error="revealFieldError" @submit="submitReveal">
            <p v-if="drawerDetail" class="m-0 text-sm"><code class="text-xs">{{ drawerDetail.orderNo }}</code> · {{ drawerDetail.member.nickname }}</p>
        </PaymentReasonDialog>

        <!-- 송장 정정 -->
        <PaymentReasonDialog v-model:visible="fixOpen" :title="t('groupBuy.fixTitle')" :description="t('groupBuy.fixBody')" :confirm-label="t('groupBuy.fixSubmit')" :busy="working === 'fix'" :error="fixError" :field-error="fixReasonError" @submit="submitFix">
            <div class="grid gap-3 sm:grid-cols-2">
                <LabeledField :label="t('groupBuy.carrier')" :error="fixCarrierError">
                    <template #default="{ id, describedBy, invalid }">
                        <Select :input-id="id" v-model="fixCarrier" :options="carriers" option-label="name" option-value="code" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
                <LabeledField :label="t('groupBuy.trackingNo')" :error="fixTrackingError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="fixTracking" maxlength="40" autocomplete="off" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </div>
        </PaymentReasonDialog>

        <!-- 배송 명단 내보내기 -->
        <PaymentReasonDialog v-model:visible="exportOpen" :title="t('groupBuy.exportTitle')" :description="t('groupBuy.exportBody')" :placeholder="t('groupBuy.exportPlaceholder')" :confirm-label="t('groupBuy.exportSubmit')" :busy="working === 'export'" :error="exportError" :field-error="exportFieldError" @submit="submitExport">
            <div class="flex flex-col gap-1 text-[13px]">
                <span class="font-medium">{{ selectedGroup?.label ?? '—' }}</span>
                <span class="text-xs text-muted-color">{{ t('groupBuy.exportScope') }}</span>
                <span class="text-xs text-muted-color">{{ t('groupBuy.exportCsvNote') }}</span>
            </div>
        </PaymentReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { GroupBuyShipTarget } from '~/components/groupbuy/GroupBuyShipDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyFulfillment, GroupBuyStatus } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import type {
    GroupBuyAdminRefundRequest,
    GroupBuyBulkResult,
    GroupBuyCarrierChoice,
    GroupBuyOrderAlert,
    GroupBuyOrderDetail,
    GroupBuyOrderQuery,
    GroupBuyOrderRow,
    GroupBuyOrderStatus,
    GroupBuyRefundPreview,
    GroupBuyRevealedAddress,
    GroupBuyShipRow,
} from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import { GROUP_BUY_ORDER_STATUSES } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilGroupBuy from '~/utils/util-groupbuy';
import UtilPayment from '~/utils/util-payment';

interface OrderFilter {
    groupBuyId: string | null;
    from: string;
    to: string;
    q: string;
}
type AppliedQuery = Omit<GroupBuyOrderQuery, 'page' | 'size' | 'status'>;
type Work = 'prepare' | 'ship' | 'deliver' | 'ordered' | 'fix' | 'refund' | 'export';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('groupBuy.ordersTitle') });

const api = useGroupBuyOrderApi();
const gbApi = useGroupBuyAdminApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const values = useDisplayValues();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const canManage = computed(() => can('commerce.order.manage'));
const canExport = computed(() => can('commerce.export') && can('commerce.address.reveal'));
const ids = { gb: useId(), q: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));

// ── 공구 거르기 고를 값 ──
const groupChoices = ref<{ value: string; label: string; status: GroupBuyStatus; fulfillment: GroupBuyFulfillment }[]>([]);
async function loadGroups(): Promise<void> {
    try {
        const found = await gbApi.list({ page: 0, size: 100 });
        groupChoices.value = found.items.filter((g) => g.status !== 'DRAFT').map((g) => ({ value: g.id, label: `${g.title}${g.roundNo > 1 ? ` (${t('groupBuy.roundTag', { n: g.roundNo })})` : ''}`, status: g.status, fulfillment: g.fulfillment }));
    } catch {
        groupChoices.value = []; // 공구 조회 권한이 없으면 거르기 칸 없이 보인다(403 은 오류가 아니다)
    }
}

const carriers = ref<GroupBuyCarrierChoice[]>([]);
async function loadCarriers(): Promise<void> {
    try {
        carriers.value = await api.carriers();
    } catch {
        carriers.value = [];
    }
}

// ── 목록 ──
const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
const filter = reactive<OrderFilter>({ groupBuyId: queryString('groupBuyId') || null, from: '', to: '', q: '' });
const alert = ref<GroupBuyOrderAlert | ''>((['SHIP_OVERDUE', 'DELIVERY_STALE'] as string[]).includes(queryString('alert')) ? (queryString('alert') as GroupBuyOrderAlert) : '');
const status = ref<string>((GROUP_BUY_ORDER_STATUSES as readonly string[]).includes(queryString('status')) ? queryString('status') : '');
const applied = ref<AppliedQuery>({});
const hasApplied = computed(() => Object.values(applied.value).some(Boolean) || !!status.value);
const selectedGroup = computed(() => groupChoices.value.find((g) => g.value === applied.value.groupBuyId) ?? null);

const rows = ref<GroupBuyOrderRow[]>([]);
const counts = ref<Record<GroupBuyOrderStatus, number> | null>(null);
const selection = ref<GroupBuyOrderRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const searchErrors = ref<Record<string, string>>({});

const tabs = computed(() => {
    const c = counts.value;
    const all = c ? GROUP_BUY_ORDER_STATUSES.reduce((sum, s) => sum + c[s], 0) : '—';
    return [{ value: '', label: t('common.all'), count: all }, ...GROUP_BUY_ORDER_STATUSES.map((s) => ({ value: s, label: t(`groupBuy.orderStatus.${s}`), count: c ? c[s] : '—' }))];
});

function errorOf(...keys: string[]): string {
    return keys
        .map((k) => searchErrors.value[k])
        .filter(Boolean)
        .join(' ');
}

function toQuery(): AppliedQuery {
    const out: AppliedQuery = { ...UtilPayment.range(filter.from, filter.to, values.value.zoneId) };
    if (filter.groupBuyId) out.groupBuyId = filter.groupBuyId;
    if (filter.q.trim()) out.q = filter.q.trim();
    if (alert.value) out.alert = alert.value;
    return out;
}

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    searchErrors.value = {};
    try {
        const found = await api.list({ ...applied.value, status: (status.value || undefined) as GroupBuyOrderStatus | undefined, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        counts.value = found.counts;
        selection.value = selection.value.filter((s) => found.items.some((r) => r.id === s.id));
    } catch (e) {
        if (mine !== ticket) return;
        const error = toApiError(e);
        rows.value = [];
        total.value = 0;
        if (error.status === 400 && error.fields && Object.keys(error.fields).length) searchErrors.value = error.fields;
        else loadError.value = error;
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function syncQuery(): void {
    void router.replace({ query: { ...route.query, status: status.value || undefined, groupBuyId: applied.value.groupBuyId || undefined, alert: alert.value || undefined } });
}

function search(): void {
    applied.value = toQuery();
    first.value = 0;
    selection.value = [];
    syncQuery();
    void reload();
}

function resetAll(): void {
    Object.assign(filter, { groupBuyId: null, from: '', to: '', q: '' });
    alert.value = '';
    status.value = '';
    search();
}

function clearAlert(): void {
    alert.value = '';
    search();
}

function selectStatus(value: string): void {
    status.value = value;
    first.value = 0;
    selection.value = [];
    syncQuery();
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
const drawerSummary = ref<GroupBuyOrderRow | null>(null);
const drawerDetail = ref<GroupBuyOrderDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);
const revealed = ref<GroupBuyRevealedAddress | null>(null);
let revealTimer: ReturnType<typeof setTimeout> | null = null;

function clearRevealed(): void {
    if (revealTimer) clearTimeout(revealTimer);
    revealTimer = null;
    revealed.value = null;
}
onBeforeUnmount(clearRevealed);

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

function show(id: string, row: GroupBuyOrderRow | null): void {
    if (drawerId.value !== id) {
        clearRevealed();
        drawerDetail.value = null;
    }
    drawerId.value = id;
    drawerSummary.value = row;
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input, .p-checkbox')) return;
    const row = event.data as GroupBuyOrderRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, order: row.id } });
}

watch(
    () => route.query.order,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open) return;
    clearRevealed(); // 서랍을 닫으면 열람한 배송지를 메모리에서 지운다
    if (!route.query.order) return;
    const { order: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

/** 동작 뒤 — 목록과 열린 서랍을 다시 읽는다 */
function afterChange(): void {
    void reload();
    if (drawerOpen.value) void loadDetail();
}

// ── 일괄 ──
const working = ref<Work | null>(null);
const bulkError = ref('');
const result = ref<GroupBuyBulkResult | null>(null);
const resultOpen = ref(false);
const resultTitle = ref('');

async function runBulk(kind: 'prepare' | 'deliver', orderIds: string[]): Promise<void> {
    if (!orderIds.length || orderIds.length > UtilGroupBuy.BULK_MAX) return;
    working.value = kind;
    bulkError.value = '';
    try {
        result.value = kind === 'prepare' ? await api.prepare(orderIds) : await api.deliver(orderIds);
        resultTitle.value = t(kind === 'prepare' ? 'groupBuy.resultPrepare' : 'groupBuy.resultDeliver');
        resultOpen.value = true;
        selection.value = [];
        afterChange();
    } catch (e) {
        bulkError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

function bulkPrepare(): void {
    void runBulk(
        'prepare',
        selection.value.map((r) => r.id),
    );
}
function bulkDeliver(): void {
    void runBulk(
        'deliver',
        selection.value.map((r) => r.id),
    );
}

const shipOpen = ref(false);
const shipTargets = ref<GroupBuyShipTarget[]>([]);
const shipError = ref('');
function openShip(list: GroupBuyOrderRow[]): void {
    shipTargets.value = list.map((r) => ({ orderId: r.id, orderNo: r.orderNo, member: r.member.nickname, items: r.itemSummary, carrierCode: r.carrierCode, trackingNo: r.trackingNo }));
    shipError.value = '';
    shipOpen.value = true;
}
async function submitShip(shipRows: GroupBuyShipRow[]): Promise<void> {
    working.value = 'ship';
    shipError.value = '';
    try {
        result.value = await api.ship(shipRows);
        resultTitle.value = t('groupBuy.resultShip');
        shipOpen.value = false;
        resultOpen.value = true;
        selection.value = [];
        afterChange();
    } catch (e) {
        shipError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

async function markOrdered(): Promise<void> {
    const g = selectedGroup.value;
    if (!g) return;
    working.value = 'ordered';
    bulkError.value = '';
    try {
        await gbApi.markOrdered(g.value);
        toast.success(t('groupBuy.markedOrdered'));
        await loadGroups();
        afterChange();
    } catch (e) {
        bulkError.value = toApiError(e).message;
    } finally {
        working.value = null;
    }
}

// ── 배송지 열람 ──
const revealOpen = ref(false);
const revealBusy = ref(false);
const revealError = ref('');
const revealFieldError = ref('');
function openReveal(): void {
    revealError.value = '';
    revealFieldError.value = '';
    revealOpen.value = true;
}
async function submitReveal(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    revealBusy.value = true;
    revealError.value = '';
    revealFieldError.value = '';
    try {
        const found = await api.revealAddress(d.id, reason);
        clearRevealed();
        revealed.value = found;
        revealTimer = setTimeout(clearRevealed, Math.max(0, Date.parse(found.expiresAt) - Date.now()));
        revealOpen.value = false;
        void loadDetail(); // 열람 기록을 다시 읽는다
    } catch (e) {
        const failure = toApiError(e);
        revealFieldError.value = failure.field('reason') ?? '';
        revealError.value = revealFieldError.value ? '' : failure.message;
    } finally {
        revealBusy.value = false;
    }
}

// ── 운영 메모 ──
const memoBusy = ref(false);
async function saveMemo(memo: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    memoBusy.value = true;
    try {
        drawerDetail.value = await api.saveMemo(d.id, memo);
        toast.success(t('groupBuy.memoSaved'));
    } catch (e) {
        const failure = toApiError(e);
        toast.error(failure.field('memo') ?? failure.message, 8000);
    } finally {
        memoBusy.value = false;
    }
}

// ── 송장 정정 ──
const fixOpen = ref(false);
const fixCarrier = ref('');
const fixTracking = ref('');
const fixError = ref('');
const fixReasonError = ref('');
const fixCarrierError = ref('');
const fixTrackingError = ref('');
function openFix(): void {
    const d = drawerDetail.value;
    fixCarrier.value = d?.carrierCode ?? '';
    fixTracking.value = d?.trackingNo ?? '';
    fixError.value = '';
    fixReasonError.value = '';
    fixCarrierError.value = '';
    fixTrackingError.value = '';
    fixOpen.value = true;
}
async function submitFix(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    fixCarrierError.value = fixCarrier.value ? '' : t('groupBuy.carrierRequired');
    fixTrackingError.value = UtilGroupBuy.trackingOk(fixTracking.value) ? '' : t('groupBuy.trackingInvalid');
    if (fixCarrierError.value || fixTrackingError.value) return;
    working.value = 'fix';
    fixError.value = '';
    fixReasonError.value = '';
    try {
        drawerDetail.value = await api.fixTracking(d.id, { carrierCode: fixCarrier.value, trackingNo: UtilGroupBuy.trackingText(fixTracking.value), reason });
        toast.success(t('groupBuy.fixDone'));
        fixOpen.value = false;
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        fixReasonError.value = failure.field('reason') ?? '';
        fixCarrierError.value = failure.field('carrierCode') ?? '';
        fixTrackingError.value = failure.field('trackingNo') ?? '';
        fixError.value = fixReasonError.value || fixCarrierError.value || fixTrackingError.value ? '' : failure.message;
    } finally {
        working.value = null;
    }
}

// ── 운영 환불 ──
const refundOpen = ref(false);
const refundPreview = ref<GroupBuyRefundPreview | null>(null);
const refundPreviewing = ref(false);
const refundPreviewError = ref('');
const refundError = ref('');
const refundFields = ref<Record<string, string>>({});
function openRefund(): void {
    refundPreview.value = null;
    refundPreviewError.value = '';
    refundError.value = '';
    refundFields.value = {};
    refundOpen.value = true;
}
async function previewRefund(body: GroupBuyAdminRefundRequest): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    refundPreviewing.value = true;
    refundPreviewError.value = '';
    refundFields.value = {};
    try {
        refundPreview.value = await api.refundPreview(d.id, body);
    } catch (e) {
        const failure = toApiError(e);
        refundPreview.value = null;
        if (failure.code === 'VALIDATION' && failure.fields) refundFields.value = failure.fields;
        else refundPreviewError.value = failure.message;
    } finally {
        refundPreviewing.value = false;
    }
}
async function submitRefund(body: GroupBuyAdminRefundRequest): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    working.value = 'refund';
    refundError.value = '';
    refundFields.value = {};
    try {
        const done = await api.refund(d.id, body);
        drawerDetail.value = done.order;
        toast.success(done.state === 'AWAITING_APPROVAL' ? t('groupBuy.refundAwaiting') : t('groupBuy.refundQueued'));
        refundOpen.value = false;
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields) refundFields.value = failure.fields;
        else refundError.value = failure.message;
    } finally {
        working.value = null;
    }
}

// ── 배송 명단 내보내기 ──
const exportOpen = ref(false);
const exportError = ref('');
const exportFieldError = ref('');
function openExport(): void {
    exportError.value = '';
    exportFieldError.value = '';
    exportOpen.value = true;
}
async function submitExport(reason: string): Promise<void> {
    const groupBuyId = applied.value.groupBuyId;
    if (!groupBuyId) return;
    working.value = 'export';
    exportError.value = '';
    exportFieldError.value = '';
    try {
        const file = await api.exportShipping({ groupBuyId, reason });
        const link = document.createElement('a');
        link.href = file.downloadUrl;
        link.download = file.fileName;
        link.click();
        toast.success(t('groupBuy.exportDone', { n: file.rowCount }));
        exportOpen.value = false;
    } catch (e) {
        const failure = toApiError(e);
        exportFieldError.value = failure.field('reason') ?? '';
        exportError.value = exportFieldError.value ? '' : failure.message;
    } finally {
        working.value = null;
    }
}

onMounted(() => {
    void loadGroups();
    void loadCarriers();
    applied.value = toQuery();
    void reload();
});
</script>
