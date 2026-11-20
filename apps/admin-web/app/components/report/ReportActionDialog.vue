<template>
    <!--
        사건 하나의 처리 대화창(게시글 관리 `PostReasonDialog` 방식) — [숨기기] · [자동 숨김 확정] · [삭제] · [소모임 비활성화](소모임 대상) · [핫딜 종료](핫딜 글 대상 — `DEAL_END`) · [제재만](`SANCTION_ONLY`) · [무시].
        칸: 사유 코드(사건 대상의 체계 — 글·댓글 6종(게시판 신고 정책의 사유 이름) · 핫딜 글은 + 핫딜 사유 2개 · 소모임 4종) + 글(조치는 필수 1~500자 — 소모임 비활성화·핫딜 종료는 1~200자, 무시는 선택).
        핫딜 글에서 「가격·정보가 사실과 달라요」를 고르면 D4 안내(종료 요청 「가격 변경」으로도 세어지고 자동 숨김 셈에서 빠진다) 한 줄. 무시는 「허위 신고로 표시」 체크(게시판 정책 반려 제한이 세는 값).
        조치는 사유 코드 필수(처음 값 = 신고가 가장 많은 사유), 무시는 사유 코드를 고르지 않아도 된다. 작성자 알림에는 사유 코드 이름과 글이 간다(게시판 정책 `notifyAuthorOnAction`).
        작성자 제재(member-sanction-contract 결정 4): 조치에는 「작성자 제재 곁들이기」 체크 → 단계 고르개(`SanctionStepPicker` — [제재하기] 대화창과 같다) + 제재 사유(처음은 처리 글과 같다 — 고치면 따로),
          「제재만」은 대상은 그대로 두고 고르개가 늘 열린다. 무시에는 제재가 없다. 비회원·탈퇴 회원 작성자는 제재 불가 안내. 제재 오류(`SANCTION_*`)가 나면 조치도 일어나지 않는다(서버 한 트랜잭션).
        열 때마다 칸을 비운다(앞 사건에 적던 글이 다음 사건에 딸려 가면 안 된다). 서버 호출은 부른 화면이 `submit` 을 받아 한다.
        칸 오류(`fields.reasonCode`·`fields.note`·`fields.sanction.*`)는 그 칸 아래, 그 밖 거절(서버 문구 그대로)은 대화창 안 한 줄. 실패해도 쓰던 글은 그대로 둔다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="kind ? t(`report.dialog.${kind}.title`) : ''" :style="{ width: 'min(36rem, calc(100vw - 2rem))' }">
        <form v-if="kind" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div v-if="item" class="flex flex-col gap-0.5 rounded-lg border border-(--line) px-3 py-2">
                <div class="flex min-w-0 items-center gap-1.5">
                    <Tag :value="t(`report.targetType.${item.targetType}`)" :severity="UtilReport.targetTypeSeverity(item.targetType)" class="shrink-0 text-[11px]!" />
                    <p class="m-0 min-w-0 truncate text-sm font-medium" :class="item.target.title === null ? 'font-normal text-muted-color' : ''">{{ UtilReportMessages.targetTitle(t, item) }}</p>
                </div>
                <p v-if="item.targetType !== 'POST' && item.target.excerpt" class="m-0 truncate text-xs text-muted-color">{{ item.target.excerpt }}</p>
                <p class="m-0 text-xs text-muted-color">{{ UtilReportMessages.caseNo(t, item) }} · {{ t('report.reportCountValue', { n: item.reportCount }) }}</p>
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`report.dialog.${kind}.text`) }}</p>
            <Message v-if="dismissNote" severity="secondary" size="small" :closable="false" icon="pi pi-info-circle">{{ dismissNote }}</Message>
            <Message v-if="sponsorNote" severity="secondary" size="small" :closable="false" icon="pi pi-info-circle">{{ sponsorNote }}</Message>
            <Message v-if="isDeal && reasonCode === 'DEAL_FALSE_PRICE'" severity="secondary" size="small" :closable="false" icon="pi pi-info-circle">{{ t('report.dealFalsePriceNote') }}</Message>

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

            <!-- 작성자 제재 — 조치에 곁들이기 / 제재만(체험단 협찬 숨김 확인과 같은 부품) -->
            <ReportSanctionSection
                v-if="sanctionAllowed"
                v-model:with="attach.withSanction.value"
                v-model:step="attach.step.value"
                :sanction="sanction"
                :only="isSanctionOnly"
                :on="attach.on.value"
                :busy="busy"
                :tried="tried"
                :reason-shown="attach.reasonShown.value"
                :reason-touched="attach.reasonTouched.value"
                :reason-error="attach.reasonError.value"
                :step-field-errors="attach.stepFieldErrors.value"
                @update:reason="attach.onReason"
                @retry="emit('retry-sanction')"
            />

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button v-if="kind" type="submit" :form="formId" :label="submitLabel" :severity="UtilReport.isDanger(kind) || attach.on.value ? 'danger' : undefined" :loading="busy" :disabled="isSanctionOnly && !attach.ready.value" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { ReportActPayload, ReportSanctionContext } from '~/components/report/ReportActionDialog.types';
import type { ReportCaseItem, ReportReasonCode } from '~/composables/report/api/useReportApi.types';
import UtilReport, { type ReportActKind } from '~/utils/util-report';
import UtilReportMessages from '~/utils/util-report-messages';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 처리 종류 — 없으면 아무것도 그리지 않는다 */
    kind?: ReportActKind | null;
    item?: ReportCaseItem | null;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 준 칸 오류(`reasonCode`·`note`·`sanction.kind`·`sanction.reason`·`sanction.skipStep`·`sanction.confirmNickname`) */
    fieldErrors?: Record<string, string>;
    /** 작성자 제재 — 없으면 제재 구획을 그리지 않는다 */
    sanction?: ReportSanctionContext | null;
}

