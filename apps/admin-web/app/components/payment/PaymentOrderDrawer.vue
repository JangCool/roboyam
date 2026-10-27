<template>
    <!--
        결제 상세 서랍 — 결제 주문 목록·결제 이상·웹훅 수신 내역·대사 불일치에서 결제 한 건을 누르면 연다. 열리는 쪽은 설정의 「서랍 위치」(`prefs.drawerPosition`).
        구획: ① 금액(주문 금액 · 결제액 · 취소액 · 남은 금액)과 결제 정보 ② 이력(`payment_events` 타임라인, 오래된 것 위) ③ 취소 대기열(`payment_cancels`) ④ 이 결제의 웹훅 기록 ⑤ 운영 조치(「포트원에서 다시 확인」).
        회원은 번호·닉네임만(이메일 없음 — 계약 결정 17). 서버가 상세를 열 때마다 열람 기록(`PAYMENT_ORDER_VIEW`, 개인정보 열람)을 남긴다는 한 줄을 머리에 둔다.
        머리(결제 번호·상태)는 누른 줄(`summary`)로 먼저 그리고, 상세는 부르는 쪽이 받아 내려 준다. 서버는 부르지 않는다(`components.md`) — 조치는 `recheck` 로 올린다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '48rem', maxWidth: '100vw' }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ head.orderName }}</h2>
                    <Tag :value="t(`payment.status.${head.status}`)" :severity="UtilPayment.statusSeverity(head.status)" />
                    <Tag v-if="head.anomaly" :value="t(`payment.anomaly.${head.anomaly}`)" severity="danger" icon="pi pi-exclamation-triangle" />
                </div>
                <code class="truncate text-[12px] text-muted-color">{{ head.paymentId }}</code>
                <p class="m-0 text-[11px] text-muted-color">{{ t('payment.drawer.viewAudit') }}</p>
            </div>
        </template>

        <ErrorState v-if="error" :title="t('payment.drawer.loadFailed')" :description="error.status === 404 ? t('payment.drawer.notFound') : error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="40%" height="1.25rem" />
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '65%'" />
        </div>

        <div v-else class="flex min-w-0 flex-col gap-6">
            <Message v-if="detail.anomaly" severity="error" :closable="false" size="small">{{ t(`payment.anomalyHelp.${detail.anomaly}`) }}</Message>
            <Message v-if="detail.openIssueCount > 0" severity="warn" :closable="false" size="small">
                {{ t('payment.drawer.openIssues', { count: detail.openIssueCount }) }}
                <NuxtLink to="/payments/reconciliation" class="ml-1 whitespace-nowrap font-medium text-primary hover:underline">{{ t('payment.drawer.openIssuesLink') }} ›</NuxtLink>
            </Message>

            <!-- ① 금액 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-amount`">
                <h3 :id="`${titleId}-amount`" class="text-sm font-semibold">{{ t('payment.drawer.secAmount') }}</h3>
                <div class="grid grid-cols-2 gap-2 sm:grid-cols-4">
                    <div v-for="box in amountBoxes" :key="box.key" class="rounded-lg border border-(--line) px-3 py-2">
                        <div class="text-[11px] text-muted-color">{{ box.label }}</div>
                        <div class="text-[15px] font-semibold tabular-nums" :class="box.tone">{{ box.value }}</div>
                    </div>
                </div>
                <dl class="m-0 flex flex-col">
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.member') }}</dt>
                        <dd :class="DD">
                            <span :class="detail.member.withdrawn ? 'text-muted-color' : ''">{{ detail.member.nickname }}</span>
                            <code v-if="detail.member.id" class="ml-1.5 text-xs tabular-nums text-muted-color">{{ detail.member.id }}</code>
                        </dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.order') }}</dt>
                        <dd :class="DD">
                            {{ t(`payment.orderType.${detail.orderType}`) }}
                            <code class="ml-1.5 text-xs tabular-nums text-muted-color">{{ detail.orderRefId }}</code>
                        </dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.payMethod') }}</dt>
                        <dd :class="DD">{{ detail.payMethod ?? '—' }}<span v-if="detail.pgProvider" class="text-muted-color"> · {{ detail.pgProvider }}</span></dd>
                    </div>
                    <div v-if="detail.pgTxId" :class="ROW">
                        <dt :class="DT">{{ t('payment.field.pgTxId') }}</dt>
                        <dd :class="DD">
                            <code class="text-xs">{{ detail.pgTxId }}</code>
                            <a v-if="detail.receiptUrl" :href="detail.receiptUrl" target="_blank" rel="noopener noreferrer" class="ml-2 whitespace-nowrap text-primary hover:underline">{{ t('payment.field.receipt') }} <i class="pi pi-external-link text-[10px]" aria-hidden="true" /></a>
                        </dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.confirmedVia') }}</dt>
                        <dd :class="DD">{{ detail.confirmedVia ? t(`payment.confirmedVia.${detail.confirmedVia}`) : '—' }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.createdAt') }}</dt>
                        <dd :class="DD" class="tabular-nums">{{ time.full(detail.createdAt) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.expiresAt') }}</dt>
                        <dd :class="DD" class="tabular-nums">{{ time.full(detail.expiresAt) }}</dd>
                    </div>
                    <div :class="ROW">
                        <dt :class="DT">{{ t('payment.field.paidAt') }}</dt>
                        <dd :class="DD" class="tabular-nums">{{ detail.paidAt ? time.full(detail.paidAt) : '—' }}</dd>
                    </div>
                </dl>
            </section>

            <!-- ② 이력 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-events`">
                <h3 :id="`${titleId}-events`" class="text-sm font-semibold">{{ t('payment.drawer.secEvents') }}</h3>
                <ol class="m-0 flex list-none flex-col p-0">
                    <li v-for="(item, index) in detail.events" :key="item.id" class="relative flex gap-3 pb-3 pl-1 last:pb-0">
                        <span v-if="index < detail.events.length - 1" class="absolute top-4 bottom-0 left-[0.6875rem] w-px bg-(--line)" aria-hidden="true" />
                        <span class="relative z-1 mt-1 flex size-3.5 shrink-0 items-center justify-center rounded-full border-2" :class="eventDot(item.kind)" aria-hidden="true" />
                        <div class="min-w-0 flex-1">
                            <div class="flex flex-wrap items-baseline gap-x-2">
                                <span class="text-[13px] font-medium">{{ t(`payment.event.${item.kind}`) }}</span>
                                <span class="text-[11px] tabular-nums text-muted-color">{{ time.full(item.createdAt) }}</span>
                            </div>
                            <p v-if="item.detail" class="m-0 text-xs text-muted-color [overflow-wrap:anywhere]">{{ item.detail }}</p>
                            <code v-if="item.providerEventId" class="text-[11px] text-muted-color">{{ t('payment.drawer.webhookId', { id: item.providerEventId }) }}</code>
                        </div>
                    </li>
                </ol>
            </section>

            <!-- ③ 취소 대기열 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-cancels`">
                <h3 :id="`${titleId}-cancels`" class="text-sm font-semibold">{{ t('payment.drawer.secCancels') }}</h3>
                <p v-if="!detail.cancels.length" class="m-0 text-[13px] text-muted-color">{{ t('payment.drawer.cancelsEmpty') }}</p>
                <ul v-else class="m-0 flex list-none flex-col gap-2 p-0">
                    <li v-for="c in detail.cancels" :key="c.id" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2">
                        <div class="flex flex-wrap items-center gap-2">
                            <span class="text-[13px] font-semibold tabular-nums">{{ t('payment.won', { amount: UtilPayment.won(c.amount, locale) }) }}</span>
                            <span class="text-[13px]">{{ t(`payment.cancelKind.${c.kind}`) }}</span>
                            <Tag :value="t(`payment.cancelStatus.${c.status}`)" :severity="UtilPayment.cancelSeverity(c.status)" />
                            <span class="ml-auto text-[11px] tabular-nums text-muted-color">{{ time.full(c.createdAt) }}</span>
                        </div>
                        <p v-if="c.reason" class="m-0 text-xs text-muted-color">{{ t('payment.drawer.cancelReason', { reason: c.reason }) }}</p>
                        <p class="m-0 text-xs text-muted-color">{{ cancelProgress(c) }}</p>
                        <p v-if="c.lastError && c.status !== 'SUCCEEDED'" class="m-0 text-xs text-red-600 [overflow-wrap:anywhere] dark:text-red-400">{{ t('payment.drawer.lastError', { error: c.lastError }) }}</p>
                        <p v-if="c.resolvedBy" class="m-0 text-xs text-muted-color">{{ t('payment.drawer.resolvedLine', { name: c.resolvedBy, note: c.resolveNote ?? '—' }) }}</p>
                    </li>
                </ul>
                <p v-if="detail.cancels.some((c) => c.status === 'GAVE_UP')" class="m-0 text-xs text-muted-color">
                    {{ t('payment.drawer.gaveUpHint') }}
                    <NuxtLink to="/payments/anomalies" class="ml-1 whitespace-nowrap text-primary hover:underline">{{ t('payment.drawer.gaveUpLink') }} ›</NuxtLink>
                </p>
            </section>

            <!-- ④ 웹훅 기록 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${titleId}-webhooks`">
                <h3 :id="`${titleId}-webhooks`" class="text-sm font-semibold">{{ t('payment.drawer.secWebhooks') }}</h3>
                <p v-if="!detail.webhooks.length" class="m-0 text-[13px] text-muted-color">{{ t('payment.drawer.webhooksEmpty') }}</p>
                <ul v-else class="m-0 flex list-none flex-col p-0">
                    <li v-for="w in detail.webhooks" :key="w.id" class="flex flex-wrap items-center gap-x-2 gap-y-0.5 border-b border-(--line) py-1.5 last:border-b-0">
                        <span class="text-[11px] tabular-nums text-muted-color">{{ time.full(w.receivedAt) }}</span>
                        <code class="text-[12px]">{{ w.eventType ?? '—' }}</code>
                        <Tag :value="t(`payment.webhookResult.${w.result}`)" :severity="UtilPayment.webhookSeverity(w.result)" />
                        <span class="text-[11px] tabular-nums text-muted-color">HTTP {{ w.httpStatus }}</span>
                        <code v-if="w.errorCode" class="text-[11px] text-muted-color">{{ w.errorCode }}</code>
                    </li>
                </ul>
                <p class="m-0 text-xs text-muted-color">{{ t('payment.drawer.webhooksNote') }}</p>
            </section>

            <!-- ⑤ 운영 조치 -->
            <section class="flex flex-col gap-3 rounded-xl border border-(--line) p-3" :aria-labelledby="`${titleId}-actions`">
                <h3 :id="`${titleId}-actions`" class="text-sm font-semibold">{{ t('payment.drawer.secActions') }}</h3>
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('payment.recheck.help') }}</p>
                <div class="flex flex-wrap items-center gap-2">
                    <Button type="button" :label="t('payment.recheck.open')" icon="pi pi-sync" size="small" severity="secondary" variant="outlined" :disabled="!canManage || busy" :loading="busy" @click="emit('recheck')" />
                    <span v-if="!canManage" class="text-xs text-muted-color">{{ t('payment.needManage') }}</span>
                </div>
            </section>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { PaymentCancelView, PaymentEventKind, PaymentOrderDetail, PaymentOrderRow } from '~/composables/payment/api/usePaymentAdminApi.types';
