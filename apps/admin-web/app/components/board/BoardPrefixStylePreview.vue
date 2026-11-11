<template>
    <!--
        글 줄 하나의 작은 미리보기 — 말머리 모양(mobile-nav-prefix-style-contract 1절)을 고를 때 그 모양을 보인다. 말머리 둘(색을 지정한 「사용기」 + 자동 색 「기타」) 아래 제목 한 줄.
        공개 사이트의 모바일 목록 줄을 줄여 그린 그림이라 화면 읽기 프로그램에는 숨긴다(고르개의 이름·설명이 뜻을 말한다).
        · TEXT: 칩 없이 글자 `사용기 | 기타` — 지정 색 말머리는 그 색 글자, 자동 색은 회색 글자, 세로 막대는 더 흐린 색
        · MUTED: 칩 모양 그대로 한 가지 회색(옅은 바탕 + 가는 테두리 + 흐린 글자)
        · COLOR: 지금 모양(표시색 칩, 자동은 id 로 정한 색)
    -->
    <div class="pointer-events-none select-none rounded-md border border-(--line) bg-surface-0 px-2.5 py-2 text-[11px] leading-snug dark:bg-surface-900" aria-hidden="true" data-testid="prefix-style-preview">
        <div class="mb-1 flex items-center justify-between gap-2">
            <span v-if="prefixStyle === 'TEXT'" class="flex min-w-0 items-center gap-1 text-[10px] leading-none">
                <span class="text-blue-600 dark:text-blue-400">{{ t('board.prefixPreview.first') }}</span>
                <span class="text-surface-300 dark:text-surface-600">|</span>
                <span class="text-muted-color">{{ t('board.prefixPreview.second') }}</span>
            </span>
            <span v-else class="flex min-w-0 items-center gap-1">
                <span :class="[chipClass, prefixStyle === 'MUTED' ? mutedClass : 'bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-300']">{{ t('board.prefixPreview.first') }}</span>
                <span :class="[chipClass, prefixStyle === 'MUTED' ? mutedClass : 'bg-teal-100 text-teal-800 dark:bg-teal-900/40 dark:text-teal-300']">{{ t('board.prefixPreview.second') }}</span>
            </span>
            <span class="flex shrink-0 items-center gap-1.5 text-[10px] text-muted-color tabular-nums">
                <span class="inline-flex items-center gap-0.5"><i class="pi pi-thumbs-up text-[9px]" />12</span>
                <span class="inline-flex items-center gap-0.5"><i class="pi pi-eye text-[9px]" />422</span>
            </span>
        </div>
        <p class="m-0 truncate text-xs font-medium text-color">{{ t('board.prefixPreview.title') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { BoardPrefixStyle } from '~/composables/board/api/useBoardApi.types';

interface Props {
    prefixStyle: BoardPrefixStyle;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
/** 미리보기 칩의 공통 틀 — 공개 사이트의 말머리 알약을 줄인 모양(글자 세로 맞춤은 components.md 2-4) */
const chipClass = 'inline-flex items-center justify-center rounded px-1 py-0.5 text-[9px] font-semibold leading-none';
/** 회색 칩 — 옅은 바탕 + 가는 테두리 + 흐린 글자 */
const mutedClass = 'border border-(--line) bg-surface-100 text-muted-color dark:bg-surface-800';
</script>
