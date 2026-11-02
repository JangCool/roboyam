<template>
    <!--
        중단하고 환불 요청 창(광고주 요청 — 목업 2026-11-02) — 예상 환불(일할 식 — 기준 = 정지 중이면 멈춘 시각, 아니면 지금) + 「총 N일 중 M일 남음」 풀이 ·
        핫딜 글이면 「게시비는 돌려드리지 않아요 — 노출 자리 남은 날만」(노출 자리가 없으면 「돌려드릴 금액이 없어요」) · 바로 내려감 · 다시 켤 수 없음(새로 신청) · 수수료 없음 ·
        금액은 운영팀 확인 뒤 확정 · 사유(1~300자, 옛 환불 요청과 같다) · 「되돌릴 수 없다는 걸 확인했어요」 체크 필수. 되돌릴 수 없는 처리라 Dialog 로 흐름을 끊는다. 보내기는 부르는 쪽이 한다.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :closable="!busy"
        :close-on-escape="!busy"
        :header="t('business.pause.stopTitle')"
        :style="{ width: 'min(30rem, calc(100vw - 2rem))' }"
        data-testid="business-stop-dialog"
        @update:visible="(v: boolean) => !v && emit('close')"
    >
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <!-- 예상 환불 -->
            <div class="flex flex-col gap-1 rounded-lg border border-hairline p-3" data-testid="business-stop-estimate">
                <template v-if="noRefund">
                    <p class="m-0 text-sm font-semibold">{{ t('business.pause.stopNoRefund') }}</p>
                </template>
                <template v-else>
                    <p class="m-0 text-sm font-semibold tabular-nums">{{ t('business.detail.refundEstimate', { amount: UtilBusinessMessages.won(t, refundAmount) }) }}</p>
                    <p class="m-0 text-xs tabular-nums text-muted-color">{{ t('business.pause.stopDays', { total: totalDays, left: leftDays }) }} · {{ fromPaused ? t('business.pause.stopFromPaused', { at: fromLabel }) : t('business.pause.stopFromNow') }}</p>
                    <p v-if="postFee !== null" class="m-0 text-xs text-muted-color" data-testid="business-stop-post-fee">{{ t('business.pause.stopPostFee', { fee: UtilBusinessMessages.won(t, postFee) }) }}</p>
                </template>
            </div>
            <ul class="m-0 flex list-none flex-col gap-1.5 p-0 text-sm">
                <li class="flex items-start gap-2"><i class="pi pi-stop-circle mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.stopNow') }}</li>
                <li class="flex items-start gap-2"><i class="pi pi-ban mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.stopNoUndo') }}</li>
                <li v-if="!noRefund" class="flex items-start gap-2"><i class="pi pi-check mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.stopNoFee') }}</li>
                <li v-if="!noRefund" class="flex items-start gap-2"><i class="pi pi-user-edit mt-0.5 text-xs text-muted-color" aria-hidden="true" />{{ t('business.pause.stopReview') }}</li>
            </ul>
            <LabeledField :label="t('business.detail.refundReason')" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea v-model="reason" :id="id" rows="3" auto-resize :maxlength="300" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <div class="flex flex-col gap-1">
                <label class="flex cursor-pointer items-start gap-2.5 text-sm font-medium">
                    <Checkbox v-model="agreed" binary :input-id="checkId" class="mt-0.5" :invalid="Boolean(agreeError)" :disabled="busy" data-testid="business-stop-agree" />
                    {{ t('business.pause.stopAgree') }}
                </label>
                <p v-if="agreeError" class="m-0 ml-7 text-xs text-red-600 dark:text-red-400" role="alert">{{ agreeError }}</p>
            </div>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('close')" />
            <Button type="submit" :form="formId" :label="t('business.pause.stopSubmit')" severity="danger" :loading="busy" data-testid="business-stop-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilBusinessMessages from '~/utils/util-business-messages';

interface Props {
    visible: boolean;
    /** 예상 환불(일할 — 운영팀이 확인한다) */
    refundAmount: number;
    /** 총 일수 */
    totalDays: number;
    /** 기준 날 뒤로 남은 일수 */
    leftDays: number;
    /** 정지 중에 중단한다 — 기준 = 멈춘 시각 */
    fromPaused?: boolean;
    /** 멈춘 시각(표시 글자) */
    fromLabel?: string;
    /** 핫딜 글의 돌려드리지 않는 게시비 — 광고면 null */
    postFee?: number | null;
    /** 돌려드릴 금액이 없다(핫딜 글만 — 노출 자리 없음) */
    noRefund?: boolean;
    busy?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { fromPaused: false, fromLabel: '', postFee: null, noRefund: false, busy: false, error: '' });
const emit = defineEmits<{ submit: [reason: string]; close: [] }>();

const { t } = useI18n();
const formId = useId();
const checkId = useId();
const reason = ref('');
const agreed = ref(false);
const tried = ref(false);

const reasonError = computed(() => (tried.value && !reason.value.trim() ? t('business.detail.refundReasonRequired') : ''));
const agreeError = computed(() => (tried.value && !agreed.value ? t('business.pause.stopAgreeRequired') : ''));

/** 열 때마다 비운다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        agreed.value = false;
        tried.value = false;
    },
);

function submit(): void {
    tried.value = true;
    if (props.busy || reasonError.value || agreeError.value) return;
    emit('submit', reason.value.trim());
}
</script>