import UtilPayment from '~/utils/util-payment';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 누른 줄 — 상세를 받는 동안 머리를 채운다 */
    summary: PaymentOrderRow | null;
    detail: PaymentOrderDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 「포트원에서 다시 확인」 중 */
    busy?: boolean;
    /** 처리 권한(`commerce.reconcile.manage`) */
    canManage?: boolean;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, busy: false, canManage: false });
const emit = defineEmits<{ recheck: []; retry: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const ROW = 'grid grid-cols-1 gap-x-3 gap-y-0.5 border-b border-(--line) py-2 last:border-b-0 sm:grid-cols-[9rem_minmax(0,1fr)]';
const DT = 'text-[13px] text-muted-color';
const DD = 'm-0 min-w-0 text-[13px] [overflow-wrap:anywhere]';

const head = computed(() => props.detail ?? props.summary);

/** 금액 칸 넷 — 주문 금액(서버 계산) · 결제액(포트원) · 취소액(SUCCEEDED 합) · 남은 금액(결제 − 취소) */
const amountBoxes = computed(() => {
    const d = props.detail;
    if (!d) return [];
    const won = (n: number | null) => (n === null ? '—' : t('payment.won', { amount: UtilPayment.won(n, locale.value) }));
    const mismatch = d.paidAmount !== null && d.paidAmount !== d.amount;
    return [
        { key: 'amount', label: t('payment.field.amount'), value: won(d.amount), tone: '' },
        { key: 'paid', label: t('payment.field.paidAmount'), value: won(d.paidAmount), tone: mismatch ? 'text-red-600 dark:text-red-400' : '' },
        { key: 'canceled', label: t('payment.field.canceledAmount'), value: won(d.canceledAmount), tone: d.canceledAmount > 0 ? 'text-orange-600 dark:text-orange-400' : '' },
        { key: 'net', label: t('payment.field.netAmount'), value: won(d.paidAmount === null ? null : d.paidAmount - d.canceledAmount), tone: '' },
    ];
});

function eventDot(kind: PaymentEventKind): string {
    if (kind === 'PAID' || kind === 'CANCEL_SUCCEEDED') return 'border-green-600 bg-green-100 dark:bg-green-950';
    if (kind === 'ANOMALY' || kind === 'CANCEL_GAVE_UP' || kind === 'EXTERNAL_CANCEL') return 'border-red-600 bg-red-100 dark:bg-red-950';
    if (kind === 'CANCEL_REQUESTED' || kind === 'CANCEL_MANUAL') return 'border-orange-500 bg-orange-100 dark:bg-orange-950';
    return 'border-surface-400 bg-(--p-content-background)';
}

/** 시도 횟수 · 다음 시도 / 끝난 시각 한 줄 */
function cancelProgress(c: PaymentCancelView): string {
    if (c.status === 'SUCCEEDED' || c.status === 'MANUAL') return t('payment.drawer.cancelDone', { at: time.value.full(c.completedAt), attempts: c.attempts });
    if (c.status === 'GAVE_UP') return t('payment.drawer.cancelGaveUp', { attempts: c.attempts });
    if (c.status === 'SENDING') return t('payment.drawer.cancelSending', { attempts: c.attempts });
    return t('payment.drawer.cancelPending', { attempts: c.attempts, next: c.nextAttemptAt ? time.value.full(c.nextAttemptAt) : '—' });
}
</script>
