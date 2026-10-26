<template>
    <!--
        결제 › 대사 불일치(`/payments/reconciliation`) — 매일 04:50(KST) 어제 하루의 포트원 결제·취소를 우리 기록과 맞춰 본 결과(commerce.md 16-1, 계약 결정 16). **자동 보정하지 않는다** — 운영자가 맞춘 뒤 「처리」로 닫는다.
        ⚠ 목업 단계(`usePaymentAdminApi` — 서버는 2a-3).
        ① 요약: 마지막으로 대사한 날 · 다음 자동 실행 · 열린 이슈 수.
        ② 날짜별 대사 상태(최근 14일, `payment_reconcile_days`): 날짜 · 상태 · 확인한 건수 · 불일치(열림/전체) · 끝난 시각 · [이슈 보기] · [이 날 다시 대사](`commerce.reconcile.manage`, 확인창 — 비동기로 돌고 몇 초 뒤 다시 읽는다).
        ③ 이슈 목록(5종, `reconciliation_issues`): 거르기 종류 · 상태(기본 열림) · 날짜(②에서 고르면). 한 줄: 발견한 날 · 종류 · 결제 번호(우리 주문이 있으면 상세 서랍) · 내용 · 상태/처리 기록 · [처리](사유 5~200자, 감사 `RECONCILE_ISSUE_RESOLVE`).
        `409 RECONCILE_ISSUE_NOT_OPEN`(다른 운영자가 먼저)은 닫고 알린 뒤 다시 읽는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('payment.reconcileTitle')" :subtitle="t('payment.reconcileSubtitle')">
            <template #actions>
                <Button type="button" :label="t('payment.refresh')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="overviewBusy" @click="loadAll" />
            </template>
        </PageTitle>

        <ErrorState v-if="overviewError && !overview" :title="t('payment.reconcileLoadFailed')" :description="overviewError.message" retry :retrying="overviewBusy" @retry="loadAll" />

        <template v-else>
            <!-- ① 요약 -->
            <div class="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3">
                    <div class="text-xs text-muted-color">{{ t('payment.reconcile.lastChecked') }}</div>
                    <div class="mt-0.5 text-base font-semibold">
                        <Skeleton v-if="!overview" width="7rem" height="1.25rem" />
                        <template v-else>{{ overview.lastCheckedDay ? time.serviceDate(overview.lastCheckedDay) : '—' }}</template>
                    </div>
                </div>
                <div class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3">
                    <div class="text-xs text-muted-color">{{ t('payment.reconcile.nextRun') }}</div>
                    <div class="mt-0.5 text-base font-semibold tabular-nums">
                        <Skeleton v-if="!overview" width="9rem" height="1.25rem" />
                        <template v-else>{{ time.full(overview.nextRunAt) }}</template>
                    </div>
                </div>
                <div class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3">
                    <div class="text-xs text-muted-color">{{ t('payment.reconcile.openIssues') }}</div>
                    <div class="mt-0.5 text-base font-semibold" :class="overview && overview.openIssueCount > 0 ? 'text-orange-600 dark:text-orange-400' : ''">
                        <Skeleton v-if="!overview" width="3rem" height="1.25rem" />
                        <template v-else>{{ t('payment.count', { count: overview.openIssueCount }) }}</template>
                    </div>
                </div>
            </div>
            <p class="m-0 px-1 text-xs leading-relaxed text-muted-color">{{ t('payment.reconcile.noAutoFix') }}</p>

            <!-- ② 날짜별 대사 상태 -->
            <AppGadget :title="t('payment.reconcile.daysTitle')">
                <DataTable :value="overview?.days ?? []" data-key="day" :loading="overviewBusy && !overview" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable class="min-w-0">
                    <Column :header="t('payment.col.day')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="text-[13px] tabular-nums" :class="data.day === filter.day ? 'font-semibold' : ''">{{ time.serviceDate(data.day) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('payment.col.status')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <Tag :value="t(`payment.dayStatus.${data.status}`)" :severity="UtilPayment.daySeverity(data.status)" :icon="data.status === 'RUNNING' ? 'pi pi-spin pi-spinner' : undefined" />
                        </template>
                    </Column>
                    <Column :header="t('payment.col.checked')" style="min-width: 6rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                        <template #body="{ data }">
                            <span class="block text-right text-[13px] tabular-nums">{{ data.status === 'DONE' || data.status === 'MISMATCH' ? t('payment.count', { count: data.checkedCount }) : '—' }}</span>
                        </template>
                    </Column>
                    <Column :header="t('payment.col.issues')" style="min-width: 7rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                        <template #body="{ data }">
                            <span class="block text-right text-[13px] tabular-nums" :class="data.openIssueCount > 0 ? 'font-semibold text-orange-600 dark:text-orange-400' : 'text-muted-color'">
                                {{ data.issueCount > 0 ? t('payment.reconcile.issueCount', { open: data.openIssueCount, all: data.issueCount }) : '0' }}
                            </span>
                        </template>
                    </Column>
                    <Column :header="t('payment.col.finishedAt')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums text-muted-color">{{ data.finishedAt ? time.shortDateTime(data.finishedAt) : '—' }}</span>
                        </template>
                    </Column>
                    <Column :header="t('payment.col.actions')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <div class="flex flex-wrap gap-1">
                                <Button v-if="data.issueCount > 0" type="button" :label="t('payment.reconcile.showIssues')" size="small" severity="secondary" variant="text" @click="pickDay(data.day)" />
                                <Button type="button" :label="t('payment.reconcile.rerun')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :disabled="!canManage || data.status === 'RUNNING' || rerunning !== null" :loading="rerunning === data.day" :title="canManage ? undefined : t('payment.needManage')" @click="rerun(data.day)" />
                            </div>
                        </template>
                    </Column>
                </DataTable>
                <p class="m-0 mt-2 text-xs text-muted-color">{{ t('payment.reconcile.daysNote') }}</p>
            </AppGadget>

            <!-- ③ 이슈 목록 -->
            <AppGadget :title="t('payment.reconcile.issuesTitle')">
                <div class="flex flex-col gap-3">
                    <form class="flex flex-col gap-3" role="search" :aria-label="t('payment.filter.searchLabel')" @submit.prevent="searchIssues">
                        <FilterRow :label="t('payment.filter.issueType')">
                            <template #default="{ labelId }">
                                <FilterOptionGroup v-model="filter.type" :options="typeOptions" :labelledby="labelId" />
                            </template>
                        </FilterRow>
                        <FilterRow :label="t('payment.filter.issueStatus')">
                            <template #default="{ labelId }">
                                <FilterOptionGroup v-model="filter.status" :options="issueStatusOptions" :labelledby="labelId" />
                            </template>
                        </FilterRow>
                        <FilterRow v-if="filter.day" :label="t('payment.filter.day')">
                            <div class="flex min-h-8 items-center gap-2">
                                <Chip :label="time.serviceDate(filter.day)" removable @remove="clearDay" />
                            </div>
                        </FilterRow>
                        <div class="flex flex-wrap items-center gap-2">
                            <Button type="submit" :label="t('common.search')" size="small" :loading="issuesBusy" />
                        </div>
                    </form>

                    <DataTable
                        :value="issues"
                        data-key="id"
                        lazy
                        paginator
                        :first="first"
                        :rows="size"
                        :total-records="issuesTotal"
                        :rows-per-page-options="[20, 50, 100]"
                        :loading="issuesBusy"
                        :size="prefs.density === 'compact' ? 'small' : undefined"
                        scrollable
                        :current-page-report-template="totalTemplate"
                        paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                        class="min-w-0"
                        @page="onPage"
                    >
                        <template #empty>
                            <ErrorState v-if="issuesError" :title="t('payment.reconcileLoadFailed')" :description="issuesError.message" retry :retrying="issuesBusy" @retry="reloadIssues" />
                            <div v-else-if="issuesBusy" class="h-24" aria-hidden="true" />
                            <EmptyState v-else icon="pi pi-check-circle" :title="t('payment.reconcile.issuesEmpty')" />
                        </template>

                        <Column :header="t('payment.col.day')" style="min-width: 7.5rem">
                            <template #body="{ data }">
                                <span class="text-[12px] tabular-nums">{{ time.serviceDate(data.detectedDay) }}</span>
                            </template>
                        </Column>
                        <Column :header="t('payment.col.kind')" style="min-width: 11rem">
                            <template #body="{ data }">
                                <div class="min-w-0">
                                    <div class="text-[13px] font-medium">{{ t(`payment.issueType.${data.type}`) }}</div>
                                    <div class="max-w-[16rem] text-[11px] text-muted-color">{{ t(`payment.issueTypeHelp.${data.type}`) }}</div>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('payment.col.paymentId')" style="min-width: 11rem">
                            <template #body="{ data }">
                                <button v-if="data.paymentOrderId" type="button" class="block max-w-[13rem] cursor-pointer truncate text-left font-mono text-[12px] text-primary hover:underline" :title="t('payment.openDetail')" @click="show(data.paymentOrderId)">
                                    {{ data.paymentId }}
                                </button>
                                <div v-else class="min-w-0">
                                    <code class="block max-w-[13rem] truncate text-[12px]" :title="data.paymentId">{{ data.paymentId }}</code>
                                    <span class="text-[11px] text-muted-color">{{ t('payment.reconcile.noLocalOrder') }}</span>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('payment.col.detail')" style="min-width: 14rem">
                            <template #body="{ data }">
                                <span class="text-[12px] [overflow-wrap:anywhere]">{{ data.detail }}</span>
                            </template>
                        </Column>
                        <Column :header="t('payment.col.status')" style="min-width: 10rem">
                            <template #body="{ data }">
                                <div class="flex min-w-0 flex-col items-start gap-0.5">
                                    <Tag :value="t(`payment.issueStatus.${data.status}`)" :severity="data.status === 'OPEN' ? 'warn' : 'secondary'" />
                                    <template v-if="data.status === 'RESOLVED'">
                                        <span class="text-[11px] tabular-nums text-muted-color">{{ data.resolvedBy ?? '—' }} · {{ time.shortDateTime(data.resolvedAt) }}</span>
                                        <span class="max-w-[14rem] truncate text-[11px] text-muted-color" :title="data.resolveNote ?? undefined">{{ data.resolveNote }}</span>
                                    </template>
                                </div>
                            </template>
                        </Column>
                        <Column :header="t('payment.col.actions')" style="min-width: 6rem">
                            <template #body="{ data }">
                                <Button v-if="data.status === 'OPEN'" type="button" :label="t('payment.reconcile.resolve')" size="small" :disabled="!canManage" :title="canManage ? undefined : t('payment.needManage')" @click="openResolve(data)" />
                            </template>
                        </Column>
                    </DataTable>
                </div>
            </AppGadget>
        </template>

        <PaymentReasonDialog
            v-model:visible="resolveOpen"
            :title="t('payment.reconcile.resolveTitle')"
            :description="t('payment.reconcile.resolveDescription')"
            :placeholder="t('payment.reconcile.resolvePlaceholder')"
            :confirm-label="t('payment.reconcile.resolve')"
            :busy="resolveBusy"
            :error="resolveError"
            :field-error="resolveFieldError"
            @submit="submitResolve"
        >
            <dl v-if="resolveTarget" class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                <dt class="text-muted-color">{{ t('payment.col.kind') }}</dt>
                <dd class="m-0">{{ t(`payment.issueType.${resolveTarget.type}`) }}</dd>
                <dt class="text-muted-color">{{ t('payment.col.paymentId') }}</dt>
                <dd class="m-0 min-w-0"><code class="text-xs [overflow-wrap:anywhere]">{{ resolveTarget.paymentId }}</code></dd>
                <dt class="text-muted-color">{{ t('payment.col.detail') }}</dt>
                <dd class="m-0 text-xs [overflow-wrap:anywhere]">{{ resolveTarget.detail }}</dd>
            </dl>
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
import type { ReconcileIssueQuery, ReconcileIssueRow, ReconcileIssueStatus, ReconcileIssueType, ReconcileOverview } from '~/composables/payment/api/usePaymentAdminApi.types';
import { RECONCILE_ISSUE_TYPES } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilPayment from '~/utils/util-payment';

