<template>
    <!--
        매출 요약 — 한 줄 행(처리할 일과 같은 모양): 오늘 결제(라벨에 건수) · 오늘 환불 · 어제 결제 · 이번 달 결제 · 이번 달 환불 · 지난달 같은 기간 결제.
        금액은 오른쪽에 한 줄(줄바꿈 없음), 라벨이 넘치면 말줄임. 어제·지난달 대비 문구는 툴팁. 결제 집계 일별 값을 읽는다. 누르면 결제 주문
    -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/payments/orders" @refresh="state.reload">
        <template #skeleton><DashboardStatSkeleton :count="6" :min="13" height="2.125rem" /></template>
        <DashboardStatGrid :min="13" row :stats="stats" />
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardStat } from '~/components/dashboard/DashboardStatCard.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const state = useDashboardKindData('SALES_SUMMARY', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    const lang = locale.value;
    const won = (n: number) => UtilDashboardMessages.won(t, n, lang);
    const to = '/payments/orders';
    return [
        { key: 'today', label: t('dashboard.salesToday', { n: d.today.paidCount }), count: won(d.today.paid), note: UtilDashboardMessages.delta(t, d.today.paid, d.yesterday.paid, 'yesterday', lang, won), icon: 'pi pi-wallet', to },
        { key: 'todayRefund', label: t('dashboard.salesTodayRefund', { n: d.today.refundedCount }), count: won(d.today.refunded), note: UtilDashboardMessages.delta(t, d.today.refunded, d.yesterday.refunded, 'yesterday', lang, won), icon: 'pi pi-replay', to },
        { key: 'yesterday', label: t('dashboard.salesYesterday', { n: d.yesterday.paidCount }), count: won(d.yesterday.paid), note: t('dashboard.salesRefundNote', { amount: won(d.yesterday.refunded), n: d.yesterday.refundedCount }), icon: 'pi pi-history', to },
        { key: 'month', label: t('dashboard.salesMonth', { n: d.month.paidCount }), count: won(d.month.paid), note: UtilDashboardMessages.delta(t, d.month.paid, d.lastMonth.paid, 'lastMonth', lang, won), icon: 'pi pi-calendar', to },
        { key: 'monthRefund', label: t('dashboard.salesMonthRefund', { n: d.month.refundedCount }), count: won(d.month.refunded), note: UtilDashboardMessages.delta(t, d.month.refunded, d.lastMonth.refunded, 'lastMonth', lang, won), icon: 'pi pi-replay', to },
        { key: 'lastMonth', label: t('dashboard.salesLastMonth', { n: d.lastMonth.paidCount }), count: won(d.lastMonth.paid), note: t('dashboard.salesRefundNote', { amount: won(d.lastMonth.refunded), n: d.lastMonth.refundedCount }), icon: 'pi pi-calendar-minus', to },
    ];
});
</script>
