<template>
    <!-- 글·댓글 활동 — 오늘 글·댓글 수(어제 대비) + 7일 일별 막대(배치가 만든 일별 집계만 읽는다). 누르면 글 관리 -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" :disabled="state.disabled.value" :collect="state.collect" more="/posts" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <DashboardStatGrid :min="9" :stats="stats" />
            <DashboardMiniBars :days="postDays" :summary="t('dashboard.postsTrendSummary', { days: postDays.length })" />
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardStat } from '~/components/dashboard/DashboardStatCard.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const state = useDashboardKindData('POST_ACTIVITY', () => props.gadget);
const v = computed(() => state.data.value);

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    return [
        { key: 'posts', label: t('dashboard.postsToday'), count: d.postsToday, note: UtilDashboardMessages.delta(t, d.postsToday, d.postsYesterday, 'yesterday', locale.value), icon: 'pi pi-file', to: '/posts' },
        { key: 'comments', label: t('dashboard.commentsToday'), count: d.commentsToday, note: UtilDashboardMessages.delta(t, d.commentsToday, d.commentsYesterday, 'yesterday', locale.value), icon: 'pi pi-comment', to: '/posts' },
    ];
});
const postDays = computed(() => v.value?.days.map((d) => ({ date: d.date, count: d.posts })) ?? []);
</script>
