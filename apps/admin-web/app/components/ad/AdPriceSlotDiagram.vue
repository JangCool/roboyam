<template>
    <!--
        상품이 공개 화면 어디에 나오는지 그린 도식 — 상품 가격 카드의 사업자 화면 미리보기(`AdPriceProductPreview`) 맨 위.
        회색 상자·줄 = 화면 짜임, 주 색상 테두리 상자 = 그 상품 자리(이름표 글자 포함). 창 머리에 화면 이름(홈·게시판 목록·핫딜 게시판 …).
          배너 넷: 홈 맨 위 큰 자리 / 게시판 목록 위 띠 / 홈 옆 칸 네모 / 게시판 옆 칸 작은 자리 · 날개: 본문 양옆 세로 · 한 줄 광고: 목록 위 글자 한 줄
          핫딜 셋: 글 게시 = 목록 속 사업자 글 한 줄(「사업자」 표시) / 게시판 상단 = 첫 쪽 맨 위 고정(「광고」) / 말머리 상단 = 말머리 고른 목록 맨 위 고정(「광고」)
        그림은 꾸밈(aria-hidden) — 뜻은 아래 설명 줄(`ad.pricePreviewWhere.*`)이 전한다. 공개 화면 짜임을 단순하게 옮긴 것(가정)이라 화면이 바뀌면 맞춘다.
    -->
    <div class="flex min-w-0 flex-col overflow-hidden rounded-md border border-(--line) bg-(--p-content-background)" aria-hidden="true" :data-testid="`ad-price-slot-${product}`">
        <!-- 창 머리 -->
        <div class="flex items-center gap-1 border-b border-(--line) px-2 py-1">
            <span class="size-1.5 rounded-full bg-surface-300 dark:bg-surface-600" />
            <span class="size-1.5 rounded-full bg-surface-300 dark:bg-surface-600" />
            <span class="ml-1 truncate text-[0.625rem] text-muted-color">{{ t(`ad.priceSlotScreen.${product}`) }}</span>
        </div>
        <!-- 사이트 머리 -->
        <div class="flex items-center gap-1.5 px-2 pt-1.5">
            <span class="h-1.5 w-6 rounded-sm bg-surface-400 dark:bg-surface-500" />
            <span class="h-1 w-4 rounded-sm bg-surface-200 dark:bg-surface-700" />
            <span class="h-1 w-4 rounded-sm bg-surface-200 dark:bg-surface-700" />
            <span class="h-1 w-4 rounded-sm bg-surface-200 dark:bg-surface-700" />
        </div>

        <div class="min-h-32 p-2">
            <!-- 넓은 배너 — 홈 맨 위 -->
            <div v-if="product === 'WIDE_BANNER'" class="flex flex-col gap-1.5">
                <span :class="[SLOT, 'h-11']">{{ label }}</span>
                <div class="grid grid-cols-3 gap-1.5">
                    <span v-for="n in 3" :key="n" :class="[BOX, 'h-9']" />
                </div>
                <div class="grid grid-cols-2 gap-1.5">
                    <span v-for="n in 2" :key="n" :class="[BOX, 'h-6']" />
                </div>
            </div>

            <!-- 띠 배너 — 게시판 목록 위 -->
            <div v-else-if="product === 'STRIP_BANNER'" class="flex flex-col gap-1">
                <span class="h-1.5 w-12 rounded-sm bg-surface-300 dark:bg-surface-600" />
                <span :class="[SLOT, 'h-5']">{{ label }}</span>
                <span v-for="n in 5" :key="n" :class="ROW" />
            </div>

            <!-- 네모 배너 — 홈 옆 칸 -->
            <div v-else-if="product === 'SQUARE_BANNER'" class="grid grid-cols-[minmax(0,1fr)_34%] gap-1.5">
                <div class="flex flex-col gap-1.5">
                    <span :class="[BOX, 'h-10']" />
                    <div class="grid grid-cols-2 gap-1.5">
                        <span v-for="n in 2" :key="n" :class="[BOX, 'h-8']" />
                    </div>
                    <span :class="[BOX, 'h-6']" />
                </div>
                <div class="flex flex-col gap-1.5">
                    <span :class="[SLOT, 'aspect-[6/5]']">{{ label }}</span>
                    <span :class="[BOX, 'h-8']" />
                </div>
            </div>

            <!-- 작은 배너 — 게시판 옆 칸 -->
            <div v-else-if="product === 'SMALL_BANNER'" class="grid grid-cols-[minmax(0,1fr)_34%] gap-1.5">
                <div class="flex flex-col gap-1">
                    <span class="h-1.5 w-12 rounded-sm bg-surface-300 dark:bg-surface-600" />
                    <span v-for="n in 6" :key="n" :class="ROW" />
                </div>
                <div class="flex flex-col gap-1.5">
                    <span :class="[BOX, 'h-10']" />
                    <span :class="[SLOT, 'h-6']">{{ label }}</span>
                    <span :class="[BOX, 'h-6']" />
                </div>
            </div>

            <!-- 날개 배너 — 본문 양옆 세로 -->
            <div v-else-if="product === 'WING_BANNER'" class="grid grid-cols-[14%_minmax(0,1fr)_14%] gap-1.5">
                <span :class="[SLOT, 'h-28 [writing-mode:vertical-rl]']">{{ label }}</span>
                <div class="flex flex-col gap-1 rounded-sm bg-surface-100 p-1.5 dark:bg-surface-800">
                    <span class="h-1.5 w-3/4 rounded-sm bg-surface-300 dark:bg-surface-600" />
                    <span v-for="n in 7" :key="n" class="h-1 rounded-sm bg-surface-200 dark:bg-surface-700" :class="n % 3 === 0 ? 'w-2/3' : 'w-full'" />
                </div>
                <span :class="[SLOT, 'h-28 [writing-mode:vertical-rl]']">{{ label }}</span>
            </div>

            <!-- 한 줄 광고 — 게시판 목록 위 글자 한 줄 -->
            <div v-else-if="product === 'TEXT_LINE'" class="flex flex-col gap-1">
                <span class="h-1.5 w-12 rounded-sm bg-surface-300 dark:bg-surface-600" />
                <span :class="[SLOT, 'h-5 justify-start px-1.5']">{{ label }}</span>
                <span v-for="n in 6" :key="n" :class="ROW" />
            </div>

            <!-- 핫딜 셋 — 핫딜 게시판 목록(썸네일 · 제목 · 가격) -->
            <div v-else class="flex flex-col gap-1">
                <div class="flex items-center gap-1">
                    <span class="h-1.5 w-10 rounded-sm bg-surface-300 dark:bg-surface-600" />
                    <!-- 말머리 칩(말머리 상단은 하나 고름) -->
                    <span class="ml-auto flex gap-0.5">
                        <span
                            v-for="n in 4"
                            :key="n"
                            class="h-2 w-4 rounded-full"
                            :class="product === 'HOTDEAL_CATEGORY' && n === 2 ? 'bg-primary/70' : 'bg-surface-200 dark:bg-surface-700'"
                        />
                    </span>
                </div>
                <div v-for="(row, i) in dealRows" :key="i" class="flex items-center gap-1.5 rounded-sm px-1 py-0.5" :class="row.slot ? 'border border-primary bg-primary/10' : ''">
                    <span class="size-5 shrink-0 rounded-sm" :class="row.slot ? 'bg-primary/40' : 'bg-surface-200 dark:bg-surface-700'" />
                    <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                        <span class="flex items-center gap-1">
                            <span v-if="row.slot" class="shrink-0 rounded-sm bg-primary px-1 text-[0.5625rem] leading-3 font-semibold text-primary-contrast">{{ row.badge }}</span>
                            <span class="h-1 flex-1 rounded-sm" :class="row.slot ? 'bg-primary/50' : 'bg-surface-300 dark:bg-surface-600'" />
                        </span>
                        <span class="h-1 w-1/3 rounded-sm" :class="row.slot ? 'bg-primary/40' : 'bg-surface-200 dark:bg-surface-700'" />
                    </span>
                    <span v-if="row.slot" class="shrink-0 text-[0.5625rem] font-medium text-primary">{{ label }}</span>
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { AdPriceProduct } from '~/composables/ad/api/useAdApi.types';

interface Props {
    product: AdPriceProduct;
}

const props = withDefaults(defineProps<Props>(), {});
const { t } = useI18n();

/** 상품 자리(주 색상 테두리 · 이름표) */
const SLOT = 'flex items-center justify-center overflow-hidden rounded-sm border border-primary bg-primary/15 px-1 text-center text-[0.625rem] leading-tight font-semibold text-primary';
/** 다른 칸(회색 상자) */
const BOX = 'block rounded-sm bg-surface-200 dark:bg-surface-700';
/** 목록 한 줄 */
const ROW = 'block h-1.5 rounded-sm bg-surface-200 dark:bg-surface-700';

const label = computed(() => t(`ad.priceSlotLabel.${props.product}`));

/** 핫딜 목록 다섯 줄 — 글 게시는 셋째 줄(새 글 차례 속 사업자 글), 상단 둘은 맨 위 고정 */
const dealRows = computed(() => {
    const slotAt = props.product === 'HOTDEAL_POST' ? 2 : 0;
    const badge = props.product === 'HOTDEAL_POST' ? t('ad.priceSlotBadge.biz') : t('ad.priceSlotBadge.ad');
    return Array.from({ length: 5 }, (_, i) => ({ slot: i === slotAt, badge }));
});
</script>
