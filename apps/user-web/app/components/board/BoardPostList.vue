<template>
    <!--
        목록형(LIST) — 칩 위치(`chipPosition`, post-chip-position-contract)에 따라:
        · 제목 위(`ABOVE`)·닉네임 아래(`BELOW`): 모바일·데스크톱 모두 여러 줄 글 줄(`PostRowFrame` — 데스크톱은 여백·제목 글자·썸네일만 조금 크게). 표는 그리지 않는다.
        · 제목 앞(`BEFORE`): 모바일은 같은 글 줄(칩이 제목 앞), 데스크톱은 표(제목·작성자·날짜·조회·추천).
        고정 공지는 맨 위에 배경을 깔고, 신고로 숨겨진 글은 자리만. 전체 공지(`globalPinned`)는 줄·표보다 위에 「공지」 줄(`BoardGlobalNoticeList`)
    -->
    <div>
        <BoardGlobalNoticeList :rows="globalPinned" :date-display="dateDisplay" />
        <ul class="-mx-4 m-0 list-none p-0" :class="rowsEverywhere ? 'md:mx-0' : 'md:hidden'" data-testid="post-list-rows">
            <template v-for="(row, i) in all" :key="`${row.pinned && pinnedIds.has(row.id) ? 'p' : ''}${row.id}`">
            <li class="border-b border-surface" :class="pinnedIds.has(row.id) ? 'bg-surface-100 dark:bg-surface-800/60' : ''">
                <p v-if="row.hiddenReason" class="flex items-center gap-1.5 px-4 py-3 text-sm text-muted-color md:px-2">
                    <i class="pi pi-eye-slash text-xs" aria-hidden="true" />{{ t(row.hiddenReason === 'STAFF' ? 'board.hiddenPostStaff' : 'board.hiddenPost') }}
                </p>
                <!-- 차단한 회원·단어의 글은 접힌 한 줄(「보기」로 그 자리에서 펼친다) -->
                <FoldedRow v-else :reason="foldOf(row)" kind="post" class="px-4 py-2.5 text-sm md:px-2" hide-class="px-4 pb-2 md:px-2">
                    <!-- 칩·숫자·날짜 자리는 칩 위치(`chipPosition`)를 따른다 — `PostRowFrame` -->
                    <NuxtLink :to="`/posts/${row.id}`" class="block px-4 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary md:px-2 md:py-3">
                        <PostRowFrame :row="row" :position="chipPosition">
                            <template #default="{ chips, stats, dateInline }">
                                <p class="line-clamp-2 text-sm leading-snug md:text-[0.9375rem]"><PostRowTitle :row="row" :new-post-hours="newPostHours" :chips="chips" /></p>
                                <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                    <PostAuthorLabel :author="row.author" avatar />
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
                            </template>
                            <template #end>
                                <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-12 shrink-0 rounded-md object-cover md:h-12 md:w-16" loading="lazy" />
                            </template>
                        </PostRowFrame>
                    </NuxtLink>
                </FoldedRow>
            </li>
            <li v-if="UtilAd.feedAdAt(feedAd, i - pinned.length)" class="border-b border-surface"><AdFeedRow :ad="UtilAd.feedAdAt(feedAd, i - pinned.length)!" variant="compact" /></li>
            <!-- 장부 한 줄 광고가 채우지 못한 자리 — 애드센스 인피드(광고가 오지 않으면 줄째 사라진다) -->
            <AdSenseFeedRow v-else-if="UtilAdSense.feedAt(feedAdsense, i - pinned.length)" :spec="UtilAdSense.feedAt(feedAdsense, i - pinned.length)!" variant="compact" class="border-b border-surface" />
            </template>
        </ul>

        <!-- 데스크톱 표 — 칩 위치가 제목 앞(`BEFORE`)일 때만 -->
        <table v-if="!rowsEverywhere" class="w-full table-fixed border-collapse text-sm max-md:hidden">
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
                <template v-for="(row, i) in all" :key="`${pinnedIds.has(row.id) ? 'p' : ''}${row.id}`">
                <FoldedRow
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
                            <td class="py-2.5 text-xs text-muted-color"><PostAuthorLabel :author="row.author" avatar link class="max-w-full" /></td>
                            <td class="py-2.5 text-center text-xs text-muted-color tabular-nums"><PostListDate :iso="row.createdAt" :mode="dateDisplay" /></td>
                            <td class="py-2.5 text-right text-xs text-muted-color tabular-nums">{{ row.viewCount.toLocaleString() }}</td>
                            <td class="py-2.5 pr-1 text-right text-xs text-muted-color tabular-nums">{{ row.likeCount.toLocaleString() }}</td>
                        </template>
                    </tr>
                </FoldedRow>
                <AdFeedRow v-if="UtilAd.feedAdAt(feedAd, i - pinned.length)" :ad="UtilAd.feedAdAt(feedAd, i - pinned.length)!" variant="table" />
                <AdSenseFeedRow v-else-if="UtilAdSense.feedAt(feedAdsense, i - pinned.length)" :spec="UtilAdSense.feedAt(feedAdsense, i - pinned.length)!" variant="table" class="border-b border-surface" />
                </template>
            </tbody>
        </table>
    </div>
</template>

<script setup lang="ts">
import UtilAd from '~/utils/util-ad';
import UtilAdSense from '~/utils/util-adsense';
import type { BoardPostListProps } from '~/components/board/BoardPostList.types';
import type { PostRow } from '~/composables/board/api/usePostApi.types';

interface Props extends BoardPostListProps {}

const props = withDefaults(defineProps<Props>(), { feedAd: null, feedAdsense: null, chipPosition: 'ABOVE' });

const { t } = useI18n();
const overrides = useBlockOverrides();
const pinnedIds = computed(() => new Set(props.pinned.map((p) => p.id)));
const all = computed(() => [...props.pinned, ...props.rows]);
/** 데스크톱에서도 글 줄로 그리는가 — 칩 위치가 제목 앞이 아니면 표 대신 모바일과 같은 줄 모양 */
const rowsEverywhere = computed(() => props.chipPosition !== 'BEFORE');
/** `all` 에서 한 줄 광고가 뒤에 붙는 차례 — 공지는 세지 않는다 */

/** 접는 까닭 — 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다 */
function foldOf(row: PostRow) {
    return overrides.blockedOf(row.author.memberId, row.blocked);
}
</script>
