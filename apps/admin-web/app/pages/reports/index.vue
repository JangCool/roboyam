<template>
    <!--
        신고 관리 — 글·댓글·소모임 신고를 대상별 사건으로 모아 보고, 검토해서 조치(글·댓글: 숨기기·자동 숨김 확정·삭제 / 소모임: 비활성화)하거나 무시한다.
        소모임 대상(community `docs/api-report.md` 의 `GROUP`): 대상 칸은 소모임 칩 + 이름·소개 앞부분, 게시판 칸은 소모임 게시판(없으면 —), 작성자 칸은 모임장. 줄 메뉴의 「소모임 관리에서 보기」는 `/groups?group=`(`group.read`).
          관리자 소모임 상세 서랍의 비활성 사건 번호가 `/reports?report={caseId}&status=ALL` 로, 「신고 보기 ›」가 `?groupId={소모임 ID}&status=ALL` 로 넘어온다(툴바에 풀기 칩).
          사유 거르기는 글·댓글 6종 + 소모임 4종, 처리 대화창의 사유 코드는 사건 대상의 체계만.
        계약 `docs/features/report-admin-contract.md`(community `admin/app/pages/reports` 의 동작을 로보얌 신고 모델·자동 숨김·게시판 신고 정책에 맞춤). 모양·구조는 게시글 관리(`pages/posts/index.vue`)와 같다.
        - 조회 폼: 검색 대상(제목·작성자·사건 번호) + 검색 칸 · 대상(전체/글/댓글 라디오) · 사유(Select) · 게시판(Select) · 마지막 신고일 기간(빠른 선택) · 상태 라디오(처리 대기·검토 중·처리됨·무시·전체 — 건수 없음) · 가운데 검색·초기화.
          검색 대상·검색어·기간은 「검색」(또는 Enter)을 눌러야 주소로 가고, 대상·사유·게시판·상태는 고르는 즉시 주소로 간다.
          목록: 정렬은 표의 「마지막 신고」(내림 = 최신 · 오름 = 오래된)·「신고 수」(많은 순) 머리 · 표 위 툴바에 건수·정렬 안내 · 쪽 크기(10/20/50).
          **모두 주소 쿼리**(`?field=&q=&type=&reason=&board=&from=&to=&status=&postId=&groupId=&sort=&page=&size=&report=`). 게시글 관리 「신고」 칸이 `?postId=` 로 넘어온다(툴바에 풀기 칩).
        - 표: 체크(열린 사건, `report.manage`) · 사건 번호 · 대상(글/댓글 칩 + 제목·발췌, 대상 상태 알약 — 숨김 까닭·임시조치 기한) · 게시판 · 작성자 · 신고 수 · 사유 분포 칩 · 마지막 신고 · 상태 알약(검토자) · ⋯ 메뉴.
          줄을 누르면 사건 서랍(`ReportDetailDrawer`, 주소 `?report=` — replace, 목록을 다시 받지 않는다). 이전/다음은 지금 쪽의 줄 순서. 운영자 알림 링크 `/reports?report={caseId}` 도 같은 서랍을 연다.
          대기 사건은 열람과 함께 서버가 검토 중으로 바꾼다 — 받은 상세의 사건으로 목록 줄을 바꿔 끼운다(목록을 다시 받지 않는다).
        - 처리: 처리 대화창(`ReportActionDialog` — 사유 코드 + 글, 무시는 「허위 신고로 표시」) · 다시 열기(무시한 사건, 확인 대화상자).
          `409 REPORT_CASE_CLOSED`·`REPORT_ACTION_INVALID`·`REPORT_CASE_OPEN_EXISTS`·`404 REPORT_CASE_NOT_FOUND`(그 사이 바뀜)는 토스트(서버 문구) + 새로 받기.
        - 일괄: 열린 사건을 고르면 아래에 일괄 줄 — [무시](허위 표시 체크) → 결과 대화창(사건마다 처리됨/못 함 + 까닭).
        - 권한: 보기 `report.read`(메뉴). `report.manage` 가 없으면 읽기 전용 안내 + 체크·처리 없음. 게시판 고르개는 게시글 관리의 게시판 선택지(`post.read`)를 빌려 쓴다 — 없으면 「전체」만.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('report.title')" :subtitle="t('report.subtitle')">
            <template #actions>
                <Button type="button" icon="pi pi-refresh" :label="t('report.refresh')" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="busy && !refreshing" @click="load(true)" />
            </template>
        </PageTitle>

        <form class="flex flex-col gap-3 rounded-xl border border-(--line) bg-(--p-content-background) p-4" role="search" :aria-label="t('report.searchLabel')" novalidate @submit.prevent="submitSearch">
            <!-- ① 맨 윗줄 — 검색 대상 + 검색 칸 -->
            <div class="flex flex-wrap items-center gap-2">
                <Select v-model="searchField" :options="searchFieldOptions" option-label="label" option-value="value" :aria-label="t('report.searchFieldLabel')" class="w-32 shrink-0" />
                <InputText
                    v-model="searchText"
                    type="search"
                    :maxlength="UtilReport.SEARCH_MAX"
                    :placeholder="t(`report.searchPlaceholder.${searchField}`)"
                    :aria-label="t('report.searchLabel')"
                    :invalid="Boolean(searchMessage)"
                    autocomplete="off"
                    class="min-w-0 flex-1 basis-56"
                />
                <Button v-if="filters.q" type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" :aria-label="t('report.searchClear')" @click="clearSearch" />
            </div>
            <p v-if="searchMessage" role="alert" class="-mt-1 text-xs text-red-600 dark:text-red-400">{{ searchMessage }}</p>

            <!-- ② 조건 줄 -->
            <FilterRow :label="t('report.filterType')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.type || ALL" :options="typeOptions" :labelledby="labelId" @update:model-value="(v) => setType(v as string)" />
                </template>
            </FilterRow>
            <FilterRow :label="t('report.filterReason')" :label-for="`${uid}-reason`">
                <Select :model-value="filters.reason || ALL" :input-id="`${uid}-reason`" :options="reasonFilterOptions" option-label="label" option-value="value" class="w-56" @update:model-value="setReason" />
            </FilterRow>
            <FilterRow :label="t('report.filterBoard')" :label-for="`${uid}-board`">
                <Select :model-value="filters.board || ALL" :input-id="`${uid}-board`" :options="boardFilterOptions" option-label="label" option-value="value" filter :loading="optionsLoading" class="w-56" @update:model-value="setBoard" />
                <p v-if="optionsError" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" role="alert">
                    {{ optionsError }}
                    <Button type="button" :label="t('report.optionsRetry')" size="small" severity="secondary" variant="text" @click="loadOptions" />
                </p>
            </FilterRow>

            <FilterRow :label="t('report.filterLastReported')" :error="fieldErrors.from || fieldErrors.to || ''">
                <DateRangeField v-model:from="dateFrom" v-model:to="dateTo" :presets="presets" :presets-label="t('member.presetsLabel')" :from-label="t('report.filterFrom')" :to-label="t('report.filterTo')" />
            </FilterRow>

            <FilterRow :label="t('report.statusLabel')">
                <template #default="{ labelId }">
                    <FilterOptionGroup :model-value="filters.status" :options="statusOptions" :labelledby="labelId" @update:model-value="(v) => setStatus(v as ReportStatusFilter)" />
                </template>
            </FilterRow>

            <!-- ③ 맨 아래 줄 — 가운데 「검색」·「초기화」 -->
            <div class="flex flex-wrap items-center justify-center gap-2 border-t border-(--line) pt-3">
                <Button type="submit" :label="t('common.search')" size="small" :loading="busy && !refreshing" />
                <Button type="button" :label="t('report.reset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
            </div>
        </form>

        <div class="flex min-w-0 flex-col gap-2">
            <!-- 툴바 — 「전체 N건」·정렬 안내·글 거르기 칩·읽기 전용 안내 -->
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                <p v-if="view" class="text-[13px] font-medium tabular-nums" aria-live="polite">{{ t('report.total', { count: view.total.toLocaleString(locale) }) }}</p>
                <p class="text-xs text-muted-color">{{ sortText }}</p>
                <span v-if="filters.postId" class="inline-flex items-center gap-1 rounded-full border border-(--line) py-0.5 pr-0.5 pl-2.5 text-xs">
                    <i class="pi pi-filter text-[10px]" aria-hidden="true" />{{ t('report.postFilter', { id: filters.postId }) }}
                    <Button type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" rounded class="h-6! w-6!" :aria-label="t('report.postFilterClear')" @click="clearPostFilter" />
                </span>
                <span v-if="filters.groupId" class="inline-flex items-center gap-1 rounded-full border border-(--line) py-0.5 pr-0.5 pl-2.5 text-xs">
                    <i class="pi pi-filter text-[10px]" aria-hidden="true" />{{ t('report.groupFilter', { id: filters.groupId }) }}
                    <Button type="button" icon="pi pi-times" size="small" severity="secondary" variant="text" rounded class="h-6! w-6!" :aria-label="t('report.groupFilterClear')" @click="clearGroupFilter" />
                </span>
                <p v-if="!canManage" class="flex items-center gap-1.5 text-xs text-muted-color">
                    <i class="pi pi-lock" aria-hidden="true" />{{ t('report.readOnly') }}
                </p>
            </div>

            <div class="report-table min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="items"
                    data-key="id"
                    lazy
                    paginator
                    :first="(filters.page - 1) * filters.size"
                    :rows="filters.size"
                    :total-records="view?.total ?? 0"
                    :rows-per-page-options="[...UtilReport.PAGE_SIZES]"
                    :loading="busy && !refreshing"
                    :size="prefs.density === 'compact' ? 'small' : undefined"
                    row-hover
                    scrollable
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                    :sort-field="UtilReport.sortField(filters.sort)"
                    :sort-order="UtilReport.sortOrder(filters.sort)"
                    :row-class="rowClass"
                    class="min-w-0"
                    @page="onPage"
                    @sort="onSort"
                    @row-click="onRowClick"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('report.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load()" />
                        <div v-else-if="busy || !view" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="hasConditions" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('report.resetFilters')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-flag" :title="t('report.empty')" :description="t('report.emptyHint')" />
                    </template>

                    <Column v-if="canManage" style="width: 2.75rem">
                        <template #header>
                            <Checkbox binary :model-value="allSelected" :indeterminate="selection.length > 0 && !allSelected" :disabled="selectableIds.length === 0 || acting" :aria-label="t('report.selectAll')" @update:model-value="toggleAll" />
                        </template>
                        <template #body="{ data }">
                            <span @click.stop>
                                <Checkbox
                                    v-if="UtilReport.isSelectable(data)"
                                    binary
                                    :model-value="selection.includes(data.id)"
                                    :disabled="acting"
                                    :aria-label="t('report.selectRow', { no: data.caseNo })"
                                    @update:model-value="toggle(data.id)"
                                />
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('report.col.caseNo')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums text-muted-color">{{ UtilReportMessages.caseNo(t, data) }}</span>
                        </template>
                    </Column>

                    <Column :header="t('report.col.target')" style="min-width: 18rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 flex-col gap-1">
                                <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                                    <Tag :value="t(`report.targetType.${data.targetType}`)" :severity="UtilReport.targetTypeSeverity(data.targetType)" class="shrink-0 text-[11px]!" />
                                    <button
                                        type="button"
                                        class="min-w-0 text-left text-[13px] font-medium break-words [overflow-wrap:anywhere] hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                                        :class="data.target.title === null ? 'font-normal text-muted-color' : data.target.state === 'DELETED' ? 'text-muted-color line-through' : ''"
                                        @click.stop="openCase(data)"
                                    >
                                        {{ data.target.title ?? t('report.targetPurged') }}
                                    </button>
                                </div>
                                <p v-if="data.targetType !== 'POST' && data.target.excerpt" class="m-0 line-clamp-2 text-xs text-muted-color">{{ data.target.excerpt }}</p>
                                <ReportTargetStateTag :target="data.target" lines />
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('report.col.board')" style="min-width: 7rem">
                        <template #body="{ data }">
                            <span v-if="data.board" class="block truncate text-[13px]">{{ localizedName(data.board.name, data.board.nameEn ?? null) }}</span>
                            <span v-else class="text-[13px] text-muted-color">—</span>
                        </template>
                    </Column>

                    <Column :header="t('report.col.author')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]" :class="data.author.withdrawn ? 'text-muted-color' : ''">{{ UtilReportMessages.author(t, data.author) }}</span>
                        </template>
                    </Column>

                    <Column field="reportCount" sortable :header="t('report.col.reportCount')" style="min-width: 6rem">
                        <template #body="{ data }">
                            <span class="text-[13px] font-medium tabular-nums">{{ data.reportCount }}</span>
                        </template>
                    </Column>

                    <Column :header="t('report.col.reasons')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <div class="flex flex-wrap gap-1">
                                <Tag
                                    v-for="r in UtilReport.reasonChips(data)"
                                    :key="r.code"
                                    :value="t('report.reasonChip', { name: UtilReportMessages.reasonName(t, r.code), n: r.count })"
                                    :severity="r.code === 'ILLEGAL_FILMING' ? 'danger' : 'secondary'"
                                    class="text-[11px]!"
                                />
                            </div>
                        </template>
                    </Column>

                    <Column field="lastReportedAt" sortable :header="t('report.col.lastReportedAt')" style="min-width: 8rem">
                        <template #body="{ data }">
                            <time :datetime="data.lastReportedAt" :title="time.full(data.lastReportedAt)" class="text-[12px] tabular-nums">{{ time.listLabel(data.lastReportedAt) }}</time>
                        </template>
                    </Column>

                    <Column :header="t('report.col.status')" style="min-width: 7.5rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 flex-col items-start gap-0.5">
                                <Tag :value="t(`report.status.${data.status}`)" :severity="UtilReport.statusSeverity(data.status)" class="text-[11px]!" />
                                <span v-if="data.reviewer" class="text-[11px] leading-snug text-muted-color">{{ data.reviewer.name }}</span>
                            </div>
                        </template>
                    </Column>

                    <Column style="width: 3rem">
                        <template #header><span class="sr-only">{{ t('report.col.actions') }}</span></template>
                        <template #body="{ data }">
                            <Button
                                v-if="rowMenuFor(data).length > 0"
                                type="button"
                                icon="pi pi-ellipsis-h"
                                size="small"
                                severity="secondary"
                                variant="text"
                                rounded
                                :disabled="acting"
                                :aria-label="t('report.actionsMenu', { no: data.caseNo })"
                                aria-haspopup="menu"
                                @click.stop="openRowMenu($event, data)"
                            />
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <!-- 일괄 줄 — 목록 아래, 화면 바닥에 붙는다 -->
        <div
            v-if="canManage && selection.length > 0"
            class="sticky bottom-3 z-20 flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-primary-200 bg-primary-50 px-3 py-2 shadow-lg dark:border-primary-800 dark:bg-primary-950"
            role="region"
            :aria-label="t('report.bulkRegion')"
            data-report-keep-drawer
        >
            <b class="text-sm">{{ t('report.selected', { n: selection.length }) }}</b>
            <span class="min-w-0 flex-1 basis-60 text-xs leading-relaxed text-muted-color max-sm:hidden">{{ t('report.bulkLead') }}</span>
            <span class="ml-auto flex flex-wrap gap-2">
                <Button type="button" :label="t('report.bulkDismiss', { n: selectedOpen.length })" size="small" :disabled="acting || selectedOpen.length === 0" @click="openBulk" />
                <Button type="button" :label="t('report.bulkClear')" size="small" severity="secondary" variant="outlined" :disabled="acting" @click="selection = []" />
            </span>
        </div>

        <Menu ref="rowMenu" :model="rowMenuItems" popup />

        <ReportActionDialog v-model:visible="actOpen" :kind="actKind" :item="actTarget" :busy="acting" :error="actError" :field-errors="actFieldErrors" @submit="submitAct" />
        <ReportBulkDialog v-model:visible="bulkOpen" :count="bulkTargetIds.length" :busy="acting" :error="bulkError" :field-error="bulkFieldError" :result="bulkResult" :titles="bulkTitles" @submit="submitBulk" />

        <ReportDetailDrawer
            v-model:visible="drawerOpen"
            :item="drawerItem"
            :detail="detail.detail.value"
            :loading="detail.loading.value"
            :error="detail.error.value"
            :actions="drawerActions"
            :busy="acting"
            :has-prev="neighbors.prev !== null"
            :has-next="neighbors.next !== null"
            :can-manage="canManage"
            :can-view-post="canViewPost"
            :can-view-group="canViewGroup"
            :author-grade="authorGrade"
            @retry="detail.reload(false)"
            @act="onDrawerAct"
            @reopen="reopen"
            @prev="neighbors.prev && openCase(neighbors.prev)"
            @next="neighbors.next && openCase(neighbors.next)"
            @closed="onDrawerClosed"
        />
    </div>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import type { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostBoardOption } from '~/composables/post/api/usePostApi.types';
import type { ReportBulkResult, ReportCaseDetail, ReportCaseItem, ReportCaseListView, ReportReasonCode, ReportStatusFilter, ReportTargetType } from '~/composables/report/api/useReportApi.types';
import UtilDateRange from '~/utils/util-date-range';
import UtilReport, { type ReportActKind, type ReportFilters } from '~/utils/util-report';
import UtilReportMessages from '~/utils/util-report-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('report.title') });

const api = useReportApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const displayValues = useDisplayValues();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const { localizedName } = useLocalizedName();
const gradeAppearance = useGradeAppearance();
const uid = useId();

/** 권한(정의: util-permission-catalog.ts) — 감추는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('report.manage'));
const canViewPost = computed(() => can('post.read'));
const canViewGroup = computed(() => can('group.read'));

/* ── 주소 ↔ 필터 ── */

const LIST_PATH = '/reports';
/** 「전체」 선택지의 값 — 빈 글자는 Select·라디오가 "고르지 않음"으로 읽는다 */
const ALL = '__all__';

const filters = computed<ReportFilters>(() => UtilReport.parse(route.query));

/**
 * 필터를 주소에 싣는다(서랍이 열려 있으면 그 사건 `report` 는 그대로). 쪽 이동은 push, 조건 변경은 replace.
 * `report` 는 주소가 아니라 서랍 상태로 정한다 — 서랍 밖을 누르면 서랍이 먼저 닫히는데(pointerdown) 주소는 아직 옛 값이라, 주소를 읽으면 닫힌 사건이 다시 열린다.
 */
function apply(next: ReportFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilReport.key(next) === UtilReport.key(filters.value)) return;
    const report = drawerOpen.value ? detail.openId.value : null;
    void router[how]({ path: LIST_PATH, query: { ...UtilReport.toQuery(next), ...(report ? { report } : {}) } });
}

