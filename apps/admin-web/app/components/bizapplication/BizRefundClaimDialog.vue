<template>
    <!--
        추가 환불(클레임) 대화상자 — biz-pause-contract 「추가 환불」. `BizReasonDialog` 짜임(안내 · 위 칸 · 사유 5~200자 · 아래 칸 · 감사 안내)에 금액 칸을 얹는다.
        위에서 아래로: 안내 · 합계(결제·환불·남은 환불 가능) · 금액(1 ~ 남은 환불 가능, 처음 값 = 남은 전액) + 빠른 채우기 「남은 전액」·「수수료만」(`feeKept` > 0 일 때)
          · 사유(클레임 내용 — 관리자에게만) · 「되돌릴 수 없어요 · 온라인은 카드 부분 취소, 수동은 송금 대기」.
        서버는 부르지 않는다 — `submit` 으로 올리고 저장·토스트는 부모 몫이다. 열 때마다 금액을 남은 전액으로 되돌린다(사유는 `BizReasonDialog` 가 비운다).
    -->
    <BizReasonDialog
        :visible="visible"
        :title="t('bizApplication.claim.dialogTitle', { no })"
        :description="t('bizApplication.claim.dialogBody')"
        :label="t('bizApplication.claim.reasonLabel')"
        :confirm-label="t('bizApplication.claim.act')"
        tone="danger"
        :busy="busy"
        :blocked="amountProblem !== null"
        :error="error"
        :field-error="fieldErrors.reason ?? ''"
        @update:visible="(v: boolean) => emit('update:visible', v)"
        @submit="submit"
    >
        <div class="flex flex-col gap-3 rounded-lg border border-(--line) px-3 py-2.5">
            <BizRefundSummaryLine :summary="summary" />
        </div>

        <LabeledField :label="t('bizApplication.claim.amountLabel')" :help="t('bizApplication.claim.amountHelp', { max: won(summary.refundable) })" :error="amountError">
            <template #default="{ id, describedBy, invalid }">
                <InputNumber v-model="amount" :input-id="id" :min="1" :max="summary.refundable" :suffix="t('bizApplication.wonSuffix')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
            </template>
        </LabeledField>
        <div class="-mt-2 flex flex-wrap items-center gap-2" role="group" :aria-label="t('bizApplication.claim.quickFill')">
            <Button type="button" :label="t('bizApplication.claim.fillAll')" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="amount = summary.refundable" />
            <Button v-if="summary.feeKept > 0" type="button" :label="`${t('bizApplication.claim.fillFee')} · ${won(summary.feeKept)}`" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="amount = summary.feeKept" />
        </div>

        <template #after>
            <p class="m-0 text-xs text-muted-color">{{ t('bizApplication.claim.reasonPrivate') }}</p>
            <Message severity="error" :closable="false"><span class="text-[13px]">{{ t('bizApplication.claim.irreversible') }}</span></Message>
        </template>
    </BizReasonDialog>
</template>

<script setup lang="ts">
import type { BizRefundClaimRequest, BizRefundSummary } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    visible: boolean;
    /** 신청 건 번호(제목) */
    no: string;
    /** 서버 합계 — 금액 상한·처음 값(`refundable`) · 「수수료만」 값(`feeKept`) */
    summary: BizRefundSummary;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 칸에 준 오류(`amount`·`reason`) */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [body: BizRefundClaimRequest] }>();

const { t, locale } = useI18n();
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);

const amount = ref<number | null>(null);

watch(
    () => props.visible,
    (open) => {
        if (open) amount.value = props.summary.refundable;
    },
    { immediate: true },
);

/** 금액 1 ~ 남은 환불 가능(서버가 행 잠금 아래에서 다시 본다 — 400 `fields.amount`) */
const amountProblem = computed(() => (amount.value === null || !Number.isInteger(amount.value) || amount.value < 1 || amount.value > props.summary.refundable ? 'RANGE' : null));
const amountError = computed(() => (amountProblem.value ? t('bizApplication.claim.amountRange', { max: won(props.summary.refundable) }) : (props.fieldErrors.amount ?? '')));

function submit(reason: string): void {
    if (props.busy || amountProblem.value || amount.value === null) return;
    emit('submit', { amount: amount.value, reason });
}
</script>
