<template>
    <!--
        일괄 무시 대화창 — 고른 열린 사건을 한꺼번에 무시한다(일괄은 무시만). 「허위 신고로 표시」 체크 + 글(선택, 0~500자).
        보내고 나면(`result`) 같은 대화창이 결과 모습으로 바뀐다 — 사건마다 처리됨/못 함 + 까닭(아는 코드는 문구, 모르는 코드는 코드 그대로). 서버는 사건마다 따로 처리한다.
        열 때마다 칸을 비운다. 서버 호출은 부른 화면이 `submit` 을 받아 한다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="result ? t('report.bulkResultTitle') : t('report.bulkTitle', { n: count })" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }">
        <!-- 결과 -->
        <div v-if="result" class="flex flex-col gap-3">
            <p class="m-0 text-sm font-medium">{{ UtilReportMessages.bulkSummary(t, result) }}</p>
            <ul class="m-0 flex max-h-80 list-none flex-col divide-y divide-(--line) overflow-y-auto rounded-lg border border-(--line) p-0">
                <li v-for="row in UtilReport.bulkRows(result)" :key="row.id" class="flex items-start gap-2 px-3 py-2 text-[13px]">
                    <i :class="row.ok ? 'pi pi-check-circle text-green-600 dark:text-green-400' : 'pi pi-times-circle text-red-600 dark:text-red-400'" class="mt-0.5 text-xs" aria-hidden="true" />
                    <div class="flex min-w-0 flex-1 flex-col">
                        <span class="truncate">{{ titles[row.id] ?? `#${row.id}` }}</span>
                        <span class="text-xs text-muted-color">{{ row.ok ? t('report.bulkRowDone') : `${t('report.bulkRowFailed')} · ${UtilReportMessages.bulkCode(t, row.code)}` }}</span>
                    </div>
                </li>
            </ul>
        </div>

        <form v-else :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('report.bulkText') }}</p>

            <LabeledField :label="t('report.noteLabel')" :hint="t('report.noteCounter', { n: UtilReport.noteLength(note), max: UtilReport.NOTE_MAX })" :help="t('report.noteHelpDismiss')" :error="noteError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="note" name="note" rows="3" auto-resize fluid :placeholder="t('report.notePlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <div class="flex items-start gap-2">
                <Checkbox v-model="falseReport" binary :input-id="`${formId}-false`" :disabled="busy" />
                <label :for="`${formId}-false`" class="flex flex-col text-sm">
                    {{ t('report.falseReport') }}
                    <span class="text-xs leading-relaxed text-muted-color">{{ t('report.falseReportHelp') }}</span>
                </label>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button v-if="result" type="button" :label="t('common.close')" @click="open = false" />
            <template v-else>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
                <Button type="submit" :form="formId" :label="t('report.bulkDismiss', { n: count })" :loading="busy" :disabled="count === 0" />
            </template>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { ReportBulkResult } from '~/composables/report/api/useReportApi.types';
import UtilReport from '~/utils/util-report';
import UtilReportMessages from '~/utils/util-report-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 보낼 사건 수 */
    count: number;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 준 `fields.note` */
    fieldError?: string;
    /** 끝난 결과 — 있으면 결과 모습 */
    result?: ReportBulkResult | null;
    /** 사건 ID → 보일 이름(결과 줄) */
    titles?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldError: '', result: null, titles: () => ({}) });
const emit = defineEmits<{ submit: [payload: { note: string; falseReport: boolean }] }>();

const { t } = useI18n();
const formId = useId();

const note = ref('');
const falseReport = ref(false);
const tried = ref(false);

const noteError = computed(() => props.fieldError || (tried.value ? UtilReportMessages.noteProblem(t, UtilReport.noteProblem(note.value, false)) : ''));

watch(open, (isOpen) => {
    if (!isOpen) return;
    note.value = '';
    falseReport.value = false;
    tried.value = false;
});

function submit(): void {
    if (props.busy || props.count === 0) return;
    tried.value = true;
    if (UtilReport.noteProblem(note.value, false)) return;
    emit('submit', { note: note.value.trim(), falseReport: falseReport.value });
}
</script>
