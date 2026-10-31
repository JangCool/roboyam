<template>
    <!--
        취소·환불 요청(H1) — 시작 전 = 「전액 자동 환불돼요」(계좌이체 건은 운영팀이 환불), 진행 중 = 「남은 기간 일할로 운영팀이 확인해요」 + 예상 환불. 사유 필수.
        되돌릴 수 없는 처리라 Dialog 로 흐름을 한 번 끊는다(§6). 보내기는 부르는 쪽이 한다.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :closable="!busy"
        :close-on-escape="!busy"
        :header="t('business.detail.refund')"
        :style="{ width: 'min(28rem, calc(100vw - 2rem))' }"
        data-testid="business-refund-dialog"
        @update:visible="(v: boolean) => !v && emit('close')"
    >
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message :severity="running ? 'warn' : 'info'" size="small" :closable="false">
                {{ running ? t('business.detail.refundProrated') : paymentMethod === 'ONLINE' ? t('business.detail.refundFull') : t('business.detail.refundFullManual') }}
            </Message>
            <p class="m-0 text-sm font-semibold tabular-nums">{{ t('business.detail.refundEstimate', { amount: UtilBusinessMessages.won(t, refundAmount) }) }}</p>
            <LabeledField :label="t('business.detail.refundReason')" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea v-model="reason" :id="id" rows="3" auto-resize :maxlength="300" :invalid="invalid" :aria-describedby="describedBy" :disabled="busy" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="busy" @click="emit('close')" />
            <Button type="submit" :form="formId" :label="t('business.detail.refundSubmit')" severity="danger" :loading="busy" data-testid="business-refund-submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { BizPaymentMethod } from '~/composables/business/api/useBusinessApi.types';
import UtilBusinessMessages from '~/utils/util-business-messages';

interface Props {
    visible: boolean;
    /** 진행 중이면 일할 심사, 아니면(시작 전) 전액 */
    running: boolean;
    paymentMethod: BizPaymentMethod;
    /** 예상 환불 금액 */
    refundAmount: number;
    busy?: boolean;
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '' });
const emit = defineEmits<{ submit: [reason: string]; close: [] }>();

const { t } = useI18n();
const formId = useId();
const reason = ref('');
const tried = ref(false);

const reasonError = computed(() => (tried.value && !reason.value.trim() ? t('business.detail.refundReasonRequired') : ''));

/** 열 때마다 비운다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        tried.value = false;
    },
);

function submit(): void {
    tried.value = true;
    if (props.busy || reasonError.value) return;
    emit('submit', reason.value.trim());
}
</script>
