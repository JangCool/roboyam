<template>
    <!--
        약관 게시 대화상자 — 시행일(날짜, 사이트 시간대의 달력 날짜 `YYYY-MM-DD`)과 선택 변경 요지(0~200자)를 받는다. 「게시한 버전은 고칠 수 없어요」를 늘 보인다.
        서버 거절은 칸에 붙는 것(`fields.effectiveDate`·`fields.changeNote`)은 그 칸 아래, 나머지(`fields.draft` — 초안 없음 등)는 대화상자 안 한 줄(서버 문구 그대로).
        서버를 부르지 않는다 — 값을 `submit` 으로 넘기고 화면(`pages/settings/terms.vue`)이 게시한다.
    -->
    <Dialog v-model:visible="visible" modal :draggable="false" :closable="!busy" :header="t('terms.publishTitle', { name: kindLabel })" :style="{ width: 'min(30rem, calc(100vw - 2rem))' }" @show="reset">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="warn" size="small" :closable="false">{{ t('terms.publishImmutable') }}</Message>

            <LabeledField :label="t('terms.effectiveDate')" :help="t('terms.effectiveDateHelp')" :error="effectiveDateError">
                <template #default="{ id, describedBy, invalid }">
                    <AppDatePicker v-model="effectiveDate" :input-id="id" :described-by="describedBy" :min="minDate" :invalid="invalid" :disabled="busy" />
                </template>
            </LabeledField>

            <LabeledField :label="t('terms.changeNote')" :hint="t('terms.changeNoteHint', { count: changeNote.length, max: UtilTerms.CHANGE_NOTE_MAX })" :help="t('terms.changeNoteHelp')" :error="changeNoteError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="changeNote" name="changeNote" rows="3" auto-resize :maxlength="UtilTerms.CHANGE_NOTE_MAX" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ formError }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="visible = false" />
            <Button type="submit" :form="formId" :label="t('terms.publishSubmit')" icon="pi pi-send" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { TermsPublishRequest } from '~/composables/terms/api/useTermsApi.types';
import UtilTerms from '~/utils/util-terms';

interface Props {
    /** 종류 이름(이용약관·개인정보처리방침) */
    kindLabel: string;
    /** 고를 수 있는 가장 이른 시행일(`YYYY-MM-DD`) — 처음 값도 이 날이다 */
    minDate: string;
    busy?: boolean;
    /** 칸에 붙지 않는 서버 문구 */
    error?: string;
    /** 서버가 준 칸 오류(`fields`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
    busy: false,
    error: '',
    fieldErrors: () => ({}),
});

const visible = defineModel<boolean>('visible', { required: true });
const emit = defineEmits<{ submit: [body: TermsPublishRequest] }>();

const { t } = useI18n();
const formId = useId();

const effectiveDate = ref('');
const changeNote = ref('');
/** 화면이 미리 본 칸 오류 — [게시] 를 누른 뒤부터 보인다 */
const tried = ref(false);

/** 열릴 때마다 처음 값으로 */
function reset(): void {
    effectiveDate.value = props.minDate;
    changeNote.value = '';
    tried.value = false;
}

const effectiveDateError = computed(() => {
    if (props.fieldErrors.effectiveDate) return props.fieldErrors.effectiveDate;
    if (!tried.value) return '';
    if (!effectiveDate.value) return t('terms.effectiveDateRequired');
    return effectiveDate.value < props.minDate ? t('terms.effectiveDateTooEarly') : '';
});
const changeNoteError = computed(() => props.fieldErrors.changeNote ?? (tried.value && changeNote.value.length > UtilTerms.CHANGE_NOTE_MAX ? t('terms.changeNoteTooLong', { max: UtilTerms.CHANGE_NOTE_MAX }) : ''));

/** 칸에 붙지 않는 오류 — 서버의 칸 밖 문구 + 이 대화상자에 칸이 없는 `fields`(`draft` 등) */
const formError = computed(() => {
    const unplaced = Object.entries(props.fieldErrors)
        .filter(([key]) => key !== 'effectiveDate' && key !== 'changeNote')
        .map(([, message]) => message);
    return [props.error, ...unplaced].filter(Boolean).join(' ');
});

function submit(): void {
    if (props.busy) return;
    tried.value = true;
    if (!effectiveDate.value || effectiveDate.value < props.minDate || changeNote.value.length > UtilTerms.CHANGE_NOTE_MAX) return;
    const note = changeNote.value.trim();
    emit('submit', note ? { effectiveDate: effectiveDate.value, changeNote: note } : { effectiveDate: effectiveDate.value });
}
</script>
