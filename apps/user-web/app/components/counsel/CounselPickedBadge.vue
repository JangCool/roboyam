<template>
    <!-- 「가장 도움 된 조언」 — 사연 쓴 사람이 고른 답의 작성자 줄(AI 상담사 답·회원 답 모두). 글자가 늘 있다(색만으로 가르지 않는다). 세로 맞춤 components.md 2-4 -->
    <span
        class="inline-flex shrink-0 items-baseline justify-center gap-1 whitespace-nowrap rounded bg-green-600 px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-white dark:bg-green-500 dark:text-green-950"
        data-testid="counsel-picked"
    >
        <i class="pi pi-check text-[0.625rem]" aria-hidden="true" /><span class="chip-label">{{ t('counsel.picked') }}</span>
    </span>
</template>

<script setup lang="ts">
const { t } = useI18n();
</script>
