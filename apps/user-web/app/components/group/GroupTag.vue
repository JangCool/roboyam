<template>
    <!-- 소모임 화면의 작은 알약 — 모집 상태·가입 방식·오프라인·모임장·승인 대기·분류 이름. 색만으로 가르지 않도록 글자가 늘 있다.
         글자는 상하좌우 가운데(components.md 2-4 — 알약은 `inline-flex items-center justify-center leading-none`, 한글 글자만 `top:-0.09em`) -->
    <span class="inline-flex shrink-0 items-center justify-center whitespace-nowrap rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none" :class="TONE_CLASS[tone]">
        <span class="relative top-[-0.09em] max-w-[10rem] truncate"><slot /></span>
    </span>
</template>

<script setup lang="ts">
export type GroupTagTone = 'green' | 'amber' | 'gray' | 'blue' | 'red' | 'sky' | 'neutral';

interface Props {
    tone?: GroupTagTone;
}

const props = withDefaults(defineProps<Props>(), {
    tone: 'gray',
});

const TONE_CLASS: Record<GroupTagTone, string> = {
    green: 'bg-green-100 text-green-800 dark:bg-green-900/40 dark:text-green-300',
    amber: 'bg-amber-100 text-amber-800 dark:bg-amber-900/40 dark:text-amber-300',
    gray: 'bg-surface-200 text-surface-700 dark:bg-surface-700 dark:text-surface-200',
    blue: 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300',
    red: 'bg-red-100 text-red-800 dark:bg-red-900/40 dark:text-red-300',
    sky: 'bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-300',
    neutral: 'bg-surface-100 text-surface-600 dark:bg-surface-800 dark:text-surface-300',
};
</script>
