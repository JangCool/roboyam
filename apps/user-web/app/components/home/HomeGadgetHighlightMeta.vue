<template>
    <!-- 하이라이트 줄의 메타 — 게시판 이름(표시색) · 시간 · 댓글 수(`bubble` 이면 줄 끝 말풍선, 아니면 「댓글 n개」). 줄 전체가 글 링크라 링크를 넣지 않는다 -->
    <p class="m-0 flex flex-wrap items-center gap-x-1 gap-y-0.5 text-xs text-muted-color">
        <span class="font-semibold" :class="boardStyle ? '' : 'text-color'" :style="boardStyle">{{ localizedName(post.board.name, post.board.nameEn) }}</span>
        <span aria-hidden="true">·</span>
        <PostListDate :iso="post.createdAt" :mode="display.dateDisplay" />
        <template v-if="post.commentCount > 0">
            <span v-if="bubble" class="ml-auto inline-flex items-baseline gap-1">
                <i class="pi pi-comment text-[0.6875rem]" aria-hidden="true" /><span class="sr-only">{{ t('home.commentCount', { n: post.commentCount }) }}</span><span aria-hidden="true">{{ post.commentCount }}</span>
            </span>
            <template v-else>
                <span aria-hidden="true">·</span>
                <span>{{ t('home.commentCount', { n: post.commentCount }) }}</span>
            </template>
        </template>
    </p>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';

interface Props {
    post: PostFeedRow;
    /** 댓글 수를 줄 끝 말풍선으로 */
    bubble?: boolean;
}

const props = withDefaults(defineProps<Props>(), { bubble: false });

const { t } = useI18n();
const { display } = useBoardDisplay();
const { localizedName } = useLocalizedName();
const boardStyle = computed(() => UtilBoard.textStyle(props.post.board.color));
</script>
