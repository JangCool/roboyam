<template>
    <!--
        확인하지 않은 게시판 경고 안내(board-sanction-contract 「화면 > user-web」) — 경고를 준 그 게시판 머리에만 「이 게시판에서 경고를 받았어요 · 사유 · [자세히] · [확인했어요]」.
        값은 게시판 응답의 `viewer.pendingWarnings`(최근 것이 위). 둘 이상이면 가장 최근 사유 + 「외 n건」. [확인했어요]는 부르는 쪽이 목록 전부를 확인한다(`/sanction` 에서도 확인한 것으로 바뀐다).
        경고는 쓰기를 막지 않으므로 둘째 줄에 그 말을 둔다. 처리자는 보이지 않는다.
    -->
    <div role="note" class="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100" data-testid="board-warning">
        <i class="pi pi-exclamation-circle mt-0.5 shrink-0 text-xs" aria-hidden="true" />
        <div class="min-w-0 flex-1 leading-relaxed">
            <p class="m-0">
                <b class="font-semibold">{{ t('boardSanction.warnedTitle') }}</b>
                <template v-if="reason"> · {{ t('boardSanction.restrictedReason', { reason }) }}</template>
                <template v-if="warnings.length > 1"> {{ t('boardSanction.warnedMore', { count: warnings.length - 1 }) }}</template>
                <NuxtLink :to="UtilSanction.PATH" class="ml-1.5 whitespace-nowrap font-semibold text-primary underline-offset-2 hover:underline" data-testid="board-warning-more">{{ t('boardSanction.restrictedMore') }}</NuxtLink>
            </p>
            <p class="m-0 mt-0.5 text-xs opacity-80">{{ t('boardSanction.warnedHint') }}</p>
            <p v-if="error" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="board-warning-error">{{ error }}</p>
        </div>
        <Button type="button" :label="t('boardSanction.ackButton')" size="small" severity="secondary" outlined :loading="busy" :disabled="busy" class="shrink-0" data-testid="board-warning-ack" @click="emit('acknowledge')" />
    </div>
</template>

<script setup lang="ts">
import type { BoardPendingWarning } from '~/composables/board/api/useBoardStaffApi.types';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanction from '~/utils/util-sanction';

interface Props {
    /** 확인하지 않은 경고(최근 것이 위, 하나 이상) */
    warnings: BoardPendingWarning[];
    busy?: boolean;
    /** 확인 실패 문구 */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '' });
const emit = defineEmits<{ acknowledge: [] }>();

const { t } = useI18n();

const reason = computed(() => UtilBoardSanctionMessages.reasonLabel(t, props.warnings[0]?.reasonCode));
</script>
