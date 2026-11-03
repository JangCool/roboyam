<template>
    <!-- 최근 가입 회원 N명(닉네임 · 가입 방법 · 시각). 누르면 회원 관리 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-user-plus"
        :empty-title="t('dashboard.recentSignupsNone')"
        more="/members"
        @refresh="state.reload"
    >
        <ul v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="m in v.items" :key="m.id" class="flex items-center gap-2 py-2 text-[13px]">
                <span class="min-w-0 flex-1 truncate" :title="m.nickname">{{ m.nickname }}</span>
                <span class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[11px] leading-none dark:bg-surface-800">{{ t(`member.authMethod.${m.method}`) }}</span>
                <span class="w-14 shrink-0 text-right text-[11px] text-muted-color tabular-nums">{{ time.ago(m.joinedAt) }}</span>
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
const state = useDashboardKindData('RECENT_SIGNUPS', () => props.gadget);
const v = computed(() => state.data.value);
</script>
