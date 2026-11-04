<template>
    <!-- 회원 숫자 — 오늘 가입 · 오늘 탈퇴(완료) · 정지 중 · 탈퇴 대기(예약)(인덱스 COUNT — 휴면 기능이 없어 휴면 칸은 뺐다, 2026-11-03). 두 열 작은 카드(설명은 툴팁). 누르면 회원 관리 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/members" @refresh="state.reload">
        <!-- 두 열 작은 카드(이름 위·숫자 아래, 방문 현황과 같은 모양 — 사용자 결정 2026-11-03), 설명은 툴팁 -->
        <template #skeleton><DashboardStatSkeleton :count="4" :min="8" height="4.5rem" /></template>
        <DashboardStatGrid :cols="2" compact :stats="stats" />
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import UtilTime from '#layers/display-time/utils/util-time';
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
        // 오늘 탈퇴·탈퇴 대기는 탈퇴 관리 화면으로(완료 오늘 = 서비스 하루(한국 시간) 기준 — 요약의 「오늘 0시부터」와 같은 기준)
        { key: 'withdrawn', label: t('dashboard.membersWithdrawn'), count: d.withdrawnToday, note: t('dashboard.membersWithdrawnNote'), to: withdrawnTodayLink.value },
        { key: 'suspended', label: t('dashboard.membersSuspended'), count: d.suspended, note: t('dashboard.membersSuspendedNote'), tone: d.suspended > 0 ? 'warn' : 'normal', to: '/members' },
        { key: 'withdrawing', label: t('dashboard.membersWithdrawing'), count: d.withdrawing, note: t('dashboard.membersWithdrawingNote'), to: '/members/withdrawals?status=SCHEDULED' },
    ];
});

/** 「오늘 탈퇴」 링크 — 탈퇴 관리의 완료 탭을 오늘(서비스 날짜) 완료일로 거른다 */
const withdrawnTodayLink = computed(() => {
    const today = UtilTime.serviceDayOf(state.at.value ?? Date.now());
    return today ? `/members/withdrawals?status=COMPLETED&completedFrom=${today}&completedTo=${today}` : '/members/withdrawals?status=COMPLETED';
});
</script>
