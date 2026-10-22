<template>
    <!--
        포인트 내역 — **모든 회원의 포인트 원장**을 조건으로 찾아보고, 운영자가 직접 지급·차감한다. 그누보드 「포인트 관리」처럼 전체 원장 + 수동 조정을 한 화면에 둔다.
        community 와 다른 점: community 에는 회원 상세의 회원별 내역(커서 더보기)과 상세 안의 조정 시트만 있었다 → 전체 원장(서버 페이지) · 기간 합계 · 조건 거르기 · 조정 대화상자의 회원 고르기를 더했다. 권한은 최고 관리자 전용 → `point.read`(조회)·`point.adjust`(조정).
        위에서 아래로: 기간 합계 타일(지급 합계 · 차감/회수 합계 · 운영자 조정 건수 — 마지막으로 검색한 조건 전체 기준) · 조회 조건(기간 · 종류 · 사유 · 회원) · 원장 표.
        조건은 「검색」(또는 Enter)을 눌렀을 때만 서버로 가고, 쪽은 마지막으로 검색한 조건으로 다시 묻는다. 기간 시작 값은 최근 1개월이고 「초기화」도 그 기간으로 돌아간다. 날짜는 표시(사이트) 시간대의 하루 경계로 바꿔 보낸다.
        사유 이름은 서버가 요청 언어로 준다(`reason`) — 운영자 조정이면 그 밑에 사유(`note`, 회원에게 공개된 문구)가 붙는다. 처리자는 운영자 조정이면 운영자 닉네임, 아니면 「시스템」.
        회원 칸: 닉네임을 누르면 그 회원의 내역만 다시 본다(회원 조건에 회원번호를 넣고 검색). 회원 상세 서랍은 열지 않는다 — 서랍은 회원 목록 한 줄(`MemberRow`)이 있어야 머리를 그리는데 원장 줄에는 그 칸이 없다. 대신 「회원 관리에서 찾기」 링크(`/members`)를 둔다.
        조건이 틀리면 서버가 칸을 모아 400 `fields.from|to|kind|reasons|page|size` 로 거절한다 — 조건 칸은 그 줄 아래, 쪽 번호·크기는 조건 아래 한 줄에 서버 문구 그대로. 쪽 크기는 서버 상한(1~100) 안의 20·50·100.
        [포인트 조정](`point.adjust` 가 있을 때만) → `PointAdjustDialog` → 확인 대화상자(차감이 잔액보다 크면 실제 차감량도 말한다) → 저장 → 응답의 실제 반영값(`applied`)으로 토스트 → 목록·합계 새로 읽기.
        서버가 남기는 것: 감사 기록 `POINT_ADJUST`(반영된 값 · 사유), 차감이 반영되면 회원에게 알림 `POINTS_REVOKED`. 본문 여백은 셸(`.app-content`)이 준다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('point.ledgerTitle')" :subtitle="t('point.ledgerSubtitle')">
            <template v-if="canAdjust" #actions>
                <Button type="button" :label="t('point.adjust')" icon="pi pi-plus-circle" size="small" @click="openAdjust" />
            </template>
        </PageTitle>

        <!-- 기간 합계 — 마지막으로 검색한 조건 전체(쪽이 아니라) 기준 -->
        <section :aria-label="t('point.summaryLabel')">
            <dl class="m-0 grid gap-3 sm:grid-cols-3">
                <div class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3">
                    <dt class="text-xs text-muted-color">{{ t('point.summaryEarned') }}</dt>
                    <dd class="m-0 mt-1 text-xl font-semibold tabular-nums text-green-700 dark:text-green-400">
                        <Skeleton v-if="!summary" width="6rem" height="1.75rem" />
                        <template v-else>{{ UtilPoint.signed(summary.earned, locale) }}P</template>
                    </dd>
                </div>
                <div class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3">
                    <dt class="text-xs text-muted-color">{{ t('point.summarySpent') }}</dt>
                    <dd class="m-0 mt-1 text-xl font-semibold tabular-nums text-red-600 dark:text-red-400">
                        <Skeleton v-if="!summary" width="6rem" height="1.75rem" />
                        <template v-else>{{ UtilPoint.signed(-summary.spent, locale) }}P</template>
                    </dd>
                </div>
                <div class="rounded-xl border border-(--line) bg-(--p-content-background) px-4 py-3">
                    <dt class="text-xs text-muted-color">{{ t('point.summaryAdjust') }}</dt>
                    <dd class="m-0 mt-1 text-xl font-semibold tabular-nums">
                        <Skeleton v-if="!summary" width="4rem" height="1.75rem" />
                        <template v-else>{{ t('point.summaryCount', { count: summary.adjustCount.toLocaleString(locale) }) }}</template>
                    </dd>
                </div>
            </dl>
        </section>

        <!-- 조회 조건 — 서버가 거절한 조건(400 `fields.<조건>`)은 그 줄 아래에 서버 문구로 -->
        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('point.searchLabel')" @submit.prevent="search">
            <FilterRow :label="t('point.filter.period')" :error="errorOf('from', 'to')">
                <DateRangeField v-model:from="filter.from" v-model:to="filter.to" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('point.filter.from')" :to-label="t('point.filter.to')" />
            </FilterRow>

            <FilterRow :label="t('point.filter.kind')" :error="errorOf('kind')">
                <template #default="{ labelId }">
                    <FilterOptionGroup v-model="filter.kind" :options="kindOptions" :labelledby="labelId" />
                </template>
            </FilterRow>

            <FilterRow :label="t('point.filter.reasons')" :label-for="ids.reasons" :error="errorOf('reasons')">
                <AppMultiSelect
                    v-model="filter.reasons"
                    :options="reasonOptions"
                    :input-id="ids.reasons"
                    :placeholder="t('point.filter.reasonsPlaceholder')"
                    :selected-items-label="t('point.filter.reasonsSelected', { count: '{0}' })"
                    :max-selected-labels="2"
                    class="max-w-xl"
                />
            </FilterRow>

            <FilterRow :label="t('point.filter.member')" :label-for="ids.q" :error="errorOf('q')">
                <InputText :id="ids.q" v-model="filter.q" type="search" maxlength="100" autocomplete="off" :placeholder="t('point.filter.memberPlaceholder')" class="w-full max-w-sm" />
            </FilterRow>

            <!-- 어느 조건에도 붙지 않는 서버 오류(쪽 번호·크기 등) -->
            <p v-if="otherErrors" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ otherErrors }}</p>

            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy" />
                <Button type="button" :label="t('point.reset')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
            </div>
        </form>

        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="rows"
                data-key="entryId"
                lazy
                paginator
                :first="first"
                :rows="size"
                :total-records="total"
                :rows-per-page-options="[20, 50, 100]"
                :loading="busy"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                scrollable
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown CurrentPageReport"
                class="min-w-0"
                @page="onPage"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('point.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <!-- 서버가 조건을 받아들이지 않았다 — 해당 줄 아래에 서버 문구가 붙어 있다 -->
                    <EmptyState v-else-if="hasSearchErrors" icon="pi pi-exclamation-circle" :title="t('point.filterRejected')" :description="t('point.filterRejectedHint')" />
                    <!-- 빈 상태를 구분한다 — 조건 때문인지 아직 기록이 없는 건지 -->
                    <EmptyState v-else-if="hasApplied" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Button type="button" :label="t('point.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearAll" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-wallet" :title="t('point.empty')" :description="t('point.emptyHint')" />
                </template>

                <Column field="createdAt" :header="t('point.col.createdAt')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums">{{ time.full(data.createdAt) }}</span>
                    </template>
                </Column>

                <Column field="memberNickname" :header="t('point.col.member')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 items-center gap-1">
                            <div class="min-w-0">
                                <button
                                    type="button"
                                    class="block max-w-full cursor-pointer truncate text-left text-[13px] hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                    :title="t('point.onlyThisMember', { name: data.memberNickname })"
                                    :aria-label="t('point.onlyThisMember', { name: data.memberNickname })"
                                    @click="onlyMember(data)"
                                >
                                    {{ data.memberNickname }}
                                </button>
                                <code class="block truncate text-[11px] tabular-nums text-muted-color">{{ data.memberId }}</code>
                            </div>
                            <NuxtLink v-tooltip.top="t('point.openMembers')" to="/members" class="ml-auto inline-grid size-7 shrink-0 place-items-center rounded text-muted-color hover:bg-(--p-content-hover-background) focus-visible:outline-2 focus-visible:outline-primary" :aria-label="t('point.openMembers')">
                                <i class="pi pi-external-link text-[11px]" aria-hidden="true" />
                            </NuxtLink>
                        </div>
                    </template>
                </Column>

                <Column field="kind" :header="t('point.col.kind')" style="min-width: 5.5rem">
                    <template #body="{ data }">
                        <Tag :value="t(`point.kind.${data.kind}`)" :severity="UtilPoint.kindSeverity(data.kind)" />
                    </template>
                </Column>

                <Column field="reason" :header="t('point.col.reason')" style="min-width: 14rem">
                    <template #body="{ data }">
                        <div class="min-w-0">
                            <div class="truncate text-[13px]">{{ data.reason }}</div>
                            <div v-if="data.note" class="truncate text-[12px] text-muted-color" :title="data.note">{{ data.note }}</div>
                        </div>
                    </template>
                </Column>

                <Column field="delta" :header="t('point.col.delta')" style="min-width: 6.5rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] font-medium tabular-nums" :class="data.delta > 0 ? 'text-green-700 dark:text-green-400' : 'text-red-600 dark:text-red-400'">{{ UtilPoint.signed(data.delta, locale) }}</span>
                    </template>
                </Column>

                <Column field="balanceAfter" :header="t('point.col.balanceAfter')" style="min-width: 7rem" :pt="{ columnHeaderContent: { class: 'justify-end' } }">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums">{{ data.balanceAfter.toLocaleString(locale) }}</span>
                    </template>
                </Column>

                <Column field="actorNickname" :header="t('point.col.actor')" style="min-width: 7rem">
                    <template #body="{ data }">
                        <span v-if="data.actorNickname" class="text-[13px]">{{ data.actorNickname }}</span>
                        <span v-else class="text-[13px] text-muted-color">{{ t('point.actorSystem') }}</span>
                    </template>
                </Column>
            </DataTable>
        </div>

        <PointAdjustDialog v-if="canAdjust" v-model:visible="adjustOpen" :candidates="candidates" :searching="candidatesLoading" :busy="adjustBusy" :error="adjustError" :field-errors="adjustFieldErrors" @search="searchCandidates" @submit="onAdjust" />
    </div>
</template>

<script setup lang="ts">
import type { PointAdjustSubmit } from '~/components/point/PointAdjustDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { PointLedgerItem, PointLedgerKind, PointLedgerQuery, PointLedgerSummary, PointMemberCandidate, PointReasonCode } from '~/composables/point/api/usePointApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilMember from '~/utils/util-member';
import UtilPoint from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';

/** 화면 조건(draft) — 「검색」을 눌러야 서버로 간다. 날짜는 달력 글자(`YYYY-MM-DD`), 종류 '' = 전체, 사유 빈 배열 = 전체 */
interface LedgerFilter {
    from: string;
    to: string;
    kind: string;
    reasons: string[];
    q: string;
}
type AppliedQuery = Omit<PointLedgerQuery, 'page' | 'size'>;

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('point.ledgerTitle') });

