<template>
    <!--
        감사 로그 — hamonica3 의 `audit.vue`(설정 › 애플리케이션) 를 옮긴 것이다.

        읽기만 하는 화면이다. 고치거나 지우는 단추가 없다 — 고칠 수 있으면 감사가 아니다. 권한은 `audit.read`(메뉴 권한이 없으면 셸이 이 화면을 그리지 않는다).
        ⚠ (서버 몫) 이 화면을 연 것은 감사 로그에 남기지 않는다(서버 가정 — 쪽을 넘길 때마다 쌓이므로). 정책이 바뀌면 서버가 첫 쪽만 남기는 방안이 있다.
        작업·대상 종류·결과의 이름과 실패·거부 사유는 서버가 요청 언어로 준다 — 화면이 i18n 키로 다시 풀지 않는다. 그래서 언어를 바꾸면 고르개와 목록을 다시 받는다.
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('audit.title')" :subtitle="t('audit.subtitle')" />

        <!-- 거르기 — 서버가 지원하는 조건(기간·사용자·작업·대상 종류·대상·결과)과 같다. 조건이 틀렸다는 서버 400(`fields.<칸>`)은 그 칸 아래에 서버 문구로 보인다 -->
        <div class="flex flex-wrap items-start gap-2">
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.from">{{ t('audit.from') }}</label>
                <AppDateTimePicker v-model="filter.from" :input-id="ids.from" :show-zone="false" class="w-52" :placeholder="t('audit.fromPlaceholder')" />
                <p v-if="filterErrors.from" role="alert" class="mt-1 max-w-52 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.from }}</p>
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.to">{{ t('audit.to') }}</label>
                <AppDateTimePicker v-model="filter.to" :input-id="ids.to" :show-zone="false" class="w-52" :placeholder="t('audit.toPlaceholder')" />
                <p v-if="filterErrors.to" role="alert" class="mt-1 max-w-52 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.to }}</p>
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.actor">{{ t('audit.actor') }}</label>
                <InputText :id="ids.actor" v-model="filter.actor" class="w-44" :invalid="!!filterErrors.actor" :placeholder="t('audit.actorPlaceholder')" @keyup.enter="reloadFirst" />
                <p v-if="filterErrors.actor" role="alert" class="mt-1 max-w-44 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.actor }}</p>
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.action">{{ t('audit.action') }}</label>
                <Select v-model="filter.action" :input-id="ids.action" :options="options.actions" option-label="label" option-value="value" filter show-clear class="w-52" :invalid="!!filterErrors.action" :placeholder="t('common.all')" />
                <p v-if="filterErrors.action" role="alert" class="mt-1 max-w-52 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.action }}</p>
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.targetType">{{ t('audit.targetType') }}</label>
                <Select v-model="filter.targetType" :input-id="ids.targetType" :options="options.targetTypes" option-label="label" option-value="value" show-clear class="w-36" :invalid="!!filterErrors.targetType" :placeholder="t('common.all')" />
                <p v-if="filterErrors.targetType" role="alert" class="mt-1 max-w-36 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.targetType }}</p>
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.target">{{ t('audit.target') }}</label>
                <InputText :id="ids.target" v-model="filter.target" class="w-40" :invalid="!!filterErrors.target" :placeholder="t('audit.targetPlaceholder')" @keyup.enter="reloadFirst" />
                <p v-if="filterErrors.target" role="alert" class="mt-1 max-w-40 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.target }}</p>
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.result">{{ t('audit.result') }}</label>
                <!-- 결과 고르개 — 서버가 준 목록(성공·실패·거부)이다 -->
                <Select v-model="filter.result" :input-id="ids.result" :options="options.results" option-label="label" option-value="value" show-clear class="w-32" :invalid="!!filterErrors.result" :placeholder="t('common.all')" />
                <p v-if="filterErrors.result" role="alert" class="mt-1 max-w-32 text-[11px] text-red-600 dark:text-red-400">{{ filterErrors.result }}</p>
            </div>

            <!-- 단추 — 칸 아래에 오류 문구가 붙어도 입력 칸과 같은 높이에 남도록 라벨 자리(투명)를 맞춰 둔다 -->
            <div>
                <span class="mb-1 block text-[11px]" aria-hidden="true">&nbsp;</span>
                <div class="flex items-center gap-2">
                    <Button :label="t('audit.search')" size="small" :loading="busy" @click="reloadFirst" />
                    <Button :label="t('audit.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                </div>
            </div>
        </div>

        <DataTable
            v-model:expanded-rows="expanded"
            :value="rows"
            data-key="auditNo"
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
                <ErrorState v-if="loadError" :title="t('audit.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                <div v-else-if="busy" class="h-32" aria-hidden="true" />
                <!-- 서버가 조건이 틀렸다고 알린 경우 — 어느 칸인지는 칸 아래에 있다. 「결과 없음」으로 읽히지 않게 따로 말한다 -->
                <EmptyState v-else-if="hasFilterErrors" icon="pi pi-exclamation-circle" :title="t('audit.filterInvalid')" :description="t('audit.filterInvalidHint')" />
                <!-- 빈 상태를 구분한다 — 거르기 때문인지 아직 아무 일도 없는 건지 -->
                <EmptyState v-else-if="hasFilter" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                    <div v-if="activeConditions.length" class="flex w-full flex-wrap justify-center gap-1">
                        <Tag v-for="condition in activeConditions" :key="condition" :value="condition" severity="secondary" class="text-[11px]!" />
                    </div>
                    <Button :label="t('audit.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                </EmptyState>
                <EmptyState v-else :title="t('audit.empty')" :description="t('audit.emptyHint')" />
            </template>

            <!-- 줄을 펼치면 바뀐 내용과 실패·거부 사유를 본다. 바뀐 값이 없는 줄(로그인·거부 등)은 「기록된 변경 내용이 없어요」가 나온다 -->
            <Column expander style="width: 3rem" />

            <Column field="occurredAt" :header="t('audit.occurredAt')" style="width: 11rem">
                <template #body="{ data }">
                    <span class="text-[12px] tabular-nums">{{ time.full(data.occurredAt) }}</span>
                </template>
            </Column>

            <Column field="actorName" :header="t('audit.actor')" style="width: 14rem">
                <template #body="{ data }">
                    <div class="min-w-0">
                        <!-- 이름 먼저(서버가 지금의 이름을 준다 — 시스템·로그인 전 행위의 이름도 서버가 준다). 이름이 없으면(지워진 운영자) 아이디로 대신한다. 서버는 이메일을 내지 않으므로 그 밑에는 IP 만 둔다. 아이디는 마우스를 올리면 보인다 -->
                        <div class="truncate text-[13px]" :title="data.actorId ?? undefined">
                            {{ actorLabel(data) }}
                        </div>
                        <div v-if="data.actorIp" class="truncate text-[11px] text-muted-color">{{ data.actorIp }}</div>
                    </div>
                </template>
            </Column>

            <Column field="actionName" :header="t('audit.action')" style="width: 13rem">
                <template #body="{ data }">
                    <div class="min-w-0">
                        <div class="truncate text-[13px]">{{ data.actionName }}</div>
                        <code class="truncate text-[11px] text-muted-color">{{ data.action }}</code>
                    </div>
                </template>
            </Column>

            <Column :header="t('audit.changes')">
                <template #body="{ data }">
                    <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                        <Tag v-if="data.targetTypeName" :value="data.targetTypeName" severity="secondary" />
                        <span class="truncate text-[13px]">
                            {{ data.targetName || data.targetId || '—' }}
                        </span>
                        <!-- 이름과 아이디가 다르면 아이디도 보인다. 이름은 그때의 것이라 지금과 다를 수 있고, 무엇을 가리키는지는 아이디가 답한다 -->
                        <code v-if="data.targetName && data.targetId" class="text-[11px] text-muted-color">
                            {{ data.targetId }}
                        </code>
                    </div>
                </template>
            </Column>

            <Column field="result" :header="t('audit.result')" style="width: 7rem">
                <template #body="{ data }">
                    <!-- 성공 · 실패 · 거부(권한이 없어 막힌 요청) — 거부는 실패와 색을 달리해 구분한다. 이름은 서버가 준 결과 목록에서 찾고 목록을 못 받았으면 번역 문구 -->
                    <Tag :value="resultLabel(data.result)" :severity="resultSeverity(data.result)" />
                </template>
            </Column>

            <template #expansion="{ data }">
                <div class="px-4 py-3">
                    <Message v-if="data.message" severity="error" size="small" variant="simple" class="mb-3">
                        {{ data.message }}
                    </Message>

                    <div v-if="changesOf(data).length" class="flex flex-col gap-1.5">
                        <div class="mb-1 text-[12px] font-medium">{{ t('audit.changeDetail') }}</div>
                        <div v-for="(c, i) in changesOf(data)" :key="i" class="flex flex-wrap items-baseline gap-2 text-[12px]">
                            <code class="min-w-32 text-muted-color">{{ c.field }}</code>
                            <span class="text-muted-color">{{ c.before ?? t('audit.none') }}</span>
                            <i class="pi pi-arrow-right text-[10px] text-muted-color" aria-hidden="true" />
                            <span>{{ c.after ?? t('audit.removed') }}</span>
                        </div>
                    </div>

                    <p v-else class="text-[12px] text-muted-color">
                        {{ t('audit.noChange') }}
                    </p>
                </div>
            </template>
        </DataTable>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AuditFilterField, AuditLogView, AuditOptions, AuditResult } from '~/composables/audit/api/useAuditApi.types';
