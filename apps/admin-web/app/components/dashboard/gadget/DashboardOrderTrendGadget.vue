<template>
    <!-- 결제 추이 — 7일·30일(⚙ 설정) 일별 결제 금액 막대 + 기간 합계 금액·건수(결제 일별 집계만 읽는다). 누르면 결제 주문 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/payments/orders" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-2">
            <p class="m-0 flex flex-wrap items-baseline gap-x-2">
                <span class="text-2xl font-semibold whitespace-nowrap tabular-nums">{{ UtilDashboardMessages.won(t, total.amount, locale) }}</span>
                <span class="text-[12px] text-muted-color">{{ t('dashboard.orderTrendTotal', { days: v.days.length, n: total.count.toLocaleString(locale) }) }}</span>
            </p>
            <DashboardMiniBars :days="amountDays" :summary="t('dashboard.orderTrendSummary', { days: v.days.length })" :height="64" />
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const state = useDashboardKindData('ORDER_TREND', () => props.gadget);
const v = computed(() => state.data.value);
const total = computed(() => (v.value?.days ?? []).reduce((s, d) => ({ amount: s.amount + d.amount, count: s.count + d.count }), { amount: 0, count: 0 }));
const amountDays = computed(() => v.value?.days.map((d) => ({ date: d.date, count: d.amount })) ?? []);
</script>
