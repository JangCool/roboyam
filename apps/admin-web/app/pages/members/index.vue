<template>
    <!--
        회원 관리 — 공개 사이트 회원(이용자)을 조건으로 찾아보는 조회 화면이다. 운영자(관리자 앱 계정)는 여기 나오지 않는다(정책: docs/domain/operator-permissions.md).
        위쪽 조회 조건 카드는 그누보드 회원 관리의 필터 구조를 따른다(`MemberSearchPanel`). 조건은 「검색」(또는 Enter)을 눌렀을 때만 서버로 가고, 쪽·정렬은 마지막으로 검색한 조건으로 다시 묻는다.
        표는 서버가 한 쪽씩 준다(`lazy`). 서버가 조건을 거절하면(400 + `fields`) 해당 칸 아래에 서버 문구가 붙는다(`searchErrors`). 이메일은 서버가 가려서 준다(`member.pii` 가 있을 때만 원문) — 화면은 받은 대로 그린다.
        행을 누르면(또는 Enter·Space) 회원 상세 서랍이 열린다(`MemberDetailDrawer`, 읽기 전용 — 조치는 후속). 행 액션은 없다. 본문 여백은 셸(`.app-content`)이 준다.
        커머스 0-4: 서랍의 「기능 제한」 구획(목록·걸기·해제)과 탈퇴 회원의 분리 보관 연락처(열람)는 이 화면이 상태(`useMemberRestrictions`·`useWithdrawnContact`)와 대화상자를 가진다.
        커머스 1-1: 「등급·포인트」 구획의 포인트 묶음(적립/충전·소멸 예정·미회수·묶음 목록)도 이 화면이 상태(`useMemberPointLots`, `point.read` 있을 때만)를 가진다.
        제재(member-sanction-contract): 「제재」 구획의 현황·이력은 제재 API 현황(`useSanctionPreview`, `sanction.read` 있을 때만 — 회원 상세 API 의 제재 칸은 읽지 않는다),
          [제재하기](`SanctionIssueDialog` — 회원이 정해져 찾기 없음)·진행 중 정지 [해제](사유 1~500자)는 이 화면이 대화상자를 가진다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('member.title')" :subtitle="t('member.subtitle')" />

        <MemberSearchPanel :model-value="filter" v-model:detail-rows="detailRows" :role-options="roleOptions" :busy="busy" :summary="summary" :errors="searchErrors" @search="search" @reset="resetAll" />

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
                        <!-- 서버가 조건을 받아들이지 않았다 — 해당 칸 아래에 서버 문구가 붙어 있다 -->
                        <EmptyState v-else-if="hasSearchErrors" icon="pi pi-exclamation-circle" :title="t('member.searchRejectedTitle')" :description="t('member.searchRejectedHint')" />
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
                            <template v-else-if="col.key === 'businessVerified'">
                                <Tag v-if="data.businessVerified" :value="t('member.businessVerified.YES')" icon="pi pi-verified" severity="info" />
                                <span v-else class="text-[13px] text-muted-color">{{ t('member.businessVerified.NO') }}</span>
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

        <MemberDetailDrawer
            v-model:visible="drawerOpen"
            ignore-selector="tbody tr"
            :member="selectedRow"
            :detail="detail"
            :loading="detailLoading"
            :error="detailError"
            :restrictions="restrictionsView"
            :withdrawn-contact="contactView"
            :point-lots="canReadPoints ? pointLotsView : null"
            :withdrawal="withdrawalView"
            :can-force-withdraw="canForceWithdraw"
            :emoticon-creator="emoticonCreator"
            @retry-withdrawal="withdrawal.retry"
            @force-withdraw="openForce"
            @cancel-withdrawal="openCancel"
            @retry="retryDetail"
            @retry-restrictions="restrictions.retry"
            @restrict="restrictOpen = true"
            @lift="openLift"
            @retry-contact="contact.retry"
            @reveal-contact="revealOpen = true"
            @mask-contact="contact.mask"
            @point-lots-query="pointLots.query"
            @retry-point-lots="pointLots.retry"
            :sanctions="sanctionPerm.canRead.value ? sanctionsView : null"
            @sanction="openSanctionIssue"
            @lift-sanction="openSanctionLift"
            @retry-sanctions="sanctionPreview.retry"
        />

        <!-- 제재하기·제재 해제 — 서랍 위에 뜬다 -->
        <SanctionIssueDialog v-model:visible="sanctionIssue.open.value" :view="sanctionIssue.view.value" :perm="sanctionLiftPerm" @retry-preview="sanctionIssue.loadPreview" @submit="submitSanctionIssue" />
        <MemberReasonDialog
            v-model:visible="sanctionLiftOpen"
            :title="t('sanction.liftTitle')"
            :label="t('sanction.liftReasonLabel')"
            :submit-label="t('sanction.lift')"
            submit-icon="pi pi-lock-open"
            :notice="t('sanction.liftNotice')"
            :min="1"
            :max="UtilSanction.TEXT_MAX"
            :busy="sanctionLiftBusy"
            :error="sanctionLiftError"
            :field-error="sanctionLiftFieldError"
            @submit="submitSanctionLift"
        >
            <p v-if="sanctionLiftTarget" class="m-0 flex flex-wrap items-center gap-1.5 text-sm">
                <b>{{ UtilSanctionMessages.userName(t, sanctionLiftTarget.user) }}</b>
                <SanctionTag type="kind" :kind="sanctionLiftTarget.kind" />
            </p>
        </MemberReasonDialog>

        <!-- 기능 제한 걸기·해제, 탈퇴 회원 연락처 열람(커머스 0-4) — 서랍 위에 뜬다(서랍의 바깥 누름 판정은 대화상자를 무시한다) -->
        <MemberRestrictionDialog
            v-model:visible="restrictOpen"
            :member-name="selectedRow?.nickname ?? ''"
            :items="restrictions.items.value ?? []"
            :busy="restrictBusy"
            :error="restrictError"
            :field-errors="restrictFieldErrors"
            @submit="submitRestriction"
        />
        <MemberReasonDialog
            v-model:visible="liftOpen"
            :title="t('restriction.liftTitle')"
            :label="t('restriction.liftReasonLabel')"
            :submit-label="t('restriction.lift')"
            submit-icon="pi pi-unlock"
            :notice="t('restriction.liftNotice')"
            :busy="liftBusy"
            :error="liftError"
            :field-error="liftFieldError"
            @submit="submitLift"
        >
            <p v-if="liftTarget" class="m-0 text-sm">{{ t('restriction.liftTarget', { type: t(`restriction.type.${liftTarget.type}`), reason: liftTarget.reason }) }}</p>
        </MemberReasonDialog>
        <!-- 강제 탈퇴(운영자, `member.withdraw`) — 남은 일·돈 미리보기를 받아 보이고, 사유 종류·운영 메모를 받는다 -->
        <MemberForceWithdrawDialog
            v-model:visible="forceOpen"
            :member-name="selectedRow?.nickname ?? detail?.nickname ?? ''"
            :member-no="selectedRow?.memberNo ?? detail?.memberNo ?? ''"
            :preview="withdrawal.preview.value"
            :preview-loading="withdrawal.previewLoading.value"
            :preview-error="withdrawal.previewError.value"
            :busy="forceBusy"
            :error="forceError"
            :field-errors="forceFieldErrors"
            @retry-preview="loadForcePreview"
            @submit="submitForce"
        />
        <!-- 운영자 대리 취소(회원 요청 시) — 탈퇴 진행 중 회원의 「탈퇴 신청」 블록 [대리 취소]가 연다 -->
        <MemberWithdrawalCancelDialog
            v-if="withdrawal.record.value"
            v-model:visible="cancelOpen"
            :member-name="selectedRow?.nickname ?? detail?.nickname ?? ''"
            :member-no="withdrawal.record.value.memberNo"
            :prior-status="withdrawal.record.value.priorStatus"
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
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonCreatorSummary } from '~/composables/emoticon/api/useEmoticonAdminApi.types';
import type { MemberQuery, MemberRoleOption, MemberRoleRef, MemberRow } from '~/composables/member/api/useMemberApi.types';
import type { MemberRestriction, MemberRestrictionCreateRequest } from '~/composables/member/api/useMemberRestrictionApi.types';
import type { MemberPointLotsView, MemberRestrictionsView, MemberWithdrawalView, WithdrawnContactView } from '~/components/member/MemberDetailContent.types';
import type { ForceWithdrawRequest, WithdrawalCancelRequest } from '~/composables/member/api/useMemberWithdrawalApi.types';
import UtilMemberWithdrawal from '~/utils/util-member-withdrawal';
import UtilMember, { type MemberColumnKey, type MemberDetailRow, type MemberFilter } from '~/utils/util-member';
import UtilMemberMessages from '~/utils/util-member-messages';
import type { SanctionCreateRequest, SanctionLine, SanctionRunningLine } from '~/composables/sanction/api/useSanctionApi.types';
import type { MemberSanctionsView } from '~/components/member/MemberDetailContent.types';
import UtilSanction from '~/utils/util-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

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
const columns = useStoredList<MemberColumnKey>('irobit:admin:member-columns', UtilMember.defaultColumns, UtilMember.sanitizeColumns);
const detailRows = useStoredList<MemberDetailRow>('irobit:admin:member-detail-rows', UtilMember.defaultDetailRows, UtilMember.sanitizeDetailRows);

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

