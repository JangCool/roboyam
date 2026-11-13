<template>
    <!--
        예상 금액 옆 요금 예약 변경 안내 — 고른 상품의 단가가 바뀔 예정일 때(광고·핫딜 신청의 결제·확인 단계, `BusinessPriceSummary` 바로 아래).
        바뀌는 단가 줄(「넓은 배너: 하루 50,000원 → 하루 60,000원 (올라요)」) + 「지금 신청하면 지금 가격 … {시각} 이후 신청부터 새 가격」.
    -->
    <section class="flex items-start gap-2.5 rounded-lg border border-hairline p-3.5" :aria-labelledby="titleId" data-testid="business-price-change-notice">
        <i class="pi pi-clock mt-0.5 text-sm text-primary" aria-hidden="true" />
        <div class="flex min-w-0 flex-col gap-1">
            <h3 :id="titleId" class="m-0 text-sm font-semibold">{{ t('business.priceChange.summaryTitle') }}</h3>
            <p v-for="line in summary.lines" :key="line" class="m-0 text-xs font-medium tabular-nums">{{ line }}</p>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ summary.text }}</p>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { BizPriceChangeSummary } from '~/utils/util-business-messages';

interface Props {
    summary: BizPriceChangeSummary;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const titleId = useId();
</script>
