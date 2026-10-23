<template>
    <!--
        배치 관리 — hamonica3 의 `batches.vue`(설정 › 애플리케이션) 를 옮긴 것이다. 서버에 등록된 배치를 한 목록에 담고, 각 행에 어디서 도는지(앱 서버 / DB pg_cron)·주기·마지막 실행 요약·다음 실행 예정을 보인다.
        DB 배치는 pg_cron 이 없거나 미등록이면 `available=false` — 행을 흐리게 하고 서버가 준 이유를 그대로 보이며 켜기/끄기·주기 변경·지금 실행을 잠근다.
        행의 「로그 보기」는 그 배치로 걸러진 배치 로그 화면(`/settings/batch-logs?jobName=…`)으로, 마지막 실행의 「상세 보기」는 화면을 옮기지 않고 이 화면의 실행 상세 서랍(`BatchRunDetailDrawer`, 주소 `?run=<logId>`)을 연다.
          서랍 머리에도 「지금 실행」이 있다 — 확인·요청·잠금은 목록과 같은 `useBatchRunAction` 이고, 서랍에서 넣은 요청도 목록의 대기 표시·다시 읽기로 이어진다.
        「지금 실행」은 요청 표 방식이다(`docs/domain/commerce.md` 15-2): 요청을 넣으면 바로 「요청했습니다 · 요청 #번호」이고, 배치를 켠 서버가 집어 실행한다.
          · 행에 대기 중/실행 중을 보이고 그동안 지금 실행을 잠근다. 대기·실행 중인 배치가 하나라도 있으면 3초마다 목록을 다시 읽고, 없거나 화면을 떠나면 멈춘다.
          · 끝나면 결과 토스트(성공/실패/할 일 없음 + 요약 — 서버 글). 거절되면 그 사유. 대기가 30초를 넘으면 행에 「배치를 켠 서버가 아직 집지 않았다」 안내.
          · 파라미터를 받거나 단계를 고를 수 있는 배치는 지금 실행 옆 단추로 「파라미터 지정 실행」(`BatchRunDialog`) — 400 `fields.params.<이름>`·`fields.steps` 는 그 칸 아래.
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('batch.title')" :subtitle="t('batch.subtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다. 이 단추가 하는 일은 목록을 다시 받아 오는 것이다 -->
            <template #actions>
                <Button :label="t('batch.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload()" />
            </template>
        </PageTitle>

        <DataTable :value="jobs" data-key="jobName" :loading="busy" :row-class="rowClass" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable scroll-height="flex" class="min-h-0 flex-1">
            <template #empty>
                <ErrorState v-if="loadError" :title="t('batch.jobsLoadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload()" />
                <div v-else-if="busy" class="h-32" aria-hidden="true" />
                <EmptyState v-else :title="t('batch.emptyJobs')" :description="t('batch.emptyJobsHint')" />
            </template>

            <Column :header="t('batch.batch')" style="min-width: 16rem">
                <template #body="{ data }">
                    <!-- 폭 상한이 있어야 긴 설명이 말줄임으로 잘린다 — 없으면 설명 길이만큼 칸이 늘어나 지금 실행 단추가 화면 밖으로 밀린다 -->
                    <div class="min-w-0 max-w-[22rem]">
                        <div class="truncate text-[13px]">{{ data.displayName }}</div>
                        <div v-if="data.description" class="truncate text-[11px] leading-relaxed text-muted-color" :title="data.description">
                            {{ data.description }}
                        </div>
                        <!-- 쓸 수 없는 이유는 서버가 요청 언어로 풀어 준 글 그대로 -->
                        <div v-if="!data.available" class="mt-0.5 flex items-start gap-1.5 text-[11px] leading-relaxed text-amber-700 dark:text-amber-400">
                            <i class="pi pi-exclamation-triangle mt-0.5 shrink-0 text-[11px]" aria-hidden="true" />
                            <span><strong>{{ t('batch.unavailable') }}</strong>{{ data.unavailableReason ? ` — ${data.unavailableReason}` : '' }}</span>
                        </div>
                    </div>
                </template>
            </Column>

            <Column :header="t('batch.where')" style="width: 9rem">
                <template #body="{ data }">
                    <BatchSourceTag :source="data.source" />
                </template>
            </Column>

            <Column :header="t('batch.schedule')" style="min-width: 15rem">
                <template #body="{ data }">
                    <!-- 사람이 읽는 설명 + 원문(cron) + 시간대. 설명으로 못 읽는 식은 원문만. 쓸 수 없는 DB 배치는 기본 cron(모르면 빈 글)이라 없으면 「—」 -->
                    <div class="flex items-start justify-between gap-1">
                        <div class="min-w-0">
                            <div v-if="UtilBatchMessages.scheduleText(t, data.schedule)" class="flex items-center gap-1.5 whitespace-nowrap text-[12px]">
                                <i class="pi pi-clock text-[11px] text-muted-color" aria-hidden="true" />
                                {{ UtilBatchMessages.scheduleText(t, data.schedule) }}
                            </div>
                            <code class="whitespace-nowrap text-[11px] text-muted-color">{{ data.schedule || '—' }}</code>
                            <div v-if="data.scheduleZone" class="whitespace-nowrap text-[11px] text-muted-color">{{ data.scheduleZone }}</div>
                        </div>
                        <span v-tooltip.top="lockReason(data, canManage, t('batch.noManageReason')) ?? t('batch.scheduleChange')" class="inline-flex shrink-0">
                            <Button
                                icon="pi pi-pencil"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :aria-label="t('batch.scheduleChangeLabel', { name: data.displayName })"
                                :disabled="!canManage || !data.available"
                                @click="openSchedule(data)"
                            />
                        </span>
                    </div>
                </template>
            </Column>

            <Column :header="t('batch.lastRun')" style="min-width: 16rem">
                <template #body="{ data }">
                    <div class="flex min-w-0 max-w-[20rem] flex-col gap-1">
                        <!-- 실행 중 — 마지막 실행이 곧 지금 도는 실행이다. 요청으로 시작했으면 요청 번호를 같이 -->
                        <div v-if="data.running" class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                            <Tag :value="data.pendingRequest ? t('batch.runningRequest', { id: data.pendingRequest.requestId }) : t('batch.running')" icon="pi pi-spin pi-spinner" severity="info" />
                            <span v-if="data.lastRunAt" class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.lastRunAt) }}</span>
                            <button v-if="runningRunId(data)" type="button" class="text-[12px] text-primary hover:underline" :aria-label="t('batch.viewDetailLabel', { name: data.displayName })" @click="runDetail.show(runningRunId(data))">
                                {{ t('batch.viewDetail') }}
                            </button>
                        </div>

                        <template v-else>
                            <!-- 대기 중 — 아직 배치 서버가 안 집었다. 30초가 넘으면 운영자 말로 이유를 짚는다 -->
                            <div v-if="data.pendingRequest" class="flex flex-col gap-0.5">
                                <!-- 요청은 집혔는데 실행 줄이 아직 안 보일 수 있다(DB 배치는 실행이 끝날 때까지 요청만 RUNNING) — 요청 상태로 말한다 -->
                                <Tag
                                    v-if="data.pendingRequest.status === 'RUNNING'"
                                    class="self-start"
                                    :value="t('batch.runningRequest', { id: data.pendingRequest.requestId })"
                                    icon="pi pi-spin pi-spinner"
                                    severity="info"
                                />
                                <Tag v-else class="self-start" :value="t('batch.pendingRequest', { id: data.pendingRequest.requestId })" icon="pi pi-hourglass" severity="warn" />
                                <div v-if="UtilBatch.isStuck(data.pendingRequest, now)" class="flex items-start gap-1.5 text-[11px] leading-relaxed text-amber-700 dark:text-amber-400" role="status">
                                    <i class="pi pi-exclamation-triangle mt-0.5 shrink-0 text-[11px]" aria-hidden="true" />
                                    <span>{{ t('batch.stuckNotice') }}</span>
                                </div>
                            </div>

                            <div v-if="data.lastRunAt" class="min-w-0">
                                <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                                    <Tag :value="UtilBatchMessages.statusLabel(t, data.lastStatus)" :icon="UtilBatch.statusIcon(data.lastStatus)" :severity="UtilBatch.statusSeverity(data.lastStatus)" />
                                    <span class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.lastRunAt) }}</span>
                                    <span v-if="data.lastAffectedCount != null" class="text-[12px] tabular-nums text-muted-color">· {{ t('batch.countCases', { n: data.lastAffectedCount }) }}</span>
                                    <button v-if="data.lastRunId" type="button" class="text-[12px] text-primary hover:underline" :aria-label="t('batch.viewDetailLabel', { name: data.displayName })" @click="runDetail.show(data.lastRunId)">
                                        {{ t('batch.viewDetail') }}
                                    </button>
                                </div>
                                <div v-if="data.lastMessage" v-tooltip.top="data.lastMessage" class="mt-0.5 truncate text-[11px]" :class="data.lastStatus === 'FAIL' ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                                    {{ data.lastMessage }}
                                </div>
                            </div>
                            <span v-else-if="!data.pendingRequest" class="text-[12px] text-muted-color">{{ t('batch.neverRan') }}</span>
                        </template>
                    </div>
                </template>
            </Column>

            <Column :header="t('batch.nextRun')" style="width: 11rem">
                <template #body="{ data }">
                    <span v-if="data.nextRunAt" class="text-[12px] tabular-nums">{{ time.full(data.nextRunAt) }}</span>
                    <span v-else class="text-[12px] text-muted-color">{{ data.enabled ? t('batch.nextRunNone') : t('batch.nextRunOff') }}</span>
                </template>
            </Column>

            <Column :header="t('batch.enabled')" style="width: 6rem">
                <template #body="{ data }">
                    <!-- 켜고 끌 권한이 없거나 쓸 수 없는 DB 배치면 잠그고 이유를 보인다. 비활성 요소는 마우스 이벤트를 안 받으므로 툴팁은 감싼 쪽에 건다 -->
                    <span v-tooltip.top="lockReason(data, canManage, t('batch.noManageReason'))" class="inline-flex">
                        <ToggleSwitch
                            :model-value="data.enabled"
                            :disabled="!canManage || !data.available || toggling === data.jobName"
                            :aria-label="t('batch.toggleLabel', { name: data.displayName })"
                            @update:model-value="(v: boolean) => onToggle(data, v)"
                        />
                    </span>
                </template>
            </Column>

            <Column header="" style="width: 18rem">
                <template #body="{ data }">
                    <!-- 단추 자리는 행마다 같다 — 파라미터 단추가 없는 행도 그 자리를 비워 두고, 지금 실행은 글자가 바뀌어도(대기 중·실행 중) 폭이 같아 위아래 줄이 맞는다 -->
                    <div class="flex items-center justify-end gap-1.5">
                        <!-- 파라미터를 받거나 단계를 고를 수 있는 배치만 — 아이콘 단추라 이름은 툴팁·aria 로 -->
                        <span class="inline-flex w-8 shrink-0 justify-center">
                            <span v-if="UtilBatch.hasRunOptions(data)" v-tooltip.top="runAction.lockReason(data) ?? t('batch.runOptions')" class="inline-flex">
                                <Button
                                    icon="pi pi-sliders-h"
                                    size="small"
                                    severity="secondary"
                                    variant="text"
                                    rounded
                                    :aria-label="t('batch.runOptionsLabel', { name: data.displayName })"
                                    :disabled="!!runAction.lockReason(data) || runAction.requesting === data.jobName"
                                    @click="runAction.openOptions(data)"
                                />
                            </span>
                        </span>
                        <!-- 확인·요청·잠금 규칙은 실행 상세 서랍의 「지금 실행」과 같다(`useBatchRunAction`) -->
                        <span v-tooltip.top="runAction.lockReason(data)" class="inline-flex">
                            <Button
                                :label="runLabel(data)"
                                :icon="UtilBatch.isBusy(data) ? (data.running ? 'pi pi-spin pi-spinner' : 'pi pi-hourglass') : 'pi pi-play'"
                                size="small"
                                severity="secondary"
                                outlined
                                class="min-w-28 justify-center whitespace-nowrap"
                                :disabled="!!runAction.lockReason(data)"
                                :loading="runAction.requesting === data.jobName"
                                @click="runAction.runNow(data)"
                            />
                        </span>
                        <Button
                            :label="t('batch.viewLogs')"
                            :aria-label="t('batch.viewLogsLabel', { name: data.displayName })"
                            icon="pi pi-book"
                            size="small"
                            severity="secondary"
                            variant="text"
                            class="whitespace-nowrap"
                            @click="viewLogs(data)"
                        />
                    </div>
                </template>
            </Column>
        </DataTable>

        <p class="text-[11px] leading-relaxed text-muted-color">
            <span v-if="anyBusy" class="mr-1 inline-flex items-center gap-1 text-primary" role="status">
                <i class="pi pi-sync pi-spin text-[10px]" aria-hidden="true" />
                {{ t('batch.autoRefresh', { s: UtilBatch.POLL_MS / 1000 }) }}
            </span>
            <strong>{{ t('batch.runNowNote') }}</strong>
            {{ t('batch.manualRunNote') }}
            <NuxtLink to="/settings/batch-logs" class="text-primary hover:underline">
                {{ t('batch.batchLogsLink') }}
            </NuxtLink>
            {{ t('batch.remainsIn') }}
            {{ zoneNote }}
        </p>

        <BatchScheduleDialog v-model:visible="scheduleOpen" :job="scheduleTarget" :busy="scheduleBusy" :error="scheduleError" :field-errors="scheduleFieldErrors" @submit="onSchedule" />
        <BatchRunDialog
            v-model:visible="runAction.dialog.visible"
            :job="runAction.dialog.job"
            :busy="runAction.dialog.busy"
            :error="runAction.dialog.error"
            :field-errors="runAction.dialog.fieldErrors"
            @submit="runAction.submitOptions"
        />
        <BatchRunDetailDrawer :ctl="runDetail" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { BatchJobView, BatchRunRequestView } from '~/composables/batch/api/useBatchApi.types';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('batch.title') });

