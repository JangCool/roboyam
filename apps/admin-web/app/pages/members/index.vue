<template>
    <!--
        회원 관리 — 공개 사이트 회원(이용자)을 조건으로 찾아보는 조회 화면이다. 운영자(관리자 앱 계정)는 여기 나오지 않는다(정책: docs/domain/operator-permissions.md).
        위쪽 조회 조건 카드는 그누보드 회원 관리의 필터 구조를 따른다(`MemberSearchPanel`). 조건은 「검색」(또는 Enter)을 눌렀을 때만 서버로 가고, 쪽·정렬은 마지막으로 검색한 조건으로 다시 묻는다.
        표는 서버가 한 쪽씩 준다(`lazy`). 이메일은 서버가 가려서 준다(`member.pii` 가 있을 때만 원문) — 화면은 받은 대로 그린다.
        행을 누르면(또는 Enter·Space) 회원 상세 서랍이 열린다(`MemberDetailDrawer`, 읽기 전용 — 조치는 후속). 행 액션은 없다. 본문 여백은 셸(`.app-content`)이 준다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('member.title')" :subtitle="t('member.subtitle')" />

        <MemberSearchPanel :model-value="filter" v-model:detail-rows="detailRows" :role-options="roleOptions" :busy="busy" :summary="summary" @search="search" @reset="resetAll" />

        <div class="flex min-w-0 flex-col gap-2">
            <!-- 툴바 — 왼쪽 「전체 N명」·정렬 안내, 오른쪽 끝 「컬럼 선택」 -->
            <div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-2">
                <div class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
                    <p class="text-[13px] font-medium" aria-live="polite">{{ t('member.total', { count: total.toLocaleString(locale) }) }}</p>
                    <p v-if="sortText" class="text-xs text-muted-color">{{ sortText }}</p>
                    <p v-if="!canPii" class="flex items-center gap-1.5 text-xs text-muted-color">
                        <i class="pi pi-eye-slash" aria-hidden="true" />
                        {{ t('member.maskedHint') }}
                    </p>
                </div>
                <CheckListPopover v-model="columns" align="right" white class="ml-auto" :items="columnItems" :button-label="t('member.columnPick')" icon="pi pi-sliders-h" :title="t('member.columnTitle')" :description="t('member.columnDesc')" :reset-label="t('member.columnReset')" @reset="columns = UtilMember.defaultColumns()" />
            </div>

            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable
                    :value="rows"
                    data-key="memberNo"
                    :selection="drawerOpen ? selectedRow : null"
                    selection-mode="single"
                    :meta-key-selection="false"
                    @row-click="onRowClick"
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
                    paginator-template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
                    class="min-w-0"
                    @page="onPage"
                    @sort="onSort"
                >
                    <template #empty>
                        <ErrorState v-if="loadError" :title="t('member.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                        <div v-else-if="busy" class="h-32" aria-hidden="true" />
                        <!-- 빈 상태를 구분한다 — 조건 때문인지 아직 회원이 없는 건지 -->
                        <EmptyState v-else-if="hasApplied" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('member.clearConditions')" size="small" severity="secondary" variant="outlined" @click="resetAll" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-users" :title="t('member.empty')" :description="t('member.emptyHint')" />
                    </template>

                    <Column v-for="col in visibleColumns" :key="col.key" :field="col.sort" :sortable="!!col.sort" :header="t(col.labelKey)" :style="`min-width: ${widthOf(col.key)}`">
                        <template #body="{ data }">
                            <template v-if="col.key === 'memberNo'">
                                <code class="text-[12px] tabular-nums text-muted-color">{{ data.memberNo }}</code>
                            </template>
                            <template v-else-if="col.key === 'email'">
                                <span v-if="data.email" class="inline-flex max-w-full items-center gap-1.5">
                                    <span class="truncate text-[13px]" :title="data.email">{{ data.email }}</span>
                                    <i v-if="data.emailMasked" class="pi pi-eye-slash text-[11px] text-muted-color" aria-hidden="true" />
                                    <span v-if="data.emailMasked" class="sr-only">{{ t('member.maskedCell') }}</span>
                                </span>
                                <span v-else class="text-muted-color">{{ t('member.none') }}</span>
                            </template>
                            <template v-else-if="col.key === 'nickname'">
                                <span class="block truncate text-[13px]" :title="data.nickname">{{ data.nickname }}</span>
                            </template>
                            <template v-else-if="col.key === 'grade'">
                                <GradeBadge v-bind="gradeState.badgeOf(data.grade)" />
                            </template>
                            <template v-else-if="col.key === 'memberType'">
                                <Tag :value="t(`member.type.${data.memberType}`)" :icon="UtilMember.typeIcon(data.memberType)" :severity="data.memberType === 'BUSINESS' ? 'contrast' : 'secondary'" />
                            </template>
                            <template v-else-if="col.key === 'status'">
                                <Tag :value="t(`member.status.${data.status}`)" :icon="UtilMember.statusIcon(data.status)" :severity="UtilMember.statusSeverity(data.status)" />
                            </template>
                            <template v-else-if="col.key === 'authMethod'">
                                <span class="text-[13px]">{{ t(`member.authMethod.${data.authMethod}`) }}</span>
                            </template>
                            <template v-else-if="col.key === 'emailVerifiedAt'">
                                <span class="text-[12px] tabular-nums">{{ data.emailVerifiedAt ? time.date(data.emailVerifiedAt) : t('member.none') }}</span>
                            </template>
                            <template v-else-if="col.key === 'joinedAt'">
                                <span class="text-[12px] tabular-nums">{{ time.date(data.joinedAt) }}</span>
                            </template>
                            <template v-else-if="col.key === 'lastLoginAt'">
                                <span v-if="data.lastLoginAt" class="text-[12px] tabular-nums">{{ time.full(data.lastLoginAt) }}</span>
                                <span v-else class="text-[12px] text-muted-color">{{ t('member.neverLoggedIn') }}</span>
                            </template>
                            <template v-else-if="col.key === 'point'">
                                <span class="block text-right text-[13px] tabular-nums">{{ data.point.toLocaleString(locale) }}</span>
                            </template>
                            <template v-else-if="col.key === 'roles'">
                                <span v-if="!data.roles.length" class="text-muted-color">{{ t('member.none') }}</span>
                                <div v-else class="flex flex-wrap items-center gap-1">
                                    <Tag v-for="role in UtilMember.splitRoles(data.roles).shown" :key="role.roleId" :value="role.roleName" severity="secondary" />
                                    <Tag
                                        v-if="UtilMember.splitRoles(data.roles).rest.length"
                                        v-tooltip.top="moreRolesText(data.roles)"
                                        :value="t('member.rolesMore', { count: UtilMember.splitRoles(data.roles).rest.length })"
                                        :aria-label="moreRolesText(data.roles)"
                                        severity="secondary"
                                    />
                                </div>
                            </template>
                            <template v-else-if="col.key === 'dormantAt'">
                                <span class="text-[12px] tabular-nums">{{ data.dormantAt ? time.date(data.dormantAt) : t('member.none') }}</span>
                            </template>
                            <template v-else-if="col.key === 'statusChangedAt'">
                                <span class="text-[12px] tabular-nums">{{ data.statusChangedAt ? time.date(data.statusChangedAt) : t('member.none') }}</span>
                            </template>
                            <template v-else-if="col.key === 'boardRole'">
                                <Tag v-if="data.boardRole !== 'NONE'" :value="t(`member.boardRole.${data.boardRole}`)" :icon="data.boardRole === 'MANAGER' ? 'pi pi-shield' : 'pi pi-user-edit'" severity="info" />
                                <span v-else class="text-muted-color">{{ t('member.none') }}</span>
                            </template>
                            <template v-else-if="col.key === 'sanctionCount'">
                                <span v-if="data.sanctionCount" class="text-[13px] tabular-nums">{{ t('member.sanctionCount', { count: data.sanctionCount }) }}</span>
                                <span v-else class="text-muted-color">{{ t('member.none') }}</span>
                            </template>
                        </template>
                    </Column>
                </DataTable>
            </div>
        </div>

        <MemberDetailDrawer v-model:visible="drawerOpen" ignore-selector="tbody tr" :member="selectedRow" :detail="detail" :loading="detailLoading" :error="detailError" @retry="retryDetail" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { MemberQuery, MemberRoleOption, MemberRoleRef, MemberRow } from '~/composables/member/api/useMemberApi.types';
