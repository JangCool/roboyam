<template>
    <!--
        환불 합계 한 줄(추가 환불 클레임 — biz-pause-contract 「추가 환불」): 「결제 {paid} · 환불 {refunded} · 남은 환불 가능 {refundable}」 + 아직 돌려주지 않은 중도 해지 수수료(있을 때).
        신청 건 심사 가운데 칸(환불 카드 안 · 환불이 없으면 결제 기록 아래)과 광고 일정표 서랍 결제 탭이 같이 쓴다. 오른쪽 슬롯에 [추가 환불] 단추를 둔다(심사 화면만).
        금액 강조는 심사 가운데 칸 관례(받는 돈 = 주 색 · 끝난 값 = 초록 · 돌려줄 돈 = 빨강)를 따른다. 서버 값 그대로 — 화면이 다시 계산하지 않는다.
    -->
    <div class="flex flex-wrap items-center justify-between gap-2" data-testid="biz-refund-summary">
        <div class="flex min-w-0 flex-col gap-0.5">
            <i18n-t keypath="bizApplication.claim.summary" tag="p" scope="global" class="m-0 text-[13px] tabular-nums [overflow-wrap:anywhere]">
                <template #paid><span :class="EMPH.money">{{ won(summary.paid) }}</span></template>
                <template #refunded><span :class="summary.refunded > 0 ? EMPH.ok : ''">{{ won(summary.refunded) }}</span></template>
                <template #refundable><span :class="summary.refundable > 0 ? EMPH.refund : 'text-muted-color'">{{ won(summary.refundable) }}</span></template>
            </i18n-t>
            <span v-if="summary.feeKept > 0" class="text-[11px] tabular-nums text-muted-color">{{ t('bizApplication.claim.feeKept', { fee: won(summary.feeKept) }) }}</span>
        </div>
        <slot />
    </div>
</template>

<script setup lang="ts">
import type { BizRefundSummary } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

interface Props {
    summary: BizRefundSummary;
}

withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
/** 심사 가운데 칸 `EMPH` 와 같은 조합 — 받는 돈 · 끝난 값 · 돌려줄 돈 */
const EMPH = {
    money: 'font-bold text-(--p-primary-color)',
    ok: 'font-semibold text-green-700 dark:text-green-400',
    refund: 'font-bold text-red-600 dark:text-red-400',
} as const;
</script>
