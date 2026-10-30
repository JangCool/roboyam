<template>
    <!--
        입금·계약 확인 대기 건(수동 결제 — ★-2 · F3) — ① 계약서 사본(필수, 여러 장, 옛 파일 「대체됨」) ② 입금 확인(입금액·입금일·입금자명) ③ [예약 확정].
        [예약 확정] 은 계약서 사본 1장 이상 + 저장된 입금 기록이 둘 다 있을 때만 켜진다(서버도 CHECK). 입금액이 계약 금액과 다르면 경고 + 사유.
        입금 기한이 지난 건은 위에 경고와 [기한 연장]·[취소] 를 둔다(자동 취소하지 않는다 — F2). 서버는 부르지 않는다 — 동작은 emit.
    -->
    <div class="flex flex-col gap-4">
        <Message v-if="overdue" severity="error" :closable="false">
            <div class="flex flex-wrap items-center justify-between gap-2 text-[13px]">
                <span>{{ t('bizApplication.depositOverdue', { at: time.shortDateTime(detail.deadlineAt!) }) }}</span>
                <span class="flex gap-2">
                    <Button type="button" :label="t('bizApplication.actExtend')" size="small" severity="secondary" :disabled="busy" @click="emit('extend')" />
                    <Button type="button" :label="t('bizApplication.actCancel')" size="small" severity="danger" variant="outlined" :disabled="busy" @click="emit('cancel')" />
                </span>
            </div>
        </Message>

        <section class="flex flex-col gap-2" :aria-labelledby="`${uid}-c`">
            <h4 :id="`${uid}-c`" class="m-0 text-[13px] font-semibold">① {{ t('bizApplication.contract.title') }} <span class="text-red-600">*</span></h4>
            <BizContractFiles :files="detail.contracts" editable :busy="busy" :error="fieldErrors.file ?? ''" @upload="(f) => emit('upload', f)" @replace="(b) => emit('replace', b)" />
        </section>

        <section class="flex flex-col gap-2" :aria-labelledby="`${uid}-d`">
            <div class="flex items-center justify-between gap-2">
                <h4 :id="`${uid}-d`" class="m-0 text-[13px] font-semibold">② {{ t('bizApplication.deposit.title') }} <span class="text-red-600">*</span></h4>
                <span v-if="detail.deposit" class="text-[11px] text-muted-color">{{ t('bizApplication.deposit.savedBy', { who: detail.deposit.confirmedBy, at: time.shortDateTime(detail.deposit.confirmedAt) }) }}</span>
            </div>
            <BizDepositForm v-model="draft" :contract-total="total" :disabled="busy" :show-errors="tried" :field-errors="fieldErrors" />
            <div class="flex justify-end">
                <Button type="button" :label="detail.deposit ? t('bizApplication.deposit.update') : t('bizApplication.deposit.save')" icon="pi pi-save" size="small" severity="secondary" :loading="busy" @click="save" />
            </div>
        </section>

        <section class="flex flex-col gap-2 rounded-lg border border-(--line) px-3 py-2.5">
            <h4 class="m-0 text-[13px] font-semibold">③ {{ t('bizApplication.confirmTitle') }}</h4>
            <ul class="m-0 flex list-none flex-col gap-1 p-0 text-[13px]">
                <li v-for="c in conditions" :key="c.key" class="flex items-center gap-2" :class="c.ok ? 'text-green-700 dark:text-green-400' : 'text-muted-color'">
                    <i :class="c.ok ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />{{ c.label }}
                </li>
            </ul>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
            <div class="flex justify-end">
                <Button type="button" :label="t('bizApplication.actConfirmBooking')" icon="pi pi-calendar-plus" :disabled="busy || !ready" @click="emit('confirm')" />
            </div>
        </section>
    </div>
</template>

<script setup lang="ts">
import type { BizApplicationDetail, BizDepositRequest } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplication, { type BizDepositDraft } from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    detail: BizApplicationDetail;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
    now?: number;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}), now: () => Date.now() });
const emit = defineEmits<{ upload: [files: File[]]; replace: [body: { id: string; file: File }]; saveDeposit: [body: BizDepositRequest]; confirm: []; extend: []; cancel: [] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const uid = useId();

const total = computed(() => props.detail.approval?.amount.total ?? props.detail.total);
/** 부모가 건마다 `:key` 로 다시 만든다 — 처음 값은 저장된 입금 기록 */
const saved = props.detail.deposit;
const draft = ref<BizDepositDraft>(saved ? { amount: saved.amount, depositedOn: saved.depositedOn, depositorName: saved.depositorName, mismatchReason: saved.mismatchReason ?? '' } : UtilBizApplication.emptyDeposit());
const tried = ref(false);

const overdue = computed(() => Boolean(props.detail.deadlineAt) && Date.parse(props.detail.deadlineAt!) < props.now);
const activeContracts = computed(() => props.detail.contracts.filter((c) => !c.replacedAt).length);
const depositOk = computed(() => {
    const d = props.detail.deposit;
    return Boolean(d) && (d!.amount === total.value || Boolean(d!.mismatchReason));
});
const ready = computed(() => activeContracts.value > 0 && depositOk.value);
const conditions = computed(() => [
    { key: 'c', ok: activeContracts.value > 0, label: t('bizApplication.condContract', { n: activeContracts.value }) },
    { key: 'd', ok: depositOk.value, label: props.detail.deposit ? t('bizApplication.condDepositSaved', { amount: UtilBizApplicationMessages.won(t, props.detail.deposit.amount, locale.value) }) : t('bizApplication.condDeposit') },
]);

function save(): void {
    tried.value = true;
    if (Object.keys(UtilBizApplication.depositProblems(draft.value, total.value)).length) return;
    const d = draft.value;
    emit('saveDeposit', { amount: d.amount ?? 0, depositedOn: d.depositedOn, depositorName: d.depositorName.trim(), mismatchReason: d.amount !== total.value ? d.mismatchReason.trim() : undefined });
}
</script>
