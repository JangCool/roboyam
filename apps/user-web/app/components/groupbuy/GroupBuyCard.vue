<template>
    <!--
        공동구매 목록 카드(쇼핑몰 상품 격자 한 칸) — 1:1 대표 그림(딱지 · 다 팔림/종료 덮개) → 이름 두 줄 → 할인율 · 공구가 · 정상가 → 별점 → 성사형 달성률 → 딱지 → 마감 줄.
        카드 전체가 상세 링크다. 테두리·바탕 없이 그림과 글자만(쇼핑몰 격자) — 둘레의 카드는 페이지(`content-card`)가 깔고, 사진 모서리는 테마 값(`GroupBuyThumb`).
        표시 판정(남은 수량 적음·할인율)은 `UtilGroupBuy`, 값은 모두 서버 값이다(계약 commerce-groupbuy 목록).
    -->
    <NuxtLink
        :to="UtilGroupBuy.detailPath(gb.id)"
        class="group flex h-full flex-col gap-2.5 rounded-[var(--gadget-thumb-radius)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-primary"
        data-testid="group-buys-item"
    >
        <GroupBuyThumb size="square" zoom :src="gb.thumbnailUrl" :alt="gb.title">
            <span v-if="badges.length" class="absolute left-2 top-2 flex flex-wrap gap-1">
                <span v-for="b in badges" :key="b.key" class="rounded px-1.5 py-0.5 text-[0.6875rem] font-bold leading-tight shadow-sm" :class="b.class">{{ b.text }}</span>
            </span>
            <span v-if="cover" class="absolute inset-0 grid place-items-center bg-black/45 text-sm font-bold text-white" data-testid="group-buys-item-cover">{{ cover }}</span>
        </GroupBuyThumb>

        <div class="flex min-w-0 flex-1 flex-col gap-1.5 px-0.5">
            <p class="m-0 line-clamp-2 text-sm leading-snug text-color group-hover:underline">{{ gb.title }}</p>

            <!-- 가격 — 할인율(정상가가 있고 「N원부터」가 아닐 때) · 공구가 · 정상가 -->
            <div class="flex flex-col">
                <s v-if="gb.normalPrice" class="text-xs tabular-nums text-muted-color">
                    <span class="sr-only">{{ t('groupBuy.normalPrice') }}</span>{{ UtilGroupBuyMessages.won(t, gb.normalPrice) }}
                </s>
                <p class="m-0 flex flex-wrap items-baseline gap-x-1.5">
                    <span v-if="discount !== null" class="text-[1.0625rem] font-extrabold tabular-nums text-red-600 dark:text-red-400" data-testid="group-buys-item-discount">
                        <span class="sr-only">{{ t('groupBuy.discountLabel') }}</span>{{ t('groupBuy.discountRate', { rate: discount }) }}
                    </span>
                    <span class="text-[1.0625rem] font-extrabold tabular-nums text-color">{{ price }}</span>
                </p>
            </div>

            <!-- 리뷰 평균 별점·수(2b-6 — 같은 상품의 모든 차수) -->
            <p v-if="gb.reviews && gb.reviews.count > 0" class="m-0 flex items-center gap-1 text-xs text-muted-color" data-testid="group-buys-item-reviews">
                <i class="pi pi-star-fill text-[0.6875rem] text-amber-500" aria-hidden="true" />
                <span class="font-semibold tabular-nums text-color">{{ gb.reviews.average?.toFixed(1) }}</span>
                <span class="tabular-nums">{{ t('groupBuyReview.countShort', { count: gb.reviews.count }) }}</span>
            </p>

            <!-- 성사형 달성률(확정 / 최소) -->
            <div v-if="reached !== null && gb.phase !== 'UPCOMING'" class="flex flex-col gap-1">
                <ProgressBar :value="UtilGroupBuy.progress(gb.currentQty, gb.minQty) ?? 0" :show-value="false" class="h-1" :aria-label="t('groupBuy.progressLabel')" />
                <p class="m-0 flex items-baseline justify-between gap-2 text-xs tabular-nums">
                    <b class="font-bold text-primary">{{ t('groupBuy.progressPercent', { percent: reached }) }}</b>
                    <span class="truncate text-muted-color">{{ t('groupBuy.progress', { current: gb.currentQty, min: gb.minQty }) }}</span>
                </p>
            </div>

            <div v-if="tags.length" class="flex flex-wrap items-center gap-1">
                <PointShopTag v-for="tag in tags" :key="tag.key" :tone="tag.tone">{{ tag.text }}</PointShopTag>
            </div>

            <!-- 예정·모집 중은 남은 시간(1초마다, 하루 안이면 빨강), 그 밖은 상태 글자 -->
            <GroupBuyCountdown v-if="gb.phase === 'UPCOMING' || gb.status === 'RECRUITING'" :gb="gb" class="mt-auto pt-0.5" />
            <p v-else class="m-0 mt-auto flex items-center gap-1 pt-0.5 text-xs tabular-nums text-muted-color">
                <i class="pi pi-clock text-[0.6875rem]" aria-hidden="true" />{{ UtilGroupBuyMessages.deadline(t, time, gb) }}
            </p>
        </div>
    </NuxtLink>
