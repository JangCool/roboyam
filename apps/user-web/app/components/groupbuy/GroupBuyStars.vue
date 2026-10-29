<template>
    <!-- 별점 보기(읽기 전용) — 0.5 단위로 채운다. 화면 읽기에는 「별점 N점」 한 마디 -->
    <span class="inline-flex items-center gap-px text-amber-500" role="img" :aria-label="t('groupBuyReview.starsLabel', { value })">
        <i v-for="n in 5" :key="n" class="pi" :class="[icon(n), size]" aria-hidden="true" />
    </span>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{ value: number; size?: string }>(), { size: 'text-xs' });
const { t } = useI18n();

function icon(n: number): string {
    if (props.value >= n) return 'pi-star-fill';
    if (props.value >= n - 0.5) return 'pi-star-half-fill';
    return 'pi-star text-surface-300 dark:text-surface-600';
}
</script>
