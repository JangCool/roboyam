<template>
    <!--
        소모임 목록(`/groups`) — community 관리자 앱 `groups/manage.vue`(소모임 관리)를 옮겼다. 열려 있는 소모임과 비활성이 된 소모임을 한 표에서 찾고, 줄을 누르면 오른쪽 서랍에서 상세와 운영 조치(비활성화·다시 열기)를 한다.
        승인 대기·반려·해산은 이 표에 나오지 않는다 — 승인 대기는 개설 승인(`/group-requests`).
        - 칩: 상태(전체·열림·비활성 — 하나만) · 성질(모집 마감·오프라인 — 켜고 끈다) · 공개 범위(전체·공개·비공개 — 하나만). 숫자는 서버가 준 건수(검색어만 적용).
        - 검색: 이름·모임장 닉네임 — 보낼 때 정한다. 정렬은 서버가 최신순(개설일)으로 고정한다(정렬 단추 없음).
        - **조건·쪽은 주소의 쿼리**(`?status=&closed=1&offline=1&visibility=&q=&page=`)이고 열어 둔 소모임은 `&group=ID` — 새로고침해도 그대로다. 조건을 바꾸면 1쪽으로.
        - 비활성화는 사유 필수(1~200자, 모임장에게 그대로 전달), 다시 열기는 메모 선택(운영 기록에만). 그 사이 다른 운영자가 처리했으면(409) 알리고 새로 받는다.
        권한: 화면 `group.read`(메뉴가 거른다) · 조치 `group.manage`(없으면 단추를 막고 이유를 보인다) · 게시판 설정 링크 `board.read`. 감추는 것은 편의이고 막는 것은 서버다.
        ⚠ 목업 API(`useGroupAdminApi` — `?mock=groups-fail` · `groups-empty` · `detail-fail` · `deactivate-conflict` …, 목록은 `group-mock.ts` 머리말).
        community 와 다른 점: 좁은 화면의 카드 목록 대신 가로 스크롤 표(게시판 관리와 같은 방식) · 상세 서랍의 외부 링크(공개 화면·회원 조회·신고 보기) 없음.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('group.listTitle')" :subtitle="t('group.listSubtitle')">
            <template #actions>
                <Button :label="t('group.refresh')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="refreshing" :disabled="loading" @click="refresh" />
            </template>
        </PageTitle>

        <Message v-if="!canWrite" severity="info" :closable="false" size="small">{{ t('group.manageReadOnly') }}</Message>

        <!-- 승인 대기 건수 · 개설 승인·설정·분류로 가는 링크 -->
        <p v-if="page" class="m-0 text-[13px] text-muted-color">
            {{ UtilGroupMessages.pendingLine(t, page.pendingRequests) }}
            <NuxtLink :to="UtilGroup.REQUESTS_PATH" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.requestsLink') }} ›</NuxtLink>
            · {{ t('group.rulesLead') }}
            <NuxtLink :to="UtilGroup.SETTINGS_PATH" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.settingsLink') }}</NuxtLink>{{ t('group.rulesMiddle') }}
            <NuxtLink :to="UtilGroup.CATEGORIES_PATH" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.categoriesLink') }}</NuxtLink>{{ t('group.rulesTail') }}
        </p>

        <!-- 칩 -->
        <div class="flex flex-wrap items-center gap-2">
            <div class="flex flex-wrap items-center gap-2" role="group" :aria-label="t('group.chipsLabel')">
                <template v-if="page">
                    <Button v-for="chip in chips" :key="chip.key" type="button" size="small" rounded :label="chip.text" :severity="chip.active ? undefined : 'secondary'" :variant="chip.active ? undefined : 'outlined'" :aria-pressed="chip.active" @click="toggleChip(chip.key)" />
                </template>
                <template v-else-if="loading">
                    <Skeleton v-for="n in 5" :key="n" border-radius="9999px" height="1.875rem" width="4.75rem" aria-hidden="true" />
                </template>
            </div>
            <span class="mx-1 h-5 w-px bg-(--line) max-sm:hidden" aria-hidden="true" />
            <div class="flex flex-wrap items-center gap-2" role="group" :aria-label="t('group.visibilityChipsLabel')">
                <Button v-for="key in VISIBILITY_KEYS" :key="key" type="button" size="small" rounded :label="visibilityChipLabel(key)" :severity="filters.visibility === key ? undefined : 'secondary'" :variant="filters.visibility === key ? undefined : 'outlined'" :aria-pressed="filters.visibility === key" @click="apply(UtilGroup.change(filters, { visibility: key }))" />
            </div>
            <Button v-if="hasConditions" type="button" :label="t('group.filterReset')" size="small" severity="secondary" variant="text" @click="resetFilters" />
        </div>

        <!-- 검색 — 보낼 때 정한다 -->
        <form class="flex max-w-xl min-w-0 gap-2" role="search" novalidate @submit.prevent="submitSearch">
            <IconField class="min-w-0 flex-1">
                <InputIcon class="pi pi-search" />
                <InputText v-model="searchText" type="search" fluid :maxlength="UtilGroup.SEARCH_MAX" :placeholder="t('group.searchPlaceholder')" :aria-label="t('group.searchLabel')" autocomplete="off" />
            </IconField>
            <Button v-if="filters.q" type="button" icon="pi pi-times" severity="secondary" variant="text" size="small" :aria-label="t('group.searchClear')" @click="clearSearch" />
            <Button type="submit" :label="t('group.searchSubmit')" size="small" severity="secondary" variant="outlined" />
        </form>

        <!-- 표 -->
        <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
            <DataTable
                :value="page?.items ?? []"
                data-key="id"
                lazy
                paginator
                :first="(filters.page - 1) * UtilGroup.PAGE_SIZE"
                :rows="UtilGroup.PAGE_SIZE"
                :total-records="page?.total ?? 0"
                :loading="refreshing"
                row-hover
                scrollable
                :size="prefs.density === 'compact' ? 'small' : undefined"
                :row-class="rowClass"
                :current-page-report-template="totalTemplate"
                paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink CurrentPageReport"
                class="min-w-0"
                @page="onPage"
                @row-click="onRowClick"
            >
                <template #empty>
                    <div v-if="loading && !page" class="flex flex-col gap-2 py-2" role="status" :aria-label="t('common.loading')">
                        <Skeleton v-for="n in 5" :key="n" height="2.5rem" />
                    </div>
                    <ErrorState v-else-if="loadError" :title="t('group.listLoadFailed')" :description="`${loadError.message} ${t('group.filterKept')}`" retry :retrying="loading" @retry="load" />
                    <EmptyState v-else-if="outOfRange" icon="pi pi-inbox" :title="t('group.outOfRangeTitle')" :description="t('group.outOfRangeBody')" />
                    <EmptyState v-else-if="UtilGroup.emptyKind(filters) === 'FILTERED'" icon="pi pi-filter-slash" :title="t('group.emptyFilteredTitle')" :description="t('group.emptyFilteredBody')">
                        <Button type="button" :label="t('group.filterReset')" size="small" severity="secondary" variant="outlined" @click="resetFilters" />
                    </EmptyState>
                    <EmptyState v-else icon="pi pi-users" :title="t('group.emptyNothingTitle')" :description="t('group.emptyNothingBody')">
                        <Button as="router-link" :to="UtilGroup.REQUESTS_PATH" :label="t('group.requestsLink')" icon="pi pi-inbox" size="small" />
                    </EmptyState>
                </template>

                <Column :header="t('group.col.name')" style="min-width: 16rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1" :class="isInactive(data) ? 'opacity-70' : ''">
                            <Button type="button" :label="data.name" variant="link" size="small" class="max-w-full min-w-0 p-0! text-left! font-semibold! [overflow-wrap:anywhere]" :aria-label="t('group.openLabel', { name: data.name })" @click="openRow(data)" />
                            <GroupTag v-for="key in badgesOf(data)" :key="key" :value="t(`group.badge.${key}`)" :severity="UtilGroup.badgeSeverity(key)" />
                        </div>
                    </template>
                </Column>

                <Column :header="t('group.col.category')" style="min-width: 9rem">
                    <template #body="{ data }">
                        <div :class="isInactive(data) ? 'opacity-70' : ''">
                            <span class="text-[13px]" :class="data.category ? '' : 'text-muted-color'">{{ data.category?.name ?? t('group.categoryNone') }}</span>
                            <small v-if="UtilGroup.boardLine(data)" class="block text-[11px] text-muted-color">{{ UtilGroup.boardLine(data) }}</small>
                        </div>
                    </template>
                </Column>

                <Column :header="t('group.col.leader')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <span class="text-[13px]" :class="isInactive(data) ? 'opacity-70' : ''"><GroupPersonLabel :person="data.leader" /></span>
                    </template>
                </Column>

                <Column :header="t('group.col.members')" style="width: 6rem">
                    <template #body="{ data }">
                        <span class="block text-right text-[13px] tabular-nums" :class="isInactive(data) ? 'opacity-70' : ''">{{ t('group.memberLine', { count: data.memberCount, capacity: data.capacity }) }}</span>
                    </template>
                </Column>

                <Column :header="t('group.col.activity')" style="width: 7.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] text-muted-color" :title="time.full(data.lastActivityAt)">{{ time.listLabel(data.lastActivityAt) || '—' }}</span>
                    </template>
                </Column>

                <Column :header="t('group.col.opened')" style="width: 8.5rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums text-muted-color" :title="time.full(data.openedAt)">{{ time.date(data.openedAt) || '—' }}</span>
                    </template>
                </Column>

                <Column :header="t('group.col.status')" style="width: 5.5rem">
                    <template #body="{ data }">
                        <GroupTag :value="t(`group.status.${data.status}`)" :severity="isInactive(data) ? 'danger' : 'success'" />
                    </template>
                </Column>
            </DataTable>
        </div>

        <GroupManageDrawer
            v-model:visible="drawerOpen"
            :summary="summary"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :busy="acting"
            :can-write="canWrite"
            :can-board="can('board.read')"
            @retry="loadDetail"
            @deactivate="openReason('deactivate')"
            @reactivate="openReason('reactivate')"
        />

        <!-- 비활성화 — 사유 필수, 모임장에게 그대로 전달 -->
        <GroupReasonDialog
            v-model:visible="deactivateOpen"
            :title="detail ? t('group.deactivateConfirmTitle', { name: detail.name }) : t('group.deactivateTitle')"
            :description="detail ? t('group.deactivateConfirmBody', { leader: detail.leader.nickname }) : ''"
            :label="t('group.deactivateLabel')"
            :hint="t('group.requiredHint')"
            :help="t('group.deactivateReasonHelp')"
            :placeholder="t('group.deactivatePlaceholder')"
            :max="UtilGroup.REASON_MAX"
            required
            :confirm-label="t('group.deactivateConfirm')"
            :busy="acting"
            :error="dialogError"
            @submit="deactivate"
        />

        <!-- 다시 열기 — 메모 선택, 운영 기록에만 -->
        <GroupReasonDialog
            v-model:visible="reactivateOpen"
            :title="detail ? t('group.reactivateConfirmTitle', { name: detail.name }) : t('group.reactivateTitle')"
            :description="detail ? UtilGroupMessages.reactivateBody(t, detail.leader.nickname, detail.deactivation?.reportCase?.no ?? null) : ''"
            :label="t('group.reactivateLabel')"
            :hint="t('group.reactivateHint')"
            :help="t('group.reactivateMemoHelp')"
            :placeholder="t('group.reactivatePlaceholder')"
            :max="UtilGroup.MEMO_MAX"
            tone="primary"
            :confirm-label="t('group.reactivateConfirm')"
            :busy="acting"
            :error="dialogError"
            @submit="reactivate"
        />
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupDetail, GroupListPage, GroupRow, GroupVisibilityFilter } from '~/composables/group/api/useGroupAdminApi.types';
import UtilGroup, { type GroupBadgeKey, type GroupListFilters } from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

