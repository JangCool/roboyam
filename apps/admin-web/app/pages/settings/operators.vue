<template>
    <!--
        운영자 관리 — hamonica3 의 `members.vue`(설정 › 애플리케이션)(회원 관리)를 옮긴 것이다. 이 제품에서 회원은 공개 사이트 이용자이고, 관리자 앱에 로그인하는 계정은 운영자다(정책: docs/domain/operator-permissions.md).
        **탭이 목록을 가른다**: 활성 · 신규(승인 대기) · 잠김 · 초대 대기 — 몇 명(건)인지가 탭에 보인다. 우측 상단 「운영자 추가」(계정 생성)·「초대」(초대 링크 메일)는 한 다이얼로그의 두 방식이다.
        신청(승인 대기)은 신규 탭에서 「승인」하거나 「반려」한다(반려는 확인창에서 사유를 선택으로 적는다). 보낸 초대는 「초대 대기」 탭에서 보고 「초대 취소」한다 — 초대 링크의 유효기간은 기본 72시간(가정)이고 기한이 지난 초대는 「기한 지남」으로 남는다.
        표는 화면 폭 전체를 쓰고, 행을 누르면(Enter·Space 도) 상세 서랍(`OperatorDetailDrawer`, PrimeVue Drawer — 열리는 쪽은 화면 설정의 "서랍 위치")이 열린다: ① 기본 정보(이름·이메일·역할 여러 개·계정 기간) ② 계정 상태(승인·잠금 해제·만료 아님·사용).
        이 페이지는 표·탭·검색·선택(서랍 열기·전환·닫기 보호)과 저장 호출을 맡는다. 서랍이 열린 채로 다른 행을 누르면 그 운영자로 바뀌고, 저장하지 않은 변경이 있으면 닫기·전환 전에 확인을 묻는다.
        hamonica3 에서 뺀 것: 국가·전화·회사·설명(운영자 정책에 없는 프로필 칸)과 워크스페이스 참여(이 제품에 워크스페이스가 없다). 역할은 하나가 아니라 여러 개다(합집합).
        본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
    -->
    <div class="flex min-h-0 flex-col gap-4">
        <PageTitle :title="t('operator.title')" :subtitle="t('operator.subtitle')">
            <template v-if="canManage" #actions>
                <Button type="button" :label="t('operator.add')" icon="pi pi-user-plus" size="small" @click="openCreate('CREATE')" />
                <Button type="button" :label="t('operator.invite')" icon="pi pi-envelope" size="small" severity="secondary" variant="outlined" @click="openCreate('INVITE')" />
            </template>
        </PageTitle>

        <!-- 탭 줄 — 숫자를 함께 보인다(없으면 눌러 보기 전에는 몇 명인지 모른다). 의미는 글자로 전한다 -->
        <SelectButton v-model="tab" :options="tabOptions" option-label="label" option-value="value" :allow-empty="false" :aria-label="t('operator.tabsLabel')" class="operator-tabs self-start" @update:model-value="reload">
            <template #option="{ option }">
                <span class="inline-flex items-center gap-1.5">
                    <i :class="option.icon" class="text-xs" aria-hidden="true" />
                    {{ option.label }}
                    <span class="operator-tabs-count text-[11px] tabular-nums">{{ option.count }}</span>
                </span>
            </template>
        </SelectButton>

        <!-- 검색 줄 -->
        <form class="flex flex-wrap items-center gap-2" role="search" @submit.prevent="reload">
            <InputText v-model="keyword" type="search" class="w-60" :placeholder="t('operator.searchPlaceholder')" :aria-label="t('operator.searchPlaceholder')" />
            <Button type="submit" :label="t('common.search')" icon="pi pi-search" size="small" :loading="busy" />
            <Button v-if="keyword" type="button" :label="t('operator.clearSearch')" size="small" severity="secondary" variant="text" @click="clearKeyword" />
        </form>

        <!-- 표는 화면 폭 전체를 쓴다 — 행을 누르면(Enter·Space 도) 상세 서랍이 열린다. 서랍이 열려 있는 동안 그 행이 강조된다. 목록도 `--line` 테두리 카드 안에 둔다 -->
        <div v-if="tab !== 'INVITED'" class="min-w-0 overflow-hidden rounded-xl border border-(--line)">
            <DataTable
                :selection="highlighted"
                :value="rows"
                data-key="operatorId"
                selection-mode="single"
                :meta-key-selection="false"
                :loading="busy"
                :size="prefs.density === 'compact' ? 'small' : undefined"
                scrollable
                scroll-height="calc(100dvh - 22rem)"
                class="operators-table min-w-0"
                @row-click="onRowClick"
            >
                <template #empty>
                    <ErrorState v-if="loadError" :title="t('operator.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="reload" />
                    <div v-else-if="busy" class="h-32" aria-hidden="true" />
                    <!-- 빈 상태를 구분한다 — 검색 때문인지 아직 아무도 없는 건지 -->
                    <EmptyState v-else-if="keyword" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                        <Tag :value="`「${keyword}」`" severity="secondary" class="text-[11px]!" />
                        <Button type="button" :label="t('operator.clearSearch')" size="small" severity="secondary" variant="outlined" @click="clearKeyword" />
                    </EmptyState>
                    <EmptyState v-else :title="emptyTitle" :description="emptyHint" />
                </template>

                <Column :header="t('operator.colName')" style="min-width: 10rem">
                    <template #body="{ data }">
                        <div class="flex min-w-0 flex-wrap items-center gap-1.5">
                            <span class="truncate text-[13px]">{{ data.operatorName }}</span>
                            <Tag v-if="data.isSuper" :value="t('operator.superTag')" icon="pi pi-shield" severity="warn" />
                        </div>
                    </template>
                </Column>

                <Column :header="t('operator.colEmail')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <span class="block truncate text-[13px] text-muted-color" :title="data.email">{{ data.email }}</span>
                    </template>
                </Column>

                <Column :header="t('operator.colRoles')" style="min-width: 12rem">
                    <template #body="{ data }">
                        <span v-if="data.isSuper" class="text-[12px] text-muted-color">{{ t('operator.superAllPermissions') }}</span>
                        <span v-else-if="!data.roles.length" class="text-[12px] text-muted-color">{{ t('operator.noRole') }}</span>
                        <div v-else class="flex flex-wrap items-center gap-1">
                            <Tag v-for="role in UtilOperator.splitRoles(data.roles).shown" :key="role.roleId" :value="role.roleName" severity="secondary" />
                            <Tag
                                v-if="UtilOperator.splitRoles(data.roles).rest.length"
                                v-tooltip.top="UtilOperatorMessages.moreRoles(t, UtilOperator.splitRoles(data.roles).rest.map((r: OperatorRoleRef) => r.roleName))"
                                :value="t('operator.rolesMore', { count: UtilOperator.splitRoles(data.roles).rest.length })"
                                :aria-label="UtilOperatorMessages.moreRoles(t, UtilOperator.splitRoles(data.roles).rest.map((r: OperatorRoleRef) => r.roleName))"
                                severity="secondary"
                            />
                        </div>
                    </template>
                </Column>

                <Column :header="t('operator.colPeriod')" style="min-width: 11rem">
                    <template #body="{ data }">
                        <span class="text-[12px] tabular-nums" :class="data.periodOver ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">
                            {{ UtilOperatorMessages.period(t, time.full, data.accountStartAt, data.accountEndAt) }}
                        </span>
                    </template>
                </Column>

                <Column :header="t('operator.colStatus')" style="min-width: 8rem">
                    <template #body="{ data }">
                        <div class="flex flex-wrap gap-1">
                            <Tag v-for="key in UtilOperator.statusKeys(data)" :key="key" :value="UtilOperatorMessages.statusLabel(t, key)" :icon="UtilOperator.statusIcon(key)" :severity="UtilOperator.statusSeverity(key)" />
                        </div>
                    </template>
                </Column>

                <!-- 신규 탭 — 신청을 바로 승인하거나 반려한다(정책 5번 ②). 승인하면 활성 탭으로 가고, 반려하면 목록에서 사라진다 -->
                <Column v-if="tab === 'PENDING' && canManage" header="" style="width: 11rem">
                    <template #body="{ data }">
                        <div class="flex items-center gap-1">
                            <Button type="button" :label="t('operator.approve')" icon="pi pi-check" size="small" severity="secondary" outlined :aria-label="t('operator.approveLabel', { name: data.operatorName })" :loading="approvingId === data.operatorId" @click.stop="approve(data)" />
                            <Button type="button" :label="t('operator.reject')" icon="pi pi-times" size="small" severity="danger" variant="outlined" :aria-label="t('operator.rejectLabel', { name: data.operatorName })" :disabled="approvingId === data.operatorId" @click.stop="openReject(data)" />
                        </div>
                    </template>
                </Column>
            </DataTable>
        </div>

        <!-- 초대 대기 탭 — 보낸 뒤 아직 수락하지 않은 초대 링크. 기한이 지난 것도 남아 있고(정리하려면 취소) 취소하면 링크를 못 쓴다 -->
        <template v-else>
            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line)">
                <DataTable :value="invitationRows" data-key="invitationId" :loading="busy" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable scroll-height="calc(100dvh - 24rem)" class="min-w-0">
                    <template #empty>
                        <ErrorState v-if="invitationError" :title="t('operator.inviteLoadFailed')" :description="invitationError.message" retry :retrying="busy" @retry="reload" />
                        <div v-else-if="busy" class="h-32" aria-hidden="true" />
                        <EmptyState v-else-if="keyword" icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Tag :value="`「${keyword}」`" severity="secondary" class="text-[11px]!" />
                            <Button type="button" :label="t('operator.clearSearch')" size="small" severity="secondary" variant="outlined" @click="clearKeyword" />
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-envelope" :title="t('operator.emptyInvited')" :description="t('operator.emptyInvitedHint')" />
                    </template>

                    <Column :header="t('operator.colEmail')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <span class="block truncate text-[13px]" :title="data.email">{{ data.email }}</span>
                        </template>
                    </Column>

                    <Column :header="t('operator.colRoles')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <span v-if="!data.roles.length" class="text-[12px] text-muted-color">{{ t('operator.noRole') }}</span>
                            <div v-else class="flex flex-wrap items-center gap-1">
                                <Tag v-for="role in UtilOperator.splitRoles(data.roles).shown" :key="role.roleId" :value="role.roleName" severity="secondary" />
                                <Tag
                                    v-if="UtilOperator.splitRoles(data.roles).rest.length"
                                    v-tooltip.top="UtilOperatorMessages.moreRoles(t, UtilOperator.splitRoles(data.roles).rest.map((r: OperatorRoleRef) => r.roleName))"
                                    :value="t('operator.rolesMore', { count: UtilOperator.splitRoles(data.roles).rest.length })"
                                    :aria-label="UtilOperatorMessages.moreRoles(t, UtilOperator.splitRoles(data.roles).rest.map((r: OperatorRoleRef) => r.roleName))"
                                    severity="secondary"
                                />
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('operator.colInvitedAt')" style="min-width: 10rem">
                        <template #body="{ data }">
                            <span class="text-[12px] tabular-nums text-muted-color">{{ time.full(data.invitedAt) }}</span>
                        </template>
                    </Column>

                    <Column :header="t('operator.colExpiresAt')" style="min-width: 12rem">
                        <template #body="{ data }">
                            <div class="flex flex-wrap items-center gap-1.5">
                                <span class="text-[12px] tabular-nums" :class="data.expired ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ time.full(data.expiresAt) }}</span>
                                <Tag v-if="data.expired" :value="t('operator.inviteExpiredTag')" icon="pi pi-calendar-times" severity="danger" />
                            </div>
                        </template>
                    </Column>

                    <Column v-if="canManage" header="" style="width: 8rem">
                        <template #body="{ data }">
                            <Button type="button" :label="t('operator.cancelInvite')" icon="pi pi-times" size="small" severity="danger" variant="outlined" :aria-label="t('operator.cancelInviteLabel', { email: data.email })" :loading="cancelingId === data.invitationId" @click="cancelInvitation(data)" />
                        </template>
                    </Column>
                </DataTable>
            </div>
            <p class="m-0 text-xs text-muted-color">{{ t('operator.inviteValidNote', { hours: UtilOperator.INVITE_VALID_HOURS }) }}</p>
        </template>

        <!-- 상세 서랍 — 설정의 "서랍 위치"를 따른다. 열려 있는 채로 다른 행을 누르면 그 운영자로 바뀐다 -->
        <OperatorDetailDrawer
            :operator="drawerOperator"
            :visible="drawerOpen"
            :role-options="roleOptions"
            :can-manage="canManage"
            :can-lock="canLock"
            :can-grant="canGrant"
            :info-saving="infoSaving"
            :info-error="infoError"
            :info-field-errors="infoFieldErrors"
            :status-saving="statusSaving"
            :status-error="statusError"
            @update:visible="onDrawerVisible"
            @update:dirty="(v: boolean) => (drawerDirty = v)"
            @save-info="saveInfo"
            @save-status="saveStatus"
            @hide="onDrawerHide"
        />

        <OperatorCreateDialog
            v-model:visible="createOpen"
            :initial-mode="createMode"
            :role-options="roleOptions"
            :can-grant="canGrant"
            :busy="createBusy"
            :error="createError"
            :field-errors="createFieldErrors"
            :failures="createFailures"
            @submit="onCreate"
        />

        <OperatorRejectDialog v-model:visible="rejectOpen" :operator-name="rejectTarget?.operatorName ?? ''" :busy="rejectBusy" :error="rejectError" :field-errors="rejectFieldErrors" @submit="onReject" />
    </div>
