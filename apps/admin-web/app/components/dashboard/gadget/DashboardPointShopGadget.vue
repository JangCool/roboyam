<template>
    <!-- 포인트 상점 — 오늘 구매 수 + 남은 수량 적은 품목. 누르면 구매 내역 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/point-shop/purchases" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <DashboardStatCard :label="t('dashboard.pointShopPurchases')" :count="v.purchasesToday" icon="pi pi-gift" to="/point-shop/purchases" compact />
            <p v-if="!v.lowStock.length" class="m-0 text-sm text-muted-color">{{ t('dashboard.pointShopStockOk') }}</p>
            <template v-else>
                <p class="m-0 text-[12px] text-muted-color">{{ t('dashboard.pointShopLowStock') }}</p>
                <ul class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                    <li v-for="it in v.lowStock" :key="it.id" class="flex items-center gap-2 py-2 text-[13px]">
                        <span class="min-w-0 flex-1 truncate" :title="localizedName(it.name, it.nameEn)">{{ localizedName(it.name, it.nameEn) }}</span>
                        <span class="shrink-0 text-[11px] tabular-nums" :class="it.stock <= 1 ? 'text-red-600 dark:text-red-400' : 'text-amber-700 dark:text-amber-400'">{{ t('dashboard.pointShopStock', { n: it.stock }) }}</span>
                    </li>
                </ul>
            </template>
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const state = useDashboardKindData('POINTSHOP', () => props.gadget);
const v = computed(() => state.data.value);
</script>
