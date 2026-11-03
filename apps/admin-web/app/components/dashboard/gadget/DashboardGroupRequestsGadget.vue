<template>
    <!-- 소모임 개설 신청 — 대기 건수 + 최근 N건(이름 · 신청자 · 신청 시각). 누르면 개설 승인 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/group-requests" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <DashboardStatCard :label="t('dashboard.groupRequestsPending')" :count="v.pending" icon="pi pi-users" :tone="v.pending > 0 ? 'warn' : 'normal'" to="/group-requests" compact />
            <p v-if="!v.recent.length" class="m-0 text-sm text-muted-color">{{ t('dashboard.groupRequestsNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                <li v-for="g in v.recent" :key="g.id" class="flex items-center gap-2 py-2 text-[13px]">
                    <span class="min-w-0 flex-1 truncate" :title="g.name">{{ g.name }}</span>
                    <span class="shrink-0 text-[11px] text-muted-color">{{ g.requesterNickname }}</span>
                    <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(g.requestedAt) }}</span>
                </li>
            </ul>
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
const time = useDisplayTime();
const state = useDashboardKindData('GROUP_REQUESTS', () => props.gadget);
const v = computed(() => state.data.value);
</script>
