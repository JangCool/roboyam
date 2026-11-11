<template>
    <!-- 섞인 목록 글 줄의 앞쪽 칩 — [어제/지난주] · 게시판 이름(또는 「소모임」). 칩 위치(post-chip-position-contract)를 따라 `FeedPostItem` 이
         제목 앞(`inline` — 칩마다 오른쪽 여백·기준선 맞춤) 또는 칩 줄(`PostRowFrame` 의 `lead` — 줄의 간격이 사이를 띄운다)에 놓는다.
         게시판 이름(「소모임」 자리 포함)은 말머리 모양(mobile-nav-prefix-style-contract 1절 — 섞인 목록 기본값 `listPrefixStyle`)을 따른다:
         TEXT 12px 글자(고른 색이면 그 색, 아니면 회색 — 뒤의 말머리 글자와 「|」로 이어진다) · MUTED 회색 칩 · COLOR 색상 칩. 「어제/지난주」는 기능 표시라 늘 칩이다 -->
    <!-- 인기글에서 어제·지난주 글로 채운 줄 -->
    <span v-if="fillTag" class="inline-flex shrink-0 items-center justify-center rounded border border-surface px-1.5 py-1 text-[0.6875rem] font-medium leading-none text-muted-color" :class="chipClass" :title="fillTag.aria">
        <span class="relative top-[-0.09em]" aria-hidden="true">{{ fillTag.label }}</span><span class="sr-only">{{ fillTag.aria }}</span>
    </span>
    <!-- 글자 모양 — 게시판 이름(공개로 전환된 소모임 글은 「소모임」) -->
    <span
        v-if="style === 'TEXT'"
        class="prefix-text shrink-0 text-xs font-normal"
        :class="[textStyle ? '' : 'text-muted-color', inline ? 'mr-1' : '']"
        :style="textStyle"
        :data-inline="inline ? '' : undefined"
        :data-testid="row.group ? 'post-group-badge' : 'post-board'"
        ><span class="sr-only">{{ srText }}</span><span aria-hidden="true">{{ shownName }}</span></span
    >
    <!-- 회색 칩 -->
    <span
        v-else-if="style === 'MUTED'"
        class="inline-flex shrink-0 items-center justify-center rounded border border-surface bg-surface-50 px-1.5 py-[3px] text-[0.6875rem] font-semibold leading-none text-muted-color dark:bg-surface-900"
        :class="chipClass"
        :data-testid="row.group ? 'post-group-badge' : 'post-board'"
    >
        <span class="relative top-[-0.09em]"><span class="sr-only">{{ srText }}</span><span aria-hidden="true">{{ shownName }}</span></span>
    </span>
    <!-- 색상 칩 — 공개로 전환된 소모임 글은 게시판 이름 대신 「소모임」 표시(소모임 게시판은 공개 게시판 목록에 없다) -->
    <GroupTag v-else-if="row.group" tone="blue" :class="chipClass" data-testid="post-group-badge">{{ t('group.postBadge') }}</GroupTag>
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
/** 게시판 이름 모양 — 섞인 목록 기본값(위에서 건 값이 있으면 그것) */
const style = usePrefixStyle();
const chipClass = computed(() => (props.inline ? 'mr-1 align-[1px]' : ''));
const boardName = computed(() => localizedName(props.row.board.name, props.row.board.nameEn));
const boardStyle = computed(() => UtilBoard.tagStyle(props.row.board.color));
/** 글자·회색 칩 모양에서 보일 이름 — 소모임 글은 「소모임」 */
const shownName = computed(() => (props.row.group ? t('group.postBadge') : boardName.value));
const srText = computed(() => (props.row.group ? t('group.postBadge') : t('feed.boardTag', { name: boardName.value })));
/** 글자 모양의 글자색 — 게시판 표시색이 있으면 그 색(소모임 글은 회색) */
const textStyle = computed(() => (props.row.group ? undefined : UtilBoard.prefixTextStyle(props.row.board.color)));
const fillTag = computed(() => (props.fill ? UtilFeedMessages.fillTag(t, props.fill) : null));
</script>
