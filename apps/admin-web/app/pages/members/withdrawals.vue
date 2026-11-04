<template>
    <!--
        회원 › 탈퇴 관리(`/members/withdrawals`) — 탈퇴를 신청한 회원의 남은 일과 완료·취소 기록(member-withdrawal-contract 「관리자」, 분석 문서 5절 6번). 서버 호출은 `useMemberWithdrawalApi`(서버 `MemberWithdrawalAdminController`).
        · 목록의 남은 일 개수는 스냅샷(진행 중은 배치가 30분마다 다시 센 것) — 칸 이름에 적는다. 서랍의 진행 중 건은 지금 다시 센 값.
        · 요약 숫자 4칸: 진행 중 · 오늘 완료 · 이번 달 완료 · 10일 넘긴 진행 중(주의 색). 기간 조건과 무관.
        · 상태 탭(진행 중 / 완료 / 취소 / 전체) · 조건: 신청일·완료일 기간, 회원 번호(완료 회원은 닉네임·이메일이 없어 번호만). 조건은 주소(`?status=&requestedFrom=…`)에 남아 대시보드 카드가 바로 연다.
        · 표: 회원 번호 · 닉네임(완료는 「탈퇴한 회원」) · 상태 · 신청 시각(10일 넘긴 진행 중은 「N일 지남」 주의 색) · 남은 일 종류별 칩 · 예상 완료 · 10일 안내 메일 · 결제 이력(연락용 이메일 보관) · 신청 경로.
        · 줄을 누르면 서랍(`?id=`, `MemberWithdrawalDrawer`): 남은 일(관리자 화면 링크) · 고른 것 · 포기 동의 · 사유·메모 · 처리 이력 · 완료 + 결제 이력이면 연락용 이메일 열람(기존 `useWithdrawnContact` + `MemberReasonDialog`).
        · 탈퇴 사유 분포: 막대 하나(사유 6종 + 고르지 않음) — 신청일 기간을 따른다(상태 무관).
        · 운영자 대리 취소(사용자 결정 2026-11-04): 진행 중 + 회원이 신청한 탈퇴만, 회원이 고객센터·전화 등으로 요청했을 때(`MemberWithdrawalCancelDialog` — 본인 요청 확인·경로·메모). 운영자 강제 탈퇴 건은 불가. 강제 탈퇴는 회원 관리의 상세 서랍에서 한다.
        권한: 화면 `member.read`(메뉴) · 연락용 이메일 열람 `member.withdrawn.contact` · 대리 취소 `member.withdraw`.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('memberWithdrawal.title')" :subtitle="t('memberWithdrawal.subtitle')" />

        <!-- 요약 숫자 -->
        <section :aria-label="t('drawerSummary.label')" class="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <template v-if="summaryError">
                <div class="sm:col-span-2 xl:col-span-4">
                    <ErrorState :title="t('memberWithdrawal.summary.loadFailed')" :description="summaryError.message" retry :retrying="summaryLoading" @retry="loadSummary" />
                </div>
            </template>
            <template v-else>
                <button
                    v-for="card in summaryCards"
                    :key="card.key"
                    type="button"
                    class="flex flex-col items-start gap-1 rounded-xl border px-4 py-3 text-left transition-colors hover:bg-surface-50 dark:hover:bg-surface-900"
                    :class="card.tone === 'warn' && card.count ? 'border-amber-300 dark:border-amber-800' : 'border-(--line) bg-(--p-content-background)'"
                    v-tooltip.bottom="card.note"
                    :aria-label="`${card.label} ${card.count ?? ''}`"
                    @click="applyCard(card.key)"
                >
                    <span class="text-xs text-muted-color">{{ card.label }}</span>
                    <Skeleton v-if="card.count === null" width="3rem" height="1.75rem" />
                    <span v-else class="text-2xl font-semibold tabular-nums" :class="card.tone === 'warn' && card.count ? 'text-amber-700 dark:text-amber-400' : ''">{{ card.count.toLocaleString(locale) }}</span>
                </button>
            </template>
        </section>

        <!-- 상태 탭 -->
        <Tabs :value="filter.status || 'ALL'" scrollable class="min-w-0" @update:value="(v: string | number) => selectStatus(String(v))">
            <TabList :aria-label="t('memberWithdrawal.filter.status')">
                <Tab v-for="s in STATUS_TABS" :key="s" :value="s">{{ s === 'ALL' ? t('memberWithdrawal.filter.statusAll') : t(`memberWithdrawal.status.${s}`) }}</Tab>
            </TabList>
        </Tabs>

        <!-- 조건 -->
        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('memberWithdrawal.searchLabel')" novalidate @submit.prevent="search">
            <FilterRow :label="t('memberWithdrawal.filter.requested')">
                <DateRangeField v-model:from="filter.requestedFrom" v-model:to="filter.requestedTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('memberWithdrawal.filter.requestedFrom')" :to-label="t('memberWithdrawal.filter.requestedTo')" />
            </FilterRow>
            <FilterRow v-if="filter.status !== 'SCHEDULED' && filter.status !== 'CANCELED'" :label="t('memberWithdrawal.filter.completed')">
                <DateRangeField v-model:from="filter.completedFrom" v-model:to="filter.completedTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('memberWithdrawal.filter.completedFrom')" :to-label="t('memberWithdrawal.filter.completedTo')" />
            </FilterRow>
            <FilterRow :label="t('memberWithdrawal.filter.memberNo')" :label-for="`${uid}-memberNo`">
                <div class="flex min-w-0 flex-col gap-1">
                    <InputText :id="`${uid}-memberNo`" v-model="filter.memberNo" type="search" maxlength="40" :placeholder="t('memberWithdrawal.filter.memberNoPlaceholder')" autocomplete="off" class="w-full max-w-xs font-mono" />
                    <span class="text-xs text-muted-color">{{ t('memberWithdrawal.filter.memberNoHelp') }}</span>
                </div>
            </FilterRow>
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('memberWithdrawal.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
            </div>
        </form>

        <!-- 표 -->
        <div class="flex min-w-0 flex-col gap-2">
            <p class="m-0 text-[13px] font-medium" aria-live="polite">{{ t('memberWithdrawal.total', { count: total.toLocaleString(locale) }) }}</p>
            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="rows"
                    data-key="id"
                    :selection="drawerOpen ? drawerSummary : null"
                    selection-mode="single"
                    :meta-key-selection="false"
                    lazy
                    paginator
                    :first="first"
                    :rows="size"
                    :total-records="total"
                    :rows-per-page-options="[20, 50, 100]"
                    :loading="busy"
                    :size="prefs.density === 'compact' ? 'small' : undefined"
                    :sort-field="sortField"
                    :sort-order="sortOrder"
                    scrollable
                    :row-class="() => 'cursor-pointer'"
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="onRowClick"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('memberWithdrawal.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                        <div v-else-if="busy" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="filtered" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('memberWithdrawal.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-user-minus" :title="t('memberWithdrawal.empty')" :description="t('memberWithdrawal.emptyHint')" />
                    </template>

                    <Column :header="t('memberWithdrawal.col.memberNo')" style="min-width: 11rem">
                        <template #body="{ data }"><code class="text-[12px] tabular-nums text-muted-color">{{ data.memberNo }}</code></template>
                    </Column>
                    <Column :header="t('memberWithdrawal.col.nickname')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span v-if="data.nickname" class="block truncate text-[13px]" :title="data.nickname">{{ data.nickname }}</span>
                            <span v-else class="inline-flex items-center gap-1 text-[13px] text-muted-color"><i class="pi pi-user-minus text-[11px]" aria-hidden="true" />{{ t('memberWithdrawal.withdrawnMember') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('memberWithdrawal.col.status')" style="min-width: 7rem">
                        <template #body="{ data }"><Tag :value="t(`memberWithdrawal.status.${data.status}`)" :icon="UtilMemberWithdrawal.statusIcon(data.status)" :severity="UtilMemberWithdrawal.statusSeverity(data.status)" /></template>
                    </Column>
                    <Column field="requestedAt" sortable :header="t('memberWithdrawal.col.requestedAt')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <div class="flex flex-col gap-0.5">
                                <span class="text-[12px] tabular-nums">{{ time.full(data.requestedAt) }}</span>
                                <span v-if="UtilMemberWithdrawal.isOverdue(data.status, data.requestedAt, now)" class="inline-flex items-center gap-1 text-[11px] font-semibold text-amber-700 dark:text-amber-400">
                                    <i class="pi pi-exclamation-triangle" aria-hidden="true" />{{ t('memberWithdrawal.overdueDays', { days: UtilMemberWithdrawal.daysSince(data.requestedAt, now) }) }}
                                </span>
                            </div>
                        </template>
                    </Column>
                    <Column :header="t('memberWithdrawal.col.pending')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <span v-if="!UtilMemberWithdrawal.pendingChips(data.pendingCounts).length" class="text-[13px] text-muted-color">{{ t('memberWithdrawal.pendingNone') }}</span>
                            <div v-else class="flex flex-wrap items-center gap-1">
                                <Tag v-for="chip in UtilMemberWithdrawal.pendingChips(data.pendingCounts)" :key="chip.code" :value="`${t(`memberWithdrawal.pending.${chip.code}`)} ${chip.count}`" :icon="UtilMemberWithdrawal.pendingIcon(chip.code)" :severity="data.status === 'SCHEDULED' ? 'warn' : 'secondary'" />
                            </div>
                        </template>
                    </Column>
                    <Column field="expectedCompleteAt" sortable :header="filter.status === 'COMPLETED' ? t('memberWithdrawal.col.completedAt') : t('memberWithdrawal.col.expectedCompleteAt')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span v-if="data.status === 'COMPLETED'" class="text-[12px] tabular-nums" :class="EMPH.date">{{ time.full(data.completedAt) }}</span>
                            <span v-else-if="data.status === 'CANCELED'" class="text-[12px] text-muted-color tabular-nums">{{ t('memberWithdrawal.event.CANCELED') }} · {{ time.full(data.canceledAt) }}</span>
                            <span v-else-if="data.expectedCompleteAt" class="text-[12px] tabular-nums">{{ time.full(data.expectedCompleteAt) }}</span>
                            <span v-else class="text-[12px] text-muted-color">{{ t('memberWithdrawal.pendingUnknownEnd') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('memberWithdrawal.col.progressMail')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <span v-if="data.progressNotifiedAt" class="inline-flex items-center gap-1 text-[12px]"><i class="pi pi-envelope text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.progressMailSent') }}</span>
                            <span v-else class="text-[12px] text-muted-color">{{ t('memberWithdrawal.progressMailNotSent') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('memberWithdrawal.col.paymentHistory')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span v-if="data.hasPaymentHistory" class="inline-flex items-center gap-1 text-[12px]"><i class="pi pi-inbox text-muted-color" aria-hidden="true" />{{ t('memberWithdrawal.paymentHistoryYes') }}</span>
                            <span v-else class="text-[12px] text-muted-color">{{ t('memberWithdrawal.paymentHistoryNo') }}</span>
                        </template>
                    </Column>
                    <Column :header="t('memberWithdrawal.col.source')" style="min-width: 7rem">
                        <template #body="{ data }"><Tag :value="t(`memberWithdrawal.source.${data.source}`)" :icon="data.source === 'OPERATOR' ? 'pi pi-user-minus' : 'pi pi-user'" :severity="data.source === 'OPERATOR' ? 'danger' : 'secondary'" /></template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <!-- 탈퇴 사유 분포 — 신청일 기간을 따른다 -->
        <AppGadget :title="t('memberWithdrawal.reasonsTitle')">
            <template #title-extra>
                <span v-if="reasons" class="text-[11px] tabular-nums text-muted-color">{{ t('memberWithdrawal.reasonsTotal', { count: reasons.total.toLocaleString(locale) }) }}</span>
            </template>
            <ErrorState v-if="reasonsError" :title="t('memberWithdrawal.reasonsLoadFailed')" :description="reasonsError.message" retry :retrying="reasonsLoading" @retry="loadReasons" />
            <div v-else-if="!reasons" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')"><Skeleton height="1.25rem" /><Skeleton height="1rem" width="60%" /></div>
            <template v-else>
                <p class="m-0 mb-2 text-xs text-muted-color">{{ t('memberWithdrawal.reasonsHint') }}</p>
                <p v-if="!segments.length" class="m-0 text-[13px] text-muted-color">{{ t('memberWithdrawal.reasonsEmpty') }}</p>
                <template v-else>
                    <div class="flex h-5 w-full overflow-hidden rounded-full bg-surface-100 dark:bg-surface-800" role="img" :aria-label="reasonsAria">
                        <div v-for="seg in segments" :key="UtilMemberWithdrawal.reasonKey(seg.reason)" v-tooltip.top="`${t(`memberWithdrawal.reason.${UtilMemberWithdrawal.reasonKey(seg.reason)}`)} ${seg.count} (${seg.percent}%)`" class="h-full" :class="seg.colorClass" :style="{ width: `${seg.percent}%` }" />
                    </div>
                    <ul class="m-0 mt-3 flex list-none flex-wrap gap-x-4 gap-y-1.5 p-0 text-[12px]">
                        <li v-for="seg in segments" :key="UtilMemberWithdrawal.reasonKey(seg.reason)" class="inline-flex items-center gap-1.5">
                            <span class="inline-block size-2.5 rounded-sm" :class="seg.colorClass" aria-hidden="true" />
                            <span>{{ t(`memberWithdrawal.reason.${UtilMemberWithdrawal.reasonKey(seg.reason)}`) }}</span>
                            <span class="tabular-nums text-muted-color">{{ seg.count }} · {{ seg.percent }}%</span>
                        </li>
                    </ul>
                </template>
            </template>
        </AppGadget>

        <MemberWithdrawalDrawer
            v-model:visible="drawerOpen"
            :summary="drawerSummary"
            :detail="drawerDetail"
            :loading="drawerLoading"
            :error="drawerError"
            :withdrawn-contact="contactView"
            :can-cancel="canCancel"
            :cancel-busy="cancelBusy"
            @retry="loadDetail"
            @retry-contact="contact.retry"
            @reveal-contact="revealOpen = true"
            @mask-contact="contact.mask"
            @cancel="openCancel"
        />

        <!-- 운영자 대리 취소(회원 요청 시) -->
        <MemberWithdrawalCancelDialog
            v-if="drawerDetail"
            v-model:visible="cancelOpen"
            :member-name="drawerDetail.nickname ?? t('memberWithdrawal.withdrawnMember')"
            :member-no="drawerDetail.memberNo"
            :prior-status="drawerDetail.priorStatus"
            :busy="cancelBusy"
            :error="cancelError"
            :field-errors="cancelFieldErrors"
            @submit="submitCancel"
        />

        <MemberReasonDialog
            v-model:visible="revealOpen"
            :title="t('withdrawnContact.revealTitle')"
            :label="t('withdrawnContact.revealReasonLabel')"
            :submit-label="t('withdrawnContact.reveal')"
            submit-icon="pi pi-eye"
            :notice="t('withdrawnContact.revealNotice')"
            :busy="revealBusy"
            :error="revealError"
            :field-error="revealFieldError"
            @submit="submitReveal"
        />
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent, DataTableSortEvent } from 'primevue/datatable';
import UtilTime from '#layers/display-time/utils/util-time';
import type { ApiError } from '~/composables/common/useApiClient';
import type { WithdrawnContactView } from '~/components/member/MemberDetailContent.types';
import type { MemberWithdrawalDetail, MemberWithdrawalReasonStats, MemberWithdrawalRow, MemberWithdrawalSummary, WithdrawalCancelRequest } from '~/composables/member/api/useMemberWithdrawalApi.types';
import UtilBizApplication from '~/utils/util-biz-application';
import UtilDateRange from '~/utils/util-date-range';
import UtilMemberWithdrawal, { type MemberWithdrawalFilter } from '~/utils/util-member-withdrawal';

const STATUS_TABS = ['SCHEDULED', 'COMPLETED', 'CANCELED', 'ALL'] as const;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('memberWithdrawal.title') });

const api = useMemberWithdrawalApi();
const errorOf = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const values = useDisplayValues();
const { prefs } = useLayoutPrefs();
const { can } = useAdminPermission();
/** 대리 취소 권한(`member.withdraw` — 강제 탈퇴와 같은 권한, 서버 `Permission.MEMBER_WITHDRAW`) */
const canCancel = computed(() => can('member.withdraw'));
const route = useRoute();
const router = useRouter();
const uid = useId();
const EMPH = UtilBizApplication.EMPH;
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
/** 「N일 지남」 판정의 지금 — 화면을 연 때 */
const now = Date.now();

// ── 조건 — 주소에서 시작하고(대시보드 카드·회원 상세 블록이 연다), 검색하면 주소에 남긴다 ─
const filter = reactive<MemberWithdrawalFilter>(UtilMemberWithdrawal.filterFromQuery(route.query as Record<string, unknown>));
const applied = ref<MemberWithdrawalFilter>({ ...filter });
const filtered = computed(() => {
    const a = applied.value;
    return Boolean(a.memberNo || a.requestedFrom || a.requestedTo || a.completedFrom || a.completedTo);
});

// ── 목록 ─
const rows = ref<MemberWithdrawalRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const sortField = ref<string | undefined>('requestedAt');
const sortOrder = ref<number | undefined>(-1);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);

let ticket = 0;
async function reload(): Promise<void> {
    const mine = ++ticket;
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.search({
            ...UtilMemberWithdrawal.toQuery(applied.value, values.value.zoneId),
            sort: sortField.value ? `${sortField.value},${sortOrder.value === 1 ? 'asc' : 'desc'}` : UtilMemberWithdrawal.DEFAULT_SORT,
            page: Math.floor(first.value / size.value),
            size: size.value,
        });
        if (mine !== ticket) return;
        rows.value = found.items;
        total.value = found.total;
    } catch (e) {
        if (mine !== ticket) return;
        rows.value = [];
        total.value = 0;
        loadError.value = errorOf(e);
    } finally {
        if (mine === ticket) busy.value = false;
    }
}

function syncQuery(): void {
    const { id } = route.query;
    const query: Record<string, string> = { ...UtilMemberWithdrawal.filterToQuery(applied.value) };
    if (typeof id === 'string') query.id = id;
    void router.replace({ query });
}

/** 「검색」·Enter — 조건을 굳히고 첫 쪽부터. 사유 분포는 신청일 기간을 따르니 함께 다시 받는다 */
function search(): void {
    applied.value = { ...filter };
    first.value = 0;
    syncQuery();
    void reload();
    void loadReasons();
}

function selectStatus(value: string): void {
    filter.status = value === 'ALL' ? '' : (value as MemberWithdrawalFilter['status']);
    // 진행 중·취소 탭에서는 완료일 조건이 뜻이 없다
    if (filter.status === 'SCHEDULED' || filter.status === 'CANCELED') {
        filter.completedFrom = '';
        filter.completedTo = '';
    }
    search();
}

function resetFilters(): void {
    const empty = UtilMemberWithdrawal.emptyFilter();
    Object.assign(filter, { ...empty, status: filter.status });
    search();
}

function onPage(e: { first: number; rows: number }): void {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

function onSort(e: DataTableSortEvent): void {
    // 서버가 받는 칸만(`requestedAt`·`expectedCompleteAt`·`completedAt`·`canceledAt`·`pendingCount`)
    sortField.value = typeof e.sortField === 'string' && UtilMemberWithdrawal.SORT_FIELDS.includes(e.sortField) ? e.sortField : undefined;
    sortOrder.value = e.sortOrder ?? undefined;
    first.value = 0;
    void reload();
}

// ── 요약 숫자 — 기간 조건과 무관. 카드를 누르면 그 조건으로 거른다 ─
const summary = ref<MemberWithdrawalSummary | null>(null);
const summaryLoading = ref(false);
const summaryError = ref<ApiError | null>(null);

async function loadSummary(): Promise<void> {
    summaryLoading.value = true;
    summaryError.value = null;
    try {
        summary.value = await api.summary();
    } catch (e) {
        summaryError.value = errorOf(e);
    } finally {
        summaryLoading.value = false;
    }
}

type CardKey = 'scheduled' | 'completedToday' | 'completedThisMonth' | 'overdue';
const summaryCards = computed(() => {
    const s = summary.value;
    const days = UtilMemberWithdrawal.OVERDUE_DAYS;
    return [
        { key: 'scheduled' as CardKey, label: t('memberWithdrawal.summary.scheduled'), note: t('memberWithdrawal.summary.scheduledNote'), count: s ? s.scheduled : null, tone: 'normal' },
        { key: 'completedToday' as CardKey, label: t('memberWithdrawal.summary.completedToday'), note: t('memberWithdrawal.summary.completedTodayNote'), count: s ? s.completedToday : null, tone: 'normal' },
        { key: 'completedThisMonth' as CardKey, label: t('memberWithdrawal.summary.completedThisMonth'), note: t('memberWithdrawal.summary.completedThisMonthNote'), count: s ? s.completedThisMonth : null, tone: 'normal' },
        { key: 'overdue' as CardKey, label: t('memberWithdrawal.summary.overdue', { days }), note: t('memberWithdrawal.summary.overdueNote', { days }), count: s ? s.overdue : null, tone: 'warn' },
    ];
});

/** 오늘·이번 달 1일(서비스 날짜 — 요약과 같은 기준) */
function serviceToday(): { today: string; monthStart: string } {
    const today = UtilTime.serviceDayOf(Date.now()) ?? '';
    return { today, monthStart: today ? `${today.slice(0, 8)}01` : '' };
}

function applyCard(key: CardKey): void {
    const { today, monthStart } = serviceToday();
    const empty = UtilMemberWithdrawal.emptyFilter();
    if (key === 'scheduled' || key === 'overdue') Object.assign(filter, { ...empty, status: 'SCHEDULED' });
    else if (key === 'completedToday') Object.assign(filter, { ...empty, status: 'COMPLETED', completedFrom: today, completedTo: today });
    else Object.assign(filter, { ...empty, status: 'COMPLETED', completedFrom: monthStart, completedTo: today });
    if (key === 'overdue') {
        sortField.value = 'requestedAt';
        sortOrder.value = 1;
    }
    search();
}

// ── 사유 분포 — 신청일 기간을 따른다(상태 무관) ─
const reasons = ref<MemberWithdrawalReasonStats | null>(null);
const reasonsLoading = ref(false);
const reasonsError = ref<ApiError | null>(null);
let rticket = 0;

async function loadReasons(): Promise<void> {
    const mine = ++rticket;
    reasonsLoading.value = true;
    reasonsError.value = null;
    try {
        const q = UtilMemberWithdrawal.toQuery(applied.value, values.value.zoneId);
        const found = await api.reasons({ requestedFrom: q.requestedFrom, requestedTo: q.requestedTo });
        if (mine === rticket) reasons.value = found;
    } catch (e) {
        if (mine === rticket) reasonsError.value = errorOf(e);
    } finally {
        if (mine === rticket) reasonsLoading.value = false;
    }
}

const segments = computed(() => (reasons.value ? UtilMemberWithdrawal.reasonSegments(reasons.value.items, reasons.value.total) : []));
const reasonsAria = computed(() => `${t('memberWithdrawal.reasonsBarLabel')}: ${segments.value.map((s) => `${t(`memberWithdrawal.reason.${UtilMemberWithdrawal.reasonKey(s.reason)}`)} ${s.percent}%`).join(', ')}`);

// ── 상세 서랍(`?id=`) ─
const drawerOpen = ref(false);
const drawerId = ref<string | null>(null);
const drawerSummary = ref<MemberWithdrawalRow | null>(null);
const drawerDetail = ref<MemberWithdrawalDetail | null>(null);
const drawerLoading = ref(false);
const drawerError = ref<ApiError | null>(null);

/** 완료 건의 연락용 이메일(분리 보관) — 기존 상태 컴포저블·대화상자 재사용 */
const contact = useWithdrawnContact();
const contactView = computed<WithdrawnContactView>(() => ({
    contact: contact.contact.value,
    loading: contact.loading.value,
    error: contact.error.value,
    revealed: contact.revealed.value,
    remaining: contact.remaining.value,
}));

let dseq = 0;
async function loadDetail(): Promise<void> {
    const id = drawerId.value;
    if (!id) return;
    const mine = ++dseq;
    drawerLoading.value = true;
    drawerError.value = null;
    try {
        const found = await api.detail(id);
        if (mine !== dseq) return;
        drawerDetail.value = found;
        if (found.status === 'COMPLETED' && found.hasPaymentHistory) void contact.open(found.memberNo);
        else contact.clear();
    } catch (e) {
        if (mine !== dseq) return;
        drawerDetail.value = null;
        drawerError.value = errorOf(e);
    } finally {
        if (mine === dseq) drawerLoading.value = false;
    }
}

function show(id: string, row: MemberWithdrawalRow | null): void {
    if (drawerId.value !== id) {
        drawerDetail.value = null;
        contact.clear();
    }
    drawerId.value = id;
    drawerSummary.value = row;
    drawerOpen.value = true;
    void loadDetail();
}

function onRowClick(event: DataTableRowClickEvent): void {
    const row = event.data as MemberWithdrawalRow;
    show(row.id, row);
    void router.replace({ query: { ...route.query, id: row.id } });
}

watch(
    () => route.query.id,
    (id) => {
        if (typeof id === 'string' && id) {
            if (id !== drawerId.value || !drawerOpen.value) show(id, rows.value.find((r) => r.id === id) ?? null);
        } else if (drawerOpen.value) drawerOpen.value = false;
    },
    { immediate: true },
);
watch(drawerOpen, (open) => {
    if (open) return;
    // 열람한 연락용 이메일은 서랍을 닫으면 바로 가린다
    contact.mask();
    if (!route.query.id) return;
    const { id: _drop, ...rest } = route.query;
    void router.replace({ query: rest });
});

// ── 운영자 대리 취소(회원 요청 시) — 서랍의 [대리 취소] → 대화상자 → 서버. 성공하면 서랍이 바뀐 상세(취소)로, 목록·요약도 다시 받는다 ─
const cancelOpen = ref(false);
const cancelBusy = ref(false);
const cancelError = ref('');
const cancelFieldErrors = ref<Record<string, string>>({});

function openCancel(): void {
    const d = drawerDetail.value;
    if (!d || !UtilMemberWithdrawal.canOperatorCancel(d.status, d.source)) return;
    cancelError.value = '';
    cancelFieldErrors.value = {};
    cancelOpen.value = true;
}

async function submitCancel(body: WithdrawalCancelRequest): Promise<void> {
    const d = drawerDetail.value;
    if (!d || cancelBusy.value) return;
    cancelBusy.value = true;
    cancelError.value = '';
    cancelFieldErrors.value = {};
    try {
        const updated = await api.cancel(d.id, body);
        if (drawerId.value === d.id) drawerDetail.value = updated;
        cancelOpen.value = false;
        toast.success(t('memberWithdrawal.cancel.done', { name: d.nickname ?? d.memberNo }));
        void reload();
        void loadSummary();
    } catch (e) {
        const failure = errorOf(e);
        // 그 사이 끝났거나(배치 완료·회원 취소) 대리 취소할 수 없는 건 — 서랍을 새로 받고 문구는 그대로 보인다
        if (failure.status === 409) {
            cancelOpen.value = false;
            toast.error(failure.message, 8000);
            void loadDetail();
            void reload();
            return;
        }
        const fields: Record<string, string> = {};
        for (const key of ['memberConfirmed', 'requestChannel', 'memo']) {
            const text = failure.field(key);
            if (text) fields[key] = text;
        }
        cancelFieldErrors.value = fields;
        cancelError.value = Object.keys(fields).length ? '' : failure.message;
    } finally {
        cancelBusy.value = false;
    }
}

// ── 연락용 이메일 열람 — 사유 대화상자(회원 관리와 같은 모양) ─
const revealOpen = ref(false);
const revealBusy = ref(false);
const revealError = ref('');
const revealFieldError = ref('');
watch(revealOpen, (open) => {
    if (!open) return;
    revealError.value = '';
    revealFieldError.value = '';
});

async function submitReveal(reason: string): Promise<void> {
    if (revealBusy.value) return;
    revealBusy.value = true;
    revealError.value = '';
    revealFieldError.value = '';
    const failure = await contact.reveal(reason);
    revealBusy.value = false;
    if (!failure) {
        revealOpen.value = false;
        return;
    }
    const field = failure.field('reason');
    revealFieldError.value = field ?? '';
    revealError.value = field ? '' : failure.message;
}

onMounted(() => {
    void loadSummary();
    void reload();
    void loadReasons();
});
</script>
