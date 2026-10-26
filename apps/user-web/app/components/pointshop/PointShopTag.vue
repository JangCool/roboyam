<template>
    <!-- 포인트 상점의 작은 딱지(사용 방식·판매 상태·보유·장착) — 글자는 상하좌우 가운데(components.md 2-4: 알약 inline-flex + leading-none, 글자에만 −0.09em) -->
    <span class="inline-flex shrink-0 items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-medium leading-none" :class="TONE[tone]">
        <span class="relative top-[-0.09em]"><slot /></span>
    </span>
</template>

<script setup lang="ts">
/** 딱지 톤 — 글자와 함께 쓴다(색만으로 뜻을 가르지 않는다) */
type TagTone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger';

interface Props {
    tone?: TagTone;
}

withDefaults(defineProps<Props>(), {
    tone: 'neutral',
});

const TONE: Record<TagTone, string> = {
    neutral: 'border border-surface text-muted-color',
    primary: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
    success: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
    warn: 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-200',
    danger: 'bg-red-100 text-red-800 dark:bg-red-950 dark:text-red-300',
};
</script>
