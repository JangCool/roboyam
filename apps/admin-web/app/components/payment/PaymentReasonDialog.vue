<template>
    <!--
        결제 처리 사유 대화상자(`commerce.reconcile.manage`) — 결제 이상의 「다시 시도」·「수동 처리 완료」, 대사 이슈 「처리」, 결제 상세의 「포트원에서 다시 확인」이 같이 쓴다(계약 세션 정리 결정 12).
        위에서 아래로: 안내(`description`) · 대상 요약(기본 슬롯 — 결제 번호·금액 등) · 사유(필수 5~200자) · 감사 안내 한 줄.
        서버는 부르지 않는다(`components.md`) — 사유는 `submit` 으로 올리고 저장·토스트·다시 읽기는 부모 몫이다. 열 때마다 사유를 비운다(다른 건에 적던 사유가 남지 않게).
        서버 오류는 사유 칸 아래(`fieldError`) 또는 폼 아래(`error`)에 서버 문구 그대로. 사유를 고치면 칸의 서버 문구는 숨긴다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>

            <slot />

            <LabeledField :label="t('payment.reason.label')" :hint="t('payment.reason.hint', { min: UtilPayment.REASON.min, max: UtilPayment.REASON.max })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="3" auto-resize :placeholder="placeholder" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="reasonLength > UtilPayment.REASON.max ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">
                        {{ t('payment.reason.counter', { length: reasonLength, max: UtilPayment.REASON.max }) }}
                    </small>
                </template>
            </LabeledField>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />
                {{ t('payment.reason.audit') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="confirmLabel" :severity="tone === 'danger' ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilPayment from '~/utils/util-payment';

interface Props {
    visible: boolean;
    title: string;
    description?: string;
    placeholder?: string;
    confirmLabel: string;
    tone?: 'danger' | 'primary';
    /** 보내는 중 — 입력·단추가 잠긴다 */
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 사유 칸에 준 오류(`fields.reason`) */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { description: '', placeholder: '', tone: 'primary', busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t } = useI18n();
const formId = useId();

const reason = ref('');
const submitted = ref(false);
const dismissed = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        submitted.value = false;
        dismissed.value = false;
    },
    { immediate: true },
);
watch(
    () => props.fieldError,
    () => (dismissed.value = false),
);
watch(reason, () => (dismissed.value = true));

const reasonLength = computed(() => UtilPayment.length(UtilPayment.reasonText(reason.value)));
const reasonError = computed(() => {
    const problem = UtilPayment.reasonProblem(reason.value);
    if (problem && (submitted.value || problem === 'TOO_LONG')) {
        if (problem === 'REQUIRED') return t('payment.reason.required');
        if (problem === 'TOO_SHORT') return t('payment.reason.tooShort', { min: UtilPayment.REASON.min });
        return t('payment.reason.tooLong', { max: UtilPayment.REASON.max });
    }
    return dismissed.value ? '' : props.fieldError;
});

function submit(): void {
    submitted.value = true;
    if (props.busy || UtilPayment.reasonProblem(reason.value)) return;
    emit('submit', UtilPayment.reasonText(reason.value));
}
</script>