function setStatus(status: ReportStatusFilter): void {
    apply(UtilReport.change(filters.value, { status }));
}
function setType(value: string): void {
    apply(UtilReport.change(filters.value, { type: value === ALL ? '' : (value as ReportTargetType) }));
}
function setReason(value: string): void {
    apply(UtilReport.change(filters.value, { reason: value === ALL ? '' : (value as ReportReasonCode) }));
}
function setBoard(value: string): void {
    apply(UtilReport.change(filters.value, { board: value === ALL ? '' : value }));
}
function clearPostFilter(): void {
    apply(UtilReport.change(filters.value, { postId: '' }));
}
function clearGroupFilter(): void {
    apply(UtilReport.change(filters.value, { groupId: '' }));
}
function onPage(e: { page: number; rows: number }): void {
    if (e.rows !== filters.value.size) apply(UtilReport.change(filters.value, { size: e.rows }));
    else apply(UtilReport.change(filters.value, { page: e.page + 1 }), 'push');
}
/** 「마지막 신고」 머리 — 오름 = 오래된, 내림 = 최신 · 「신고 수」 머리 — 많은 순. 1쪽으로 */
function onSort(e: { sortField?: unknown; sortOrder?: number | null }): void {
    apply(UtilReport.change(filters.value, { sort: UtilReport.sortOf(typeof e.sortField === 'string' ? e.sortField : null, e.sortOrder) }));
}
/** 「초기화」 — 검색·기간(입력 중인 것 포함)과 대상·사유·게시판·상태·글 거르기를 비운다(정렬·쪽 크기는 그대로) */
function resetFilters(): void {
    const empty = UtilReport.emptyFilters();
    searchField.value = empty.field;
    searchText.value = '';
    dateFrom.value = '';
    dateTo.value = '';
    searchError.value = '';
    apply(UtilReport.clear(filters.value));
}

