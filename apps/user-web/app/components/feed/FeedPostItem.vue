<template>
    <!-- 게시판이 섞인 목록의 글 한 줄(목록의 <li>) — [순위] · [어제/지난주] · 게시판 이름 · 말머리 · 제목(일치 강조)·[댓글 수] / 본문 조각 / 작성자·추천·날짜.
         최신글 전체·인기글 전체·검색·회원 프로필 작성글이 함께 쓴다(community `PostRowItem` + `SearchResultItem`). 줄 전체가 글로 가는 링크라 작성자 이름은 링크가 아니다 -->
    <li class="border-b border-surface last:border-b-0" :class="ranked ? 'flex items-start' : ''">
        <span v-if="ranked" class="w-9 shrink-0 pl-4 pt-3 text-sm font-bold leading-snug tabular-nums md:pl-2" :class="rank <= 3 ? 'text-primary' : 'text-muted-color'" data-testid="post-rank">
            <span aria-hidden="true">{{ rank }}</span><span class="sr-only">{{ t('feed.rank', { n: rank }) }}</span>
        </span>
        <!-- 차단한 회원·단어의 글은 접힌 한 줄(게시판 글 목록과 같은 `FoldedRow` — 「보기」로 그 자리에서 펼친다). 순위 칸은 남겨 순서가 밀리지 않는다 -->
        <div class="min-w-0 flex-1">
            <FoldedRow :reason="foldReason" kind="post" class="py-3 text-sm" :class="padClass" :hide-class="`${padClass} pb-2`">
                <!-- 칩·추천·날짜 자리는 칩 위치(`chipPosition` — 공개 표시 기본값 `listChipPosition`)를 따른다. 앞쪽 칩([어제/지난주]·게시판 이름)도 칩과 함께 움직이고,
                     게시판 이름이 늘 있어 「제목 위」 윗줄에 「일반」 자리 칩은 두지 않는다.
                     조회 수도 보인다(기준 이상 강조는 게시판 목록과 같다 — feed-debate-counts-contract). 찬반 토론 글은 찬·반·중 수를 글자 색으로:
                     「제목 위」 윗줄 오른쪽 = 찬·반·중 → 세로 구분선 → 추천 → 조회, 그 밖의 칩 위치는 메타 줄(글쓴이 다음)에 -->
                <NuxtLink
                    :to="`/posts/${row.id}`"
                    class="block min-w-0 flex-1 py-3 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800/60"
                    :class="padClass"
                >
                    <PostRowFrame :row="row" :position="chipPosition" :general="false">
                        <template #lead><FeedPostLeadTags :row="row" :fill="fill" /></template>
                        <template v-if="row.debate" #stats-lead>
                            <FeedDebateCounts :debate="row.debate" class="text-xs leading-none" />
                            <span aria-hidden="true" class="h-3 w-px bg-surface-300 dark:bg-surface-600" />
                        </template>
                        <template #default="{ chips, stats, dateInline }">
                            <p class="line-clamp-2 break-words text-sm leading-snug">
                                <FeedPostLeadTags v-if="chips" :row="row" :fill="fill" inline />
                                <PostRowTitle :row="row" :new-post-hours="newPostHours" :pinned-mark="false" :tokens="tokens" :chips="chips" />
                            </p>
                            <!-- 본문이 걸린 검색 결과만 — 일치한 자리 앞뒤 글자(잘린 쪽의 「…」는 서버가 붙여 준다) -->
                            <p v-if="snippet" class="mt-1 line-clamp-2 break-words text-sm leading-snug text-muted-color">
                                <template v-for="(part, i) in snippetParts" :key="i"><mark v-if="part.match" class="bg-transparent font-bold text-color">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template>
                            </p>
                            <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                                <PostAuthorLabel :author="row.author" avatar />
                                <template v-if="stats">
                                    <template v-if="row.debate">
                                        <span aria-hidden="true">·</span>
                                        <FeedDebateCounts :debate="row.debate" />
                                    </template>
                                    <span aria-hidden="true">·</span>
                                    <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                                    <span aria-hidden="true">·</span>
                                    <span>{{ t('board.viewsShort', { n: row.viewCount }) }}</span>
                                    <span aria-hidden="true">·</span>
                                    <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                </template>
                                <!-- 「제목 위」 — 날짜만 닉네임(등급) 바로 뒤에(조회·추천은 윗줄 오른쪽 끝) -->
                                <template v-if="dateInline">
                                    <span aria-hidden="true">·</span>
                                    <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                                </template>
                            </p>
                        </template>
                        <template #end>
                            <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-12 shrink-0 rounded-md object-cover" loading="lazy" />
                        </template>
                    </PostRowFrame>
                </NuxtLink>
            </FoldedRow>
        </div>
    </li>
</template>

<script setup lang="ts">
import type { BoardDateDisplay, PostChipPosition } from '~/composables/board/api/useBoardApi.types';
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PopularFill } from '~/composables/home/api/useHomeApi.types';
import UtilSearchRules from '~/utils/util-search-rules';

interface Props {
    row: PostFeedRow;
    /** 날짜 표시 방식(공개 표시 기본값) */
    dateDisplay: BoardDateDisplay;
    /** 「새 글」 기준(시간) */
    newPostHours: number;
    /** 순위 번호(1부터) — 0 이면 번호 칸이 없다(인기글만 준다) */
    rank?: number;
    /** 인기글에서 다른 기간 글로 채운 줄이면 그 출처 */
    fill?: PopularFill | null;
    /** 검색 결과의 일치 강조 단어 */
    tokens?: string[];
    /** 검색 결과의 본문 일치 조각 */
    snippet?: string | null;
    /** 말머리·상태 칩 위치 — 페이지가 공개 표시 기본값의 `listChipPosition` 을 준다(기본 `ABOVE`) */
    chipPosition?: PostChipPosition;
}

const props = withDefaults(defineProps<Props>(), {
    rank: 0,
    fill: null,
    tokens: () => [],
    snippet: null,
    chipPosition: 'ABOVE',
});

const { t } = useI18n();
const overrides = useBlockOverrides();
const ranked = computed(() => props.rank > 0);
/** 줄 좌우 여백 — 순위 칸이 있으면 왼쪽을 줄인다(접힌 줄·「다시 숨기기」도 같은 여백) */
const padClass = computed(() => (ranked.value ? 'pl-1 pr-4 md:pr-2' : 'px-4 md:px-2'));
/** 접는 까닭 — 서버의 `blocked`(`USER|WORD|null`)에 이 기기에서 방금 바꾼 차단을 겹친다(게시판 글 목록과 같다) */
const foldReason = computed(() => overrides.blockedOf(props.row.author.memberId, props.row.blocked));
const snippetParts = computed(() => UtilSearchRules.highlightParts(props.snippet ?? '', props.tokens));
</script>
