<template>
    <!-- 말머리 알약 — 운영자가 고른 표시색이 있으면 그 색, 없으면 id 로 정한 색, 운영진 전용은 회색. 글자 세로 맞춤은 components.md 2-4 -->
    <span class="inline-flex items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none" :class="style ? '' : UtilBoard.tagClass(UtilBoard.prefixTone(prefix))" :style="style">
        <span class="relative top-[-0.09em]">{{ prefix.name }}</span>
    </span>
</template>

<script setup lang="ts">
import type { PostPrefixRef } from '~/composables/board/api/usePostApi.types';
import UtilBoard from '~/utils/util-board';

interface Props {
    prefix: PostPrefixRef;
}

const props = withDefaults(defineProps<Props>(), {});

const style = computed(() => UtilBoard.tagStyle(props.prefix.color, props.prefix.staffOnly));
</script>
