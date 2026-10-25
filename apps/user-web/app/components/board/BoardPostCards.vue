<template>
    <!-- 카드형(CARD) — 썸네일 + 제목 + 작성자·날짜·추천·댓글. 모바일 1열 · 데스크톱 2열. 고정 공지는 위에 한 줄씩 -->
    <div>
        <BoardPinnedList :rows="pinned" :date-display="dateDisplay" :new-post-hours="newPostHours" />
        <ul class="m-0 mt-3 grid list-none grid-cols-1 gap-3 p-0 md:grid-cols-2">
            <li v-for="row in rows" :key="row.id" class="overflow-hidden rounded-lg border border-surface bg-surface-0 dark:bg-surface-900">
                <p v-if="row.hiddenReason" class="flex items-center gap-1.5 px-4 py-6 text-sm text-muted-color">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <FoldedRow v-else :reason="overrides.blockedOf(row.author.memberId, row.blocked)" kind="post" class="px-4 py-6 text-sm" hide-class="px-3 pb-2">
                    <NuxtLink :to="`/posts/${row.id}`" class="flex gap-3 p-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                        <div class="aspect-[4/3] w-28 shrink-0 overflow-hidden rounded-md bg-surface-100 dark:bg-surface-800">
                            <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-full object-cover" loading="lazy" />
                            <div v-else class="grid size-full place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-file" /></div>
                        </div>
                        <div class="flex min-w-0 flex-1 flex-col">
                            <p class="line-clamp-2 text-sm font-medium leading-snug"><PostRowTitle :row="row" :new-post-hours="newPostHours" /></p>
                            <p class="mt-auto flex flex-wrap items-center gap-x-1.5 pt-1 text-xs text-muted-color">
                                <PostAuthorLabel :author="row.author" />
                                <span aria-hidden="true">·</span>
                                <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                <span aria-hidden="true">·</span>
                                <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                            </p>
                        </div>
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';

interface Props extends BoardPostListProps {}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
/** 차단한 회원·단어의 글은 접힌 칸 — 서버의 `blocked`(서버 후보)에 이 기기에서 방금 바꾼 것을 겹친다 */
const overrides = useBlockOverrides();
</script>
