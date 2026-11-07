<template>
    <!-- 결제 방식 고르기(★-2) — 온라인 결제 / 계좌이체·계약(수동). 운영자가 승인 때 바꿀 수 있다. 광고·핫딜 신청이 같이 쓴다.
         온라인 결제를 받지 않으면(`online-available` false — 운영 주체가 개인 단계) 온라인 칸을 막고 한 줄로 알린다 -->
    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" data-testid="business-payment-method">
        <legend class="mb-2 text-sm font-semibold">{{ t('business.ad.paymentTitle') }}</legend>
        <label
            v-for="method in METHODS"
            :key="method"
            class="flex items-start gap-3 rounded-lg border p-3.5 transition-colors"
            :class="[
                off(method) ? 'cursor-not-allowed opacity-50' : 'cursor-pointer',
                model === method ? 'border-primary bg-(--p-highlight-background)' : 'border-hairline',
                !off(method) && model !== method ? 'hover:bg-(--p-content-hover-background)' : '',
            ]"
        >
            <RadioButton v-model="model" :value="method" :input-id="`${uid}-${method}`" :name="uid" :disabled="off(method)" class="mt-0.5" />
            <span class="min-w-0">
                <span class="flex items-center gap-1.5 text-sm font-semibold">
                    <i :class="[method === 'ONLINE' ? 'pi pi-credit-card' : 'pi pi-building-columns', 'text-xs text-muted-color']" aria-hidden="true" />{{ t(`business.paymentMethod.${method}`) }}
                </span>
                <span class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ off(method) ? t('business.paymentOnlineOff') : t(`business.paymentMethodHelp.${method}`) }}</span>
            </span>
        </label>
        <p class="m-0 mt-1 flex items-start gap-1.5 text-xs font-medium text-primary">
            <i class="pi pi-check-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('business.ad.noPayNow') }}
        </p>
    </fieldset>
</template>

<script setup lang="ts">
import type { BizPaymentMethod } from '~/composables/business/api/useBusinessApi.types';

const model = defineModel<BizPaymentMethod>({ required: true });

interface Props {
    /** 온라인 결제를 받는가(상품 응답의 `onlinePayment`) — false 면 온라인 칸을 막는다 */
    onlineAvailable?: boolean;
}
const props = withDefaults(defineProps<Props>(), { onlineAvailable: true });

/** 고를 수 없는 방식인가 */
function off(method: BizPaymentMethod): boolean {
    return method === 'ONLINE' && !props.onlineAvailable;
}

// 온라인을 못 고르면 수동으로 옮겨 둔다
watch(
    () => props.onlineAvailable,
    (on) => {
        if (!on && model.value === 'ONLINE') model.value = 'MANUAL';
    },
    { immediate: true },
);

const METHODS: readonly BizPaymentMethod[] = ['ONLINE', 'MANUAL'];

const { t } = useI18n();
const uid = useId();
</script>
