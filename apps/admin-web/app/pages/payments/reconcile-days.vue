<template>
    <!--
        결제 › 날짜별 대사(`/payments/reconcile-days`) — 매일 04:50(KST) 어제 하루의 포트원 결제·취소를 우리 기록과 맞춰 본 결과를 날짜마다 한 줄로(`payment_reconcile_days`, commerce.md 16-1, 계약 결정 16).
        ⚠ 목업 단계(`usePaymentAdminApi` — 서버는 2a-3). 다른 점(이슈)을 보고 닫는 일은 「대사 불일치」(`/payments/reconciliation`) 화면이 한다.
        ① 요약: 마지막으로 대사한 날 · 다음 자동 실행 · 열린 이슈 수(누르면 대사 불일치).
        ② 날짜 표(최근 14일): 날짜 · 상태 · 확인한 건수 · 불일치(열림/전체) · 끝난 시각 · [이슈 보기](대사 불일치 `?day=`) · [이 날 다시 대사](`commerce.reconcile.manage`, 확인창 — 비동기로 돌고 몇 초 뒤 다시 읽는다).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('payment.reconcileDaysTitle')" :subtitle="t('payment.reconcileDaysSubtitle')">
            <template #actions>
                <Button type="button" :label="t('payment.refresh')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="load" />
            </template>
        </PageTitle>

        <ErrorState v-if="loadError && !overview" :title="t('payment.reconcileLoadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load" />

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
                <NuxtLink to="/payments/reconciliation" class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-primary">
                    <div class="text-xs text-muted-color">{{ t('payment.reconcile.openIssues') }} ›</div>
                    <div class="mt-0.5 text-base font-semibold" :class="overview && overview.openIssueCount > 0 ? 'text-orange-600 dark:text-orange-400' : ''">
                        <Skeleton v-if="!overview" width="3rem" height="1.25rem" />
                        <template v-else>{{ t('payment.count', { count: overview.openIssueCount }) }}</template>
                    </div>
                </NuxtLink>
            </div>
            <p class="m-0 px-1 text-xs leading-relaxed text-muted-color">{{ t('payment.reconcile.noAutoFix') }}</p>

            <!-- ② 날짜 표 -->
            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable :value="overview?.days ?? []" data-key="day" :loading="busy && !overview" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable class="min-w-0">
                    <template #empty>
                        <div v-if="busy" class="h-24" aria-hidden="true" />
                        <EmptyState v-else icon="pi pi-calendar" :title="t('payment.reconcile.daysEmpty')" />
                    </template>
                    <Column :header="t('payment.col.day')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="text-[13px] tabular-nums">{{ time.serviceDate(data.day) }}</span>
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
                                <Button v-if="data.issueCount > 0" as="router-link" :to="`/payments/reconciliation?day=${data.day}`" :label="t('payment.reconcile.showIssues')" size="small" severity="secondary" variant="text" />
                                <Button type="button" :label="t('payment.reconcile.rerun')" icon="pi pi-replay" size="small" severity="secondary" variant="outlined" :disabled="!canManage || data.status === 'RUNNING' || rerunning !== null" :loading="rerunning === data.day" :title="canManage ? undefined : t('payment.needManage')" @click="rerun(data.day)" />
                            </div>
                        </template>
                    </Column>
                </DataTable>
            </div>
            <p class="m-0 px-1 text-xs text-muted-color">{{ t('payment.reconcile.daysNote') }}</p>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { ReconcileOverview } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilPayment from '~/utils/util-payment';

const { t } = useI18n();
useSeoMeta({ title: () => t('payment.reconcileDaysTitle') });

const api = usePaymentAdminApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();
const { prefs } = useLayoutPrefs();

const canManage = computed(() => can('commerce.reconcile.manage'));

const overview = ref<ReconcileOverview | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

async function load(): Promise<void> {
    busy.value = true;
    loadError.value = null;
    try {
        overview.value = await api.reconcile();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        busy.value = false;
    }
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
        rerunTimer = setTimeout(load, 4000);
    } catch (e) {
        toast.error(toApiError(e).message, 8000);
    } finally {
        rerunning.value = null;
    }
}
onBeforeUnmount(() => clearTimeout(rerunTimer));

onMounted(load);
</script>
