<template>
    <!--
        가짜 결제창(개발용) — 서버 게이트웨이가 가짜(`checkout.gateway === 'FAKE'`, local·시험 전용)일 때 포트원 결제창 대신 띄운다. 돈은 오가지 않는다.
        위에서 아래로: 「가짜 결제창」 경고 · 주문 이름 · 결제 금액 · 유효 시각 · 결과 고르기(정상 결제 · 결제 실패 · 금액 바꿔 결제(금액 칸) · 늦게 결제(유효 시각 1분 뒤로)).
        [결제하기]는 고른 결과를 `submit` 으로 올리고(부르는 쪽이 가짜 PG 에 남긴 뒤 완료 요청을 보낸다), [닫기]·Esc·바깥 누름은 회원이 결제창을 닫은 것과 같다(`close`).
        서버는 부르지 않는다(`components.md`). 지금은 `/dev/payment` 하나가 쓰고, 충전(3단계)·공구(2b) 화면도 가짜 게이트웨이에서 이 창을 쓴다.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :closable="!busy"
        :close-on-escape="!busy"
        :header="t('payment.fakeTitle')"
        :style="{ width: 'min(28rem, calc(100vw - 2rem))' }"
        data-testid="payment-fake-dialog"
        @update:visible="(v: boolean) => !v && emit('close')"
    >
        <form v-if="checkout" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="warn" size="small" :closable="false">{{ t('payment.fakeNotice') }}</Message>

            <dl class="m-0 grid grid-cols-[6rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                <dt class="text-muted-color">{{ t('payment.orderName') }}</dt>
                <dd class="m-0 min-w-0">{{ checkout.orderName }}</dd>
                <dt class="text-muted-color">{{ t('payment.amount') }}</dt>
                <dd class="m-0 text-base font-semibold tabular-nums">{{ t('payment.won', { amount: checkout.amount.toLocaleString(locale) }) }}</dd>
                <dt class="text-muted-color">{{ t('payment.expiresAt') }}</dt>
                <dd class="m-0 tabular-nums">{{ time.full(expiresAt) }}</dd>
            </dl>

            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="mb-1 text-sm font-medium">{{ t('payment.fakeOutcome') }}</legend>
                <label v-for="item in options" :key="item.value" class="flex cursor-pointer items-start gap-2 text-sm">
                    <RadioButton v-model="outcome" :value="item.value" :input-id="`${formId}-${item.value}`" name="fake-outcome" class="mt-0.5" :disabled="busy" />
                    <span class="min-w-0">
                        <span class="block">{{ item.label }}</span>
                        <span class="block text-xs text-muted-color">{{ item.help }}</span>
                    </span>
                </label>
            </fieldset>

            <LabeledField v-if="outcome === 'AMOUNT'" :label="t('payment.fakeAmount')" :help="t('payment.fakeAmountHelp', { amount: checkout.amount.toLocaleString(locale) })" :error="amountError">
                <template #default="{ id, describedBy, invalid }">
                    <InputNumber v-model="otherAmount" :input-id="id" :min="1" :max="10_000_000" :use-grouping="true" :suffix="t('payment.wonSuffix')" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('payment.fakeClose')" severity="secondary" variant="text" :disabled="busy" @click="emit('close')" />
            <Button type="submit" :form="formId" :label="t('payment.fakePay')" :loading="busy" data-testid="payment-fake-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { PaymentCheckout, PaymentFakeCheckoutRequest } from '~/composables/payment/api/usePaymentApi.types';

type FakeOutcome = 'PAID' | 'FAILED' | 'AMOUNT' | 'LATE';

interface Props {
    visible: boolean;
    checkout: PaymentCheckout | null;
    /** 결제 유효 시각 */
    expiresAt: string;
    /** 보내는 중 — 고르기·단추가 잠긴다 */
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '' });
const emit = defineEmits<{ submit: [body: PaymentFakeCheckoutRequest]; close: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const formId = useId();

const outcome = ref<FakeOutcome>('PAID');
const otherAmount = ref<number | null>(null);
const tried = ref(false);

const options = computed(() =>
    (['PAID', 'FAILED', 'AMOUNT', 'LATE'] as const).map((value) => ({ value, label: t(`payment.fakeOption.${value}`), help: t(`payment.fakeOptionHelp.${value}`) })),
);

/** 열 때마다 「정상 결제」로 — 금액 바꾸기 칸은 주문 금액의 절반으로 채운다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        outcome.value = 'PAID';
        otherAmount.value = props.checkout ? Math.max(1, Math.floor(props.checkout.amount / 2)) : null;
        tried.value = false;
    },
    { immediate: true },
);

const amountError = computed(() => {
    if (!tried.value || outcome.value !== 'AMOUNT') return '';
    if (!otherAmount.value || otherAmount.value < 1) return t('payment.fakeAmountRequired');
    if (otherAmount.value === props.checkout?.amount) return t('payment.fakeAmountSame');
    return '';
});

function submit(): void {
    tried.value = true;
    const checkout = props.checkout;
    if (!checkout || props.busy || amountError.value) return;
    if (outcome.value === 'FAILED') emit('submit', { outcome: 'FAILED', amount: checkout.amount, late: false });
    else emit('submit', { outcome: 'PAID', amount: outcome.value === 'AMOUNT' ? (otherAmount.value ?? checkout.amount) : checkout.amount, late: outcome.value === 'LATE' });
}
</script>