/** 서버가 거절한 조회 조건 문구(`400 VALIDATION` 등의 `fields` — 조건 이름 → 서버 문구). 검색 패널의 해당 칸 아래에 붙는다. 다시 검색하면 지운다 */
const searchErrors = ref<Record<string, string>>({});
const hasSearchErrors = computed(() => Object.keys(searchErrors.value).length > 0);

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
    // 커머스 0-4 — 기능 제한 목록은 늘, 분리 보관 연락처는 탈퇴 회원만 받는다
    void restrictions.open(e.data.memberNo);
    if (e.data.status === 'WITHDRAWN') void contact.open(e.data.memberNo);
    else contact.clear();
    // 회원 탈퇴 관리 — 탈퇴 진행 중·완료 회원은 「탈퇴 신청」·「탈퇴 완료」 블록에 그 회원의 기록을 보인다
    if (e.data.status === 'WITHDRAWING' || e.data.status === 'WITHDRAWN') void withdrawal.open(e.data.memberNo);
    else withdrawal.clear();
    // 커머스 1-1 — 포인트 묶음(적립/충전·소멸 예정·미회수)은 `point.read` 가 있을 때만(탈퇴 회원은 등급·포인트 구획이 비어 받지 않는다)
    if (canReadPoints.value && e.data.status !== 'WITHDRAWN') pointLots.open(e.data.memberNo);
    else pointLots.clear();
    // 로보티콘 작가 한 줄(실제 API) — `emoticon.read` 가 있을 때만
    void loadEmoticonCreator(e.data.status !== 'WITHDRAWN' ? e.data.memberNo : null);
    // 제재 — 탈퇴(진행 중 포함) 회원은 제재 현황이 없다(서버 404)
    if (sanctionPerm.canRead.value && e.data.status !== 'WITHDRAWN' && e.data.status !== 'WITHDRAWING') void sanctionPreview.open(e.data.memberNo);
    else sanctionPreview.clear();
}