const api = useBatchApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다. 지금 실행(`batch.run`)은 `runAction` 이 본다 */
const canManage = computed(() => can('batch.manage'));

/**
 * 「지금 실행」·「파라미터 지정 실행」 — 실행 상세 서랍과 같은 확인·요청·잠금(`useBatchRunAction`). 받아들여진 요청은 목록(대기 표시·다시 읽기)과 서랍(요청 띠) 양쪽에 알린다.
 * 실행 상세는 같은 화면의 서랍이다(`?run=<logId>`).
 */
const runAction = useBatchRunAction({
    onRequested: (request) => {
        onRequested(request);
        runDetail.track(request);
    },
    onStale: async () => {
        await reload();
        if (runDetail.visible) runDetail.refresh();
    },
});
const runDetail = useBatchRunDetail(runAction);

const jobs = ref<BatchJobView[]>([]);
/** 주기는 배치가 도는 곳의 시간대로 읽힌다 — 값은 응답의 `scheduleZone` 에서 모은다(고정 문구를 두지 않는다) */
const zoneNote = computed(() => UtilBatchMessages.zoneNote(t, jobs.value));
const busy = ref(false);
const toggling = ref<string | null>(null);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다. 켜고 끄기·실행 실패는 토스트다 */
const loadError = ref<ApiError | null>(null);

