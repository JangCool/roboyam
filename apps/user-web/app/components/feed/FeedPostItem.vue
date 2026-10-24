<template>
    <!-- 게시판이 섞인 목록의 글 한 줄(목록의 <li>) — [순위] · [어제/지난주] · 게시판 이름 · 말머리 · 제목(일치 강조)·[댓글 수] / 본문 조각 / 작성자·추천·날짜.
         최신글 전체·인기글 전체·검색·회원 프로필 작성글이 함께 쓴다(community `PostRowItem` + `SearchResultItem`). 줄 전체가 글로 가는 링크라 작성자 이름은 링크가 아니다 -->
    <li class="border-b border-surface last:border-b-0" :class="ranked ? 'flex items-start' : ''">
        <span v-if="ranked" class="w-9 shrink-0 pl-4 pt-3 text-sm font-bold leading-snug tabular-nums md:pl-2" :class="rank <= 3 ? 'text-primary' : 'text-muted-color'" data-testid="post-rank">
            <span aria-hidden="true">{{ rank }}</span><span class="sr-only">{{ t('feed.rank', { n: rank }) }}</span>
        </span>
        <NuxtLink
            :to="`/posts/${row.id}`"
            class="flex min-w-0 flex-1 items-start gap-3 py-3 hover:bg-surface-100 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary dark:hover:bg-surface-800/60"
            :class="ranked ? 'pl-1 pr-4 md:pr-2' : 'px-4 md:px-2'"
        >
            <div class="min-w-0 flex-1">
                <p class="line-clamp-2 break-words text-sm leading-snug">
                    <!-- 인기글에서 어제·지난주 글로 채운 줄 -->
                    <span v-if="fillTag" class="mr-1 inline-flex items-center justify-center rounded border border-surface px-1.5 py-1 align-[1px] text-[0.6875rem] font-medium leading-none text-muted-color" :title="fillTag.aria">
                        <span class="relative top-[-0.09em]" aria-hidden="true">{{ fillTag.label }}</span><span class="sr-only">{{ fillTag.aria }}</span>
                    </span>
                    <!-- 게시판 이름 — 운영자가 고른 표시색이 있으면 그 색, 없으면 중립색 -->
                    <span
                        class="mr-1 inline-flex items-center justify-center rounded px-1.5 py-1 align-[1px] text-[0.6875rem] font-semibold leading-none"
                        :class="boardStyle ? '' : UtilBoard.tagClass('neutral')"
                        :style="boardStyle"
                        data-testid="post-board"
                    >
                        <span class="relative top-[-0.09em]"><span class="sr-only">{{ t('feed.boardTag', { name: boardName }) }}</span><span aria-hidden="true">{{ boardName }}</span></span>
                    </span>
                    <PostRowTitle :row="row" :new-post-hours="newPostHours" :pinned-mark="false" :tokens="tokens" />
                </p>
                <!-- 본문이 걸린 검색 결과만 — 일치한 자리 앞뒤 글자(잘린 쪽의 「…」는 서버가 붙여 준다) -->
                <p v-if="snippet" class="mt-1 line-clamp-2 break-words text-sm leading-snug text-muted-color">
                    <template v-for="(part, i) in snippetParts" :key="i"><mark v-if="part.match" class="bg-transparent font-bold text-color">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template>
                </p>
                <p class="mt-1 flex flex-wrap items-center gap-x-1.5 text-xs text-muted-color">
                    <PostAuthorLabel :author="row.author" />
                    <span aria-hidden="true">·</span>
                    <span>{{ t('board.likesShort', { n: row.likeCount }) }}</span>
                    <span aria-hidden="true">·</span>
                    <PostListDate :iso="row.createdAt" :mode="dateDisplay" />
                </p>
            </div>
            <img v-if="row.thumbnailUrl" :src="row.thumbnailUrl" alt="" class="size-12 shrink-0 rounded-md object-cover" loading="lazy" />
        </NuxtLink>
    </li>
</template>

<script setup lang="ts">
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PopularFill } from '~/composables/home/api/useHomeApi.types';
import UtilBoard from '~/utils/util-board';
import UtilFeedMessages from '~/utils/util-feed-messages';
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
}

const props = withDefaults(defineProps<Props>(), {
    rank: 0,
    fill: null,
    tokens: () => [],
    snippet: null,
});

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const ranked = computed(() => props.rank > 0);
const boardName = computed(() => localizedName(props.row.board.name, props.row.board.nameEn));
const boardStyle = computed(() => UtilBoard.tagStyle(props.row.board.color));
const fillTag = computed(() => (props.fill ? UtilFeedMessages.fillTag(t, props.fill) : null));
const snippetParts = computed(() => UtilSearchRules.highlightParts(props.snippet ?? '', props.tokens));
</script>
