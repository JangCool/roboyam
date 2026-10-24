<template>
    <!-- 목록형(LIST) — 모바일: 두 줄짜리 글 줄 · 데스크톱: 표(제목·작성자·날짜·조회·추천). 고정 공지는 맨 위에 배경을 깔고, 신고로 숨겨진 글은 자리만 -->
    <div>
        <ul class="-mx-4 m-0 list-none p-0 md:hidden">
            <li v-for="row in all" :key="`${row.pinned && pinnedIds.has(row.id) ? 'p' : ''}${row.id}`" class="border-b border-surface" :class="pinnedIds.has(row.id) ? 'bg-surface-100 dark:bg-surface-800/60' : ''">
                <p v-if="row.hiddenReason" class="flex items-center gap-1.5 px-4 py-3 text-sm text-muted-color">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <!-- 차단한 회원·단어의 글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
                <FoldedRow v-else :reason="foldOf(row)" kind="post" class="px-4 py-2.5 text-sm" hide-class="px-4 pb-2">
                    <NuxtLink :to="`/posts/${row.id}`" class="flex items-start gap-3 px-4 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                        <div class="min-w-0 flex-1">
                            <p class="line-clamp-2 text-sm leading-snug"><PostRowTitle :row="row" :new-post-hours="newPostHours" /></p>
                            <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                <PostAuthorLabel :author="row.author" />
                                <span aria-hidden="true">·</span>
                                <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                <span aria-hidden="true">·</span>
                                <span>{{ t('board.viewsShort', { n: row.viewCount }) }}</span>
                                <span aria-hidden="true">·</span>
                                <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                            </p>
                        </div>
                        <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-12 shrink-0 rounded-md object-cover" loading="lazy" />
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>

        <table class="w-full table-fixed border-collapse text-sm max-md:hidden">
            <caption class="sr-only">{{ t('board.listCaption') }}</caption>
            <thead>
                <tr class="border-b border-surface text-xs text-muted-color">
                    <th scope="col" class="py-2 text-left font-medium">{{ t('board.colTitle') }}</th>
                    <th scope="col" class="w-36 py-2 text-left font-medium">{{ t('board.colAuthor') }}</th>
                    <th scope="col" class="w-20 py-2 text-center font-medium">{{ t('board.colDate') }}</th>
                    <th scope="col" class="w-14 py-2 text-right font-medium">{{ t('board.colViews') }}</th>
                    <th scope="col" class="w-14 py-2 pr-1 text-right font-medium">{{ t('board.colLikes') }}</th>
                </tr>
            </thead>
            <tbody>
                <!-- 차단한 회원·단어의 글은 표 한 줄을 통째로 접는다(펼치면 제목 칸에 「다시 숨기기」) -->
                <FoldedRow
                    v-for="row in all"
                    :key="`${pinnedIds.has(row.id) ? 'p' : ''}${row.id}`"
                    v-slot="{ revealed, hide }"
                    :reason="row.hiddenReason ? null : foldOf(row)"
                    kind="post"
                    table
                    :colspan="5"
                >
                    <tr class="border-b border-surface" :class="pinnedIds.has(row.id) ? 'bg-surface-100 dark:bg-surface-800/60' : 'hover:bg-surface-50 dark:hover:bg-surface-900'">
                        <td v-if="row.hiddenReason" colspan="5" class="py-2.5 text-muted-color">
                            <i class="pi pi-eye-slash mr-1.5 text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                        </td>
                        <template v-else>
                            <td class="py-2.5 pr-3">
                                <NuxtLink :to="`/posts/${row.id}`" class="line-clamp-1 hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                                    <PostRowTitle :row="row" :new-post-hours="newPostHours" />
                                </NuxtLink>
                                <button v-if="revealed" type="button" class="rounded px-1 py-0.5 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="folded-hide" @click="hide">
                                    {{ t('block.hideAgain') }}
                                </button>
                            </td>
                            <td class="py-2.5 text-xs text-muted-color"><PostAuthorLabel :author="row.author" link class="max-w-full" /></td>
                            <td class="py-2.5 text-center text-xs text-muted-color tabular-nums"><PostListDate :iso="row.createdAt" :mode="dateDisplay" /></td>
                            <td class="py-2.5 text-right text-xs text-muted-color tabular-nums">{{ row.viewCount.toLocaleString() }}</td>
                            <td class="py-2.5 pr-1 text-right text-xs text-muted-color tabular-nums">{{ row.likeCount.toLocaleString() }}</td>
                        </template>
                    </tr>
                </FoldedRow>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

interface Props extends BoardPostListProps {}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const overrides = useBlockOverrides();
const pinnedIds = computed(() => new Set(props.pinned.map((p) => p.id)));
const all = computed(() => [...props.pinned, ...props.rows]);

/** 접는 까닭 — 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다 */
function foldOf(row: PostRow) {
    return overrides.blockedOf(row.author.memberId, row.blocked);
}
</script>