/** 대기·실행 중인 배치가 있는가 — 있는 동안만 목록을 3초마다 다시 읽는다 */
const anyBusy = computed(() => jobs.value.some(UtilBatch.isBusy));
/** 대기 30초 판정의 기준 시각 — 다시 읽을 때마다 맞춘다 */
const now = ref(Date.now());
let pollTimer: ReturnType<typeof setTimeout> | null = null;
let disposed = false;
/** 이 화면에서 넣은 요청(배치 키 → 요청 번호) — 끝났을 때 거절인지 확인하려고 붙든다 */
const myRequests = new Map<string, string>();

/** 주기 변경 대화상자 — 열 때의 목록 한 줄을 붙들고, 서버 호출 결과(칸별 오류 포함)를 대화상자 안에 보인다 */
const scheduleOpen = ref(false);
const scheduleTarget = ref<BatchJobView | null>(null);
const scheduleBusy = ref(false);
const scheduleError = ref('');
const scheduleFieldErrors = ref<Record<string, string>>({});

onMounted(() => reload());
onBeforeUnmount(() => {
    disposed = true;
    stopPolling();
});

/** 쓸 수 없는 DB 배치는 행을 흐리게 한다 */
function rowClass(job: BatchJobView): string {
    return job.available ? '' : 'opacity-60';
}

