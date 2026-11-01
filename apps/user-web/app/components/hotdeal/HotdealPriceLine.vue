<template>
    <!-- 가격 줄 — 할인율(있을 때) · 딜가 굵게 · 정상가 취소선 · 무료배송(선택). 목록 줄·카드·갤러리·가젯·글 보기 딜 카드가 같이 쓴다 -->
    <span class="inline-flex min-w-0 flex-wrap items-baseline gap-x-1.5" :class="SIZE[size].wrap" data-testid="hotdeal-price">
        <span v-if="deal.discountRate !== null" class="font-bold text-red-600 tabular-nums dark:text-red-400" :class="SIZE[size].rate">
            <span class="sr-only">{{ t('hotdeal.discountAria') }}</span>{{ t('hotdeal.discount', { n: deal.discountRate }) }}
        </span>
        <span class="font-bold tabular-nums" :class="SIZE[size].deal">
            <span class="sr-only">{{ t('hotdeal.dealPriceAria') }}</span>{{ UtilHotdealMessages.won(t, deal.dealPrice) }}
        </span>
        <s v-if="deal.normalPrice !== null && deal.discountRate !== null" class="text-muted-color tabular-nums" :class="SIZE[size].normal">
            <span class="sr-only">{{ t('hotdeal.normalPriceAria') }}</span>{{ UtilHotdealMessages.won(t, deal.normalPrice) }}
        </s>
        <span v-if="shipping && deal.shippingFee === 0" class="text-xs font-medium text-primary">{{ t('hotdeal.shippingFree') }}</span>
    </span>
</template>

<script setup lang="ts">
import type { PostDeal } from '~/composables/hotdeal/api/useHotdealApi.types';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

interface Props {
    deal: Pick<PostDeal, 'dealPrice' | 'normalPrice' | 'discountRate' | 'shippingFee'>;
    /** `sm` 목록·가젯 · `md` 카드 · `lg` 글 보기 딜 카드 */
    size?: 'sm' | 'md' | 'lg';
    /** 무료배송이면 「무료배송」을 붙인다 */
    shipping?: boolean;
}

withDefaults(defineProps<Props>(), { size: 'sm', shipping: false });

const SIZE = {
    sm: { wrap: 'text-sm', rate: 'text-sm', deal: 'text-sm', normal: 'text-xs' },
    md: { wrap: 'text-sm', rate: 'text-[0.9375rem]', deal: 'text-[0.9375rem]', normal: 'text-xs' },
    lg: { wrap: 'text-base', rate: 'text-xl', deal: 'text-xl', normal: 'text-sm' },
} as const;

const { t } = useI18n();
</script>
