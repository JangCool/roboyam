<template>
    <!--
        스텝 처리 사유 시트(게시판 매니저·스텝 — board-staff-action-contract 「화면 > user-web」). 글 ⋯ 「글 숨기기」·「글 삭제」, 댓글 ⋯ 「댓글 삭제」가 연다.
        두 단계: ① 대상(글 제목 / 댓글 앞부분) · 사유 코드 4개(필수, 자유 입력 없음) · 오늘 남은 횟수 → [다음]
        ② 확인 — 「이 글을 숨길까요?」 · 사유 · 「숨기면 작성자에게 사유와 함께 알림이 가요」(삭제는 「삭제하면 작성자에게 알림이 가요」 — 되돌릴 수 있다는 말은 하지 않는다) → [뒤로] [숨기기|삭제하기].
        제출·결과 처리는 부르는 화면이 한다(`useBoardStaffActionForm`) — 여기는 그리기와 입력 검사만.
    -->
    <Dialog :visible="visible" modal :header="UtilBoardStaffActionMessages.title(t, kind)" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="close">
        <form class="flex flex-col gap-4" novalidate data-testid="board-staff-action-form" @submit.prevent="onSubmit">
            <!-- 대상 -->
            <div class="rounded-lg border border-surface bg-surface-50 px-3 py-2.5 dark:bg-surface-800/60" data-testid="board-staff-action-target">
                <p class="m-0 text-xs text-muted-color">{{ isComment ? t('boardStaffAction.targetComment') : t('boardStaffAction.targetPost') }}</p>
                <p class="m-0 mt-0.5 line-clamp-2 break-words text-sm font-semibold [overflow-wrap:anywhere]">{{ targetText }}</p>
            </div>

            <!-- ① 사유 코드(필수) -->
            <fieldset v-if="step === 'reason'" class="m-0 flex flex-col gap-1.5 border-0 p-0">
                <legend class="mb-1 text-sm font-medium">{{ t('boardStaffAction.reasonLegend') }} <span class="text-red-600 dark:text-red-400" aria-hidden="true">*</span></legend>
                <label v-for="r in BOARD_STAFF_ACTION_REASONS" :key="r" class="flex cursor-pointer items-center gap-2 text-sm" :data-testid="`board-staff-action-reason-${r}`">
                    <RadioButton v-model="reasonCode" :value="r" :input-id="`${baseId}-reason-${r}`" name="board-staff-action-reason" :disabled="submitting" :invalid="Boolean(reasonError)" />
                    <span>{{ UtilBoardStaffActionMessages.reasonLabel(t, r) }}</span>
                </label>
                <small v-if="reasonError" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ reasonError }}</small>
            </fieldset>

            <!-- ② 확인 -->
            <div v-else class="flex flex-col gap-1.5" data-testid="board-staff-action-confirm">
                <p class="m-0 text-sm font-semibold">{{ UtilBoardStaffActionMessages.confirmQuestion(t, kind) }}</p>
                <p v-if="reasonCode" class="m-0 text-sm">{{ t('boardStaffAction.confirmReason', { reason: UtilBoardStaffActionMessages.reasonLabel(t, reasonCode) }) }}</p>
                <p class="m-0 text-xs text-muted-color">{{ UtilBoardStaffActionMessages.notice(t, kind) }} {{ t('boardStaffAction.noticePrivate') }}</p>
            </div>

            <Message v-if="formError" severity="error" variant="simple" size="small" data-testid="board-staff-action-error">{{ formError }}</Message>

            <div class="flex flex-wrap items-center justify-between gap-2 border-t border-surface pt-3">
                <span v-if="quota || unlimited" class="text-xs tabular-nums" :class="exhausted ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="board-staff-action-quota">
                    {{ exhausted ? t('boardStaffAction.quotaEmpty') : UtilBoardStaffActionMessages.quotaLine(t, kind, unlimited ? null : quota) }}
                </span>
                <span v-else />
                <div class="flex gap-2">
                    <template v-if="step === 'reason'">
                        <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="close(false)" />
                        <Button type="submit" :label="t('boardStaffAction.next')" :disabled="exhausted" data-testid="board-staff-action-next" />
                    </template>
                    <template v-else>
                        <Button type="button" :label="t('boardStaffAction.back')" severity="secondary" variant="text" :disabled="submitting" @click="step = 'reason'" />
                        <Button type="submit" :label="UtilBoardStaffActionMessages.submitLabel(t, kind)" severity="danger" :loading="submitting" :disabled="exhausted" data-testid="board-staff-action-submit" />
                    </template>
                </div>
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import type { BoardStaffActionKind, BoardStaffActionReasonCode, BoardStaffQuota } from '~/composables/board/api/useBoardStaffApi.types';
import { BOARD_STAFF_ACTION_REASONS } from '~/composables/board/api/useBoardStaffApi.types';
import UtilBoardStaffActionMessages from '~/utils/util-board-staff-action-messages';

interface Props {
    visible: boolean;
    kind: BoardStaffActionKind;
    /** 대상 글 제목 또는 댓글 앞부분 */
    targetText?: string;
    /** 이 종류의 오늘 남은 한도(글 처리면 글 한도, 댓글 삭제면 댓글 한도) — 모르면 null */
    quota?: BoardStaffQuota | null;
    /** 하루 한도 없음(운영자 회원 계정 — `staffTools.operator`) — 남은 횟수 대신 「운영자 계정 · 하루 한도 없음」 */
    unlimited?: boolean;
    submitting?: boolean;
    /** 제출 실패 서버 문구(칸에 붙지 않는 것) */
    formError?: string;
    /** 서버 `fields.reasonCode` */
    reasonFieldError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    targetText: '',
    quota: null,
    unlimited: false,
    submitting: false,
    formError: '',
    reasonFieldError: '',
});

const emit = defineEmits<{
    'update:visible': [value: boolean];
    submit: [reasonCode: BoardStaffActionReasonCode];
}>();

const { t } = useI18n();
const baseId = useId();

const step = ref<'reason' | 'confirm'>('reason');
const reasonCode = ref<BoardStaffActionReasonCode | null>(null);
const tried = ref(false);

const isComment = computed(() => props.kind === 'COMMENT_DELETE');
const exhausted = computed(() => Boolean(!props.unlimited && props.quota && props.quota.remaining <= 0));
const reasonError = computed(() => props.reasonFieldError || (tried.value && !reasonCode.value ? t('boardStaffAction.reasonRequired') : ''));

/** 닫히면 처음 단계로 비운다 */
watch(
    () => props.visible,
    (open) => {
        if (open) return;
        step.value = 'reason';
        reasonCode.value = null;
        tried.value = false;
    },
);
/** 서버가 사유 칸을 지목하면 사유 단계로 돌아간다 */
watch(
    () => props.reasonFieldError,
    (message) => {
        if (message) step.value = 'reason';
    },
);

function close(value: boolean): void {
    emit('update:visible', value);
}

function onSubmit(): void {
    if (exhausted.value || props.submitting) return;
    if (step.value === 'reason') {
        tried.value = true;
        if (reasonCode.value) step.value = 'confirm';
        return;
    }
    if (reasonCode.value) emit('submit', reasonCode.value);
}
</script>
