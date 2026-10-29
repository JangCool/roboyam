<template>
    <!--
        공구 › Q&A(`/group-buy-qna`) — 문의 대기열(계약 「Q&A 규칙」·결정 35·60, 2b-6).
        · 상태 탭(건수): 답변 대기(오래 기다린 것이 위) · 답변 완료 · 숨김 · 전체(작성자가 지운 것 포함). 「기한 지난 것만」(2영업일) · 찾기(공구 제목·번호 · 닉네임 · 회원 번호).
        · 한 줄: 작성 시각 · 공구(차수) · 회원 · 문의 앞부분(비밀글·자동 비밀글·신고 수) · 상태(경과 영업일 · 기한 지남).
        · 줄을 누르면 상세 서랍(`?question=ID`): 답변·답변 수정(이력) · 숨기기/숨김 풀기(사유 5~200자).
        권한: 화면 `commerce.order.read` · 답변·숨김 `commerce.order.manage`. 목록·상세 조회는 서버가 개인정보 열람 감사(`GROUPBUY_QNA_VIEW`)를 남긴다.
        서버 호출은 `useGroupBuyQnaAdminApi`(서버 연결 2026-10-29).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('groupBuyQna.title')" :subtitle="t('groupBuyQna.subtitle')" />
        <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-eye mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuyQna.auditNote') }}</p>

        <form class="flex flex-wrap items-center gap-x-4 gap-y-2" role="search" :aria-label="t('groupBuyQna.search')" @submit.prevent="search">
            <div class="flex items-center gap-1.5">
                <Checkbox v-model="overdueOnly" binary :input-id="overdueId" />
                <label :for="overdueId" class="text-[13px]">{{ t('groupBuyQna.overdueOnly') }}</label>
                <Tag v-if="overdueCount > 0" :value="t('groupBuyQna.overdueCount', { count: overdueCount })" severity="danger" icon="pi pi-clock" />
            </div>
            <label class="sr-only" :for="qId">{{ t('groupBuyQna.filterQ') }}</label>
            <InputText :id="qId" v-model="q" type="search" maxlength="100" autocomplete="off" :placeholder="t('groupBuyQna.filterQPlaceholder')" class="w-full max-w-xs" />
            <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
        </form>

        <Tabs :value="tab" scrollable class="min-w-0" @update:value="(v: string | number) => selectTab(String(v) as GroupBuyQnaTab)">
            <TabList :aria-label="t('groupBuyQna.tabsLabel')">
                <Tab v-for="item in tabs" :key="item.value" :value="item.value">
                    <span class="inline-flex items-center gap-1.5">{{ item.label }}<span v-if="item.count !== null" class="text-[11px] font-normal tabular-nums text-muted-color">{{ item.count }}</span></span>
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
                    <ErrorState v-if="loadError" :code="loadError.status === 403 ? '403' : '!'" :title="loadError.status === 403 ? t('groupBuyQna.forbidden') : t('groupBuyQna.loadFailed')" :description="loadError.message" :retry="loadError.status !== 403" :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <EmptyState v-else-if="applied.q || applied.overdue || tab !== 'OPEN'" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')" />
                    <EmptyState v-else icon="pi pi-check-circle" :title="t('groupBuyQna.empty')" :description="t('groupBuyQna.emptyHint')" />
                </template>

                <Column :header="t('groupBuyQna.col.createdAt')" style="min-width: 8.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.shortDateTime(data.createdAt) }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuyQna.col.groupBuy')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <span class="block max-w-56 truncate text-[13px]">{{ data.groupBuy.title }}</span>
                        <span class="block text-[11px] text-muted-color">#{{ data.groupBuy.id }} · {{ t('groupBuyQna.roundN', { n: data.groupBuy.roundNo }) }}</span>
                    </template>
                </Column>
                <Column :header="t('groupBuyQna.col.member')" style="min-width: 7rem"><template #body="{ data }"><PaymentMemberLabel :member="data.member" /></template></Column>
                <Column :header="t('groupBuyQna.col.question')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <div class="flex flex-col gap-1">
                            <span class="line-clamp-2 text-[13px] [overflow-wrap:anywhere]"><i v-if="data.secret" class="pi pi-lock mr-1 text-[11px] text-muted-color" :aria-label="t('groupBuyQna.secret')" />{{ data.preview }}</span>
                            <div v-if="data.forcedSecret || data.reportCount > 0 || data.deleted" class="flex flex-wrap gap-1">
                                <Tag v-if="data.forcedSecret" :value="t('groupBuyQna.forcedSecret')" severity="warn" />
                                <Tag v-if="data.reportCount > 0" :value="t('groupBuyQna.reports', { count: data.reportCount })" severity="danger" icon="pi pi-flag" />
                                <Tag v-if="data.deleted" :value="t('groupBuyQna.deleted')" severity="secondary" />
                            </div>
                        </div>
                    </template>
                </Column>
                <Column :header="t('groupBuyQna.col.status')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <div class="flex flex-col items-start gap-0.5">
                            <Tag v-if="data.hidden" :value="t('groupBuyQna.hidden')" severity="secondary" icon="pi pi-eye-slash" />
                            <Tag v-else :value="t(`groupBuyQna.status.${data.status}`)" :severity="data.status === 'ANSWERED' ? 'success' : 'warn'" />
                            <Tag v-if="data.overdue" :value="t('groupBuyQna.overdue', { n: data.elapsedBusinessDays })" severity="danger" icon="pi pi-clock" />
                            <span v-else-if="data.status === 'OPEN' && !data.hidden && !data.deleted" class="text-[11px] text-muted-color">{{ t('groupBuyQna.elapsed', { n: data.elapsedBusinessDays }) }}</span>
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <GroupBuyQnaDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :busy="working"
            :can-manage="can('commerce.order.manage')"
            :answer-error="answerError"
            :answer-field-error="answerFieldError"
            @retry="loadDetail"
            @answer="submitAnswer"
            @hide="openHide(true)"
            @unhide="openHide(false)"
        />

        <PaymentReasonDialog
            v-model:visible="hideOpen"
            :title="hideMode ? t('groupBuyQna.hideTitle') : t('groupBuyQna.unhideTitle')"
            :description="hideMode ? t('groupBuyQna.hideBody') : t('groupBuyQna.unhideBody')"
            :confirm-label="hideMode ? t('groupBuyQna.actHide') : t('groupBuyQna.actUnhide')"
            :tone="hideMode ? 'danger' : 'primary'"
            :busy="working === 'hide'"
            :error="hideError"
            :field-error="hideFieldError"
            @submit="submitHide"
        />
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupBuyQnaDetail, GroupBuyQnaListResponse, GroupBuyQnaQuery, GroupBuyQnaRow, GroupBuyQnaTab } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';
import { GROUP_BUY_QNA_TABS } from '~/composables/groupbuy/api/useGroupBuyQnaAdminApi.types';

