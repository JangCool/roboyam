<template>
    <!--
        신청 건 가운데 칸 — 신청 내용 + 상태별 처리.
        · 심사 대기: 점검표 5개(규격·링크·정책·기간·가격) — 다 체크해야 [승인]. 시작이 지났으면 경고(승인 창에서 기간을 옮긴다 — F6). 자리가 없으면 경고.
          운영자 등록 건: 계약서 사본을 심사 대기부터 올릴 수 있고(서버 허용), [취소](등록 실수)가 있다. 자기 승인을 막는 설정이면 [승인] 대신 「다른 운영자가 승인」 안내.
        · 결제 대기: 확정 금액 · 결제 기한 카운트다운 · [수동 결제로 전환] · [취소](결제 완료는 사업자의 온라인 결제 — 결제 모듈).
        · 입금·계약 확인 대기(기한 지남 포함): `BizAppDepositPanel`.
        · 환불: 종류·상태 · 일할 계산 제안 · [거절] · [환불 승인](열린 환불만) · 수동 환불 송금 대기면 [송금 완료 표시].
        · 내림(사업자 정지·긴급) 사유. 계약서 사본은 [보기](`viewContract` — 부모가 `data:` 주소로 연다, 열람 기록).
        · 그 밖: 결제 기록(온라인 결제 또는 계약서·입금 기록)만.
        맨 아래 처리 기록. 서버는 부르지 않는다 — 동작은 emit.
    -->
    <div class="flex flex-col gap-5 p-4">
        <header class="flex flex-col gap-1">
            <div class="flex flex-wrap items-center gap-2">
                <code class="text-[12px] text-muted-color">{{ detail.no }}</code>
                <Tag :value="t(`bizApplication.kind.${detail.kind}`)" severity="secondary" />
                <Tag :value="t(`bizApplication.status.${detail.status}`)" :severity="UtilBizApplication.statusSeverity(detail.status)" />
                <Tag v-if="detail.source === 'OPERATOR'" :value="t('bizApplication.sourceOperator')" severity="info" />
            </div>
            <h2 class="m-0 text-base font-semibold">{{ detail.business.name }} <span class="text-[12px] font-normal text-muted-color">{{ detail.business.maskedRegNo }}</span></h2>
            <span class="text-[12px] text-muted-color">{{ t('bizApplication.submittedBy', { who: detail.applicant, at: time.shortDateTime(detail.submittedAt) }) }}</span>
        </header>

        <dl class="m-0 grid grid-cols-[7rem_1fr] gap-x-3 gap-y-1.5 rounded-lg border border-(--line) px-3 py-2.5 text-[13px]">
            <template v-if="detail.adType">
                <dt class="text-muted-color">{{ t('bizApplication.adTypeLabel') }}</dt>
                <dd class="m-0">{{ t(`bizApplication.adType.${detail.adType}`) }}</dd>
            </template>
            <dt class="text-muted-color">{{ detail.kind === 'HOTDEAL_POST' ? t('bizApplication.dealPeriod') : t('bizApplication.periodLabel') }}</dt>
            <dd class="m-0 tabular-nums">{{ UtilBizApplicationMessages.period(t, time, detail.period) }} <span class="text-muted-color">· {{ t('bizApplication.daysN', { n: UtilBizApplication.days(detail.period) }) }}</span></dd>
            <template v-if="detail.adType && detail.status === 'SUBMITTED'">
                <dt class="text-muted-color">{{ t('bizApplication.slots') }}</dt>
                <dd class="m-0" :class="detail.remainingSlots <= 0 ? 'font-semibold text-red-600 dark:text-red-400' : ''">{{ t('bizApplication.slotsLeft', { n: detail.remainingSlots, limit: detail.slotLimit }) }}</dd>
            </template>
            <dt class="text-muted-color">{{ t('bizApplication.preferredMethod') }}</dt>
            <dd class="m-0">{{ t(`bizApplication.method.${detail.preferredMethod}`) }}</dd>
            <dt class="text-muted-color">{{ detail.approval ? t('bizApplication.amountFixed') : t('bizApplication.quoteLabel') }}</dt>
            <dd class="m-0 tabular-nums">
                {{ won(detail.approval?.amount.total ?? detail.quote.total) }}
                <span class="text-[11px] text-muted-color">({{ t('bizApplication.supplyVat', { supply: won(detail.approval?.amount.supply ?? detail.quote.supply), vat: won(detail.approval?.amount.vat ?? detail.quote.vat) }) }})</span>
                <span v-if="detail.approval?.adjustReason" class="block text-[11px] text-muted-color">{{ t('bizApplication.adjusted', { reason: detail.approval.adjustReason }) }}</span>
                <span v-for="line in quoteLines" :key="line.product" class="block text-[11px] text-muted-color">{{ t('bizApplication.quoteLine', { product: t(`bizApplication.product.${line.product}`), price: won(line.unitPrice), units: line.units, unit: t(`bizApplication.unit.${line.unit}`), supply: won(line.supply) }) }}</span>
            </dd>
            <template v-if="operator">
                <dt class="text-muted-color">{{ t('bizApplication.weight') }}</dt>
                <dd class="m-0 tabular-nums">{{ detail.weight }}</dd>
            </template>
            <template v-if="detail.registrant">
                <dt class="text-muted-color">{{ t('bizApplication.registrant') }}</dt>
                <dd class="m-0">{{ detail.registrant.name }}<Tag v-if="detail.registrant.me" :value="t('bizApplication.registrantMe')" severity="secondary" class="ml-1" /></dd>
            </template>
            <template v-if="detail.memo">
                <dt class="text-muted-color">{{ operator ? t('bizApplication.operatorMemo') : t('bizApplication.memo') }}</dt>
                <dd class="m-0 [overflow-wrap:anywhere]">{{ detail.memo }}</dd>
            </template>
            <template v-if="detail.policyAgreedVersion">
                <dt class="text-muted-color">{{ t('bizApplication.agreed') }}</dt>
                <dd class="m-0 text-[12px]">{{ detail.policyAgreedVersion }}</dd>
            </template>
        </dl>

        <!-- 심사 대기 -->
        <section v-if="detail.status === 'SUBMITTED'" class="flex flex-col gap-3">
            <Message v-if="UtilBizApplication.started(detail.period, now)" severity="warn" :closable="false"><span class="text-[13px]">{{ t('bizApplication.startPassedNote') }}</span></Message>
            <Message v-if="detail.adType && detail.remainingSlots <= 0" severity="error" :closable="false"><span class="text-[13px]">{{ t('bizApplication.noSlotNote') }}</span></Message>
            <fieldset class="m-0 flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5" :disabled="!canManage || busy">
                <legend class="px-1 text-[13px] font-semibold">{{ t('bizApplication.checklist') }} <span class="font-normal tabular-nums text-muted-color">{{ checked.length }}/{{ UtilBizApplication.CHECKLIST.length }}</span></legend>
                <div v-for="item in UtilBizApplication.CHECKLIST" :key="item" class="flex items-start gap-2">
                    <Checkbox v-model="checked" :input-id="`${uid}-${item}`" :value="item" />
                    <label :for="`${uid}-${item}`" class="text-[13px] leading-snug">{{ t(`bizApplication.check.${item}`) }}</label>
                </div>
            </fieldset>
            <!-- 운영자 등록 건 — 오프라인으로 먼저 받은 계약서 사본은 심사 대기부터 올릴 수 있다(입금 확인은 승인 뒤) -->
            <div v-if="operator && canManage" class="flex flex-col gap-1.5">
                <h4 class="m-0 text-[13px] font-semibold">{{ t('bizApplication.contract.title') }}</h4>
                <BizContractFiles :files="detail.contracts" editable viewable :busy="busy" :error="fieldErrors.file ?? error" @upload="(f) => emit('upload', f)" @replace="(b) => emit('replace', b)" @view="(id) => emit('viewContract', id)" />
            </div>
            <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                <Button v-if="operator" type="button" :label="t('bizApplication.actCancel')" severity="secondary" variant="outlined" :disabled="busy" @click="emit('cancel')" />
                <Button type="button" :label="t('bizApplication.actReject')" severity="danger" variant="outlined" :disabled="busy" @click="emit('reject')" />
                <Message v-if="selfDenied" severity="info" size="small" :closable="false"><span class="text-[13px]">{{ t('bizApplication.selfApprovalDenied') }}</span></Message>
                <Button v-else type="button" :label="t('bizApplication.actApprove')" icon="pi pi-check" :disabled="busy || checked.length < UtilBizApplication.CHECKLIST.length" @click="emit('approve')" />
            </div>
            <p v-if="canManage && !selfDenied && checked.length < UtilBizApplication.CHECKLIST.length" class="m-0 text-right text-xs text-muted-color">{{ t('bizApplication.checklistHint') }}</p>
        </section>

        <!-- 결제 대기 -->
        <section v-else-if="detail.status === 'AWAITING_PAYMENT'" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="text-[13px] font-semibold">{{ t('bizApplication.awaitingOnline') }}</span>
                <Tag v-if="detail.deadlineAt" :value="UtilBizApplicationMessages.remain(t, detail.deadlineAt, now)" :severity="UtilBizApplicationMessages.remainSeverity(detail.deadlineAt, now)" icon="pi pi-clock" />
            </div>
            <p class="m-0 text-xs text-muted-color">{{ t('bizApplication.awaitingOnlineHelp', { at: detail.deadlineAt ? time.full(detail.deadlineAt) : '—' }) }}</p>
            <div v-if="canManage" class="flex flex-wrap justify-end gap-2">
                <Button type="button" :label="t('bizApplication.actCancel')" size="small" severity="danger" variant="outlined" :disabled="busy" @click="emit('cancel')" />
                <Button type="button" :label="t('bizApplication.actSwitchManual')" icon="pi pi-file-edit" size="small" :disabled="busy" @click="emit('switchManual')" />
            </div>
        </section>

        <!-- 입금·계약 확인 대기 -->
        <BizAppDepositPanel
            v-else-if="detail.status === 'AWAITING_DEPOSIT' && canManage"
            :key="detail.id"
            :detail="detail"
            :busy="busy"
            :error="error"
            :field-errors="fieldErrors"
            :now="now"
            @upload="(f) => emit('upload', f)"
            @replace="(b) => emit('replace', b)"
            @save-deposit="(b) => emit('saveDeposit', b)"
            @confirm="emit('confirm')"
            @extend="emit('extend')"
            @cancel="emit('cancel')"
            @view-contract="(id) => emit('viewContract', id)"
        />

        <Message v-if="detail.rejection" severity="warn" :closable="false">
            <span class="text-[13px]">{{ t('bizApplication.rejectedNote', { code: t(`bizApplication.rejectCode.${detail.rejection.code}`) }) }} — {{ detail.rejection.text }}</span>
        </Message>

        <Message v-if="detail.takedown" severity="error" :closable="false">
            <span class="text-[13px]">{{ t('bizApplication.takedownNote', { cause: t(`bizApplication.takedownCause.${detail.takedown.cause}`), at: time.shortDateTime(detail.takedown.at) }) }}<template v-if="detail.takedown.text"> — {{ detail.takedown.text }}</template></span>
        </Message>

        <!-- 환불 -->
        <section v-if="refund" class="flex flex-col gap-2 rounded-lg border px-3 py-3" :class="refund.status === 'REQUESTED' || refund.transferStatus === 'PENDING' ? 'border-red-300 dark:border-red-800' : 'border-(--line)'">
            <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="text-[13px] font-semibold">{{ t('bizApplication.refundTitle') }} <span class="font-normal text-muted-color">· {{ t(`bizApplication.refundType.${refund.type}`) }}</span></span>
                <Tag :value="t(`bizApplication.refundStatus.${refund.status}`)" :severity="refund.status === 'REQUESTED' ? 'danger' : refund.status === 'APPROVED' ? 'success' : 'secondary'" />
            </div>
            <p v-if="refund.reason" class="m-0 text-[13px] [overflow-wrap:anywhere]">“{{ refund.reason }}” <span class="text-[11px] text-muted-color">· {{ time.shortDateTime(refund.requestedAt) }}</span></p>
            <p v-if="refund.type === 'FULL_AUTO'" class="m-0 text-[13px] tabular-nums">{{ t('bizApplication.refundFull', { paid: won(refund.paidAmount) }) }}</p>
            <p v-else class="m-0 text-[13px] tabular-nums">{{ t('bizApplication.refundCalc', { paid: won(refund.paidAmount), used: refund.usedDays, total: refund.totalDays, proposed: won(refund.proposedAmount) }) }}</p>
            <p v-if="refund.status !== 'REQUESTED' && (refund.approvedAmount !== null || refund.decisionReason)" class="m-0 text-[13px] tabular-nums [overflow-wrap:anywhere]">
                <template v-if="refund.approvedAmount !== null">{{ t('bizApplication.refundApprovedLine', { amount: won(refund.approvedAmount) }) }}</template>
                <template v-if="refund.decisionReason"> — {{ refund.decisionReason }}</template>
            </p>
            <!-- 돌려주는 길: 온라인(PG 취소) · 수동(운영자 송금) -->
            <template v-if="refund.status === 'APPROVED' && (refund.approvedAmount ?? 0) > 0">
                <p v-if="refund.method === 'ONLINE'" class="m-0 text-xs text-muted-color">{{ refund.completedAt ? t('bizApplication.refundPgDone', { at: time.shortDateTime(refund.completedAt) }) : t('bizApplication.refundPgPending') }}</p>
                <p v-else-if="refund.transferStatus === 'DONE'" class="m-0 text-xs text-muted-color">{{ t('bizApplication.refundTransferDone', { at: refund.transferredAt ? time.shortDateTime(refund.transferredAt) : '—' }) }}</p>
                <Message v-else-if="refund.transferStatus === 'PENDING'" severity="warn" :closable="false">
                    <div class="flex flex-wrap items-center justify-between gap-2 text-[13px]">
                        <span>{{ t('bizApplication.refundTransferPending', { amount: won(refund.approvedAmount ?? 0) }) }}</span>
                        <Button v-if="canRefund" type="button" :label="t('bizApplication.actMarkTransferred')" icon="pi pi-check" size="small" :disabled="busy" @click="emit('markTransferred')" />
                    </div>
                </Message>
            </template>
            <div v-if="refund.status === 'REQUESTED' && canRefund" class="flex justify-end gap-2">
                <Button type="button" :label="t('bizApplication.actDenyRefund')" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('denyRefund')" />
                <Button type="button" :label="t('bizApplication.actApproveRefund')" size="small" severity="danger" :disabled="busy" @click="emit('approveRefund')" />
            </div>
        </section>

        <!-- 결제 기록(확정 뒤) -->
        <section v-if="detail.onlinePayment || (detail.status !== 'AWAITING_DEPOSIT' && !(operator && canManage && detail.status === 'SUBMITTED') && (detail.contracts.length || detail.deposit))" class="flex flex-col gap-2">
            <h3 class="m-0 text-sm font-semibold">{{ t('bizApplication.paymentRecord') }}</h3>
            <p v-if="detail.onlinePayment" class="m-0 text-[13px]">{{ t('bizApplication.paidOnline', { at: time.shortDateTime(detail.onlinePayment.paidAt), tx: detail.onlinePayment.pgTxId }) }}</p>
            <template v-else>
                <BizContractFiles :files="detail.contracts" :viewable="canManage" :busy="busy" @view="(id) => emit('viewContract', id)" />
                <p v-if="detail.deposit" class="m-0 text-[13px]">{{ t('bizApplication.depositLine', { amount: won(detail.deposit.amount), date: time.serviceDate(detail.deposit.depositedOn), name: detail.deposit.depositorName, who: detail.deposit.confirmedBy }) }}</p>
            </template>
        </section>

        <section class="flex flex-col gap-2">
            <h3 class="m-0 text-sm font-semibold">{{ t('bizApplication.history') }}</h3>
            <Timeline :value="history" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                <template #content="{ item }">
                    <div class="flex flex-col pb-3">
                        <span class="font-medium">{{ UtilBizApplicationMessages.historyAction(t, item.action) }} <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                        <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                        <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ UtilBizApplicationMessages.historyNote(t, item.action, item.note, locale) }}</span>
                    </div>
                </template>
            </Timeline>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { BizApplicationDetail, BizDepositRequest } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    detail: BizApplicationDetail;
    canManage?: boolean;
    canRefund?: boolean;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
    now?: number;
}

