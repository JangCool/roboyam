<template>
    <!--
        게시판 글 목록 맨 위의 전체 공지(공지 게시판 고정글을 모든 게시판 맨 위에도 — notice-admin-contract 「전체 공지」, 사용자 확정 2026-11-08).
        한 줄씩: 옅은 주 색상 바탕 + 「공지」 칩(확성기) + 제목(한 줄 말줄임) + 날짜, 누르면 원래 공지 글(`/posts/{id}`).
        목록형·카드형·갤러리형·핫딜 목록·테마 목록이 모두 이 줄을 그 게시판 고정글(`BoardPinnedList`·목록 안 고정 줄)보다 위에 그린다. 서버가 첫 쪽·거르기 없음일 때만 준다.
    -->
    <ul v-if="rows.length" class="-mx-4 m-0 list-none p-0 md:mx-0" :aria-label="t('board.globalNoticeList')" data-testid="board-global-notices" data-cache-area="notice.global">
        <li v-for="row in rows" :key="row.id" class="border-b border-surface bg-primary-50 dark:bg-primary-950/40">
            <NuxtLink :to="`/posts/${row.id}`" class="flex items-center gap-2 px-4 py-2.5 text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-3">
                <span class="inline-flex shrink-0 items-center gap-1 rounded bg-primary px-1.5 py-0.5 text-[11px] font-semibold leading-none text-primary-contrast">
                    <i class="pi pi-megaphone text-[10px]" aria-hidden="true" />{{ t('board.globalNoticeChip') }}
                </span>
                <span class="min-w-0 flex-1 truncate font-medium hover:underline">{{ row.title }}</span>
                <PostListDate :iso="row.createdAt" :mode="dateDisplay" class="shrink-0 text-xs text-muted-color" />
            </NuxtLink>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { GlobalNoticeRow } from '~/composables/board/api/usePostApi.types';

interface Props {
    rows?: GlobalNoticeRow[];
    dateDisplay: BoardDateDisplay;
}

withDefaults(defineProps<Props>(), { rows: () => [] });

const { t } = useI18n();
</script>
