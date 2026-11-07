<template>
    <!--
        보상 이벤트(`/reward-events`) — 가입 선착순 지급·추첨 당첨·전체 일괄 지급처럼 회원에게 보상을 주는 이벤트 목록. 「사이트 팝업」(알림 창)과 다른 업무다.
        상태 탭(건수 — 검색어·방식을 적용한 건수) · 검색(이름·번호) · 방식 거르기 · 표(번호·이름·방식·보상 요약·기간·지급 수·상태·만든 운영자). 줄을 누르면 상세(`/reward-events/{id}`).
        탭은 주소 `?status=` 로 남는다. 권한: 화면 `reward-event.read`, 「새 보상 이벤트」 `reward-event.manage`(없으면 단추를 숨긴다 — 막는 것은 서버).
        서버 호출은 `useRewardEventAdminApi`(admin `RewardEventAdminController`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('rewardEvent.listTitle')" :subtitle="t('rewardEvent.listSubtitle')">
            <template #actions>
                <Button v-if="canManage" as="router-link" to="/reward-events/new" :label="t('rewardEvent.newEvent')" icon="pi pi-plus" size="small" />
            </template>
        </PageTitle>

        <form class="flex flex-wrap items-center gap-2" role="search" :aria-label="t('rewardEvent.searchLabel')" @submit.prevent="search">
            <label class="sr-only" :for="qId">{{ t('rewardEvent.searchLabel') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('rewardEvent.searchPlaceholder')" class="w-full max-w-sm" />
            <label class="sr-only" :for="modeId">{{ t('rewardEvent.col.mode') }}</label>
            <Select v-model="mode" :input-id="modeId" :options="modeOptions" option-label="label" option-value="value" class="w-44" @update:model-value="search" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="status" scrollable class="min-w-0" @update:value="(v: string | number) => selectStatus(String(v))">
            <TabList :aria-label="t('rewardEvent.statusTabs')">
                <Tab v-for="tab in tabs" :key="tab.value" :value="tab.value">
                    <span class="inline-flex items-center gap-1.5">
                        {{ tab.label }}
                        <span class="text-[11px] font-normal tabular-nums text-muted-color">{{ tab.count }}</span>
                    </span>
                </Tab>
            </TabList>
        </Tabs>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="rows"
                data-key="id"
                lazy
                paginator
                :first="first"
                :rows="size"
                :total-records="total"
                :rows-per-page-options="[20, 50, 100]"
                :loading="busy"
                scrollable
                :row-class="() => 'cursor-pointer'"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                @page="onPage"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('rewardEvent.forbidden') : t('rewardEvent.listLoadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || applied.mode || status" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-gift" :title="t('rewardEvent.listEmpty')" :description="t('rewardEvent.listEmptyHint')">
                        <Button v-if="canManage" as="router-link" to="/reward-events/new" :label="t('rewardEvent.newEvent')" icon="pi pi-plus" size="small" />
                    </EmptyState>
                </template>

                <Column :header="t('rewardEvent.col.no')" style="width: 4rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums text-muted-color">{{ data.no }}</span>
                    </template>
                </Column>
                <Column :header="t('rewardEvent.col.name')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <div class="min-w-0">
                            <div class="text-[13px] font-medium">{{ data.name }}</div>
                            <div class="mt-0.5 text-[11px] text-muted-color">{{ UtilRewardEventMessages.tiersSummary(t, data.tiers, locale, true) }}</div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('rewardEvent.col.mode')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <span class="inline-flex items-center gap-1.5 text-[12px]"><i :class="modeIcon(data.mode)" class="text-xs text-muted-color" aria-hidden="true" />{{ t(`rewardEvent.mode.${data.mode}`) }}</span>
                    </template>
                </Column>
                <Column :header="t('rewardEvent.col.period')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div class="text-[12px] tabular-nums">{{ time.shortDateTime(data.startAt) }}</div>
                        <div class="text-[12px] tabular-nums text-muted-color">~ {{ time.shortDateTime(data.endAt) }}</div>
                    </template>
                </Column>
                <Column :header="t('rewardEvent.col.progress')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div class="text-[13px] tabular-nums">{{ progressLine(data) }}</div>
                        <div v-if="data.stats.failed + data.stats.partial || data.stats.skippedLines || data.stats.revoked || data.stats.manualPending" class="flex flex-wrap gap-x-2 text-[11px] tabular-nums">
                            <span v-if="data.stats.failed + data.stats.partial" class="text-red-600 dark:text-red-400">{{ t('rewardEvent.failedN', { n: (data.stats.failed + data.stats.partial).toLocaleString(locale) }) }}</span>
                            <span v-if="data.stats.skippedLines" class="text-muted-color">{{ t('rewardEvent.skippedN', { n: data.stats.skippedLines.toLocaleString(locale) }) }}</span>
                            <span v-if="data.stats.revoked" class="text-muted-color">{{ t('rewardEvent.revokedN', { n: data.stats.revoked.toLocaleString(locale) }) }}</span>
                            <span v-if="data.stats.manualPending" class="text-amber-700 dark:text-amber-400">{{ t('rewardEvent.manualPendingN', { n: data.stats.manualPending.toLocaleString(locale) }) }}</span>
                        </div>
                    </template>
                </Column>
                <Column :header="t('rewardEvent.col.status')" style="min-width: 6rem">
                    <template #body="{ data }">
                        <Tag :value="t(`rewardEvent.status.${data.status}`)" :severity="UtilRewardEvent.statusSeverity(data.status)" />
                    </template>
                </Column>
                <Column :header="t('rewardEvent.col.createdBy')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div class="text-[12px]">{{ UtilRewardEventMessages.operatorName(t, data.createdBy) }}</div>
                        <div class="text-[11px] tabular-nums text-muted-color">{{ time.shortDateTime(data.createdAt) }}</div>
                    </template>
                </Column>
            </DataTable>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { RewardEventMode, RewardEventRow, RewardEventStatus } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import { REWARD_EVENT_MODES, REWARD_EVENT_STATUSES } from '~/composables/rewardevent/api/useRewardEventAdminApi.types';
import UtilRewardEvent from '~/utils/util-reward-event';
import UtilRewardEventMessages from '~/utils/util-reward-event-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('rewardEvent.listTitle') });

const api = useRewardEventAdminApi();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const canManage = computed(() => can('reward-event.manage'));
const qId = useId();
const modeId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const status = ref<string>(typeof route.query.status === 'string' && (REWARD_EVENT_STATUSES as readonly string[]).includes(route.query.status) ? route.query.status : '');
const q = ref('');
const mode = ref<string>('');
const applied = ref<{ q: string; mode: string }>({ q: '', mode: '' });
const rows = ref<RewardEventRow[]>([]);
const counts = ref<Record<RewardEventStatus, number> | null>(null);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const modeOptions = computed(() => [{ value: '', label: t('rewardEvent.allModes') }, ...REWARD_EVENT_MODES.map((m) => ({ value: m, label: t(`rewardEvent.mode.${m}`) }))]);
const tabs = computed(() => {
    const c = counts.value;
    const all = c ? REWARD_EVENT_STATUSES.reduce((sum, s) => sum + c[s], 0) : null;
    return [{ value: '', label: t('common.all'), count: all ?? '—' }, ...REWARD_EVENT_STATUSES.map((s) => ({ value: s, label: t(`rewardEvent.status.${s}`), count: c ? c[s] : '—' }))];
});

function modeIcon(m: RewardEventMode): string {
    if (m === 'FIRST_COME') return 'pi pi-bolt';
    if (m === 'SELECTION') return 'pi pi-trophy';
    return 'pi pi-users';
}

/** 「지급 완료 / 대상」 — 선착순은 인원 기준, 지급 전 일괄은 「지급 전」 */
function progressLine(row: RewardEventRow): string {
    const done = (row.stats.granted + row.stats.partial + row.stats.skipped).toLocaleString(locale.value);
    if (row.mode === 'FIRST_COME') return t('rewardEvent.progressOf', { done, total: (row.firstComeLimit ?? 0).toLocaleString(locale.value) });
    if (row.mode === 'BULK' && row.stats.targets === 0) return t('rewardEvent.notGrantedYet');
    const quota = row.mode === 'SELECTION' ? row.tiers.reduce((n, tier) => n + tier.quota, 0) : row.stats.targets;
    return t('rewardEvent.progressOf', { done, total: quota.toLocaleString(locale.value) });
}

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ status: (status.value || undefined) as RewardEventStatus | undefined, mode: (applied.value.mode || undefined) as RewardEventMode | undefined, q: applied.value.q || undefined, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        counts.value = found.counts;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        total.value = 0;
        loadError.value = toApiError(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function search(): void {
    applied.value = { q: q.value.trim(), mode: mode.value };
    first.value = 0;
    void reload();
}

function selectStatus(value: string): void {
    status.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, status: value || undefined } });
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

function onRowClick(event: DataTableRowClickEvent): void {
    void router.push(`/reward-events/${encodeURIComponent((event.data as RewardEventRow).id)}`);
}

onMounted(reload);
</script>