type ChipKey = 'ALL' | 'ACTIVE' | 'INACTIVE' | 'closed' | 'offline';
const VISIBILITY_KEYS: readonly GroupVisibilityFilter[] = ['ALL', 'PUBLIC', 'PRIVATE'];

const { t } = useI18n();
useSeoMeta({ title: () => t('group.listTitle') });

const api = useGroupAdminApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { prefs } = useLayoutPrefs();
const { can } = useAdminPermission();

/** 처리 권한 — 승인·반려·비활성화·다시 열기(정의: util-permission-catalog.ts) */
const canWrite = computed(() => can('group.manage'));

/** 주소의 쿼리를 읽은 필터 */
const filters = computed<GroupListFilters>(() => UtilGroup.parse(route.query));
const hasConditions = computed(() => UtilGroup.hasConditions(filters.value));

const page = ref<GroupListPage | null>(null);
const loading = ref(false);
const refreshing = ref(false);
const loadError = ref<ApiError | null>(null);

const outOfRange = computed(() => (page.value ? UtilGroup.outOfRange(page.value.items.length, page.value.total, filters.value.page) : false));
const totalTemplate = computed(() => t('common.totalCount', { count: '{totalRecords}' }));

let seq = 0;
/** 지금 필터로 받는다 — 보이던 표는 지우지 않는다(`silent` 면 스켈레톤 없이) */
async function load(silent = false): Promise<void> {
    const mine = ++seq;
    if (silent) refreshing.value = true;
    else loading.value = true;
    loadError.value = null;
    try {
        const res = await api.list(UtilGroup.toApi(filters.value));
        if (mine !== seq) return;
        page.value = res;
    } catch (e) {
        if (mine !== seq) return;
        if (silent && page.value) toast.error(UtilGroupMessages.failed(t, 'group.refreshFailed', e), 8000);
        else {
            page.value = null;
            loadError.value = toApiError(e);
        }
    } finally {
        if (mine === seq) {
            loading.value = false;
            refreshing.value = false;
        }
    }
}

