<template>
    <div class="mx-auto flex max-w-3xl flex-col gap-4" data-testid="my-orders">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h1 class="m-0 text-lg font-bold">{{ t('commerce.ordersTitle') }}</h1>
            <Button as="router-link" :to="UtilGroupBuy.LIST_PATH" :label="term('GROUP_BUY')" icon="pi pi-shopping-bag" size="small" severity="secondary" variant="outlined" />
        </div>

        <div v-if="status === 'loading'" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')" data-testid="my-orders-loading">
            <div v-for="n in 4" :key="n" class="h-24 animate-pulse rounded-xl bg-surface-200 dark:bg-surface-800" />
        </div>
        <ErrorState v-else-if="status === 'error'" :title="t('groupBuy.ordersFailed')" :description="loadError" retry data-testid="my-orders-error" @retry="load" />
        <EmptyState v-else-if="items.length === 0" :title="t('groupBuy.ordersEmpty')" :description="t('groupBuy.ordersEmptyText')" icon="pi pi-receipt" data-testid="my-orders-empty">
            <Button as="router-link" :to="UtilGroupBuy.LIST_PATH" :label="t('groupBuy.browse')" />
        </EmptyState>
        <template v-else>
            <ul class="m-0 flex list-none flex-col gap-2 p-0" data-testid="my-orders-items">
                <li v-for="order in items" :key="order.orderNo">
                    <NuxtLink
                        :to="UtilGroupBuy.myOrderPath(order.orderNo)"
                        class="flex gap-3 rounded-xl border border-surface bg-surface-0 p-3 hover:border-primary focus-visible:outline-2 focus-visible:outline-primary dark:bg-surface-900"
                        data-testid="my-orders-item"
                    >
                        <GroupBuyThumb :src="order.thumbnailUrl" :alt="order.title" size="sm" />
                        <div class="flex min-w-0 flex-1 flex-col gap-1">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <PointShopTag :tone="statusTone(order.status)">{{ t(`groupBuy.orderStatus.${order.status}`) }}</PointShopTag>
                                <PointShopTag v-if="order.attention" tone="warn">{{ t(`groupBuy.attention.${order.attention}`) }}</PointShopTag>
                                <span class="text-xs tabular-nums text-muted-color">{{ time.listLabel(order.createdAt) }} · {{ order.orderNo }}</span>
                            </div>
                            <p class="m-0 truncate text-sm font-semibold">{{ order.title }}</p>
                            <p v-if="UtilGroupBuyMessages.itemsLabel(t, order)" class="m-0 truncate text-xs text-muted-color">{{ UtilGroupBuyMessages.itemsLabel(t, order) }}</p>
                            <p class="m-0 text-sm tabular-nums">
                                <span class="font-semibold">{{ UtilGroupBuyMessages.won(t, order.amount) }}</span>
                                <span v-if="order.refundedAmount > 0" class="ml-1.5 text-xs text-muted-color">{{ t('groupBuy.refundedSum', { amount: UtilGroupBuyMessages.won(t, order.refundedAmount) }) }}</span>
                            </p>
                        </div>
                        <i class="pi pi-angle-right self-center text-muted-color" aria-hidden="true" />
                    </NuxtLink>
                </li>
            </ul>
            <div v-if="items.length < total" class="flex justify-center">
                <Button type="button" :label="t('common.more')" severity="secondary" variant="outlined" :loading="moreBusy" @click="loadMore" />
            </div>
            <Message v-if="moreError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ moreError }}</Message>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyOrderStatus, MyGroupBuyOrderSummary } from '~/composables/groupbuy/api/useGroupBuyOrderApi.types';
import UtilGroupBuy from '~/utils/util-group-buy';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

/**
 * 내 주문 `/me/orders` — 로그인 필요. 공구 주문을 최근 것부터(주문 번호·상태·금액·환불 누계·할 일 표시 — 결제 대기 · 공급 불가 답 · 지연 공지 · 반품비 결제).
 * 계약 commerce-groupbuy 화면 목록. 햄버거 메뉴 「주문 내역」(`UtilCommerce.ORDERS_PATH`)이 여기로 온다.
 */
definePageMeta({ middleware: 'auth' });

const { t } = useI18n();
const { term } = useSiteTerms();
const time = useDisplayTime();
const api = useGroupBuyOrderApi();
useSeoMeta({ title: () => t('commerce.ordersTitle'), robots: 'noindex' });

const status = ref<'loading' | 'ready' | 'error'>('loading');
const loadError = ref('');
const items = ref<MyGroupBuyOrderSummary[]>([]);
const total = ref(0);
const page = ref(0);
const moreBusy = ref(false);
const moreError = ref('');

async function load(): Promise<void> {
    status.value = 'loading';
    loadError.value = '';
    try {
        const res = await api.list(0);
        items.value = res.items;
        total.value = res.total;
        page.value = 0;
        status.value = 'ready';
    } catch (e) {
        loadError.value = UtilGroupBuyMessages.error(t, e);
        status.value = 'error';
    }
}

async function loadMore(): Promise<void> {
    if (moreBusy.value) return;
    moreBusy.value = true;
    moreError.value = '';
    try {
        const res = await api.list(page.value + 1);
        items.value = [...items.value, ...res.items];
        total.value = res.total;
        page.value = res.page;
    } catch (e) {
        moreError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        moreBusy.value = false;
    }
}

void load();

function statusTone(s: GroupBuyOrderStatus): 'neutral' | 'primary' | 'success' | 'warn' | 'danger' {
    if (s === 'PENDING_PAYMENT') return 'warn';
    if (s === 'DELIVERED') return 'success';
    if (s === 'CANCELED' || s === 'REFUNDED' || s === 'EXPIRED') return 'neutral';
    return 'primary';
}
</script>
