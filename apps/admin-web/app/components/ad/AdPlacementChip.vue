<template>
    <!-- 미리보기 안에서 보는 경우를 고르는 작은 알약 단추(저장하지 않는 값) — 고른 것은 주 색상, `aria-pressed` -->
    <button type="button" class="inline-flex items-center justify-center gap-1 rounded-full border px-2 py-1 text-[0.6875rem] leading-none focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary" :class="pressed ? on : off" :aria-pressed="pressed">
        <slot />
    </button>
</template>

<script setup lang="ts">
interface Props {
    pressed?: boolean;
}

withDefaults(defineProps<Props>(), { pressed: false });

const on = 'border-primary bg-primary-50 font-medium text-primary dark:bg-primary-950/40';
const off = 'border-(--line-strong) bg-(--p-content-background) text-muted-color hover:bg-(--p-content-hover-background)';
</script>