// ── 제재(member-sanction-contract) — 「제재」 구획의 현황·이력과 제재하기·해제 대화상자. 서버 호출은 상태 컴포저블 한 겹
const sanctionPerm = useSanctionPermission();
const sanctionLiftPerm = computed(() => ({ canManage: sanctionPerm.canManage.value, canBan: sanctionPerm.canBan.value }));
const sanctionPreview = useSanctionPreview();
const sanctionsView = computed<MemberSanctionsView>(() => ({ preview: sanctionPreview.preview.value, loading: sanctionPreview.loading.value, error: sanctionPreview.error.value }));
const sanctionIssue = useSanctionIssue();

function openSanctionIssue(): void {
    const row = selectedRow.value ?? detail.value;
    if (row) sanctionIssue.start({ id: row.memberNo, nickname: row.nickname });
}

async function submitSanctionIssue(body: Omit<SanctionCreateRequest, 'userId' | 'reportCaseId'>): Promise<void> {
    const created = await sanctionIssue.submit(body);
    if (!created) return;
    toast.success(UtilSanctionMessages.issued(t, created));
    void sanctionPreview.reload();
}

const sanctionLiftOpen = ref(false);
const sanctionLiftBusy = ref(false);
const sanctionLiftTarget = ref<SanctionRunningLine | null>(null);
const sanctionLiftError = ref('');
const sanctionLiftFieldError = ref('');

