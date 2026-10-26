<template>
    <!-- 소모임 화면의 안내 띠 — 정보(회색) · 주의(노랑) · 경고(빨강). 색만으로 가르지 않도록 제목·문장이 늘 있다 -->
    <div class="rounded-lg border px-3 py-2.5 text-sm leading-relaxed" :class="TONE_CLASS[tone]" :role="tone === 'danger' ? 'alert' : 'note'">
        <b v-if="title" class="mr-1 font-semibold">{{ title }}</b><slot />
    </div>
</template>

<script setup lang="ts">
export type GroupNoticeTone = 'info' | 'warn' | 'danger';

interface Props {
    tone?: GroupNoticeTone;
    title?: string;
}

const props = withDefaults(defineProps<Props>(), {
    tone: 'info',
    title: '',
});

const TONE_CLASS: Record<GroupNoticeTone, string> = {
    info: 'border-surface bg-surface-100 text-color dark:bg-surface-800',
    warn: 'border-amber-300 bg-amber-50 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200',
    danger: 'border-red-300 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950/40 dark:text-red-200',
};
</script>
