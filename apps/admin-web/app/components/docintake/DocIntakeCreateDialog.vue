<template>
    <!--
        [접수 기록] 대화상자 — 인쇄본·팩스로 받은 계약서 사본·사업자등록증 사본을 기록한다.
        접수 방식(우편 · 직접 전달 · 팩스) → 팩스면 받은 형태(종이 · 파일). 파일(전자 팩스)이면 파일을 골라 파일 사본으로 올리고 방식만 「전자 팩스」로 표시한다(`submitFile`).
        종이면 접수일(필수, 오늘 이전) · 쪽수(선택 1~200) · 인쇄본 처리(계속 보관 기본 / 스캔 후 폐기 예정) · 보관 위치(계속 보관일 때, 선택) · 메모(선택) → `submitPaper`. 접수자는 자동.
        서버는 부르지 않는다 — 저장·닫기·토스트는 부모 몫이다. 열 때마다 칸을 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('docIntake.create.title', { doc: t(`docIntake.doc.${doc}`) })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="mb-1.5 text-sm font-medium">{{ t('docIntake.create.channel') }}</legend>
                <div class="flex flex-wrap gap-x-4 gap-y-2">
                    <div v-for="c in DOC_INTAKE_CHANNELS" :key="c" class="flex items-center gap-2">
                        <RadioButton v-model="draft.channel" :input-id="`${formId}-c-${c}`" :name="`${formId}-c`" :value="c" :disabled="busy" />
                        <label :for="`${formId}-c-${c}`" class="inline-flex items-center gap-1.5 text-[13px]"><i :class="UtilDocIntake.channelIcon(c)" class="text-xs text-muted-color" aria-hidden="true" />{{ t(`docIntake.channelPick.${c}`) }}</label>
                    </div>
                </div>
            </fieldset>

            <fieldset v-if="draft.channel === 'FAX'" class="m-0 flex flex-col gap-2 border-0 p-0">
                <legend class="mb-1.5 text-sm font-medium">{{ t('docIntake.create.faxForm') }}</legend>
                <div class="flex flex-wrap gap-x-4 gap-y-2">
                    <div v-for="f in FAX_FORMS" :key="f" class="flex items-center gap-2">
                        <RadioButton v-model="faxForm" :input-id="`${formId}-f-${f}`" :name="`${formId}-f`" :value="f" :disabled="busy" />
                        <label :for="`${formId}-f-${f}`" class="text-[13px]">{{ t(`docIntake.create.faxFormOption.${f}`) }}</label>
                    </div>
                </div>
            </fieldset>

            <!-- 전자 팩스 — 파일 사본으로 올리고 방식만 「전자 팩스」 -->
            <template v-if="fileMode">
                <LabeledField :label="t('docIntake.create.file')" :help="t(`docIntake.create.fileHelp.${doc}`)" :error="fileError">
                    <template #default="{ id, describedBy }">
                        <div class="flex flex-wrap items-center gap-2">
                            <Button :id="id" type="button" :label="t('docIntake.create.choose')" icon="pi pi-paperclip" size="small" severity="secondary" variant="outlined" :disabled="busy" :aria-describedby="describedBy" @click="fileInput?.click()" />
                            <span v-if="file" class="min-w-0 truncate text-[13px]">{{ file.name }} <span class="text-[11px] text-muted-color">· {{ UtilBizApplication.fileSize(file.size) }}</span></span>
                        </div>
                        <input ref="fileInput" type="file" class="hidden" tabindex="-1" :accept="UtilDocIntake.acceptOf(doc)" :aria-label="t('docIntake.create.choose')" @change="onFile" />
                    </template>
                </LabeledField>
            </template>

            <template v-else>
                <div class="grid grid-cols-1 gap-x-4 gap-y-1 sm:grid-cols-2">
                    <LabeledField :label="t('docIntake.create.receivedOn')" :error="err('receivedOn')">
                        <template #default="{ id, describedBy, invalid }">
                            <AppDatePicker v-model="draft.receivedOn" :input-id="id" :max="today" :disabled="busy" :invalid="invalid" :described-by="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('docIntake.create.pages')" :hint="t('docIntake.create.pagesHint', { min: UtilDocIntake.PAGES.min, max: UtilDocIntake.PAGES.max })" :error="err('pages')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="draft.pages" :input-id="id" :min="UtilDocIntake.PAGES.min" :max="UtilDocIntake.PAGES.max" :use-grouping="false" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                        </template>
                    </LabeledField>
                </div>

                <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                    <legend class="mb-1.5 text-sm font-medium">{{ t('docIntake.create.handling') }}</legend>
                    <div v-for="h in DOC_INTAKE_OPEN_HANDLINGS" :key="h" class="flex items-start gap-2">
                        <RadioButton v-model="draft.handling" :input-id="`${formId}-h-${h}`" :name="`${formId}-h`" :value="h" :disabled="busy" class="mt-0.5" />
                        <label :for="`${formId}-h-${h}`" class="flex flex-col text-[13px] leading-snug">
                            {{ t(`docIntake.handling.${h}`) }}
                            <span class="text-[11px] text-muted-color">{{ t(`docIntake.create.handlingHelp.${h}`) }}</span>
                        </label>
                    </div>
                </fieldset>

                <LabeledField v-if="draft.handling === 'KEEP'" :label="t('docIntake.create.storage')" :hint="t('docIntake.create.optionalMax', { max: UtilDocIntake.STORAGE_MAX })" :error="err('storageLocation')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="draft.storageLocation" :maxlength="UtilDocIntake.STORAGE_MAX" :placeholder="t('docIntake.create.storagePlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>

                <LabeledField :label="t('docIntake.create.memo')" :hint="t('docIntake.create.optionalMax', { max: UtilDocIntake.MEMO_MAX })" :error="err('memo')">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="draft.memo" rows="2" auto-resize :maxlength="UtilDocIntake.MEMO_MAX" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>
            </template>

            <p class="m-0 flex items-center gap-1.5 text-xs text-muted-color">
                <i class="pi pi-user text-xs" aria-hidden="true" />{{ t('docIntake.create.receiver') }} · {{ receiver ? t('docIntake.create.receiverAuto', { who: receiver }) : t('docIntake.create.receiverUnknown') }}
            </p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="fileMode ? t('docIntake.create.submitFile') : t('docIntake.create.submit')" :icon="fileMode ? 'pi pi-upload' : 'pi pi-inbox'" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { DocIntakeCreateRequest, DocIntakeDoc } from '~/composables/docintake/api/useDocIntakeApi.types';
import { DOC_INTAKE_CHANNELS, DOC_INTAKE_OPEN_HANDLINGS } from '~/composables/docintake/api/useDocIntakeApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilDocIntake, { type DocIntakeDraft } from '~/utils/util-doc-intake';

interface Props {
    visible: boolean;
    doc: DocIntakeDoc;
    /** 접수자(자동) — 로그인 운영자 이름 */
    receiver?: string;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버 칸 오류 */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { receiver: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submitPaper: [body: DocIntakeCreateRequest]; submitFile: [file: File] }>();

const FAX_FORMS = ['PAPER', 'FILE'] as const;

const { t } = useI18n();
const formId = useId();
const today = UtilBizApplication.kstToday();

const draft = ref<DocIntakeDraft>(UtilDocIntake.emptyDraft(today));
const faxForm = ref<(typeof FAX_FORMS)[number]>('PAPER');
const file = ref<File | null>(null);
const fileInput = ref<HTMLInputElement | null>(null);
const submitted = ref(false);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        draft.value = UtilDocIntake.emptyDraft(UtilBizApplication.kstToday());
        faxForm.value = 'PAPER';
        file.value = null;
        submitted.value = false;
    },
    { immediate: true },
);