function openSanctionLift(line: SanctionRunningLine): void {
    if (!UtilSanction.canLift(line, sanctionLiftPerm.value)) return;
    sanctionLiftTarget.value = line;
    sanctionLiftError.value = '';
    sanctionLiftFieldError.value = '';
    sanctionLiftOpen.value = true;
}

async function submitSanctionLift(reason: string): Promise<void> {
    const target = sanctionLiftTarget.value;
    if (!target || sanctionLiftBusy.value) return;
    sanctionLiftBusy.value = true;
    sanctionLiftError.value = '';
    sanctionLiftFieldError.value = '';
    const failure = await sanctionPreview.lift(target, reason);
    sanctionLiftBusy.value = false;
    if (!failure) {
        sanctionLiftOpen.value = false;
        toast.success(t('sanction.lifted', { name: target.sealed ? (sanctionPreview.preview.value?.user.nickname ?? '') : target.user.nickname }));
        return;
    }
    if (failure.code === 'SANCTION_NOT_LIFTABLE' || failure.code === 'SANCTION_NOT_FOUND') {
        sanctionLiftOpen.value = false;
        toast.error(t('sanction.staleFailed', { reason: failure.message }), 8000);
        return;
    }
    const split = splitError(failure, ['reason']);
    sanctionLiftFieldError.value = split.fields.reason ?? '';
    sanctionLiftError.value = split.form;
}

// ── 포인트 묶음(커머스 1-1) — 「등급·포인트」 구획이 보는 상태. 서버 호출은 상태 컴포저블 한 겹이다
const canReadPoints = computed(() => can('point.read'));
const pointLots = useMemberPointLots();
const pointLotsView = computed<MemberPointLotsView>(() => ({
    data: pointLots.data.value,
    loading: pointLots.loading.value,
    error: pointLots.error.value,
    active: pointLots.active.value,
    page: pointLots.page.value,
    size: pointLots.size,
}));

// ── 로보티콘 작가 한 줄(emoticon-contract — 실제 API `useEmoticonAdminApi.creator`) — 「등급·포인트」 구획. 작가가 아니면 null(줄을 그리지 않는다). 못 받아도 조용히 넘긴다(보조 정보)
const emoticonApi = useEmoticonAdminApi();
const emoticonCreator = ref<EmoticonCreatorSummary | null>(null);
let emoticonTicket = 0;
async function loadEmoticonCreator(memberNo: string | null): Promise<void> {
    const mine = ++emoticonTicket;
    emoticonCreator.value = null;
    if (!memberNo || !can('emoticon.read')) return;
    try {
        const found = await emoticonApi.creator(memberNo);
        if (mine === emoticonTicket) emoticonCreator.value = found;
    } catch {
        // 보조 한 줄이라 오류로 올리지 않는다
    }
}

// ── 기능 제한·탈퇴 회원 연락처(커머스 0-4) — 서랍 구획이 보는 상태와 대화상자. 서버 호출은 상태 컴포저블 한 겹이다
const restrictions = useMemberRestrictions();
const contact = useWithdrawnContact();
const toast = useAppToast();

// ── 회원 탈퇴 관리 — 탈퇴 블록이 보는 그 회원의 기록 + 강제 탈퇴(미리보기·요청)·대리 취소. 서버 호출은 `useMemberWithdrawal` 한 겹이다
const withdrawal = useMemberWithdrawal();
const withdrawalView = computed<MemberWithdrawalView>(() => ({ record: withdrawal.record.value, loading: withdrawal.loading.value, error: withdrawal.error.value }));
/** 강제 탈퇴·대리 취소 권한(`member.withdraw` — SENSITIVE, 서버 `Permission.MEMBER_WITHDRAW`) */
const canForceWithdraw = computed(() => can('member.withdraw'));

