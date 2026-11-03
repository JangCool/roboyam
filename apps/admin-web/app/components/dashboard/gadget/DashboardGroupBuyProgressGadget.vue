<template>
    <!-- 모집 중 공구 — 마감 임박 차례 N개(달성률 진행 막대 · 마감까지 남은 시간). 누르면 공구 묶음 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-shopping-bag"
        :empty-title="t('dashboard.groupBuyNone')"
        more="/group-buys?status=RECRUITING"
        @refresh="state.reload"
    >
        <ul v-if="v" class="m-0 flex list-none flex-col gap-3 p-0">
            <li v-for="g in v.items" :key="g.id" class="flex flex-col gap-1 text-[13px]">
                <span class="flex items-center gap-2">
                    <span class="min-w-0 flex-1 truncate" :title="g.title">{{ g.title }}</span>
                    <span class="shrink-0 text-[11px] tabular-nums" :class="UtilDashboardMessages.urgent(g.endsAt) ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ UtilDashboardMessages.remaining(t, g.endsAt) }}</span>
                </span>
                <span class="flex items-center gap-2">
                    <ProgressBar :value="Math.min(100, rate(g))" :show-value="false" class="h-1.5! flex-1" :aria-label="t('dashboard.groupBuyRate', { title: g.title, rate: rate(g) })" />
                    <span class="w-24 shrink-0 text-right text-[11px] text-muted-color tabular-nums">{{ t('dashboard.groupBuyJoined', { joined: g.joined, goal: g.goal, rate: rate(g) }) }}</span>
                </span>
            </li>
        </ul>
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

const { t } = useI18n();
const state = useDashboardKindData('GROUPBUY_PROGRESS', () => props.gadget);
const v = computed(() => state.data.value);

/** 달성률(%, 100 을 넘어도 막대는 100) */
function rate(g: { joined: number; goal: number }): number {
    return g.goal > 0 ? Math.round((g.joined / g.goal) * 100) : 0;
}
</script>