</template>

<script setup lang="ts">
import type { OperatorCreateMode, OperatorCreateSubmit } from '~/components/operator/OperatorCreateDialog.types';
import type { OperatorRoleOption } from '~/components/operator/OperatorInfoForm.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { OperatorInvitationView, OperatorRoleRef, OperatorStatusRequest, OperatorTab, OperatorUpdateRequest, OperatorView } from '~/composables/operator/api/useOperatorApi.types';
import type { RoleView } from '~/composables/role/api/useRoleApi.types';
import type { RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';
import UtilOperator from '~/utils/util-operator';
import UtilOperatorMessages from '~/utils/util-operator-messages';
import UtilRole from '~/utils/util-role';

const { t } = useI18n();
useSeoMeta({ title: () => t('operator.title') });

const api = useOperatorApi();
const roleApi = useRoleApi();
const roleTypeApi = useRoleTypeApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { confirm } = useConfirmDialog();
const time = useDisplayTime();
const { isSuper, can } = useAdminPermission();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다 — 템플릿에서 `prefs.density` 로 읽는다
const { prefs } = useLayoutPrefs();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('operator.manage'));
const canLock = computed(() => can('operator.lock'));
const canGrant = computed(() => can('role.grant'));

/** 화면의 탭 — 운영자 목록을 가르는 세 탭(`OperatorTab`)과 대기 중인 초대 */
type ListTab = OperatorTab | 'INVITED';

