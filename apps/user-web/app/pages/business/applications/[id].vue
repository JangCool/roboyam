<template>
    <!--
        신청 건 상세 `/business/applications/{id}` — 단계 표시(신청 → 심사 → 결제 → 진행 → 종료) + 상태별 본문 + 신청 내용·소재 + 처리 기록(Timeline).
        심사 중 = [신청 취소] · 소재 수정 시 재심사 안내 / 반려 = 사유 코드·글 + [고쳐서 다시 신청] /
        결제 대기(온라인) = 확정 금액·기한 카운트다운(24시간 안이면 경고)·[결제하기] → 가짜 결제창(`PaymentFakeCheckoutDialog`) → 예약 확정 안내 /
        입금 대기(수동) = 입금 계좌·금액·기한·입금자명, 계약서 상태, 「입금이 확인되면 운영팀이 예약을 확정해요」 /
        예약·진행 = 기간·소재 버전·[취소·환불 요청](시작 전 = 전액 자동, 진행 중 = 일할 운영팀 확인 + 사유).
        `?pay=1` 로 오면(홈의 [결제하기]) 결제창을 바로 연다. ⚠ MOCK: 아래 「목업 운영자 처리」로 승인·입금 확인·시작·종료를 흉내 낸다.
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="business-application">
        <div class="flex items-center gap-2">
            <Button as="router-link" :to="UtilBusiness.APPLICATIONS_PATH" icon="pi pi-angle-left" severity="secondary" variant="text" rounded :aria-label="t('business.detail.back')" />
            <h1 class="m-0 min-w-0 truncate text-lg font-bold">{{ app?.title ?? t('business.detail.title') }}</h1>
        </div>

        <div v-if="status === 'loading' && !app" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <div v-for="n in 3" :key="n" class="h-28 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="!app" :title="t('business.detail.loadFailed')" :description="loadError" retry @retry="load" />

        <template v-else>
            <div class="flex flex-wrap items-center gap-1.5">
                <BusinessStatusTag :status="app.status" :phase="app.phase" />
                <span class="text-xs text-muted-color">{{ kindLabel }} · {{ app.id }}</span>
            </div>

            <section class="panel-card p-4">
                <BusinessStageTrack :status="app.status" :phase="app.phase" :paid="paid" />
            </section>

            <Message v-if="justPaid && app.status === 'SCHEDULED'" severity="success" :closable="false" data-testid="business-paid">
                <p class="m-0 font-semibold">{{ t('business.detail.paidTitle') }}</p>
                <p class="m-0 mt-1 text-sm">{{ t('business.detail.paidText', { date: time.serviceDate(app.startDate) }) }}</p>
            </Message>

            <!-- 상태별 본문 -->
            <section class="panel-card flex flex-col gap-3 p-4" data-testid="business-application-state">
                <!-- 심사 중 -->
                <template v-if="app.status === 'SUBMITTED'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.reviewTitle') }}</h2>
                    <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.detail.reviewText') }}</p>
                    <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('business.detail.creativeEditNote') }}</p>
                    <div>
                        <Button type="button" :label="t('business.detail.cancel')" severity="danger" variant="outlined" size="small" :loading="actionBusy" data-testid="business-cancel" @click="cancel" />
                    </div>
                </template>

                <!-- 반려 -->
                <template v-else-if="app.status === 'REJECTED'">
                    <h2 class="m-0 flex flex-wrap items-center gap-1.5 text-base font-semibold">
                        {{ t('business.detail.rejectedTitle') }}
                        <PointShopTag v-if="app.rejectCode" tone="danger">{{ t(`business.rejectCode.${app.rejectCode}`) }}</PointShopTag>
                    </h2>
                    <p class="m-0 whitespace-pre-line text-sm leading-relaxed">{{ app.rejectReason }}</p>
                    <div>
                        <Button as="router-link" :to="retryPath" :label="t('business.detail.retry')" icon="pi pi-refresh" size="small" />
                    </div>
                </template>

                <!-- 결제 대기(온라인) -->
                <template v-else-if="app.status === 'AWAITING_PAYMENT'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.payTitle') }}</h2>
                    <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.detail.payText') }}</p>
                    <Message v-if="dueSoon" severity="warn" size="small" :closable="false" data-testid="business-due-soon">{{ t('business.deadline.soon') }}</Message>
                    <BusinessDeadline v-if="app.paymentDueAt" :due-at="app.paymentDueAt" kind="PAYMENT" variant="box" />
                    <BusinessPriceSummary v-if="app.confirmed" :amount="app.confirmed" confirmed :adjust-reason="app.adjustReason" />
                    <div class="flex flex-wrap gap-2">
                        <Button type="button" :label="t('business.detail.pay')" icon="pi pi-credit-card" :loading="payStarting" data-testid="business-pay" @click="startPay" />
                        <Button type="button" :label="t('business.detail.cancel')" severity="secondary" variant="text" :loading="actionBusy" @click="cancel" />
                    </div>
                    <Message v-if="payError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ payError }}</Message>
                </template>

                <!-- 입금 대기(수동) -->
                <template v-else-if="app.status === 'AWAITING_DEPOSIT' && app.deposit">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.depositTitle') }}</h2>
                    <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.detail.depositText') }}</p>
                    <BusinessDeadline v-if="app.depositDueAt" :due-at="app.depositDueAt" kind="DEPOSIT" variant="box" />
                    <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 rounded-lg border border-hairline p-3.5 text-sm">
                        <dt class="text-muted-color">{{ t('business.detail.bank') }}</dt>
                        <dd class="m-0">{{ app.deposit.bankName }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.account') }}</dt>
                        <dd class="m-0 font-semibold tabular-nums">{{ app.deposit.accountNo }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.holder') }}</dt>
                        <dd class="m-0">{{ app.deposit.accountHolder }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.depositAmount') }}</dt>
                        <dd class="m-0 font-bold tabular-nums">{{ UtilBusinessMessages.won(t, app.confirmed?.total ?? app.amount.total) }}</dd>
                        <dt class="text-muted-color">{{ t('business.detail.depositor') }}</dt>
                        <dd class="m-0">
                            <span class="font-semibold">{{ app.deposit.depositorName }}</span>
                            <span class="block text-xs text-muted-color">{{ t('business.detail.depositorHelp') }}</span>
                        </dd>
                        <dt class="text-muted-color">{{ t('business.detail.contract') }}</dt>
                        <dd class="m-0 flex items-center">
                            <PointShopTag :tone="app.deposit.contract === 'RECEIVED' ? 'success' : 'neutral'">{{ t(`business.detail.contractStatus.${app.deposit.contract}`) }}</PointShopTag>
                        </dd>
                        <dt class="text-muted-color">{{ t('business.detail.deposit') }}</dt>
                        <dd class="m-0 flex items-center">
                            <PointShopTag :tone="app.deposit.depositConfirmed ? 'success' : 'neutral'">{{ t(`business.detail.depositState.${app.deposit.depositConfirmed ? 'CONFIRMED' : 'WAITING'}`) }}</PointShopTag>
                        </dd>
                    </dl>
                    <p class="m-0 text-xs text-muted-color">{{ t('business.detail.depositCancelNote') }}</p>
                </template>

                <!-- 예약·진행·종료 -->
                <template v-else-if="app.status === 'SCHEDULED'">
                    <h2 class="m-0 text-base font-semibold">
                        {{ app.phase === 'RUNNING' ? t('business.detail.runningTitle') : app.phase === 'ENDED' ? t('business.detail.endedTitle') : t('business.detail.upcomingTitle') }}
                    </h2>
                    <p v-if="app.phase === 'UPCOMING'" class="m-0 text-sm text-muted-color">{{ t('business.detail.upcomingText', { date: time.serviceDate(app.startDate) }) }}</p>
                    <p v-else-if="app.phase === 'RUNNING'" class="m-0 text-sm text-muted-color">{{ t('business.detail.runningText', { date: time.serviceDate(app.endDate) }) }}</p>
                    <div v-if="app.phase !== 'ENDED' && !app.refund">
                        <Button type="button" :label="t('business.detail.refund')" icon="pi pi-replay" severity="secondary" variant="outlined" size="small" data-testid="business-refund" @click="refundOpen = true" />
                    </div>
                </template>

                <template v-else-if="app.status === 'TAKEN_DOWN'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.takenDownTitle') }}</h2>
                    <p v-if="app.takedownReason" class="m-0 whitespace-pre-line text-sm">{{ app.takedownReason }}</p>
                </template>

                <template v-else-if="app.status === 'EXPIRED'">
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.expiredTitle') }}</h2>
                    <p class="m-0 text-sm text-muted-color">{{ t('business.detail.expiredText') }}</p>
                    <div>
                        <Button as="router-link" :to="retryPath" :label="t('business.detail.newAgain')" size="small" severity="secondary" variant="outlined" />
                    </div>
                </template>

                <template v-else>
                    <h2 class="m-0 text-base font-semibold">{{ t('business.detail.canceledTitle') }}</h2>
                </template>

                <!-- 환불 요청 -->
                <div v-if="app.refund" class="flex flex-col gap-1 rounded-lg border border-hairline p-3 text-sm" data-testid="business-refund-info">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 font-semibold">
                        {{ t('business.detail.refundTitle') }}
                        <PointShopTag :tone="app.refund.status === 'REFUNDED' ? 'success' : 'warn'">{{ t(`business.detail.refundStatus.${app.refund.status}`) }}</PointShopTag>
                        <span class="text-xs font-normal text-muted-color">{{ t(`business.detail.refundType.${app.refund.type}`) }}</span>
                    </p>
                    <p v-if="app.refund.refundAmount !== null" class="m-0 tabular-nums">{{ t('business.detail.refundEstimate', { amount: UtilBusinessMessages.won(t, app.refund.refundAmount) }) }}</p>
                    <p class="m-0 whitespace-pre-line text-xs text-muted-color">{{ app.refund.reason }}</p>
                </div>
            </section>

            <!-- 신청 내용 -->
            <section class="panel-card flex flex-col gap-3 p-4" :aria-labelledby="`${uid}-info`">
                <h2 :id="`${uid}-info`" class="m-0 text-sm font-semibold">{{ t('business.detail.info') }}</h2>
                <dl class="m-0 grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                    <dt class="text-muted-color">{{ t('business.detail.kind') }}</dt>
                    <dd class="m-0">{{ kindLabel }}</dd>
                    <dt class="text-muted-color">{{ t('business.detail.period') }}</dt>
                    <dd class="m-0 tabular-nums">{{ periodLabel }}</dd>
                    <template v-if="app.placement && app.placement !== 'NONE'">
                        <dt class="text-muted-color">{{ t('business.detail.placement') }}</dt>
                        <dd class="m-0">{{ t(`business.placement.${app.placement}`) }}</dd>
                    </template>
                    <dt class="text-muted-color">{{ t('business.detail.paymentMethod') }}</dt>
                    <dd class="m-0">{{ t(`business.paymentMethod.${app.paymentMethod}`) }}</dd>
                    <dt class="text-muted-color">{{ t('business.detail.submittedAt') }}</dt>
                    <dd class="m-0 tabular-nums">{{ time.full(app.submittedAt) }}</dd>
                </dl>
                <BusinessPriceSummary v-if="app.status !== 'AWAITING_PAYMENT'" :amount="app.confirmed ?? app.estimate" :confirmed="app.confirmed !== null" :adjust-reason="app.adjustReason" />

                <!-- 소재(광고) -->
                <div v-if="app.creative && app.adType" class="flex flex-col gap-2">
                    <p class="m-0 flex items-center gap-1.5 text-sm font-semibold">
                        {{ t('business.detail.creativeTitle') }}
                        <PointShopTag tone="neutral">{{ t('business.detail.creativeVersion', { version: app.creative.version }) }}</PointShopTag>
                    </p>
                    <BusinessAdPreview :ad-type="app.adType" :image-url="app.creative.imageUrl" :link-url="app.creative.linkUrl" :alt-text="app.creative.altText ?? ''" :text="app.creative.text ?? ''" />
                    <p class="m-0 break-all text-xs text-muted-color">{{ app.creative.linkUrl }}<template v-if="app.creative.imageName"> · {{ app.creative.imageName }}</template></p>
                </div>

                <!-- 딜 정보(핫딜) -->
                <div v-if="app.deal" class="flex flex-col gap-1.5">
                    <p class="m-0 text-sm font-semibold">{{ t('business.detail.dealTitle') }}</p>
                    <p class="m-0 flex flex-wrap items-baseline gap-x-1.5 tabular-nums">
                        <span v-if="dealDiscount !== null" class="text-sm font-bold text-red-600 dark:text-red-400">{{ dealDiscount }}%</span>
                        <span class="text-base font-bold">{{ UtilBusinessMessages.won(t, app.deal.dealPrice) }}</span>
                        <span v-if="app.deal.listPrice" class="text-xs text-muted-color line-through">{{ UtilBusinessMessages.won(t, app.deal.listPrice) }}</span>
                    </p>
                    <p class="m-0 text-xs text-muted-color">
                        {{ app.deal.shopName }} · {{ t('business.deal.stock') }} {{ t(`business.deal.stockOption.${app.deal.stock}`) }}<template v-if="app.deal.endsOn"> · {{ t('business.deal.endsOn') }} {{ time.serviceDate(app.deal.endsOn) }}</template>
                    </p>
                    <a :href="app.deal.linkUrl" target="_blank" rel="sponsored nofollow noopener noreferrer" class="break-all text-xs text-primary hover:underline">{{ app.deal.linkUrl }}</a>
                    <p class="m-0 whitespace-pre-line text-sm">{{ app.deal.body }}</p>
                </div>
            </section>

            <!-- 처리 기록 -->
            <section class="panel-card p-4" :aria-labelledby="`${uid}-history`">
                <h2 :id="`${uid}-history`" class="m-0 mb-3 text-sm font-semibold">{{ t('business.detail.historyTitle') }}</h2>
                <BusinessHistoryTimeline :history="app.history" />
            </section>

            <!-- MOCK: 운영자 처리 흉내 -->
            <BusinessMockPanel v-if="mockActions.length" :title="t('business.mock.operatorTitle')" :help="t('business.mock.operatorHelp')" initially-open>
                <div class="flex flex-wrap gap-2">
                    <Button
                        v-for="action in mockActions"
                        :key="action"
                        type="button"
                        :label="t(`business.mock.op.${action}`)"
                        size="small"
                        severity="warn"
                        variant="outlined"
                        :disabled="actionBusy"
                        :data-testid="`business-mock-${action}`"
                        @click="operate(action)"
                    />
                </div>
            </BusinessMockPanel>
        </template>

        <PaymentFakeCheckoutDialog
            :visible="payOpen"
            :checkout="payTarget?.checkout ?? null"
            :expires-at="payTarget?.expiresAt ?? ''"
            :busy="payBusy"
            :error="payDialogError"
            @submit="completePay"
            @close="payOpen = false"
        />
        <BusinessRefundDialog
            v-if="app"
            :visible="refundOpen"
            :running="app.phase === 'RUNNING'"
            :payment-method="app.paymentMethod"
            :refund-amount="refundAmount"
            :busy="actionBusy"
            :error="refundError"
            @submit="requestRefund"
            @close="refundOpen = false"
        />
    </div>
</template>

<script setup lang="ts">
import type { BizApplicationDetail, BizMockOperatorAction, BizPaymentCompleteRequest, BizPaymentStart } from '~/composables/business/api/useBusinessApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBusiness from '~/utils/util-business';
import UtilBusinessMessages from '~/utils/util-business-messages';

/**
 * 신청 건 상세 `/business/applications/{id}` — 로그인 필요. 내 신청 건 목록·사업자 센터 홈의 할 일 카드·알림(결제 요청·기한 임박)이 온다.
 * 결제는 `payment` 모듈 한 길(`BIZ_APPLICATION`) — 목업은 가짜 결제창만 쓴다(실제 PG 호출 없음). ⚠ 목업 단계(`useBusinessApi`).
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
useSeoMeta({ title: () => t('business.detail.title'), description: () => t('business.detail.seoDescription'), robots: 'noindex' });

const uid = useId();
const route = useRoute();
const router = useRouter();
const api = useBusinessApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const now = useNowTicker();

const id = computed(() => String(route.params.id ?? ''));
const app = ref<BizApplicationDetail | null>(null);
const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const actionBusy = ref(false);
const justPaid = ref(false);

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        app.value = await api.application(id.value);
        status.value = 'ready';
    } catch (e) {
        app.value = null;
        loadError.value = e instanceof Error ? e.message : '';
        status.value = 'error';
    }
}

onMounted(async () => {
    await load();
    // 홈의 [결제하기]로 왔으면 결제창을 바로 연다(쿼리는 지운다 — 새로고침에 다시 열리지 않게)
    if (route.query.pay) {
        void router.replace({ query: { ...route.query, pay: undefined } });
        if (app.value?.status === 'AWAITING_PAYMENT') void startPay();
    }
});

/** 처리 상태가 이미 바뀌었으면(409) 알리고 다시 읽는다 — 그 밖의 오류는 문구를 돌려준다 */
async function handleError(e: unknown, fallback: string): Promise<string> {
    if (e instanceof ApiError && e.code === 'BIZ_APPLICATION_STATE_CHANGED') {
        toast.show(t('business.detail.stateChanged'));
        await load();
        return '';
    }
    return e instanceof Error ? e.message : fallback;
}

const kindLabel = computed(() => {
    if (!app.value) return '';
    const kind = t(`business.kind.${app.value.kind}`);
    return app.value.adType && app.value.kind === 'AD_BANNER' ? `${kind} · ${t(`business.adType.${app.value.adType}`)}` : kind;
});
const periodLabel = computed(() =>
    app.value ? UtilBusinessMessages.period(t, time.value.serviceDate(app.value.startDate), time.value.serviceDate(app.value.endDate), app.value.startDate, app.value.endDate) : '',
);
/** 결제(입금)까지 마친 건인가 — 단계 표시의 멈춘 자리 */
const paid = computed(() => Boolean(app.value?.history.some((h) => h.action === 'PAID' || h.action === 'DEPOSIT_CONFIRMED')));
const dueSoon = computed(() => Boolean(app.value?.paymentDueAt && Date.parse(app.value.paymentDueAt) - now.value < UtilBusiness.DUE_WARN_MS));
const dealDiscount = computed(() => (app.value?.deal ? UtilBusiness.discountRate(app.value.deal.listPrice, app.value.deal.dealPrice) : null));
const retryPath = computed(() => (app.value && UtilBusiness.isAd(app.value.kind) ? UtilBusiness.AD_NEW_PATH : UtilBusiness.DEAL_NEW_PATH));

/* ── 신청 취소(결제 전) ── */
async function cancel(): Promise<void> {
    if (!app.value || actionBusy.value) return;
    const ok = await confirm({ title: t('business.detail.cancelConfirmTitle'), message: t('business.detail.cancelConfirmMessage'), confirmLabel: t('business.detail.cancel'), tone: 'danger' });
    if (!ok) return;
    actionBusy.value = true;
    try {
        app.value = await api.cancel(app.value.id);
        toast.success(t('business.detail.canceled'));
    } catch (e) {
        const message = await handleError(e, t('common.failed'));
        if (message) toast.error(message, 8000);
    } finally {
        actionBusy.value = false;
    }
}

/* ── 결제(온라인) — 가짜 결제창 ── */
const payTarget = ref<BizPaymentStart | null>(null);
const payOpen = ref(false);
const payStarting = ref(false);
const payBusy = ref(false);
const payError = ref('');
const payDialogError = ref('');

async function startPay(): Promise<void> {
    if (!app.value || payStarting.value) return;
    payStarting.value = true;
    payError.value = '';
    payDialogError.value = '';
    try {
        payTarget.value = await api.startPayment(app.value.id);
        payOpen.value = true;
    } catch (e) {
        payError.value = await handleError(e, t('business.detail.payFailed'));
    } finally {
        payStarting.value = false;
    }
}

async function completePay(body: BizPaymentCompleteRequest): Promise<void> {
    if (!app.value) return;
    payBusy.value = true;
    payDialogError.value = '';
    try {
        app.value = await api.completePayment(app.value.id, { ...body });
        payOpen.value = false;
        justPaid.value = true;
        toast.success(t('business.detail.paidTitle'));
    } catch (e) {
        payDialogError.value = await handleError(e, t('business.detail.payFailed'));
        if (!payDialogError.value) payOpen.value = false;
    } finally {
        payBusy.value = false;
    }
}

/* ── 취소·환불 요청(예약·진행) ── */
const refundOpen = ref(false);
const refundError = ref('');
const refundAmount = computed(() => {
    const a = app.value;
    if (!a) return 0;
    const total = a.confirmed?.total ?? 0;
    return a.phase === 'RUNNING' ? UtilBusiness.proratedRefund(total, a.startDate, a.endDate, UtilBusiness.todayYmd(now.value)) : total;
});

async function requestRefund(reason: string): Promise<void> {
    if (!app.value) return;
    actionBusy.value = true;
    refundError.value = '';
    try {
        const result = await api.refundRequest(app.value.id, reason);
        app.value = result;
        refundOpen.value = false;
        toast.success(result.refund?.status === 'REFUNDED' ? t('business.detail.refundAutoDone') : t('business.detail.refundRequested'));
    } catch (e) {
        refundError.value = await handleError(e, t('common.failed'));
        if (!refundError.value) refundOpen.value = false;
    } finally {
        actionBusy.value = false;
    }
}

/* ── MOCK: 운영자 처리 ── */
const mockActions = computed<BizMockOperatorAction[]>(() => {
    const a = app.value;
    if (!a) return [];
    switch (a.status) {
        case 'SUBMITTED':
            return ['APPROVE_ONLINE', 'APPROVE_MANUAL', 'REJECT'];
        case 'AWAITING_PAYMENT':
            return ['NEAR_DUE'];
        case 'AWAITING_DEPOSIT':
            return ['CONTRACT_AND_DEPOSIT'];
        case 'SCHEDULED':
            return a.phase === 'UPCOMING' ? ['START'] : a.phase === 'RUNNING' ? ['END'] : [];
        default:
            return [];
    }
});

async function operate(action: BizMockOperatorAction): Promise<void> {
    if (!app.value) return;
    actionBusy.value = true;
    try {
        app.value = await api.mockOperate(app.value.id, action);
        justPaid.value = false;
        toast.show(t('business.mock.done'));
    } catch (e) {
        toast.error(e instanceof Error ? e.message : t('common.failed'), 8000);
    } finally {
        actionBusy.value = false;
    }
}
</script>
