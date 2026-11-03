<template>
    <!-- 신고 — 대기·검토 중 건수(상태별 COUNT 한 번) + 오래된 대기 N건(사유 · 대상 종류 · 접수 시각, 자동 숨김 표시). 누르면 신고 관리 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/reports" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <!-- 건수는 작은 한 줄 행 두 열(사용자 결정 2026-11-03 「크기를 더 줄여 2단으로」) -->
            <DashboardStatGrid :cols="2" row :stats="stats" />
            <p v-if="!v.oldest.length" class="m-0 text-sm text-muted-color">{{ t('dashboard.reportsNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0" :aria-label="t('dashboard.reportsOldest')">
                <li v-for="r in v.oldest" :key="r.id" class="flex items-center gap-2 py-2 text-[13px]">
                    <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ t(`report.targetType.${r.targetType}`) }}</span>
                    <span class="min-w-0 flex-1 truncate">{{ t(`board.reason.${r.reason}`) }}</span>
                    <span v-if="r.autoHidden" class="shrink-0 text-[11px] text-amber-700 dark:text-amber-400">{{ t('dashboard.reportsAutoHidden') }}</span>
                    <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(r.reportedAt) }}</span>
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
const state = useDashboardKindData('REPORTS', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'pending', label: t('dashboard.reportsPending'), count: d.pending, tone: d.pending > 0 ? 'warn' : 'normal', to: '/reports' },
        { key: 'reviewing', label: t('dashboard.reportsReviewing'), count: d.reviewing, to: '/reports' },
    ];
});
</script>
