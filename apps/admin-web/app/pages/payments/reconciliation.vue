<template>
    <!--
        결제 › 대사 불일치(`/payments/reconciliation`) — 매일 대사가 찾은 다른 점(`reconciliation_issues`, 5종)을 보고 닫는다(commerce.md 16-1, 계약 결정 16). **자동 보정하지 않는다** — 운영자가 맞춘 뒤 「처리」로 닫는다.
        ⚠ 목업 단계(`usePaymentAdminApi` — 서버는 2a-3). 날짜마다 대사가 돌았는지·다시 돌리기는 「날짜별 대사」(`/payments/reconcile-days`) 화면이 한다.
        거르기: 종류 · 상태(기본 열림) · 날짜(`?day=YYYY-MM-DD` — 날짜별 대사의 [이슈 보기]로 들어오면 그 날, 칩으로 뗀다).
        한 줄: 발견한 날 · 종류(+ 풀이) · 결제 번호(우리 주문이 있으면 상세 서랍) · 내용 · 상태/처리 기록 · [처리](사유 5~200자, 감사 `RECONCILE_ISSUE_RESOLVE`, `commerce.reconcile.manage`).
        `409 RECONCILE_ISSUE_NOT_OPEN`(다른 운영자가 먼저)은 닫고 알린 뒤 다시 읽는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('payment.reconcileTitle')" :subtitle="t('payment.reconcileSubtitle')">
            <template #actions>
                <Button as="router-link" to="/payments/reconcile-days" :label="t('payment.reconcile.daysLink')" icon="pi pi-calendar" size="small" severity="secondary" variant="outlined" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('payment.filter.searchLabel')" @submit.prevent="search">
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
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
            </div>
        </form>

        <p class="m-0 px-1 text-xs leading-relaxed text-muted-color">{{ t('payment.reconcile.noAutoFix') }}</p>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="issues"
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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('payment.forbidden') : t('payment.reconcileLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-24" aria-hidden="true" />
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
import type { ReconcileIssueQuery, ReconcileIssueRow, ReconcileIssueStatus, ReconcileIssueType } from '~/composables/payment/api/usePaymentAdminApi.types';
import { RECONCILE_ISSUE_TYPES } from '~/composables/payment/api/usePaymentAdminApi.types';

interface IssueFilter {
    type: string;
    /** 기본 OPEN, '' = 전체 */
    status: string;
    day: string;
}

const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

const { t } = useI18n();
useSeoMeta({ title: () => t('payment.reconcileTitle') });

const api = usePaymentAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const route = useRoute();
const router = useRouter();

const canManage = computed(() => can('commerce.reconcile.manage'));
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const typeOptions = computed(() => [{ value: '', label: t('common.all') }, ...RECONCILE_ISSUE_TYPES.map((v) => ({ value: v, label: t(`payment.issueType.${v}`) }))]);
const issueStatusOptions = computed(() => [{ value: 'OPEN', label: t('payment.issueStatus.OPEN') }, { value: 'RESOLVED', label: t('payment.issueStatus.RESOLVED') }, { value: '', label: t('common.all') }]);

const filter = reactive<IssueFilter>({ type: '', status: 'OPEN', day: '' });
const applied = ref<Omit<ReconcileIssueQuery, 'page' | 'size'>>({});
const issues = ref<ReconcileIssueRow[]>([]);
const total = ref(0);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
const first = ref(0);
const size = ref(20);

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.issues({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        issues.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        issues.value = [];
        total.value = 0;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function search(): void {
    const q: Omit<ReconcileIssueQuery, 'page' | 'size'> = {};
    if (filter.type) q.type = filter.type as ReconcileIssueType;
    if (filter.status) q.status = filter.status as ReconcileIssueStatus;
    if (filter.day) q.day = filter.day;
    applied.value = q;
    first.value = 0;
    void reload();
}

/** 날짜 칩을 떼면 주소의 `?day=` 도 지운다 */
function clearDay(): void {
    filter.day = '';
    if (route.query.day) {
        const { day: _drop, ...rest } = route.query;
        void router.replace({ query: rest });
    }
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

/** 주소의 `?day=`(날짜별 대사의 [이슈 보기]) — 그 날의 이슈를 상태와 상관없이 */
function dayFromRoute(): string {
    const value = route.query.day;
    return typeof value === 'string' && DAY_PATTERN.test(value) ? value : '';
}

watch(
    () => route.query.day,
    () => {
        const day = dayFromRoute();
        if (!day || day === filter.day) return;
        filter.day = day;
        filter.status = '';
        search();
    },
);

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
        void reload();
    } catch (e) {
        const error = toApiError(e);
        if (error.code === 'RECONCILE_ISSUE_NOT_OPEN') {
            resolveOpen.value = false;
            toast.error(error.message, 8000);
            void reload();
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
} = usePaymentOrderDrawer(() => void reload());

onMounted(() => {
    const day = dayFromRoute();
    if (day) {
        filter.day = day;
        filter.status = '';
    }
    search();
});
</script>