const forceOpen = ref(false);
const forceBusy = ref(false);
const forceError = ref('');
const forceFieldErrors = ref<Record<string, string>>({});

function loadForcePreview(): void {
    const row = selectedRow.value ?? detail.value;
    if (row) void withdrawal.loadPreview(row.memberNo);
}

function openForce(): void {
    forceError.value = '';
    forceFieldErrors.value = {};
    loadForcePreview();
    forceOpen.value = true;
}

/** 강제 탈퇴 — 즉시 완료면 목록을 다시 받고(상태가 탈퇴로), 진행 중이면 그 회원의 블록이 「탈퇴 신청」으로 바뀐다 */
async function submitForce(body: ForceWithdrawRequest): Promise<void> {
    if (forceBusy.value) return;
    const name = selectedRow.value?.nickname ?? detail.value?.nickname ?? '';
    forceBusy.value = true;
    forceError.value = '';
    forceFieldErrors.value = {};
    try {
        const result = await withdrawal.force(body);
        forceOpen.value = false;
        if (result.status === 'COMPLETED') toast.success(t('memberWithdrawal.force.resultCompleted', { name }));
        else toast.success(t('memberWithdrawal.force.resultScheduled', { name, count: result.pendingCount }), 8000);
        // 상태가 바뀌었으니(탈퇴·탈퇴 진행 중) 목록·상세를 다시 받는다
        void reload();
        if (selectedRow.value) void openDetail(selectedRow.value.memberNo);
    } catch (e) {
        const failure = errorOf(e);
        const split = splitError(failure, ['reasonKind', 'memo']);
        forceFieldErrors.value = split.fields;
        forceError.value = split.form;
    } finally {
        forceBusy.value = false;
    }
}

// ── 운영자 대리 취소(회원 요청 시) — 진행 중 + 회원 신청 건만. 성공하면 블록이 「취소됨」 안내로 바뀌고 목록·상세를 다시 받는다(상태가 신청 전으로 돌아간다)
const cancelOpen = ref(false);
const cancelBusy = ref(false);
const cancelError = ref('');
const cancelFieldErrors = ref<Record<string, string>>({});

function openCancel(): void {
    const record = withdrawal.record.value;
    if (!record || !UtilMemberWithdrawal.canOperatorCancel(record.status, record.source)) return;
    cancelError.value = '';
    cancelFieldErrors.value = {};
    cancelOpen.value = true;
}

async function submitCancel(body: WithdrawalCancelRequest): Promise<void> {
    if (cancelBusy.value) return;
    const name = selectedRow.value?.nickname ?? detail.value?.nickname ?? '';
    cancelBusy.value = true;
    cancelError.value = '';
    cancelFieldErrors.value = {};
    try {
        await withdrawal.cancel(body);
        cancelOpen.value = false;
        toast.success(t('memberWithdrawal.cancel.done', { name }));
        void reload();
        if (selectedRow.value) void openDetail(selectedRow.value.memberNo);
    } catch (e) {
        const failure = errorOf(e);
        if (failure.status === 409) {
            // 그 사이 끝났다(배치 완료·회원 취소) — 상태 컴포저블이 기록을 새로 받았다. 문구는 서버 것 그대로
            cancelOpen.value = false;
            toast.error(failure.message, 8000);
            void reload();
            return;
        }
        const split = splitError(failure, ['memberConfirmed', 'requestChannel', 'memo']);
        cancelFieldErrors.value = split.fields;
        cancelError.value = split.form;
    } finally {
        cancelBusy.value = false;
    }
}

const restrictionsView = computed<MemberRestrictionsView>(() => ({ items: restrictions.items.value, loading: restrictions.loading.value, error: restrictions.error.value }));
const contactView = computed<WithdrawnContactView>(() => ({
    contact: contact.contact.value,
    loading: contact.loading.value,
    error: contact.error.value,
    revealed: contact.revealed.value,
    remaining: contact.remaining.value,
}));

/** 서버 오류를 칸 문구와 폼 한 줄로 나눈다(`400 VALIDATION` 의 `fields` 는 칸 아래) */
function splitError(error: ApiError, keys: readonly string[]): { fields: Record<string, string>; form: string } {
    const fields: Record<string, string> = {};
    for (const key of keys) {
        const text = error.field(key);
        if (text) fields[key] = text;
    }
    return { fields, form: Object.keys(fields).length ? '' : error.message };
}

