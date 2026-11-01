<template>
    <!-- 가격 줄 — 딜가 굵게 · 정상가 취소선 · 할인율(계약 「목록은 가격 줄」과 같은 차례). 할인율은 서버가 계산한 값만 보인다 -->
    <span class="inline-flex flex-wrap items-baseline gap-x-1.5 tabular-nums" :class="ended ? 'opacity-60' : ''">
        <b :class="large ? 'text-lg' : 'text-[13px]'">{{ UtilHotdealMessages.won(t, locale, dealPrice) }}</b>
        <s v-if="normalPrice !== null && normalPrice > dealPrice" class="text-xs text-muted-color">{{ UtilHotdealMessages.won(t, locale, normalPrice) }}</s>
        <span v-if="discountRate !== null" class="text-xs font-semibold text-red-600 dark:text-red-400">{{ t('hotdeal.discount', { n: discountRate }) }}</span>
    </span>
</template>

<script setup lang="ts">
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

interface Props {
    dealPrice: number;
    normalPrice?: number | null;
    discountRate?: number | null;
    /** 큰 글씨(서랍의 딜 카드) */
    large?: boolean;
    /** 종료·기간 지남 — 흐리게 */
    ended?: boolean;
}

withDefaults(defineProps<Props>(), { normalPrice: null, discountRate: null, large: false, ended: false });

const { t, locale } = useI18n();
</script>
