<template>
    <!--
        배치 관리 — hamonica3 의 `batches.vue`(설정 › 애플리케이션) 를 옮긴 것이다. 자바 스케줄러에서 도는 배치와 DB(pg_cron)에서 도는 배치를 **한 목록**에 담는다
        (관리자에게는 "지금 무엇이 돌고 있나"가 한 덩어리다). pg_cron 것은 우리가 끄거나 실행할 수 없어(`runnable: false`) 스위치를 잠그고 "DB 가 돌립니다"를 보인다.
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('batch.title')" :subtitle="t('batch.subtitle')">
            <!-- 이름이 「다시 조회」다 — 「새로 고침」은 브라우저를 새로 고친다는 뜻으로 먼저 읽힌다. 이 단추가 하는 일은 목록을 다시 받아 오는 것이다 -->
            <template #actions>
                <Button :label="t('batch.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            </template>
        </PageTitle>

        <DataTable :value="jobs" data-key="jobName" :loading="busy" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable scroll-height="flex" class="min-h-0 flex-1">
            <template #empty>
                <ErrorState v-if="loadError" :title="t('batch.jobsLoadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                <div v-else-if="busy" class="h-32" aria-hidden="true" />
                <EmptyState v-else :title="t('batch.emptyJobs')" :description="t('batch.emptyJobsHint')" />
            </template>

            <Column :header="t('batch.batch')" style="min-width: 16rem">
                <template #body="{ data }">
                    <div class="min-w-0">
                        <div class="truncate text-[13px]">{{ dbText(data.i18n, data.displayName) }}</div>
                        <div v-if="data.description" class="truncate text-[11px] leading-relaxed text-muted-color" :title="dbText(data.descI18n, data.description)">
                            {{ dbText(data.descI18n, data.description) }}
                        </div>
                    </div>
                </template>
            </Column>

            <Column :header="t('batch.where')" style="width: 9rem">
                <template #body="{ data }">
                    <!-- 어디서 도는지가 중요하다. pg_cron 것은 우리가 끌 수도 실행할 수도 없어서, 무엇을 할 수 있는지가 여기서 갈린다 -->
                    <Tag :value="data.source === 'JAVA' ? t('batch.whereApp') : t('batch.whereDb')" :icon="data.source === 'JAVA' ? 'pi pi-server' : 'pi pi-database'" :severity="data.source === 'JAVA' ? 'info' : 'secondary'" />
                </template>
            </Column>

            <Column :header="t('batch.schedule')" style="width: 11rem">
                <template #body="{ data }">
                    <span class="inline-flex items-center gap-1.5">
                        <i class="pi pi-clock text-[11px] text-muted-color" aria-hidden="true" />
                        <code class="text-[12px] text-muted-color">{{ data.schedule }}</code>
                    </span>
                </template>
            </Column>

            <Column :header="t('batch.lastRun')" style="width: 13rem">
                <template #body="{ data }">
                    <div v-if="data.lastRunAt" class="min-w-0">
                        <div class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.lastRunAt) }}</div>
                        <Tag :value="UtilBatchMessages.statusLabel(t, data.lastStatus)" :icon="UtilBatch.statusIcon(data.lastStatus)" :severity="UtilBatch.statusSeverity(data.lastStatus)" class="mt-0.5" />
                    </div>
                    <span v-else class="text-[12px] text-muted-color">{{ t('batch.neverRan') }}</span>
                </template>
            </Column>

            <Column :header="t('batch.enabled')" style="width: 6rem">
                <template #body="{ data }">
                    <!-- pg_cron 은 여기서 못 끈다 — cron.alter_job 을 우리가 부르면 DB 의 시각표를 우리가 고치는 셈이라 그쪽 설정과 어긋난다 -->
                    <ToggleSwitch
                        :model-value="data.enabled"
                        :disabled="data.source !== 'JAVA' || toggling === data.jobName"
                        :aria-label="t('batch.toggleLabel', { name: dbText(data.i18n, data.displayName) })"
                        @update:model-value="(v: boolean) => onToggle(data, v)"
                    />
                </template>
            </Column>

            <Column header="" style="width: 8rem">
                <template #body="{ data }">
                    <Button v-if="data.runnable" :label="t('batch.runNow')" icon="pi pi-play" size="small" severity="secondary" outlined :loading="running === data.jobName" @click="onRun(data)" />
                    <span v-else v-tooltip.top="t('batch.dbRunsTooltip')" class="inline-flex items-center gap-1.5 text-[11px] text-muted-color">
                        <i class="pi pi-lock text-[10px]" aria-hidden="true" />
                        {{ t('batch.runByDb') }}
                    </span>
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
        </p>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { BatchJobView } from '~/composables/batch/api/useBatchApi.types';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

const { t, te } = useI18n();
useSeoMeta({ title: () => t('batch.title') });

const api = useBatchApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

const jobs = ref<BatchJobView[]>([]);
const busy = ref(false);
const toggling = ref<string | null>(null);
const running = ref<string | null>(null);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다. 켜고 끄기·실행 실패는 토스트다 */
const loadError = ref<ApiError | null>(null);

/**
 * 표에서 온 이름·설명을 사람 말로 — 우리가 넣은 행(자바 배치)에는 문구 열쇠가 있고, 고객사가 만든 행에는 없다.
 * 열쇠의 문구가 있을 때만 옮기고 없으면 적어 넣은 글자를 그대로 쓴다(남이 적은 말을 갈아치우지 않는다).
 */
function dbText(key: string | null | undefined, saved: string | null | undefined): string {
    return key && te(key) ? t(key) : (saved ?? '');
}

onMounted(reload);

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

async function onToggle(job: BatchJobView, enabled: boolean) {
    toggling.value = job.jobName;
    try {
        jobs.value = await api.toggle(job.jobName, enabled);
        toast.success(`${dbText(job.i18n, job.displayName)} — ${enabled ? t('batch.turnedOn') : t('batch.turnedOff')}`);
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        toggling.value = null;
    }
}

async function onRun(job: BatchJobView) {
    running.value = job.jobName;
    try {
        jobs.value = await api.runNow(job.jobName);
        // 결과를 그 자리에서 말한다. 「실행했습니다」만 하면 성공인지 실패인지 보려고 로그 화면까지 건너가야 한다
        const after = jobs.value.find((j) => j.jobName === job.jobName);
        const failed = after?.lastStatus === 'FAIL';
        const summary = `${dbText(job.i18n, job.displayName)} — ${UtilBatchMessages.statusLabel(t, after?.lastStatus)}`;
        const message = after?.lastMessage ? `${summary}: ${after.lastMessage}` : summary;
        if (failed) toast.error(message, 10000);
        else toast.success(message, 4000);
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        running.value = null;
    }
}

</script>
