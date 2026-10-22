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
        <span class="break-words">{{ row.title }}</span>
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

interface Props {
    row: PostRow;
    /** 「새 글」 기준(시간) */
    newPostHours: number;
    showPrefix?: boolean;
    /** 고정 공지에 핀 표시 */
    pinnedMark?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    showPrefix: true,
    pinnedMark: true,
});

const { t } = useI18n();
const isNew = computed(() => UtilPost.isNew(props.row.createdAt, props.newPostHours, Date.now()));
</script>
