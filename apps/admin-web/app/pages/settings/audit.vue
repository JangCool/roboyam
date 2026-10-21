<template>
    <!--
        감사 로그 — hamonica3 의 `audit.vue`(설정 › 애플리케이션) 를 옮긴 것이다.

        읽기만 하는 화면이다. 고치거나 지우는 단추가 없다 — 고칠 수 있으면 감사가 아니다.
        ⚠ (서버 몫) hamonica3 는 이 화면을 연 것도 감사 로그에 남겼다 — 감사 기록을 누가 뒤졌는지 모르면 감사가 반쪽이라서다. 서버가 첫 쪽을 부를 때만 남기고 쪽을 넘길 때마다 쌓지 않았다. 이 제품에서도 그럴지는 정해지지 않았다.
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4 lg:h-full">
        <PageTitle :title="t('audit.title')" :subtitle="t('audit.subtitle')" />

        <!-- 거르기 -->
        <div class="flex flex-wrap items-end gap-2">
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.from">{{ t('audit.from') }}</label>
                <AppDateTimePicker v-model="filter.from" :input-id="ids.from" :show-zone="false" class="w-52" :placeholder="t('audit.fromPlaceholder')" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.to">{{ t('audit.to') }}</label>
                <AppDateTimePicker v-model="filter.to" :input-id="ids.to" :show-zone="false" class="w-52" :placeholder="t('audit.toPlaceholder')" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.actor">{{ t('audit.actor') }}</label>
                <InputText :id="ids.actor" v-model="filter.actor" class="w-44" :placeholder="t('audit.actorPlaceholder')" @keyup.enter="reloadFirst" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.action">{{ t('audit.action') }}</label>
                <Select v-model="filter.action" :input-id="ids.action" :options="options.actions" option-label="label" option-value="value" filter show-clear class="w-52" :placeholder="t('common.all')" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.targetType">{{ t('audit.targetType') }}</label>
                <Select v-model="filter.targetType" :input-id="ids.targetType" :options="options.targetTypes" option-label="label" option-value="value" show-clear class="w-36" :placeholder="t('common.all')" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.target">{{ t('audit.target') }}</label>
                <InputText :id="ids.target" v-model="filter.target" class="w-40" :placeholder="t('audit.targetPlaceholder')" @keyup.enter="reloadFirst" />
            </div>
            <div>
                <label class="mb-1 block text-[11px] text-muted-color" :for="ids.result">{{ t('audit.result') }}</label>
                <Select v-model="filter.result" :input-id="ids.result" :options="results" option-label="label" option-value="value" show-clear class="w-28" :placeholder="t('common.all')" />
            </div>

            <Button :label="t('audit.search')" size="small" :loading="busy" @click="reloadFirst" />
            <Button :label="t('audit.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
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
                <!-- 빈 상태를 구분한다 — 거르기 때문인지 아직 아무 일도 없는 건지 -->
                <EmptyState v-else-if="hasFilter" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                    <div v-if="activeConditions.length" class="flex w-full flex-wrap justify-center gap-1">
                        <Tag v-for="condition in activeConditions" :key="condition" :value="condition" severity="secondary" class="text-[11px]!" />
                    </div>
                    <Button :label="t('audit.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                </EmptyState>
                <EmptyState v-else :title="t('audit.empty')" :description="t('audit.emptyHint')" />
            </template>

            <!-- 줄을 펼치면 바뀐 내용을 본다. 보기만 한 줄은 "조회 기록입니다"가 나온다 -->
            <Column expander style="width: 3rem" />

            <Column field="occurredAt" :header="t('audit.occurredAt')" style="width: 11rem">
                <template #body="{ data }">
                    <span class="text-[12px] tabular-nums">{{ time.full(data.occurredAt) }}</span>
                </template>
            </Column>

            <Column field="actorEmail" :header="t('audit.actor')" style="width: 14rem">
                <template #body="{ data }">
                    <div class="min-w-0">
                        <!-- 이름 먼저, 그 밑에 메일. 메일은 버리지 않는다 — 동명이인이 있을 때 사람을 가르는 값이고, 감사 기록은 되짚을 수 있어야 한다. IP 와 같은 줄에 둔다 -->
                        <div class="truncate text-[13px]">
                            {{ data.actorName || data.actorEmail || data.actorId }}
                        </div>
                        <div v-if="data.actorEmail || data.actorIp" class="truncate text-[11px] text-muted-color">
                            {{ [data.actorEmail, data.actorIp].filter(Boolean).join(' · ') }}
                        </div>
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
                    <Tag :value="data.result === 'OK' ? t('audit.succeeded') : t('audit.failed')" :severity="data.result === 'OK' ? 'success' : 'danger'" />
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
                        {{ t('audit.noChangeReadOnly') }}
                    </p>
                </div>
            </template>
        </DataTable>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AuditLogView, AuditOptions } from '~/composables/audit/api/useAuditApi.types';
import UtilAudit from '~/utils/util-audit';

const { t } = useI18n();
useSeoMeta({ title: () => t('audit.title') });

const api = useAuditApi();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

const ids = { from: useId(), to: useId(), actor: useId(), action: useId(), targetType: useId(), target: useId(), result: useId() };

/** 쪽 안내(「전체 N건」). ⚠ `{totalRecords}` 는 PrimeVue 가 채우는 자리다 — 번역에서는 `{count}` 로 두고 여기서 PrimeVue 가 아는 이름으로 바꿔 준다 */
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const results = computed(() => [
    { label: t('audit.succeeded'), value: 'OK' },
    { label: t('audit.failed'), value: 'FAIL' },
]);

const rows = ref<AuditLogView[]>([]);
const options = ref<AuditOptions>({ actions: [], targetTypes: [] });
const expanded = ref<AuditLogView[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다 */
const loadError = ref<ApiError | null>(null);

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
    if (filter.result) out.push(filter.result === 'OK' ? t('audit.resultOk') : t('audit.resultFail'));
    return out;
});

onMounted(async () => {
    try {
        options.value = await api.loadOptions();
    } catch {
        // 고르개를 못 채워도 목록은 봐야 한다. 조용히 빈 채로 둔다.
    }
    await reload();
});

async function reload() {
    busy.value = true;
    loadError.value = null;
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
        loadError.value = errorOf(e);
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
