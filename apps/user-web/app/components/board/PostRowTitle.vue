<template>
    <!-- 글 줄의 제목 부분 — 말머리 · 19 · 제목 · 이미지 표시 · [댓글 수] · 새 글. 세 가지 목록 모양(LIST·CARD·GALLERY)이 같이 쓴다 -->
    <span>
        <i v-if="row.pinned && pinnedMark" class="pi pi-thumbtack mr-1 text-xs text-muted-color" aria-hidden="true" />
        <span v-if="row.pinned && pinnedMark" class="sr-only">{{ t('board.pinned') }}</span>
        <PostPrefixTag v-if="row.prefix && showPrefix" :prefix="row.prefix" class="mr-1 align-[1px]" />
        <span v-if="row.adult" class="mr-1 inline-flex size-4 items-center justify-center rounded-full bg-red-600 align-[1px] text-[0.5625rem] font-bold leading-none text-white" :title="t('board.adultMark')">
            <span class="relative top-[-0.09em]" aria-hidden="true">19</span>
            <span class="sr-only">{{ t('board.adultMark') }}</span>
        </span>
        <span v-if="tokens.length" class="break-words"><template v-for="(part, i) in titleParts" :key="i"><mark v-if="part.match" class="rounded-sm bg-transparent font-bold text-primary">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span>
        <span v-else class="break-words">{{ row.title }}</span>
        <i v-if="row.hasImage" class="pi pi-image ml-1 text-xs text-muted-color" :title="t('board.hasImage')" aria-hidden="true" />
        <span v-if="row.hasImage" class="sr-only">{{ t('board.hasImage') }}</span>
        <span v-if="row.commentCount > 0" class="ml-1 text-xs font-semibold text-primary">
            <span class="sr-only">{{ t('board.commentCountLabel') }}</span>[{{ row.commentCount }}]
        </span>
        <span v-if="isNew" class="ml-1 inline-flex size-4 items-center justify-center rounded-full bg-primary align-[1px] text-[0.5625rem] font-bold leading-none text-primary-contrast" :title="t('board.newMark')">
            <span class="relative top-[-0.09em]" aria-hidden="true">N</span>
            <span class="sr-only">{{ t('board.newMark') }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
import type { PostRow } from '~/composables/board/api/usePostApi.types';
import UtilPost from '~/utils/util-post';
import UtilSearchRules from '~/utils/util-search-rules';

interface Props {
    row: PostRow;
    /** 「새 글」 기준(시간) */
    newPostHours: number;
    showPrefix?: boolean;
    /** 고정 공지에 핀 표시 */
    pinnedMark?: boolean;
    /** 검색 결과의 일치 강조 단어(검색 응답의 `tokens`) — 비면 강조하지 않는다 */
    tokens?: string[];
}

const props = withDefaults(defineProps<Props>(), {
    showPrefix: true,
    pinnedMark: true,
    tokens: () => [],
});

const { t } = useI18n();
const titleParts = computed(() => UtilSearchRules.highlightParts(props.row.title, props.tokens));
const isNew = computed(() => UtilPost.isNew(props.row.createdAt, props.newPostHours, Date.now()));
</script>
