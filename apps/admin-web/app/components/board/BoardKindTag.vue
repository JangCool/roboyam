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
/* 뱃지 글자 상하 중앙 — 한글 글꼴이 줄 상자 중앙보다 약 0.09em 아래 앉는 것을 글자 요소에서만 보정한다(`components.md` 2-4) */
.board-kind-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.board-kind-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
