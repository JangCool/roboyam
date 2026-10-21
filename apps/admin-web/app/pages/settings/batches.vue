<template>
    <!--
        배치 관리 — hamonica3 의 `batches.vue`(설정 › 애플리케이션) 를 옮긴 것이다. 서버에 등록된 배치를 한 목록에 담고, 각 행에 어디서 도는지(앱 서버 / DB pg_cron)·주기·마지막 실행 요약·다음 실행 예정을 보인다.
        DB 배치는 pg_cron 이 없거나 미등록이면 `available=false` — 행을 흐리게 하고 서버가 준 이유를 그대로 보이며 켜기/끄기·주기 변경·지금 실행을 잠근다.
        행의 「로그 보기」는 그 배치로 걸러진 배치 로그 화면(`/settings/batch-logs?jobName=…`)으로 간다 — 목록에서 지난 실행 결과를 바로 따라가게 하려는 것이다.
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('batch.title')" :subtitle="t('batch.subtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다. 이 단추가 하는 일은 목록을 다시 받아 오는 것이다 -->
            <template #actions>
                <Button :label="t('batch.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>

        <DataTable :value="jobs" data-key="jobName" :loading="busy" :row-class="rowClass" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable scroll-height="flex" class="min-h-0 flex-1">
            <template #empty>
                <ErrorState v-if="loadError" :title="t('batch.jobsLoadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                <div v-else-if="busy" class="h-32" aria-hidden="true" />
                <EmptyState v-else :title="t('batch.emptyJobs')" :description="t('batch.emptyJobsHint')" />
            </template>

            <Column :header="t('batch.batch')" style="min-width: 16rem">
                <template #body="{ data }">
                    <div class="min-w-0">
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

            <Column :header="t('batch.lastRun')" style="min-width: 15rem">
                <template #body="{ data }">
                    <div v-if="data.lastRunAt" class="min-w-0">
                        <div class="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                            <Tag :value="UtilBatchMessages.statusLabel(t, data.lastStatus)" :icon="UtilBatch.statusIcon(data.lastStatus)" :severity="UtilBatch.statusSeverity(data.lastStatus)" />
                            <span class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.lastRunAt) }}</span>
                            <span v-if="data.lastAffectedCount != null" class="text-[12px] tabular-nums text-muted-color">· {{ t('batch.countCases', { n: data.lastAffectedCount }) }}</span>
                        </div>
                        <div v-if="data.lastMessage" v-tooltip.top="data.lastMessage" class="mt-0.5 truncate text-[11px]" :class="data.lastStatus === 'FAIL' ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                            {{ data.lastMessage }}
                        </div>
                    </div>
                    <span v-else class="text-[12px] text-muted-color">{{ t('batch.neverRan') }}</span>
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

            <Column header="" style="width: 16rem">
                <template #body="{ data }">
                    <div class="flex items-center justify-end gap-1.5">
                        <span v-tooltip.top="lockReason(data, canRun, t('batch.noRunReason'))" class="inline-flex">
                            <Button
                                :label="data.running ? t('batch.running') : t('batch.runNow')"
                                icon="pi pi-play"
                                size="small"
                                severity="secondary"
                                outlined
                                class="whitespace-nowrap"
                                :disabled="!canRun || !data.runnable || data.running"
                                :loading="running === data.jobName"
                                @click="onRun(data)"
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
            <strong>{{ t('batch.runNowNote') }}</strong>
            {{ t('batch.manualRunNote') }}
            <NuxtLink to="/settings/batch-logs" class="text-primary hover:underline">
                {{ t('batch.batchLogsLink') }}
            </NuxtLink>
            {{ t('batch.remainsIn') }}
            {{ zoneNote }}
        </p>

        <BatchScheduleDialog v-model:visible="scheduleOpen" :job="scheduleTarget" :busy="scheduleBusy" :error="scheduleError" :field-errors="scheduleFieldErrors" @submit="onSchedule" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { BatchJobView } from '~/composables/batch/api/useBatchApi.types';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('batch.title') });

