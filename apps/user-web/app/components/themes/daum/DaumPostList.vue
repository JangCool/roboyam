<template>
    <!--
        다음식 목록형(`postList:LIST`) — 헤드라인형 줄: 제목 2줄(15.5/23 보통 굵기, 뒤에 [댓글 수]) + 오른쪽 썸네일 112×72(반경 8, 없으면 칸 없음) + 아래 메타 한 줄(12.5/18 흐린 글자: 작성자·날짜·조회·추천).
        줄 사이 1px 머리카락 선, 줄 위아래 10. 흰 카드(`content-card`)는 페이지가 깐다. 모바일·데스크톱이 같은 줄 모양이다.
        계약은 기본 목록과 같은 `BoardPostListProps` — 그리기만 한다. 고정 공지는 카드형·갤러리형과 같은 `BoardPinnedList`, 숨겨진 글은 자리만, 차단한 회원·단어의 글은 `FoldedRow` 로 접는다.
        community 시안 `design/_shared/botbi-news.css` `.nw-li` · `.nw-meta`.
    -->
    <div>
        <BoardPinnedList :rows="pinned" :global="globalPinned" :date-display="dateDisplay" :new-post-hours="newPostHours" />
        <ul class="m-0 list-none p-0" data-testid="post-list-headline">
            <template v-for="(row, i) in rows" :key="row.id">
            <li class="border-t border-hairline first:border-t-0">
                <p v-if="row.hiddenReason" class="m-0 flex items-center gap-1.5 py-2.5 text-sm text-muted-color">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <!-- 차단한 회원·단어의 글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
                <FoldedRow v-else :reason="foldOf(row)" kind="post" class="py-2.5 text-sm" hide-class="pb-2">
                    <!-- 칩·숫자·날짜 자리는 칩 위치(`chipPosition`)를 따른다 — `PostRowFrame` -->
                    <NuxtLink :to="`/posts/${row.id}`" class="group block py-2.5 md:py-2 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary">
                        <!-- 데스크톱 + 「제목 위」(기본) — A 방식 두 줄(`BoardPostRowDesktop`, 사용자 시안 확정 2026-11-12). 모바일·다른 칩 위치는 아래 `PostRowFrame` -->
                        <BoardPostRowDesktop v-if="chipPosition === 'ABOVE'" :row="row" :new-post-hours="newPostHours" :date-display="dateDisplay" class="max-md:hidden">
                            <!-- 찬반 토론 글 — 날짜 뒤·추천 앞에 찬·반·중 수(섞인 목록과 같다, 2026-11-12) -->
                            <template v-if="row.debate" #stats-lead>
                                <FeedDebateCounts :debate="row.debate" class="whitespace-nowrap" />
                            </template>
                        </BoardPostRowDesktop>
                        <PostRowFrame :row="row" :position="chipPosition" stats-below :date-display="dateDisplay" :class="chipPosition === 'ABOVE' ? 'md:hidden' : ''">
                            <template v-if="row.debate" #stats-lead>
                                <FeedDebateCounts :debate="row.debate" class="whitespace-nowrap text-xs leading-none" />
                                <span aria-hidden="true" class="h-3 w-px bg-surface-300 dark:bg-surface-600" />
                            </template>
                            <template #default="{ chips, stats, dateInline, statsEnd }">
                                <p class="m-0 line-clamp-2 break-keep text-[15.5px] font-normal leading-[23px] group-hover:underline">
                                    <PostRowTitle :row="row" :new-post-hours="newPostHours" :chips="chips" />
                                </p>
                                <p class="m-0 mt-[3px] flex items-center gap-x-1.5 text-[12.5px] leading-[18px] text-muted-color" :class="statsEnd ? 'flex-nowrap' : 'flex-wrap'">
                                    <PostAuthorLabel :author="row.author" avatar />
                                    <template v-if="stats">
                                        <span aria-hidden="true">·</span>
                                        <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                        <template v-if="row.debate">
                                            <span aria-hidden="true">·</span>
                                            <FeedDebateCounts :debate="row.debate" />
                                        </template>
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
                                    <!-- 모바일 숫자 아래 + 썸네일 없음 — 추천·조회를 닉네임 줄 오른쪽 끝에(사용자 결정 2026-11-12) -->
                                    <span v-if="statsEnd" class="ml-auto inline-flex shrink-0 items-center gap-2">
                                        <template v-if="row.debate"><FeedDebateCounts :debate="row.debate" /><span aria-hidden="true" class="h-3 w-px bg-surface-300 dark:bg-surface-600" /></template>
                                        <PostRowStats :row="row" />
                                    </span>
                                </p>
                            </template>
                            <!-- 썸네일 64×44 — 모바일은 그 밑에 추천·조회(제목 두 줄 높이와 거의 같다 — 2026-11-12 사용자 원래 크기로) -->
                            <template #end>
                                <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="h-11 w-16 shrink-0 rounded-md object-cover" loading="lazy" />
                            </template>
                        </PostRowFrame>
                    </NuxtLink>
                </FoldedRow>
            </li>
            <li v-if="UtilAd.feedAdAt(feedAd, i)" class="border-t border-hairline"><AdFeedRow :ad="UtilAd.feedAdAt(feedAd, i)!" variant="headline" /></li>
            <!-- 장부 한 줄 광고가 채우지 못한 자리 — 애드센스 인피드(광고가 오지 않으면 줄째 사라진다) -->
            <AdSenseFeedRow v-else-if="UtilAdSense.feedAt(feedAdsense, i)" :spec="UtilAdSense.feedAt(feedAdsense, i)!" variant="headline" class="border-t border-hairline" />
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
