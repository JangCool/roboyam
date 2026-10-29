<template>
    <!--
        진행 중 공동구매 한 장(`HomeGadgetGroupBuy` 가 쓴다 — 넘기기 카드 · 한 열의 가로로 미는 줄 같은 모양) — 회원 공구 목록 줄(`GroupBuyListItem`)을 그린다.
        사진 + 마감(연장 중이면 연장 끝) 또는 품절 표시 · 이름 · 할인율·공구가(「부터」)·정상가 · 리뷰 평균·수 · 진행 막대(성사형 = 확정/최소, 선착순 = 확정/최대) · 남은 수량.
        카드 전체가 공구 상세(`UtilGroupBuy.detailPath`) 링크다. 품절·남은 수량은 서버 값이다.
    -->
    <NuxtLink :to="UtilGroupBuy.detailPath(item.id)" class="block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="home-group-buy-card">
        <HomeThumb :src="item.thumbnailUrl" ratio="16 / 9" class="w-full">
            <span v-if="badge" class="pointer-events-none absolute left-2 top-2 inline-flex items-center rounded bg-surface-950/75 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-surface-0">
                <span class="relative top-[-0.09em]">{{ badge }}</span>
            </span>
        </HomeThumb>
        <p class="mt-2 line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug">{{ item.title }}</p>
        <p class="mt-1 flex flex-wrap items-baseline gap-x-1.5">
            <span v-if="discount !== null" class="text-base font-extrabold text-red-600 dark:text-red-400">
                <span aria-hidden="true">{{ t('home.discount', { n: discount }) }}</span><span class="sr-only">{{ t('home.discountLabel', { n: discount }) }}</span>
            </span>
            <span class="text-base font-extrabold">{{ price }}</span>
            <template v-if="discount !== null && item.normalPrice !== null">
                <s class="text-xs text-muted-color" aria-hidden="true">{{ UtilHomeGadgetMessages.price(t, item.normalPrice) }}</s>
                <span class="sr-only">{{ t('home.listPriceLabel', { amount: UtilHomeGadgetMessages.price(t, item.normalPrice) }) }}</span>
            </template>
        </p>
        <!-- 리뷰 평균 별점·수(2b-6 — 같은 상품의 모든 차수, 목록 카드와 같다) -->
        <p v-if="item.reviews && item.reviews.count > 0" class="mt-1 flex items-center gap-1 text-xs text-muted-color" data-testid="home-group-buy-reviews">
            <i class="pi pi-star-fill text-[11px] text-amber-500" aria-hidden="true" />
            <span class="font-semibold tabular-nums text-color">{{ item.reviews.average?.toFixed(1) }}</span>
            <span class="tabular-nums">{{ t('groupBuyReview.countShort', { count: item.reviews.count }) }}</span>
        </p>
        <div v-if="sold !== null" class="mt-2 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" aria-hidden="true">
            <div class="h-full rounded-full bg-primary" :style="{ width: `${sold}%` }" />
        </div>
        <p class="mt-1.5 text-xs text-muted-color">
            <template v-if="item.minQty > 0">{{ t('groupBuy.progress', { current: item.currentQty, min: item.minQty }) }}</template>
            <template v-else-if="sold !== null">{{ t('home.groupBuySold', { n: sold }) }}</template>
            <template v-if="item.stockState === 'AVAILABLE'"> · {{ t('home.groupBuyLeft', { n: item.remainingQty }) }}</template>
        </p>
    </NuxtLink>
</template>

<script setup lang="ts">
import type { GroupBuyListItem } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilHomeGadgetMessages from '~/utils/util-home-gadget-messages';

interface Props {
    item: GroupBuyListItem;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { now } = useHomeGadgetShared();

const discount = computed(() => UtilHomeGadget.discountPercent(props.item));
const sold = computed(() => UtilHomeGadget.soldPercent(props.item));
const price = computed(() => {
    const won = UtilHomeGadgetMessages.price(t, props.item.groupPrice);
    return props.item.priceFrom ? t('groupBuy.priceFrom', { price: won }) : won;
});
/** 사진 위 표시 — 품절·잠시 구매 불가가 마감보다 앞선다 */
const badge = computed(() => {
    if (props.item.stockState !== 'AVAILABLE') return t(`groupBuy.stock.${props.item.stockState}`);
    return UtilHomeGadgetMessages.deadline(t, UtilHomeGadget.daysLeft(props.item.extendedUntil ?? props.item.endAt, now.value));
});
</script>
