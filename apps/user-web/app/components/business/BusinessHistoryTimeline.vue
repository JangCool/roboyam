<template>
    <!-- 처리 기록(`Timeline`) — 최근 것이 위. 누가(나·운영팀·자동) · 무엇을 · 언제 · 사유(서버 문구 그대로) -->
    <Timeline :value="ordered" class="business-timeline" data-testid="business-history">
        <template #marker="{ item }">
            <span class="grid size-6 place-items-center rounded-full text-[0.625rem]" :class="MARKER[(item as BizHistoryEntry).actor]" aria-hidden="true">
                <i :class="ICON[(item as BizHistoryEntry).actor]" />
            </span>
        </template>
        <template #content="{ item }">
            <div class="flex min-w-0 flex-col gap-0.5 pb-3">
                <p class="m-0 text-sm font-medium">{{ t(`business.detail.action.${(item as BizHistoryEntry).action}`) }}</p>
                <p class="m-0 text-xs tabular-nums text-muted-color">{{ t(`business.detail.actor.${(item as BizHistoryEntry).actor}`) }} · {{ time.full((item as BizHistoryEntry).at) }}</p>
                <p v-if="(item as BizHistoryEntry).note" class="m-0 whitespace-pre-line break-words text-xs leading-relaxed">{{ (item as BizHistoryEntry).note }}</p>
            </div>
        </template>
    </Timeline>
</template>

<script setup lang="ts">
import type { BizHistoryEntry } from '~/composables/business/api/useBusinessApi.types';

interface Props {
    history: BizHistoryEntry[];
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();

const ordered = computed(() => [...props.history].reverse());

const MARKER: Record<BizHistoryEntry['actor'], string> = {
    ME: 'bg-primary text-primary-contrast',
    OPERATOR: 'bg-amber-500 text-white',
    SYSTEM: 'bg-surface-300 text-surface-800 dark:bg-surface-600 dark:text-surface-100',
};
const ICON: Record<BizHistoryEntry['actor'], string> = {
    ME: 'pi pi-user',
    OPERATOR: 'pi pi-shield',
    SYSTEM: 'pi pi-cog',
};
</script>

<style scoped>
/* 왼쪽 정렬 한 열(반대편 빈 칸을 없앤다) */
.business-timeline :deep(.p-timeline-event-opposite) {
    display: none;
}
</style>
