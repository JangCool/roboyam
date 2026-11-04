<template>
    <!--
        방문자 통계 › 수집 진단(`/analytics/visits/health`, 2단계 2026-11-04) — 기간 없음(최근 24시간). 운영 점검용이라 **숫자보다 「무엇이 문제인지」 문장을 먼저** 보인다.
        ① 상태 칸: 배지(정상·지연·멈춤·꺼짐 — 서버 판정) + 한 줄 + 문제 문장 목록(서버 코드 → 문장, 심각한 차례)
        ② 서버별(인스턴스) 기록 표: 마지막 기록(점 색 — 2분 안 초록 · 10분 안 노랑 · 넘으면 빨강) · 1분 기록 성공/실패 · 버린 신호(버퍼 넘침) · 무시한 신호(봇 · 형식 오류) | 원본 보관 현황
        ③ 크론 표(파티션·정리·게시판 활동 …): 주기 · 마지막 실행 · 결과(배치 로그와 같은 딱지) · 처리 건수 · 사유 · 「로그」(배치 로그 `?jobName=` — `batch.read` 일 때만)
        ④ 최근 기록 실패 5건(시각 · 서버 · 사유).
        스위치 없음(늘 동작). 권한: `site.read`(메뉴 `visitHealth`). 서버 호출은 `useVisitAnalyticsApi().health`(`GET /api/admin/analytics/visits/health` — 크론 7개, 실패 `detail` = 「예외 이름 (SQLSTATE x) — 묶음 이름」).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <VisitStatsHeader :title="t('menu.visitHealth')" :subtitle="t('visitAnalytics.health.subtitle')" :as-of="h?.asOf ?? null" :as-of-note="t('visitAnalytics.health.window')" :busy="health.loading.value" :range="false" @refresh="load" />

        <EmptyState v-if="!allowed" icon="pi pi-lock" :title="t('dashboard.forbiddenTitle')" :description="t('visitAnalytics.forbiddenBody')" />
        <AppGadget v-else-if="health.error.value" :title="t('menu.visitHealth')">
            <ErrorState :code="health.error.value.status === 403 ? '403' : '!'" :title="t('visitAnalytics.health.loadFailed')" :description="health.error.value.message" :retry="health.error.value.status !== 403" :retrying="health.loading.value" @retry="load" />
        </AppGadget>
        <template v-else-if="!h">
            <Skeleton height="7rem" border-radius="0.75rem" />
            <Skeleton height="14rem" border-radius="0.75rem" />
        </template>

        <template v-else>
            <!-- ① 상태 -->
            <section class="flex items-start gap-4 rounded-xl border px-5 py-4 transition-opacity" :class="[statusBox, { 'opacity-60': health.loading.value }]" role="status" :aria-label="t('visitAnalytics.health.statusLabel')">
                <span class="grid size-10 shrink-0 place-items-center rounded-full" :class="statusIconBox" aria-hidden="true"><i :class="statusIcon" class="text-lg" /></span>
                <div class="flex min-w-0 flex-1 flex-col gap-2">
                    <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                        <span class="inline-flex items-center justify-center rounded-full px-2 py-1 text-[12px] leading-none font-semibold" :class="statusBadge">{{ t(`visitAnalytics.health.status.${h.status}`) }}</span>
                        <p class="m-0 text-[15px] font-medium">{{ t(`visitAnalytics.health.headline.${h.status}`) }}</p>
                    </div>
                    <ul v-if="h.issues.length" class="m-0 flex list-none flex-col gap-1 p-0 text-[13px] leading-relaxed">
                        <li v-for="(issue, i) in h.issues" :key="i" class="flex items-start gap-2">
                            <i class="mt-1 text-[11px]" :class="issue.level === 'ERROR' ? 'pi pi-times-circle text-red-600 dark:text-red-400' : 'pi pi-exclamation-triangle text-amber-600 dark:text-amber-400'" aria-hidden="true" />
                            <span>
                                {{ issueText(issue) }}
                                <span class="text-muted-color">— {{ t(`visitAnalytics.health.fix.${issue.code}`) }}</span>
                            </span>
                        </li>
                    </ul>
                    <p v-else class="m-0 text-[13px] text-muted-color">{{ t('visitAnalytics.health.noIssues') }}</p>
                </div>
            </section>

            <!-- ② 서버별 기록 | 원본 보관 -->
            <div class="grid min-w-0 items-start gap-4 lg:grid-cols-3">
                <AppGadget :title="t('visitAnalytics.health.instancesTitle')" class="lg:col-span-2">
                    <template #title-extra><span class="hidden text-[12px] font-normal text-muted-color sm:inline">{{ t('visitAnalytics.health.instancesHint') }}</span></template>
                    <p v-if="!h.instances.length" class="m-0 py-2 text-[13px] text-muted-color">{{ t('visitAnalytics.health.instancesEmpty') }}</p>
                    <div v-else class="min-w-0 overflow-x-auto">
                        <table class="w-full border-collapse text-[13px]">
                            <thead>
                                <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colInstance') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colLastWrite') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 text-right font-normal">{{ t('visitAnalytics.health.colFlush') }}</th>
                                    <th scope="col" class="py-1.5 pr-2 text-right font-normal">{{ t('visitAnalytics.health.colDropped') }}</th>
                                    <th scope="col" class="py-1.5 text-right font-normal">{{ t('visitAnalytics.health.colIgnored') }}</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-(--line)">
                                <tr v-for="r in h.instances" :key="r.instance">
                                    <td class="py-2 pr-2 font-mono text-[12px]">{{ r.instance }}</td>
                                    <td class="py-2 pr-2 whitespace-nowrap">
                                        <span class="inline-flex items-center gap-1.5" :title="r.lastWriteAt ? time.full(r.lastWriteAt) : undefined">
                                            <span class="size-2 shrink-0 rounded-full" :class="freshDot(r.lastWriteAt)" aria-hidden="true" />{{ r.lastWriteAt ? time.ago(r.lastWriteAt) : t('visitAnalytics.health.never') }}
                                        </span>
                                    </td>
                                    <td class="py-2 pr-2 text-right whitespace-nowrap tabular-nums">
                                        {{ num(r.flushOk) }}<span class="text-muted-color"> / </span><span :class="r.flushFail ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ num(r.flushFail) }}</span>
                                    </td>
                                    <td class="py-2 pr-2 text-right tabular-nums" :class="r.dropped ? 'font-semibold text-amber-700 dark:text-amber-400' : 'text-muted-color'">{{ num(r.dropped) }}</td>
                                    <td class="py-2 text-right whitespace-nowrap tabular-nums text-muted-color" :title="t('visitAnalytics.health.ignoredTip', { bot: num(r.ignoredBot), invalid: num(r.ignoredInvalid) })">
                                        {{ t('visitAnalytics.health.ignoredCell', { bot: num(r.ignoredBot), invalid: num(r.ignoredInvalid) }) }}
                                    </td>
                                </tr>
                            </tbody>
                        </table>
                        <p class="m-0 mt-2 text-[11px] leading-relaxed text-muted-color">{{ t('visitAnalytics.health.instancesNote') }}</p>
                    </div>
                </AppGadget>

                <AppGadget :title="t('visitAnalytics.health.rawTitle')">
                    <p v-if="!h.raw.rawEvents" class="m-0 mb-3 rounded-lg bg-surface-50 px-3 py-2 text-[12px] text-muted-color dark:bg-surface-900">{{ t('visitAnalytics.health.rawOff') }}</p>
                    <dl class="m-0 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-[13px]">
                        <dt class="text-muted-color">{{ t('visitAnalytics.health.rawRetention') }}</dt>
                        <dd class="m-0 text-right tabular-nums">{{ t('analytics.retentionDays', { n: h.raw.retentionDays }) }}</dd>
                        <dt class="text-muted-color">{{ t('visitAnalytics.health.rawOldest') }}</dt>
                        <dd class="m-0 text-right tabular-nums">{{ h.raw.oldestDay ? time.serviceDate(h.raw.oldestDay) : '—' }}</dd>
                        <dt class="text-muted-color">{{ t('visitAnalytics.health.rawPartitions') }}</dt>
                        <dd class="m-0 text-right tabular-nums">{{ t('visitAnalytics.health.rawPartitionsN', { n: h.raw.partitions }) }}</dd>
                        <dt class="text-muted-color">{{ t('visitAnalytics.health.rawRows') }}</dt>
                        <dd class="m-0 text-right tabular-nums">{{ h.raw.approxRows === null ? '—' : t('visitAnalytics.health.rawRowsN', { n: UtilVisitAnalytics.compact(h.raw.approxRows, locale) }) }}</dd>
                        <dt class="text-muted-color">{{ t('visitAnalytics.health.rawNext') }}</dt>
                        <dd class="m-0 text-right">
                            <span class="inline-flex items-center gap-1" :class="h.raw.nextPartitionReady ? 'text-green-700 dark:text-green-400' : 'text-amber-700 dark:text-amber-400'">
                                <i :class="h.raw.nextPartitionReady ? 'pi pi-check' : 'pi pi-exclamation-triangle'" class="text-[11px]" aria-hidden="true" />{{ h.raw.nextPartitionReady ? t('visitAnalytics.health.rawNextReady') : t('visitAnalytics.health.rawNextMissing') }}
                            </span>
                        </dd>
                    </dl>
                </AppGadget>
            </div>

            <!-- ③ 크론 -->
            <AppGadget :title="t('visitAnalytics.health.cronsTitle')">
                <template #title-extra><span class="hidden text-[12px] font-normal text-muted-color sm:inline">{{ t('visitAnalytics.health.cronsHint') }}</span></template>
                <template v-if="canBatch" #actions>
                    <NuxtLink to="/settings/batch-logs" class="inline-flex items-center gap-1 text-[12px] font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                        {{ t('visitAnalytics.health.allLogs') }}<i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                    </NuxtLink>
                </template>
                <div class="min-w-0 overflow-x-auto">
                    <table class="w-full border-collapse text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colJob') }}</th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colSchedule') }}</th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colLastRun') }}</th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colResult') }}</th>
                                <th scope="col" class="py-1.5 pr-2 text-right font-normal">{{ t('visitAnalytics.health.colProcessed') }}</th>
                                <th scope="col" class="py-1.5 font-normal">{{ t('visitAnalytics.health.colMessage') }}</th>
                                <th v-if="canBatch" scope="col" class="w-14 py-1.5 font-normal"><span class="sr-only">{{ t('visitAnalytics.health.colLog') }}</span></th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-(--line)">
                            <tr v-for="c in h.crons" :key="c.job">
                                <td class="py-2 pr-2">
                                    <span class="flex min-w-0 flex-col">
                                        <span class="truncate">{{ jobName(c.job) }}</span>
                                        <span class="truncate font-mono text-[11px] text-muted-color">{{ c.job }}</span>
                                    </span>
                                </td>
                                <td class="py-2 pr-2 whitespace-nowrap text-muted-color">{{ UtilBatchMessages.scheduleText(t, c.cron) ?? c.cron ?? '—' }}</td>
                                <td class="py-2 pr-2 whitespace-nowrap" :title="c.lastRunAt ? time.full(c.lastRunAt) : undefined">
                                    <span :class="c.late ? 'font-medium text-amber-700 dark:text-amber-400' : ''">{{ c.lastRunAt ? time.ago(c.lastRunAt) : t('visitAnalytics.health.never') }}</span>
                                    <span v-if="c.late" class="ml-1 text-[11px] text-amber-700 dark:text-amber-400">· {{ t('visitAnalytics.health.late') }}</span>
                                </td>
                                <td class="py-2 pr-2"><Tag v-if="c.lastStatus" :value="UtilBatchMessages.statusLabel(t, c.lastStatus)" :icon="UtilBatch.statusIcon(c.lastStatus)" :severity="UtilBatch.statusSeverity(c.lastStatus)" /><span v-else class="text-muted-color">—</span></td>
                                <td class="py-2 pr-2 text-right tabular-nums">{{ c.processed === null ? '—' : num(c.processed) }}</td>
                                <td class="max-w-60 truncate py-2 text-[12px]" :class="c.lastStatus === 'FAIL' ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :title="c.message ?? undefined">{{ c.message ?? '' }}</td>
                                <td v-if="canBatch" class="py-2 text-right">
                                    <NuxtLink :to="{ path: '/settings/batch-logs', query: { jobName: c.job } }" class="text-[12px] text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" :aria-label="t('visitAnalytics.health.logOf', { job: jobName(c.job) })">
                                        {{ t('visitAnalytics.health.colLog') }}
                                    </NuxtLink>
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </AppGadget>

            <!-- ④ 최근 기록 실패 -->
            <AppGadget :title="t('visitAnalytics.health.failuresTitle')">
                <p v-if="!h.failures.length" class="m-0 flex items-center gap-2 py-1 text-[13px] text-muted-color">
                    <i class="pi pi-check-circle text-[12px] text-green-600 dark:text-green-400" aria-hidden="true" />{{ t('visitAnalytics.health.failuresEmpty') }}
                </p>
                <div v-else class="min-w-0 overflow-x-auto">
                    <table class="w-full border-collapse text-[13px]">
                        <thead>
                            <tr class="border-b border-(--line) text-left text-[11px] text-muted-color">
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colAt') }}</th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colInstance') }}</th>
                                <th scope="col" class="py-1.5 pr-2 font-normal">{{ t('visitAnalytics.health.colReason') }}</th>
                                <th scope="col" class="py-1.5 font-normal">{{ t('visitAnalytics.health.colDetail') }}</th>
                            </tr>
                        </thead>
                        <tbody class="divide-y divide-(--line)">
                            <tr v-for="(f, i) in h.failures" :key="i">
                                <td class="py-2 pr-2 whitespace-nowrap tabular-nums">{{ time.full(f.at) }}</td>
                                <td class="py-2 pr-2 font-mono text-[12px]">{{ f.instance }}</td>
                                <td class="py-2 pr-2 whitespace-nowrap">{{ reasonName(f.reason) }}</td>
                                <td class="max-w-72 truncate py-2 font-mono text-[12px] text-muted-color" :title="f.detail ?? undefined">{{ f.detail ?? '' }}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </AppGadget>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { VisitHealthIssue, VisitHealthStatus } from '~/composables/analytics/api/useVisitAnalyticsApi.types';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';