const props = withDefaults(defineProps<Props>(), { kind: null, item: null, busy: false, error: '', fieldErrors: () => ({}), sanction: null });
const emit = defineEmits<{ submit: [payload: ReportActPayload]; 'retry-sanction': [] }>();

const { t } = useI18n();
const formId = useId();

const reasonCode = ref<ReportReasonCode | null>(null);
const note = ref('');
const falseReport = ref(false);
const tried = ref(false);

const isDismiss = computed(() => props.kind === 'DISMISS');
const isSanctionOnly = computed(() => props.kind === 'SANCTION_ONLY');
// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const isDeal = computed(() => (props.item ? UtilReport.isDealCase(props.item) : false));
const reasonOptions = computed(() => UtilReport.reasonsFor(props.item?.targetType ?? 'POST', isDeal.value, props.item ? UtilReport.isSponsorCase(props.item) : false).map((value) => ({ value, label: UtilReportMessages.reasonName(t, value) })));
/**
 * 협찬 숨김 신고가 든 글 사건(점검 D2) — 사건을 닫으면 체험단 「확인할 일」의 협찬 숨김 줄도 함께 닫힌다.
 * 사유 `SPONSOR_HIDDEN` 으로 조치 = 협찬 고정 확인, 그 밖 조치·무시 = 그 줄은 무시로.
 */
const sponsorNote = computed(() => {
    if (!props.item || !UtilReport.isSponsorCase(props.item)) return '';
    return !isDismiss.value && reasonCode.value === 'SPONSOR_HIDDEN' ? t('report.sponsorCaseConfirm') : t('report.sponsorCaseDismiss');
});
/** 글 최대 — 소모임 비활성화는 200자(모임장에게 사유로 간다) */
const noteMax = computed(() => UtilReport.noteMax(props.kind));
const noteHint = computed(() => t('report.noteCounter', { n: UtilReport.noteLength(note.value), max: noteMax.value }));

const reasonCodeError = computed(() => props.fieldErrors.reasonCode || (tried.value && !isDismiss.value && !reasonCode.value ? t('report.reasonCodeRequired') : ''));
const noteError = computed(() => props.fieldErrors.note || (tried.value ? UtilReportMessages.noteProblem(t, UtilReport.noteProblem(note.value, !isDismiss.value, noteMax.value), noteMax.value) : ''));

/* ── 작성자 제재 ── */

const sanctionAllowed = computed(() => props.sanction !== null && UtilReport.allowsSanction(props.kind));
/** 곁들이는 제재 상태 — 체험단 협찬 숨김 확인과 같은 컴포저블 */
const attach = useReportSanctionAttach({
    context: () => props.sanction,
    allowed: () => sanctionAllowed.value,
    only: () => isSanctionOnly.value,
    note: () => note.value,
    fieldErrors: () => props.fieldErrors,
    tried: () => tried.value,
});
const submitLabel = computed(() => {
    if (!props.kind) return '';
    const stepKind = attach.step.value.kind;
    if (attach.on.value && !isSanctionOnly.value && stepKind) return t('report.actWithSanction', { act: t(`report.act.${props.kind}`), kind: UtilSanctionMessages.kindName(t, stepKind) });
    return t(`report.act.${props.kind}`);
});

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
    attach.reset();
});

function submit(): void {
    if (props.busy || !props.kind) return;
    tried.value = true;
    if (!isDismiss.value && !reasonCode.value) return;
    if (UtilReport.noteProblem(note.value, !isDismiss.value, noteMax.value)) return;
    const built = attach.build();
    if (!built.valid) return;
    const sanction: ReportActPayload['sanction'] = built.sanction;
    emit('submit', { reasonCode: reasonCode.value, note: note.value.trim(), falseReport: isDismiss.value && falseReport.value, sanction });
}
</script>
