<template>
    <!-- 별점 한 칸(읽기 전용) — 별 다섯 + 숫자. 낮은 별점(1~2)은 숫자를 붉게 -->
    <span class="inline-flex items-center gap-1" role="img" :aria-label="t('groupBuyReview.ratingN', { n: value })">
        <span class="inline-flex gap-px text-amber-500" aria-hidden="true">
            <i v-for="n in 5" :key="n" class="pi text-[11px]" :class="value >= n ? 'pi-star-fill' : 'pi-star text-surface-300 dark:text-surface-600'" />
        </span>
        <span class="text-[12px] font-semibold tabular-nums" :class="value <= 2 ? 'text-red-600 dark:text-red-400' : ''" aria-hidden="true">{{ value }}</span>
    </span>
</template>

<script setup lang="ts">
defineProps<{ value: number }>();
const { t } = useI18n();
</script>