</template>

<script setup lang="ts">
import type { GroupBuyListItem } from '~/composables/groupbuy/api/useGroupBuyApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

const props = defineProps<{ gb: GroupBuyListItem }>();
const { t } = useI18n();
const time = useDisplayTime();

const price = computed(() => (props.gb.priceFrom ? t('groupBuy.priceFrom', { price: UtilGroupBuyMessages.won(t, props.gb.groupPrice) }) : UtilGroupBuyMessages.won(t, props.gb.groupPrice)));
const discount = computed(() => UtilGroupBuy.discountRate(props.gb.normalPrice, props.gb.groupPrice, props.gb.priceFrom));
const reached = computed(() => UtilGroupBuy.reached(props.gb.currentQty, props.gb.minQty));

/** 그림 위 덮개 — 모집 중 다 팔림 · 끝난 공구(종료 사유). 글자로 말한다 */
const cover = computed(() => {
    const gb = props.gb;
    if (gb.phase === 'ONGOING' && gb.status === 'RECRUITING' && gb.stockState === 'SOLD_OUT') return t('groupBuy.stock.SOLD_OUT');
    if (gb.phase === 'ENDED') return gb.closeReason ? t(`groupBuy.closeReason.${gb.closeReason}`) : t(`groupBuy.status.${gb.status}`);
    return '';
});

/** 그림 위 왼쪽 딱지 — N차 · 마감 임박/연장 · 예정(눈에 띄는 것만, 나머지는 아래 딱지 줄) */
const badges = computed(() => {
    const gb = props.gb;
    const out: { key: string; text: string; class: string }[] = [];
    if (gb.phase === 'UPCOMING') out.push({ key: 'phase', text: t('groupBuy.phase.UPCOMING'), class: 'bg-primary text-primary-contrast' });
    if (gb.phase === 'ONGOING' && gb.status === 'RECRUITING') {
        if (gb.extendedUntil) out.push({ key: 'extended', text: t('groupBuy.extendedShort'), class: 'bg-orange-600 text-white' });
        else if (gb.closingSoon) out.push({ key: 'soon', text: t('groupBuy.closingSoon'), class: 'bg-red-600 text-white' });
    }
    if (gb.roundNo > 1) out.push({ key: 'round', text: t('groupBuy.roundNo', { n: gb.roundNo }), class: 'bg-surface-900/80 text-white dark:bg-surface-0/85 dark:text-surface-900' });
    return out;
});

type Tone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger';

/** 아래 딱지 줄 — 글자와 함께(색만으로 뜻을 가르지 않는다). 덮개·그림 딱지에 이미 보인 것은 뺀다 */
const tags = computed(() => {
    const gb = props.gb;
    const out: { key: string; text: string; tone: Tone }[] = [];
    if (gb.phase === 'ONGOING') {
        if (gb.status === 'CLOSING') out.push({ key: 'judging', text: t('groupBuy.judgingShort'), tone: 'neutral' });
        else if (gb.stockState === 'TEMP_SOLD_OUT') out.push({ key: 'stock', text: t('groupBuy.stock.TEMP_SOLD_OUT'), tone: 'warn' });
        else if (gb.stockState !== 'SOLD_OUT' && UtilGroupBuy.lowStock(gb.remainingQty, gb.maxQty)) out.push({ key: 'low', text: t('groupBuy.remainingFew', { count: gb.remainingQty }), tone: 'warn' });
    }
    out.push({ key: 'kind', text: gb.minQty > 0 ? t('groupBuy.kindMin') : t('groupBuy.kindFirst'), tone: 'neutral' });
    return out;
});
</script>
