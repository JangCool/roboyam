<template>
    <!-- 게시판 종류 배지(읽기 전용) — 일반 · 핫딜 · 직접홍보 · 소모임. 게시판 목록과 게시판 설정 머리가 같은 모양으로 쓴다. 종류는 만든 뒤 바꿀 수 없다 -->
    <Tag class="board-kind-tag" :value="t(`board.kind.${kind}`)" :severity="UtilBoard.kindSeverity(kind)" />
</template>

<script setup lang="ts">
import type { BoardKind } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';

interface Props {
    kind: BoardKind;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>

<style scoped>
/* 뱃지 글자 세로 가운데 — 글자 상자를 대문자 높이~기준선으로 깎고 대칭 여백을 준다(`--chip-label-pad`, main.css · components.md 2-4) */
.board-kind-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.board-kind-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
}
</style>