const tab = ref<ListTab>('ACTIVE');
/** 운영자 목록을 읽을 때 쓰는 탭 — 초대 대기 탭을 보는 동안에는 마지막으로 보던 운영자 탭이다(서랍 저장 뒤 목록을 다시 읽을 때 필요하다) */
const lastOperatorTab = ref<OperatorTab>('ACTIVE');
watch(tab, (value) => {
    if (value !== 'INVITED') lastOperatorTab.value = value;
});
const operatorTab = computed<OperatorTab>(() => (tab.value === 'INVITED' ? lastOperatorTab.value : tab.value));
const keyword = ref('');
const rows = ref<OperatorView[]>([]);
const counts = ref({ active: 0, pending: 0, blocked: 0 });
/** 서랍이 보여 주는 운영자 — 목록에서 고르거나 저장 응답으로 갱신한다. 서랍이 열린 채 목록이 새로고침되면 같은 운영자의 최신 값으로 바뀐다 */
const drawerOperator = ref<OperatorView | null>(null);
const drawerOpen = ref(false);
/** 서랍 안에 저장하지 않은 변경이 있나(서랍이 알려 준다) */
const drawerDirty = ref(false);
/** 서랍을 연 행 — 닫으면 이 행으로 초점을 돌려준다 */
let triggerRow: HTMLElement | null = null;
/** 서랍이 열려 있는 동안 표에서 그 행을 강조한다 */
const highlighted = computed(() => (drawerOpen.value ? (rows.value.find((o) => o.operatorId === drawerOperator.value?.operatorId) ?? null) : null));
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 표 자리에 오류 상태(다시 시도)로 보인다. 저장 실패는 폼 안·토스트다 */
const loadError = ref<ApiError | null>(null);
const approvingId = ref<string | null>(null);

