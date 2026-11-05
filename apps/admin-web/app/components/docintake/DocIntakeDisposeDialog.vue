<template>
    <!--
        [폐기 처리] 대화상자 — 인쇄본을 폐기했다고 남긴다(되돌릴 수 없다). 폐기일(필수, 접수일 ~ 오늘) · 사유(필수, 200자) · 스캔본.
        계약서는 스캔본 필수 — 그 건의 대체되지 않은 파일 사본 중 고른다(5년 보존은 파일로 채운다). 하나도 없으면 [폐기 처리] 를 끄고 「스캔본을 먼저 올려야」 안내.
        등록증은 스캔본 선택(있으면 고르고, 없어도 된다). 서버는 부르지 않는다 — 저장·닫기는 부모 몫이다. 열 때마다 칸을 비운다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :close-on-escape="!busy" :header="t('docIntake.dispose.title')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="warn" :closable="false" icon="pi pi-exclamation-triangle"><span class="text-[13px]">{{ t('docIntake.dispose.warn') }}</span></Message>
            <p v-if="intake" class="m-0 text-[13px] text-muted-color">
                {{ t(`docIntake.channel.${intake.channel}`) }}<template v-if="intake.pages"> · {{ t('docIntake.pagesN', { n: intake.pages }) }}</template> · {{ t('docIntake.receivedLine', { date: time.serviceDate(intake.receivedOn), who: intake.receivedBy ?? '—' }) }}
            </p>

            <LabeledField :label="t('docIntake.dispose.disposedOn')" :error="err('disposedOn')">
                <template #default="{ id, describedBy, invalid }">
                    <AppDatePicker v-model="draft.disposedOn" :input-id="id" :min="intake?.receivedOn" :max="today" :disabled="busy" :invalid="invalid" :described-by="describedBy" />
                </template>
            </LabeledField>

            <LabeledField :label="t('docIntake.dispose.reason')" :hint="t('docIntake.dispose.reasonHint', { max: UtilDocIntake.DISPOSE_REASON_MAX })" :error="err('reason')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="draft.reason" rows="2" auto-resize :placeholder="t('docIntake.dispose.reasonPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" fluid />
                </template>
                <template #aside>
                    <small class="shrink-0 text-xs tabular-nums" :class="draft.reason.trim().length > UtilDocIntake.DISPOSE_REASON_MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ draft.reason.trim().length }}/{{ UtilDocIntake.DISPOSE_REASON_MAX }}</small>
                </template>
            </LabeledField>

            <LabeledField :label="t('docIntake.dispose.scan')" :hint="scanRequired ? '' : t('docIntake.dispose.scanOptional')" :help="t(`docIntake.dispose.scanHelp.${doc}`)" :error="err('scanFileId')">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        v-model="draft.scanFileId"
                        :input-id="id"
                        :options="scanOptions"
                        option-label="label"
                        option-value="id"
                        :placeholder="scanOptions.length ? t('docIntake.dispose.scanPick') : t('docIntake.dispose.scanEmpty')"
                        :show-clear="!scanRequired"
                        :disabled="busy || !scanOptions.length"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                    />
                </template>
            </LabeledField>
            <p v-if="blocked" class="m-0 flex items-center gap-1.5 text-xs text-red-700 dark:text-red-400" role="status"><i class="pi pi-lock" aria-hidden="true" />{{ t('docIntake.dispose.needScan') }}</p>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('docIntake.dispose.submit')" icon="pi pi-trash" severity="danger" :loading="busy" :disabled="blocked" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { DocIntake, DocIntakeDisposeRequest, DocIntakeDoc } from '~/composables/docintake/api/useDocIntakeApi.types';
import type { DocScanFile } from '~/composables/docintake/useDocIntake.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilDocIntake, { type DocIntakeDisposeDraft } from '~/utils/util-doc-intake';

interface Props {
    visible: boolean;
    doc: DocIntakeDoc;
    intake: DocIntake | null;
    /** 스캔본 후보 — 대체된 파일(`active: false`)은 고를 수 없다 */
    files?: DocScanFile[];
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { files: () => [], busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: DocIntakeDisposeRequest] }>();

const { t } = useI18n();
const time = useDisplayTime();
const formId = useId();
const today = UtilBizApplication.kstToday();

const draft = ref<DocIntakeDisposeDraft>({ disposedOn: today, reason: '', scanFileId: null });
const submitted = ref(false);

/** 계약서는 스캔본 필수(대체되지 않은 파일 사본이 있을 때만 폐기) */
const scanRequired = computed(() => props.doc === 'CONTRACT');
const scanOptions = computed(() => props.files.filter((f) => f.active));
/** 계약서인데 고를 파일 사본이 없다 — 폐기할 수 없다 */
const blocked = computed(() => scanRequired.value && scanOptions.value.length === 0);

watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        const only = scanOptions.value.length === 1 ? scanOptions.value[0]!.id : null;
        draft.value = { disposedOn: UtilBizApplication.kstToday(), reason: '', scanFileId: scanRequired.value ? only : null };
        submitted.value = false;
    },
    { immediate: true },
);

const problems = computed(() => UtilDocIntake.disposeProblems(draft.value, props.intake?.receivedOn ?? '', today, scanRequired.value));

function err(key: 'disposedOn' | 'reason' | 'scanFileId'): string {
    const p = problems.value[key];
    if (p && (submitted.value || p === 'TOO_LONG' || p === 'FUTURE' || p === 'BEFORE_RECEIVED')) return t(`docIntake.problem.${key}.${p}`, { max: UtilDocIntake.DISPOSE_REASON_MAX });
    return props.fieldErrors[key] ?? '';
}

function submit(): void {
    submitted.value = true;
    if (props.busy || blocked.value || Object.keys(problems.value).length) return;
    emit('submit', { disposedOn: draft.value.disposedOn, reason: draft.value.reason.trim(), scanFileId: draft.value.scanFileId });
}
</script>