function refresh(): void {
    void load(true);
    if (openId.value) void loadDetail();
}

// 필터(주소)가 바뀌면 다시 받는다 — 서랍의 `group` 은 필터 열쇠에 들어 있지 않아 다시 받지 않는다
watch(() => UtilGroup.key(filters.value), () => void load(Boolean(page.value)), { immediate: true });

// 건수는 있는데 이 쪽이 비었으면(큰 쪽 번호를 주소에 넣었다) 마지막 쪽으로
watch(outOfRange, (out) => {
    if (out && page.value) apply(UtilGroup.change(filters.value, { page: UtilGroup.pageCount(page.value.total) }));
});

/* ── 주소 ↔ 필터 ── */

function apply(next: GroupListFilters, how: 'push' | 'replace' = 'replace'): void {
    if (UtilGroup.key(next) === UtilGroup.key(filters.value)) return;
    void router[how]({ path: UtilGroup.LIST_PATH, query: UtilGroup.toQuery(next) });
}

function onPage(event: { first: number }): void {
    apply(UtilGroup.change(filters.value, { page: Math.floor(event.first / UtilGroup.PAGE_SIZE) + 1 }), 'push');
}

function resetFilters(): void {
    searchText.value = '';
    apply({ ...UtilGroup.DEFAULTS });
}

