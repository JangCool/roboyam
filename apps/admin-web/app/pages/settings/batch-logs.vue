<template>
    <!--
        배치 로그 — hamonica3 의 `batch-logs.vue`(설정 › 애플리케이션) 를 옮긴 것이다. 배치 실행 이력을 시작 시각 최근순으로 본다. 서버가 한 쪽씩 준다(`lazy`).
        배치 관리 화면의 「로그 보기」가 `?jobName=<배치 키>` 로 들어오면 그 배치로 거른 채 열린다.
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('batch.logsTitle')" :subtitle="t('batch.logsSubtitle')" />

        <div class="flex flex-wrap items-end gap-2">
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.job">{{ t('batch.batch') }}</label>
                <Select v-model="filter.jobName" :input-id="ids.job" :options="jobOptions" option-label="label" option-value="value" show-clear class="w-56" :placeholder="t('common.all')" @change="onJobChange" />
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
            data-key="logId"
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
                <ErrorState v-if="loadError" :title="t('batch.logsLoadFailed')" :description="errorText(loadError)" retry :retrying="busy" @retry="reload" />
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
                    <span class="block truncate text-[13px]">{{ data.displayName }}</span>
                </template>
            </Column>

            <Column :header="t('batch.trigger')" style="width: 10rem">
                <template #body="{ data }">
                    <!-- 수동이면 누가 눌렀는지(운영자가 지워졌으면 이름 없음) -->
                    <div class="min-w-0">
                        <Tag :value="UtilBatchMessages.triggerLabel(t, data.trigger)" :icon="data.trigger === 'MANUAL' ? 'pi pi-user' : 'pi pi-clock'" severity="secondary" />
                        <div v-if="data.operatorName" class="mt-0.5 truncate text-[11px] text-muted-color">{{ data.operatorName }}</div>
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
                    <!-- 0 과 비어 있음은 다르다. 0 이면 돌긴 돌았는데 할 일이 없던 것이고, 비어 있으면 아직 안 끝났거나 실패해서 센 값이 없는 것이다 -->
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

const route = useRoute();
const ids = { job: useId(), status: useId() };

/** 쪽 안내(「전체 N건」). ⚠ `{totalRecords}` 는 PrimeVue 가 채우는 자리다 — 번역에서는 `{count}` 로 두고 여기서 PrimeVue 가 아는 이름으로 바꿔 준다 */
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const statuses = computed(() => [
    { label: t('batch.statusRunning'), value: 'RUNNING' },
    { label: t('batch.statusOk'), value: 'OK' },
    { label: t('batch.statusFail'), value: 'FAIL' },
    { label: t('batch.statusSkip'), value: 'SKIP' },
]);

const rows = ref<BatchLogView[]>([]);
const jobOptions = ref<{ label: string; value: string }[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다 */
const loadError = ref<ApiError | null>(null);

/** 주소의 `?jobName=` — 배치 관리 화면의 「로그 보기」가 건다. 문자열 하나만 받는다 */
function jobNameFromRoute(): string | null {
    const value = route.query.jobName;
    return typeof value === 'string' && value ? value : null;
}

const filter = reactive<{ jobName: string | null; status: string | null }>({
    jobName: jobNameFromRoute(),
    status: null,
});

// 같은 화면 안에서 주소의 배치만 바뀐 경우(예: 사이드바의 「배치 로그」) — 걸린 거르기를 주소에 맞춘다
watch(
    () => route.query.jobName,
    () => {
        if (route.path !== '/settings/batch-logs' || filter.jobName === jobNameFromRoute()) return;
        filter.jobName = jobNameFromRoute();
        reloadFirst();
    },
);

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
        rows.value = page.items;
        total.value = page.total;
    } catch (e) {
        loadError.value = errorOf(e);
        rows.value = [];
        total.value = 0;
    } finally {
        busy.value = false;
    }
}

/** 배치 고르개를 바꾸면 주소의 `?jobName=` 도 맞춘다 — 안 그러면 새로 고침이나 되돌아오기에서 지운 조건이 다시 걸린다 */
function onJobChange() {
    void navigateTo({ path: route.path, query: { ...route.query, jobName: filter.jobName || undefined } }, { replace: true });
    reloadFirst();
}

/** 조회 조건이 틀린 400(`VALIDATION`)은 칸마다의 서버 문구가 있으면 그것을, 없으면(`fields` 가 없을 수 있다) 서버 `message` 를 그대로 보인다 */
function errorText(e: ApiError): string {
    const fieldMessages = Object.values(e.fields ?? {});
    return fieldMessages.length ? fieldMessages.join(' ') : e.message;
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
    onJobChange();
}

/** 얼마나 걸렸나. 초 단위로 반올림하지 않는다 — 배치는 대개 1초 안에 끝나서, 반올림하면 전부 「0초」가 되어 「어느 것이 느린가」를 볼 수 없다 */
function elapsed(row: BatchLogView): string {
    if (!row.finishedAt) return '—';
    const ms = new Date(row.finishedAt).getTime() - new Date(row.startedAt).getTime();
    if (ms < 1000) return `${ms}ms`;
    return t('batch.runBySeconds', { n: (ms / 1000).toFixed(1) });
}
</script>
