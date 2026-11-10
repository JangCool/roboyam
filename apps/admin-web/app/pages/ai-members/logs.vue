<template>
    <!--
        AI 회원 — 활동 기록(`/ai-members/logs`). 계약 정책 14(요청 모델·토큰 수·결과·검수자). ⚠ 목업(`useAiMemberAdminApi` // MOCK, 화면 확정 전 2026-11-10).
        표: 시각 · 계정 · 종류(글/댓글/답글) · 대상(게시판 · 글 제목) · 결과(게시·대기·버림·실패 — 실패는 이유 한 줄) · 모델(실제로 부른 모델) · 토큰(입력 / 출력) · 검수자.
        거르기: 계정 · 결과 · 종류 — 서버 쪽 쪽 나누기(20줄), 머리에 이 조건의 토큰 합.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('aiMember.logsTitle')" :subtitle="t('aiMember.logsSubtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('aiMember.refresh')" size="small" severity="secondary" variant="outlined" :loading="busy" @click="load" />
            </template>
        </PageTitle>
        <AiMemberNav :pending="pendingTotal" />

        <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
            <div class="flex items-center gap-2">
                <label :for="ids.account" class="text-sm font-medium">{{ t('aiMember.filterAccount') }}</label>
                <Select v-model="filters.aiMemberId" :input-id="ids.account" :options="accountOptions" option-label="label" option-value="value" class="w-44" @update:model-value="changed" />
            </div>
            <div class="flex items-center gap-2">
                <label :for="ids.result" class="text-sm font-medium">{{ t('aiMember.filterResult') }}</label>
                <Select v-model="filters.result" :input-id="ids.result" :options="resultOptions" option-label="label" option-value="value" class="w-32" @update:model-value="changed" />
            </div>
            <div class="flex items-center gap-2">
                <label :for="ids.kind" class="text-sm font-medium">{{ t('aiMember.filterKind') }}</label>
                <Select v-model="filters.kind" :input-id="ids.kind" :options="kindOptions" option-label="label" option-value="value" class="w-32" @update:model-value="changed" />
            </div>
            <Button v-if="filtered" type="button" :label="t('aiMember.filterReset')" size="small" severity="secondary" variant="text" @click="resetFilters" />
        </div>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <div v-if="page" class="flex justify-end border-b border-(--line) px-3 py-2 text-xs tabular-nums text-muted-color">{{ t('aiMember.tokenSum', { n: page.tokenSum.toLocaleString() }) }}</div>
            <DataTable
                :value="page?.items ?? []"
                data-key="id"
                lazy
                paginator
                :first="(filters.page - 1) * UtilAiMember.LOG_PAGE_SIZE"
                :rows="UtilAiMember.LOG_PAGE_SIZE"
                :total-records="page?.total ?? 0"
                :loading="busy && !!page"
                scrollable
                :size="prefs.density === 'compact' ? 'small' : undefined"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
                class="min-w-0"
                @page="onPage"
            >
                <template #empty>
                    <div v-if="busy && !page" class="flex flex-col gap-2 py-2" role="status" :aria-label="t('common.loading')">
                        <Skeleton v-for="n in 6" :key="n" height="2.25rem" />
                    </div>
                    <ErrorState v-else-if="loadError" :title="t('aiMember.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load" />
                    <EmptyState v-else-if="filtered" icon="pi pi-filter-slash" :title="t('aiMember.logsFilteredEmpty')" />
                    <EmptyState v-else icon="pi pi-history" :title="t('aiMember.logsEmpty')" />
                </template>

                <Column :header="t('aiMember.logCol.at')" style="width: 8.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums text-muted-color" :title="time.full(data.at)">{{ time.listLabel(data.at) }}</span>
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.account')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <span class="text-[13px] font-medium">{{ data.account.nickname }}</span>
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.kind')" style="width: 5rem">
                    <template #body="{ data }">
                        <span class="text-[13px]">{{ t(`aiMember.kind.${data.kind}`) }}</span>
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.target')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <span class="line-clamp-2 text-[13px] [overflow-wrap:anywhere]">{{ data.target }}</span>
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.result')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag :value="t(`aiMember.result.${data.result}`)" :severity="UtilAiMember.resultSeverity(data.result)" class="ai-member-tag text-[11px]!" />
                            <small v-if="data.failReason" class="text-[11px] leading-snug text-red-700 dark:text-red-400">{{ data.failReason }}</small>
                        </div>
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.model')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <AiModelTag :provider="data.provider" :model="data.model" />
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.tokens')" style="width: 9rem">
                    <template #body="{ data }">
                        <span class="block text-right text-[12px] tabular-nums">{{ t('aiMember.tokensPair', { input: data.tokens.input.toLocaleString(), output: data.tokens.output.toLocaleString() }) }}</span>
                    </template>
                </Column>
                <Column :header="t('aiMember.logCol.reviewer')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <span class="text-[12px]" :class="data.reviewer ? '' : 'text-muted-color'">{{ data.reviewer ?? t('aiMember.reviewerNone') }}</span>
                    </template>
                </Column>
            </DataTable>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import { AI_DRAFT_KINDS, AI_LOG_RESULTS, type AiDraftKind, type AiLogPage, type AiLogResult, type AiMemberListView } from '~/composables/aimember/api/useAiMemberAdminApi.types';
import UtilAiMember from '~/utils/util-ai-member';

const { t } = useI18n();
useSeoMeta({ title: () => t('aiMember.logsTitle') });

const api = useAiMemberAdminApi();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const ids = { account: useId(), result: useId(), kind: useId() };

interface LogFilters {
    aiMemberId: string;
    result: AiLogResult | 'ALL';
    kind: AiDraftKind | 'ALL';
    page: number;
}

const filters = reactive<LogFilters>({ aiMemberId: '', result: 'ALL', kind: 'ALL', page: 1 });
const filtered = computed(() => !!filters.aiMemberId || filters.result !== 'ALL' || filters.kind !== 'ALL');

const page = ref<AiLogPage | null>(null);
const accounts = ref<AiMemberListView | null>(null);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const pendingTotal = computed(() => (accounts.value ? accounts.value.items.reduce((sum, row) => sum + row.pendingDrafts, 0) : null));
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const accountOptions = computed(() => [{ value: '', label: t('aiMember.filterAccountAll') }, ...(accounts.value?.items ?? []).map((row) => ({ value: row.id, label: row.member.nickname }))]);
const resultOptions = computed(() => [{ value: 'ALL', label: t('aiMember.resultAll') }, ...AI_LOG_RESULTS.map((value) => ({ value, label: t(`aiMember.result.${value}`) }))]);
const kindOptions = computed(() => [{ value: 'ALL', label: t('aiMember.kindAll') }, ...AI_DRAFT_KINDS.map((value) => ({ value, label: t(`aiMember.kind.${value}`) }))]);

let seq = 0;
async function load(): Promise<void> {
    const mine = ++seq;
    busy.value = true;
    loadError.value = null;
    try {
        const [found, list] = await Promise.all([
            api.logs({ aiMemberId: filters.aiMemberId || undefined, result: filters.result, kind: filters.kind, page: filters.page, size: UtilAiMember.LOG_PAGE_SIZE }),
            accounts.value ? Promise.resolve(accounts.value) : api.list(),
        ]);
        if (mine !== seq) return;
        page.value = found;
        accounts.value = list;
    } catch (e) {
        if (mine !== seq) return;
        page.value = null;
        loadError.value = errorOf(e);
    } finally {
        if (mine === seq) busy.value = false;
    }
}

onMounted(() => void load());

/** 조건을 바꾸면 1쪽부터 */
function changed(): void {
    filters.page = 1;
    void load();
}

function resetFilters(): void {
    Object.assign(filters, { aiMemberId: '', result: 'ALL', kind: 'ALL', page: 1 });
    void load();
}

function onPage(event: { first: number }): void {
    filters.page = Math.floor(event.first / UtilAiMember.LOG_PAGE_SIZE) + 1;
    void load();
}
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.ai-member-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
