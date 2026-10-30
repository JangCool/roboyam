<template>
    <!--
        신청 건 가운데 칸 — 신청 내용 + 상태별 처리.
        · 심사 대기: 점검표 5개(규격·링크·정책·기간·가격) — 다 체크해야 [승인]. 시작이 지났으면 경고(승인 창에서 기간을 옮긴다 — F6). 자리가 없으면 경고.
        · 결제 대기: 확정 금액 · 결제 기한 카운트다운 · [수동 결제로 전환] · [취소] · (목업) 결제 완료.
        · 입금·계약 확인 대기(기한 지남 포함): `BizAppDepositPanel`.
        · 환불 심사: 일할 계산 제안 · [거절] · [환불 승인].
        · 그 밖: 결제 기록(온라인 결제 또는 계약서·입금 기록)만.
        맨 아래 처리 기록. 서버는 부르지 않는다 — 동작은 emit.
    -->
    <div class="flex flex-col gap-5 p-4">
        <header class="flex flex-col gap-1">
            <div class="flex flex-wrap items-center gap-2">
                <code class="text-[12px] text-muted-color">{{ detail.no }}</code>
                <Tag :value="t(`bizApplication.kind.${detail.kind}`)" severity="secondary" />
                <Tag :value="t(`bizApplication.status.${detail.status}`)" :severity="UtilBizApplication.statusSeverity(detail.status)" />
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
            </dd>
            <template v-if="detail.memo">
                <dt class="text-muted-color">{{ t('bizApplication.memo') }}</dt>
                <dd class="m-0 [overflow-wrap:anywhere]">{{ detail.memo }}</dd>
            </template>
            <dt class="text-muted-color">{{ t('bizApplication.agreed') }}</dt>
            <dd class="m-0 text-[12px]">{{ detail.policyAgreedVersion }}</dd>
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
            <div v-if="canManage" class="flex flex-wrap justify-end gap-2">
                <Button type="button" :label="t('bizApplication.actReject')" severity="danger" variant="outlined" :disabled="busy" @click="emit('reject')" />
                <Button type="button" :label="t('bizApplication.actApprove')" icon="pi pi-check" :disabled="busy || checked.length < UtilBizApplication.CHECKLIST.length" @click="emit('approve')" />
            </div>
            <p v-if="canManage && checked.length < UtilBizApplication.CHECKLIST.length" class="m-0 text-right text-xs text-muted-color">{{ t('bizApplication.checklistHint') }}</p>
        </section>

        <!-- 결제 대기 -->
        <section v-else-if="detail.status === 'AWAITING_PAYMENT'" class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-3">
            <div class="flex flex-wrap items-center justify-between gap-2">
                <span class="text-[13px] font-semibold">{{ t('bizApplication.awaitingOnline') }}</span>
                <Tag v-if="detail.deadlineAt" :value="UtilBizApplicationMessages.remain(t, detail.deadlineAt, now)" :severity="UtilBizApplicationMessages.remainSeverity(detail.deadlineAt, now)" icon="pi pi-clock" />
            </div>
            <p class="m-0 text-xs text-muted-color">{{ t('bizApplication.awaitingOnlineHelp', { at: detail.deadlineAt ? time.full(detail.deadlineAt) : '—' }) }}</p>
            <div v-if="canManage" class="flex flex-wrap justify-end gap-2">
                <Button type="button" :label="t('bizApplication.actMockPay')" icon="pi pi-credit-card" size="small" severity="secondary" variant="text" :disabled="busy" @click="emit('mockPay')" />
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
        />

        <Message v-if="detail.rejection" severity="warn" :closable="false">
            <span class="text-[13px]">{{ t('bizApplication.rejectedNote', { code: t(`bizApplication.rejectCode.${detail.rejection.code}`) }) }} — {{ detail.rejection.text }}</span>
        </Message>

        <!-- 환불 심사 -->
        <section v-if="detail.refund" class="flex flex-col gap-2 rounded-lg border px-3 py-3" :class="detail.refund.status === 'PENDING' ? 'border-red-300 dark:border-red-800' : 'border-(--line)'">
            <div class="flex items-center justify-between gap-2">
                <span class="text-[13px] font-semibold">{{ t('bizApplication.refundTitle') }}</span>
                <Tag :value="t(`bizApplication.refundStatus.${detail.refund.status}`)" :severity="detail.refund.status === 'PENDING' ? 'danger' : 'secondary'" />
            </div>
            <p class="m-0 text-[13px] [overflow-wrap:anywhere]">“{{ detail.refund.reason }}” <span class="text-[11px] text-muted-color">· {{ time.shortDateTime(detail.refund.requestedAt) }}</span></p>
            <p class="m-0 text-[13px] tabular-nums">{{ t('bizApplication.refundCalc', { paid: won(detail.refund.paidAmount), used: detail.refund.usedDays, total: detail.refund.totalDays, proposed: won(detail.refund.proposedAmount) }) }}</p>
            <div v-if="detail.refund.status === 'PENDING' && canRefund" class="flex justify-end gap-2">
                <Button type="button" :label="t('bizApplication.actDenyRefund')" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('denyRefund')" />
                <Button type="button" :label="t('bizApplication.actApproveRefund')" size="small" severity="danger" :disabled="busy" @click="emit('approveRefund')" />
            </div>
        </section>

        <!-- 결제 기록(확정 뒤) -->
        <section v-if="detail.onlinePayment || (detail.status !== 'AWAITING_DEPOSIT' && (detail.contracts.length || detail.deposit))" class="flex flex-col gap-2">
            <h3 class="m-0 text-sm font-semibold">{{ t('bizApplication.paymentRecord') }}</h3>
            <p v-if="detail.onlinePayment" class="m-0 text-[13px]">{{ t('bizApplication.paidOnline', { at: time.shortDateTime(detail.onlinePayment.paidAt), tx: detail.onlinePayment.pgTxId }) }}</p>
            <template v-else>
                <BizContractFiles :files="detail.contracts" />
                <p v-if="detail.deposit" class="m-0 text-[13px]">{{ t('bizApplication.depositLine', { amount: won(detail.deposit.amount), date: time.serviceDate(detail.deposit.depositedOn), name: detail.deposit.depositorName, who: detail.deposit.confirmedBy }) }}</p>
            </template>
        </section>

        <section class="flex flex-col gap-2">
            <h3 class="m-0 text-sm font-semibold">{{ t('bizApplication.history') }}</h3>
            <Timeline :value="history" class="text-[13px]" :pt="{ eventOpposite: { class: 'hidden' } }">
                <template #content="{ item }">
                    <div class="flex flex-col pb-3">
                        <span class="font-medium">{{ item.action }} <span class="font-normal text-muted-color">· {{ item.actor }}</span></span>
                        <span class="text-[11px] text-muted-color">{{ time.shortDateTime(item.at) }}</span>
                        <span v-if="item.note" class="text-xs [overflow-wrap:anywhere]">{{ item.note }}</span>
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
    mockPay: [];
    cancel: [];
    upload: [files: File[]];
    replace: [body: { id: string; file: File }];
    saveDeposit: [body: BizDepositRequest];
    confirm: [];
    extend: [];
    approveRefund: [];
    denyRefund: [];
}>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const uid = useId();

/** 점검표 — 부모가 건마다 `:key` 로 다시 만든다 */
const checked = ref<string[]>([]);
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
const history = computed(() => [...props.detail.history].sort((a, b) => b.at.localeCompare(a.at)));
</script>