const hasConditions = computed(() => UtilReport.hasConditions(filters.value));

/* ── 검색 대상·검색어·기간(「검색」을 눌러야 주소로 간다) ── */

const searchField = ref(filters.value.field);
const searchText = ref(filters.value.q);
const dateFrom = ref(filters.value.from);
const dateTo = ref(filters.value.to);
const searchError = ref('');
/** 서버가 거절한 조건 문구(`400` 의 `fields` — `q`·`searchField`·`from`·`to`). 목록을 다시 받을 때 비운다 */
const fieldErrors = ref<Record<string, string>>({});
const FIELD_KEYS = ['q', 'searchField', 'from', 'to'];
const searchMessage = computed(() => searchError.value || fieldErrors.value.q || fieldErrors.value.searchField || '');

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const searchFieldOptions = computed(() => UtilReport.SEARCH_FIELDS.map((value) => ({ value, label: t(`report.searchField.${value}`) })));
const presets = computed(() => UtilDateRange.PRESETS.map((kind) => ({ kind, label: t(`member.preset.${kind}`) })));
const typeOptions = computed(() => [{ value: ALL, label: t('report.filterAll') }, ...UtilReport.TARGET_TYPES.map((value) => ({ value, label: t(`report.targetType.${value}`) }))]);
const reasonFilterOptions = computed(() => [{ value: ALL, label: t('report.filterAll') }, ...UtilReport.REASONS.map((value) => ({ value, label: UtilReportMessages.reasonName(t, value) }))]);
const statusOptions = computed(() => UtilReport.STATUS_FILTERS.map((value) => ({ value, label: t(`report.statusFilter.${value}`) })));
/** 툴바의 정렬 안내 — 게시글 관리와 같은 꼴(「정렬: 마지막 신고 내림차순」) */
const sortText = computed(() =>
    filters.value.sort === 'most'
        ? t('report.sortBy', { field: t('report.col.reportCount'), dir: t('report.sortDesc') })
        : t('report.sortBy', { field: t('report.col.lastReportedAt'), dir: t(filters.value.sort === 'oldest' ? 'report.sortAsc' : 'report.sortDesc') }),
);