const api = usePointApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 수동 지급·차감 권한 — 없으면 단추가 없다(숨기는 것은 편의이고 막는 것은 서버다) */
const canAdjust = computed(() => can('point.adjust'));

const ids = { reasons: useId(), q: useId() };
/** 쪽 안내(「전체 N건」) — `{totalRecords}` 는 PrimeVue 가 채우는 자리 */
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const kindOptions = computed(() => [{ value: '', label: t('common.all') }, ...UtilPoint.KINDS.map((kind) => ({ value: kind, label: t(`point.kind.${kind}`) }))]);
const reasonOptions = computed(() => UtilPoint.REASON_CODES.map((code) => ({ value: code, label: t(`point.reasonCode.${code}`) })));

/** 기본 기간 — 표시 시간대의 오늘 기준 최근 1개월 */
function defaultFilter(): LedgerFilter {
    const p = time.value.parts(new Date().toISOString());
    const today = p ? `${p.y}-${String(p.mo).padStart(2, '0')}-${String(p.d).padStart(2, '0')}` : '';
    const range = UtilDateRange.fill('MONTH1', today);
    return { from: range?.from ?? '', to: range?.to ?? '', kind: '', reasons: [], q: '' };
}

const filter = reactive<LedgerFilter>(defaultFilter());
const rows = ref<PointLedgerItem[]>([]);
const total = ref(0);
/** 마지막으로 검색한 조건의 합계 — 아직 못 받았으면 null(뼈대) */
const summary = ref<PointLedgerSummary | null>(null);
const first = ref(0);
const size = ref(20);
const busy = ref(false);
const loadError = ref<ApiError | null>(null);
/** 서버가 거절한 조건 문구(`400` 의 `fields` — 조건 이름 → 서버 문구) */
const searchErrors = ref<Record<string, string>>({});
const hasSearchErrors = computed(() => Object.keys(searchErrors.value).length > 0);
const FILTER_KEYS = ['from', 'to', 'kind', 'reasons', 'q'];
const otherErrors = computed(() =>
    Object.entries(searchErrors.value)
        .filter(([key]) => !FILTER_KEYS.includes(key))
        .map(([, message]) => message)
        .join(' '),
);

