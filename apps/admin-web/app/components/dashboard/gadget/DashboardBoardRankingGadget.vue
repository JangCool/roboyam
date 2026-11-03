<template>
    <!-- 오늘 활발한 게시판 TOP 5(글 · 댓글 수, 막대는 1위 대비) — 게시판 일별 집계를 읽는다. 누르면 게시판 관리 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-sort-amount-down"
        :empty-title="t('dashboard.boardRankingNone')"
        more="/boards"
        @refresh="state.reload"
    >
        <ol v-if="v" class="m-0 flex list-none flex-col gap-2.5 p-0">
            <li v-for="(b, i) in v.items" :key="b.boardId" class="flex flex-col gap-1 text-[13px]">
                <span class="flex items-center gap-2">
                    <span class="w-5 shrink-0 text-center font-semibold tabular-nums" :class="i < 3 ? 'text-primary' : 'text-muted-color'">{{ i + 1 }}</span>
                    <span class="min-w-0 flex-1 truncate">{{ b.boardName }}</span>
                    <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ t('dashboard.boardRankingCounts', { posts: b.posts.toLocaleString(locale), comments: b.comments.toLocaleString(locale) }) }}</span>
                </span>
                <span class="ml-7 h-1 overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" aria-hidden="true">
                    <span class="block h-full rounded-full bg-primary" :style="{ width: `${share(b)}%` }" />
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
const state = useDashboardKindData('BOARD_RANKING', () => props.gadget);
const v = computed(() => state.data.value);

const top = computed(() => Math.max(1, ...(v.value?.items ?? []).map((b) => b.posts + b.comments)));
/** 1위 대비 비율(%) */
function share(b: { posts: number; comments: number }): number {
    return Math.round(((b.posts + b.comments) / top.value) * 100);
}
</script>
