<template>
    <!--
        작성자 제재(게시판 매니저·스텝 — board-sanction-contract 「화면 > user-web」). 글 ⋯ 메뉴·댓글 ⋯ 메뉴의 「작성자 제재」가 연다.
        대상 닉네임 · 그 게시판 제재 현황(경고 n · 쓰기 제한 n · 진행 중이면 끝) · 단계 4개(자유 선택 — 권장 단계에 「권장」 배지 + 「권장: …」 한 줄, 막지 않는다.
        진행 중인 쓰기 제한이 있으면 제한 3개는 비활성) · 사유 코드 5개(필수) · 덧붙임 0~200자(기타면 5자 이상) · 오늘 남은 횟수 → [제재하기].
        현황 읽기·제출은 부르는 화면이 한다(`useBoardSanctionForm`) — 여기는 그리기와 입력 검사만. 받을 수 없는 대상이면 서버 문구(「제재할 수 없는 회원이에요」)만 보이고 폼을 그리지 않는다.
    -->
    <Dialog :visible="visible" modal :header="t('boardSanction.dialogTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" :draggable="false" @update:visible="close">
        <div v-if="state === 'loading'" class="flex flex-col gap-3" role="status" :aria-label="t('boardSanction.loadingPreview')" data-testid="board-sanction-loading">
            <div class="h-14 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            <div class="grid grid-cols-2 gap-2">
                <div v-for="n in 4" :key="n" class="h-12 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
            </div>
            <div class="h-24 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
        </div>

        <!-- 받을 수 없는 대상(409 BOARD_STAFF_TARGET_PROTECTED) — 서버 문구 그대로 -->
        <div v-else-if="state === 'protected'" class="flex flex-col gap-3" data-testid="board-sanction-protected">
            <Message severity="warn" :closable="false">{{ loadError }}</Message>
            <div class="flex justify-end">
                <Button type="button" :label="t('common.close')" severity="secondary" @click="close(false)" />
            </div>
        </div>

        <div v-else-if="state === 'error' || !preview" class="flex flex-col gap-3" data-testid="board-sanction-load-error">
            <Message severity="error" variant="simple">{{ loadError || t('boardSanction.previewFailed') }}</Message>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="close(false)" />
                <Button type="button" :label="t('common.retry')" @click="$emit('retry')" />
            </div>
        </div>

        <form v-else class="flex flex-col gap-4" novalidate data-testid="board-sanction-form" @submit.prevent="submit">
            <!-- 대상 · 그 게시판 제재 현황 -->
            <div class="rounded-lg border border-surface bg-surface-50 px-3 py-2.5 dark:bg-surface-800/60" data-testid="board-sanction-target">
                <p class="m-0 text-sm"><span class="text-muted-color">{{ t('boardSanction.targetLabel') }}</span> <b class="font-semibold">{{ nickname }}</b></p>
                <p class="m-0 mt-1 text-xs text-muted-color" data-testid="board-sanction-counts">
                    {{ t('boardSanction.countsPrefix') }} {{ UtilBoardSanctionMessages.countsLine(t, preview) }}
                </p>
                <p v-if="preview.activeRestriction" class="m-0 mt-1 text-xs font-semibold text-red-700 dark:text-red-300" data-testid="board-sanction-active">
                    {{ t('boardSanction.activeUntil', { date: UtilBoardSanctionMessages.untilText(preview.activeRestriction.until, time) }) }}
                </p>
            </div>

            <!-- 단계 — 자유 선택, 권장은 배지와 한 줄 설명뿐 -->
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="mb-1 text-sm font-medium">{{ t('boardSanction.kindLegend') }}</legend>
                <div class="grid grid-cols-2 gap-2">
                    <label
                        v-for="k in BOARD_SANCTION_KINDS"
                        :key="k"
                        class="flex items-center gap-2 rounded-lg border px-2.5 py-2 text-sm"
                        :class="[
                            kind === k ? 'border-primary bg-primary/5' : 'border-surface',
                            kindDisabled(k) ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:bg-surface-100 dark:hover:bg-surface-800',
                        ]"
                        :data-testid="`board-sanction-kind-${k}`"
                    >
                        <RadioButton v-model="kind" :value="k" :input-id="`${baseId}-kind-${k}`" name="board-sanction-kind" :disabled="kindDisabled(k) || submitting" />
                        <span class="min-w-0 flex-1">{{ UtilBoardSanctionMessages.kindLabel(t, k) }}</span>
                        <span
                            v-if="preview.recommended === k"
                            class="inline-flex shrink-0 items-center justify-center rounded bg-primary px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-primary-contrast"
                            data-testid="board-sanction-recommended"
                        >
                            <span class="relative top-[-0.09em]">{{ t('boardSanction.recommendedBadge') }}</span>
                        </span>
                    </label>
                </div>
                <p class="m-0 text-xs text-muted-color" data-testid="board-sanction-recommend-line">{{ UtilBoardSanctionMessages.recommendLine(t, preview.recommended, preview) }}</p>
                <p v-if="preview.activeRestriction" class="m-0 text-xs text-muted-color">{{ t('boardSanction.restrictDisabledHint') }}</p>
            </fieldset>

            <!-- 사유 코드(필수) -->
            <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                <legend class="mb-1 text-sm font-medium">{{ t('boardSanction.reasonLegend') }} <span class="text-red-600 dark:text-red-400" aria-hidden="true">*</span></legend>
                <label v-for="r in BOARD_SANCTION_REASONS" :key="r" class="flex cursor-pointer items-center gap-2 text-sm">
                    <RadioButton v-model="reasonCode" :value="r" :input-id="`${baseId}-reason-${r}`" name="board-sanction-reason" :disabled="submitting" :invalid="Boolean(reasonError)" />
                    <span>{{ UtilBoardSanctionMessages.reasonLabel(t, r) }}</span>
                </label>
                <small v-if="reasonError" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ reasonError }}</small>
            </fieldset>

            <!-- 덧붙임 0~200자, 기타면 5자 이상 -->
            <LabeledField :label="t('boardSanction.textLabel')" :hint="reasonCode === 'OTHER' ? t('boardSanction.textHintOther') : t('boardSanction.textHint')" :error="textError">
                <template #aside>
                    <span class="text-xs tabular-nums" :class="textProblem === 'TOO_LONG' ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">
                        {{ t('boardSanction.textCounter', { n: textLength, max: UtilBoardSanction.TEXT_MAX }) }}
                    </span>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="text" rows="3" auto-resize fluid :disabled="submitting" :invalid="invalid" :aria-describedby="describedBy" data-testid="board-sanction-text" />
                </template>
            </LabeledField>

            <p class="m-0 text-xs text-muted-color">{{ t('boardSanction.noticeToMember') }}</p>

            <Message v-if="formError" severity="error" variant="simple" size="small" data-testid="board-sanction-error">{{ formError }}</Message>

            <div class="flex flex-wrap items-center justify-between gap-2 border-t border-surface pt-3">
                <span class="text-xs tabular-nums" :class="preview.quota.remaining > 0 ? 'text-muted-color' : 'font-semibold text-red-600 dark:text-red-400'" data-testid="board-sanction-quota">
                    {{ UtilBoardSanctionMessages.quotaLine(t, preview.quota) }}
                </span>
                <div class="flex gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="close(false)" />
                    <Button type="submit" :label="t('boardSanction.submit')" severity="danger" :loading="submitting" :disabled="!canSubmit" data-testid="board-sanction-submit" />
                </div>
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import type { BoardSanctionKind, BoardSanctionPreview, BoardSanctionReasonCode } from '~/composables/board/api/useBoardStaffApi.types';
import { BOARD_SANCTION_KINDS, BOARD_SANCTION_REASONS } from '~/composables/board/api/useBoardStaffApi.types';
import type { BoardSanctionDialogState, BoardSanctionDialogValue } from '~/components/board/BoardSanctionDialog.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';

