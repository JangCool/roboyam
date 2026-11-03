<template>
    <!-- 결제 대사 — 최근 대사일 상태(일치·불일치 건·대기) + 최근 24시간 웹훅 실패. 한 줄 행(대사 날짜·설명은 툴팁 — 사용자 지적 2026-11-03 「항목에 비해 영역이 너무 크다」). 누르면 결제 대사 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/payments/reconciliation" @refresh="state.reload">
        <template #skeleton><DashboardStatSkeleton :count="2" :min="13" height="2.125rem" /></template>
        <DashboardStatGrid :min="13" row :stats="stats" />
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
const time = useDisplayTime();
const state = useDashboardKindData('RECONCILE', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    const mismatch = d.status === 'MISMATCHED';
    return [
        {
            key: 'reconcile',
            label: t('dashboard.reconcileLabel'),
            count: mismatch ? t('dashboard.reconcileMismatched', { n: d.mismatched }) : t(`dashboard.reconcileStatus.${d.status}`),
            note: d.lastDate ? t('dashboard.reconcileDate', { date: time.value.serviceDate(d.lastDate) }) : t('dashboard.reconcileNever'),
            icon: mismatch ? 'pi pi-times-circle' : 'pi pi-check-circle',
            tone: mismatch ? 'danger' : 'normal',
            to: '/payments/reconciliation',
        },
        {
            key: 'webhook',
            label: t('dashboard.webhookFailures'),
            count: d.webhookFailures,
            note: t('dashboard.webhookFailuresNote'),
            icon: 'pi pi-bolt',
            tone: d.webhookFailures > 0 ? 'danger' : 'normal',
            to: '/payments/reconciliation',
        },
    ];
});
</script>
