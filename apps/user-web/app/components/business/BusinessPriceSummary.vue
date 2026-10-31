<template>
    <!-- 금액 내역 — 공급가 · 부가세 10% · 합계. 예상 금액이면 「최종 금액은 심사 때 확정돼요」(광고·핫딜 신청 마지막 단계, 신청 건 상세) -->
    <section class="rounded-lg border border-hairline p-3.5" :aria-labelledby="titleId" data-testid="business-price-summary">
        <h3 :id="titleId" class="m-0 mb-2 text-sm font-semibold">{{ confirmed ? t('business.price.confirmedTitle') : t('business.price.title') }}</h3>
        <p v-for="line in breakdown" :key="line" class="m-0 mb-1 text-xs tabular-nums text-muted-color">{{ line }}</p>
        <dl class="m-0 grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1 text-sm">
            <dt class="text-muted-color">{{ t('business.price.supply') }}</dt>
            <dd class="m-0 text-right tabular-nums">{{ UtilBusinessMessages.won(t, amount.supply) }}</dd>
            <dt class="text-muted-color">{{ t('business.price.vat') }}</dt>
            <dd class="m-0 text-right tabular-nums">{{ UtilBusinessMessages.won(t, amount.vat) }}</dd>
            <dt class="border-t border-hairline pt-1.5 font-semibold">{{ t('business.price.total') }}</dt>
            <dd class="m-0 border-t border-hairline pt-1.5 text-right text-base font-bold tabular-nums">{{ UtilBusinessMessages.won(t, amount.total) }}</dd>
        </dl>
        <p v-if="adjustReason" class="m-0 mt-2 text-xs text-muted-color">{{ t('business.price.adjusted', { reason: adjustReason }) }}</p>
        <p v-if="!confirmed" class="m-0 mt-2 flex items-start gap-1.5 text-xs text-muted-color">
            <i class="pi pi-info-circle mt-0.5 text-[0.6875rem]" aria-hidden="true" />{{ t('business.price.finalNote') }}
        </p>
    </section>
</template>

<script setup lang="ts">
import type { BizAmount } from '~/composables/business/api/useBusinessApi.types';
import UtilBusinessMessages from '~/utils/util-business-messages';

interface Props {
    amount: BizAmount;
    /** 승인 때 확정한 금액인가 — 아니면 예상 금액 안내를 붙인다 */
    confirmed?: boolean;
    /** 계산 줄(「7일 × 50,000원」) */
    breakdown?: string[];
    adjustReason?: string | null;
}

withDefaults(defineProps<Props>(), { confirmed: false, breakdown: () => [], adjustReason: null });

const { t } = useI18n();
const titleId = useId();
</script>