/** 마지막으로 「검색」한 조건 — 쪽은 이것으로 다시 묻는다 */
const applied = ref<AppliedQuery>({});
/** 기본 기간 말고 다른 조건이 걸려 있나 — 빈 상태 문구를 가른다 */
const hasApplied = computed(() => Boolean(applied.value.kind || applied.value.reasons?.length || applied.value.q || applied.value.from || applied.value.to));

function errorOf(...keys: string[]): string {
    return keys
        .map((key) => searchErrors.value[key])
        .filter(Boolean)
        .join(' ');
}

function toQuery(f: LedgerFilter): AppliedQuery {
    const zoneId = values.value.zoneId;
    const out: AppliedQuery = {};
    const from = f.from ? UtilMember.dayStart(f.from, zoneId) : undefined;
    const to = f.to ? UtilMember.dayEnd(f.to, zoneId) : undefined;
    if (from) out.from = from;
    if (to) out.to = to;
    if (f.kind) out.kind = f.kind as PointLedgerKind;
    if (f.reasons.length) out.reasons = f.reasons as PointReasonCode[];
    if (f.q.trim()) out.q = f.q.trim();
    return out;
}

async function reload() {
    busy.value = true;
    loadError.value = null;
    searchErrors.value = {};
    try {
        const found = await api.ledger({ ...applied.value, page: Math.floor(first.value / size.value), size: size.value });
        rows.value = found.items;
        total.value = found.total;
        summary.value = found.summary;
    } catch (e) {
        const error = toApiError(e);
        rows.value = [];
        total.value = 0;
        summary.value = { earned: 0, spent: 0, adjustCount: 0 };
        // 조건이 틀려 서버가 조건 이름과 함께 거절했으면 그 줄 아래에 서버 문구를 붙인다
        if (error.status === 400 && error.fields && Object.keys(error.fields).length > 0) searchErrors.value = error.fields;
        else loadError.value = error;
    } finally {
        busy.value = false;
    }
}

