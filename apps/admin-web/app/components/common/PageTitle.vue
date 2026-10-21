<template>
    <!-- 페이지 상단 제목 블록 — 셸이 아니라 본문 안에 있다. 오른쪽 `actions` 슬롯에 버튼을 둘 수 있다 -->
    <div class="flex items-end justify-between gap-3">
        <div class="min-w-0 flex-1">
            <h1 class="truncate text-xl font-semibold tracking-tight">{{ title }}</h1>
            <p v-if="subtitle" class="mt-1 text-sm text-muted-color">{{ subtitle }}</p>
        </div>
        <div v-if="$slots.actions" class="flex shrink-0 flex-wrap items-center justify-end gap-2">
            <slot name="actions" />
        </div>
    </div>
</template>

<script setup lang="ts">
interface Props {
    title: string;
    subtitle?: string;
}

withDefaults(defineProps<Props>(), { subtitle: '' });
</script>