watch(
    () => [filters.value.field, filters.value.q, filters.value.from, filters.value.to] as const,
    ([field, q, from, to]) => {
        searchField.value = field;
        searchText.value = q;
        dateFrom.value = from;
        dateTo.value = to;
        searchError.value = '';
    },
);
watch(searchField, () => {
    searchError.value = '';
});

/** 보낼 때 정한다 — 제목·작성자는 2자 이상, 사건 번호는 숫자만. 비우고 보내면 검색을 푼다 */
function submitSearch(): void {
    const parsed = UtilReport.parseSearch(searchField.value, searchText.value);
    if (parsed.kind === 'TOO_SHORT' || parsed.kind === 'NUMBER_INVALID') {
        searchError.value = t(parsed.kind === 'TOO_SHORT' ? 'report.searchTooShort' : 'report.searchNumberInvalid');
        return;
    }
    searchError.value = '';
    const next = UtilReport.change(filters.value, { field: searchField.value, q: parsed.text, from: dateFrom.value, to: dateTo.value });
    if (UtilReport.key(next) === UtilReport.key(filters.value)) void load();
    else apply(next);
}
function clearSearch(): void {
    searchText.value = '';
    searchError.value = '';
    apply(UtilReport.change(filters.value, { q: '' }));
}

/* ── 게시판 선택지(신고 관리의 `board-options` — `report.read`, 모양은 게시글 관리 선택지와 같다) ── */

