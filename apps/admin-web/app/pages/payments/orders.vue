<template>
    <!--
        결제 › 결제 주문(`/payments/orders`) — 현금 결제 시도(`payment_orders`) 한 건 = 한 줄. 충전·공구·시험 주문이 함께 보인다(계약 commerce-payment 2a-1).
        ⚠ 목업 단계(`usePaymentAdminApi` — 서버는 2a-3). 만든 시각 내림차순, 서버 쪽 나누기(20개 기본, 상한 100).
        권한: 조회 `commerce.payment.read`(메뉴), 서랍의 「포트원에서 다시 확인」 `commerce.reconcile.manage`(없으면 단추가 잠기고 이유를 보인다).
        조건(기간 · 상태 · 이상 · 주문 종류 · 결제 번호/회원 번호)은 「검색」(또는 Enter)을 눌렀을 때만 서버로 간다. 기간 시작 값은 최근 1주일.
        한 줄: 만든 시각 · 결제 번호 · 회원(닉네임·번호) · 주문(종류·이름) · 금액(주문 / 결제 · 취소) · 상태(+ 이상 · 취소 진행) · 결제 시각. 줄을 누르면 상세 서랍(`?payment=ID` 로도 연다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('payment.ordersTitle')" :subtitle="t('payment.ordersSubtitle')" />

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('payment.filter.searchLabel')" @submit.prevent="search">
            <FilterRow :label="t('payment.filter.period')" :error="errorOf('from', 'to')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('payment.filter.from')" :to-label="t('payment.filter.to')" />
            </FilterRow>
            <FilterRow :label="t('payment.filter.status')" :error="errorOf('status')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.status" :options="statusOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('payment.filter.anomaly')" :error="errorOf('anomaly')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.anomaly" :options="anomalyOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('payment.filter.orderType')" :error="errorOf('orderType')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.orderType" :options="typeOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('payment.filter.q')" :label-for="ids.q" :error="errorOf('q')">
                <InputText :id="ids.q" v-model="filter.q" type="search" maxlength="100" autocomplete="off" :placeholder="t('payment.filter.qPlaceholder')" class="w-full max-w-sm" />
            </FilterRow>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('payment.filter.reset')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
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
                :size="prefs.density === 'compact' ? 'small' : undefined"
                scrollable
                :row-class="() => 'cursor-pointer'"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                class="min-w-0"
                @page="onPage"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('payment.forbidden') : t('payment.ordersLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="hasSearchErrors" icon="pi pi-exclamation-circle" :title="t('payment.filterRejected')" />
                    <EmptyState v-else-if="hasApplied" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Button type="button" :label="t('payment.filter.clear')" size="small" severity="secondary" variant="outlined" @click="clearAll" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-credit-card" :title="t('payment.ordersEmpty')" :description="t('payment.ordersEmptyHint')" />
                </template>

                <Column :header="t('payment.col.createdAt')" style="min-width: 9.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.full(data.createdAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('payment.col.paymentId')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <code class="block max-w-[13rem] truncate text-[12px]" :title="data.paymentId">{{ data.paymentId }}</code>
                    </template>
                </Column>
                <Column :header="t('payment.col.member')" style="min-width: 8rem">
                    <template #body="{ data }"><PaymentMemberLabel :member="data.member" /></template>
                </Column>
                <Column :header="t('payment.col.order')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <div class="min-w-0">
                            <div class="truncate text-[13px]">{{ data.orderName }}</div>
                            <div class="truncate text-[11px] text-muted-color">{{ t(`payment.orderType.${data.orderType}`) }}</div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('payment.col.amount')" style="min-width: 8.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right tabular-nums">
                            <div class="text-[13px]">{{ t('payment.won', { amount: UtilPayment.won(data.amount, locale) }) }}</div>
                            <div v-if="data.paidAmount !== null && data.paidAmount !== data.amount" class="text-[11px] text-red-600 dark:text-red-400">{{ t('payment.paidLine', { amount: UtilPayment.won(data.paidAmount, locale) }) }}</div>
                            <div v-if="data.canceledAmount > 0" class="text-[11px] text-orange-600 dark:text-orange-400">{{ t('payment.canceledLine', { amount: UtilPayment.won(data.canceledAmount, locale) }) }}</div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('payment.col.status')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag :value="t(`payment.status.${data.status}`)" :severity="UtilPayment.statusSeverity(data.status)" />
                            <Tag v-if="data.anomaly" :value="t(`payment.anomaly.${data.anomaly}`)" severity="danger" icon="pi pi-exclamation-triangle" />
                            <span v-if="data.cancelPending" class="text-[11px] text-orange-600 dark:text-orange-400"><i class="pi pi-hourglass text-[10px]" aria-hidden="true" /> {{ t('payment.cancelPending') }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('payment.col.paidAt')" style="min-width: 8.5rem">
                    <template #body="{ data }">
                        <div v-if="data.paidAt" class="min-w-0">
                            <div class="text-[12px] tabular-nums">{{ time.shortDateTime(data.paidAt) }}</div>
                            <div v-if="data.payMethod" class="truncate text-[11px] text-muted-color">{{ data.payMethod }}</div>
                        </div>
                        <span v-else class="text-[11px] text-muted-color">{{ data.status === 'READY' ? t('payment.expiresLine', { at: time.shortDateTime(data.expiresAt) }) : '—' }}</span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <PaymentOrderDrawer v-model:visible="drawerOpen" :summary="drawerSummary" :detail="drawerDetail" :loading="drawerLoading" :error="drawerError" :busy="recheckBusy" :can-manage="canManage" @retry="drawerReload" @recheck="startRecheck" />
        <PaymentReasonDialog
            v-model:visible="recheckOpen"
            :title="t('payment.recheck.title')"
            :description="t('payment.recheck.description')"
            :placeholder="t('payment.recheck.placeholder')"
            :confirm-label="t('payment.recheck.submit')"
            :busy="recheckBusy"
            :error="recheckError"
            :field-error="recheckFieldError"
            @submit="submitRecheck"
        >
            <p v-if="drawerDetail" class="m-0 text-sm">
                <code class="text-xs">{{ drawerDetail.paymentId }}</code> · {{ t(`payment.status.${drawerDetail.status}`) }}
            </p>
        </PaymentReasonDialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { PaymentAnomaly, PaymentOrderQuery, PaymentOrderRow, PaymentOrderType, PaymentStatus } from '~/composables/payment/api/usePaymentAdminApi.types';
import { PAYMENT_ANOMALIES, PAYMENT_ORDER_TYPES, PAYMENT_STATUSES } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilPayment from '~/utils/util-payment';

interface OrderFilter {
    from: string;
    to: string;
    status: string;
    /** '' 전체 · ANY 이상 있음 · 종류 하나 */
    anomaly: string;
    orderType: string;
    q: string;
}
type AppliedQuery = Omit<PaymentOrderQuery, 'page' | 'size'>;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('payment.ordersTitle') });

const api = usePaymentAdminApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const route = useRoute();
const router = useRouter();

const canManage = computed(() => can('commerce.reconcile.manage'));

const ids = { q: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const statusOptions = computed(() => [{ value: '', label: t('common.all') }, ...PAYMENT_STATUSES.map((s) => ({ value: s, label: t(`payment.status.${s}`) }))]);
const anomalyOptions = computed(() => [{ value: '', label: t('common.all') }, { value: 'ANY', label: t('payment.filter.anomalyAny') }, ...PAYMENT_ANOMALIES.map((a) => ({ value: a, label: t(`payment.anomaly.${a}`) }))]);
const typeOptions = computed(() => [{ value: '', label: t('common.all') }, ...PAYMENT_ORDER_TYPES.map((o) => ({ value: o, label: t(`payment.orderType.${o}`) }))]);

function defaultFilter(): OrderFilter {
    const p = time.value.parts(new Date().toISOString());
    const today = p ? `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}` : '';
    const range = UtilDateRange.fill('WEEK', today);
    return { from: range?.from ?? '', to: range?.to ?? '', status: '', anomaly: '', orderType: '', q: '' };
}

const filter = reactive<OrderFilter>(defaultFilter());
const rows = ref<PaymentOrderRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const searchErrors = ref<Record<string, string>>({});
const hasSearchErrors = computed(() => Object.keys(searchErrors.value).length > 0);
const applied = ref<AppliedQuery>({});
const hasApplied = computed(() => Object.values(applied.value).some(Boolean));

function errorOf(...keys: string[]): string {
    return keys
        .map((key) => searchErrors.value[key])
        .filter(Boolean)
        .join(' ');
}

function toQuery(f: OrderFilter): AppliedQuery {
    const out: AppliedQuery = { ...UtilPayment.range(f.from, f.to, values.value.zoneId) };
    if (f.status) out.status = f.status as PaymentStatus;
    if (f.anomaly) out.anomaly = f.anomaly as PaymentAnomaly | 'ANY';
    if (f.orderType) out.orderType = f.orderType as PaymentOrderType;
    if (f.q.trim()) out.q = f.q.trim();
    return out;
}

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    searchErrors.value = {};
    try {
        const found = await api.orders({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        const error = toApiError(e);
        rows.value = [];
        total.value = 0;
        if (error.status === 400 && error.fields && Object.keys(error.fields).length > 0) searchErrors.value = error.fields;
        else loadError.value = error;
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function search(): void {
    applied.value = toQuery(filter);
    first.value = 0;
    void reload();
}

function resetAll(): void {
    Object.assign(filter, defaultFilter());
    search();
}

function clearAll(): void {
    Object.assign(filter, { from: '', to: '', status: '', anomaly: '', orderType: '', q: '' });
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

// ── 상세 서랍 ─
const {
    open: drawerOpen,
    summary: drawerSummary,
    detail: drawerDetail,
    loading: drawerLoading,
    error: drawerError,
    show,
    reload: drawerReload,
    recheckOpen,
    recheckBusy,
    recheckError,
    recheckFieldError,
    startRecheck,
    submitRecheck,
} = usePaymentOrderDrawer(() => void reload());

function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    const row = event.data as PaymentOrderRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, payment: row.id } });
}

// 주소의 `?payment=ID`(새로고침·다른 화면의 링크)를 따라 서랍을 연다
watch(
    () => route.query.payment,
    (id) => {
        if (typeof id === 'string' && id) show(id, rows.value.find((r) => r.id === id) ?? null);
        else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (value) => {
    if (value || !route.query.payment) return;
    const { payment: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

onMounted(search);
</script>
