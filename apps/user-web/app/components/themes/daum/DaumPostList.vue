<template>
    <!--
        다음식 목록형(`postList:LIST`) — 헤드라인형 줄: 제목 2줄(15.5/23 보통 굵기, 뒤에 [댓글 수]) + 오른쪽 썸네일 112×72(반경 8, 없으면 칸 없음) + 아래 메타 한 줄(12.5/18 흐린 글자: 작성자·날짜·조회·추천).
        줄 사이 1px 머리카락 선, 줄 위아래 10. 흰 카드(`content-card`)는 페이지가 깐다. 모바일·데스크톱이 같은 줄 모양이다.
        계약은 기본 목록과 같은 `BoardPostListProps` — 그리기만 한다. 고정 공지는 카드형·갤러리형과 같은 `BoardPinnedList`, 숨겨진 글은 자리만, 차단한 회원·단어의 글은 `FoldedRow` 로 접는다.
        community 시안 `design/_shared/botbi-news.css` `.nw-li` · `.nw-meta`.
    -->
    <div>
        <BoardPinnedList :rows="pinned" :date-display="dateDisplay" :new-post-hours="newPostHours" />
        <ul class="m-0 list-none p-0" data-testid="post-list-headline">
            <li v-for="row in rows" :key="row.id" class="border-t border-hairline first:border-t-0">
                <p v-if="row.hiddenReason" class="m-0 flex items-center gap-1.5 py-2.5 text-sm text-muted-color">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <!-- 차단한 회원·단어의 글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
                <FoldedRow v-else :reason="foldOf(row)" kind="post" class="py-2.5 text-sm" hide-class="pb-2">
                    <NuxtLink :to="`/posts/${row.id}`" class="group flex items-start gap-3 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                        <div class="min-w-0 flex-1">
                            <p class="m-0 line-clamp-2 break-keep text-[15.5px] font-normal leading-[23px] group-hover:underline">
                                <PostRowTitle :row="row" :new-post-hours="newPostHours" />
                            </p>
                            <p class="m-0 mt-[3px] flex flex-wrap items-center gap-x-1.5 text-[12.5px] leading-[18px] text-muted-color">
                                <PostAuthorLabel :author="row.author" />
                                <span aria-hidden="true">·</span>
                                <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                <span aria-hidden="true">·</span>
                                <span>{{ t('board.viewsShort', { n: row.viewCount }) }}</span>
                                <span aria-hidden="true">·</span>
                                <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                            </p>
                        </div>
                        <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="h-[72px] w-28 shrink-0 rounded-lg object-cover" loading="lazy" />
                    </NuxtLink>
                </FoldedRow>
            </li>
        </ul>
    </div>
</template>

<script setup lang="ts">
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

interface Props extends BoardPostListProps {}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const overrides = useBlockOverrides();

/** 접는 까닭 — 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다 */
function foldOf(row: PostRow) {
    return overrides.blockedOf(row.author.memberId, row.blocked);
}
</script>
