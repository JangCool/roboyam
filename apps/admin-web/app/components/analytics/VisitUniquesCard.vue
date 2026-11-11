<template>
    <!--
        순방문자(주·월) 카드 — 「방문자 통계 › 개요」 요약 카드 아래(방문 통계 3단계 결정 ④, ⚠ 지금은 목업 숫자). 고른 기간과 무관하게 늘 오늘 기준.
        머리: 제목 + ⓘ(세는 기준 — 달력·브라우저·봇과 운영자·AI 계정 제외·로그인 회원) · 오른쪽 방문자 / 회원 전환(이 화면에서만 기억).
        본문: 이번 주 · 지난 주 · 이번 달 · 지난 달 네 칸(진행 중인 칸은 「진행 중」 딱지 + 「지난 주(달) 같은 시점까지」 대비 증감 칩) → 최근 12주 · 최근 12개월 작은 막대(진행 중 칸은 빗금, 집계 전 칸은 바닥 선).
        수집이 꺼져 있으면(`collect.visits`) 「수집이 꺼져 있어요」. 값은 부모가 준다(서버를 부르지 않는다).
    -->
    <AppGadget :title="t('visitAnalytics.uniques.title')">
        <template #title-extra>
            <button
                v-tooltip.top="helpText"
                type="button"
                class="inline-grid size-5 shrink-0 place-items-center rounded text-muted-color hover:text-color focus-visible:outline-2 focus-visible:outline-primary"
                :aria-label="`${t('visitAnalytics.helpOf', { name: t('visitAnalytics.uniques.title') })} — ${helpText}`"
            >
                <i class="pi pi-info-circle text-[12px]" aria-hidden="true" />
            </button>
        </template>
        <template v-if="data?.collect.visits" #actions>
            <SelectButton v-model="audience" :options="audienceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('visitAnalytics.uniques.audienceLabel')" />
        </template>

        <ErrorState v-if="error" :title="t('visitAnalytics.uniques.loadFailed')" :description="error.message" :retry="error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!data" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <div class="grid grid-cols-2 gap-2 lg:grid-cols-4"><Skeleton v-for="n in 4" :key="n" height="5.5rem" border-radius="0.75rem" /></div>
            <Skeleton height="4.5rem" />
        </div>
        <VisitCollectOff v-else-if="!data.collect.visits" item="visits" :enabled="data.collect.enabled" />
        <div v-else class="flex min-w-0 flex-col gap-4 transition-opacity" :class="{ 'opacity-60': loading }" :aria-busy="loading">
            <p class="m-0 text-[12px] text-muted-color">{{ t('visitAnalytics.uniques.basis') }}</p>

            <dl class="m-0 grid grid-cols-2 gap-2 lg:grid-cols-4">
                <div v-for="c in cells" :key="c.key" class="flex min-w-0 flex-col gap-1 rounded-xl border border-(--line) bg-(--p-content-background) px-3.5 pt-3 pb-2.5">
                    <dt class="flex min-w-0 items-center gap-1.5 text-xs text-muted-color">
                        <span class="truncate">{{ c.label }}</span>
                        <span v-if="c.partial" class="inline-flex shrink-0 items-center rounded-full bg-primary-50 px-1.5 py-0.5 text-[10px] leading-none font-medium text-primary dark:bg-primary-950/40">{{ t('visitAnalytics.uniques.inProgress') }}</span>
                    </dt>
                    <dd class="m-0 flex min-w-0 flex-col gap-1">
                        <span class="truncate text-[11px] text-muted-color tabular-nums">{{ c.dates }}</span>
                        <span class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                            <span v-if="c.count === null" class="text-[15px] leading-tight font-medium text-muted-color">{{ t('visitAnalytics.uniques.notYet') }}</span>
                            <span v-else class="truncate text-[22px] leading-tight font-semibold tabular-nums">{{ num(c.count) }}</span>
                            <span v-if="c.partial && c.count !== null" class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[11px] leading-none font-medium whitespace-nowrap tabular-nums" :class="chipClass(c.change)" :title="c.deltaText">
                                <span aria-hidden="true">{{ arrow(c.change) }}</span>
                                <span class="sr-only">{{ c.deltaText }}</span>
                            </span>
                        </span>
                        <span class="truncate text-[12px] text-muted-color tabular-nums">{{ c.sub || ' ' }}</span>
                    </dd>
                </div>
            </dl>

            <div class="grid min-w-0 gap-4 md:grid-cols-2">
                <section v-for="b in bars" :key="b.key" class="flex min-w-0 flex-col gap-1.5" :aria-label="b.title">
                    <h3 class="m-0 text-[12px] font-medium text-muted-color">{{ b.title }}</h3>
                    <VisitColumnBars :items="b.items" :summary="b.summary" :format="num" :current="b.current" :current-text="t('visitAnalytics.uniques.inProgress')" :none-text="t('visitAnalytics.uniques.notYet')" :label-every="3" :height="56" />
                </section>
            </div>
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { VISIT_AUDIENCES, type VisitAudience, type VisitUniqueCompare, type VisitUniquePeriod, type VisitUniques } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';