import UtilVisitAnalytics from '~/utils/util-visit-analytics';

const { t, te, locale } = useI18n();
useSeoMeta({ title: () => `${t('menu.visitHealth')} · ${t('menu.visitStats')}` });

const api = useVisitAnalyticsApi();
const displayTime = useDisplayTime();
const time = computed(() => displayTime.value);
const { can } = useAdminPermission();
const allowed = computed(() => can('site.read'));
/** 배치 로그 링크는 배치 권한이 있을 때만 */
const canBatch = computed(() => can('batch.read'));

const health = useVisitLoad(api.health);
const h = computed(() => health.data.value);

function load(): void {
    void health.load();
}

onMounted(() => {
    if (allowed.value) load();
});

function num(n: number): string {
    return UtilVisitAnalytics.num(n, locale.value);
}

/** 크론 이름 — 아는 배치는 이름, 모르는 배치는 키 그대로 */
function jobName(job: string): string {
    const key = `visitAnalytics.health.job.${job}`;
    return te(key) ? t(key) : job;
}

/** 실패 사유 코드 → 이름(모르는 코드는 그대로) */
function reasonName(reason: string): string {
    const key = `visitAnalytics.health.reason.${reason}`;
    return te(key) ? t(key) : reason;
}

/** 문제 한 줄의 문장 — 코드마다 문장 키, 이름·숫자·시각은 칸에서 */
function issueText(issue: VisitHealthIssue): string {
    return t(`visitAnalytics.health.issue.${issue.code}`, {
        instance: issue.instance ?? '',
        job: issue.job ? jobName(issue.job) : '',
        n: issue.count === null ? '' : num(issue.count),
        since: issue.since ? time.value.ago(issue.since) : '',
    });
}

