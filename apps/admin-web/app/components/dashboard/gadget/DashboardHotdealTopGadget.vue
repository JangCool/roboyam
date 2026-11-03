<template>
    <!-- 지금 인기 핫딜 TOP 5(판매처 · 제목 · 가격 · 추천). 누르면 핫딜 글 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-bolt"
        :empty-title="t('dashboard.hotdealTopNone')"
        more="/hotdeals"
        @refresh="state.reload"
    >
        <ol v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="(d, i) in v.items" :key="d.id" class="flex items-center gap-2 py-2 text-[13px]">
                <span class="w-5 shrink-0 text-center font-semibold tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">{{ i + 1 }}</span>
                <span class="flex min-w-0 flex-1 flex-col">
                    <span class="truncate" :title="d.title">{{ d.title }}</span>
                    <span class="truncate text-[11px] text-muted-color">{{ d.shopName }}<template v-if="d.price !== null"> · {{ UtilDashboardMessages.won(t, d.price, locale) }}</template></span>
                </span>
                <span class="inline-flex shrink-0 items-center gap-1 text-[11px] text-muted-color tabular-nums">
                    <i class="pi pi-thumbs-up text-[10px]" aria-hidden="true" /><span class="sr-only">{{ t('dashboard.likes') }}</span>{{ d.likes.toLocaleString(locale) }}
                </span>
            </li>
        </ol>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const state = useDashboardKindData('HOTDEAL_TOP', () => props.gadget);
const v = computed(() => state.data.value);
</script>
