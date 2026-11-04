<template>
    <!--
        게시판 쓰기 제한 안내(board-sanction-contract 「화면 > user-web」) — 「이 게시판에서 {날짜}까지 글·댓글 쓰기가 제한돼요 · 사유 · [자세히]」(→ `/sanction`).
        게시판 머리(글쓰기 단추 자리)·글쓰기 폼·댓글 입력 자리가 쓴다. 값은 게시판 응답의 `viewer.writeRestriction` 또는 쓰기 오류 `403 BOARD_WRITE_RESTRICTED`(`until`)에서 온다.
        처리자는 보이지 않는다. 읽기·추천·수정·삭제·다른 게시판은 그대로라는 말을 둘째 줄에 둔다.
    -->
    <div role="note" class="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100" data-testid="board-write-restriction">
        <i class="pi pi-ban mt-0.5 shrink-0 text-xs" aria-hidden="true" />
        <div class="min-w-0 flex-1 leading-relaxed">
            <p class="m-0">
                <b class="font-semibold">{{ title }}</b>
                <template v-if="reason"> · {{ t('boardSanction.restrictedReason', { reason }) }}</template>
                <NuxtLink :to="UtilSanction.PATH" class="ml-1.5 whitespace-nowrap font-semibold text-primary underline-offset-2 hover:underline" data-testid="board-write-restriction-more">{{ t('boardSanction.restrictedMore') }}</NuxtLink>
            </p>
            <p v-if="!compact" class="m-0 mt-0.5 text-xs opacity-80">{{ t('boardSanction.restrictedAllowed') }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { BoardWriteRestriction } from '~/composables/board/api/useBoardStaffApi.types';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction from '~/utils/util-sanction';

interface Props {
    restriction: Pick<BoardWriteRestriction, 'until' | 'reasonCode'>;
    /** 둘째 줄(그래도 할 수 있는 것)을 뺀다 — 댓글 자리처럼 좁은 곳 */
    compact?: boolean;
}

const props = withDefaults(defineProps<Props>(), { compact: false });

const { t } = useI18n();
const time = useDisplayTime();

const title = computed(() => UtilBoardSanctionMessages.restrictionTitle(t, props.restriction, time.value));
const reason = computed(() => UtilBoardSanctionMessages.reasonLabel(t, props.restriction.reasonCode));
</script>
