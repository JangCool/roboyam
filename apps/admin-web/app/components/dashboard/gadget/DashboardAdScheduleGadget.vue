<template>
    <!-- 이번 주 광고 일정 — 시작·종료 예정 광고(시각 차례, 최대 8). 누르면 광고 일정·등록 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-calendar"
        :empty-title="t('dashboard.adScheduleNone')"
        more="/ads"
        @refresh="state.reload"
    >
        <ul v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="a in v.items" :key="a.id" class="flex items-center gap-2 py-2 text-[13px]">
                <span
                    class="inline-flex shrink-0 items-center justify-center rounded px-1.5 py-1 text-[11px] leading-none"
                    :class="a.kind === 'START' ? 'bg-primary-50 text-primary dark:bg-primary-950' : 'bg-surface-100 text-muted-color dark:bg-surface-800'"
                >
                    {{ a.kind === 'START' ? t('dashboard.adStart') : t('dashboard.adEnd') }}
                </span>
                <span class="flex min-w-0 flex-1 flex-col">
                    <span class="truncate" :title="a.title">{{ a.title }}</span>
                    <span class="truncate text-[11px] text-muted-color">{{ adType(a.placementName) }}</span>
                </span>
                <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.shortDateTime(a.at) }}</span>
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

const { t, te } = useI18n();
const time = useDisplayTime();
const state = useDashboardKindData('AD_SCHEDULE', () => props.gadget);
const v = computed(() => state.data.value);

/** 광고 유형 코드 → 이름(`bizApplication.adType.*`) — 모르는 코드는 코드 그대로 */
function adType(code: string): string {
    const key = `bizApplication.adType.${code}`;
    return te(key) ? t(key) : code;
}
</script>
