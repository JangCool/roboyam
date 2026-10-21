<template>
    <!--
        신청 반려 확인 — 승인 대기(신청) 건을 반려하기 전에 한 번 더 묻는다. 반려하면 신청이 목록에서 사라지고 되돌릴 수 없다. **사유는 선택**이다(비워도 반려된다).
        서버 호출은 부르는 쪽(페이지)이 하고 여기는 `submit` 으로 사유를 내보낸다. 사유 칸은 열 때마다 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :header="t('operator.rejectTitle')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed">{{ t('operator.rejectMessage', { name: operatorName }) }}</p>

            <LabeledField :label="t('operator.rejectReason')" :help="t('operator.rejectReasonHelp')" :error="reasonProblemText || fieldErrors.reason">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="3" fluid :placeholder="t('operator.rejectReasonPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('operator.reject')" severity="danger" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilOperator from '~/utils/util-operator';

interface Props {
    visible: boolean;
    /** 반려할 신청자의 이름 */
    operatorName?: string;
    busy?: boolean;
    /** 칸에 붙지 않는 오류 */
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { operatorName: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t } = useI18n();
const formId = useId();

const reason = ref('');
const submitted = ref(false);

/** 열 때마다 비운다 — 앞 신청에 적던 사유가 다음 신청에 딸려 가면 안 된다 */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const reasonProblemText = computed(() => (submitted.value && reason.value.trim().length > UtilOperator.REJECT_REASON_MAX ? t('operator.rejectReasonTooLong', { max: UtilOperator.REJECT_REASON_MAX }) : ''));

function submit(): void {
    submitted.value = true;
    if (reasonProblemText.value) return;
    emit('submit', reason.value.trim());
}
</script>
