<template>
    <!--
        글 줄 하나의 작은 미리보기 — 말머리·상태 칩 위치(post-chip-position-contract)를 고를 때 그 모양을 보인다. 공개 사이트의 모바일 목록 줄을 줄여 그린 그림이라 화면 읽기 프로그램에는 숨긴다(고르개의 이름·설명이 뜻을 말한다).
        · ABOVE: 윗줄 = 칩 + 오른쪽 끝 조회·추천·날짜 → 제목 → 글쓴이(썸네일은 제목 줄부터 오른쪽)
        · BEFORE: 칩 + 제목 한 줄 → 글쓴이·날짜·조회·추천
        · BELOW: 제목 → 글쓴이·날짜·조회·추천 → 칩 줄
    -->
    <div class="pointer-events-none select-none rounded-md border border-(--line) bg-surface-0 px-2.5 py-2 text-[11px] leading-snug dark:bg-surface-900" aria-hidden="true" data-testid="chip-position-preview">
        <div v-if="position === 'ABOVE'" class="mb-1 flex items-center justify-between gap-2">
            <span class="flex min-w-0 items-center gap-1"><span :class="prefixClass">{{ t('board.chipPreview.prefix') }}</span><span :class="statusClass">{{ t('board.chipPreview.status') }}</span></span>
            <span class="flex shrink-0 items-center gap-1.5 text-[10px] text-muted-color tabular-nums">
                <span class="inline-flex items-center gap-0.5"><i class="pi pi-eye text-[9px]" />128</span>
                <span class="inline-flex items-center gap-0.5"><i class="pi pi-thumbs-up text-[9px]" />5</span>
                <span>{{ t('board.chipPreview.date') }}</span>
            </span>
        </div>
        <div class="flex items-start gap-2">
            <div class="min-w-0 flex-1">
                <p class="m-0 truncate text-xs font-medium text-color">
                    <template v-if="position === 'BEFORE'"><span :class="[prefixClass, 'mr-1']">{{ t('board.chipPreview.prefix') }}</span><span :class="[statusClass, 'mr-1']">{{ t('board.chipPreview.status') }}</span></template>{{ t('board.chipPreview.title') }}
                </p>
                <p class="m-0 mt-0.5 flex items-center gap-1 text-[10px] text-muted-color">
                    <span class="size-3 shrink-0 rounded-full bg-surface-300 dark:bg-surface-600" />
                    <span>{{ t('board.chipPreview.author') }}</span>
                    <template v-if="position !== 'ABOVE'">
                        <span>·</span><span>{{ t('board.chipPreview.date') }}</span><span>·</span><span class="tabular-nums">128</span><span>·</span><span class="tabular-nums">5</span>
                    </template>
                </p>
                <p v-if="position === 'BELOW'" class="m-0 mt-1 flex items-center gap-1"><span :class="prefixClass">{{ t('board.chipPreview.prefix') }}</span><span :class="statusClass">{{ t('board.chipPreview.status') }}</span></p>
            </div>
            <span class="h-7 w-9 shrink-0 rounded bg-surface-200 dark:bg-surface-700" />
        </div>
    </div>
</template>

<script setup lang="ts">
import type { BoardChipPosition } from '~/composables/board/api/useBoardApi.types';

interface Props {
    position: BoardChipPosition;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
/** 미리보기 칩 — 공개 사이트의 말머리 알약·처리 상태 딱지를 줄인 모양(글자 세로 맞춤은 components.md 2-4) */
const prefixClass = 'inline-flex items-center justify-center rounded bg-blue-100 px-1 py-0.5 text-[9px] font-semibold leading-none text-blue-800 dark:bg-blue-900/40 dark:text-blue-300';
const statusClass = 'inline-flex items-center justify-center rounded border border-amber-400 px-1 py-px text-[9px] font-semibold leading-none text-amber-700 dark:border-amber-600 dark:text-amber-300';
</script>
