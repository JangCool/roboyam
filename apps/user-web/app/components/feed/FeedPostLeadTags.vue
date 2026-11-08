<template>
    <!-- 섞인 목록 글 줄의 앞쪽 칩 — [어제/지난주] · 게시판 이름(또는 「소모임」). 칩 위치(post-chip-position-contract)를 따라 `FeedPostItem` 이
         제목 앞(`inline` — 칩마다 오른쪽 여백·기준선 맞춤) 또는 칩 줄(`PostRowFrame` 의 `lead` — 줄의 간격이 사이를 띄운다)에 놓는다 -->
    <!-- 인기글에서 어제·지난주 글로 채운 줄 -->
    <span v-if="fillTag" class="inline-flex shrink-0 items-center justify-center rounded border border-surface px-1.5 py-1 text-[0.6875rem] font-medium leading-none text-muted-color" :class="chipClass" :title="fillTag.aria">
        <span class="relative top-[-0.09em]" aria-hidden="true">{{ fillTag.label }}</span><span class="sr-only">{{ fillTag.aria }}</span>
    </span>
    <!-- 공개로 전환된 소모임 글 — 게시판 이름 대신 「소모임」 표시(소모임 게시판은 공개 게시판 목록에 없다) -->
    <GroupTag v-if="row.group" tone="blue" :class="chipClass" data-testid="post-group-badge">{{ t('group.postBadge') }}</GroupTag>
    <!-- 게시판 이름 — 운영자가 고른 표시색이 있으면 그 색, 없으면 중립색 -->
    <span
        v-else
        class="inline-flex shrink-0 items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none"
        :class="[boardStyle ? '' : UtilBoard.tagClass('neutral'), chipClass]"
        :style="boardStyle"
        data-testid="post-board"
    >
        <span class="relative top-[-0.09em]"><span class="sr-only">{{ t('feed.boardTag', { name: boardName }) }}</span><span aria-hidden="true">{{ boardName }}</span></span>
    </span>
</template>

<script setup lang="ts">
import type { PostFeedRow } from '~/composables/board/api/usePostApi.types';
import type { PopularFill } from '~/composables/home/api/useHomeApi.types';
import UtilBoard from '~/utils/util-board';
import UtilFeedMessages from '~/utils/util-feed-messages';

interface Props {
    row: PostFeedRow;
    /** 인기글에서 다른 기간 글로 채운 줄이면 그 출처 */
    fill?: PopularFill | null;
    /** 제목 앞 한 줄 안(칩 위치 `BEFORE`) */
    inline?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    fill: null,
    inline: false,
});

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const chipClass = computed(() => (props.inline ? 'mr-1 align-[1px]' : ''));
const boardName = computed(() => localizedName(props.row.board.name, props.row.board.nameEn));
const boardStyle = computed(() => UtilBoard.tagStyle(props.row.board.color));
const fillTag = computed(() => (props.fill ? UtilFeedMessages.fillTag(t, props.fill) : null));
</script>