/** 대기 중인 초대 — 탭 숫자를 위해 운영자 목록과 함께 읽는다. 검색어는 이메일 부분 일치로 화면에서 거른다 */
const invitations = ref<OperatorInvitationView[]>([]);
const invitationError = ref<ApiError | null>(null);
const cancelingId = ref<string | null>(null);
const invitationRows = computed(() => {
    const k = keyword.value.trim().toLowerCase();
    return k ? invitations.value.filter((i) => i.email.toLowerCase().includes(k)) : invitations.value;
});

const rejectOpen = ref(false);
const rejectTarget = ref<OperatorView | null>(null);
const rejectBusy = ref(false);
const rejectError = ref('');
const rejectFieldErrors = ref<Record<string, string>>({});

const infoSaving = ref(false);
const infoError = ref('');
const infoFieldErrors = ref<Record<string, string>>({});
const statusSaving = ref(false);
const statusError = ref('');

const createOpen = ref(false);
const createMode = ref<OperatorCreateMode>('CREATE');
const createBusy = ref(false);
const createError = ref('');
const createFieldErrors = ref<Record<string, string>>({});
const createFailures = ref<Record<string, string>>({});

// ⚠ `computed` 다 — 그냥 배열이면 처음 읽은 순간의 언어로 굳는다
const tabOptions = computed(() => [
    { value: 'ACTIVE' as OperatorTab, label: t('operator.tabActive'), icon: 'pi pi-users', count: counts.value.active },
    { value: 'PENDING' as OperatorTab, label: t('operator.tabPending'), icon: 'pi pi-user-plus', count: counts.value.pending },
    { value: 'BLOCKED' as OperatorTab, label: t('operator.tabBlocked'), icon: 'pi pi-lock', count: counts.value.blocked },
    { value: 'INVITED' as ListTab, label: t('operator.tabInvited'), icon: 'pi pi-envelope', count: invitations.value.length },
]);

