<template>
    <!-- 최근 글 N건(게시판 · 제목 · 작성자 · 시각, 숨김·신고 표시). 누르면 글 관리 -->
    <DashboardGadgetFrame
        :title="title"
        :at="state.at.value"
        :loading="state.loading.value"
        :error="state.error.value"
        :ready="!!v"
        :forbidden="state.forbidden.value"
        :unavailable="state.unavailable.value"
        :empty="!v?.items.length"
        empty-icon="pi pi-file"
        :empty-title="t('dashboard.recentPostsNone')"
        more="/posts"
        @refresh="state.reload"
    >
        <ul v-if="v" class="m-0 flex list-none flex-col divide-y divide-(--line) p-0">
            <li v-for="p in v.items" :key="p.id" class="flex items-center gap-2 py-2 text-[13px]">
                <span class="flex min-w-0 flex-1 flex-col">
                    <span class="flex min-w-0 items-center gap-1.5">
                        <span class="min-w-0 truncate" :class="p.hidden ? 'text-muted-color line-through' : ''" :title="p.title">{{ p.title }}</span>
                        <span v-if="p.hidden" class="inline-flex shrink-0 items-center justify-center rounded bg-surface-100 px-1.5 py-1 text-[10px] leading-none text-muted-color dark:bg-surface-800">{{ t('dashboard.postHidden') }}</span>
                        <span v-if="p.reported" class="inline-flex shrink-0 items-center justify-center rounded bg-amber-50 px-1.5 py-1 text-[10px] leading-none text-amber-700 dark:bg-amber-950 dark:text-amber-400">{{ t('dashboard.postReported') }}</span>
                    </span>
                    <span class="truncate text-[11px] text-muted-color">{{ localizedName(p.boardName, p.boardNameEn) }} · {{ p.authorNickname }}</span>
                </span>
                <span class="shrink-0 text-[11px] text-muted-color tabular-nums">{{ time.ago(p.createdAt) }}</span>
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
const { localizedName } = useLocalizedName();
const state = useDashboardKindData('RECENT_POSTS', () => props.gadget);
const v = computed(() => state.data.value);
</script>
