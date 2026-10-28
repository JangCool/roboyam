<template>
    <!--
        결제 › 결제 이상(`/payments/anomalies`) — 받지 않은 결제(금액 불일치 · 도메인 거절 · 늦은 결제 — 모두 전액 자동 취소)와 5번 실패해 멈춘 취소(GAVE_UP)를 한곳에서 본다(commerce.md 13-5, 계약 결정 12·P17).
        서버 API: `usePaymentAdminApi`(2a-3). 기본은 처리 안 된 것만(「처리된 것도 보기」로 넓힌다), 발견 시각 내림차순.
        한 줄: 발견 시각 · 종류 · 결제(번호 → 상세 서랍 · 주문 이름 · 회원) · 금액(주문 / 결제) · 자동 취소(금액 · 상태 · 시도 횟수 · 마지막 오류) · 조치.
        조치는 취소가 GAVE_UP 인 줄만, `commerce.reconcile.manage`: 「다시 시도」(대기열로 되돌림 — 1분 워커가 다시 보낸다) · 「수동 처리 완료」(포트원 콘솔 등에서 직접 끝냈음).
        둘 다 사유 5~200자 → 저장 → 토스트 → 다시 읽기. 서버가 감사(돈 관련 — `PAYMENT_CANCEL_RETRY`·`PAYMENT_CANCEL_MANUAL`)를 남긴다. `409 PAYMENT_CANCEL_NOT_GAVE_UP`(다른 운영자가 먼저)은 닫고 알린 뒤 다시 읽는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('payment.anomaliesTitle')" :subtitle="t('payment.anomaliesSubtitle')">
            <template v-if="openCount > 0" #actions>
                <Tag :value="t('payment.openCount', { count: openCount })" severity="danger" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('payment.filter.searchLabel')" @submit.prevent="search">
            <FilterRow :label="t('payment.filter.kind')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.kind" :options="kindOptions" :labelledby="labelId" />
                </template>
            </FilterRow>
            <FilterRow :label="t('payment.filter.period')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('payment.filter.from')" :to-label="t('payment.filter.to')" />
            </FilterRow>
            <FilterRow :label="t('payment.filter.resolved')" :label-for="ids.resolved">
                <div class="flex min-h-8 items-center gap-2">
                    <Checkbox v-model="filter.includeResolved" :input-id="ids.resolved" binary />
                    <span class="text-[13px]">{{ t('payment.filter.includeResolved') }}</span>
                </div>
            </FilterRow>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('payment.filter.reset')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
        </form>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="rows"
                data-key="key"
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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('payment.forbidden') : t('payment.anomaliesLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else icon="pi pi-check-circle" :title="t('payment.anomaliesEmpty')" :description="filter.includeResolved ? '' : t('payment.anomaliesEmptyHint')" />
                </template>

                <Column :header="t('payment.col.detectedAt')" style="min-width: 9.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.full(data.detectedAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('payment.col.kind')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag :value="t(`payment.anomalyKind.${data.kind}`)" :severity="data.resolved ? 'secondary' : 'danger'" />
                            <span v-if="data.resolved" class="text-[11px] text-muted-color"><i class="pi pi-check text-[10px]" aria-hidden="true" /> {{ t('payment.resolved') }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('payment.col.payment')" style="min-width: 13rem">
                    <template #body="{ data }">
                        <div class="min-w-0">
                            <button type="button" class="block max-w-[14rem] cursor-pointer truncate text-left font-mono text-[12px] text-primary hover:underline" :title="t('payment.openDetail')" @click="show(data.order.id, data.order)">
                                {{ data.order.paymentId }}
                            </button>
                            <div class="truncate text-[12px]">{{ data.order.orderName }} <span class="text-muted-color">· {{ t(`payment.orderType.${data.order.orderType}`) }}</span></div>
                            <div class="truncate text-[11px] text-muted-color">{{ data.order.member.nickname }}<template v-if="data.order.member.id"> · {{ data.order.member.id }}</template></div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('payment.col.amount')" style="min-width: 8rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <div class="text-right tabular-nums">
                            <div class="text-[13px]">{{ t('payment.won', { amount: UtilPayment.won(data.order.amount, locale) }) }}</div>
                            <div v-if="data.order.paidAmount !== null && data.order.paidAmount !== data.order.amount" class="text-[11px] text-red-600 dark:text-red-400">{{ t('payment.paidLine', { amount: UtilPayment.won(data.order.paidAmount, locale) }) }}</div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('payment.col.cancel')" style="min-width: 15rem">
                    <template #body="{ data }">
                        <div v-if="data.cancel" class="flex min-w-0 flex-col items-start gap-0.5">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <span class="text-[12px] tabular-nums">{{ t('payment.won', { amount: UtilPayment.won(data.cancel.amount, locale) }) }}</span>
                                <span class="text-[12px] text-muted-color">{{ t(`payment.cancelKind.${data.cancel.kind}`) }}</span>
                                <Tag :value="t(`payment.cancelStatus.${data.cancel.status}`)" :severity="UtilPayment.cancelSeverity(data.cancel.status)" />
                            </div>
                            <span class="text-[11px] tabular-nums text-muted-color">{{ t('payment.attempts', { count: data.cancel.attempts }) }}</span>
                            <span v-if="data.cancel.lastError && data.cancel.status === 'GAVE_UP'" class="max-w-[18rem] text-[11px] text-red-600 [overflow-wrap:anywhere] dark:text-red-400">{{ data.cancel.lastError }}</span>
                            <span v-if="data.cancel.resolvedBy" class="max-w-[18rem] truncate text-[11px] text-muted-color" :title="data.cancel.resolveNote ?? undefined">{{ t('payment.resolvedBy', { name: data.cancel.resolvedBy, note: data.cancel.resolveNote ?? '—' }) }}</span>
                        </div>
                        <span v-else class="text-[12px] text-muted-color">—</span>
                    </template>
                </Column>
                <Column :header="t('payment.col.actions')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div v-if="data.cancel?.status === 'GAVE_UP'" class="flex flex-wrap gap-1">
                            <Button type="button" :label="t('payment.action.retry')" size="small" :disabled="!canManage" :title="canManage ? undefined : t('payment.needManage')" @click="openAction('retry', data)" />
                            <Button type="button" :label="t('payment.action.manual')" size="small" severity="secondary" variant="outlined" :disabled="!canManage" :title="canManage ? undefined : t('payment.needManage')" @click="openAction('manual', data)" />
                        </div>
                        <span v-else-if="data.cancel && !data.resolved" class="text-[11px] text-muted-color">{{ t('payment.action.waiting') }}</span>
                    </template>
                </Column>
            </DataTable>
        </div>
        <p v-if="!canManage" class="m-0 px-1 text-xs text-muted-color">{{ t('payment.needManage') }}</p>

        <PaymentReasonDialog
            v-model:visible="actionOpen"
            :title="actionKind === 'retry' ? t('payment.action.retryTitle') : t('payment.action.manualTitle')"
            :description="actionKind === 'retry' ? t('payment.action.retryDescription') : t('payment.action.manualDescription')"
            :placeholder="actionKind === 'retry' ? t('payment.action.retryPlaceholder') : t('payment.action.manualPlaceholder')"
            :confirm-label="actionKind === 'retry' ? t('payment.action.retry') : t('payment.action.manual')"
            :tone="actionKind === 'manual' ? 'danger' : 'primary'"
            :busy="actionBusy"
            :error="actionError"
            :field-error="actionFieldError"
            @submit="submitAction"
        >
            <dl v-if="actionTarget?.cancel" class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                <dt class="text-muted-color">{{ t('payment.col.payment') }}</dt>
                <dd class="m-0 min-w-0"><code class="text-xs [overflow-wrap:anywhere]">{{ actionTarget.order.paymentId }}</code></dd>
                <dt class="text-muted-color">{{ t('payment.col.cancel') }}</dt>
                <dd class="m-0 font-semibold tabular-nums">{{ t('payment.won', { amount: UtilPayment.won(actionTarget.cancel.amount, locale) }) }} · {{ t(`payment.cancelKind.${actionTarget.cancel.kind}`) }}</dd>
                <template v-if="actionTarget.cancel.lastError">
                    <dt class="text-muted-color">{{ t('payment.lastErrorLabel') }}</dt>
                    <dd class="m-0 text-xs [overflow-wrap:anywhere]">{{ actionTarget.cancel.lastError }}</dd>
                </template>
            </dl>
            <div v-if="actionKind === 'manual'" class="mt-3 flex flex-col gap-2" data-testid="payment-manual-method">
                <span :id="manualMethodLabelId" class="text-sm font-medium">{{ t('payment.action.manualMethodLabel') }}</span>
                <SelectButton v-model="manualMethod" :options="manualMethodOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="manualMethodLabelId" :disabled="actionBusy" />
                <p class="m-0 text-xs text-muted-color">{{ t(`payment.action.manualMethodHint.${manualMethod}`) }}</p>
                <div v-if="manualMethod === 'CONSOLE_CANCEL'" class="flex flex-col gap-1">
                    <label :for="pgCancelInputId" class="text-sm">{{ t('payment.action.pgCancelIdLabel') }}</label>
                    <InputText :id="pgCancelInputId" v-model="pgCancelId" size="small" maxlength="100" :disabled="actionBusy" :invalid="!!pgCancelError" data-testid="payment-manual-pg-cancel-id" />
                    <small v-if="pgCancelError" class="text-red-600 dark:text-red-400">{{ pgCancelError }}</small>
                </div>
            </div>
        </PaymentReasonDialog>

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
import type { PaymentAnomalyQuery, PaymentAnomalyRow } from '~/composables/payment/api/usePaymentAdminApi.types';
import { PAYMENT_ANOMALIES } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilPayment from '~/utils/util-payment';

interface AnomalyFilter {
    kind: string;
    from: string;
    to: string;
    includeResolved: boolean;
}
type AppliedQuery = Omit<PaymentAnomalyQuery, 'page' | 'size'>;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('payment.anomaliesTitle') });

