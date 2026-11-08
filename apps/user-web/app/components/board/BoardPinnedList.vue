<template>
    <!-- 카드형·갤러리형 위의 고정 공지 — 한 줄씩(제목·날짜). 목록형은 표·줄 안에 고정 공지를 같이 그린다. 전체 공지(`global`)는 그 위에 「공지」 줄 -->
    <BoardGlobalNoticeList :rows="global" :date-display="dateDisplay" />
    <ul v-if="rows.length" class="-mx-4 m-0 list-none p-0 md:mx-0">
        <li v-for="row in rows" :key="row.id" class="border-b border-surface bg-surface-100 dark:bg-surface-800/60">
            <NuxtLink :to="`/posts/${row.id}`" class="flex items-center gap-2 px-4 py-2.5 text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-3">
                <span class="min-w-0 flex-1 truncate"><PostRowTitle :row="row" :new-post-hours="newPostHours" /></span>
                <PostListDate :iso="row.createdAt" :mode="dateDisplay" class="shrink-0 text-xs text-muted-color" />
            </NuxtLink>
        </li>
    </ul>
</template>

<script setup lang="ts">
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { GlobalNoticeRow, PostRow } from '~/composables/board/api/usePostApi.types';

interface Props {
    rows: PostRow[];
    /** 전체 공지 — 고정 공지보다 위 */
    global?: GlobalNoticeRow[];
    dateDisplay: BoardDateDisplay;
    newPostHours: number;
}

const props = withDefaults(defineProps<Props>(), { global: () => [] });
</script>
