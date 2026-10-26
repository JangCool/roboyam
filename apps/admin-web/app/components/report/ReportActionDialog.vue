<template>
    <!--
        사건 하나의 처리 대화창(게시글 관리 `PostReasonDialog` 방식) — [숨기기] · [자동 숨김 확정] · [삭제] · [소모임 비활성화](소모임 대상) · [무시].
        칸: 사유 코드(사건 대상의 체계 — 글·댓글 6종(게시판 신고 정책의 사유 이름) · 소모임 4종) + 글(조치는 필수 1~500자, 무시는 선택). 무시는 「허위 신고로 표시」 체크(게시판 정책 반려 제한이 세는 값).
        조치는 사유 코드 필수(처음 값 = 신고가 가장 많은 사유), 무시는 사유 코드를 고르지 않아도 된다. 작성자 알림에는 사유 코드 이름과 글이 간다(게시판 정책 `notifyAuthorOnAction`).
        열 때마다 칸을 비운다(앞 사건에 적던 글이 다음 사건에 딸려 가면 안 된다). 서버 호출은 부른 화면이 `submit` 을 받아 한다.
        칸 오류(`fields.reasonCode`·`fields.note`)는 그 칸 아래, 그 밖 거절(서버 문구 그대로)은 대화창 안 한 줄. 실패해도 쓰던 글은 그대로 둔다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="kind ? t(`report.dialog.${kind}.title`) : ''" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }">
        <form v-if="kind" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div v-if="item" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                <div class="flex min-w-0 items-center gap-1.5">
                    <Tag :value="t(`report.targetType.${item.targetType}`)" :severity="UtilReport.targetTypeSeverity(item.targetType)" class="shrink-0 text-[11px]!" />
                    <p class="m-0 min-w-0 truncate text-sm font-medium" :class="item.target.title === null ? 'font-normal text-muted-color' : ''">{{ item.target.title ?? t('report.targetPurged') }}</p>
                </div>
                <p v-if="item.targetType !== 'POST' && item.target.excerpt" class="m-0 truncate text-xs text-muted-color">{{ item.target.excerpt }}</p>
                <p class="m-0 text-xs text-muted-color">{{ UtilReportMessages.caseNo(t, item) }} · {{ t('report.reportCountValue', { n: item.reportCount }) }}</p>
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`report.dialog.${kind}.text`) }}</p>
            <Message v-if="dismissNote" severity="secondary" size="small" :closable="false" icon="pi pi-info-circle">{{ dismissNote }}</Message>

            <LabeledField :label="t('report.reasonCodeLabel')" :hint="isDismiss ? t('report.optional') : ''" :error="reasonCodeError">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        v-model="reasonCode"
                        :input-id="id"
                        :options="reasonOptions"
                        option-label="label"
                        option-value="value"
                        :placeholder="t('report.reasonCodePlaceholder')"
                        :show-clear="isDismiss"
                        :disabled="busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        fluid
                    />
                </template>
            </LabeledField>

            <LabeledField :label="t('report.noteLabel')" :hint="noteHint" :help="isDismiss ? t('report.noteHelpDismiss') : t('report.noteHelp')" :error="noteError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="note" name="note" rows="3" auto-resize fluid :placeholder="t('report.notePlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <div v-if="isDismiss" class="flex items-start gap-2">
                <Checkbox v-model="falseReport" binary :input-id="`${formId}-false`" :disabled="busy" />
                <label :for="`${formId}-false`" class="flex flex-col text-sm">
                    {{ t('report.falseReport') }}
                    <span class="text-xs leading-relaxed text-muted-color">{{ t('report.falseReportHelp') }}</span>
                </label>
            </div>

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button v-if="kind" type="submit" :form="formId" :label="t(`report.act.${kind}`)" :severity="UtilReport.isDanger(kind) ? 'danger' : undefined" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { ReportCaseItem, ReportReasonCode } from '~/composables/report/api/useReportApi.types';
import UtilReport, { type ReportActKind } from '~/utils/util-report';
import UtilReportMessages from '~/utils/util-report-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 처리 종류 — 없으면 아무것도 그리지 않는다 */
    kind?: ReportActKind | null;
    item?: ReportCaseItem | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 준 칸 오류(`reasonCode`·`note`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { kind: null, item: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ submit: [payload: { reasonCode: ReportReasonCode | null; note: string; falseReport: boolean }] }>();

const { t } = useI18n();
const formId = useId();

const reasonCode = ref<ReportReasonCode | null>(null);
const note = ref('');
const falseReport = ref(false);
const tried = ref(false);

const isDismiss = computed(() => props.kind === 'DISMISS');
// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const reasonOptions = computed(() => UtilReport.reasonsFor(props.item?.targetType ?? 'POST').map((value) => ({ value, label: UtilReportMessages.reasonName(t, value) })));
/** 글 최대 — 소모임 비활성화는 200자(모임장에게 사유로 간다) */
const noteMax = computed(() => UtilReport.noteMax(props.kind));
const noteHint = computed(() => t('report.noteCounter', { n: UtilReport.noteLength(note.value), max: noteMax.value }));

const reasonCodeError = computed(() => props.fieldErrors.reasonCode || (tried.value && !isDismiss.value && !reasonCode.value ? t('report.reasonCodeRequired') : ''));
const noteError = computed(() => props.fieldErrors.note || (tried.value ? UtilReportMessages.noteProblem(t, UtilReport.noteProblem(note.value, !isDismiss.value, noteMax.value), noteMax.value) : ''));

/** 무시할 때 대상이 어떻게 되는지 — 자동 숨김은 복구, 운영자 숨김은 그대로 */
const dismissNote = computed(() => {
    if (!isDismiss.value || !props.item) return '';
    if (UtilReport.isAutoHidden(props.item.target)) return t('report.dismissRestores');
    if (props.item.target.state === 'HIDDEN') return t('report.dismissStaffKeeps');
    return '';
});

watch(open, (isOpen) => {
    if (!isOpen) return;
    // 조치는 신고가 가장 많은 사유를 먼저 골라 둔다(바꿀 수 있다). 무시는 비워 둔다
    reasonCode.value = props.kind !== 'DISMISS' && props.item ? UtilReport.topReason(props.item) : null;
    note.value = '';
    falseReport.value = false;
    tried.value = false;
});

function submit(): void {
    if (props.busy || !props.kind) return;
    tried.value = true;
    if (!isDismiss.value && !reasonCode.value) return;
    if (UtilReport.noteProblem(note.value, !isDismiss.value, noteMax.value)) return;
    emit('submit', { reasonCode: reasonCode.value, note: note.value.trim(), falseReport: isDismiss.value && falseReport.value });
}
</script>
