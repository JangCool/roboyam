<template>
    <!--
        대시보드 — 지금은 커머스 칸 하나(commerce.md 19절, 계약 commerce-groupbuy 「화면 목록」): 준비 대기 · 열린 요청(기한 경고) · 결제 이상 · 곧 만료될 선점 · 남은 수량 적은 공구
        · 발송 예정일 지난 발송 전 주문 · 발송 뒤 N일 지난 SHIPPED(배송완료 처리 누락 — 청약철회 기한이 시작되지 않는다). 숫자 + 해당 화면 링크.
        숫자는 서버가 그 운영자의 권한 안에서 센다 — 볼 권한이 없는 칸은 `null` 이라 그리지 않는다(칸이 하나도 없으면 빈 화면 안내). 「답변 안 한 Q&A」는 2b-6 에서 더한다.
        서버 호출은 `useCommerceDashboardApi`(지금 목업 — `?mock=dashboard-fail`·`dashboard-empty`·`dashboard-partial`).
    -->
    <section class="flex flex-col gap-4">
        <PageTitle :title="t('dashboard.title')" />

        <AppGadget :title="t('dashboard.commerceTitle')">
            <template #actions>
                <span v-if="view" class="text-[11px] text-muted-color">{{ t('dashboard.generatedAt', { at: time.clock(view.generatedAt) }) }}</span>
                <Button type="button" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :aria-label="t('dashboard.refresh')" :loading="loading" @click="load" />
            </template>

            <ErrorState v-if="error && !view" :title="t('dashboard.loadFailed')" :description="error.message" retry :retrying="loading" @retry="load" />
            <div v-else-if="!view" class="grid grid-cols-[repeat(auto-fill,minmax(min(100%,13rem),1fr))] gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 7" :key="n" height="5.5rem" border-radius="0.75rem" />
            </div>
            <EmptyState v-else-if="!cards.length" icon="pi pi-chart-bar" :title="t('dashboard.emptyTitle')" :description="t('dashboard.emptyBody')" />
            <ul v-else class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(min(100%,13rem),1fr))] gap-3 p-0">
                <li v-for="card in cards" :key="card.key">
                    <NuxtLink :to="card.to" class="flex h-full flex-col gap-1 rounded-xl border px-4 py-3 transition-colors hover:bg-surface-50 dark:hover:bg-surface-900" :class="card.tone === 'danger' ? 'border-red-300 dark:border-red-800' : card.tone === 'warn' ? 'border-amber-300 dark:border-amber-800' : 'border-(--line)'">
                        <span class="flex items-center gap-2 text-[13px] text-muted-color"><i :class="card.icon" class="text-xs" aria-hidden="true" />{{ card.label }}</span>
                        <span class="text-2xl font-semibold tabular-nums" :class="card.count > 0 && card.tone === 'danger' ? 'text-red-600 dark:text-red-400' : card.count > 0 && card.tone === 'warn' ? 'text-amber-700 dark:text-amber-400' : ''">{{ card.count }}</span>
                        <span v-if="card.note" class="text-[11px] text-muted-color">{{ card.note }}</span>
                    </NuxtLink>
                </li>
            </ul>
        </AppGadget>
    </section>
</template>

<script setup lang="ts">
import type { CommerceDashboardView } from '~/composables/commerce/api/useCommerceDashboardApi.types';
import type { ApiError } from '~/composables/common/useApiClient';

interface DashboardCard {
    key: string;
    label: string;
    count: number;
    note: string;
    to: string;
    icon: string;
    tone: 'normal' | 'warn' | 'danger';
}

const { t } = useI18n();
useSeoMeta({ title: () => t('dashboard.title') });

const api = useCommerceDashboardApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();

const view = ref<CommerceDashboardView | null>(null);
const loading = ref(false);
const error = ref<ApiError | null>(null);

/** 커머스 칸을 볼 권한이 하나라도 있나 — 없으면 부르지 않는다(403 은 오류가 아니다) */
const anyCommerce = computed(() => ['commerce.order.read', 'commerce.product.read', 'commerce.payment.read'].some(can));

const cards = computed<DashboardCard[]>(() => {
    const v = view.value;
    if (!v) return [];
    const out: DashboardCard[] = [];
    if (v.preparingWaiting !== null) out.push({ key: 'preparing', label: t('dashboard.preparing'), count: v.preparingWaiting, note: t('dashboard.preparingNote'), to: '/group-buy-orders?status=CONFIRMED', icon: 'pi pi-box', tone: 'normal' });
    if (v.openRequests !== null) out.push({ key: 'requests', label: t('dashboard.openRequests'), count: v.openRequests.total, note: t('dashboard.openRequestsNote', { warn: v.openRequests.warn, critical: v.openRequests.critical }), to: v.openRequests.warn + v.openRequests.critical > 0 ? '/group-buy-requests?status=ALL&level=WARN' : '/group-buy-requests', icon: 'pi pi-replay', tone: v.openRequests.critical > 0 ? 'danger' : v.openRequests.warn > 0 ? 'warn' : 'normal' });
    if (v.paymentAnomalies !== null) out.push({ key: 'anomalies', label: t('dashboard.paymentAnomalies'), count: v.paymentAnomalies, note: '', to: '/payments/anomalies', icon: 'pi pi-exclamation-triangle', tone: v.paymentAnomalies > 0 ? 'danger' : 'normal' });
    if (v.expiringHolds !== null) out.push({ key: 'holds', label: t('dashboard.expiringHolds'), count: v.expiringHolds, note: t('dashboard.expiringHoldsNote'), to: '/group-buy-stock', icon: 'pi pi-clock', tone: 'normal' });
    if (v.lowStockGroupBuys !== null) out.push({ key: 'lowStock', label: t('dashboard.lowStock'), count: v.lowStockGroupBuys, note: t('dashboard.lowStockNote'), to: '/group-buys?status=RECRUITING', icon: 'pi pi-chart-bar', tone: v.lowStockGroupBuys > 0 ? 'warn' : 'normal' });
    if (v.shipOverdue !== null) out.push({ key: 'overdue', label: t('dashboard.shipOverdue'), count: v.shipOverdue, note: t('dashboard.shipOverdueNote'), to: '/group-buy-orders?alert=SHIP_OVERDUE', icon: 'pi pi-calendar-times', tone: v.shipOverdue > 0 ? 'danger' : 'normal' });
    if (v.deliveryStale !== null) out.push({ key: 'stale', label: t('dashboard.deliveryStale', { days: v.deliveryStale.days }), count: v.deliveryStale.count, note: t('dashboard.deliveryStaleNote'), to: '/group-buy-orders?alert=DELIVERY_STALE', icon: 'pi pi-truck', tone: v.deliveryStale.count > 0 ? 'warn' : 'normal' });
    return out;
});

async function load(): Promise<void> {
    if (!anyCommerce.value) {
        view.value = { preparingWaiting: null, openRequests: null, paymentAnomalies: null, expiringHolds: null, lowStockGroupBuys: null, shipOverdue: null, deliveryStale: null, generatedAt: new Date().toISOString() };
        return;
    }
    loading.value = true;
    error.value = null;
    try {
        view.value = await api.summary();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.status === 403) view.value = { preparingWaiting: null, openRequests: null, paymentAnomalies: null, expiringHolds: null, lowStockGroupBuys: null, shipOverdue: null, deliveryStale: null, generatedAt: new Date().toISOString() };
        else error.value = failure;
    } finally {
        loading.value = false;
    }
}

onMounted(load);
</script>