const api = usePaymentAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();

const canManage = computed(() => can('commerce.reconcile.manage'));
const ids = { resolved: useId() };
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const kindOptions = computed(() => [{ value: '', label: t('common.all') }, ...[...PAYMENT_ANOMALIES, 'CANCEL_GAVE_UP' as const].map((k) => ({ value: k, label: t(`payment.anomalyKind.${k}`) }))]);

/** 기본은 기간 없이 처리 안 된 것 전부 — 오래 묵은 이상도 놓치지 않게 */
const defaultFilter = (): AnomalyFilter => ({ kind: '', from: '', to: '', includeResolved: false });

const filter = reactive<AnomalyFilter>(defaultFilter());
const rows = ref<PaymentAnomalyRow[]>([]);
const total = ref(0);
const openCount = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const applied = ref<AppliedQuery>({});

function toQuery(f: AnomalyFilter): AppliedQuery {
    const out: AppliedQuery = { ...UtilPayment.range(f.from, f.to, values.value.zoneId) };
    if (f.kind) out.kind = f.kind as PaymentAnomalyRow['kind'];
    if (f.includeResolved) out.includeResolved = true;
    return out;
}

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.anomalies({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        openCount.value = found.openCount;
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
    applied.value = toQuery(filter);
    first.value = 0;
    void reload();
}

function resetAll(): void {
    Object.assign(filter, defaultFilter());
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

// ── 다시 시도 · 수동 처리 완료 ─
const actionOpen = ref(false);
const actionKind = ref<'retry' | 'manual'>('retry');
const actionTarget = ref<PaymentAnomalyRow | null>(null);
const actionBusy = ref(false);
const actionError = ref('');
const actionFieldError = ref('');

// 수동 처리 방법(공구 계약 결정 74) — 표시만 · 콘솔에서 취소함(취소 ID) · 계좌로 돌려줌
type ManualMethod = 'NONE' | 'CONSOLE_CANCEL' | 'BANK_TRANSFER';
const manualMethod = ref<ManualMethod>('NONE');
const pgCancelId = ref('');
const pgCancelError = ref('');
const manualMethodLabelId = useId();
const pgCancelInputId = useId();
const manualMethodOptions = computed(() => (['NONE', 'CONSOLE_CANCEL', 'BANK_TRANSFER'] as const).map((value) => ({ value, label: t(`payment.action.manualMethod.${value}`) })));

function openAction(kind: 'retry' | 'manual', row: PaymentAnomalyRow): void {
    if (!canManage.value || !row.cancel) return;
    manualMethod.value = 'NONE';
    pgCancelId.value = '';
    pgCancelError.value = '';
    actionKind.value = kind;
    actionTarget.value = row;
    actionError.value = '';
    actionFieldError.value = '';
    actionOpen.value = true;
}

async function submitAction(reason: string): Promise<void> {
    const cancel = actionTarget.value?.cancel;
    if (!cancel || actionBusy.value) return;
    actionBusy.value = true;
    actionError.value = '';
    actionFieldError.value = '';
    try {
        // 감사(돈 관련)는 서버가 남긴다
        if (actionKind.value === 'retry') {
            await api.retryCancel(cancel.id, reason);
            toast.success(t('payment.action.retried'));
        } else {
            if (manualMethod.value === 'CONSOLE_CANCEL' && !pgCancelId.value.trim()) {
                pgCancelError.value = t('payment.action.pgCancelIdRequired');
                return;
            }
            await api.manualCancel(cancel.id, reason, manualMethod.value === 'NONE' ? null : manualMethod.value, manualMethod.value === 'CONSOLE_CANCEL' ? pgCancelId.value.trim() : null);
            toast.success(t('payment.action.manualDone'));
        }
        actionOpen.value = false;
        void reload();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'PAYMENT_CANCEL_NOT_GAVE_UP') {
            actionOpen.value = false;
            toast.error(error.message, 8000);
            void reload();
            return;
        }
        if (error.status === 400 && error.field('pgCancelId')) pgCancelError.value = error.field('pgCancelId') ?? '';
        const field = error.status === 400 ? error.field('reason') : '';
        if (field) actionFieldError.value = field;
        else actionError.value = error.message;
    } finally {
        actionBusy.value = false;
    }
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
} = usePaymentOrderDrawer(() => void reload());

onMounted(search);
</script>
