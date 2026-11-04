<template>
    <!--
        방문 현황 — 오늘 방문자 · 페이지뷰 · 지금 접속(최근 5분)을 한 줄 행으로(어제 같은 시각까지 대비는 툴팁) + 오늘 시간대별 방문자 막대(시간별 집계만 읽는다 — 원본 로그를 세지 않는다).
        바로 가기 = 「방문자 통계 › 개요」의 오늘(`/analytics/visits?from=오늘&to=오늘`).
    -->
    <DashboardGadgetFrame :title="title" :at="state.at.value" :loading="state.loading.value" :error="state.error.value" :ready="!!v" :forbidden="state.forbidden.value" :unavailable="state.unavailable.value" :disabled="state.disabled.value" :collect="state.collect" :more="moreLink" @refresh="state.reload">
        <div v-if="v" class="flex flex-col gap-3">
            <!-- 숫자는 세 열(사용자 결정 2026-11-03) — 좁은 칸이라 이름 위·숫자 아래 작은 카드, 짧은 이름·아이콘 없음(긴 이름·대비는 툴팁) -->
            <DashboardStatGrid :cols="3" compact :stats="stats" />
            <DashboardMiniBars :days="hourBars" :current="hourBars[hourBars.length - 1]?.date ?? ''" :edges="edges" :summary="t('dashboard.visitorsTrendSummary', { n: hourBars.length })" />
        </div>
    </DashboardGadgetFrame>
</template>

<script setup lang="ts">
import type { DashboardStat } from '~/components/dashboard/DashboardStatCard.types';
import type { DashboardGadgetItem } from '~/composables/dashboard/api/useDashboardLayoutApi.types';
import UtilDashboardMessages from '~/utils/util-dashboard-messages';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

interface Props {
    gadget: DashboardGadgetItem;
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t, locale } = useI18n();
const state = useDashboardKindData('VISITORS', () => props.gadget);
const v = computed(() => state.data.value);
/** 바로 가기 — 방문 분석 화면의 오늘(날짜는 그릴 때 계산) */
const moreLink = computed(() => UtilVisitAnalytics.link('TODAY'));

const stats = computed<DashboardStat[]>(() => {
    const d = v.value;
    if (!d) return [];
    const lang = locale.value;
    return [
        { key: 'visitors', label: t('dashboard.visitorsShort'), count: d.visitorsToday, note: `${t('dashboard.visitorsToday')} · ${UtilDashboardMessages.delta(t, d.visitorsToday, d.visitorsYesterdaySameTime, 'yesterday', lang)}` },
        { key: 'views', label: t('dashboard.pageViewsShort'), count: d.pageViewsToday, note: `${t('dashboard.pageViewsToday')} · ${UtilDashboardMessages.delta(t, d.pageViewsToday, d.pageViewsYesterdaySameTime, 'yesterday', lang)}` },
        { key: 'active', label: t('dashboard.activeNow'), count: d.activeNow, note: t('dashboard.activeNowNote') },
    ];
});

/** 시간대 막대 — 칸 키는 `h<시>`, 마지막(지금 시각) 칸은 집계 중 */
const hourBars = computed(() => v.value?.hours.map((h) => ({ date: `h${h.hour}`, count: h.visitors })) ?? []);
const edges = computed<[string, string] | null>(() => {
    const hours = v.value?.hours;
    if (!hours?.length) return null;
    return [t('dashboard.hourN', { n: hours[0]!.hour }), t('dashboard.hourN', { n: hours[hours.length - 1]!.hour })];
});
</script>