const emptyTitle = computed(() => ({ ACTIVE: t('operator.emptyActive'), PENDING: t('operator.emptyPending'), BLOCKED: t('operator.emptyBlocked') })[operatorTab.value]);
const emptyHint = computed(() => ({ ACTIVE: t('operator.emptyActiveHint'), PENDING: t('operator.emptyPendingHint'), BLOCKED: t('operator.emptyBlockedHint') })[operatorTab.value]);

/**
 * 역할 고르개의 항목. **권한 상승 방지**: 내가 가진 권한 밖의 권한이 든 역할(과 최고 관리자 아닌 이가 보는 종합 관리 역할)은 비활성이다.
 * 역할 목록은 역할 조회 권한이 있어야 받는다 — 없으면 빈 채로 두고 이미 받은 역할은 목록의 딱지로만 보인다.
 */
const roles = ref<RoleView[]>([]);
const roleTypes = ref<RoleTypeView[]>([]);
/** 고르개에서 역할 유형(탭) 이름별로 묶어 보인다 — 역할 유형 목록을 못 받았으면 묶음 이름이 비어 한 묶음이 된다 */
const roleOptions = computed<OperatorRoleOption[]>(() =>
    roles.value.map((role) => {
        const type = roleTypes.value.find((t0) => t0.roleTypeId === role.roleTypeId);
        return {
            roleId: role.roleId,
            roleName: role.roleName,
            group: type ? UtilRole.typePathLabel(t, type) : '',
            disabled: (UtilRole.isGeneralType(role.roleTypeId) && !isSuper.value) || UtilRole.outsideOf(role, can).length > 0,
        };
    }),
);

onMounted(async () => {
    if (can('role.read')) {
        try {
            // 운영자에게 줄 수 있는 것은 운영자 역할뿐이다 — 회원 역할(등급·회원 유형 등)은 섞이지 않는다
            const [types, list] = await Promise.all([roleTypeApi.load(), roleApi.load()]);
            roleTypes.value = types;
            roles.value = list.filter((role) => role.roleKind === 'OPERATOR');
        } catch {
            // 고르개를 못 채워도 운영자 목록은 봐야 한다. 조용히 빈 채로 둔다.
        }
    }
    await reload();
});

/** 운영자 목록과 대기 중인 초대를 함께 다시 읽는다 — 탭에 보이는 숫자가 어긋나지 않게 */
async function reload() {
    busy.value = true;
    await Promise.all([loadOperators(), loadInvitations()]);
    busy.value = false;
}

async function loadInvitations() {
    invitationError.value = null;
    try {
        invitations.value = await api.invitations();
    } catch (e) {
        invitationError.value = errorOf(e);
        invitations.value = [];
    }
}

