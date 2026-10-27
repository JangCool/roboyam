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
import type { PaymentFakeCheckoutRequest, PaymentResult, PaymentTestOrder } from '~/composables/payment/api/usePaymentApi.types';
import UtilPortone from '~/utils/util-portone';

/**
 * 시험 결제 `/dev/payment` — 개발 모드에서만 열린다(운영 빌드는 404). 서버도 `roboyam.payment.test-orders` 를 켠 환경(local·dev)에서만 시험 주문을 받는다.
 * 로그인해야 한다(`auth` 미들웨어 — 결제는 회원의 것).
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
const paymentApi = usePaymentApi();

/* ── 1. 시험 주문 ── */

const amount = ref<number | null>(1000);
const validMinutes = ref<number | null>(10);
const createBusy = ref(false);
const createFailure = ref('');
const fieldErrors = ref<Record<string, string>>({});
const order = ref<PaymentTestOrder | null>(null);

async function create(): Promise<void> {
    if (createBusy.value) return;
    createBusy.value = true;
    createFailure.value = '';
    fieldErrors.value = {};
    try {
        const made = await testApi.create({ amount: amount.value ?? 0, validMinutes: validMinutes.value ?? 0 });
        order.value = made;
        result.value = null;
        note.value = null;
        noteDetail.value = '';
        completeError.value = '';
    } catch (e) {
        if (e instanceof ApiError && e.status === 400 && e.fields && Object.keys(e.fields).length) fieldErrors.value = e.fields;
        else createFailure.value = e instanceof Error ? e.message : String(e);
    } finally {
        createBusy.value = false;
    }
}

/* ── 2. 결제창 ── */

const checkoutOpen = ref(false);
const checkoutBusy = ref(false);
const checkoutError = ref('');
const completeBusy = ref(false);
const completeError = ref('');
/** 완료 요청을 보내지 않고 끝난 까닭 — 가짜 결제창 실패(다시 열 수 없음) · 창 닫기 · 포트원 실패(같은 결제 번호로 다시 열 수 있음) */
const note = ref<'FAILED' | 'CLOSED' | 'PG_FAILED' | null>(null);
/** 포트원이 준 실패 문구와 코드(`code`·`pgCode` — 개발 전용 화면이라 그대로 보여 PG 마다 다른 취소 코드를 확인한다) */
const noteDetail = ref('');
/** 포트원 결제창이 열려 있는 동안 */
const portoneBusy = ref(false);

function openCheckout(): void {
    const target = order.value;
    if (!target || result.value !== null || completeBusy.value || portoneBusy.value) return;
    note.value = null;
    noteDetail.value = '';
    completeError.value = '';
    if (target.checkout.gateway === 'PORTONE') {
        void openPortone(target);
        return;
    }
    checkoutError.value = '';
    checkoutOpen.value = true;
}

/* ── 포트원 결제창(브라우저 SDK) ── */

/** 모바일 리디렉션으로 이 화면을 떠났다 돌아올 때 이어 갈 시험 주문(sessionStorage — 이 탭에만) */
const PENDING_KEY = 'roboyam-dev-payment-pending';
/** 리디렉션으로 돌아올 때 포트원이 붙이는 쿼리 — 처리한 뒤 지운다(`?mock=` 같은 다른 쿼리는 둔다) */
const REDIRECT_QUERY = ['paymentId', 'code', 'message', 'pgCode', 'pgMessage', 'transactionType', 'txId'];

const route = useRoute();
const router = useRouter();

function savePending(target: PaymentTestOrder): void {
    try {
        window.sessionStorage.setItem(PENDING_KEY, JSON.stringify(target));
    } catch {
        // 저장할 수 없는 환경(사생활 보호 창 등) — 팝업 결제는 그대로 되고, 리디렉션에서 돌아오면 이어 가지 못할 뿐이다
    }
}

function takePending(): PaymentTestOrder | null {
    try {
        const raw = window.sessionStorage.getItem(PENDING_KEY);
        window.sessionStorage.removeItem(PENDING_KEY);
        const found = raw ? (JSON.parse(raw) as PaymentTestOrder) : null;
        return found?.checkout?.paymentId && found.orderRefId ? found : null;
    } catch {
        return null;
    }
}

/** SDK 응답의 실패 — 사용자가 닫았으면 CLOSED, 아니면 PG_FAILED. 완료 요청은 보내지 않는다 */
function settleFailure(code: string, message: string | undefined, pgCode: string | undefined): void {
    note.value = UtilPortone.isUserCancel(code, message) ? 'CLOSED' : 'PG_FAILED';
    noteDetail.value = [message, `code ${code}`, pgCode ? `pgCode ${pgCode}` : ''].filter(Boolean).join(' · ');
}