/* ── 칩 ── */

const chips = computed(() => {
    const c = page.value?.counts;
    if (!c) return [];
    const list: { key: ChipKey; name: string; count: number; active: boolean }[] = [
        { key: 'ALL', name: t('group.chipAll'), count: c.all, active: filters.value.status === 'ALL' },
        { key: 'ACTIVE', name: t('group.status.ACTIVE'), count: c.active, active: filters.value.status === 'ACTIVE' },
        { key: 'INACTIVE', name: t('group.status.INACTIVE'), count: c.inactive, active: filters.value.status === 'INACTIVE' },
        { key: 'closed', name: t('group.badge.CLOSED'), count: c.closed, active: filters.value.closed },
        { key: 'offline', name: t('group.badge.OFFLINE'), count: c.offline, active: filters.value.offline },
    ];
    return list.map((chip) => ({ key: chip.key, active: chip.active, text: t('group.chip', { name: chip.name, count: chip.count }) }));
});

/** 상태 칩은 하나만(켠 것을 다시 누르면 전체) · 성질 칩은 켜고 끈다 */
function toggleChip(key: ChipKey): void {
    const f = filters.value;
    if (key === 'closed') apply(UtilGroup.change(f, { closed: !f.closed }));
    else if (key === 'offline') apply(UtilGroup.change(f, { offline: !f.offline }));
    else apply(UtilGroup.change(f, { status: f.status === key ? 'ALL' : key }));
}

function visibilityChipLabel(key: GroupVisibilityFilter): string {
    return key === 'ALL' ? t('group.chipAll') : t(`group.badge.${key}`);
}

/* ── 검색 ── */

const searchText = ref(filters.value.q);
watch(
    () => filters.value.q,
    (q) => (searchText.value = q),
);

function submitSearch(): void {
    apply(UtilGroup.change(filters.value, { q: searchText.value.trim() }));
}

function clearSearch(): void {
    searchText.value = '';
    apply(UtilGroup.change(filters.value, { q: '' }));
}

/* ── 줄 ── */

function isInactive(row: GroupRow): boolean {
    return row.status === 'INACTIVE';
}