interface Props {
    /** 순방문자 — null 이면 처음 읽는 중 */
    data?: VisitUniques | null;
    loading?: boolean;
    error?: ApiError | null;
}

const props = withDefaults(defineProps<Props>(), { data: null, loading: false, error: null });

const emit = defineEmits<{ (e: 'retry'): void }>();

const { t, locale } = useI18n();

/** 방문자 / 회원 — 화면에서만 바꾼다(다시 읽지 않는다 — 응답에 둘 다 있다) */
const audience = ref<VisitAudience>('VISITOR');
const audienceOptions = computed(() => VISIT_AUDIENCES.map((value) => ({ value, label: t(`visitAnalytics.uniques.audience.${value}`) })));

const helpText = computed(() => t(audience.value === 'MEMBER' ? 'visitAnalytics.uniques.helpMember' : 'visitAnalytics.uniques.helpVisitor'));

function num(n: number): string {
    return UtilVisitAnalytics.num(n, locale.value);
}

/** 「11/10 ~ 11/16」 */
function shortDate(ymd: string): string {
    return `${Number(ymd.slice(5, 7))}/${Number(ymd.slice(8, 10))}`;
}
function dates(p: VisitUniquePeriod): string {
    return `${shortDate(p.start)} ~ ${shortDate(p.end)}`;
}

interface Cell {
    key: string;
    label: string;
    dates: string;
    count: number | null;
    partial: boolean;
    change: number | null;
    deltaText: string;
    sub: string;
}

function cellsOf(c: VisitUniqueCompare, kind: 'week' | 'month'): Cell[] {
    const lang = locale.value;
    const monthDates = (p: VisitUniquePeriod) => UtilVisitAnalytics.monthLabel(p.start.slice(0, 7), lang);
    const change = c.current.count === null || c.previousSamePoint === null ? null : UtilVisitAnalytics.change(c.current.count, c.previousSamePoint);
    const samePoint = c.previousSamePoint === null ? '' : t(`visitAnalytics.uniques.samePoint.${kind}`, { n: num(c.previousSamePoint) });
    return [
        {
            key: `${kind}-current`,
            label: t(`visitAnalytics.uniques.current.${kind}`),
            dates: kind === 'week' ? dates(c.current) : monthDates(c.current),
            count: c.current.count,
            partial: c.current.partial,
            change,
            deltaText: `${t(`visitAnalytics.uniques.vsSamePoint.${kind}`)} · ${UtilVisitAnalyticsMessages.delta(t, change, lang)}`,
            sub: samePoint,
        },
        {
            key: `${kind}-previous`,
            label: t(`visitAnalytics.uniques.previous.${kind}`),
            dates: kind === 'week' ? dates(c.previous) : monthDates(c.previous),
            count: c.previous.count,
            partial: c.previous.partial,
            change: null,
            deltaText: '',
            sub: '',
        },
    ];
}

const picked = computed(() => props.data?.audiences[audience.value] ?? null);

const cells = computed<Cell[]>(() => {
    const a = picked.value;
    if (!a) return [];
    return [...cellsOf(a.week, 'week'), ...cellsOf(a.month, 'month')];
});

const bars = computed(() => {
    const a = picked.value;
    if (!a) return [];
    const lang = locale.value;
    const weekItems = a.weeks.map((p) => ({ key: p.start, label: dates(p), short: shortDate(p.start), value: p.count }));
    const monthItems = a.months.map((p) => ({ key: p.start, label: UtilVisitAnalytics.monthLabel(p.start.slice(0, 7), lang), short: UtilVisitAnalytics.monthShort(p.start.slice(0, 7), lang), value: p.count }));
    const currentOf = (list: VisitUniquePeriod[]) => list.find((p) => p.partial)?.start ?? '';
    return [
        { key: 'weeks', title: t('visitAnalytics.uniques.weeksTitle'), items: weekItems, current: currentOf(a.weeks), summary: t('visitAnalytics.uniques.weeksSummary', { n: weekItems.length }) },
        { key: 'months', title: t('visitAnalytics.uniques.monthsTitle'), items: monthItems, current: currentOf(a.months), summary: t('visitAnalytics.uniques.monthsSummary', { n: monthItems.length }) },
    ];
});

/** 증감 글자 — `▲ 12.3%` · `▼ 4.0%` · `—`(비교할 수 없음) — 요약 카드(`VisitSummaryStrip`)와 같은 모양 */
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