const { t } = useI18n();
useSeoMeta({ title: () => t('groupBuyQna.title') });

const api = useGroupBuyQnaAdminApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const route = useRoute();
const router = useRouter();

const overdueId = useId();
const qId = useId();
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

const queryString = (key: string) => (typeof route.query[key] === 'string' ? (route.query[key] as string) : '');
/** 기본 탭은 「답변 대기」(처리할 것) — 대시보드 「기한 지난 Q&A」는 `?overdue=1` 로 온다 */
const tab = ref<GroupBuyQnaTab>((GROUP_BUY_QNA_TABS as readonly string[]).includes(queryString('tab')) ? (queryString('tab') as GroupBuyQnaTab) : 'OPEN');
const overdueOnly = ref(queryString('overdue') === '1');
const q = ref('');
const applied = ref<Omit<GroupBuyQnaQuery, 'page' | 'size' | 'tab'>>({ overdue: overdueOnly.value || undefined });

const rows = ref<GroupBuyQnaRow[]>([]);
const counts = ref<GroupBuyQnaListResponse['counts'] | null>(null);
const overdueCount = ref(0);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

const tabs = computed(() => GROUP_BUY_QNA_TABS.map((value) => ({ value, label: t(`groupBuyQna.tab.${value}`), count: value === 'ALL' ? null : (counts.value?.[value] ?? '—') })));

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.list({ ...applied.value, tab: tab.value, page: Math.floor(first.value / size.value), size: size.value });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
        counts.value = found.counts;
        overdueCount.value = found.overdueCount;
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
    applied.value = { overdue: overdueOnly.value || undefined, q: q.value.trim() || undefined };
    first.value = 0;
    void router.replace({ query: { ...route.query, overdue: overdueOnly.value ? '1' : undefined } });
    void reload();
}

