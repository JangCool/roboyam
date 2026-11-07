<template>
    <!--
        약관 게시 대화상자 — 시행일(날짜, 사이트 시간대의 달력 날짜 `YYYY-MM-DD`)·선택 변경 요지(0~200자)·「회원에게 불리한 변경이에요」를 받는다. 「게시한 버전은 고칠 수 없어요」와 자리표시 안내를 늘 보인다.
        최소 시행일(화면 편의 — 판정은 서버, `UtilTerms.noticeDays`): 그 종류의 첫 게시본이면 내일, 개정이면 7일 뒤, 불리한 변경이면 30일 뒤. 마지막 게시본 시행일 이후(같은 날 허용) 규칙도 그대로다.
          불리한 변경을 켜서 최소일이 늦어지면 고른 날이 그보다 이를 때 최소일로 옮긴다. 켜면 「시행 30일 전부터 공지하고, 가입 회원에게 메일로도 알려요」(메일 발송은 서버 후속 — 지금은 문구만).
        서버 거절은 칸에 붙는 것(`fields.effectiveDate`·`fields.changeNote`)은 그 칸 아래, 나머지(`fields.draft` — 초안 없음 등)는 대화상자 안 한 줄(서버 문구 그대로).
        서버를 부르지 않는다 — 값을 `submit` 으로 넘기고 화면(`pages/settings/terms.vue`)이 게시한다.
    -->
    <Dialog v-model:visible="visible" modal :draggable="false" :closable="!busy" :header="t('terms.publishTitle', { name: kindLabel })" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @show="reset">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="warn" size="small" :closable="false">{{ t('terms.publishImmutable') }}</Message>
            <Message severity="info" size="small" :closable="false">{{ t('terms.publishPlaceholderNote') }}</Message>

            <div class="flex flex-col gap-1">
                <div class="flex items-start gap-2.5">
                    <Checkbox :model-value="unfavorable" binary :input-id="`${formId}-unfavorable`" :disabled="busy" class="mt-0.5" :aria-describedby="unfavorable ? `${formId}-unfavorable-help` : undefined" @update:model-value="onUnfavorable" />
                    <label :for="`${formId}-unfavorable`" class="cursor-pointer text-sm">{{ t('terms.unfavorable') }}</label>
                </div>
                <p v-if="unfavorable" :id="`${formId}-unfavorable-help`" class="m-0 pl-7 text-xs leading-relaxed text-amber-700 dark:text-amber-400">{{ t('terms.unfavorableHelp') }}</p>
            </div>

            <LabeledField :label="t('terms.effectiveDate')" :help="t('terms.effectiveDateHelp', { rule: ruleText })" :error="effectiveDateError">
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
    /** 종류 이름(이용약관·개인정보처리방침·운영정책 …) */
    kindLabel: string;
    /** 오늘(`YYYY-MM-DD`, 표시 시간대) — 최소 시행일의 기준 */
    today: string;
    /** 그 종류의 마지막 게시본 시행일(없으면 null — 첫 게시본) */
    lastEffectiveDate?: string | null;
    busy?: boolean;
    /** 칸에 붙지 않는 서버 문구 */
    error?: string;
    /** 서버가 준 칸 오류(`fields`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), {
    lastEffectiveDate: null,
    busy: false,
    error: '',
    fieldErrors: () => ({}),
});

const visible = defineModel<boolean>('visible', { required: true });
const emit = defineEmits<{ submit: [body: TermsPublishRequest] }>();

const { t } = useI18n();
const time = useDisplayTime();
const formId = useId();

const effectiveDate = ref('');
const changeNote = ref('');
const unfavorable = ref(false);
/** 화면이 미리 본 칸 오류 — [게시] 를 누른 뒤부터 보인다 */
const tried = ref(false);

/** 개정인가 — 그 종류에 게시본이 이미 있다 */
const revision = computed(() => Boolean(props.lastEffectiveDate));
/** 고를 수 있는 가장 이른 시행일 */
const minDate = computed(() => UtilTerms.minEffectiveDate(props.today, props.lastEffectiveDate, UtilTerms.noticeDays(revision.value, unfavorable.value)));
const ruleText = computed(() => {
    if (unfavorable.value) return t('terms.effectiveDateRuleUnfavorable', { days: UtilTerms.NOTICE_DAYS_UNFAVORABLE });
    return revision.value ? t('terms.effectiveDateRuleRevision', { days: UtilTerms.NOTICE_DAYS_REVISION }) : t('terms.effectiveDateRuleFirst');
});

/** 열릴 때마다 처음 값으로 */
function reset(): void {
    unfavorable.value = false;
    effectiveDate.value = minDate.value;
    changeNote.value = '';
    tried.value = false;
}

/** 불리한 변경을 켜고 끈다 — 최소일이 늦어져 고른 날이 그보다 이르면 최소일로 옮긴다 */
function onUnfavorable(value: boolean): void {
    unfavorable.value = value;
    if (!effectiveDate.value || effectiveDate.value < minDate.value) effectiveDate.value = minDate.value;
}

const effectiveDateError = computed(() => {
    if (props.fieldErrors.effectiveDate) return props.fieldErrors.effectiveDate;
    if (!tried.value) return '';
    if (!effectiveDate.value) return t('terms.effectiveDateRequired');
    return effectiveDate.value < minDate.value ? t('terms.effectiveDateTooEarly', { date: time.value.serviceDate(minDate.value) }) : '';
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
    if (!effectiveDate.value || effectiveDate.value < minDate.value || changeNote.value.length > UtilTerms.CHANGE_NOTE_MAX) return;
    const note = changeNote.value.trim();
    emit('submit', note ? { effectiveDate: effectiveDate.value, changeNote: note, unfavorable: unfavorable.value } : { effectiveDate: effectiveDate.value, unfavorable: unfavorable.value });
}
</script>