async function openPortone(target: PaymentTestOrder): Promise<void> {
    const { storeId, channelKey, paymentId, orderName, amount } = target.checkout;
    if (!import.meta.client) return;
    if (!storeId || !channelKey) {
        completeError.value = t('payment.devPortoneMissing');
        return;
    }
    portoneBusy.value = true;
    savePending(target);
    let response;
    try {
        const { default: PortOne } = await import('@portone/browser-sdk/v2');
        response = await PortOne.requestPayment({
            storeId,
            channelKey,
            paymentId,
            orderName,
            totalAmount: amount,
            currency: 'KRW',
            payMethod: 'CARD',
            // 개발 시험용 고정값 — 충전·공구 화면은 회원 정보에서 받는다(KG이니시스는 구매자 이름·전화·이메일을 요구한다)
            customer: { fullName: '시험회원', phoneNumber: '01000000000', email: 'test@example.com' },
            redirectUrl: new URL(router.resolve({ path: '/dev/payment' }).href, window.location.origin).href,
        });
    } catch (e) {
        takePending();
        completeError.value = e instanceof Error ? e.message : String(e);
        return;
    } finally {
        portoneBusy.value = false;
    }
    // 리디렉션된 경우 — 이 화면을 떠난다. 돌아오면 `resumeRedirect` 가 이어 간다
    if (response === undefined) return;
    takePending();
    if (response.code !== undefined) {
        settleFailure(response.code, response.message, response.pgCode);
        return;
    }
    // 금액·상태는 서버가 PG 에 다시 물어 정한다 — 응답의 값은 쓰지 않는다
    await complete(target);
}

/** 모바일 리디렉션에서 돌아왔다 — 떠나기 전 둔 시험 주문과 결제 번호가 같을 때만 이어 간다 */
async function resumeRedirect(): Promise<void> {
    const query = route.query;
    const paymentId = typeof query.paymentId === 'string' ? query.paymentId : null;
    if (!paymentId) return;
    const code = typeof query.code === 'string' && query.code ? query.code : null;
    const message = typeof query.message === 'string' ? query.message : undefined;
    const pgCode = typeof query.pgCode === 'string' && query.pgCode ? query.pgCode : undefined;
    const pending = takePending();
    const rest = { ...query };
    for (const key of REDIRECT_QUERY) delete rest[key];
    await router.replace({ query: rest });
    if (!pending || pending.checkout.paymentId !== paymentId) return;
    order.value = pending;
    result.value = null;
    if (code) {
        settleFailure(code, message, pgCode);
        return;
    }
    await complete(pending);
}

onMounted(() => {
    void resumeRedirect();
});

function onFakeClose(): void {
    if (checkoutBusy.value) return;
    checkoutOpen.value = false;
    note.value = 'CLOSED';
}

async function onFakeSubmit(body: PaymentFakeCheckoutRequest): Promise<void> {
    const target = order.value;
    if (!target || checkoutBusy.value) return;
    checkoutBusy.value = true;
    checkoutError.value = '';
    try {
        await testApi.fakeCheckout(target.checkout.paymentId, body);
    } catch (e) {
        checkoutError.value = e instanceof Error ? e.message : String(e);
        checkoutBusy.value = false;
        return;
    }
    checkoutBusy.value = false;
    checkoutOpen.value = false;
    if (body.outcome === 'FAILED') {
        note.value = 'FAILED';
        return;
    }
    await complete(target);
}

/** 완료 요청 — 결제창이 끝난 뒤 서버가 PG 에 다시 물어 확정한다 */
async function complete(target: PaymentTestOrder): Promise<void> {
    completeBusy.value = true;
    completeError.value = '';
    try {
        result.value = await paymentApi.complete(target.checkout.paymentId, { orderType: target.orderType, orderRefId: target.orderRefId });
    } catch (e) {
        completeError.value = e instanceof Error ? e.message : String(e);
    } finally {
        completeBusy.value = false;
    }
}

/* ── 3. 결과 ── */

const result = ref<PaymentResult | null>(null);
const resultBusy = ref(false);

async function reloadResult(): Promise<void> {
    const target = order.value;
    if (!target || resultBusy.value) return;
    resultBusy.value = true;
    try {
        result.value = await testApi.result(target.checkout.paymentId);
    } catch (e) {
        completeError.value = e instanceof Error ? e.message : String(e);
    } finally {
        resultBusy.value = false;
    }
}
</script>
