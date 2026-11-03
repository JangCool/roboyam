<template>
    <!--
        제재 — 진행 중 · 이의 신청 대기를 한 줄 행 두 열로(신고와 같은 모양) + 이의 신청 오래된 차례 3건(닉네임 · 제재 · 신청 시각).
        제재 전용 화면이 아직 없어(권한 `sanction.read` 는 카탈로그에 「기능 준비 중」) 회원 관리로 간다.
    -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/members" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <!-- 건수는 한 줄 행 두 열(사용자 결정 2026-11-03 — 신고와 같은 모양, 「오늘 끝나는 제재」는 처리할 일이 아니라 뺐다) -->
            <DashboardStatGrid :cols="2" row :stats="stats" />
            <p v-if="!v.appeals.length" class="m-0 text-sm text-muted-color">{{ t('dashboard.appealsNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-label="t('dashboard.appealsOldest')">
                <li v-for="a in v.appeals" :key="a.id" class="flex items-center gap-2 py-2 text-[13px]">
                    <span class="flex min-w-0 flex-1 flex-col">
                        <span class="truncate">{{ a.nickname }}</span>
                        <span class="truncate text-[11px] text-muted-color">{{ a.kind }}</span>
                    </span>
                    <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(a.appealedAt) }}</span>
                </li>
            </ul>
        </div>
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
const state = useDashboardKindData('SANCTIONS', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'active', label: t('dashboard.sanctionsActive'), count: d.active, to: '/members' },
        { key: 'appeals', label: t('dashboard.sanctionsAppeals'), count: d.appealsPending, tone: d.appealsPending > 0 ? 'warn' : 'normal', to: '/members' },
    ];
});
</script>