function selectTab(value: GroupBuyQnaTab): void {
    tab.value = value;
    first.value = 0;
    void router.replace({ query: { ...route.query, tab: value } });
    void reload();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

// ── 상세 서랍 ──
const drawerOpen = ref(false);
const drawerId = ref<string | null>(null);
const drawerSummary = ref<GroupBuyQnaRow | null>(null);
const drawerDetail = ref<GroupBuyQnaDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);

let dseq = 0;
async function loadDetail(): Promise<void> {
    const id = drawerId.value;
    if (!id) return;
    const mine = ++dseq;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.detail(id);
        if (mine === dseq) drawerDetail.value = found;
    } catch (e) {
        if (mine !== dseq) return;
        drawerDetail.value = null;
        drawerError.value = toApiError(e);
    } finally {
        if (mine === dseq) drawerLoading.value = false;
    }
}

function show(id: string, row: GroupBuyQnaRow | null): void {
    if (drawerId.value !== id) drawerDetail.value = null;
    drawerId.value = id;
    drawerSummary.value = row;
    answerError.value = '';
    answerFieldError.value = '';
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as GroupBuyQnaRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, question: row.id } });
}

// 주소의 `?question=` 과 서랍을 맞춘다(바로가기·뒤로 가기)
watch(
    () => route.query.question,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open || !route.query.question) return;
    const { question: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 동작 ──
const working = ref<'answer' | 'hide' | null>(null);
const answerError = ref('');
const answerFieldError = ref('');

async function submitAnswer(body: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    const isFirst = !d.answer;
    working.value = 'answer';
    answerError.value = '';
    answerFieldError.value = '';
    try {
        drawerDetail.value = await api.answer(d.id, { body });
        toast.success(isFirst ? t('groupBuyQna.answered') : t('groupBuyQna.answerUpdated'));
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields?.body) answerFieldError.value = failure.fields.body;
        else answerError.value = failure.message;
        if (failure.code === 'GROUP_BUY_QUESTION_DELETED') void loadDetail();
    } finally {
        working.value = null;
    }
}

const hideOpen = ref(false);
/** true = 숨기기, false = 숨김 풀기 */
const hideMode = ref(true);
const hideError = ref('');
const hideFieldError = ref('');

function openHide(hide: boolean): void {
    hideMode.value = hide;
    hideError.value = '';
    hideFieldError.value = '';
    hideOpen.value = true;
}

async function submitHide(reason: string): Promise<void> {
    const d = drawerDetail.value;
    if (!d) return;
    working.value = 'hide';
    hideError.value = '';
    hideFieldError.value = '';
    try {
        drawerDetail.value = hideMode.value ? await api.hide(d.id, reason) : await api.unhide(d.id, reason);
        toast.success(hideMode.value ? t('groupBuyQna.hiddenDone') : t('groupBuyQna.unhiddenDone'));
        hideOpen.value = false;
        void reload();
    } catch (e) {
        const failure = toApiError(e);
        if (failure.code === 'VALIDATION' && failure.fields?.reason) hideFieldError.value = failure.fields.reason;
        else hideError.value = failure.message;
    } finally {
        working.value = null;
    }
}

onMounted(() => void reload());
</script>
