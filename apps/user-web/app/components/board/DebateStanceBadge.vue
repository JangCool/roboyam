<template>
    <!-- 하이라이트 토론의 입장 배지 — 찬성(파랑) · 반대(빨강). 댓글 머리와 「내 입장」 줄이 같이 쓴다. 글자는 상하좌우 중앙(한글 내려앉음 보정은 글자 요소에만 — components.md 2-4) -->
    <span class="inline-flex shrink-0 items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none" :class="stance === 'PRO' ? 'bg-blue-100 text-blue-800 dark:bg-blue-900/50 dark:text-blue-200' : 'bg-red-100 text-red-800 dark:bg-red-900/50 dark:text-red-200'">
        <span class="relative top-[-0.09em]">{{ t(`comment.stance.${stance}`) }}</span>
    </span>
</template>

<script setup lang="ts">
import type { DebateStance } from '~/composables/board/api/useDebateApi.types';

interface Props {
    stance: DebateStance;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