import UtilAudit from '~/utils/util-audit';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('audit.title') });

const api = useAuditApi();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

const ids = { from: useId(), to: useId(), actor: useId(), action: useId(), targetType: useId(), target: useId(), result: useId() };

/** 쪽 안내(「전체 N건」). ⚠ `{totalRecords}` 는 PrimeVue 가 채우는 자리다 — 번역에서는 `{count}` 로 두고 여기서 PrimeVue 가 아는 이름으로 바꿔 준다 */
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const rows = ref<AuditLogView[]>([]);
/** 거르기 고르개 목록 — 서버가 요청 언어로 준다(작업·대상 종류·결과). 화면에 고정 목록을 두지 않는다 */
const options = ref<AuditOptions>({ actions: [], targetTypes: [], results: [] });
const expanded = ref<AuditLogView[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다 */
const loadError = ref<ApiError | null>(null);
/** 서버가 조건이 틀렸다고 알린 칸(400 `fields.<칸>`)의 문구 — 해당 필터 칸 아래에 보인다 */
const filterErrors = reactive<Partial<Record<AuditFilterField, string>>>({});
const FILTER_FIELDS: readonly AuditFilterField[] = ['from', 'to', 'actor', 'action', 'targetType', 'target', 'result'];
const hasFilterErrors = computed(() => FILTER_FIELDS.some((field) => !!filterErrors[field]));

/** 거르기 값. 시각(`from`·`to`)은 순간(UTC ISO 글자)이고 비면 '' — 고르개가 표시 시간대로 읽고 쓴다(`AppDateTimePicker`) */
const filter = reactive<{
    from: string;
    to: string;
    actor: string;
    action: string | null;
    targetType: string | null;
    target: string;
    result: string | null;
}>({ from: '', to: '', actor: '', action: null, targetType: null, target: '', result: null });

const hasFilter = computed(() => !!filter.from || !!filter.to || !!filter.actor || !!filter.action || !!filter.targetType || !!filter.target || !!filter.result);

/** 빈 상태가 「무엇 때문에 안 나오는지」 말해 준다 */
const activeConditions = computed(() => {
    const out: string[] = [];
    if (filter.from) out.push(t('audit.filterAfter', { at: time.value.full(filter.from) }));
    if (filter.to) out.push(t('audit.filterBefore', { at: time.value.full(filter.to) }));
    if (filter.actor) out.push(t('audit.filterActor', { name: filter.actor }));
    if (filter.action) out.push(options.value.actions.find((a) => a.value === filter.action)?.label ?? filter.action);
    if (filter.targetType) out.push(options.value.targetTypes.find((type) => type.value === filter.targetType)?.label ?? filter.targetType);
    if (filter.target) out.push(t('audit.filterTarget', { name: filter.target }));
    if (filter.result) out.push(resultLabel(filter.result));
    return out;
});

/** 결과 이름 — 서버가 준 결과 목록에서 찾고, 목록을 못 받았으면 번역 문구로 대신한다 */
function resultLabel(result: string): string {
    const found = options.value.results.find((option) => option.value === result);
    if (found) return found.label;
    if (result === 'OK') return t('audit.succeeded');
    if (result === 'FAIL') return t('audit.failed');
    if (result === 'DENIED') return t('audit.denied');
    return result;
}

/** 성공은 초록 · 실패는 빨강 · 거부(권한이 없어 막힘)는 주황 — 거부는 시스템 오류가 아니라 정책이 막은 것이라 실패와 색을 달리한다 */
function resultSeverity(result: AuditResult): 'success' | 'danger' | 'warn' {
    if (result === 'OK') return 'success';
    if (result === 'DENIED') return 'warn';
    return 'danger';
}

/** 행위자 칸의 글자 — 서버가 준 이름, 없으면(지워진 운영자) 아이디, 둘 다 없으면 행위자 종류로 대신한다 */
function actorLabel(row: AuditLogView): string {
    if (row.actorName) return row.actorName;
    if (row.actorId) return row.actorId;
    if (row.actorType === 'SYSTEM') return t('audit.actorSystem');
    if (row.actorType === 'ANONYMOUS') return t('audit.actorAnonymous');
    return '—';
}

/** 고르개 목록을 받는다 — 못 받아도 목록은 봐야 하므로 조용히 비워 둔다 */
async function loadOptions() {
    try {
        options.value = await api.loadOptions();
    } catch {
        // 고르개를 못 채워도 목록은 봐야 한다.
    }
}

onMounted(async () => {
    await Promise.all([loadOptions(), reload()]);
});

// 작업·대상·결과 이름과 사유는 서버가 요청 언어로 준다 — 화면 언어가 바뀌면 고르개와 목록을 다시 받아야 이름이 바뀐다(고른 값은 코드라 그대로 남는다)
watch(locale, () => {
    void loadOptions();
    void reload();
});

/** 서버 400 의 `fields` 중 거르기 칸에 해당하는 것만 칸 아래 문구로 옮긴다. 하나라도 옮겼으면 true */
function applyFilterErrors(e: ApiError): boolean {
    let any = false;
    for (const field of FILTER_FIELDS) {
        const message = e.field(field);
        if (message) {
            filterErrors[field] = message;
            any = true;
        }
    }
    return any;
}

async function reload() {
    busy.value = true;
    loadError.value = null;
    for (const field of FILTER_FIELDS) filterErrors[field] = undefined;
    try {
        const found = await api.search({
            // 고른 시간대의 그 시각이 이미 순간(UTC ISO)이다 — 브라우저 시간대로 다시 읽지 않는다(`AppDateTimePicker`)
            from: filter.from || undefined,
            to: filter.to || undefined,
            actor: filter.actor,
            action: filter.action ?? undefined,
            targetType: filter.targetType ?? undefined,
            target: filter.target,
            result: filter.result ?? undefined,
            page: Math.floor(first.value / size.value),
            size: size.value,
        });
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        const failure = errorOf(e);
        // 조건이 틀렸다는 400 이면 그 칸 아래에 서버 문구를 보이고, 칸에 붙지 않는 오류(page·size 등)와 그 밖의 실패는 표 자리의 오류 상태로 보인다
        if (!(failure.code === 'VALIDATION' && applyFilterErrors(failure))) loadError.value = failure;
        rows.value = [];
        total.value = 0;
    } finally {
        busy.value = false;
    }
}

/** 조회·Enter 는 첫 쪽부터 다시 본다 — 거른 결과가 더 적은데 스무 번째 쪽에 머물면 빈 목록을 만난다 */
function reloadFirst() {
    first.value = 0;
    void reload();
}

/**
 * 쪽을 넘긴다. 서버가 한 쪽씩 준다(`lazy`) — 감사 로그는 몇십만 건이 될 수 있어 통째로 받을 수 없다.
 */
function onPage(e: { first: number; rows: number }) {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

function clearFilter() {
    filter.from = '';
    filter.to = '';
    filter.actor = '';
    filter.action = null;
    filter.targetType = null;
    filter.target = '';
    filter.result = null;
    reloadFirst();
}

/** 펼칠 때만 연다. 못 열어도 화면이 멈추면 안 된다 — 옛 기록의 모양이 다를 수 있다. */
function changesOf(row: AuditLogView) {
    return UtilAudit.parseChanges(row.changes);
}
</script>