/** 「검색」·Enter — 지금 조건을 굳히고 첫 쪽부터 다시 본다 */
function search() {
    applied.value = toQuery(filter);
    first.value = 0;
    void reload();
}

/** 「초기화」 — 기본 기간(최근 1개월)으로 돌아가 바로 다시 본다 */
function resetAll() {
    Object.assign(filter, defaultFilter());
    search();
}

/** 빈 결과의 「조건 지우기」 — 기간까지 모두 비우고 다시 본다 */
function clearAll() {
    Object.assign(filter, { from: '', to: '', kind: '', reasons: [], q: '' });
    search();
}

/** 닉네임을 누르면 그 회원의 내역만 — 회원 조건에 회원번호를 넣고 다시 본다(다른 조건은 그대로) */
function onlyMember(row: PointLedgerItem) {
    filter.q = row.memberId;
    search();
}

function onPage(e: { first: number; rows: number }) {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

onMounted(search);

// ── 포인트 조정 ─
const adjustOpen = ref(false);
const candidates = ref<PointMemberCandidate[]>([]);
const candidatesLoading = ref(false);
const adjustBusy = ref(false);
const adjustError = ref('');
const adjustFieldErrors = ref<Record<string, string>>({});
/** 늦게 도착한 앞 찾기의 응답은 버린다(번호표) */
let candidateTicket = 0;

function openAdjust() {
    adjustError.value = '';
    adjustFieldErrors.value = {};
    adjustOpen.value = true;
}

async function searchCandidates(q: string) {
    const mine = ++candidateTicket;
    candidatesLoading.value = true;
    try {
        const found = await api.memberCandidates(q);
        if (mine === candidateTicket) candidates.value = found;
    } catch (e) {
        if (mine === candidateTicket) {
            candidates.value = [];
            adjustError.value = toApiError(e).message;
        }
    } finally {
        if (mine === candidateTicket) candidatesLoading.value = false;
    }
}

async function onAdjust(payload: PointAdjustSubmit) {
    if (adjustBusy.value) return;
    const expected = UtilPoint.preview(payload.member.balance, payload.delta);
    const ok = await confirm({
        title: t('point.confirmTitle'),
        message: UtilPointMessages.adjustConfirm(t, payload.member.nickname, payload.mode, payload.amount, expected.applied, locale.value),
        confirmLabel: t('point.adjustSubmit'),
        cancelLabel: t('common.cancel'),
        tone: payload.mode === 'DEDUCT' ? 'danger' : 'primary',
    });
    if (!ok) return;
    adjustBusy.value = true;
    adjustError.value = '';
    adjustFieldErrors.value = {};
    try {
        // 감사 기록 `POINT_ADJUST` 와 (차감이 반영되면) 회원 알림 `POINTS_REVOKED` 는 서버가 남긴다
        const res = await api.adjust(payload.member.memberId, { delta: payload.delta, reason: payload.reason });
        toast.success(UtilPointMessages.adjusted(t, payload.member.nickname, res.applied, locale.value));
        adjustOpen.value = false;
        void reload();
    } catch (e) {
        const error = toApiError(e);
        const fields = error.status === 400 && error.fields ? error.fields : {};
        adjustFieldErrors.value = fields;
        const rest = Object.entries(fields)
            .filter(([key]) => key !== 'delta' && key !== 'reason')
            .map(([, message]) => message);
        adjustError.value = Object.keys(fields).length ? rest.join(' ') : `${t('point.adjustFailed')} — ${error.message}`;
    } finally {
        adjustBusy.value = false;
    }
}
</script>
