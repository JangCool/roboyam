<template>
    <!-- 지금 인기글 TOP N(게시판 · 제목 · 추천) — 인기글 집계 결과를 읽는다. 누르면 인기글 관리 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-star"
        :empty-title="t('dashboard.popularNone')"
        more="/content/popular"
        @refresh="state.reload"
    >
        <ol v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="(p, i) in v.items" :key="p.postId" class="flex items-center gap-2 py-2 text-[13px]">
                <span class="w-5 shrink-0 text-center font-semibold tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">{{ i + 1 }}</span>
                <span class="shrink-0 text-[11px] text-muted-color">{{ localizedName(p.boardName, p.boardNameEn) }}</span>
                <span class="min-w-0 flex-1 truncate" :title="p.title">{{ p.title }}</span>
                <span class="inline-flex shrink-0 items-center gap-1 text-[11px] text-muted-color tabular-nums">
                    <i class="pi pi-thumbs-up text-[10px]" aria-hidden="true" /><span class="sr-only">{{ t('dashboard.likes') }}</span>{{ p.likes.toLocaleString(locale) }}
                </span>
            </li>
        </ol>
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
const { localizedName } = useLocalizedName();
const state = useDashboardKindData('POPULAR_NOW', () => props.gadget);
const v = computed(() => state.data.value);
</script>