async function loadOperators() {
    loadError.value = null;
    try {
        const page = await api.list(operatorTab.value, keyword.value);
        rows.value = page.items;
        counts.value = page.counts;
        // 서랍이 열려 있으면 같은 운영자의 최신 값으로 바꾼다. 이 탭·검색에 없으면(상태가 바뀌어 다른 탭으로 갔다) 서랍의 값을 그대로 둔다
        const fresh = drawerOperator.value ? page.items.find((o) => o.operatorId === drawerOperator.value!.operatorId) : undefined;
        if (fresh) drawerOperator.value = fresh;
    } catch (e) {
        loadError.value = errorOf(e);
        rows.value = [];
    }
}

function clearKeyword() {
    keyword.value = '';
    void reload();
}

/**
 * 저장하지 않은 변경이 있으면 버려도 되는지 묻는다 — 서랍을 닫거나 다른 행으로 바꾸기 전에 부른다. 변경이 없으면 묻지 않고 true.
 * @returns {Promise<boolean>} 계속해도 되면 true(취소하면 서랍은 열린 채 그대로).
 */
async function confirmDiscard(): Promise<boolean> {
    if (!drawerDirty.value) return true;
    return confirm({ title: t('operator.discardTitle'), message: t('operator.discardMessage'), confirmLabel: t('operator.discardConfirm'), cancelLabel: t('operator.discardKeep'), tone: 'danger' });
}

/** 행을 누르면(Enter·Space 포함) 서랍을 연다. 이미 열려 있으면 그 운영자로 바꾼다 */
async function onRowClick(event: { originalEvent: Event; data: OperatorView }) {
    const row = (event.originalEvent.currentTarget as HTMLElement | null) ?? null;
    if (drawerOpen.value && drawerOperator.value?.operatorId === event.data.operatorId) return;
    if (!(await confirmDiscard())) return;
    // 다른 운영자를 보면 앞 운영자의 저장 오류를 지운다
    infoError.value = '';
    infoFieldErrors.value = {};
    statusError.value = '';
    triggerRow = row;
    drawerOperator.value = event.data;
    drawerOpen.value = true;
}

/** 서랍이 닫기를 요청했다(Esc · 바깥 · 닫기 단추) — 변경이 있으면 확인 뒤에 닫는다 */
async function onDrawerVisible(visible: boolean) {
    if (visible) return;
    if (!(await confirmDiscard())) return;
    drawerOpen.value = false;
}

/** 서랍이 닫혔다 — 눌렀던 행으로 초점을 돌려준다(그 사이 표가 다시 그려졌으면 첫 행) */
function onDrawerHide() {
    drawerDirty.value = false;
    const target = triggerRow?.isConnected ? triggerRow : document.querySelector<HTMLElement>('.operators-table tbody tr');
    triggerRow = null;
    void nextTick(() => target?.focus());
}

async function saveInfo(body: OperatorUpdateRequest) {
    if (!drawerOperator.value) return;
    infoSaving.value = true;
    infoError.value = '';
    infoFieldErrors.value = {};
    try {
        // 저장 응답이 최신 값이다 — 서랍을 바로 갱신하고 목록(탭 인원수 포함)도 다시 읽는다
        drawerOperator.value = await api.update(drawerOperator.value.operatorId, body);
        await reload();
        toast.success(t('common.saved'));
    } catch (e) {
        const error = errorOf(e);
        infoFieldErrors.value = error.fields ?? {};
        // 칸에 붙은 오류가 있으면 칸 아래에 보이므로 폼 위쪽에는 칸에 붙지 않는 것만 모은다
        infoError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
    } finally {
        infoSaving.value = false;
    }
}

async function saveStatus(body: OperatorStatusRequest) {
    if (!drawerOperator.value) return;
    statusSaving.value = true;
    statusError.value = '';
    try {
        drawerOperator.value = await api.changeStatus(drawerOperator.value.operatorId, body);
        await reload();
        toast.success(t('operator.statusSaved'));
    } catch (e) {
        statusError.value = errorOf(e).message;
    } finally {
        statusSaving.value = false;
    }
}

/** 신규 탭의 「승인」 — 신청한 운영자를 승인한다. 목록 한 줄의 수정 실패는 토스트다 */
async function approve(operator: OperatorView) {
    approvingId.value = operator.operatorId;
    try {
        await api.changeStatus(operator.operatorId, { approved: true });
        toast.success(t('operator.approved', { name: operator.operatorName }));
        await reload();
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        approvingId.value = null;
    }
}