interface IssueFilter {
    type: string;
    /** 기본 OPEN, '' = 전체 */
    status: string;
    day: string;
}

const { t } = useI18n();
useSeoMeta({ title: () => t('payment.reconcileTitle') });

const api = usePaymentAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();

const canManage = computed(() => can('commerce.reconcile.manage'));
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const typeOptions = computed(() => [{ value: '', label: t('common.all') }, ...RECONCILE_ISSUE_TYPES.map((v) => ({ value: v, label: t(`payment.issueType.${v}`) }))]);
const issueStatusOptions = computed(() => [{ value: 'OPEN', label: t('payment.issueStatus.OPEN') }, { value: 'RESOLVED', label: t('payment.issueStatus.RESOLVED') }, { value: '', label: t('common.all') }]);

// ── ①② 요약 · 날짜별 ─
const overview = ref<ReconcileOverview | null>(null);
const overviewBusy = ref(false);
const overviewError = ref<ApiError | null>(null);

async function loadOverview(): Promise<void> {
    overviewBusy.value = true;
    overviewError.value = null;
    try {
        overview.value = await api.reconcile();
    } catch (e) {
        overviewError.value = toApiError(e);
    } finally {
        overviewBusy.value = false;
    }
}

function loadAll(): void {
    void loadOverview();
    void reloadIssues();
}

