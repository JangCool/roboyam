<template>
    <!-- 최근 운영자 활동 N건(누가 · 무엇 · 언제) — 감사 로그 최근 행. 누르면 감사 로그 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-history"
        :empty-title="t('dashboard.auditNone')"
        more="/settings/audit"
        @refresh="state.reload"
    >
        <ul v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="a in v.items" :key="a.id" class="flex items-center gap-2 py-2 text-[13px]">
                <span class="flex min-w-0 flex-1 flex-col">
                    <span class="truncate" :title="a.summary">{{ a.summary }}</span>
                    <span class="truncate text-[11px] text-muted-color">{{ a.actorNickname }}</span>
                </span>
                <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(a.at) }}</span>
            </li>
        </ul>
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
const state = useDashboardKindData('AUDIT_RECENT', () => props.gadget);
const v = computed(() => state.data.value);
</script>
