<template>
    <!--
        요약 카드 5장 — 방문자 · 신규 방문자(비율) · 페이지뷰 · 1인당 페이지뷰 · 지금 접속(최근 5분).
        카드 = 이름(+ ⓘ 툴팁) · 큰 숫자 · 증감 칩(직전 같은 길이 기간 대비, ▲ 초록 · ▼ 빨강 · 비교 불가 —) · 덧글자 · 미니 추이(`StatsTrendChart` compact — 대시보드 가젯의 미니 막대와 같은 부품).
        여러 날 기간의 방문자는 「방문자(일별 합)」 — 날마다 센 순방문자의 합이라 기간 전체 순방문자가 아니다(ⓘ 설명, 덧글자 일평균). 하루 기간이면 「방문자」.
        지금 접속은 기간과 무관(증감·추이 없음). `summary` 가 없으면 Skeleton.
    -->
    <div class="flex flex-col gap-1.5">
        <dl class="m-0 grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-5">
            <div v-for="c in cells" :key="c.key" class="flex min-w-0 flex-col gap-1.5 rounded-xl border border-(--line) bg-(--p-content-background) px-3.5 pt-3 pb-2.5" :class="c.key === 'active' ? 'col-span-2 sm:col-span-1' : ''">
                <dt class="flex items-center gap-1 text-xs text-muted-color">
                    <i v-if="c.key === 'active'" class="pi pi-circle-fill text-[7px] text-green-600 dark:text-green-400" aria-hidden="true" />
                    <span class="truncate">{{ c.label }}</span>
                    <button
                        v-tooltip.top="c.help"
                        type="button"
                        class="inline-grid size-5 shrink-0 place-items-center rounded text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                        :aria-label="`${t('visitAnalytics.helpOf', { name: c.label })} — ${c.help}`"
                    >
                        <i class="pi pi-info-circle text-[11px]" aria-hidden="true" />
                    </button>
                </dt>
                <dd class="m-0 flex min-w-0 flex-col gap-1">
                    <template v-if="!summary">
                        <Skeleton height="1.75rem" width="60%" />
                        <Skeleton height="1.75rem" />
                    </template>
                    <template v-else>
                        <span class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                            <span class="truncate text-[22px] leading-tight font-semibold tabular-nums">{{ c.value }}</span>
                            <span v-if="c.key !== 'active'" class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[11px] leading-none font-medium whitespace-nowrap tabular-nums" :class="chipClass(c.change)" :title="c.deltaText">
                                <span aria-hidden="true">{{ arrow(c.change) }}</span>
                                <span class="sr-only">{{ c.deltaText }}</span>
                            </span>
                        </span>
                        <span class="truncate text-[12px] text-muted-color tabular-nums">{{ c.sub || ' ' }}</span>
                        <StatsTrendChart v-if="c.spark.length > 1" :days="c.spark" :today="current" :labels="sparkLabels(c.label)" compact :height="28" />
                        <span v-else class="h-7" aria-hidden="true" />
                    </template>
                </dd>
            </div>
        </dl>
        <p v-if="summary" class="m-0 text-[12px] text-muted-color tabular-nums">
            {{ UtilVisitAnalyticsMessages.compareBase(t, summary.range, includesToday) }} · {{ t('visitAnalytics.previousRange', { range: UtilVisitAnalyticsMessages.range(summary.previous.range, (d) => time.serviceDate(d)) }) }}
        </p>
    </div>
</template>

<script setup lang="ts">
import type { StatsTrendDay, StatsTrendLabels } from '#layers/chart/components/StatsTrendChart.types';
import type { VisitSeriesPoint, VisitSummary } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';

interface Props {
    /** 요약 — null 이면 처음 읽는 중 */
    summary?: VisitSummary | null;
}

const props = withDefaults(defineProps<Props>(), { summary: null });

const { t, locale } = useI18n();
const time = useDisplayTime();

const single = computed(() => (props.summary ? UtilVisitAnalytics.isSingleDay(props.summary.range) : true));
const includesToday = computed(() => (props.summary ? UtilVisitAnalytics.includesToday(props.summary.range) : false));

/** 미니 추이에서 집계 중인 칸 — 시간별이면 마지막 칸, 일별이면 오늘 */
const current = computed(() => {
    const s = props.summary;
    if (!s || !includesToday.value) return '';
    return s.granularity === 'DAY' ? UtilVisitAnalytics.today() : (s.series[s.series.length - 1]?.at ?? '');
});