/**
 * 단추가 잠긴 이유 — 권한이 없으면 그 문구를, 쓸 수 없는 DB 배치면 서버가 준 이유를 그대로. 잠기지 않았으면 undefined(툴팁 없음).
 * 권한이 우선이다(권한이 없으면 배치 상태를 알려 줄 일이 아니다).
 */
function lockReason(job: BatchJobView, allowed: boolean, noPermissionText: string): string | undefined {
    if (!allowed) return noPermissionText;
    if (!job.available) return job.unavailableReason || t('batch.unavailable');
    return undefined;
}

function runLabel(job: BatchJobView): string {
    if (job.running) return t('batch.running');
    if (job.pendingRequest) return t('batch.pending');
    return t('batch.runNow');
}

/** 지금 도는 실행의 로그 번호 — 요청으로 시작했으면 요청의 `runId`, 아니면 마지막 실행 */
function runningRunId(job: BatchJobView): string {
    return job.pendingRequest?.runId ?? job.lastRunId ?? '';
}

/**
 * 목록을 다시 받는다. `silent` 는 자동 다시 읽기 — 표 로딩 덮개를 띄우지 않고, 실패해도 지금 목록을 그대로 둔다(다음 차례에 다시 읽는다).
 */
async function reload(silent = false) {
    if (!silent) {
        busy.value = true;
        loadError.value = null;
    }
    try {
        applyJobs(await api.loadJobs());
    } catch (e) {
        if (!silent) {
            loadError.value = errorOf(e);
            jobs.value = [];
        }
    } finally {
        if (!silent) busy.value = false;
        now.value = Date.now();
        schedulePoll();
    }
}