const rerunning = ref<string | null>(null);
let rerunTimer: ReturnType<typeof setTimeout> | undefined;

async function rerun(day: string): Promise<void> {
    if (!canManage.value || rerunning.value) return;
    const ok = await confirm({ title: t('payment.reconcile.rerunTitle', { day: time.value.serviceDate(day) }), message: t('payment.reconcile.rerunMessage'), confirmLabel: t('payment.reconcile.rerun') });
    if (!ok) return;
    rerunning.value = day;
    try {
        const row = await api.rerunDay(day);
        if (overview.value) overview.value = { ...overview.value, days: overview.value.days.map((d) => (d.day === day ? row : d)) };
        toast.success(t('payment.reconcile.rerunStarted', { day: time.value.serviceDate(day) }));
        // 배치는 비동기로 돈다 — 잠시 뒤 한 번 다시 읽는다(그래도 RUNNING 이면 「새로 고침」)
        clearTimeout(rerunTimer);
        rerunTimer = setTimeout(loadAll, 4000);
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        rerunning.value = null;
    }
}
onBeforeUnmount(() => clearTimeout(rerunTimer));

// ── ③ 이슈 ─
const filter = reactive<IssueFilter>({ type: '', status: 'OPEN', day: '' });
const applied = ref<Omit<ReconcileIssueQuery, 'page' | 'size'>>({});
const issues = ref<ReconcileIssueRow[]>([]);
const issuesTotal = ref(0);
const issuesBusy = ref(false);
const issuesError = ref<ApiError | null>(null);
const first = ref(0);
const size = ref(20);

