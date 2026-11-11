<template>
    <!--
        페이지 서랍 — 「방문자 통계 › 페이지」에서 줄을 누르면 연다(앱의 서랍 관례: 위치는 화면 설정 `drawerPosition`, 머리 = 이름 + 종류, 본문 맨 위 요약 영역 `DrawerSummary`).
        요약(경로 · 기간 조회수 · 일평균 · 가장 많이 본 날) + 「이동 흐름에서 보기」(같은 기간·`path`) → 일별 조회(세로 막대) → 들어온 곳 · 나간 곳 상위 5(「바로 들어옴」·「그 밖」 흐리게).
        이동 수집이 꺼져 있으면 들어온/나간 곳 자리에 「수집이 꺼져 있어요」. 서버는 부르지 않는다(값은 부모가 준다).
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '36rem', maxWidth: '100vw' }">
        <template #header>
            <div class="flex min-w-0 flex-1 items-center gap-2">
                <h2 :id="titleId" class="m-0 min-w-0 truncate text-base font-semibold" :class="detail && !UtilVisitPathMessages.named(detail) ? 'font-mono text-[14px]' : ''">{{ headTitle }}</h2>
                <Tag v-if="detail" :value="kindName(detail.kind)" severity="secondary" />
            </div>
        </template>

        <ErrorState v-if="error" :title="t('visitAnalytics.pageDetailFailed')" :description="error.message" :retry="error.status !== 403" :retrying="loading" @retry="emit('retry')" />
        <div v-else-if="!detail" class="flex flex-col gap-3 py-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 6" :key="n" height="1rem" :width="n % 2 ? '85%' : '60%'" />
        </div>

        <div v-else class="flex flex-col gap-5 transition-opacity" :class="{ 'opacity-60': loading }" :aria-busy="loading">
            <DrawerSummary :label="t('visitAnalytics.pageSummary')" :items="summaryItems" :columns="3">
                <template #links>
                    <NuxtLink v-if="collect.flows" :to="flowsLink" class="inline-flex items-center gap-1 font-medium text-(--p-primary-color) hover:underline">
                        <i class="pi pi-sitemap text-[11px]" aria-hidden="true" />{{ t('visitAnalytics.openFlows') }}
                    </NuxtLink>
                </template>
            </DrawerSummary>

            <section class="flex flex-col gap-2" :aria-labelledby="daysId">
                <h3 :id="daysId" class="m-0 text-sm font-medium">{{ t('visitAnalytics.pageDaily') }}</h3>
                <EmptyState v-if="!detail.views" icon="pi pi-chart-bar" :title="t('visitAnalytics.pageNoViews')" />
                <VisitColumnBars
                    v-else
                    :items="dayItems"
                    :summary="t('visitAnalytics.pageDailySummary', { n: dayItems.length })"
                    :format="numFmt"
                    :current="today"
                    :current-text="t('visitAnalytics.chartNow')"
                    :label-every="Math.max(1, Math.ceil(dayItems.length / 6))"
                    :height="96"
                />
            </section>

            <VisitCollectOff v-if="!collect.flows" item="flows" :enabled="collect.enabled" />
            <div v-else class="grid gap-5 sm:grid-cols-2">
                <section class="flex min-w-0 flex-col gap-2" :aria-labelledby="inId">
                    <h3 :id="inId" class="m-0 flex items-baseline justify-between gap-2 text-sm font-medium">
                        <span>{{ t('visitAnalytics.incoming') }}</span>
                        <span class="text-[12px] font-normal text-muted-color tabular-nums">{{ t('visitAnalytics.incomingTotal', { n: UtilVisitAnalytics.num(detail.incomingTotal, locale) }) }}</span>
                    </h3>
                    <p v-if="!detail.incoming.length" class="m-0 text-[13px] text-muted-color">{{ t('visitAnalytics.topEmpty') }}</p>
                    <VisitBarList v-else :rows="endRows(detail.incoming, detail.incomingTotal)" :total="detail.incomingTotal" />
                </section>
                <section class="flex min-w-0 flex-col gap-2" :aria-labelledby="outId">
                    <h3 :id="outId" class="m-0 flex items-baseline justify-between gap-2 text-sm font-medium">
                        <span>{{ t('visitAnalytics.outgoing') }}</span>
                        <span class="text-[12px] font-normal text-muted-color tabular-nums">{{ t('visitAnalytics.outgoingTotal', { n: UtilVisitAnalytics.num(detail.outgoingTotal, locale) }) }}</span>
                    </h3>
                    <p v-if="!detail.outgoing.length" class="m-0 text-[13px] text-muted-color">{{ t('visitAnalytics.topEmpty') }}</p>
                    <VisitBarList v-else :rows="endRows(detail.outgoing, detail.outgoingTotal)" :total="detail.outgoingTotal" />
                </section>
            </div>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import type { DrawerSummaryItem } from '~/components/common/DrawerSummary.types';
