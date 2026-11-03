<template>
    <!-- 회원 숫자 — 오늘 가입 · 오늘 탈퇴 · 휴면 예정(7일 안) · 정지 중(상태별 COUNT 한 번). 두 열 작은 카드(설명은 툴팁). 누르면 회원 관리 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/members" @refresh="state.reload">
        <!-- 두 열 작은 카드(이름 위·숫자 아래, 방문 현황과 같은 모양 — 사용자 결정 2026-11-03), 설명은 툴팁 -->
        <template #skeleton><DashboardStatSkeleton :count="4" :min="8" height="4.5rem" /></template>
        <DashboardStatGrid :cols="2" compact :stats="stats" />
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
const state = useDashboardKindData('MEMBER_STATS', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'joined', label: t('dashboard.membersJoined'), count: d.joinedToday, note: t('dashboard.membersJoinedNote'), to: '/members' },
        { key: 'withdrawn', label: t('dashboard.membersWithdrawn'), count: d.withdrawnToday, note: t('dashboard.membersWithdrawnNote'), to: '/members' },
        { key: 'dormant', label: t('dashboard.membersDormantSoon'), count: d.dormantSoon, note: t('dashboard.membersDormantSoonNote'), to: '/members' },
        { key: 'suspended', label: t('dashboard.membersSuspended'), count: d.suspended, note: t('dashboard.membersSuspendedNote'), tone: d.suspended > 0 ? 'warn' : 'normal', to: '/members' },
    ];
});
</script>