let ticket = 0;
async function reloadIssues(): Promise<void> {
    const mine = ++ticket;
    issuesBusy.value = true;
    issuesError.value = null;
    try {
        const found = await api.issues({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        issues.value = found.items;
        issuesTotal.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        issues.value = [];
        issuesTotal.value = 0;
        issuesError.value = toApiError(e);
    } finally {
        if (mine === ticket) issuesBusy.value = false;
    }
}

function searchIssues(): void {
    const q: Omit<ReconcileIssueQuery, 'page' | 'size'> = {};
    if (filter.type) q.type = filter.type as ReconcileIssueType;
    if (filter.status) q.status = filter.status as ReconcileIssueStatus;
    if (filter.day) q.day = filter.day;
    applied.value = q;
    first.value = 0;
    void reloadIssues();
}

/** 날짜 줄의 [이슈 보기] — 그 날의 이슈를 상태와 상관없이 */
function pickDay(day: string): void {
    filter.day = day;
    filter.status = '';
    searchIssues();
}

function clearDay(): void {
    filter.day = '';
    searchIssues();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reloadIssues();
}

// ── 처리 ─
const resolveOpen = ref(false);
const resolveTarget = ref<ReconcileIssueRow | null>(null);
const resolveBusy = ref(false);
const resolveError = ref('');
const resolveFieldError = ref('');

function openResolve(row: ReconcileIssueRow): void {
    if (!canManage.value) return;
    resolveTarget.value = row;
    resolveError.value = '';
    resolveFieldError.value = '';
    resolveOpen.value = true;
}

async function submitResolve(reason: string): Promise<void> {
    const target = resolveTarget.value;
    if (!target || resolveBusy.value) return;
    resolveBusy.value = true;
    resolveError.value = '';
    resolveFieldError.value = '';
    try {
        // 감사(돈 관련 — RECONCILE_ISSUE_RESOLVE)는 서버가 남긴다
        await api.resolveIssue(target.id, reason);
        toast.success(t('payment.reconcile.resolved'));
        resolveOpen.value = false;
        loadAll();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'RECONCILE_ISSUE_NOT_OPEN') {
            resolveOpen.value = false;
            toast.error(error.message, 8000);
            loadAll();
            return;
        }
        const field = error.status === 400 ? error.field('reason') : '';
        if (field) resolveFieldError.value = field;
        else resolveError.value = error.message;
    } finally {
        resolveBusy.value = false;
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
} = usePaymentOrderDrawer(loadAll);

onMounted(() => {
    void loadOverview();
    searchIssues();
});
</script>
