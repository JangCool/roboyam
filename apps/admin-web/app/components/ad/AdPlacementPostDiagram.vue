<template>
    <!--
        글 보기 도식 — user-web `pages/posts/[id]/index.vue` 짜임: 글 카드(머리 · 본문 · 투표·찬반 토론 · [AFTER_BODY 광고] · 추천 줄 · 이전/다음 글) → [BETWEEN_CARDS 광고] → 댓글 카드.
        광고 자리만 주 색상이고 나머지는 회색 상자·줄이다. `ad` 가 null 이면 광고 없이 두 카드 사이 원래 간격.
    -->
    <div class="flex min-w-0 flex-col" data-testid="ad-placement-post-diagram">
        <!-- 글 카드 -->
        <div :class="card">
            <span :class="cardLabel">{{ t('ad.previewPostCard') }}</span>
            <div class="flex items-center gap-1.5">
                <span v-if="adult" class="inline-flex size-3.5 shrink-0 items-center justify-center rounded-full bg-surface-400 text-[0.5rem] font-bold leading-none text-surface-0 dark:bg-surface-500" aria-hidden="true">19</span>
                <span class="block h-2 w-3/5 rounded-full bg-surface-300 dark:bg-surface-600" />
            </div>
            <span :class="[bar, 'mt-1.5 w-1/3']" />
            <div class="mt-2.5 flex flex-col gap-1.5" :class="compact ? '' : 'mb-0.5'">
                <span :class="[bar, 'w-full']" />
                <span :class="[bar, 'w-11/12']" />
                <span v-if="!compact" :class="[bar, 'w-4/5']" />
            </div>
            <div v-if="ad?.position === 'AFTER_BODY'" class="py-2" data-testid="ad-placement-post-after-body">
                <AdPlacementSlot :shape="shapeOf(ad.type)" center />
            </div>
            <!-- 추천 · 링크 복사 줄 -->
            <div class="flex justify-center gap-1.5" :class="ad?.position === 'AFTER_BODY' ? '' : 'mt-2.5'">
                <span class="h-3 w-9 rounded-full border border-(--line-strong)" />
                <span class="h-3 w-9 rounded-full border border-(--line-strong)" />
            </div>
            <div v-if="!compact" class="mt-2 flex flex-col gap-1.5 border-t border-(--line) pt-2">
                <span :class="[bar, 'w-1/2']" />
                <span :class="[bar, 'w-2/5']" />
            </div>
        </div>

        <!-- 두 카드 사이 -->
        <div v-if="ad?.position === 'BETWEEN_CARDS'" class="py-1.5" data-testid="ad-placement-post-between">
            <AdPlacementSlot :shape="shapeOf(ad.type)" />
        </div>
        <div v-else class="h-2" />

        <!-- 댓글 카드 -->
        <div :class="card">
            <span :class="cardLabel">{{ t('ad.previewCommentCard') }}</span>
            <span :class="[barStrong, 'w-1/4']" />
            <div v-for="n in compact ? 1 : 2" :key="n" class="mt-2 flex items-start gap-1.5">
                <span class="size-3 shrink-0 rounded-full bg-surface-200 dark:bg-surface-700" />
                <div class="flex min-w-0 flex-1 flex-col gap-1">
                    <span :class="[bar, 'w-1/4']" />
                    <span :class="[bar, n === 1 ? 'w-4/5' : 'w-3/5']" />
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { AdPostInlinePosition, AdPostInlineType } from '~/composables/ad/api/useAdApi.types';

interface Props {
    /** 그릴 광고 자리 — null 이면 광고 없음 */
    ad?: { type: AdPostInlineType; position: AdPostInlinePosition } | null;
    /** 줄 수를 줄인 작은 도식(19금 묶음) */
    compact?: boolean;
    /** 19금 글 표시 */
    adult?: boolean;
}

withDefaults(defineProps<Props>(), { ad: null, compact: false, adult: false });
const { t } = useI18n();

const card = 'relative rounded-md border border-(--line) bg-(--p-content-background) px-2.5 pb-2.5 pt-3';
const cardLabel = 'absolute right-1.5 top-0.5 text-[0.5625rem] leading-tight text-muted-color';
const bar = 'block h-1.5 rounded-full bg-surface-200 dark:bg-surface-700';
const barStrong = 'block h-1.5 rounded-full bg-surface-300 dark:bg-surface-600';

function shapeOf(type: AdPostInlineType): 'line' | 'strip' | 'square' {
    if (type === 'STRIP_BANNER') return 'strip';
    if (type === 'SQUARE_BANNER') return 'square';
    return 'line';
}
</script>
