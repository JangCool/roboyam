<template>
    <!--
        하이라이트 줄의 메타(feed-debate-counts-contract — 사용자 2026-11-10 시안) — 게시판 이름(표시색) → [찬·반·중] → [추천(엄지+숫자)] → 댓글(말풍선+숫자, 화면 읽기 「댓글 n개」)
        → 오른쪽 끝(ml-auto) [아바타 · 닉네임] · 날짜. 좁아서 넘치면 아바타·닉네임·날짜 묶음이 다음 줄로 내려간다(flex-wrap).
        · `likes` — 추천 칸(작은 목록 줄은 추천이 제목 줄 오른쪽에 있어 끈다).
        · `debate` — 찬반 토론 글의 수를 이 줄에 글자 색으로(사진 위 띠가 없을 때 — 사진 없는 글·사진을 보이지 않는 모양).
        · `author` — 가젯 설정 「작성자 보이기」(`showAuthor`, 기본 켬). 끄면 아바타·닉네임을 숨기고 날짜는 남긴다.
        줄 전체가 글 링크라 링크를 넣지 않는다(닉네임도 링크 아님)
    -->
    <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-0.5 text-xs text-muted-color" data-testid="home-highlight-meta">
        <span class="font-semibold" :class="boardStyle ? '' : 'text-color'" :style="boardStyle">{{ localizedName(post.board.name, post.board.nameEn) }}</span>
        <FeedDebateCounts v-if="debate && post.debate" :debate="post.debate" />
        <span v-if="likes" class="inline-flex items-center gap-0.5 tabular-nums">
            <i class="pi pi-thumbs-up text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('board.likesShort', { n: post.likeCount }) }}</span><span aria-hidden="true">{{ post.likeCount.toLocaleString() }}</span>
        </span>
        <span class="inline-flex items-center gap-0.5 tabular-nums">
            <i class="pi pi-comment text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('home.commentCount', { n: post.commentCount }) }}</span><span aria-hidden="true">{{ post.commentCount.toLocaleString() }}</span>
        </span>
        <span class="ml-auto inline-flex min-w-0 items-center gap-1" data-testid="home-highlight-byline">
            <template v-if="author">
                <PostAuthorLabel :author="post.author" avatar />
                <span aria-hidden="true">·</span>
            </template>
            <PostListDate :iso="post.createdAt" :mode="display.dateDisplay" class="shrink-0" />
        </span>
    </p>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';

interface Props {
    post: PostFeedRow;
    /** 추천 칸 */
    likes?: boolean;
    /** 찬반 토론 수를 이 줄에(사진 위 띠가 없을 때) */
    debate?: boolean;
    /** 작성자(아바타·닉네임) — 가젯 설정 `showAuthor` */
    author?: boolean;
}

const props = withDefaults(defineProps<Props>(), { likes: true, debate: false, author: true });

const { t } = useI18n();
const { display } = useBoardDisplay();
const { localizedName } = useLocalizedName();
const boardStyle = computed(() => UtilBoard.textStyle(props.post.board.color));
</script>