const api = useBatchApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('batch.manage'));
const canRun = computed(() => can('batch.run'));

const jobs = ref<BatchJobView[]>([]);
/** 주기는 배치가 도는 곳의 시간대로 읽힌다 — 값은 응답의 `scheduleZone` 에서 모은다(고정 문구를 두지 않는다) */
const zoneNote = computed(() => UtilBatchMessages.zoneNote(t, jobs.value));
const busy = ref(false);
const toggling = ref<string | null>(null);
const running = ref<string | null>(null);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다. 켜고 끄기·실행 실패는 토스트다 */
const loadError = ref<ApiError | null>(null);

/** 주기 변경 대화상자 — 열 때의 목록 한 줄을 붙들고, 서버 호출 결과(칸별 오류 포함)를 대화상자 안에 보인다 */
const scheduleOpen = ref(false);
const scheduleTarget = ref<BatchJobView | null>(null);
const scheduleBusy = ref(false);
const scheduleError = ref('');
const scheduleFieldErrors = ref<Record<string, string>>({});

onMounted(reload);

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

async function reload() {
    busy.value = true;
    loadError.value = null;
    try {
        jobs.value = await api.loadJobs();
    } catch (e) {
        loadError.value = errorOf(e);
        jobs.value = [];
    } finally {
        busy.value = false;
    }
}

/** 그 배치로 걸러진 배치 로그 화면으로 */
function viewLogs(job: BatchJobView) {
    void navigateTo({ path: '/settings/batch-logs', query: { jobName: job.jobName } });
}

async function onToggle(job: BatchJobView, enabled: boolean) {
    toggling.value = job.jobName;
    try {
        jobs.value = await api.toggle(job.jobName, enabled);
        toast.success(`${job.displayName} — ${enabled ? t('batch.turnedOn') : t('batch.turnedOff')}`);
    } catch (e) {
        await onActionError(errorOf(e));
    } finally {
        toggling.value = null;
    }
}

/**
 * 켜기/끄기·지금 실행이 서버에서 거절당했을 때 — 서버 문구를 그대로 토스트로 보이고, 목록이 낡았을 때는 다시 읽는다.
 * 409 `BATCH_RUNNING`(다른 곳에서 이미 실행 중 — 「실행 중」 표시가 낡았다) · 409 `BATCH_UNAVAILABLE`(DB 배치를 쓸 수 없게 됐다 — 「사용 불가」 표시가 필요하다) · 404 `BATCH_NOT_FOUND`.
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
        jobs.value = await api.updateSchedule(target.jobName, schedule);
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

async function onRun(job: BatchJobView) {
    // 되돌릴 수 없는 처리(예: 회원 등급 승급)를 바로 돌리므로 한 번 더 묻는다
    const ok = await confirm({
        title: t('batch.runConfirmTitle'),
        message: t('batch.runConfirmMessage', { name: job.displayName }),
        confirmLabel: t('batch.runConfirmLabel'),
    });
    if (!ok) return;

    running.value = job.jobName;
    try {
        jobs.value = await api.runNow(job.jobName);
        // 결과를 그 자리에서 말한다. 「실행했습니다」만 하면 성공인지 실패인지 보려고 로그 화면까지 건너가야 한다 (일이 실패해도 서버는 200 이고 마지막 상태가 FAIL 이다)
        const after = jobs.value.find((j) => j.jobName === job.jobName);
        const failed = after?.lastStatus === 'FAIL';
        const summary = `${job.displayName} — ${UtilBatchMessages.statusLabel(t, after?.lastStatus)}`;
        const message = after?.lastMessage ? `${summary}: ${after.lastMessage}` : summary;
        if (failed) toast.error(message, 10000);
        else toast.success(message, 4000);
    } catch (e) {
        await onActionError(errorOf(e));
    } finally {
        running.value = null;
    }
}
</script>
