<template>
    <!-- 핫딜 현황 — 진행 중 · 오늘 등록 · 오늘 종료 · 확인 필요(`FLAGGED`)(상태별 COUNT 한 번). 누르면 핫딜 글 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/hotdeals" @refresh="state.reload">
        <template #skeleton><DashboardStatSkeleton :min="8" height="4rem" /></template>
        <DashboardStatGrid :min="8" compact :stats="stats" />
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
const state = useDashboardKindData('HOTDEAL_STATUS', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'running', label: t('dashboard.hotdealRunning'), count: d.running, icon: 'pi pi-ticket', to: '/hotdeals' },
        { key: 'registered', label: t('dashboard.hotdealRegisteredToday'), count: d.registeredToday, icon: 'pi pi-plus-circle', to: '/hotdeals' },
        { key: 'ending', label: t('dashboard.hotdealEndingToday'), count: d.endingToday, icon: 'pi pi-clock', to: '/hotdeals' },
        { key: 'flagged', label: t('dashboard.hotdealFlagged'), count: d.flagged, icon: 'pi pi-flag', tone: d.flagged > 0 ? 'warn' : 'normal', to: '/hotdeals?view=FLAGGED' },
    ];
});
</script>