const boards = ref<AdminPostBoardOption[]>([]);
const optionsLoading = ref(false);
const optionsError = ref('');

async function loadOptions(): Promise<void> {
    optionsLoading.value = true;
    optionsError.value = '';
    try {
        boards.value = (await api.boardOptions()).boards;
    } catch (e) {
        const error = errorOf(e);
        if (error.status !== 403) optionsError.value = `${t('report.optionsFailed')} ${error.message}`;
    } finally {
        optionsLoading.value = false;
    }
}

const boardFilterOptions = computed(() => {
    const list = [{ value: ALL, label: t('report.filterAll') }, ...boards.value.map((b) => ({ value: b.id, label: localizedName(b.name, b.nameEn) }))];
    const id = filters.value.board;
    if (id && !list.some((o) => o.value === id)) list.push({ value: id, label: `#${id}` });
    return list;
});

/* ── 목록 ── */

const view = ref<ReportCaseListView | null>(null);
const items = computed(() => view.value?.items ?? []);
const busy = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);

let seq = 0;
let abort: AbortController | null = null;
onBeforeUnmount(() => {
    seq++;
    abort?.abort();
});

/** 지금 필터로 받는다. `silent` 면 보이던 표를 그대로 두고 새로 받고, 실패하면 토스트만 */
async function load(silent = false): Promise<void> {
    const mine = ++seq;
    abort?.abort();
    abort = new AbortController();
    busy.value = true;
    refreshing.value = silent;
    if (!silent) {
        loadError.value = null;
        fieldErrors.value = {};
    }
    try {
        const found = await api.list(UtilReport.toApi(filters.value, displayValues.value.zoneId), abort.signal);
        if (mine !== seq) return;
        view.value = found;
        loadError.value = null;
        fieldErrors.value = {};
        selection.value = selection.value.filter((id) => selectableIds.value.includes(id));
        // 건수는 있는데 이 쪽이 비었다(큰 쪽 번호·마지막 쪽을 다 처리함) — 마지막 쪽으로
        if (found.items.length === 0 && found.total > 0 && filters.value.page > 1) apply(UtilReport.change(filters.value, { page: UtilReport.pageCount(found.total, found.size || filters.value.size) }));
    } catch (e) {
        if (mine !== seq) return;
        const error = errorOf(e);
        const fields = error.status === 400 ? (error.fields ?? {}) : {};
        if (Object.keys(fields).some((key) => FIELD_KEYS.includes(key))) {
            fieldErrors.value = { ...fields };
            view.value = null;
            loadError.value = null;
        } else if (silent && view.value) toast.error(t('report.refreshFailed', { reason: error.message }), 8000);
        else {
            view.value = null;
            loadError.value = error;
        }
    } finally {
        if (mine === seq) {
            busy.value = false;
            refreshing.value = false;
        }
    }
}