/** 마지막 기록의 신선함 — 2분 안 초록 · 10분 안 노랑 · 넘거나 없으면 빨강 */
function freshDot(at: string | null): string {
    if (!at) return 'bg-red-500';
    const minutes = (Date.now() - Date.parse(at)) / 60_000;
    if (minutes <= 2) return 'bg-green-500';
    if (minutes <= 10) return 'bg-amber-500';
    return 'bg-red-500';
}

const TONE: Record<VisitHealthStatus, { box: string; iconBox: string; icon: string; badge: string }> = {
    OK: {
        box: 'border-green-200 bg-green-50/60 dark:border-green-900 dark:bg-green-950/30',
        iconBox: 'bg-green-100 text-green-700 dark:bg-green-900/60 dark:text-green-300',
        icon: 'pi pi-check-circle',
        badge: 'bg-green-600 text-white dark:bg-green-500 dark:text-green-950',
    },
    DELAYED: {
        box: 'border-amber-200 bg-amber-50/60 dark:border-amber-900 dark:bg-amber-950/30',
        iconBox: 'bg-amber-100 text-amber-700 dark:bg-amber-900/60 dark:text-amber-300',
        icon: 'pi pi-exclamation-triangle',
        badge: 'bg-amber-500 text-amber-950',
    },
    STOPPED: {
        box: 'border-red-200 bg-red-50/60 dark:border-red-900 dark:bg-red-950/30',
        iconBox: 'bg-red-100 text-red-700 dark:bg-red-900/60 dark:text-red-300',
        icon: 'pi pi-times-circle',
        badge: 'bg-red-600 text-white dark:bg-red-500 dark:text-red-950',
    },
    OFF: {
        box: 'border-(--line) bg-(--p-content-background)',
        iconBox: 'bg-surface-100 text-muted-color dark:bg-surface-800',
        icon: 'pi pi-power-off',
        badge: 'bg-surface-200 text-color dark:bg-surface-700',
    },
};

const tone = computed(() => TONE[h.value?.status ?? 'OFF']);
const statusBox = computed(() => tone.value.box);
const statusIconBox = computed(() => tone.value.iconBox);
const statusIcon = computed(() => tone.value.icon);
const statusBadge = computed(() => tone.value.badge);
</script>
