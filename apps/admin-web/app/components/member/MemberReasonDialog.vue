<template>
    <!--
        사유를 받고 조치를 확인하는 대화상자 — 회원 상세의 기능 제한 [해제]와 탈퇴 회원 연락처 [열람]이 같은 모양으로 쓴다(사유 필수 5~200자 + 안내 한 줄).
        위에 무엇에 대한 조치인지(기본 슬롯), 사유 칸, 안내(`notice` — 예: 「열람 기록·감사에 남아요」). 서버 호출은 부모가 하고 여기는 `submit` 으로 사유를 올린다.
        서버 칸 오류(`fieldError` — `fields.reason`)는 칸 아래, 그 밖의 오류(`error`)는 폼 아래에 서버 문구 그대로. 사유 칸은 열 때마다 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="title" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <slot />

            <LabeledField :label="label" :hint="t('reason.hint', { min, max })" :error="reasonError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="reason" name="reason" rows="3" fluid :maxlength="max + 50" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" aria-required="true" />
                </template>
            </LabeledField>
            <p class="-mt-3 text-right text-[11px] tabular-nums text-muted-color">{{ t('reason.count', { count: reason.trim().length, max }) }}</p>

            <p v-if="notice" class="m-0 flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                <i class="pi pi-info-circle mt-0.5" aria-hidden="true" />
                <span>{{ notice }}</span>
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="submitLabel" :icon="submitIcon" :severity="danger ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
interface Props {
    visible: boolean;
    title: string;
    /** 사유 칸 이름 */
    label: string;
    submitLabel: string;
    submitIcon?: string;
    /** 확인 단추를 붉게 */
    danger?: boolean;
    /** 사유 칸 아래 안내 한 줄 */
    notice?: string;
    /** 사유 길이(서버 규칙과 같다) */
    min?: number;
    max?: number;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 거절한 사유 칸 문구(`fields.reason`) */
    fieldError?: string;
}

const props = withDefaults(defineProps<Props>(), { submitIcon: undefined, danger: false, notice: '', min: 5, max: 200, busy: false, error: '', fieldError: '' });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [reason: string] }>();

const { t } = useI18n();
const formId = useId();

const reason = ref('');
const submitted = ref(false);
/** 서버 칸 오류를 지금 보일지 — 칸을 고치면 숨긴다 */
const showServer = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        reason.value = '';
        submitted.value = false;
        showServer.value = false;
    },
    { immediate: true },
);
watch(
    () => props.fieldError,
    (value) => (showServer.value = Boolean(value)),
);
watch(reason, () => (showServer.value = false));

const reasonError = computed(() => {
    if (showServer.value && props.fieldError) return props.fieldError;
    if (!submitted.value) return '';
    const length = reason.value.trim().length;
    if (length === 0) return t('reason.required');
    if (length < props.min || length > props.max) return t('reason.length', { min: props.min, max: props.max });
    return '';
});

function submit(): void {
    submitted.value = true;
    showServer.value = false;
    if (reasonError.value || props.busy) return;
    emit('submit', reason.value.trim());
}
</script>