/** 받은 상세의 사건으로 목록 줄을 바꿔 끼운다(열람으로 검토 중이 됨·처리 뒤) — 목록을 다시 받지 않는다 */
function patchRow(next: ReportCaseItem): void {
    const list = view.value;
    if (!list) return;
    const at = list.items.findIndex((item) => item.id === next.id);
    if (at < 0) return;
    const copy = [...list.items];
    copy[at] = next;
    view.value = { ...list, items: copy };
}

onMounted(() => {
    void loadOptions();
    void gradeAppearance.ensureLoaded();
});

/* ── 선택·일괄 무시 ── */

const selection = ref<string[]>([]);
const selectableIds = computed(() => (canManage.value ? items.value.filter((item) => UtilReport.isSelectable(item)).map((item) => item.id) : []));
const allSelected = computed(() => selectableIds.value.length > 0 && selectableIds.value.every((id) => selection.value.includes(id)));
const selectedOpen = computed(() => items.value.filter((item) => selection.value.includes(item.id) && UtilReport.isSelectable(item)));

function toggle(id: string): void {
    if (selection.value.includes(id)) selection.value = selection.value.filter((x) => x !== id);
    else if (selectableIds.value.includes(id) && selection.value.length < UtilReport.BULK_MAX) selection.value = [...selection.value, id];
}
function toggleAll(): void {
    selection.value = allSelected.value ? [] : selectableIds.value.slice(0, UtilReport.BULK_MAX);
}

const bulkOpen = ref(false);
const bulkError = ref('');
const bulkFieldError = ref('');
const bulkResult = ref<ReportBulkResult | null>(null);
const bulkTitles = ref<Record<string, string>>({});
const bulkTargetIds = computed(() => (bulkResult.value ? [] : selectedOpen.value.map((item) => item.id)));

function openBulk(): void {
    if (acting.value || selectedOpen.value.length === 0) return;
    bulkError.value = '';
    bulkFieldError.value = '';
    bulkResult.value = null;
    bulkOpen.value = true;
}

watch(bulkOpen, (isOpen) => {
    if (!isOpen) bulkResult.value = null;
});

