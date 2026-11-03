<template>
    <!-- 숫자 카드 격자가 오기 전 자리 -->
    <div class="grid gap-3" :style="{ gridTemplateColumns: `repeat(auto-fill, minmax(min(100%, ${min}rem), 1fr))` }" role="status" :aria-label="t('common.loading')">
        <Skeleton v-for="n in count" :key="n" :height="height" border-radius="0.75rem" />
    </div>
</template>

<script setup lang="ts">
interface Props {
    count?: number;
    min?: number;
    height?: string;
}

withDefaults(defineProps<Props>(), { count: 4, min: 13, height: '5.5rem' });

const { t } = useI18n();
</script>
