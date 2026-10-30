<template>
    <!--
        사유 대화상자 — 사업자·신청 건·광고의 되돌릴 수 없는 처리(평문 보기 · 조회 불가 승인 · 수동 결제 전환 · 취소 · 긴급 내림 · 되돌리기 · 환불 거절)가 같이 쓴다.
        위에서 아래로: 안내(`description`) · 위 슬롯(영향 건수·선택지) · 사유(필수 5~200자) · 아래 슬롯(메일 체크 등) · 감사 안내 한 줄.
        서버는 부르지 않는다 — 사유는 `submit` 으로 올리고 저장·토스트는 부모 몫이다. 열 때마다 사유를 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="title" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="description" class="m-0 text-sm leading-relaxed text-muted-color">{{ description }}</p>

            <slot />

            <LabeledField :label="label || t('bizApplication.reason.label')" :hint="t('bizApplication.reason.hint', { min: REASON.min, max: REASON.max })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" rows="3" auto-resize :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="reason.trim().length > REASON.max ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ reason.trim().length }}/{{ REASON.max }}</small>
                </template>
            </LabeledField>

            <slot name="after" />

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color">
                <i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />
                {{ t('bizApplication.reason.audit') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="confirmLabel" :severity="tone === 'danger' ? 'danger' : undefined" :loading="busy" :disabled="blocked" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    visible: boolean;
    title: string;
    description?: string;
    /** 사유 칸 이름(기본 「사유」) */
    label?: string;
    confirmLabel: string;
    tone?: 'danger' | 'primary';
    busy?: boolean;
    /** 확인 단추를 막는다(부모의 다른 칸이 덜 채워졌을 때) */
    blocked?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 사유 칸에 준 오류 */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { description: '', label: '', tone: 'primary', busy: false, blocked: false, error: '', fieldError: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t } = useI18n();
const formId = useId();
const REASON = UtilBizApplication.REASON;

const reason = ref('');
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        submitted.value = false;
    },
    { immediate: true },
);

const reasonError = computed(() => {
    const problem = UtilBizApplication.reasonProblem(reason.value);
    if (problem && (submitted.value || problem === 'TOO_LONG')) return t(`bizApplication.reason.${problem}`, { min: REASON.min, max: REASON.max });
    return props.fieldError;
});

function submit(): void {
    submitted.value = true;
    if (props.busy || props.blocked || UtilBizApplication.reasonProblem(reason.value)) return;
    emit('submit', reason.value.trim());
}
</script>
