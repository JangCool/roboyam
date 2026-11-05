<template>
    <!--
        [처리 바꾸기] 대화상자 — 인쇄본 처리를 계속 보관 ↔ 스캔 후 폐기 예정으로 바꾼다(폐기는 [폐기 처리] 로만). 계속 보관이면 보관 위치(선택).
        열 때 지금 처리의 반대쪽을 골라 두고 보관 위치는 지금 값으로 채운다. 서버는 부르지 않는다 — 저장·닫기는 부모 몫이다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('docIntake.handlingDialog.title')" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p v-if="intake" class="m-0 text-[13px] text-muted-color">
                {{ t(`docIntake.channel.${intake.channel}`) }} · {{ t('docIntake.receivedLine', { date: time.serviceDate(intake.receivedOn), who: intake.receivedBy }) }} ·
                {{ t('docIntake.handlingDialog.now', { handling: t(`docIntake.handling.${intake.handling}`) }) }}
            </p>
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="mb-1.5 text-sm font-medium">{{ t('docIntake.create.handling') }}</legend>
                <div v-for="h in DOC_INTAKE_OPEN_HANDLINGS" :key="h" class="flex items-start gap-2">
                    <RadioButton v-model="handling" :input-id="`${formId}-h-${h}`" :name="`${formId}-h`" :value="h" :disabled="busy" class="mt-0.5" />
                    <label :for="`${formId}-h-${h}`" class="flex flex-col text-[13px] leading-snug">
                        {{ t(`docIntake.handling.${h}`) }}
                        <span class="text-[11px] text-muted-color">{{ t(`docIntake.create.handlingHelp.${h}`) }}</span>
                    </label>
                </div>
            </fieldset>
            <LabeledField v-if="handling === 'KEEP'" :label="t('docIntake.create.storage')" :hint="t('docIntake.create.optionalMax', { max: UtilDocIntake.STORAGE_MAX })" :error="storageError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="storage" :maxlength="UtilDocIntake.STORAGE_MAX" :placeholder="t('docIntake.create.storagePlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('docIntake.handlingDialog.submit')" :loading="busy" :disabled="!changed" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { DocIntake, DocIntakeHandlingRequest, DocIntakeOpenHandling } from '~/composables/docintake/api/useDocIntakeApi.types';
import { DOC_INTAKE_OPEN_HANDLINGS } from '~/composables/docintake/api/useDocIntakeApi.types';
import UtilDocIntake from '~/utils/util-doc-intake';

interface Props {
    visible: boolean;
    intake: DocIntake | null;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: DocIntakeHandlingRequest] }>();

const { t } = useI18n();
const time = useDisplayTime();
const formId = useId();

const handling = ref<DocIntakeOpenHandling>('KEEP');
const storage = ref('');

watch(
    () => props.visible,
    (open) => {
        if (!open || !props.intake) return;
        handling.value = props.intake.handling === 'KEEP' ? 'DISPOSE_PENDING' : 'KEEP';
        storage.value = props.intake.storageLocation ?? '';
    },
    { immediate: true },
);

const storageError = computed(() => (storage.value.trim().length > UtilDocIntake.STORAGE_MAX ? t('docIntake.problem.storageLocation.TOO_LONG', { max: UtilDocIntake.STORAGE_MAX }) : (props.fieldErrors.storageLocation ?? '')));
/** 처리나 보관 위치가 바뀌었나 */
const changed = computed(() => {
    const i = props.intake;
    if (!i) return false;
    return handling.value !== i.handling || (handling.value === 'KEEP' && storage.value.trim() !== (i.storageLocation ?? ''));
});

function submit(): void {
    if (props.busy || !changed.value || storage.value.trim().length > UtilDocIntake.STORAGE_MAX) return;
    emit('submit', { handling: handling.value, storageLocation: handling.value === 'KEEP' ? storage.value.trim() || null : null });
}
</script>
