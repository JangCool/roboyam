<template>
    <!--
        날짜별 미니 막대 — 공유 레이어 차트(`StatsTrendChart` compact, `@roboyam/chart` — ECharts)에 하루 숫자 하나를 막대로 넘기는 얇은 겹. 오늘 막대는 빗금·흐리게(집계 중).
        축·툴팁이 없어 차트 상자의 `aria-label`(요약)과 아래 첫날·마지막 날 글자로 읽는다.
        날짜가 아닌 칸(방문 현황의 시간대)은 `edges` 로 양 끝 글자를, `current` 로 집계 중인 칸의 키를 준다.
    -->
    <div class="flex flex-col gap-1">
        <StatsTrendChart :days="chartDays" :today="current || today" :labels="labels" compact :height="height" />
        <div v-if="days.length" class="flex justify-between text-[10px] text-muted-color tabular-nums" aria-hidden="true">
            <span>{{ edges ? edges[0] : time.serviceDate(days[0]!.date) }}</span>
            <span>{{ edges ? edges[1] : time.serviceDate(days[days.length - 1]!.date) }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { StatsTrendDay, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { DashboardDayCount } from '~/composables/dashboard/api/useDashboardGadgetApi.types';
import UtilBizApplication from '~/utils/util-biz-application';

interface Props {
    /** 오래된 차례, 빈 날 없이 */
    days: DashboardDayCount[];
    /** 차트 요약(스크린 리더) */
    summary: string;
    /** 높이(px) */
    height?: number;
    /** 양 끝 글자 — 없으면 첫날·마지막 날(날짜가 아닌 칸일 때 준다) */
    edges?: [string, string] | null;
    /** 집계 중인 칸의 `date` 키 — 없으면 오늘 날짜 */
    current?: string;
}

const props = withDefaults(defineProps<Props>(), { height: 56, edges: null, current: '' });

const time = useDisplayTime();
const today = UtilBizApplication.kstToday();

const chartDays = computed<StatsTrendDay[]>(() => props.days.map((d) => ({ date: d.date, impressions: d.count, clicks: 0 })));

/** compact 는 요약만 읽는다 — 나머지는 비워 둔다(레이어 계약상 칸만 채움) */
const labels = computed<StatsTrendLabels>(() => ({
    summary: props.summary,
    keys: '',
    impressions: '',
    clicks: '',
    ctr: '',
    date: '',
    state: '',
    tableCaption: '',
    today: '',
    states: { SHOWING: '', PAUSED: '', TAKEN_DOWN: '' },
    legend: '',
    legendImpressions: '',
    legendClicks: '',
    legendPaused: '',
    legendTakenDown: '',
    legendToday: '',
}));
</script>