/** 새 목록으로 바꾸고, 그 사이에 끝난 배치(바빴다가 한가해진 배치)를 알린다 */
function applyJobs(next: BatchJobView[]) {
    const before = jobs.value;
    jobs.value = next;
    for (const prev of before) {
        if (!UtilBatch.isBusy(prev)) continue;
        const after = next.find((j) => j.jobName === prev.jobName);
        if (!after || UtilBatch.isBusy(after)) continue;
        void announceFinished(after, myRequests.get(prev.jobName) ?? prev.pendingRequest?.requestId ?? null);
        myRequests.delete(prev.jobName);
    }
}

/**
 * 끝난 배치를 토스트로 — 요청으로 시작했으면 요청을 다시 읽어 거절인지 본다(거절이면 서버가 준 사유). 아니면 결과(성공/실패/할 일 없음 + 서버 요약).
 * 「상세 보기」는 토스트에 단추를 둘 수 없어(`useAppToast` 는 글만) 행의 마지막 실행 칸에 두고, 같은 화면의 실행 상세 서랍을 연다.
 */
async function announceFinished(job: BatchJobView, requestId: string | null) {
    if (requestId) {
        try {
            const request = await api.loadRequest(requestId);
            if (request.status === 'REJECTED') {
                const head = t('batch.requestRejectedToast', { name: job.displayName });
                toast.error(request.message ? `${head}: ${request.message}` : head, 8000);
                return;
            }
        } catch {
            // 요청을 못 읽어도 결과는 목록에 있다 — 아래에서 결과로 알린다
        }
    }
    runAction.announceResult(job);
}

