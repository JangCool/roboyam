<template>
    <!-- 운영자 보안 — 잠긴 운영자 · 2차 인증 미설정 운영자 수(전체 중). 누르면 운영자 관리 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/settings/operators" @refresh="state.reload">
        <template #skeleton><DashboardStatSkeleton :count="2" :min="8" height="4rem" /></template>
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
const state = useDashboardKindData('OPERATOR_SECURITY', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'locked', label: t('dashboard.operatorsLocked'), count: d.locked, icon: 'pi pi-lock', tone: d.locked > 0 ? 'warn' : 'normal', to: '/settings/operators' },
        { key: 'mfa', label: t('dashboard.operatorsMfaMissing', { total: d.total }), count: d.mfaMissing, icon: 'pi pi-shield', tone: d.mfaMissing > 0 ? 'warn' : 'normal', to: '/settings/operators' },
    ];
});
</script>
