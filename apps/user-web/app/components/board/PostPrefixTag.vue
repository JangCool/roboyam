<template>
    <!--
        말머리 — 모양(mobile-nav-prefix-style-contract 1절)은 위에서 건 값(`providePrefixStyle` — 게시판 목록·소모임 글 목록) 또는 섞인 목록 기본값(`usePrefixStyle`), `mode` 를 주면 그것.
          · TEXT  칩 없이 12px 글자 — 운영자가 고른 색이면 그 색 글자, 「자동」·운영진 전용은 회색. 이어진 글자 사이 「|」·제목 앞이면 뒤에 「|」는 `main.css` `.prefix-text`
          · MUTED 회색 칩(옅은 바탕 + 가는 테두리 + 흐린 글자) — 지정 색은 무시
          · COLOR 색상 칩 — 운영자가 고른 표시색이 있으면 그 색, 없으면 id 로 정한 색, 운영진 전용은 회색
        칩 글자 세로 맞춤은 components.md 2-4
    -->
    <span v-if="style === 'TEXT'" class="prefix-text text-xs font-normal" :class="textStyle ? '' : 'text-muted-color'" :style="textStyle" :data-inline="inline ? '' : undefined" data-prefix-style="TEXT">{{ prefix.name }}</span>
    <span
        v-else-if="style === 'MUTED'"
        class="inline-flex items-center justify-center rounded border border-surface bg-surface-50 px-1.5 py-[3px] text-[0.6875rem] font-semibold leading-none text-muted-color dark:bg-surface-900"
        data-prefix-style="MUTED"
    >
        <span class="relative top-[-0.09em]">{{ prefix.name }}</span>
    </span>
    <span
        v-else
        class="inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none"
        :class="tagStyle ? '' : UtilBoard.tagClass(UtilBoard.prefixTone(prefix))"
        :style="tagStyle"
        data-prefix-style="COLOR"
    >
        <span class="relative top-[-0.09em]">{{ prefix.name }}</span>
    </span>
</template>

<script setup lang="ts">
import type { PostPrefixStyle } from '~/composables/board/api/useBoardApi.types';
import type { PostPrefixRef } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';

interface Props {
    prefix: PostPrefixRef;
    /** 제목 앞 한 줄 안(칩 위치 `BEFORE`·한 줄짜리 자리) — 글자 모양이면 뒤에 「|」 */
    inline?: boolean;
    /** 모양을 직접 정한다(글 보기 머리 — 게시판 정보의 `prefixStyle`). 없으면 위에서 건 값·섞인 목록 기본값 */
    mode?: PostPrefixStyle | null;
}

const props = withDefaults(defineProps<Props>(), { inline: false, mode: null });

const contextStyle = usePrefixStyle();
const style = computed<PostPrefixStyle>(() => props.mode ?? contextStyle.value);
const tagStyle = computed(() => UtilBoard.tagStyle(props.prefix.color, props.prefix.staffOnly));
const textStyle = computed(() => UtilBoard.prefixTextStyle(props.prefix.color, props.prefix.staffOnly));
</script>
