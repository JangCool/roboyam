<template>
    <!--
        사업자 정지 해제 대화상자(H2 · business-contract 「정지 해제 API」) — 정지로 내려간 진행 중이던 건을 어떻게 되돌릴지 둘 중 하나를 고르고, 선택 메모(200자까지)를 남긴다.
        · 남은 기간 연장 후 되돌리기(`EXTEND_RESTORE`, 기본): 내려가 있던 시간만큼 끝을 늘려 되돌린다. 자리가 꽉 차면 늘리지 않고 되돌리며, 정지로 생긴 환불 요청은 거절로 닫힌다.
        · 환불 유지(`KEEP_REFUND`): 환불 심사 대기 건은 그대로 두고, 환불 대기가 없는 내린 건만 연장 없이 되돌린다.
        서버는 부르지 않는다 — 고른 값은 `submit` 으로 올린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('business.unsuspendTitle', { name })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t('business.unsuspendBody') }}</p>

            <fieldset class="m-0 flex flex-col gap-2.5 rounded-lg border border-(--line) px-3 py-2.5" :disabled="busy">
                <legend class="px-1 text-[13px] font-semibold">{{ t('business.restoreLabel') }}</legend>
                <div v-for="opt in options" :key="opt.value" class="flex items-start gap-2">
                    <RadioButton v-model="restore" :input-id="`${formId}-${opt.value}`" :name="`${formId}-restore`" :value="opt.value" :aria-describedby="`${formId}-${opt.value}-help`" />
                    <div class="flex min-w-0 flex-col gap-0.5">
                        <label :for="`${formId}-${opt.value}`" class="text-[13px] leading-snug">
                            {{ opt.label }}<span v-if="opt.default" class="ml-1 text-[11px] text-muted-color">{{ t('business.defaultChoice') }}</span>
                        </label>
                        <span :id="`${formId}-${opt.value}-help`" class="text-xs leading-snug text-muted-color">{{ opt.help }}</span>
                    </div>
                </div>
            </fieldset>

            <LabeledField :label="t('business.unsuspendNote')" :hint="t('business.unsuspendNoteHint', { max: NOTE_MAX })" :error="noteError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="note" rows="2" auto-resize :maxlength="NOTE_MAX" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('business.actUnsuspend')" icon="pi pi-replay" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { BusinessUnsuspendRequest, UnsuspendRestore } from '~/composables/business/api/useBusinessApi.types';

interface Props {
    visible: boolean;
    name: string;
    busy?: boolean;
    error?: string;
    /** 서버 칸 오류(`note`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: BusinessUnsuspendRequest] }>();

const { t } = useI18n();
const formId = useId();
/** 메모 길이(서버 200자) */
const NOTE_MAX = 200;

const restore = ref<UnsuspendRestore>('EXTEND_RESTORE');
const note = ref('');

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        restore.value = 'EXTEND_RESTORE';
        note.value = '';
    },
    { immediate: true },
);

const options = computed(() => [
    { value: 'EXTEND_RESTORE' as const, label: t('business.restore.EXTEND_RESTORE'), help: t('business.restoreHelp.EXTEND_RESTORE'), default: true },
    { value: 'KEEP_REFUND' as const, label: t('business.restore.KEEP_REFUND'), help: t('business.restoreHelp.KEEP_REFUND'), default: false },
]);

const noteError = computed(() => props.fieldErrors.note ?? (note.value.trim().length > NOTE_MAX ? t('business.unsuspendNoteTooLong', { max: NOTE_MAX }) : ''));

function submit(): void {
    const text = note.value.trim();
    if (props.busy || text.length > NOTE_MAX) return;
    emit('submit', text ? { restore: restore.value, note: text } : { restore: restore.value });
}
</script>
