<template>
    <!-- 가입 추이 — 7일·30일(⚙ 설정) 일별 막대 + 기간 합계(배치가 만든 일별 집계만 읽는다). 누르면 회원 관리 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" more="/members" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-2">
            <p class="m-0 flex items-baseline gap-2">
                <span class="text-2xl font-semibold tabular-nums">{{ total.toLocaleString(locale) }}</span>
                <span class="text-[12px] text-muted-color">{{ t('dashboard.signupTotal', { days: v.days.length }) }}</span>
            </p>
            <DashboardMiniBars :days="v.days" :summary="t('dashboard.signupTrendSummary', { days: v.days.length, n: total })" :height="64" />
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

const { t, locale } = useI18n();
const state = useDashboardKindData('SIGNUP_TREND', () => props.gadget);
const v = computed(() => state.data.value);
const total = computed(() => v.value?.days.reduce((s, d) => s + d.count, 0) ?? 0);
</script>