const restrictOpen = ref(false);
const restrictBusy = ref(false);
const restrictError = ref('');
const restrictFieldErrors = ref<Record<string, string>>({});
watch(restrictOpen, (open) => {
    if (!open) return;
    restrictError.value = '';
    restrictFieldErrors.value = {};
});

async function submitRestriction(body: MemberRestrictionCreateRequest): Promise<void> {
    if (restrictBusy.value) return;
    restrictBusy.value = true;
    restrictError.value = '';
    const failure = await restrictions.create(body);
    restrictBusy.value = false;
    if (!failure) {
        restrictOpen.value = false;
        toast.success(t('restriction.created', { type: t(`restriction.type.${body.type}`) }));
        return;
    }
    const split = splitError(failure, ['type', 'until', 'reason', 'adminMemo']);
    restrictFieldErrors.value = split.fields;
    restrictError.value = split.form;
}

const liftOpen = ref(false);
const liftBusy = ref(false);
const liftTarget = ref<MemberRestriction | null>(null);
const liftError = ref('');
const liftFieldError = ref('');

function openLift(target: MemberRestriction): void {
    liftTarget.value = target;
    liftError.value = '';
    liftFieldError.value = '';
    liftOpen.value = true;
}

async function submitLift(reason: string): Promise<void> {
    const target = liftTarget.value;
    if (!target || liftBusy.value) return;
    liftBusy.value = true;
    liftError.value = '';
    liftFieldError.value = '';
    const failure = await restrictions.lift(target.id, reason);
    liftBusy.value = false;
    if (!failure) {
        liftOpen.value = false;
        toast.success(t('restriction.lifted', { type: t(`restriction.type.${target.type}`) }));
        return;
    }
    const split = splitError(failure, ['reason']);
    liftFieldError.value = split.fields.reason ?? '';
    liftError.value = split.form;
}

const revealOpen = ref(false);
const revealBusy = ref(false);
const revealError = ref('');
const revealFieldError = ref('');
watch(revealOpen, (open) => {
    if (!open) return;
    revealError.value = '';
    revealFieldError.value = '';
});

/** 연락처 열람 — 이메일은 상태 메모리에만 두고 토스트에 싣지 않는다 */
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
    const split = splitError(failure, ['reason']);
    revealFieldError.value = split.fields.reason ?? '';
    revealError.value = split.form;
}

watch(drawerOpen, (isOpen) => {
    if (isOpen) return;
    // 열람한 탈퇴 회원 연락처는 서랍을 닫으면 바로 가린다(값을 메모리에 남기지 않는다)
    contact.mask();
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

const route = useRoute();

onMounted(async () => {
    // 등급 목록(필터 옵션·뱃지) — 못 받아도 목록은 봐야 한다. 못 받으면 중립 뱃지 + 등급 키로 보인다.
    void gradeState.ensureLoaded();
    // 역할 고르개를 못 채워도 목록은 봐야 한다. 조용히 빈 채로 둔다.
    void api
        .loadRoleOptions()
        .then((found) => (roleOptions.value = found))
        .catch(() => undefined);
    // 다른 화면(탈퇴 관리 서랍의 「회원 관리에서 보기」)이 회원 번호로 열면 그 번호로 바로 검색한다
    const memberNo = typeof route.query.memberNo === 'string' ? route.query.memberNo.trim() : '';
    if (memberNo) {
        filter.searchField = 'MEMBER_NO';
        filter.keyword = memberNo;
        search();
        return;
    }
    await reload();
});

async function reload() {
    busy.value = true;
    loadError.value = null;
    searchErrors.value = {};
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
        const error = errorOf(e);
        rows.value = [];
        total.value = 0;
        // 조건이 틀려 서버가 칸 이름과 함께 거절했으면(400 + fields) 오류 화면이 아니라 그 칸 아래에 서버 문구를 붙인다
        if (error.status === 400 && error.fields && Object.keys(error.fields).length > 0) searchErrors.value = error.fields;
        else loadError.value = error;
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
