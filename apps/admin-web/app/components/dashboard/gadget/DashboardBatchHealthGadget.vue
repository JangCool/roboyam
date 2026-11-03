<template>
    <!-- 배치 상태 — 최근 24시간 실패한 배치 · 지금 실행 중인 배치. 누르면 배치 로그 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/settings/batch-logs" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3 text-[13px]">
            <section class="flex flex-col gap-1">
                <h3 class="m-0 flex items-center gap-1.5 text-[12px] font-medium" :class="v.failed.length ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                    <i :class="v.failed.length ? 'pi pi-times-circle' : 'pi pi-check-circle'" class="text-xs" aria-hidden="true" />{{ t('dashboard.batchFailed', { n: v.failed.length }) }}
                </h3>
                <ul v-if="v.failed.length" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                    <li v-for="b in v.failed" :key="b.id" class="flex flex-col py-1.5">
                        <span class="flex items-center gap-2">
                            <span class="min-w-0 flex-1 truncate">{{ b.name }}</span>
                            <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(b.failedAt) }}</span>
                        </span>
                        <span class="truncate text-[11px] text-muted-color" :title="b.message">{{ b.message }}</span>
                    </li>
                </ul>
            </section>
            <section class="flex flex-col gap-1">
                <h3 class="m-0 flex items-center gap-1.5 text-[12px] font-medium text-muted-color"><i class="pi pi-spinner text-xs" aria-hidden="true" />{{ t('dashboard.batchRunning', { n: v.running.length }) }}</h3>
                <ul v-if="v.running.length" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                    <li v-for="b in v.running" :key="b.id" class="flex items-center gap-2 py-1.5">
                        <span class="min-w-0 flex-1 truncate">{{ b.name }}</span>
                        <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ t('dashboard.batchStartedAt', { at: time.clock(b.startedAt) }) }}</span>
                    </li>
                </ul>
            </section>
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
const state = useDashboardKindData('BATCH_HEALTH', () => props.gadget);
const v = computed(() => state.data.value);
</script>