async function submitBulk(payload: { note: string; falseReport: boolean }): Promise<void> {
    const targets = selectedOpen.value;
    if (acting.value || targets.length === 0) return;
    acting.value = true;
    bulkError.value = '';
    bulkFieldError.value = '';
    try {
        const result = await api.bulkDismiss({ ids: targets.map((item) => item.id), falseReport: payload.falseReport, note: payload.note || undefined });
        bulkTitles.value = Object.fromEntries(targets.map((item) => [item.id, `${UtilReportMessages.caseNo(t, item)} ${item.target.title ?? t('report.targetPurged')}`]));
        bulkResult.value = { done: result.done ?? [], failed: result.failed ?? [] };
        selection.value = [];
        toast.show(UtilReportMessages.bulkSummary(t, bulkResult.value), { tone: bulkResult.value.failed.length === 0 ? 'success' : 'default', life: 6000 });
        await load(true);
        const openId = detail.openId.value;
        if (openId && targets.some((item) => item.id === openId)) void detail.reload();
    } catch (e) {
        const error = errorOf(e);
        if (error.fields?.note) bulkFieldError.value = error.fields.note;
        else bulkError.value = error.fields?.ids ?? error.message;
    } finally {
        acting.value = false;
    }
}

/* ── 사건 하나의 처리 ── */

/** 처리·일괄·다시 열기가 가는 중 — 같이 쓴다 */
const acting = ref(false);

const actOpen = ref(false);
const actKind = ref<ReportActKind | null>(null);
const actTarget = ref<ReportCaseItem | null>(null);
const actError = ref('');
const actFieldErrors = ref<Record<string, string>>({});

function openAct(item: ReportCaseItem, kind: ReportActKind): void {
    if (acting.value) return;
    actTarget.value = item;
    actKind.value = kind;
    actError.value = '';
    actFieldErrors.value = {};
    actOpen.value = true;
}

/** 그 사이 다른 운영자가 처리했거나 상태가 바뀌었다 — 서버 문구로 알리고 새로 받는다(대화창은 닫는다) */
const STALE_CODES = new Set(['REPORT_CASE_CLOSED', 'REPORT_ACTION_INVALID', 'REPORT_CASE_OPEN_EXISTS', 'REPORT_CASE_NOT_FOUND']);

/** 처리 응답(새 상세)을 서랍·목록에 반영한다 */
async function afterChange(id: string, next: ReportCaseDetail | null): Promise<void> {
    if (next) {
        detail.set(next);
        patchRow(next.case);
    }
    await load(true);
    if (!next && detail.openId.value === id) await detail.reload();
}

async function submitAct(payload: { reasonCode: ReportReasonCode | null; note: string; falseReport: boolean }): Promise<void> {
    const item = actTarget.value;
    const kind = actKind.value;
    if (!item || !kind || acting.value) return;
    acting.value = true;
    actError.value = '';
    actFieldErrors.value = {};
    let result: ReportCaseDetail;
    try {
        result =
            kind === 'DISMISS'
                ? await api.dismiss(item.id, { reasonCode: payload.reasonCode ?? undefined, note: payload.note || undefined, falseReport: payload.falseReport })
                : await api.resolve(item.id, { action: kind, reasonCode: payload.reasonCode as ReportReasonCode, note: payload.note });
    } catch (e) {
        acting.value = false;
        const error = errorOf(e);
        if (STALE_CODES.has(error.code)) {
            toast.error(t('report.staleFailed', { reason: error.message }), 8000);
            actOpen.value = false;
            await afterChange(item.id, null);
            return;
        }
        // 칸 오류는 그 칸 아래, 그 밖은 대화창 안 한 줄(서버 문구 그대로) — 쓰던 글은 그대로 둔다
        const fields = error.status === 400 ? (error.fields ?? {}) : {};
        actFieldErrors.value = { ...fields };
        const other = Object.entries(fields)
            .filter(([k]) => k !== 'reasonCode' && k !== 'note')
            .map(([, v]) => v);
        actError.value = Object.keys(fields).length > 0 ? other.join(' ') : error.message;
        return;
    }
    acting.value = false;
    actOpen.value = false;
    toast.success(UtilReportMessages.done(t, kind, payload.falseReport));
    await afterChange(item.id, result);
}

/** 무시한 사건을 다시 연다 — 신고들이 다시 대기가 되고 허위 표시가 풀린다(복구한 자동 숨김은 다시 숨기지 않는다) */
async function reopen(): Promise<void> {
    const item = detail.detail.value?.case;
    if (!item || acting.value) return;
    const ok = await confirm({ title: t('report.reopenTitle'), message: t('report.reopenText'), confirmLabel: t('report.reopen') });
    if (!ok) return;
    acting.value = true;
    let result: ReportCaseDetail;
    try {
        result = await api.reopen(item.id);
    } catch (e) {
        acting.value = false;
        const error = errorOf(e);
        toast.error(STALE_CODES.has(error.code) ? t('report.staleFailed', { reason: error.message }) : error.message, 8000);
        if (STALE_CODES.has(error.code)) await afterChange(item.id, null);
        return;
    }
    acting.value = false;
    toast.success(t('report.done.REOPEN'));
    await afterChange(item.id, result);
}

/* ── 줄의 ⋯ 메뉴 ── */

const rowMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const rowMenuTarget = ref<ReportCaseItem | null>(null);