interface Cell {
    key: string;
    label: string;
    help: string;
    value: string;
    sub: string;
    change: number | null;
    deltaText: string;
    spark: StatsTrendDay[];
}

function spark(pick: (p: VisitSeriesPoint) => number): StatsTrendDay[] {
    return (props.summary?.series ?? []).map((p) => ({ date: p.at, impressions: Math.max(0, pick(p)), clicks: 0 }));
}

const cells = computed<Cell[]>(() => {
    const s = props.summary;
    const lang = locale.value;
    const num = (n: number) => UtilVisitAnalytics.num(n, lang);
    const cur = s?.totals;
    const prev = s?.previous;
    const days = s ? UtilVisitAnalytics.days(s.range) : 1;
    const cell = (key: string, label: string, help: string, value: string, sub: string, now: number | null, before: number | null, sp: StatsTrendDay[]): Cell => {
        const change = now === null || before === null ? null : UtilVisitAnalytics.change(now, before);
        return { key, label, help, value, sub, change, deltaText: UtilVisitAnalyticsMessages.delta(t, change, lang), spark: sp };
    };
    const perNow = cur ? UtilVisitAnalytics.perVisitor(cur) : null;
    const perBefore = prev ? UtilVisitAnalytics.perVisitor(prev) : null;
    return [
        cell(
            'visitors',
            single.value ? t('visitAnalytics.visitors') : t('visitAnalytics.visitorsDaily'),
            single.value ? t('visitAnalytics.visitorsHelpSingle') : t('visitAnalytics.visitorsHelp'),
            cur ? num(cur.visitors) : '—',
            cur && !single.value ? t('visitAnalytics.dailyAverage', { n: num(Math.round(cur.visitors / days)) }) : '',
            cur?.visitors ?? null,
            prev?.visitors ?? null,
            spark((p) => p.visitors),
        ),
        cell(
            'new',
            t('visitAnalytics.newVisitors'),
            t('visitAnalytics.newHelp'),
            cur ? num(cur.newVisitors) : '—',
            cur ? t('visitAnalytics.newRate', { rate: UtilVisitAnalytics.percent(UtilVisitAnalytics.newRate(cur), lang) }) : '',
            cur?.newVisitors ?? null,
            prev?.newVisitors ?? null,
            spark((p) => p.newVisitors),
        ),
        cell('views', t('visitAnalytics.pageViews'), t('visitAnalytics.pageViewsHelp'), cur ? num(cur.pageViews) : '—', '', cur?.pageViews ?? null, prev?.pageViews ?? null, spark((p) => p.pageViews)),
        cell(
            'per',
            t('visitAnalytics.perVisitor'),
            single.value ? t('visitAnalytics.perVisitorHelp') : t('visitAnalytics.perVisitorHelpDaily'),
            UtilVisitAnalytics.decimal(perNow, lang),
            '',
            perNow,
            perBefore,
            // 미니 막대는 정수 축이라 10배로 그린다(모양만 본다)
            spark((p) => Math.round((p.visitors > 0 ? p.pageViews / p.visitors : 0) * 10)),
        ),
        { key: 'active', label: t('visitAnalytics.activeNow'), help: t('visitAnalytics.activeNowHelp'), value: s ? num(s.activeNow) : '—', sub: t('visitAnalytics.activeNowSub'), change: null, deltaText: '', spark: [] },
    ];
});

function sparkLabels(name: string): StatsTrendLabels {
    return {
        summary: t('visitAnalytics.sparkSummary', { name }),
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
    };
}

/** 증감 글자 — `▲ 12.3%` · `▼ 4.0%` · `—`(비교할 수 없음) · `0.0%` */
function arrow(change: number | null): string {
    if (change === null) return '—';
    const rate = UtilVisitAnalytics.percent(Math.abs(change), locale.value);
    if (rate === UtilVisitAnalytics.percent(0, locale.value)) return rate;
    return `${change > 0 ? '▲' : '▼'} ${rate}`;
}

function chipClass(change: number | null): string {
    if (change === null || Math.abs(change) < 0.05) return 'bg-surface-100 text-muted-color dark:bg-surface-800';
    return change > 0 ? 'bg-green-50 text-green-700 dark:bg-green-950/40 dark:text-green-400' : 'bg-red-50 text-red-600 dark:bg-red-950/40 dark:text-red-400';
}
</script>
