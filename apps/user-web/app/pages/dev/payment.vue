<template>
    <!--
        시험 결제 (개발 모드 전용 — `/dev/payment`). 운영 빌드·`generate` 에서는 404 다(아래 라우트 미들웨어). 계약 commerce-payment 결정 7·9.
        충전·공동구매 화면이 아직 없어서, 결제 모듈만 시험 주문(`PAYMENT_TEST`)으로 끝까지 눌러 본다. ⚠ 목업 단계(`usePaymentTestApi`·`usePaymentApi` — 서버는 2a-2).
        1. 시험 주문 만들기 — 금액(100~1,000,000원)·유효 시간(1~60분) → 서버가 결제 번호·결제창 정보를 준다.
        2. 결제창 — 게이트웨이가 가짜면 `PaymentFakeCheckoutDialog`(정상 · 실패 · 금액 바꿔 결제 · 늦게 결제). 포트원이면 브라우저 SDK `requestPayment`(KG이니시스 V2 테스트 채널, 카드)로 실제 테스트 결제창을 연다.
           결제했으면 완료 요청(`complete`)을 보낸다 — 금액·상태는 서버가 PG 에 다시 물어 정한다(SDK 응답의 금액은 믿지 않는다). 실패·창 닫기(SDK 응답 `code`)는 완료 요청을 보내지 않는다.
           모바일은 결제사 화면으로 갔다가 `redirectUrl`(이 화면)로 돌아온다 — 떠나기 전 시험 주문을 sessionStorage 에 두고, 돌아온 쿼리 `paymentId`(실패면 `code`·`message`)가 같을 때만 복원해 이어 간 뒤 쿼리를 지운다.
        3. 결과 — 상태 · 받아들였나 · 이상(금액 불일치·늦은 결제) · 자동 취소 진행. [결과 다시 받기]로 자동 취소가 끝났는지 본다. 같은 결제는 관리자 › 결제 › 결제 주문에서도 보인다.
    -->
    <div class="mx-auto flex max-w-3xl flex-col gap-8" data-testid="dev-payment">
        <header>
            <p class="m-0 text-xs font-semibold text-muted-color">{{ t('payment.devBadge') }}</p>
            <h1 class="m-0 mt-0.5 text-lg font-semibold">{{ t('payment.devTitle') }}</h1>
            <p class="m-0 mt-1 text-sm leading-relaxed text-muted-color">{{ t('payment.devIntro') }}</p>
        </header>

        <!-- 1. 시험 주문 만들기 -->
        <section :aria-labelledby="`${uid}-create`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-create`" class="m-0 mb-1 text-base font-semibold">{{ t('payment.devCreateTitle') }}</h2>
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('payment.devCreateIntro') }}</p>
            <form class="flex flex-col gap-3" novalidate @submit.prevent="create">
                <div class="grid gap-3 sm:grid-cols-2">
                    <LabeledField :label="t('payment.devAmount')" :hint="t('payment.devAmountHint')" :error="fieldErrors.amount">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="amount" :input-id="id" :min="100" :max="1_000_000" :step="100" :suffix="t('payment.wonSuffix')" :invalid="invalid" :aria-describedby="describedBy" :disabled="createBusy" fluid data-testid="dev-payment-amount" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('payment.devValidMinutes')" :hint="t('payment.devValidMinutesHint')" :error="fieldErrors.validMinutes">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="validMinutes" :input-id="id" :min="1" :max="60" :suffix="t('payment.minuteSuffix')" :invalid="invalid" :aria-describedby="describedBy" :disabled="createBusy" fluid data-testid="dev-payment-minutes" />
                        </template>
                    </LabeledField>
                </div>
                <div class="flex flex-wrap items-center gap-2">
                    <Button type="submit" :label="order ? t('payment.devCreateAgain') : t('payment.devCreate')" icon="pi pi-plus" :loading="createBusy" data-testid="dev-payment-create" />
                    <Message v-if="createFailure" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ createFailure }}</Message>
                </div>
            </form>
        </section>

        <!-- 2. 결제창 -->
        <section v-if="order" :aria-labelledby="`${uid}-checkout`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900">
            <h2 :id="`${uid}-checkout`" class="m-0 mb-3 text-base font-semibold">{{ t('payment.devCheckoutTitle') }}</h2>
            <dl class="m-0 grid grid-cols-[7rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                <dt class="text-muted-color">{{ t('payment.paymentId') }}</dt>
                <dd class="m-0 min-w-0"><code class="text-xs [overflow-wrap:anywhere]" data-testid="dev-payment-id">{{ order.checkout.paymentId }}</code></dd>
                <dt class="text-muted-color">{{ t('payment.amount') }}</dt>
                <dd class="m-0 font-semibold tabular-nums">{{ t('payment.won', { amount: order.checkout.amount.toLocaleString(locale) }) }}</dd>
                <dt class="text-muted-color">{{ t('payment.expiresAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(order.expiresAt) }}</dd>
                <dt class="text-muted-color">{{ t('payment.gateway') }}</dt>
                <dd class="m-0">
                    <Tag :value="t(`payment.gatewayName.${order.checkout.gateway}`)" :severity="order.checkout.gateway === 'FAKE' ? 'warn' : 'info'" />
                </dd>
            </dl>
            <Message v-if="order.checkout.gateway === 'PORTONE'" severity="info" size="small" :closable="false" class="mt-3">{{ t('payment.devPortone') }}</Message>
            <div class="mt-3 flex flex-wrap items-center gap-2">
                <Button
                    type="button"
                    :label="t('payment.devOpenCheckout')"
                    icon="pi pi-credit-card"
                    :disabled="result !== null || completeBusy || portoneBusy"
                    :loading="completeBusy || portoneBusy"
                    data-testid="dev-payment-open"
                    @click="openCheckout"
                />
                <span v-if="note" class="text-sm text-muted-color" aria-live="polite" data-testid="dev-payment-note">
                    <i class="pi pi-info-circle text-xs" aria-hidden="true" /> {{ t(`payment.devNote.${note}`) }}
                    <template v-if="noteDetail">({{ noteDetail }})</template>
                </span>
            </div>
            <Message v-if="completeError" severity="error" size="small" :closable="false" class="mt-3" role="alert">{{ completeError }}</Message>
        </section>

        <!-- 3. 결과 -->
        <section v-if="result" :aria-labelledby="`${uid}-result`" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" data-testid="dev-payment-result">
            <div class="mb-3 flex flex-wrap items-center gap-2">
                <h2 :id="`${uid}-result`" class="m-0 text-base font-semibold">{{ t('payment.devResultTitle') }}</h2>
                <Tag :value="t(`payment.status.${result.status}`)" :severity="result.accepted ? 'success' : result.status === 'CANCELED' ? 'secondary' : 'warn'" />
                <Button type="button" class="ml-auto" :label="t('payment.devReload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="resultBusy" @click="reloadResult" />
            </div>
            <p class="m-0 flex items-start gap-1.5 text-sm" aria-live="polite">
                <i :class="result.accepted ? 'pi pi-check-circle text-green-700 dark:text-green-400' : 'pi pi-exclamation-triangle text-orange-600 dark:text-orange-400'" class="mt-0.5" aria-hidden="true" />
                <span>{{ result.accepted ? t('payment.devAccepted') : result.anomaly ? t(`payment.devAnomaly.${result.anomaly}`) : t('payment.devNotAccepted') }}</span>
            </p>
            <dl class="m-0 mt-3 grid grid-cols-[7rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                <dt class="text-muted-color">{{ t('payment.amount') }}</dt>
                <dd class="m-0 tabular-nums">{{ t('payment.won', { amount: result.amount.toLocaleString(locale) }) }}</dd>
                <dt class="text-muted-color">{{ t('payment.paidAmount') }}</dt>
                <dd class="m-0 tabular-nums" :class="result.paidAmount !== null && result.paidAmount !== result.amount ? 'text-red-600 dark:text-red-400' : ''">
                    {{ result.paidAmount === null ? '—' : t('payment.won', { amount: result.paidAmount.toLocaleString(locale) }) }}
                </dd>
                <dt class="text-muted-color">{{ t('payment.paidAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ result.paidAt ? time.full(result.paidAt) : '—' }}</dd>
                <template v-if="result.cancel">
                    <dt class="text-muted-color">{{ t('payment.autoCancel') }}</dt>
                    <dd class="m-0">
                        {{ t('payment.won', { amount: result.cancel.amount.toLocaleString(locale) }) }} ·
                        <span :class="result.cancel.status === 'SUCCEEDED' ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">{{ t(`payment.cancelStatus.${result.cancel.status}`) }}</span>
                    </dd>
                </template>
            </dl>
            <p class="m-0 mt-3 text-xs text-muted-color">{{ t('payment.devAdminHint') }}</p>
        </section>

        <PaymentFakeCheckoutDialog
            :visible="checkoutOpen"
            :checkout="order?.checkout ?? null"
            :expires-at="order?.expiresAt ?? ''"
            :busy="checkoutBusy"
            :error="checkoutError"
            @submit="onFakeSubmit"
            @close="onFakeClose"
        />
    </div>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { PaymentResult, PaymentTestOrder } from '~/composables/payment/api/usePaymentApi.types';

/**
 * 시험 결제 `/dev/payment` — 개발 모드에서만 열린다(운영 빌드는 404). 서버도 `roboyam.payment.test-orders` 를 켠 환경(local·dev)에서만 시험 주문을 받는다.
 * 로그인해야 한다(`auth` 미들웨어 — 결제는 회원의 것).
 * 결제창 열기·포트원 SDK·창 닫기 판정·모바일 리디렉션 복원·완료 요청은 공용 `usePaymentCheckout` 이 한다(공구 주문서와 같은 흐름).
 */
definePageMeta({
    middleware: [
        () => {
            if (!import.meta.dev) return abortNavigation(createError({ statusCode: 404, statusMessage: 'Page Not Found' }));
        },
        'auth',
    ],
});

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('payment.devTitle'), robots: 'noindex' });

const uid = useId();
const time = useDisplayTime();
const testApi = usePaymentTestApi();

/* ── 결제창(공용 흐름) ── */

const pay = usePaymentCheckout<PaymentTestOrder>({
    /** 모바일 리디렉션으로 이 화면을 떠났다 돌아올 때 이어 갈 시험 주문(sessionStorage — 이 탭에만) */
    pendingKey: 'roboyam-dev-payment-pending',
    redirectPath: () => '/dev/payment',
    // 개발 시험용 고정값 — 충전·공구 화면은 회원 정보에서 받는다(KG이니시스는 구매자 이름·전화·이메일을 요구한다)
    customer: () => ({ fullName: '시험회원', phoneNumber: '01000000000', email: 'test@example.com' }),
    fakeCheckout: (paymentId, body) => testApi.fakeCheckout(paymentId, body),
    missingConfigMessage: () => t('payment.devPortoneMissing'),
});
const {
    target: order,
    result,
    completeBusy,
    completeError,
    portoneBusy,
    note,
    noteDetail,
    fakeOpen: checkoutOpen,
    fakeBusy: checkoutBusy,
    fakeError: checkoutError,
    submitFake: onFakeSubmit,
    closeFake: onFakeClose,
} = pay;

/* ── 1. 시험 주문 ── */

const amount = ref<number | null>(1000);
const validMinutes = ref<number | null>(10);
const createBusy = ref(false);
const createFailure = ref('');
const fieldErrors = ref<Record<string, string>>({});

async function create(): Promise<void> {
    if (createBusy.value) return;
    createBusy.value = true;
    createFailure.value = '';
    fieldErrors.value = {};
    try {
        const made = await testApi.create({ amount: amount.value ?? 0, validMinutes: validMinutes.value ?? 0 });
        pay.reset(made);
    } catch (e) {
        if (e instanceof ApiError && e.status === 400 && e.fields && Object.keys(e.fields).length) fieldErrors.value = e.fields;
        else createFailure.value = e instanceof Error ? e.message : String(e);
    } finally {
        createBusy.value = false;
    }
}

/* ── 2. 결제창 ── */

function openCheckout(): void {
    pay.open();
}

onMounted(() => {
    void pay.resumeRedirect();
});

/* ── 3. 결과 ── */

const resultBusy = ref(false);

async function reloadResult(): Promise<void> {
    const current = order.value;
    if (!current || resultBusy.value) return;
    resultBusy.value = true;
    try {
        result.value = (await testApi.result(current.checkout.paymentId)) satisfies PaymentResult;
    } catch (e) {
        completeError.value = e instanceof Error ? e.message : String(e);
    } finally {
        resultBusy.value = false;
    }
}
</script>
