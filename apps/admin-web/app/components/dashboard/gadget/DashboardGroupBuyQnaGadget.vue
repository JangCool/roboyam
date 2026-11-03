<template>
    <!-- 답변 대기 공구 Q&A — 건수 + 오래된 차례 N건(공구 · 질문 · 물은 시각). 누르면 공구 Q&A -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/group-buy-qna" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <DashboardStatCard :label="t('dashboard.qnaPending')" :count="v.pending" icon="pi pi-question-circle" :tone="v.pending > 0 ? 'warn' : 'normal'" to="/group-buy-qna" compact />
            <p v-if="!v.oldest.length" class="m-0 text-sm text-muted-color">{{ t('dashboard.qnaNone') }}</p>
            <ul v-else class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
                <li v-for="q in v.oldest" :key="q.id" class="flex items-center gap-2 py-2 text-[13px]">
                    <span class="flex min-w-0 flex-1 flex-col">
                        <span class="truncate" :title="q.question">{{ q.question }}</span>
                        <span class="truncate text-[11px] text-muted-color">{{ q.groupBuyTitle }}</span>
                    </span>
                    <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(q.askedAt) }}</span>
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
const state = useDashboardKindData('GROUPBUY_QNA', () => props.gadget);
const v = computed(() => state.data.value);
</script>
