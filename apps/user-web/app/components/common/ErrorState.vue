<template>
    <!-- 오류 상태 — 불러오지 못했을 때(다시 시도) · 없는 것(404) · 삭제된 글처럼 볼 수 없을 때 (design `.err-state`) -->
    <div class="flex flex-col items-center px-5 py-10 text-center" role="alert">
        <p class="mb-2 text-4xl font-bold text-surface-300 dark:text-surface-600" aria-hidden="true">{{ code }}</p>
        <h2 class="text-base font-semibold">{{ title }}</h2>
        <p v-if="description" class="mt-1 text-sm text-muted-color">{{ description }}</p>
        <div v-if="$slots.default || retry" class="mt-4 flex flex-wrap justify-center gap-2">
            <Button v-if="retry" type="button" :label="t('common.retry')" :loading="retrying" @click="$emit('retry')" />
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
const { t } = useI18n();

interface Props {
    title: string;
    description?: string;
    /** 큰 글자 — `!` 또는 `404` */
    code?: string;
    /** true 면 "다시 시도" 버튼을 둔다 */
    retry?: boolean;
    retrying?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    description: '',
    code: '!',
    retry: false,
    retrying: false,
});

defineEmits<{ retry: [] }>();
</script>
