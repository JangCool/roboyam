<template>
    <!-- 결제 방식 고르기(★-2) — 온라인 결제 / 계좌이체·계약(수동). 운영자가 승인 때 바꿀 수 있다. 광고·핫딜 신청이 같이 쓴다 -->
    <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" data-testid="business-payment-method">
        <legend class="mb-2 text-sm font-semibold">{{ t('business.ad.paymentTitle') }}</legend>
        <label
            v-for="method in METHODS"
            :key="method"
            class="flex cursor-pointer items-start gap-3 rounded-lg border p-3.5 transition-colors"
            :class="model === method ? 'border-primary bg-(--p-highlight-background)' : 'border-hairline hover:bg-(--p-content-hover-background)'"
        >
            <RadioButton v-model="model" :value="method" :input-id="`${uid}-${method}`" :name="uid" class="mt-0.5" />
            <span class="min-w-0">
                <span class="flex items-center gap-1.5 text-sm font-semibold">
                    <i :class="[method === 'ONLINE' ? 'pi pi-credit-card' : 'pi pi-building-columns', 'text-xs text-muted-color']" aria-hidden="true" />{{ t(`business.paymentMethod.${method}`) }}
                </span>
                <span class="mt-0.5 block text-xs leading-relaxed text-muted-color">{{ t(`business.paymentMethodHelp.${method}`) }}</span>
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

const METHODS: readonly BizPaymentMethod[] = ['ONLINE', 'MANUAL'];

const { t } = useI18n();
const uid = useId();
</script>