import UtilMember, { type MemberColumnKey, type MemberDetailRow, type MemberFilter } from '~/utils/util-member';
import UtilMemberMessages from '~/utils/util-member-messages';

// 등급 목록 — 앱 전체가 한 번 읽어 공유한다(`useGradeAppearance`). 표의 뱃지·필터 옵션·조건 칩이 이것을 본다
const gradeState = useGradeAppearance();

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('member.title') });

const api = useMemberApi();
const errorOf = useApiErrorOf();
const time = useDisplayTime();
const values = useDisplayValues();
const { can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 전체 이메일 열람 권한 — 없으면 서버가 이메일을 가려서 주고 화면은 안내 한 줄을 보인다(감추는 것은 편의이고 막는 것은 서버다) */
const canPii = computed(() => can('member.pii'));

// 조건 — 화면 값(draft). 「검색」을 눌러야 서버로 간다
const filter = reactive<MemberFilter>(UtilMember.emptyFilter());
// 표에 보일 칸 · 화면에 보일 상세 조건 줄 — 이 브라우저에 기억한다(localStorage, 계정 설정 API 가 생기면 서버로)
const columns = useStoredList<MemberColumnKey>('roboyam:admin:member-columns', UtilMember.defaultColumns, UtilMember.sanitizeColumns);
const detailRows = useStoredList<MemberDetailRow>('roboyam:admin:member-detail-rows', UtilMember.defaultDetailRows, UtilMember.sanitizeDetailRows);

const roleOptions = ref<MemberRoleOption[]>([]);
const rows = ref<MemberRow[]>([]);
const total = ref(0);
const first = ref(0);
const size = ref(20);
const sortField = ref<string | undefined>('joinedAt');
const sortOrder = ref<number | undefined>(-1);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다 */
const loadError = ref<ApiError | null>(null);

/** 마지막으로 「검색」한 조건 — 쪽·정렬은 이것으로 다시 묻는다(타이핑 중인 조건이 아니다) */
const appliedQuery = ref<MemberQuery>({});
const appliedFilter = ref<MemberFilter>(UtilMember.emptyFilter());
const appliedRows = ref<MemberDetailRow[]>([]);

const hasApplied = computed(() => Object.keys(appliedQuery.value).length > 0);

const visibleColumns = computed(() => UtilMember.COLUMNS.filter((c) => columns.value.includes(c.key)));
const columnItems = computed(() => UtilMember.COLUMNS.map((c) => ({ key: c.key as string, label: t(c.labelKey), required: c.required })));

/** 툴바의 정렬 안내 — 지금 정렬 칸과 방향 */
const sortText = computed(() => {
    const col = UtilMember.COLUMNS.find((c) => c.sort === sortField.value);
    if (!col) return '';
    return t('member.sortBy', { field: t(col.labelKey), dir: t(sortOrder.value === 1 ? 'member.sortAsc' : 'member.sortDesc') });
});

// ── 상세 서랍 — 행을 누르면 열린다. 열린 채 다른 행을 누르면 내용만 바뀐다. 닫히면 눌렀던 행으로 초점이 돌아간다.
const { detail, loading: detailLoading, error: detailError, open: openDetail, retry: retryDetail } = useMemberDetail();
const drawerOpen = ref(false);
const selectedRow = ref<MemberRow | null>(null);
/** 눌렀던 행 요소 — 서랍을 닫을 때 초점을 돌려줄 곳 */
let triggerRow: HTMLElement | null = null;

function onRowClick(e: { originalEvent: Event; data: MemberRow }) {
    const row = (e.originalEvent.target as HTMLElement | null)?.closest('tr');
    if (row) triggerRow = row as HTMLElement;
    selectedRow.value = e.data;
    drawerOpen.value = true;
    void openDetail(e.data.memberNo);
}

watch(drawerOpen, (isOpen) => {
    if (isOpen) return;
    // 서랍이 닫히며 초점이 사라지기 전에 눌렀던 행으로 — 그 사이 표가 다시 그려져 행이 없어졌으면 그대로 둔다
    void nextTick(() => {
        if (triggerRow?.isConnected) triggerRow.focus();
    });
});

/** 조회 카드 안 "적용 중인 조건" 칩 */
const summary = computed(() =>
    UtilMemberMessages.summary(t, appliedFilter.value, appliedRows.value, {
        roleName: (id) => roleOptions.value.find((r) => r.roleId === id)?.roleName ?? id,
        gradeName: (key) => gradeState.badgeOf(key).label,
        date: (ymd) => time.value.serviceDate(ymd),
        number: (n) => n.toLocaleString(locale.value),
    }),
);

onMounted(async () => {
    // 등급 목록(필터 옵션·뱃지) — 못 받아도 목록은 봐야 한다. 못 받으면 중립 뱃지 + 등급 키로 보인다.
    void gradeState.ensureLoaded();
    // 역할 고르개를 못 채워도 목록은 봐야 한다. 조용히 빈 채로 둔다.
    void api
        .loadRoleOptions()
        .then((found) => (roleOptions.value = found))
        .catch(() => undefined);
    await reload();
});

async function reload() {
    busy.value = true;
    loadError.value = null;
    try {
        const found = await api.search({
            ...appliedQuery.value,
            sort: UtilMember.sortParam(sortField.value, sortOrder.value),
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

/** 「검색」·Enter — 지금 조건을 굳히고 첫 쪽부터 다시 본다(거른 결과가 더 적은데 스무 번째 쪽에 머물면 빈 목록을 만난다) */
function search() {
    if (UtilMember.pointReversed(filter)) return;
    appliedQuery.value = UtilMember.toQuery(filter, detailRows.value, values.value.zoneId);
    appliedFilter.value = JSON.parse(JSON.stringify(filter)) as MemberFilter;
    appliedRows.value = [...detailRows.value];
    first.value = 0;
    void reload();
}

/** 「초기화」 — 조건을 모두 비우고 바로 다시 본다. 표에 보일 칸·검색편집 선택은 그대로 둔다 */
function resetAll() {
    Object.assign(filter, UtilMember.emptyFilter());
    search();
}

function onPage(e: { first: number; rows: number }) {
    first.value = e.first;
    size.value = e.rows;
    void reload();
}

/** 정렬을 바꾸면 첫 쪽부터 — 서버가 정렬한다(`lazy`) */
function onSort(e: { sortField?: string | ((item: unknown) => string); sortOrder?: number | null }) {
    sortField.value = typeof e.sortField === 'string' ? e.sortField : undefined;
    sortOrder.value = e.sortOrder ?? undefined;
    first.value = 0;
    void reload();
}

/** 칸마다 최소 너비 — 표가 좁아지면 가로로 스크롤한다 */
function widthOf(key: MemberColumnKey): string {
    const widths: Partial<Record<MemberColumnKey, string>> = { memberNo: '11rem', email: '13rem', nickname: '9rem', roles: '11rem', lastLoginAt: '10rem', boardRole: '8rem' };
    return widths[key] ?? '7rem';
}

function moreRolesText(roles: MemberRoleRef[]): string {
    return t('member.rolesMoreLabel', { names: UtilMember.splitRoles(roles).rest.map((r) => r.roleName).join(', ') });
}
</script>