/** 신규 탭의 「반려」 — 확인창(사유는 선택)을 연다 */
function openReject(operator: OperatorView) {
    rejectTarget.value = operator;
    rejectError.value = '';
    rejectFieldErrors.value = {};
    rejectOpen.value = true;
}

async function onReject(reason: string) {
    const target = rejectTarget.value;
    if (!target) return;
    rejectBusy.value = true;
    rejectError.value = '';
    rejectFieldErrors.value = {};
    try {
        await api.reject(target.operatorId, { reason });
        rejectOpen.value = false;
        toast.success(t('operator.rejected', { name: target.operatorName }));
        await reload();
    } catch (e) {
        const error = errorOf(e);
        rejectFieldErrors.value = error.fields ?? {};
        rejectError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
    } finally {
        rejectBusy.value = false;
    }
}

/** 초대 대기 탭의 「초대 취소」 — 링크를 못 쓰게 한다. 목록 한 줄의 실패는 토스트다 */
async function cancelInvitation(invitation: OperatorInvitationView) {
    const ok = await confirm({ title: t('operator.cancelInviteTitle'), message: t('operator.cancelInviteMessage', { email: invitation.email }), confirmLabel: t('operator.cancelInvite'), cancelLabel: t('operator.cancelInviteKeep'), tone: 'danger' });
    if (!ok) return;
    cancelingId.value = invitation.invitationId;
    try {
        await api.cancelInvitation(invitation.invitationId);
        toast.success(t('operator.inviteCancelled'));
    } catch (e) {
        toast.error(errorOf(e).message, 8000);
    } finally {
        cancelingId.value = null;
        await loadInvitations();
    }
}

function openCreate(mode: OperatorCreateMode) {
    createMode.value = mode;
    createError.value = '';
    createFieldErrors.value = {};
    createFailures.value = {};
    createOpen.value = true;
}

async function onCreate(payload: OperatorCreateSubmit) {
    createBusy.value = true;
    createError.value = '';
    createFieldErrors.value = {};
    try {
        if (payload.mode === 'CREATE') {
            const created = await api.create({ operatorName: payload.operatorName, email: payload.email, roleIds: payload.roleIds });
            createOpen.value = false;
            toast.success(t('operator.created', { name: created.operatorName }));
            tab.value = 'ACTIVE';
            await reload();
            return;
        }

        // 초대는 한 줄씩 따로 보낸다 — 메일은 어차피 한 통씩 나가고, 실패한 줄은 이유를 달고 목록에 남아 고쳐서 다시 보낼 수 있다
        const failures: Record<string, string> = {};
        let sent = 0;
        for (const email of payload.emails) {
            try {
                await api.invite({ email, roleIds: payload.roleIds });
                sent += 1;
            } catch (e) {
                failures[email] = errorOf(e).message;
            }
        }
        createFailures.value = failures;
        if (sent) {
            toast.success(t('operator.inviteSent', { count: sent }));
            await loadInvitations();
        }
        const failed = Object.keys(failures).length;
        if (failed) createError.value = t('operator.inviteSomeFailed', { count: failed });
        else createOpen.value = false;
    } catch (e) {
        const error = errorOf(e);
        createFieldErrors.value = error.fields ?? {};
        createError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
    } finally {
        createBusy.value = false;
    }
}
</script>

<style scoped>
/* 탭 — 고른 탭을 강조색으로 짚는다. 기본(회색 배경)은 옆의 안 고른 탭과 명암 차이가 작아 지금 어느 탭인지 한눈에 안 들어온다. 색만으로 전하지 않도록 아이콘·글자도 함께 있다 */
.operator-tabs :deep(.p-togglebutton-checked) {
    background: var(--p-primary-color);
    color: var(--p-primary-contrast-color);
    border-color: var(--p-primary-color);
}

/* 고른 탭 안의 숫자도 함께 뒤집는다 — 안 그러면 진한 바탕에 진한 글자가 된다 */
.operator-tabs :deep(.p-togglebutton-checked) .operator-tabs-count {
    color: var(--p-primary-contrast-color);
    opacity: 0.85;
}

.operator-tabs :deep(.p-togglebutton-content) {
    background: transparent;
    box-shadow: none;
}
</style>
