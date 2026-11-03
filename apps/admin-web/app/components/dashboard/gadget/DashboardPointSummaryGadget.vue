<template>
    <!-- 포인트 요약 — 오늘 지급·사용 · 7일 안 만료 예정 + 환불 승인 대기(승인 권한 `point.refund.approve` 이 있을 때만 — 서버가 없으면 null). 한 줄 행(처리할 일과 같은 모양 — 사용자 지적 2026-11-03 「항목에 비해 영역이 너무 크다」). 누르면 포인트 내역 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/points" @refresh="state.reload">
        <template #skeleton><DashboardStatSkeleton :count="4" :min="13" height="2.125rem" /></template>
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
const { can } = useAdminPermission();
const state = useDashboardKindData('POINT_SUMMARY', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    const p = (n: number) => t('dashboard.points', { n: UtilDashboardMessages.compact(n, locale.value) });
    const out: DashboardStat[] = [
        { key: 'granted', label: t('dashboard.pointsGranted'), count: p(d.grantedToday), icon: 'pi pi-plus', to: '/points' },
        { key: 'used', label: t('dashboard.pointsUsed'), count: p(d.usedToday), icon: 'pi pi-minus', to: '/points' },
        { key: 'expiring', label: t('dashboard.pointsExpiring'), count: p(d.expiringSoon), icon: 'pi pi-hourglass', to: '/points' },
    ];
    // 승인 권한이 있고 서버가 숫자를 줬을 때만(감추는 것은 편의 — 서버가 null 로 막는다)
    if (d.refundPending !== null && can('point.refund.approve')) {
        out.push({ key: 'refund', label: t('dashboard.pointsRefundPending'), count: d.refundPending, icon: 'pi pi-replay', tone: d.refundPending > 0 ? 'warn' : 'normal', to: '/points' });
    }
    return out;
});
</script>