/** 팩스를 파일로 받았나(전자 팩스) */
const fileMode = computed(() => draft.value.channel === 'FAX' && faxForm.value === 'FILE');
const problems = computed(() => UtilDocIntake.draftProblems(draft.value, today));

/** 칸 오류 — 제출을 한 번 누른 뒤부터(글자 수 초과는 바로). 서버 칸 오류는 늘 */
function err(key: 'receivedOn' | 'pages' | 'storageLocation' | 'memo'): string {
    const p = problems.value[key];
    if (p && (submitted.value || p === 'TOO_LONG' || p === 'FUTURE')) return t(`docIntake.problem.${key}.${p}`, { max: key === 'memo' ? UtilDocIntake.MEMO_MAX : UtilDocIntake.STORAGE_MAX });
    return props.fieldErrors[key] ?? '';
}

const fileProblem = computed(() => (file.value ? UtilDocIntake.fileProblem(props.doc, file.value) : null));
const fileError = computed(() => {
    if (fileProblem.value) return t(`docIntake.problem.file.${fileProblem.value}`);
    if (submitted.value && !file.value) return t('docIntake.problem.file.REQUIRED');
    return props.fieldErrors.file ?? '';
});

function onFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    file.value = input.files?.[0] ?? null;
    input.value = '';
}

function submit(): void {
    submitted.value = true;
    if (props.busy) return;
    if (fileMode.value) {
        if (file.value && !fileProblem.value) emit('submitFile', file.value);
        return;
    }
    if (Object.keys(problems.value).length) return;
    const d = draft.value;
    emit('submitPaper', {
        channel: d.channel,
        receivedOn: d.receivedOn,
        pages: d.pages,
        memo: d.memo.trim() || null,
        handling: d.handling,
        storageLocation: d.handling === 'KEEP' ? d.storageLocation.trim() || null : null,
    });
}
</script>
