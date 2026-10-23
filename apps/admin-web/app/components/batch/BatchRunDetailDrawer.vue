<template>
    <!--
        실행 상세 서랍 — 배치 로그 행·배치 관리의 「상세 보기」가 같은 화면에서 연다(화면 전환 없음). 늘 오른쪽, 폭 60rem(좁은 화면은 화면 폭). 열린 실행은 주소 `?run=<logId>` 에 남는다.
        상태·서버 호출·다시 읽기는 화면이 만든 `useBatchRunDetail` 이 하고(`ctl`), 여기는 그리기만 한다(`components.md` — 부품은 서버를 부르지 않는다).
        머리: 이름 · 실행 번호 · (서랍 안에서 옮겨 왔으면) 「이전 실행」 · 단추 줄 — 「지금 실행」(+ 「파라미터 지정 실행」, 목록과 같은 확인·요청·잠금 = `useBatchRunAction`) · 「실패 지점부터 다시」 · 「실패 건만 다시」 · 「다시 조회」.
        본문 차례: 요청 상태 띠 → 실행 정보 → 건수 · 파라미터 · 이어진 실행 → 단계별 진행(실행 중 강조, 안 돈 단계 흐리게) → 실패 건(미해결/해결/전체) → 오류 사유.
        서랍 안의 실행 링크(이어진 실행 · 해결한 실행 · 새 실행)는 화면을 옮기지 않고 서랍 내용을 그 실행으로 바꾼다(`ctl.follow`, 「이전 실행」으로 돌아온다).
        DB 배치(`source: POSTGRESQL`)는 단계 1개·처리 건수만이고 실패 건 구획은 안내만 한다(덩어리 실패는 오류 사유로).
    -->
    <Drawer
        :visible="ctl.visible"
        position="right"
        :aria-labelledby="titleId"
        :style="{ width: '60rem', maxWidth: '100vw' }"
        @update:visible="(v: boolean) => { if (!v) ctl.close(); }"
    >
        <template #header>
            <div class="flex min-w-0 flex-1 flex-col gap-2">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <Button
                        v-if="ctl.trail.length"
                        v-tooltip.bottom="t('batch.drawerBack')"
                        icon="pi pi-arrow-left"
                        size="small"
                        severity="secondary"
                        variant="text"
                        rounded
                        :aria-label="t('batch.drawerBack')"
                        @click="ctl.back()"
                    />
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ d?.displayName ?? t('batch.detailTitle') }}</h2>
                    <span v-if="ctl.logId" class="text-[12px] tabular-nums text-muted-color">{{ t('batch.runNo', { id: ctl.logId }) }}</span>
                    <Tag v-if="d" :value="UtilBatchMessages.statusLabel(t, d.status)" :icon="UtilBatch.statusIcon(d.status)" :severity="UtilBatch.statusSeverity(d.status)" />
                </div>
                <div v-if="d" class="flex flex-wrap items-center gap-1.5">
                    <span v-tooltip.bottom="runLock" class="inline-flex">
                        <Button
                            :label="runLabel"
                            :icon="busyJob ? (ctl.job?.running ? 'pi pi-spin pi-spinner' : 'pi pi-hourglass') : 'pi pi-play'"
                            size="small"
                            :disabled="!ctl.job || !!runLock"
                            :loading="!!ctl.job && ctl.run.requesting === ctl.job.jobName"
                            @click="ctl.runNow()"
                        />
                    </span>
                    <span v-if="ctl.job && UtilBatch.hasRunOptions(ctl.job)" v-tooltip.bottom="runLock ?? t('batch.runOptions')" class="inline-flex">
                        <Button
                            icon="pi pi-sliders-h"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            :aria-label="t('batch.runOptionsLabel', { name: d.displayName })"
                            :disabled="!!runLock || ctl.run.requesting === ctl.job.jobName"
                            @click="ctl.runOptions()"
                        />
                    </span>
                    <span v-if="showResume" v-tooltip.bottom="resumeLock" class="inline-flex">
                        <Button :label="t('batch.resume')" icon="pi pi-step-forward" size="small" severity="secondary" variant="outlined" :disabled="!!resumeLock" :loading="ctl.acting === 'resume'" @click="ctl.resume()" />
                    </span>
                    <span v-if="showRetry" v-tooltip.bottom="retryLock" class="inline-flex">
                        <Button
                            :label="d.unresolvedFailureCount ? t('batch.retryFailures', { n: d.unresolvedFailureCount }) : t('batch.retryFailuresNone')"
                            icon="pi pi-replay"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            :disabled="!!retryLock"
                            :loading="ctl.acting === 'retry'"
                            @click="ctl.retry()"
                        />
                    </span>
                    <Button :label="t('batch.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :loading="ctl.loading" @click="ctl.refresh()" />
                </div>
            </div>
        </template>

        <div v-if="ctl.loading && !d" class="flex flex-col gap-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton height="9rem" />
            <Skeleton height="14rem" />
        </div>

        <template v-else-if="!d">
            <Message v-if="ctl.loadError?.status === 403" severity="info" :closable="false" size="small">{{ t('batch.logDenied') }}</Message>
            <ErrorState v-else-if="ctl.loadError?.status === 404" code="404" :title="t('batch.logNotFound')" :description="ctl.loadError.message" />
            <ErrorState v-else :title="t('batch.logLoadFailed')" :description="ctl.loadError?.message ?? ''" retry :retrying="ctl.loading" @retry="ctl.refresh()" />
        </template>

        <div v-else class="flex flex-col gap-4">
            <!-- 요청 상태 띠 — 이 서랍에서 요청했거나 이 실행에 대한 요청이 대기·진행 중일 때 -->
            <Message v-if="ctl.request" :severity="UtilBatch.requestSeverity(ctl.request.status)" :closable="false" role="status">
                <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                    <i v-if="UtilBatch.isRequestActive(ctl.request)" :class="ctl.request.status === 'PENDING' ? 'pi pi-hourglass' : 'pi pi-spin pi-spinner'" aria-hidden="true" />
                    <span>{{ UtilBatchMessages.requestLine(t, ctl.request) }}</span>
                    <span v-if="ctl.request.status === 'REJECTED' && ctl.request.message">— {{ ctl.request.message }}</span>
                    <button v-if="ctl.request.runId && ctl.request.runId !== d.logId" type="button" class="font-medium underline" @click="ctl.follow(ctl.request.runId)">
                        {{ t('batch.newRunLink', { id: ctl.request.runId }) }}
                    </button>
                </div>
                <div v-if="UtilBatch.isStuck(ctl.request, ctl.now)" class="mt-1 text-[12px]">{{ t('batch.stuckNotice') }}</div>
            </Message>

            <AppGadget :title="t('batch.runInfo')">
                <dl class="grid gap-x-6 gap-y-3 sm:grid-cols-2 lg:grid-cols-4">
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.result') }}</dt>
                        <dd class="mt-1 flex flex-wrap items-center gap-1.5">
                            <Tag :value="UtilBatchMessages.statusLabel(t, d.status)" :icon="UtilBatch.statusIcon(d.status)" :severity="UtilBatch.statusSeverity(d.status)" />
                            <span v-if="d.status === 'RUNNING'" class="text-[11px] text-muted-color">{{ t('batch.liveNote', { s: UtilBatch.POLL_MS / 1000 }) }}</span>
                        </dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.trigger') }}</dt>
                        <dd class="mt-1 flex flex-wrap items-center gap-1.5">
                            <Tag :value="UtilBatchMessages.triggerLabel(t, d.trigger)" :icon="d.trigger === 'MANUAL' ? 'pi pi-user' : 'pi pi-clock'" severity="secondary" />
                            <Tag v-if="UtilBatchMessages.runModeLabel(t, d.runMode)" :value="UtilBatchMessages.runModeLabel(t, d.runMode) ?? ''" :icon="UtilBatch.runModeIcon(d.runMode)" severity="warn" />
                            <span v-if="d.trigger === 'MANUAL'" class="text-[12px] text-muted-color">{{ d.operatorName ?? '—' }}</span>
                        </dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.where') }}</dt>
                        <dd class="mt-1"><BatchSourceTag :source="d.source" /></dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.nodeId') }}</dt>
                        <dd class="mt-1 text-[13px]">
                            <code v-if="d.nodeId">{{ d.nodeId }}</code>
                            <span v-else class="text-muted-color">—</span>
                        </dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.startedAt') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">{{ time.full(d.startedAt) }}</dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.finishedAt') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">{{ d.finishedAt ? time.full(d.finishedAt) : '—' }}</dd>
                    </div>
                    <div>
                        <dt class="text-[11px] text-muted-color">{{ t('batch.elapsed') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">{{ UtilBatchMessages.elapsed(t, d.startedAt, d.finishedAt) }}</dd>
                    </div>
                    <div v-if="d.scheduledAt">
                        <dt class="text-[11px] text-muted-color">{{ t('batch.scheduledAt') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">{{ time.full(d.scheduledAt) }}</dd>
                    </div>
                    <div v-if="d.requestId">
                        <dt class="text-[11px] text-muted-color">{{ t('batch.requestNo') }}</dt>
                        <dd class="mt-1 text-[13px] tabular-nums">#{{ d.requestId }}</dd>
                    </div>
                    <div v-if="d.summary" class="sm:col-span-2 lg:col-span-4">
                        <dt class="text-[11px] text-muted-color">{{ t('batch.summary') }}</dt>
                        <dd class="mt-1 text-[13px]">{{ d.summary }}</dd>
                    </div>
                </dl>
            </AppGadget>

            <div class="grid gap-4 lg:grid-cols-2">
                <AppGadget :title="t('batch.counts')">
                    <dl class="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-2 xl:grid-cols-4">
                        <div v-for="tile in countTiles" :key="tile.key" class="rounded-lg border border-(--line) px-3 py-2.5">
                            <dt class="text-[11px] text-muted-color">{{ tile.label }}</dt>
                            <dd class="mt-0.5 text-lg font-semibold tabular-nums" :class="tile.danger ? 'text-red-600 dark:text-red-400' : ''">{{ tile.value }}</dd>
                        </div>
                    </dl>
                    <p v-if="d.source === 'POSTGRESQL'" class="mt-2 text-[11px] text-muted-color">{{ t('batch.countsDbNote') }}</p>
                    <p v-else-if="d.checkpoint" class="mt-2 text-[12px] text-muted-color">
                        {{ t('batch.checkpoint') }} <code class="text-color">{{ d.checkpoint }}</code>
                    </p>
                </AppGadget>

                <div class="flex flex-col gap-4">
                    <AppGadget v-if="paramEntries.length" :title="t('batch.params')">
                        <dl class="flex flex-col gap-2">
                            <div v-for="entry in paramEntries" :key="entry.name" class="flex flex-wrap items-baseline gap-x-2">
                                <dt class="text-[12px] text-muted-color">{{ entry.label }}</dt>
                                <dd class="text-[13px]"><code>{{ entry.value }}</code></dd>
                            </div>
                        </dl>
                    </AppGadget>

                    <AppGadget v-if="d.resumedFromRunId || d.resumedByRunIds.length" :title="t('batch.related')">
                        <dl class="flex flex-col gap-2 text-[13px]">
                            <div v-if="d.resumedFromRunId" class="flex flex-wrap items-baseline gap-x-2">
                                <dt class="text-[12px] text-muted-color">{{ t('batch.resumedFrom') }}</dt>
                                <dd>
                                    <button type="button" class="text-primary hover:underline" @click="ctl.follow(d.resumedFromRunId)">{{ t('batch.runNo', { id: d.resumedFromRunId }) }}</button>
                                </dd>
                            </div>
                            <div v-if="d.resumedByRunIds.length" class="flex flex-wrap items-baseline gap-x-2">
                                <dt class="text-[12px] text-muted-color">{{ t('batch.resumedBy') }}</dt>
                                <dd class="flex flex-wrap gap-x-3">
                                    <button v-for="id in d.resumedByRunIds" :key="id" type="button" class="text-primary hover:underline" @click="ctl.follow(id)">{{ t('batch.runNo', { id }) }}</button>
                                </dd>
                            </div>
                        </dl>
                    </AppGadget>
                </div>
            </div>

            <AppGadget :title="t('batch.steps')">
                <ol class="flex flex-col gap-2">
                    <li
                        v-for="step in d.steps"
                        :key="step.name"
                        class="rounded-lg border px-3 py-2.5"
                        :class="[step.status === 'RUNNING' ? 'border-primary bg-emphasis' : 'border-(--line)', step.status === 'PENDING' ? 'opacity-50' : '']"
                        :aria-current="step.status === 'RUNNING' ? 'step' : undefined"
                    >
                        <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                            <span class="inline-flex size-6 shrink-0 items-center justify-center rounded-full border border-(--line) text-[11px] leading-none tabular-nums">{{ step.order }}</span>
                            <span class="text-[13px] font-medium">{{ step.label }}</span>
                            <Tag :value="UtilBatchMessages.stepStatusLabel(t, step.status)" :icon="UtilBatch.stepIcon(step.status)" :severity="UtilBatch.stepSeverity(step.status)" />
                            <span v-if="step.startedAt" class="ml-auto text-[12px] tabular-nums text-muted-color">
                                {{ time.full(step.startedAt) }} → {{ step.finishedAt ? time.clock(step.finishedAt) : '…' }} · {{ UtilBatchMessages.elapsed(t, step.startedAt, step.finishedAt) }}
                            </span>
                        </div>
                        <div v-if="hasStepCounts(step)" class="mt-1.5 flex flex-wrap gap-x-4 gap-y-1 pl-9 text-[12px] tabular-nums text-muted-color">
                            <span v-if="step.readCount != null">{{ t('batch.countRead') }} {{ step.readCount.toLocaleString() }}</span>
                            <span v-if="step.processedCount != null">{{ t('batch.countProcessed') }} {{ step.processedCount.toLocaleString() }}</span>
                            <span v-if="step.failedCount != null" :class="step.failedCount ? 'text-red-600 dark:text-red-400' : ''">{{ t('batch.countFailed') }} {{ step.failedCount.toLocaleString() }}</span>
                            <span v-if="step.skippedCount != null">{{ t('batch.countSkipped') }} {{ step.skippedCount.toLocaleString() }}</span>
                            <span v-if="step.checkpoint">{{ t('batch.checkpoint') }} <code class="text-color">{{ step.checkpoint }}</code></span>
                        </div>
                    </li>
                </ol>
            </AppGadget>

            <AppGadget :title="t('batch.failures')">
                <template v-if="d.source !== 'POSTGRESQL'" #title-extra>
                    <Tag v-if="d.unresolvedFailureCount" :value="`${t('batch.failuresUnresolved')} ${d.unresolvedFailureCount}`" severity="danger" />
                </template>
                <template v-if="d.source !== 'POSTGRESQL'" #actions>
                    <SelectButton
                        :model-value="ctl.failFilter"
                        :options="failFilterOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :aria-label="t('batch.failureState')"
                        @update:model-value="(v: BatchFailFilter) => ctl.setFailFilter(v)"
                    />
                </template>

                <p v-if="d.source === 'POSTGRESQL'" class="text-[12px] leading-relaxed text-muted-color">{{ t('batch.failuresDbNote') }}</p>
                <DataTable
                    v-else
                    :value="ctl.failures"
                    data-key="failureId"
                    lazy
                    paginator
                    :first="ctl.failFirst"
                    :rows="ctl.failSize"
                    :total-records="ctl.failTotal"
                    :loading="ctl.failBusy"
                    :size="prefs.density === 'compact' ? 'small' : undefined"
                    :always-show-paginator="false"
                    :current-page-report-template="totalTemplate"
                    paginator-template="PrevPageLink PageLinks NextPageLink CurrentPageReport"
                    scrollable
                    @page="(e: { first: number }) => ctl.setFailPage(e.first)"
                >
                    <template #empty>
                        <ErrorState v-if="ctl.failError" :title="t('batch.failuresLoadFailed')" :description="ctl.failError.message" retry :retrying="ctl.failBusy" @retry="ctl.loadFailures()" />
                        <div v-else-if="ctl.failBusy" class="h-16" aria-hidden="true" />
                        <EmptyState v-else icon="pi pi-check-circle" :title="failEmptyTitle" />
                    </template>

                    <Column :header="t('batch.failureAt')" style="width: 11rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ time.full(data.occurredAt) }}</span>
                        </template>
                    </Column>
                    <Column :header="t('batch.failureStep')" style="width: 7rem">
                        <template #body="{ data }">
                            <span class="text-[12px]">{{ data.stepLabel }}</span>
                        </template>
                    </Column>
                    <Column :header="t('batch.failureTarget')" style="min-width: 9rem">
                        <template #body="{ data }">
                            <div class="flex flex-wrap items-baseline gap-x-1.5 text-[12px]">
                                <span class="text-muted-color">{{ data.targetLabel }}</span>
                                <code>{{ data.targetId }}</code>
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('batch.failureCode')" style="width: 11rem">
                        <template #body="{ data }">
                            <code class="text-[11px]">{{ data.errorCode }}</code>
                        </template>
                    </Column>
                    <Column :header="t('batch.message')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <span v-tooltip.top="data.message" class="block truncate text-[12px]">{{ data.message }}</span>
                        </template>
                    </Column>
                    <Column :header="t('batch.failureAttempts')" style="width: 4.5rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums">{{ data.attempts }}</span>
                        </template>
                    </Column>
                    <Column :header="t('batch.failureState')" style="width: 9rem">
                        <template #body="{ data }">
                            <div class="flex flex-col items-start gap-0.5">
                                <Tag :value="data.resolved ? t('batch.failuresResolved') : t('batch.failuresUnresolved')" :severity="data.resolved ? 'success' : 'warn'" />
                                <button
                                    v-if="data.resolvedByRunId"
                                    v-tooltip.top="t('batch.failureResolvedBy', { id: data.resolvedByRunId })"
                                    type="button"
                                    class="text-[11px] text-primary hover:underline"
                                    @click="ctl.follow(data.resolvedByRunId)"
                                >
                                    {{ t('batch.failureResolvedLink', { id: data.resolvedByRunId }) }}
                                </button>
                            </div>
                        </template>
                    </Column>
                </DataTable>
            </AppGadget>

            <AppGadget v-if="d.errorMessage" :title="t('batch.errorReason')">
                <pre class="whitespace-pre-wrap break-words font-mono text-[12px] leading-relaxed text-red-600 dark:text-red-400">{{ d.errorMessage }}</pre>
            </AppGadget>
        </div>
    </Drawer>
</template>

<script setup lang="ts">
import type { BatchRunStepView } from '~/composables/batch/api/useBatchApi.types';
import type { BatchFailFilter, BatchRunDetail } from '~/composables/batch/useBatchRunDetail';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

interface Props {
    /** 화면이 만든 실행 상세 상태(`useBatchRunDetail`) — 열림·내용·다시 읽기·다시 실행을 모두 여기에 맡긴다 */
    ctl: BatchRunDetail;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const titleId = useId();

const d = computed(() => props.ctl.detail);

const busyJob = computed(() => !!props.ctl.job && UtilBatch.isBusy(props.ctl.job));
const runLock = computed(() => props.ctl.run.lockReason(props.ctl.job));
const runLabel = computed(() => {
    if (props.ctl.job?.running) return t('batch.running');
    if (props.ctl.job?.pendingRequest) return t('batch.pending');
    return t('batch.runNow');
});

/** 「실패 지점부터 다시」는 체크포인트 재시작을 지원하는 배치에만(배치 한 줄을 못 받았으면 서버가 된다고 할 때만) */
const showResume = computed(() => (props.ctl.job ? props.ctl.job.resumable : !!d.value?.canResume));
/** 「실패 건만 다시」는 건별 실패를 남긴 앱 서버 배치 실행에만 — 건별 실패를 기록하지 않는 작업(예: 고아 파일 정리)·DB 배치는 단추를 두지 않는다 */
const showRetry = computed(() => d.value?.source === 'JAVA' && (d.value.unresolvedFailureCount > 0 || (d.value.failedCount ?? 0) > 0));
const resumeLock = computed(() => (d.value ? UtilBatchMessages.resumeLockReason(t, d.value, props.ctl.run.canRun) : undefined));
const retryLock = computed(() => (d.value ? UtilBatchMessages.retryLockReason(t, d.value, props.ctl.run.canRun) : undefined));

const countTiles = computed(() => {
    const run = d.value;
    if (!run) return [];
    const show = (n: number | null) => (n == null ? '—' : n.toLocaleString());
    return [
        { key: 'read', label: t('batch.countRead'), value: show(run.readCount), danger: false },
        { key: 'processed', label: t('batch.countProcessed'), value: show(run.processedCount), danger: false },
        { key: 'failed', label: t('batch.countFailed'), value: show(run.failedCount), danger: !!run.failedCount },
        { key: 'skipped', label: t('batch.countSkipped'), value: show(run.skippedCount), danger: false },
    ];
});

/** 파라미터 — 이름은 배치 정의의 라벨(서버 글), 정의에 없는 이름은 키 그대로 */
const paramEntries = computed(() =>
    Object.entries(d.value?.params ?? {}).map(([name, value]) => ({ name, value, label: props.ctl.job?.params.find((p) => p.name === name)?.label ?? name })),
);

const failFilterOptions = computed(() => [
    { label: t('batch.failuresUnresolved'), value: 'UNRESOLVED' },
    { label: t('batch.failuresResolved'), value: 'RESOLVED' },
    { label: t('common.all'), value: 'ALL' },
]);
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const failEmptyTitle = computed(() => {
    if (props.ctl.failFilter === 'UNRESOLVED') return t('batch.failuresEmptyUnresolved');
    if (props.ctl.failFilter === 'RESOLVED') return t('batch.failuresEmptyResolved');
    return t('batch.failuresEmpty');
});

function hasStepCounts(step: BatchRunStepView): boolean {
    return step.readCount != null || step.processedCount != null || step.failedCount != null || step.skippedCount != null || !!step.checkpoint;
}
</script>
