<template>
    <!--
        결제 › 웹훅 수신 내역(`/payments/webhooks`) — 포트원이 보낸 웹훅을 받은 대로 한 줄씩(`payment_webhook_logs`, 90일 보관, **본문은 저장하지 않는다**). 계약 「웹훅 처리 표」.
        서버 API: `usePaymentAdminApi`(2a-3). 받은 시각 내림차순, 서버 쪽 나누기.
        결과: 처리함(PROCESSED — 같은 webhook-id 다시 받음 포함) · 무시함(IGNORED — 우리 결제가 아님·모르는 이벤트) · 거절함(REJECTED — 서명·시각·머리글 문제, 400) · 다시 받기(RETRY — 포트원 장애·아직 결제 전, 503 이라 포트원이 다시 보낸다).
        권한: 조회 `commerce.payment.read`. 우리 결제 주문이 있는 줄은 결제 번호를 눌러 상세 서랍을 연다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('payment.webhooksTitle')" :subtitle="t('payment.webhooksSubtitle')" />

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('payment.filter.searchLabel')" @submit.prevent="search">
            <FilterRow :label="t('payment.filter.period')" :error="errorOf('from', 'to')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('payment.filter.from')" :to-label="t('payment.filter.to')" />
            </FilterRow>
            <FilterRow :label="t('payment.filter.result')" :error="errorOf('result')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.result" :options="resultOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('payment.filter.webhookQ')" :label-for="ids.q" :error="errorOf('q')">
                <InputText :id="ids.q" v-model="filter.q" type="search" maxlength="100" autocomplete="off" :placeholder="t('payment.filter.webhookQPlaceholder')" class="w-full max-w-sm" />
            </FilterRow>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('payment.filter.reset')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
        </form>

        <ul class="m-0 flex list-none flex-wrap gap-x-4 gap-y-1 px-1 text-xs text-muted-color">
            <li v-for="r in PAYMENT_WEBHOOK_RESULTS" :key="r" class="flex items-center gap-1.5">
                <Tag :value="t(`payment.webhookResult.${r}`)" :severity="UtilPayment.webhookSeverity(r)" />
                {{ t(`payment.webhookResultHelp.${r}`) }}
            </li>
        </ul>

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
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                class="min-w-0"
                @page="onPage"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('payment.forbidden') : t('payment.webhooksLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="hasApplied" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Button type="button" :label="t('payment.filter.clear')" size="small" severity="secondary" variant="outlined" @click="clearAll" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-inbox" :title="t('payment.webhooksEmpty')" />
                </template>

                <Column :header="t('payment.col.receivedAt')" style="min-width: 9.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.full(data.receivedAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('payment.col.eventType')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <code class="text-[12px]" :class="data.eventType ? '' : 'text-muted-color'">{{ data.eventType ?? t('payment.unknownEvent') }}</code>
                    </template>
                </Column>
                <Column :header="t('payment.col.paymentId')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <button v-if="data.paymentOrderId" type="button" class="block max-w-[13rem] cursor-pointer truncate text-left font-mono text-[12px] text-primary hover:underline" :title="t('payment.openDetail')" @click="show(data.paymentOrderId)">
                            {{ data.paymentId }}
                        </button>
                        <code v-else class="block max-w-[13rem] truncate text-[12px] text-muted-color" :title="data.paymentId ?? undefined">{{ data.paymentId ?? '—' }}</code>
                    </template>
                </Column>
                <Column :header="t('payment.col.result')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <Tag :value="t(`payment.webhookResult.${data.result}`)" :severity="UtilPayment.webhookSeverity(data.result)" />
                    </template>
                </Column>
                <Column :header="t('payment.col.http')" style="min-width: 5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ data.httpStatus }}</span>
                    </template>
                </Column>
                <Column :header="t('payment.col.errorCode')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <code v-if="data.errorCode" class="text-[12px]">{{ data.errorCode }}</code>
                        <span v-else class="text-[12px] text-muted-color">—</span>
                    </template>
                </Column>
                <Column :header="t('payment.col.webhookId')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <code class="block max-w-[11rem] truncate text-[11px] text-muted-color" :title="data.webhookId ?? undefined">{{ data.webhookId ?? '—' }}</code>
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
        />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PaymentWebhookQuery, PaymentWebhookResult, PaymentWebhookRow } from '~/composables/payment/api/usePaymentAdminApi.types';
import { PAYMENT_WEBHOOK_RESULTS } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilPayment from '~/utils/util-payment';

interface WebhookFilter {
    from: string;
    to: string;
    result: string;
    q: string;
}
type AppliedQuery = Omit<PaymentWebhookQuery, 'page' | 'size'>;

const { t } = useI18n();
useSeoMeta({ title: () => t('payment.webhooksTitle') });

const api = usePaymentAdminApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();

const canManage = computed(() => can('commerce.reconcile.manage'));
const ids = { q: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const resultOptions = computed(() => [{ value: '', label: t('common.all') }, ...PAYMENT_WEBHOOK_RESULTS.map((r) => ({ value: r, label: t(`payment.webhookResult.${r}`) }))]);

function defaultFilter(): WebhookFilter {
    const p = time.value.parts(new Date().toISOString());
    const today = p ? `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}` : '';
    const range = UtilDateRange.fill('WEEK', today);
    return { from: range?.from ?? '', to: range?.to ?? '', result: '', q: '' };
}

const filter = reactive<WebhookFilter>(defaultFilter());
const rows = ref<PaymentWebhookRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const searchErrors = ref<Record<string, string>>({});
const applied = ref<AppliedQuery>({});
const hasApplied = computed(() => Object.values(applied.value).some(Boolean));

function errorOf(...keys: string[]): string {
    return keys
        .map((key) => searchErrors.value[key])
        .filter(Boolean)
        .join(' ');
}

function toQuery(f: WebhookFilter): AppliedQuery {
    const out: AppliedQuery = { ...UtilPayment.range(f.from, f.to, values.value.zoneId) };
    if (f.result) out.result = f.result as PaymentWebhookResult;
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
        const found = await api.webhooks({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
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
    Object.assign(filter, { from: '', to: '', result: '', q: '' });
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

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
} = usePaymentOrderDrawer();

onMounted(search);
</script>