const props = withDefaults(defineProps<Props>(), { canManage: false, canRefund: false, busy: false, error: '', fieldErrors: () => ({}), now: () => Date.now() });
const emit = defineEmits<{
    approve: [];
    reject: [];
    switchManual: [];
    cancel: [];
    upload: [files: File[]];
    replace: [body: { id: string; file: File }];
    saveDeposit: [body: BizDepositRequest];
    confirm: [];
    extend: [];
    approveRefund: [];
    denyRefund: [];
    markTransferred: [];
    viewContract: [id: string];
}>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const uid = useId();

/** 점검표 — 부모가 건마다 `:key` 로 다시 만든다 */
const checked = ref<string[]>([]);
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
const refund = computed(() => props.detail.refund);
/** 운영자 등록 건(광고 일정표 수동 등록) */
const operator = computed(() => props.detail.source === 'OPERATOR');
/** 내가 등록한 건인데 자기 승인을 막는 설정 — 서버 409 `BIZ_SELF_APPROVAL_DENIED` */
const selfDenied = computed(() => props.detail.registrant?.me === true && props.detail.selfApproval === 'DENY');
/** 요금표 줄 — 두 줄 이상(핫딜 글 + 노출)일 때만 나눠 보인다 */
const quoteLines = computed(() => (props.detail.quote.lines && props.detail.quote.lines.length > 1 ? props.detail.quote.lines : []));
const history = computed(() => [...props.detail.history].sort((a, b) => b.at.localeCompare(a.at)));
</script>
