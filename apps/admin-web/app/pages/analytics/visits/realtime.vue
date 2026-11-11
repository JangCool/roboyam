<template>
    <!--
        방문자 통계 › 실시간(`/analytics/visits/realtime`) — 기간 없음. 지금 접속(최근 5분) 큰 숫자 + 최근 30분 분당 페이지뷰(세로 막대 30, 지금 분은 집계 중)
        → 최근 5분 많이 본 페이지 10 | 최근 5분 유입 분류.
        화면이 보일 때만 30초마다 저절로 새로 고친다(탭이 가려지면 멈추고, 다시 보이면 바로 한 번 읽고 다시 센다). 「실시간은 최대 1분 늦어요」(서버가 1분마다 몰아 쓴다).
        지금 접속은 `analytics_visit_active`(방문 수집), 분 단위 숫자·최근 페이지·유입은 원본 최근 30분 — 원본 기록이 꺼져 있으면 지금 접속만 + 안내.
        권한: `site.read`(메뉴 `visitRealtime`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader
            :title="t('visitAnalytics.realtimeTitle')"
            :subtitle="t('visitAnalytics.realtimeSubtitle')"
            :as-of="rt?.asOf ?? null"
            :as-of-note="t('visitAnalytics.realtimeNote')"
            :busy="realtime.loading.value"
            :range="false"
            :insights="insights"
            @refresh="refreshNow"
        />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="realtime.error.value && !rt" :title="t('visitAnalytics.realtimeTitle')">
            <ErrorState :title="t('visitAnalytics.realtimeLoadFailed')" :description="realtime.error.value.message" retry :retrying="realtime.loading.value" @retry="refreshNow" />
        </AppGadget>
        <Skeleton v-else-if="!rt" height="16rem" border-radius="0.75rem" />
        <AppGadget v-else-if="!rt.collect.visits" :title="t('visitAnalytics.realtimeTitle')">
            <VisitCollectOff item="visits" :enabled="rt.collect.enabled" />
        </AppGadget>

        <template v-else>
            <div class="grid min-w-0 items-stretch gap-4 lg:grid-cols-3">
                <!-- 지금 접속 -->
                <div class="flex flex-col justify-between gap-3 rounded-xl border border-(--line) bg-(--p-content-background) px-5 py-4">
                    <div class="flex items-center gap-2 text-sm text-muted-color">
                        <span class="relative inline-flex size-2.5" aria-hidden="true">
                            <span class="rt-ping absolute inline-flex size-full rounded-full bg-green-500 opacity-60" />
                            <span class="relative inline-flex size-2.5 rounded-full bg-green-500" />
                        </span>
                        {{ t('visitAnalytics.activeNow') }}
                    </div>
                    <div class="flex items-baseline gap-2">
                        <span class="text-5xl leading-none font-semibold tracking-tight tabular-nums" aria-live="polite">{{ UtilVisitAnalytics.num(rt.activeNow, locale) }}</span>
                    </div>
                    <div class="flex flex-col gap-0.5 text-[12px] text-muted-color">
                        <span>{{ t('visitAnalytics.activeBigHint') }}</span>
                        <span class="inline-flex items-center gap-1.5">
                            <i :class="paused ? 'pi pi-pause-circle' : 'pi pi-sync'" class="text-[11px]" aria-hidden="true" />{{ paused ? t('visitAnalytics.autoPaused') : t('visitAnalytics.autoRefresh') }}
                        </span>
                        <span v-if="realtime.error.value" class="inline-flex items-center gap-1.5 text-amber-700 dark:text-amber-400">
                            <i class="pi pi-exclamation-circle text-[11px]" aria-hidden="true" />{{ t('dashboard.refreshFailed', { reason: realtime.error.value.message }) }}
                        </span>
                    </div>
                </div>

                <!-- 분당 페이지뷰 -->
                <AppGadget :title="t('visitAnalytics.perMinuteTitle')" class="lg:col-span-2">
                    <p v-if="!rt.collect.rawEvents" class="m-0 rounded-lg bg-surface-50 px-3 py-2.5 text-[13px] text-muted-color dark:bg-surface-900">{{ t('visitAnalytics.rtRawOff') }}</p>
                    <VisitColumnBars
                        v-else
                        :items="minuteItems"
                        :summary="t('visitAnalytics.perMinuteSummary')"
                        :format="numFmt"
                        :current="minuteItems[minuteItems.length - 1]?.key ?? ''"
                        :current-text="t('visitAnalytics.chartNow')"
                        :label-every="1"
                        :height="120"
                    />
                </AppGadget>
            </div>

            <div v-if="rt.collect.rawEvents" class="grid min-w-0 items-start gap-4 lg:grid-cols-5">
                <AppGadget :title="t('visitAnalytics.rtPagesTitle')" class="lg:col-span-3">
                    <EmptyState v-if="!rt.pages.length" icon="pi pi-desktop" :title="t('visitAnalytics.rtEmpty')" />
                    <VisitBarList v-else :rows="pageRows" rank />
                </AppGadget>
                <AppGadget :title="t('visitAnalytics.rtReferrersTitle')" class="lg:col-span-2">
                    <EmptyState v-if="!referrerTotal" icon="pi pi-directions" :title="t('visitAnalytics.rtReferrersEmpty')" />
                    <VisitBarList v-else :rows="groupRows" :total="referrerTotal" />
                </AppGadget>
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { VisitBarRow } from '~/components/analytics/VisitBarList.types';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';
import UtilVisitAnalyticsMessages from '~/utils/util-visit-analytics-messages';
import UtilVisitPathMessages from '~/utils/util-visit-path-messages';

const { t, te, locale } = useI18n();
useSeoMeta({ title: () => `${t('visitAnalytics.realtimeTitle')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const allowed = computed(() => can('site.read'));

const realtime = useVisitLoad(api.realtime);
const rt = computed(() => realtime.data.value);
const numFmt = (n: number) => UtilVisitAnalytics.num(n, locale.value);

// ── 30초 자동 새로 고침(보일 때만) ──
const EVERY_MS = 30_000;
const paused = ref(false);
let timer: ReturnType<typeof setInterval> | null = null;

/** 값은 두고 다시 읽는다 — 실패해도 지난 값은 그대로(머리에 경고) */
async function read(): Promise<void> {
    const before = realtime.data.value;
    await realtime.load();
    if (!realtime.data.value && before) realtime.data.value = before;
}

function start(): void {
    stop();
    timer = setInterval(() => void read(), EVERY_MS);
}
function stop(): void {
    if (timer) clearInterval(timer);
    timer = null;
}
function refreshNow(): void {
    void read();
    if (!paused.value) start();
}
function onVisibility(): void {
    if (document.visibilityState === 'hidden') {
        paused.value = true;
        stop();
    } else {
        paused.value = false;
        refreshNow();
    }
}

onMounted(() => {
    if (!allowed.value) return;
    paused.value = document.visibilityState === 'hidden';
    void read();
    if (!paused.value) start();
    document.addEventListener('visibilitychange', onVisibility);
});
onBeforeUnmount(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibility);
});

// ── 값 ──
const minuteItems = computed(() => {
    const list = rt.value?.perMinute ?? [];
    return list.map((p, i) => {
        const ago = list.length - 1 - i;
        const label = ago === 0 ? t('visitAnalytics.nowLabel') : t('visitAnalytics.minutesAgo', { n: ago });
        // 아래 눈금은 5분 단위 「25분 전 · 20분 전 … 지금」만(사용자 결정 2026-11-04 — 「-29」 대신 읽기 쉬운 말), 나머지 칸은 비운다
        return { key: p.at, label, short: ago % 5 === 0 ? label : '', value: p.pageViews };
    });
});

function kindName(kind: string): string {
    const key = `dashboard.pageKind.${kind}`;
    return te(key) ? t(key) : t('dashboard.pageKind.OTHER');
}

const pageRows = computed<VisitBarRow[]>(() =>
    (rt.value?.pages ?? []).map((p) => ({ key: p.path, label: UtilVisitPathMessages.label(t, localizedName, p), title: p.path, mono: !UtilVisitPathMessages.named(p), tag: kindName(p.kind), value: p.views })),
);
const groupRows = computed<VisitBarRow[]>(() => UtilVisitAnalyticsMessages.groupRows(t, rt.value?.referrers ?? []));
const referrerTotal = computed(() => (rt.value?.referrers ?? []).reduce((a, g) => a + g.visits, 0));

const insights = computed(() => {
    const d = rt.value;
    if (!d || !d.collect.visits) return [];
    const lang = locale.value;
    const out = [t('visitAnalytics.insightActive', { n: UtilVisitAnalytics.num(d.activeNow, lang) })];
    const top = d.pages[0];
    if (top) out.push(t('visitAnalytics.insightRealtimeTop', { page: UtilVisitPathMessages.label(t, localizedName, top), n: UtilVisitAnalytics.num(top.views, lang) }));
    return out;
});
</script>

<style scoped>
.rt-ping {
    animation: rt-ping 1.6s cubic-bezier(0, 0, 0.2, 1) infinite;
}
@keyframes rt-ping {
    75%,
    100% {
        transform: scale(2);
        opacity: 0;
    }
}
@media (prefers-reduced-motion: reduce) {
    .rt-ping {
        animation: none;
    }
}
</style>
