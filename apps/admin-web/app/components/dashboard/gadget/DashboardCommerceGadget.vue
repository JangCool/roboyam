<template>
    <!--
        커머스(commerce.md 19절, 계약 commerce-groupbuy 「화면 목록」): 준비 대기 · 열린 요청(기한 경고) · 결제 이상 · 곧 만료될 선점 · 남은 수량 적은 공구
        · 발송 예정일 지난 발송 전 주문 · 발송 뒤 N일 지난 SHIPPED(배송완료 처리 누락 — 청약철회 기한이 시작되지 않는다). 숫자 + 해당 화면 링크.
        숫자는 서버가 그 운영자의 권한 안에서 센다 — 볼 권한이 없는 칸은 `null` 이라 그리지 않는다(칸이 하나도 없으면 빈 화면 안내).
        서버 호출은 `useCommerceDashboardApi`. (대시보드가 가젯형이 되며 페이지에서 옮겼다 — 규칙은 그대로)
    -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="state.data.value !== null"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!cards.length"
        empty-icon="pi pi-chart-bar"
        :empty-title="t('dashboard.emptyTitle')"
        :empty-body="t('dashboard.emptyBody')"
        @refresh="state.reload"
    >
        <!-- 한 줄 행 두 열(사용자 결정 2026-11-03 「영역이 너무 크고 넓다」) — 설명은 마우스를 올리면 보인다 -->
        <template #skeleton><DashboardStatSkeleton :count="7" :min="13" height="2.125rem" /></template>
        <DashboardStatGrid :stats="cards" :min="13" row />
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardStat } from '~/components/dashboard/DashboardStatCard.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const api = useCommerceDashboardApi();

const state = useDashboardGadgetData(
    async () => {
        const view = await api.summary();
        return { generatedAt: view.generatedAt, data: view };
    },
    { refresh: () => props.gadget.config.refresh ?? 0 },
);

const cards = computed<DashboardStat[]>(() => {
    const v = state.data.value;
    if (!v) return [];
    const out: DashboardStat[] = [];
    if (v.preparingWaiting !== null) out.push({ key: 'preparing', label: t('dashboard.preparing'), count: v.preparingWaiting, note: t('dashboard.preparingNote'), to: '/group-buy-orders?status=CONFIRMED', icon: 'pi pi-box', tone: 'normal' });
    if (v.openRequests !== null) out.push({ key: 'requests', label: t('dashboard.openRequests'), count: v.openRequests.total, note: t('dashboard.openRequestsNote', { warn: v.openRequests.warn, critical: v.openRequests.critical }), to: v.openRequests.warn + v.openRequests.critical > 0 ? '/group-buy-requests?status=ALL&level=WARN' : '/group-buy-requests', icon: 'pi pi-replay', tone: v.openRequests.critical > 0 ? 'danger' : v.openRequests.warn > 0 ? 'warn' : 'normal' });
    if (v.paymentAnomalies !== null) out.push({ key: 'anomalies', label: t('dashboard.paymentAnomalies'), count: v.paymentAnomalies, note: '', to: '/payments/anomalies', icon: 'pi pi-exclamation-triangle', tone: v.paymentAnomalies > 0 ? 'danger' : 'normal' });
    if (v.expiringHolds !== null) out.push({ key: 'holds', label: t('dashboard.expiringHolds'), count: v.expiringHolds, note: t('dashboard.expiringHoldsNote'), to: '/group-buy-stock', icon: 'pi pi-clock', tone: 'normal' });
    if (v.lowStockGroupBuys !== null) out.push({ key: 'lowStock', label: t('dashboard.lowStock'), count: v.lowStockGroupBuys, note: t('dashboard.lowStockNote'), to: '/group-buys?status=RECRUITING', icon: 'pi pi-chart-bar', tone: v.lowStockGroupBuys > 0 ? 'warn' : 'normal' });
    if (v.shipOverdue !== null) out.push({ key: 'overdue', label: t('dashboard.shipOverdue'), count: v.shipOverdue, note: t('dashboard.shipOverdueNote'), to: '/group-buy-orders?alert=SHIP_OVERDUE', icon: 'pi pi-calendar-times', tone: v.shipOverdue > 0 ? 'danger' : 'normal' });
    if (v.deliveryStale !== null) out.push({ key: 'stale', label: t('dashboard.deliveryStale', { days: v.deliveryStale.days }), count: v.deliveryStale.count, note: t('dashboard.deliveryStaleNote'), to: '/group-buy-orders?alert=DELIVERY_STALE', icon: 'pi pi-truck', tone: v.deliveryStale.count > 0 ? 'warn' : 'normal' });
    return out;
});
</script>