function badgesOf(row: GroupRow): GroupBadgeKey[] {
    // 상태 칸이 따로 있어 「비활성」 딱지는 이름 뒤에 다시 붙이지 않는다
    return UtilGroup.badges(row).filter((key) => key !== 'INACTIVE');
}

function rowClass(row: GroupRow): string {
    return `cursor-pointer${isInactive(row) ? ' bg-surface-50 dark:bg-surface-900' : ''}`;
}

/** 줄을 누르면 서랍 — 줄 안의 단추·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent): void {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input')) return;
    openRow(event.data as GroupRow);
}

/* ── 서랍 ── */

const drawerOpen = ref(false);
const openId = ref<string | null>(null);
const summary = ref<GroupRow | null>(null);
const detail = ref<GroupDetail | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);

let detailSeq = 0;
async function loadDetail(): Promise<void> {
    const id = openId.value;
    if (!id) return;
    const mine = ++detailSeq;
    detailLoading.value = true;
    detailError.value = null;
    try {
        const res = await api.detail(id);
        if (mine !== detailSeq) return;
        detail.value = res;
    } catch (e) {
        if (mine !== detailSeq) return;
        detail.value = null;
        detailError.value = toApiError(e);
    } finally {
        if (mine === detailSeq) detailLoading.value = false;
    }
}

/** `?group=ID` 를 바꾼다 — 주소만 바꾸고 목록은 다시 받지 않는다(필터 열쇠에 들어 있지 않다) */
function setGroupQuery(id: string | null): void {
    const query: Record<string, string> = { ...UtilGroup.toQuery(filters.value) };
    if (id) query.group = id;
    void router.replace({ path: UtilGroup.LIST_PATH, query });
}

function openRow(row: GroupRow): void {
    summary.value = row;
    if (openId.value !== row.id) {
        openId.value = row.id;
        detail.value = null;
        void loadDetail();
    }
    drawerOpen.value = true;
    setGroupQuery(row.id);
}

// 주소의 `group`(새로고침·뒤로 가기)을 따라 서랍을 연다
watch(
    () => UtilGroup.parseGroup(route.query),
    (id) => {
        if (id && id !== openId.value) {
            openId.value = id;
            summary.value = page.value?.items.find((row) => row.id === id) ?? null;
            detail.value = null;
            drawerOpen.value = true;
            void loadDetail();
        } else if (!id && drawerOpen.value) {
            drawerOpen.value = false;
        }
    },
    { immediate: true },
);

// 서랍을 닫으면 주소의 `group` 을 지운다
watch(drawerOpen, (value) => {
    if (value) return;
    openId.value = null;
    if (UtilGroup.parseGroup(route.query)) setGroupQuery(null);
});

/* ── 운영 조치 ── */

const deactivateOpen = ref(false);
const reactivateOpen = ref(false);
const acting = ref(false);
/** 대화상자 칸 아래에 보일 서버 문구(`fields.reason`·`fields.memo` 또는 서버 문구) */
const dialogError = ref('');

function openReason(kind: 'deactivate' | 'reactivate'): void {
    if (!canWrite.value || acting.value) return;
    dialogError.value = '';
    if (kind === 'deactivate') deactivateOpen.value = true;
    else reactivateOpen.value = true;
}

async function act(kind: 'deactivate' | 'reactivate', text: string): Promise<void> {
    const target = detail.value;
    if (!target || acting.value) return;
    acting.value = true;
    dialogError.value = '';
    try {
        if (kind === 'deactivate') await api.deactivate(target.id, text);
        else await api.reactivate(target.id, text);
        deactivateOpen.value = false;
        reactivateOpen.value = false;
        toast.success(t(kind === 'deactivate' ? 'group.deactivated' : 'group.reactivated', { name: target.name }), 5000);
        void load(true);
        void loadDetail();
    } catch (e) {
        const field = UtilGroup.fieldError(e, kind === 'deactivate' ? 'reason' : 'memo');
        if (field) {
            dialogError.value = field;
            return;
        }
        deactivateOpen.value = false;
        reactivateOpen.value = false;
        if (UtilGroup.isConflict(e)) toast.show(t('group.alreadyHandled'), { life: 5000 });
        else toast.error(UtilGroupMessages.failed(t, 'group.actionFailed', e), 8000);
        void load(true);
        void loadDetail();
    } finally {
        acting.value = false;
    }
}

const deactivate = (reason: string) => act('deactivate', reason);
const reactivate = (memo: string) => act('reactivate', memo);
</script>