function schedulePoll() {
    stopPolling();
    if (disposed || !anyBusy.value) return;
    pollTimer = setTimeout(() => {
        pollTimer = null;
        void reload(true);
    }, UtilBatch.POLL_MS);
}

function stopPolling() {
    if (pollTimer) clearTimeout(pollTimer);
    pollTimer = null;
}

/** 그 배치로 걸러진 배치 로그 화면으로 */
function viewLogs(job: BatchJobView) {
    void navigateTo({ path: '/settings/batch-logs', query: { jobName: job.jobName } });
}

async function onToggle(job: BatchJobView, enabled: boolean) {
    toggling.value = job.jobName;
    try {
        applyJobs(await api.toggle(job.jobName, enabled));
        toast.success(`${job.displayName} — ${enabled ? t('batch.turnedOn') : t('batch.turnedOff')}`);
    } catch (e) {
        await onActionError(errorOf(e));
    } finally {
        toggling.value = null;
    }
}

/**
 * 켜기/끄기가 서버에서 거절당했을 때(지금 실행의 거절은 `runAction` 이 같은 규칙으로 다룬다) — 서버 문구를 그대로 토스트로 보이고, 목록이 낡았을 때는 다시 읽는다.
 * 409 `BATCH_RUNNING`(이미 실행 중이거나 대기 중인 요청이 있음 — 표시가 낡았다) · 409 `BATCH_UNAVAILABLE`(DB 배치를 쓸 수 없게 됐다 — 「사용 불가」 표시가 필요하다) · 404 `BATCH_NOT_FOUND`.
 */
async function onActionError(error: ApiError) {
    toast.error(error.message, 8000);
    if (error.code === 'BATCH_RUNNING' || error.code === 'BATCH_UNAVAILABLE' || error.code === 'BATCH_NOT_FOUND') await reload();
}

function openSchedule(job: BatchJobView) {
    scheduleTarget.value = job;
    scheduleError.value = '';
    scheduleFieldErrors.value = {};
    scheduleOpen.value = true;
}

async function onSchedule(schedule: string) {
    const target = scheduleTarget.value;
    if (!target) return;
    scheduleBusy.value = true;
    scheduleError.value = '';
    scheduleFieldErrors.value = {};
    try {
        applyJobs(await api.updateSchedule(target.jobName, schedule));
        scheduleOpen.value = false;
        // 다음 실행 시각은 바뀐 목록에 들어 있다 — 꺼져 있으면 예정이 없다
        const nextRunAt = jobs.value.find((j) => j.jobName === target.jobName)?.nextRunAt;
        toast.success(`${target.displayName} — ${nextRunAt ? t('batch.scheduleChangedNext', { at: time.value.full(nextRunAt) }) : t('batch.scheduleChanged')}`);
    } catch (e) {
        const error = errorOf(e);
        // 식이 틀린 400 은 입력칸 아래에 서버 문구 그대로. 칸에 붙지 않는 오류(409 등)는 대화상자 안에 보이고, 목록이 낡았으면 뒤에서 다시 읽는다
        scheduleFieldErrors.value = error.fields ?? {};
        scheduleError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
        if (error.code === 'BATCH_UNAVAILABLE' || error.code === 'BATCH_RUNNING' || error.code === 'BATCH_NOT_FOUND') await reload();
    } finally {
        scheduleBusy.value = false;
    }
}

/**
 * 요청이 받아들여졌을 때(목록·서랍 어디서 넣었든 — 다시 실행 포함) — 행에 대기 표시를 바로 붙이고 다시 읽기를 건다.
 * 토스트 「요청했습니다 · 요청 #번호」는 `runAction` 이 띄운다.
 */
function onRequested(request: BatchRunRequestView) {
    myRequests.set(request.jobName, request.requestId);
    jobs.value = jobs.value.map((j) => (j.jobName === request.jobName ? { ...j, pendingRequest: request } : j));
    now.value = Date.now();
    schedulePoll();
}
</script>