/** 줄 메뉴 — 처리(열린 사건 + `report.manage`) · 게시글 관리에서 보기(`post.read`) */
function rowMenuFor(item: ReportCaseItem): MenuItem[] {
    const menu: MenuItem[] = UtilReport.actionList(UtilReport.actionsOf(item, canManage.value)).map((kind) => ({
        label: t(`report.act.${kind}`),
        icon: UtilReport.actIcon(kind),
        class: UtilReport.isDanger(kind) ? 'report-menu-danger' : undefined,
        command: () => openAct(item, kind),
    }));
    const postId = item.target.postId;
    const groupId = item.target.groupId;
    if (canViewPost.value && postId) menu.push({ label: t('report.openPostAdmin'), icon: 'pi pi-file-edit', command: () => void navigateTo({ path: '/posts', query: { post: postId } }) });
    if (canViewGroup.value && item.targetType === 'GROUP' && groupId) menu.push({ label: t('report.openGroupAdmin'), icon: 'pi pi-users', command: () => void navigateTo({ path: '/groups', query: { group: groupId } }) });
    return menu;
}
const rowMenuItems = computed<MenuItem[]>(() => (rowMenuTarget.value ? rowMenuFor(rowMenuTarget.value) : []));

function openRowMenu(event: Event, item: ReportCaseItem): void {
    rowMenuTarget.value = item;
    rowMenu.value?.toggle(event);
}

/* ── 사건 서랍 ── */

const detail = useReportDetail();
const drawerOpen = ref(false);

const drawerItem = computed<ReportCaseItem | null>(() => {
    const id = detail.openId.value;
    if (!id) return null;
    return detail.detail.value?.case ?? items.value.find((item) => item.id === id) ?? null;
});
const neighbors = computed(() => UtilReport.neighbors(items.value, detail.openId.value));
const drawerActions = computed(() => (detail.detail.value ? UtilReport.actionsOf(detail.detail.value.case, canManage.value) : UtilReport.noActions()));
const authorGrade = computed(() => {
    const grade = detail.detail.value?.authorCard.grade;
    return grade ? gradeAppearance.badgeOf(grade) : null;
});

// 상세를 받으면(열람으로 검토 중이 됨) 목록 줄도 그 사건으로 바꾼다
watch(
    () => detail.detail.value?.case,
    (next) => {
        if (next) patchRow(next);
    },
);

function rowClass(data: ReportCaseItem): string {
    return drawerOpen.value && data.id === detail.openId.value ? 'report-row-open cursor-pointer' : 'cursor-pointer';
}

/** `?report=` 만 바꾼다(replace) — 목록은 다시 받지 않는다(필터 열쇠에 없다) */
function setReportQuery(id: string | null): void {
    void router.replace({ path: LIST_PATH, query: { ...UtilReport.toQuery(filters.value), ...(id ? { report: id } : {}) } });
}

function openCase(item: ReportCaseItem): void {
    drawerOpen.value = true;
    if (detail.openId.value !== item.id) void detail.open(item.id);
    setReportQuery(item.id);
}

function onRowClick(e: { originalEvent: Event; data: ReportCaseItem }): void {
    openCase(e.data);
}

function onDrawerAct(kind: ReportActKind): void {
    const item = detail.detail.value?.case;
    if (item) openAct(item, kind);
}

function onDrawerClosed(): void {
    if (drawerOpen.value) return;
    detail.close();
}

watch(drawerOpen, (isOpen) => {
    if (!isOpen && UtilReport.parseReport(route.query) !== null) setReportQuery(null);
});

// 주소의 `report`(새로고침·뒤로 가기·운영자 알림 링크)를 따라 서랍을 연다
watch(
    () => UtilReport.parseReport(route.query),
    (id) => {
        if (id && (id !== detail.openId.value || !drawerOpen.value)) {
            drawerOpen.value = true;
            if (id !== detail.openId.value) void detail.open(id);
        } else if (!id && drawerOpen.value) {
            drawerOpen.value = false;
        }
    },
    { immediate: true },
);

// 필터가 바뀌면 선택을 비우고 다시 받는다. 처음 한 번도 여기서 받는다(`report` 는 열쇠에 없다).
// ⚠ 맨 끝에 둔다 — 바로 한 번 도는(`immediate`) 감시라 위의 상태(`selection` 등)가 모두 만들어진 뒤여야 한다
watch(
    () => UtilReport.key(filters.value),
    () => {
        selection.value = [];
        void load();
    },
    { immediate: true },
);
</script>

<style scoped>
/* 서랍에 열린 사건의 줄 */
.report-table :deep(tr.report-row-open) {
    background: var(--p-highlight-background);
    color: var(--p-highlight-color);
}
</style>

<style>
/* ⋯ 메뉴의 위험한 처리(숨기기·삭제) — 메뉴는 body 로 떠서 scoped 가 닿지 않는다 */
.p-menu .report-menu-danger .p-menu-item-label,
.p-menu .report-menu-danger .p-menu-item-icon {
    color: var(--p-red-600);
}
</style>
