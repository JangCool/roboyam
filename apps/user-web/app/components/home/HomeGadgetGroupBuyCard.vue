<template>
    <!--
        진행 중 공동구매 한 장(`HomeGadgetGroupBuy` 가 쓴다 — 넘기기 카드 · 한 열의 가로로 미는 줄 같은 모양) — 사진 + 마감 표시 · 이름 · 할인율·공구가·정가 · 판매 진행 막대 · 남은 수량.
        카드 전체가 공구 상세(`/group-buys/{id}`, 2b 화면) 링크다.
    -->
    <NuxtLink :to="`/group-buys/${item.id}`" class="block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary" data-testid="home-group-buy-card">
        <HomeThumb :src="item.imageUrl" ratio="16 / 9" class="w-full">
            <span v-if="deadline" class="pointer-events-none absolute left-2 top-2 inline-flex items-center rounded bg-surface-950/75 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-surface-0">
                <span class="relative top-[-0.09em]">{{ deadline }}</span>
            </span>
        </HomeThumb>
        <p class="mt-2 line-clamp-2 break-words text-[0.9375rem] font-semibold leading-snug">{{ item.title }}</p>
        <p class="mt-1 flex flex-wrap items-baseline gap-x-1.5">
            <span v-if="discount !== null" class="text-base font-extrabold text-red-600 dark:text-red-400">
                <span aria-hidden="true">{{ t('home.discount', { n: discount }) }}</span><span class="sr-only">{{ t('home.discountLabel', { n: discount }) }}</span>
            </span>
            <span class="text-base font-extrabold">{{ UtilHomeGadgetMessages.price(t, item.price) }}</span>
            <template v-if="item.listPrice !== null">
                <s class="text-xs text-muted-color" aria-hidden="true">{{ UtilHomeGadgetMessages.price(t, item.listPrice) }}</s>
                <span class="sr-only">{{ t('home.listPriceLabel', { amount: UtilHomeGadgetMessages.price(t, item.listPrice) }) }}</span>
            </template>
        </p>
        <div v-if="sold !== null" class="mt-2 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700" aria-hidden="true">
            <div class="h-full rounded-full bg-primary" :style="{ width: `${sold}%` }" />
        </div>
        <p class="mt-1.5 text-xs text-muted-color">
            <template v-if="sold !== null && item.totalQty !== null">
                {{ t('home.groupBuySold', { n: sold }) }} · {{ t('home.groupBuyLeft', { n: Math.max(0, item.totalQty - item.soldQty) }) }}
            </template>
            <template v-else>{{ t('home.groupBuySoldCount', { n: item.soldQty }) }}</template>
        </p>
    </NuxtLink>
</template>

<script setup lang="ts">
import type { HomeGroupBuyItem } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';
import UtilHomeGadgetMessages from '~/utils/util-home-gadget-messages';

interface Props {
    item: HomeGroupBuyItem;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { now } = useHomeGadgetShared();

const discount = computed(() => UtilHomeGadget.discountPercent(props.item));
const sold = computed(() => UtilHomeGadget.soldPercent(props.item));
const deadline = computed(() => UtilHomeGadgetMessages.deadline(t, UtilHomeGadget.daysLeft(props.item.endsAt, now.value)));
</script>