import type { VisitCollect, VisitFlowEnd, VisitPageDetail, VisitPageKind } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 상세 — null 이면 읽는 중 */
    detail?: VisitPageDetail | null;
    /** 열 때 바로 보일 이름(상세가 오기 전) */
    fallbackTitle?: string;
    loading?: boolean;
    error?: ApiError | null;
    collect: VisitCollect;
    /** 「이동 흐름에서 보기」 주소 */
    flowsLink: RouteLocationRaw;
}

const props = withDefaults(defineProps<Props>(), { detail: null, fallbackTitle: '', loading: false, error: null });

const emit = defineEmits<{ (e: 'retry'): void }>();

const { t, te, locale } = useI18n();
const { localizedName } = useLocalizedName();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const daysId = useId();
const inId = useId();
const outId = useId();
const today = UtilVisitAnalytics.today();
const numFmt = (n: number) => UtilVisitAnalytics.num(n, locale.value);

function kindName(kind: VisitPageKind | string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}

const headTitle = computed(() => {
    const d = props.detail;
    if (!d) return props.fallbackTitle;
    return UtilVisitPathMessages.label(t, localizedName, d);
});

const summaryItems = computed<DrawerSummaryItem[]>(() => {
    const d = props.detail;
    if (!d) return [];
    const lang = locale.value;
    const best = d.days.reduce((a, x) => (x.views > (a?.views ?? -1) ? x : a), null as { date: string; views: number } | null);
    return [
        { key: 'path', label: t('visitAnalytics.col.page'), value: d.path, code: true, wide: true },
        { key: 'views', label: t('visitAnalytics.pageViewsInRange'), value: UtilVisitAnalytics.num(d.views, lang) },
        { key: 'avg', label: t('visitAnalytics.pageViewsPerDay'), value: UtilVisitAnalytics.decimal(d.days.length ? d.views / d.days.length : null, lang) },
        { key: 'best', label: t('visitAnalytics.pageBestDay'), value: best && best.views > 0 ? `${time.value.serviceDate(best.date)} · ${UtilVisitAnalytics.num(best.views, lang)}` : '—' },
    ];
});

const dayItems = computed(() =>
    (props.detail?.days ?? []).map((d) => ({ key: d.date, label: time.value.serviceDate(d.date), short: `${Number(d.date.slice(5, 7))}/${Number(d.date.slice(8, 10))}`, value: d.views })),
);

/** 들어온/나간 곳 줄 — 상위 5 + 「그 밖」(합과 차이) */
function endRows(list: VisitFlowEnd[], total: number): VisitBarRow[] {
    const rows: VisitBarRow[] = list.map((e) =>
        e.path === null
            ? { key: '__entry', label: t('visitAnalytics.directEntry'), title: t('visitAnalytics.directEntryHelp'), value: e.moves, muted: true }
            : { key: e.path, label: UtilVisitPathMessages.label(t, localizedName, { ...e, path: e.path }), sub: UtilVisitPathMessages.named(e) ? e.path : undefined, mono: !UtilVisitPathMessages.named(e), title: e.path, value: e.moves },
    );
    const rest = total - list.reduce((a, e) => a + e.moves, 0);
    if (rest > 0) rows.push({ key: '__rest', label: t('visitAnalytics.rest'), title: t('visitAnalytics.restHelp'), value: rest, muted: true });
    return rows;
}
</script>