interface Props {
    visible: boolean;
    /** 대상 닉네임(누른 글·댓글의 작성자) */
    nickname: string;
    /** 현황 읽기 상태 — `protected` 는 받을 수 없는 대상 */
    state: BoardSanctionDialogState;
    preview?: BoardSanctionPreview | null;
    /** 현황을 못 읽었을 때의 서버 문구 */
    loadError?: string;
    submitting?: boolean;
    /** 제출 실패 서버 문구(칸에 붙지 않는 것) */
    formError?: string;
    /** 서버 `fields`(`reasonCode`·`reasonText`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
    preview: null,
    loadError: '',
    submitting: false,
    formError: '',
    fieldErrors: () => ({}),
});

const emit = defineEmits<{
    'update:visible': [value: boolean];
    submit: [value: BoardSanctionDialogValue];
    retry: [];
}>();

const { t } = useI18n();
const time = useDisplayTime();
const baseId = useId();

const kind = ref<BoardSanctionKind>('WARNING');
const reasonCode = ref<BoardSanctionReasonCode | null>(null);
const text = ref('');
const tried = ref(false);

const restricted = computed(() => Boolean(props.preview?.activeRestriction));
function kindDisabled(k: BoardSanctionKind): boolean {
    return !UtilBoardSanction.kindAllowed(k, restricted.value);
}

const textLength = computed(() => UtilBoardSanction.textLength(text.value));
const textProblem = computed(() => UtilBoardSanction.textProblem(text.value, reasonCode.value));
/** 덧붙임 안내 — 너무 길면 바로, 기타 5자 미만은 [제재하기]를 누른 뒤, 서버가 지목하면 그대로 */
const textError = computed(() => {
    if (props.fieldErrors.reasonText) return props.fieldErrors.reasonText;
    const p = textProblem.value;
    if (p === 'TOO_LONG' || (p && tried.value)) return UtilBoardSanctionMessages.textProblem(t, p, textLength.value);
    return '';
});
const reasonError = computed(() => props.fieldErrors.reasonCode || (tried.value && !reasonCode.value ? t('boardSanction.reasonRequired') : ''));
const canSubmit = computed(() => !props.submitting && Boolean(props.preview) && props.preview!.quota.remaining > 0 && !kindDisabled(kind.value));

/** 현황이 새로 오면 단계를 권장으로 둔다(진행 중 제한이 있어 권장이 막혔으면 경고) */
watch(
    () => props.preview,
    (p) => {
        if (!p) return;
        kind.value = UtilBoardSanction.kindAllowed(p.recommended, Boolean(p.activeRestriction)) ? p.recommended : 'WARNING';
    },
);
watch(
    () => props.visible,
    (open) => {
        if (open) return;
        reasonCode.value = null;
        text.value = '';
        tried.value = false;
    },
);

function close(value: boolean): void {
    emit('update:visible', value);
}

function submit(): void {
    tried.value = true;
    if (!canSubmit.value || !reasonCode.value || textProblem.value) return;
    emit('submit', { kind: kind.value, reasonCode: reasonCode.value, reasonText: UtilBoardSanction.textBody(text.value) });
}
</script>
