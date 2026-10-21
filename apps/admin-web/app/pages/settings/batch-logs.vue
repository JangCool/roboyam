<template>
    <!--
        배치 로그 — hamonica3 의 `batch-logs.vue`(설정 › 애플리케이션) 를 옮긴 것이다. 자바(`batch_log`)와 pg_cron(`cron.job_run_details`) 두 곳의 실행 이력을 **섞어 시간 역순**으로 본다
        (관리자에게는 "지난주에 무엇이 실패했나"가 한 물음이지 두 물음이 아니다). 서버가 한 쪽씩 준다(`lazy`).
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('batch.logsTitle')" :subtitle="t('batch.logsSubtitle')" />

        <div class="flex flex-wrap items-end gap-2">
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.job">{{ t('batch.batch') }}</label>
                <Select v-model="filter.jobName" :input-id="ids.job" :options="jobOptions" option-label="label" option-value="value" show-clear class="w-56" :placeholder="t('common.all')" @change="reloadFirst" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.status">{{ t('batch.result') }}</label>
                <Select v-model="filter.status" :input-id="ids.status" :options="statuses" option-label="label" option-value="value" show-clear class="w-32" :placeholder="t('common.all')" @change="reloadFirst" />
            </div>
            <!-- 이름·색 모두 배치 관리 화면과 같다 -->
            <Button :label="t('batch.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="busy" @click="reload" />
            <Button v-if="hasFilter" :label="t('batch.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
        </div>

        <DataTable
            :value="rows"
            data-key="rowKey"
            lazy
            paginator
            :first="first"
            :rows="size"
            :total-records="total"
            :rows-per-page-options="[20, 50, 100]"
            :loading="busy"
            :size="prefs.density === 'compact' ? 'small' : undefined"
            scrollable
            scroll-height="flex"
            :current-page-report-template="totalTemplate"
            paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
            class="min-h-0 flex-1"
            @page="onPage"
        >
            <template #empty>
                <ErrorState v-if="loadError" :title="t('batch.logsLoadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                <div v-else-if="busy" class="h-32" aria-hidden="true" />
                <!-- 빈 상태를 구분한다 — 거르기 때문인지 아직 아무 일도 없는 건지 -->
                <EmptyState v-else-if="hasFilter" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                    <div v-if="activeConditions.length" class="flex w-full flex-wrap justify-center gap-1">
                        <Tag v-for="condition in activeConditions" :key="condition" :value="condition" severity="secondary" class="text-[11px]!" />
                    </div>
                    <Button :label="t('batch.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                </EmptyState>
                <EmptyState v-else :title="t('batch.emptyLogs')" :description="t('batch.emptyLogsHint')" />
            </template>

            <Column :header="t('batch.startedAt')" style="width: 12rem">
                <template #body="{ data }">
                    <span class="text-[12px] tabular-nums">{{ time.full(data.startedAt) }}</span>
                </template>
            </Column>

            <Column :header="t('batch.batch')" style="min-width: 14rem">
                <template #body="{ data }">
                    <div class="flex min-w-0 items-center gap-1.5">
                        <Tag :value="data.source === 'JAVA' ? t('batch.dbBadge') : t('batch.dbBadgeDb')" :icon="data.source === 'JAVA' ? 'pi pi-server' : 'pi pi-database'" :severity="data.source === 'JAVA' ? 'info' : 'secondary'" />
                        <span class="truncate text-[13px]">{{ data.displayName }}</span>
                    </div>
                </template>
            </Column>

            <Column :header="t('batch.result')" style="width: 8rem">
                <template #body="{ data }">
                    <Tag :value="UtilBatchMessages.statusLabel(t, data.status)" :icon="UtilBatch.statusIcon(data.status)" :severity="UtilBatch.statusSeverity(data.status)" />
                </template>
            </Column>

            <Column :header="t('batch.elapsed')" style="width: 9rem">
                <template #body="{ data }">
                    <span class="inline-flex items-center gap-1.5">
                        <i class="pi pi-stopwatch text-[11px] text-muted-color" aria-hidden="true" />
                        <span class="text-[12px] tabular-nums text-muted-color">{{ elapsed(data) }}</span>
                    </span>
                </template>
            </Column>

            <Column :header="t('batch.affected')" style="width: 7rem">
                <template #body="{ data }">
                    <!-- 0 과 「안 셈」은 다르다. 0 이면 돌긴 돌았는데 할 일이 없던 것이고, 비어 있으면 애초에 세지 않는 배치다(pg_cron 은 늘 그렇다) -->
                    <span class="text-[12px] tabular-nums text-muted-color">{{ data.affectedCount ?? '—' }}</span>
                </template>
            </Column>

            <Column :header="t('batch.message')">
                <template #body="{ data }">
                    <span v-if="data.message" v-tooltip.top="data.message" class="block truncate text-[12px]" :class="data.status === 'FAIL' ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                        {{ data.message }}
                    </span>
                    <span v-else class="text-[12px] text-muted-color">—</span>
                </template>
            </Column>
        </DataTable>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { BatchLogView } from '~/composables/batch/api/useBatchApi.types';
