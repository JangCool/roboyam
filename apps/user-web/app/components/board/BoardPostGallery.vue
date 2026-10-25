<template>
    <!-- 갤러리형(GALLERY) — 정사각 썸네일 격자(모바일 2열 · 데스크톱 4열) + 제목·작성자·날짜. 고정 공지는 위에 한 줄씩 -->
    <div>
        <BoardPinnedList :rows="pinned" :date-display="dateDisplay" :new-post-hours="newPostHours" />
        <ul class="m-0 mt-3 grid list-none grid-cols-2 gap-x-3 gap-y-4 p-0 md:grid-cols-4">
            <li v-for="row in rows" :key="row.id" class="min-w-0">
                <div v-if="row.hiddenReason" class="grid aspect-square place-items-center rounded-lg bg-surface-100 px-2 text-center text-xs text-muted-color dark:bg-surface-800">
                    <span><i class="pi pi-eye-slash mb-1 block text-sm" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}</span>
                </div>
                <FoldedRow
                    v-else
                    :reason="overrides.blockedOf(row.author.memberId, row.blocked)"
                    kind="post"
                    class="aspect-square content-center justify-center rounded-lg bg-surface-100 px-2 text-center text-xs dark:bg-surface-800"
                    hide-class="pt-1 text-center"
                >
                    <NuxtLink :to="`/posts/${row.id}`" class="group block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                        <div class="aspect-square overflow-hidden rounded-lg bg-surface-100 dark:bg-surface-800">
                            <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-full object-cover transition-transform group-hover:scale-[1.03]" loading="lazy" />
                            <div v-else class="grid size-full place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-image text-xl" /></div>
                        </div>
                        <p class="mt-1.5 line-clamp-2 text-sm leading-snug"><PostRowTitle :row="row" :new-post-hours="newPostHours" :show-prefix="false" /></p>
                        <p class="mt-0.5 flex flex-wrap items-center gap-x-1 text-xs text-muted-color">
                            <PostAuthorLabel :author="row.author" />
                            <span aria-hidden="true">·</span>
                            <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                        </p>
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
