<template>
    <!--
        하이라이트 줄의 메타(feed-debate-counts-contract — 사용자 2026-11-10 시안). 두 가지 차례:
        · `order="board"`(큰 카드·그 밖 모양): 게시판 이름(표시색) → [찬·반·중] → [추천] → 댓글 → 오른쪽 끝(ml-auto) [아바타 · 닉네임] · 날짜.
        · `order="author"`(로보얌 오른쪽 작은 목록 줄 — 사용자 2026-11-10 최종): [게시판 이름 — 사진 위 라벨이 없을 때만] → [아바타 · 닉네임] → 추천 → 댓글 → 오른쪽 끝 날짜.
          게시판 이름은 사진 왼쪽 아래 라벨로 옮기고, 추천·댓글은 닉네임 바로 오른쪽에 붙인다.
        · `likes` — 추천 칸. `board` — 게시판 이름 칸. `debate` — 찬반 토론 수를 이 줄에 글자 색으로(사진 위 라벨이 없을 때 — 사진 없는 글·사진을 보이지 않는 모양).
        · `author` — 가젯 설정 「작성자 보이기」(`showAuthor`, 기본 켬). 끄면 아바타·닉네임을 숨기고 날짜는 남긴다.
        좁아서 넘치면 `board` 차례는 다음 줄로 내려가고(flex-wrap), `author` 차례는 한 줄을 지키며 닉네임을 말줄임한다(날짜가 떨어지지 않게). 줄 전체가 글 링크라 링크를 넣지 않는다(닉네임도 링크 아님)
    -->
    <p class="m-0 flex items-center gap-x-2 gap-y-0.5 text-xs text-muted-color" :class="order === 'author' ? 'flex-nowrap' : 'flex-wrap'" data-testid="home-highlight-meta">
        <template v-if="order === 'author'">
            <span v-if="board" class="shrink-0 font-semibold" :class="boardStyle ? '' : 'text-color'" :style="boardStyle">{{ localizedName(post.board.name, post.board.nameEn) }}</span>
            <FeedDebateCounts v-if="debate && post.debate" :debate="post.debate" class="shrink-0" />
            <PostAuthorLabel v-if="author" :author="post.author" avatar :grade="false" class="min-w-0" data-testid="home-highlight-byline" />
            <span v-if="likes" class="inline-flex shrink-0 items-center gap-0.5 tabular-nums" :class="hotLikes ? 'font-semibold' : ''" :style="hotLikes ? hotStyle : undefined" :data-hot="hotLikes ? 'true' : undefined" data-testid="home-highlight-likes">
                <i :class="hotLikes ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'" class="text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('board.likesShort', { n: post.likeCount }) }}</span><span aria-hidden="true">{{ post.likeCount.toLocaleString() }}</span>
            </span>
            <span v-if="comments" class="inline-flex shrink-0 items-center gap-0.5 tabular-nums">
                <i class="pi pi-comment text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('home.commentCount', { n: post.commentCount }) }}</span><span aria-hidden="true">{{ post.commentCount.toLocaleString() }}</span>
            </span>
            <PostListDate :iso="post.createdAt" :mode="display.dateDisplay" class="ml-auto shrink-0" />
        </template>
        <template v-else>
            <span v-if="board" class="font-semibold" :class="boardStyle ? '' : 'text-color'" :style="boardStyle">{{ localizedName(post.board.name, post.board.nameEn) }}</span>
            <FeedDebateCounts v-if="debate && post.debate" :debate="post.debate" />
            <span v-if="likes" class="inline-flex items-center gap-0.5 tabular-nums" :class="hotLikes ? 'font-semibold' : ''" :style="hotLikes ? hotStyle : undefined" :data-hot="hotLikes ? 'true' : undefined">
                <i :class="hotLikes ? 'pi pi-thumbs-up-fill' : 'pi pi-thumbs-up'" class="text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('board.likesShort', { n: post.likeCount }) }}</span><span aria-hidden="true">{{ post.likeCount.toLocaleString() }}</span>
            </span>
            <span class="inline-flex items-center gap-0.5 tabular-nums">
                <i class="pi pi-comment text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('home.commentCount', { n: post.commentCount }) }}</span><span aria-hidden="true">{{ post.commentCount.toLocaleString() }}</span>
            </span>
            <span class="ml-auto inline-flex min-w-0 items-center gap-1" data-testid="home-highlight-byline">
                <template v-if="author">
                    <PostAuthorLabel :author="post.author" avatar :grade="false" />
                    <span aria-hidden="true">·</span>
                </template>
                <PostListDate :iso="post.createdAt" :mode="display.dateDisplay" class="shrink-0" />
            </span>
        </template>
    </p>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';
import UtilColor from '~/utils/util-color';

interface Props {
    post: PostFeedRow;
    /** 칸 차례 — `board`(게시판 먼저, 작성자·날짜는 오른쪽 끝) · `author`(작성자 → 추천·댓글, 날짜만 오른쪽 끝) */
    order?: 'board' | 'author';
    /** 게시판 이름 칸 */
    board?: boolean;
    /** 추천 칸 */
    likes?: boolean;
    /** 댓글 칸 — 로보얌 오른쪽 작은 목록 줄은 댓글 수를 제목 뒤 「[n]」로 옮겨 끈다(`author` 차례만) */
    comments?: boolean;
    /** 찬반 토론 수를 이 줄에(사진 위 라벨이 없을 때) */
    debate?: boolean;
    /** 작성자(아바타·닉네임) — 가젯 설정 `showAuthor` */
    author?: boolean;
}

const props = withDefaults(defineProps<Props>(), { order: 'board', board: true, likes: true, comments: true, debate: false, author: true });

const { t } = useI18n();
const { display } = useBoardDisplay();
const { localizedName } = useLocalizedName();
const boardStyle = computed(() => UtilBoard.textStyle(props.post.board.color));
/** 추천이 운영자 강조 기준 이상인가 — 게시판 글 목록(`PostRowStats`)과 같은 규칙(기준 0 = 끔, 강조 색 기본 빨강, 채운 엄지) */
const hotLikes = computed(() => UtilBoard.isHighlighted(props.post.likeCount, display.value.likeHighlight));
const hotStyle = computed(() => ({ color: UtilColor.cssPair(display.value.highlightColor) ?? UtilColor.cssPair(UtilBoard.HIGHLIGHT_COLOR) ?? undefined }));
</script>