import UtilBatch from '~/utils/util-batch';
import UtilBatchMessages from '~/utils/util-batch-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('batch.logsTitle') });

const api = useBatchApi();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

const ids = { job: useId(), status: useId() };

/** 쪽 안내(「전체 N건」). ⚠ `{totalRecords}` 는 PrimeVue 가 채우는 자리다 — 번역에서는 `{count}` 로 두고 여기서 PrimeVue 가 아는 이름으로 바꿔 준다 */
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const statuses = computed(() => [
    { label: t('batch.statusOk'), value: 'OK' },
    { label: t('batch.statusFail'), value: 'FAIL' },
    { label: t('batch.statusSkip'), value: 'SKIP' },
]);

/** 화면이 줄을 알아보는 열쇠. 번호가 두 곳(자바·pg_cron)에서 오므로 이름+시각으로 만든다 */
type Row = BatchLogView & { rowKey: string };

const rows = ref<Row[]>([]);
const jobOptions = ref<{ label: string; value: string }[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다 */
const loadError = ref<ApiError | null>(null);

const filter = reactive<{ jobName: string | null; status: string | null }>({
    jobName: null,
    status: null,
});

const hasFilter = computed(() => !!filter.jobName || !!filter.status);

const activeConditions = computed(() => {
    const out: string[] = [];
    if (filter.jobName) out.push(jobOptions.value.find((j) => j.value === filter.jobName)?.label ?? filter.jobName);
    if (filter.status) out.push(statuses.value.find((s) => s.value === filter.status)?.label ?? filter.status);
    return out;
});

onMounted(async () => {
    try {
        jobOptions.value = (await api.loadJobs()).map((j) => ({ label: j.displayName, value: j.jobName }));
    } catch {
        // 고르개를 못 채워도 목록은 봐야 한다.
    }
    await reload();
});

async function reload() {
    busy.value = true;
    loadError.value = null;
    try {
        const page = await api.loadLogs({
            jobName: filter.jobName ?? undefined,
            status: filter.status ?? undefined,
            page: Math.floor(first.value / size.value),
            size: size.value,
        });
        rows.value = page.items.map((r) => ({ ...r, rowKey: `${r.jobName}-${r.startedAt}` }));
        total.value = page.total;
    } catch (e) {
        loadError.value = errorOf(e);
        rows.value = [];
        total.value = 0;
    } finally {
        busy.value = false;
    }
}

/** 거르기를 바꾸면 첫 쪽으로. 안 그러면 스무 번째 쪽에서 빈 목록을 만난다 */
function reloadFirst() {
    first.value = 0;
    void reload();
}

function onPage(e: { first: number; rows: number }) {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

function clearFilter() {
    filter.jobName = null;
    filter.status = null;
    reloadFirst();
}

/** 얼마나 걸렸나. 초 단위로 반올림하지 않는다 — 배치는 대개 1초 안에 끝나서, 반올림하면 전부 「0초」가 되어 「어느 것이 느린가」를 볼 수 없다 */
function elapsed(row: BatchLogView): string {
    if (!row.finishedAt) return '—';
    const ms = new Date(row.finishedAt).getTime() - new Date(row.startedAt).getTime();
    if (ms < 1000) return `${ms}ms`;
    return t('batch.runBySeconds', { n: (ms / 1000).toFixed(1) });
}
</script>
