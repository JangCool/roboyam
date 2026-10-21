<template>
    <!-- 빈 상태 — 왜 비었는지 말하고 다음 행동을 준다 -->
    <div class="flex flex-col items-center px-5 py-10 text-center">
        <div class="mb-3 grid size-12 place-items-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
            <i :class="[icon, 'text-lg']" />
        </div>
        <p class="font-semibold">{{ title }}</p>
        <p v-if="description" class="mt-1 text-sm text-muted-color">{{ description }}</p>
        <div v-if="$slots.default" class="mt-4 flex flex-wrap justify-center gap-2">
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    title: string;
    description?: string;
    /** primeicons 클래스 */
    icon?: string;
}

const props = withDefaults(defineProps<Props>(), {
    description: '',
    icon: 'pi pi-inbox',
});
</script>
