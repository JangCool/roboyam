<template>
    <!--
        네이버식 목록형(`postList:LIST`) — 기사형 줄: 왼쪽 썸네일 115×75(없으면 칸 없음) + 제목 2줄(15.5/23) + 아래 줄 왼쪽에 작성자·시간·조회·추천, 오른쪽에 말풍선 + 댓글 수(0 이면 숨김).
        줄 사이 1px 머리카락 선. 흰 카드(`content-card`, 반경 10 · 그림자 없음)는 페이지가 깐다. 모바일·데스크톱이 같은 줄 모양이다.
        계약은 기본 목록과 같은 `BoardPostListProps` — 그리기만 한다. 고정 공지는 카드형·갤러리형과 같은 `BoardPinnedList`, 숨겨진 글은 자리만, 차단한 회원·단어의 글은 `FoldedRow` 로 접는다.
        community 시안 `design/_shared/botbi-news.css` `.nw-art` · `.nw-cmt`(시안의 2열은 쓰지 않고 한 열이다).
    -->
    <div>
        <BoardPinnedList :rows="pinned" :global="globalPinned" :date-display="dateDisplay" :new-post-hours="newPostHours" />
        <ul class="m-0 list-none p-0" data-testid="post-list-article">
            <template v-for="(row, i) in rows" :key="row.id">
            <li class="border-b border-hairline last:border-b-0">
                <p v-if="row.hiddenReason" class="m-0 flex items-center gap-1.5 py-3.5 text-sm text-muted-color">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <!-- 차단한 회원·단어의 글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
                <FoldedRow v-else :reason="foldOf(row)" kind="post" class="py-3.5 text-sm" hide-class="pb-2">
                    <!-- 칩·숫자·날짜 자리는 칩 위치(`chipPosition`)를 따른다 — 「제목 위」 윗줄은 썸네일보다 위(줄 폭 전체), 썸네일은 이 테마대로 아래 칸 왼쪽. 말풍선 댓글 수는 메타 줄 오른쪽 그대로 -->
                    <NuxtLink :to="`/posts/${row.id}`" class="group block py-3 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:py-3.5">
                        <PostRowFrame :row="row" :position="chipPosition" row-class="gap-3.5" body-class="flex flex-col">
                            <!-- 찬반 토론 글 — 윗줄 숫자 앞에 찬·반·중 수(2026-11-12) -->
                            <template v-if="row.debate" #stats-lead>
                                <FeedDebateCounts :debate="row.debate" class="whitespace-nowrap text-xs leading-none" />
                                <span aria-hidden="true" class="h-3 w-px bg-surface-300 dark:bg-surface-600" />
                            </template>
                            <!-- 썸네일은 글 두 줄(제목 + 메타) 높이를 넘지 않는다(2026-11-06 사용자) -->
                            <template #start>
                                <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="h-11 w-16 shrink-0 rounded-md object-cover" loading="lazy" />
                            </template>
                            <template #default="{ chips, stats, dateInline }">
                                <p class="m-0 line-clamp-2 break-keep text-[15.5px] font-normal leading-[23px] group-hover:underline">
                                    <PostRowTitle :row="row" :new-post-hours="newPostHours" :comment-count="false" :chips="chips" />
                                </p>
                                <div class="mt-auto flex items-center justify-between gap-3 pt-1.5 text-[12.5px] leading-[18px] text-muted-color">
                                    <p class="m-0 flex min-w-0 flex-wrap items-center gap-x-1.5">
                                        <PostAuthorLabel :author="row.author" avatar menu :blocked="row.blocked === 'USER'" />
                                        <template v-if="stats">
                                            <span aria-hidden="true">·</span>
                                            <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                            <span aria-hidden="true">·</span>
                                            <span>{{ t('board.viewsShort', { n: row.viewCount }) }}</span>
                                            <span aria-hidden="true">·</span>
                                            <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                        </template>
                                        <!-- 「제목 위」 — 날짜만 닉네임(등급) 바로 뒤에(조회·추천은 윗줄 오른쪽 끝) -->
                                        <template v-if="dateInline">
                                            <span aria-hidden="true">·</span>
                                            <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                        </template>
                                    </p>
                                    <span v-if="row.commentCount > 0" class="inline-flex shrink-0 items-center gap-1 tabular-nums" data-testid="post-row-comments">
                                        <i class="pi pi-comment text-[0.6875rem]" aria-hidden="true" />
                                        <span class="sr-only">{{ t('board.commentCountLabel') }}</span>{{ row.commentCount.toLocaleString() }}
                                    </span>
                                </div>
                            </template>
                        </PostRowFrame>
                    </NuxtLink>
                </FoldedRow>
            </li>
            <li v-if="UtilAd.feedAdAt(feedAd, i)" class="border-b border-hairline last:border-b-0"><AdFeedRow :ad="UtilAd.feedAdAt(feedAd, i)!" variant="article" /></li>
            <!-- 장부 한 줄 광고가 채우지 못한 자리 — 애드센스 인피드(광고가 오지 않으면 줄째 사라진다) -->
            <AdSenseFeedRow v-else-if="UtilAdSense.feedAt(feedAdsense, i)" :spec="UtilAdSense.feedAt(feedAdsense, i)!" variant="article" class="border-b border-hairline last:border-b-0" />
            </template>
        </ul>
    </div>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';
import UtilAdSense from '~/utils/util-adsense';
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

interface Props extends BoardPostListProps {}

withDefaults(defineProps<Props>(), { feedAd: null, feedAdsense: null, chipPosition: 'ABOVE' });

const { t } = useI18n();
const overrides = useBlockOverrides();

/** 접는 까닭 — 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다 */
function foldOf(row: PostRow) {
    return overrides.blockedOf(row.author.memberId, row.blocked);
}
</script>
