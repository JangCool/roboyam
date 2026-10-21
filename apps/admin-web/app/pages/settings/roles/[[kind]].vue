<template>
    <!--
        역할 관리 — hamonica3 의 `roles.vue`(역할·권한 관리)를 옮긴 것이다. **메뉴의 하위 항목이 곧 화면 종류를 정한다**: 주소 `/settings/roles/<종류>` 의 종류(`UtilRole.KINDS`)가
        「운영자 역할」(`operator`) · 「회원 역할」(`member`) · 「등급 역할」(`grade`) 중 어느 탭들을 보일지 정한다. 종류가 없거나 모르는 값은 미들웨어가 `/settings/roles/operator` 로 보낸다(그래서 이 파일은 선택 파라미터 `[[kind]]` 다).
        부모 메뉴 「역할 관리」는 눌러도 이동하지 않고 펼침/접힘만 한다(`util-admin-menu.ts`) — 이 화면은 하위 항목으로 들어온다.
          · 운영자 역할: **영역(대분류) 탭 → 세부 유형(24개)** 두 단계. 영역이 탭이고 그 안의 세부 유형이 왼쪽 목록의 소제목이다. 역할 하나는 세부 유형 하나에 속하고 그 유형의 권한만 고른다.
          · 회원 역할: 회원 유형 · 게시판 단위 · 사용자 정의 3종이 탭이다. 회원 유형·게시판 단위는 제품이 정한 역할만 있다(권한만 고친다). 역할을 새로 만드는 곳은 사용자 정의뿐이다 — 그래서 회원 역할 화면은 어느 탭에서든 목록 머리에 「역할 추가」를 두고, 누르면 사용자 정의 유형으로 추가 다이얼로그를 연다(만든 뒤 사용자 정의 탭으로 옮겨 새 역할을 고른다).
          · 등급 역할: 탭 하나(등급). 등급은 등급 설정에서 파생돼 역할을 추가·삭제·이름 편집할 수 없다(권한만 고친다). 탭이 하나라 탭 줄은 그리지 않는다.
        탭 아래는 지금 탭의 역할만 3열로 보인다 — 왼쪽에서 역할을 고르고, 가운데에서 그 역할이 가진 권한을 보고(고치는 것은 「수정」 다이얼로그), 오른쪽에서 운영자 역할은 "이 역할이 보는 메뉴"를, 회원·등급 역할은 "이 역할이 하는 일"을 본다.
        역할 유형은 고정이다(운영자가 추가·삭제하지 않는다). 권한은 조회 · 처리 · 설정 · 민감 조치(운영자) 또는 이용 · 게시판 운영(회원)으로 묶는다.
        **권한 상승 방지**(운영자 역할만): 지금 로그인한 운영자가 가진 권한만 줄 수 있다. 종합 관리 유형의 역할은 최고 관리자만 만들고 고친다. 감추는 것은 편의이고 막는 것은 서버다.
        선택한 탭은 `?tab=<운영자: 영역 id / 회원·등급: 역할 유형 id / 전체: all>` 로 유지된다(그 종류 안의 탭만). 탭이 둘 이상인 종류(운영자·회원)는 탭 줄 맨 앞에 **「전체」 탭**(`UtilRole.TAB_ALL`)이 있고 모르는 값·없는 값의 기본이다 —
        전체 탭은 그 종류의 모든 역할을 유형별 소제목(운영자: 영역 › 세부 유형)으로 한 목록에 보인다. 전체 탭에서 운영자 역할의 「추가」는 유형을 정하지 않고 다이얼로그에서 영역 → 세부 유형을 고른다. 등급 역할은 탭이 하나라 전체 탭이 없다. 본문 여백은 셸(`.app-content`)이 준다 — hamonica3 의 `p-4 md:p-6` 은 옮기지 않았다.
        **3열(lg 이상)**: 왼쪽 18rem · 가운데 남는 폭 · 오른쪽 20rem 카드가 한 행에서 같은 높이로 서고, 화면 높이(상단 네비·메뉴 줄·본문 여백을 뺀 것)에 맞춰 카드마다 **카드 안에서 스크롤**한다(`.roles-screen`, 아래 style).
        탭 줄·안내 줄은 그 높이 안의 위쪽 줄이라 3열이 남는 높이를 쓴다. 좁은 화면(lg 미만)은 왼쪽 → 가운데 → 오른쪽 순서로 세로로 쌓이고 페이지가 스크롤된다.
        구역 내비(`AppGadget` 의 `anchor`)는 달지 않았다: 가운데 열은 값을 고치고 저장하는 구획이 아니라(고치는 곳은 다이얼로그) 읽는 화면이다.
    -->
    <div class="roles-screen flex flex-col gap-4">
        <PageTitle :title="t(`role.kindTitle.${kind}`)" :subtitle="t(`role.kindSubtitle.${kind}`)" />

        <ErrorState v-if="loadError" :title="t('role.loadFailed')" :description="loadError.message" retry :retrying="busy" @retry="load" />

        <template v-else>
            <!-- 탭 줄 — 이 종류의 탭(운영자: 영역, 회원: 유형). 라벨 = 이름 + 역할 수. 탭이 하나뿐인 종류(등급)는 줄을 그리지 않는다 -->
            <div v-if="busy && !kindTabs.length" class="flex gap-2" aria-hidden="true">
                <Skeleton v-for="n in 4" :key="n" width="7rem" height="2.25rem" />
            </div>
            <Tabs v-else-if="kindTabs.length > 1" :value="activeTabId" scrollable class="roles-tabs min-w-0" @update:value="(v: string | number) => selectTab(String(v))">
                <TabList :aria-label="t('role.tabsLabel')">
                    <Tab v-for="tab in tabs" :key="tab.id" :value="tab.id">
                        <span class="inline-flex items-center gap-1.5">
                            {{ UtilRole.tabLabel(t, tab) }}
                            <span class="text-[11px] font-normal tabular-nums text-muted-color">{{ countOfTab(tab) }}</span>
                        </span>
                    </Tab>
                </TabList>
            </Tabs>

            <!-- 지금 탭 안내 줄 — 설명 -->
            <p v-if="activeTab" class="m-0 min-w-0 text-xs text-muted-color">{{ tabNote }}</p>

            <div v-if="activeTab" role="tabpanel" :aria-label="UtilRole.tabLabel(t, activeTab)" class="grid min-h-0 flex-1 grid-cols-1 gap-4 lg:grid-cols-[18rem_minmax(0,1fr)_20rem] lg:grid-rows-[minmax(0,1fr)]">
                <!-- 왼쪽 — 이 탭의 역할 목록(운영자 영역은 세부 유형별 소제목) -->
                <AppGadget :title="t('role.listTitle')" fill>
                    <!-- 회원 역할 화면은 어느 탭에서든 「역할 추가」가 보인다 — 새로 만들 수 있는 것은 사용자 정의 유형뿐이라 그 유형으로 다이얼로그를 연다. 그 밖의 화면은 탭의 유형에 만들 수 있을 때만 -->
                    <template v-if="canAddMemberRole || canAdd" #actions>
                        <Button
                            v-if="canAddMemberRole"
                            type="button"
                            :label="t('role.addRole')"
                            icon="pi pi-plus"
                            size="small"
                            v-tooltip.top="t('role.addMemberTip')"
                            @click="openCreate(UtilRole.TYPE_CUSTOM)"
                        />
                        <Button v-else type="button" :label="t('common.add')" size="small" variant="text" @click="openCreate()" />
                    </template>

                    <div v-if="busy && !roles.length" class="flex flex-col gap-2" aria-hidden="true">
                        <Skeleton v-for="n in 5" :key="n" height="2.5rem" />
                    </div>

                    <EmptyState v-else-if="!tabRoles.length" icon="pi pi-id-card" :title="t('role.typeEmpty')" :description="emptyHint">
                        <Button v-if="canAdd" type="button" :label="t('role.addRole')" icon="pi pi-plus" size="small" @click="openCreate(canAddMemberRole ? UtilRole.TYPE_CUSTOM : '')" />
                    </EmptyState>

                    <nav v-else :aria-label="t('role.listTitle')" class="flex flex-col gap-3">
                        <section v-for="group in listSections" :key="group.id" :aria-labelledby="showHeadings ? `${idBase}-${group.id}` : undefined">
                            <div v-if="showHeadings" class="mb-1 flex items-center gap-1 px-1">
                                <h3 :id="`${idBase}-${group.id}`" class="m-0 min-w-0 flex-1 truncate text-[11px] font-normal text-muted-color">
                                    {{ group.label }} <span class="tabular-nums">{{ group.roles.length }}</span>
                                </h3>
                                <Button v-if="canAddTo(group.id)" type="button" icon="pi pi-plus" size="small" severity="secondary" variant="text" rounded class="size-6! shrink-0" :aria-label="t('role.sectionAdd', { type: group.label })" v-tooltip.top="t('role.sectionAdd', { type: group.label })" @click="openCreate(group.id)" />
                            </div>
                            <p v-if="showHeadings && !group.roles.length" class="m-0 px-2.5 py-1 text-[11px] text-muted-color">{{ t('role.sectionEmpty') }}</p>
                            <ul v-else class="m-0 flex list-none flex-col gap-0.5 p-0">
                                <li v-for="role in group.roles" :key="role.roleId">
                                    <button
                                        type="button"
                                        class="flex w-full cursor-pointer items-center gap-2 rounded-lg border-0 bg-transparent px-2.5 py-2 text-left focus-visible:outline-2 focus-visible:outline-primary"
                                        :class="role.roleId === selectedId ? 'bg-primary-50! dark:bg-primary-950!' : 'hover:bg-(--p-content-hover-background)'"
                                        :aria-current="role.roleId === selectedId ? 'true' : undefined"
                                        @click="select(role.roleId)"
                                    >
                                        <span class="min-w-0 flex-1">
                                            <span class="block truncate text-[13px]" :class="role.roleId === selectedId ? 'text-primary-700 dark:text-primary-300' : ''">{{ role.roleName }}</span>
                                            <span class="block truncate text-[11px] text-muted-color">{{ role.roleId }}</span>
                                        </span>
                                        <!-- 제품이 넣은 역할은 지울 수 없다 — 그 사실을 미리 보여 준다 -->
                                        <span v-if="role.builtin" v-tooltip.top="t('role.builtIn')" class="inline-flex shrink-0" role="img" :aria-label="t('role.builtIn')">
                                            <i class="pi pi-lock text-[11px] text-muted-color" aria-hidden="true" />
                                        </span>
                                        <span class="shrink-0 text-[11px] tabular-nums text-muted-color" :aria-label="t('role.permCountLabel', { count: role.permissionCodes.length })">{{ role.permissionCodes.length }}</span>
                                    </button>
                                </li>
                            </ul>
                        </section>
                    </nav>

                    <!-- 회원 역할 중 역할을 새로 만들 수 없는 탭 — 등급은 등급 설정에서 만들고, 회원 유형·게시판 단위는 제품이 정한 역할만 있다 -->
                    <p v-if="fixedMemberType" class="mt-3 rounded-lg border border-(--line) p-2.5 text-[11px] leading-relaxed text-muted-color">
                        {{ fixedMemberType.derived ? t('role.derivedNote') : t('role.fixedNote') }}
                        <NuxtLink v-if="fixedMemberType.derived && can('grade.read')" to="/grades" class="mt-1 block text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('role.derivedLink') }}</NuxtLink>
                    </p>
                </AppGadget>

                <!-- 가운데 — 고른 역할 -->
                <AppGadget :title="selected ? selected.roleName : t('role.detailTitle')" fill>
                    <template v-if="selected" #title-extra>
                        <Tag v-if="selectedAreaName" :value="selectedAreaName" severity="secondary" class="role-tag text-[11px]!" />
                        <Tag :value="selectedTypeName" severity="secondary" class="role-tag text-[11px]!" />
                        <!-- 등급 역할은 등급 설정에서 정한 뱃지를 그대로 보인다(미리보기와 같은 `GradeBadge`) -->
                        <GradeBadge v-if="selectedGradeKey" v-bind="gradeState.badgeOf(selectedGradeKey)" size="small" class="role-grade-badge" />
                    </template>
                    <template v-if="selected" #actions>
                        <!-- 비활성 단추는 마우스 이벤트가 없어 툴팁을 감싼 칸에 단다. 이유는 접근성을 위해 글자(aria-description)로도 준다 -->
                        <span v-if="canManage" v-tooltip.top="editBlockedReason || undefined" class="inline-flex">
                            <Button type="button" :label="t('common.edit')" size="small" severity="secondary" variant="outlined" :disabled="!!editBlockedReason" :aria-description="editBlockedReason || undefined" @click="openEdit" />
                        </span>
                        <span v-if="canManage" v-tooltip.top="deleteBlockedReason || undefined" class="inline-flex">
                            <Button type="button" :label="t('common.delete')" size="small" severity="danger" variant="outlined" :disabled="!!deleteBlockedReason" :aria-description="deleteBlockedReason || undefined" :loading="deleting" @click="confirmDelete" />
                        </span>
                    </template>

                    <EmptyState v-if="!selected" icon="pi pi-id-card" :title="t('role.pick')" :description="t('role.pickHint')" />

                    <div v-else class="flex flex-col gap-4">
                        <div>
                            <p class="text-[13px] text-muted-color">{{ selected.description || t('role.noDescription') }}</p>
                            <p class="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1 text-[11px] text-muted-color">
                                <span>{{ selectedTypePath }}</span>
                                <code>{{ selected.roleId }}</code>
                                <Tag v-if="selected.builtin" :value="t('role.builtInTag')" icon="pi pi-lock" severity="secondary" class="role-tag text-[10px]!" />
                                <span v-if="selected.roleKind === 'OPERATOR'">{{ t('role.operatorCount', { count: selected.operatorCount }) }}</span>
                                <span v-else>{{ t('role.memberCount', { count: selected.memberCount.toLocaleString() }) }}</span>
                            </p>
                            <!-- 회원 역할의 부여 방식과 범위 -->
                            <dl v-if="selected.roleKind === 'MEMBER'" class="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs">
                                <dt class="text-muted-color">{{ t('role.metaGrantMode') }}</dt>
                                <dd class="m-0">{{ t(`role.grantMode.${selected.grantMode}`) }}<span class="ml-1 text-muted-color">· {{ t(`role.grantModeHint.${selected.grantMode}`) }}</span></dd>
                                <dt class="text-muted-color">{{ t('role.metaScope') }}</dt>
                                <dd class="m-0">{{ t(`role.scope.${selected.scope}`) }}</dd>
                            </dl>
                        </div>

                        <!-- 등급 역할의 승급 조건 — 읽기 전용 요약. 조건은 「등급 설정」에서 바꾼다(등급 역할은 "무엇을 할 수 있나", 조건은 "어떻게 오르나") -->
                        <div v-if="selectedGradeKey" class="rounded-lg border border-(--line) p-3">
                            <div class="mb-1.5 flex items-center gap-2">
                                <div class="text-xs font-semibold">{{ t('role.promoTitle') }}</div>
                                <NuxtLink v-if="can('grade.read')" to="/grades" class="ml-auto text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('role.promoLink') }}</NuxtLink>
                            </div>
                            <p v-if="!can('grade.read')" class="text-xs text-muted-color">{{ t('role.promoNoPermission') }}</p>
                            <p v-else-if="gradeState.error.value" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ t('role.promoLoadFailed') }} — {{ gradeState.error.value.message }}</p>
                            <p v-else-if="selectedGradeRule" class="text-sm tabular-nums">{{ UtilGradeMessages.condition(t, selectedGradeRule) }}</p>
                            <Skeleton v-else height="1.25rem" width="14rem" />
                            <p class="mt-1.5 text-[11px] leading-relaxed text-muted-color">{{ t('role.promoNote') }}</p>
                        </div>

                        <Message v-if="selectedIsGeneral" severity="info" size="small" variant="simple" :closable="false">
                            {{ isSuper ? t('role.generalNoteSuper') : t('role.generalNote') }}
                        </Message>

                        <!-- 게시판 단위 역할(매니저·스텝)의 기본 한도 — 읽기 전용 안내. 편집은 게시판 기능을 만들 때 정한다(미확정) -->
                        <div v-if="selected.scope === 'BOARD'" class="rounded-lg border border-(--line) p-3">
                            <div class="mb-1.5 text-xs font-semibold">{{ t('role.boardLimitsTitle') }}</div>
                            <ul class="m-0 flex list-none flex-col gap-0.5 p-0 text-xs">
                                <li>{{ t('role.boardLimitPostActions', { count: UtilRole.BOARD_LIMITS.postActionsPerDay }) }}</li>
                                <li>{{ t('role.boardLimitCommentDeletes', { count: UtilRole.BOARD_LIMITS.commentDeletesPerDay }) }}</li>
                                <li>{{ t('role.boardLimitSanctions', { count: UtilRole.BOARD_LIMITS.sanctionsPerDay }) }}</li>
                                <li>{{ t('role.boardLimitHeadcount', { managers: UtilRole.BOARD_LIMITS.managersPerBoard, staff: UtilRole.BOARD_LIMITS.staffPerBoard }) }}</li>
                            </ul>
                            <p class="mt-1.5 text-[11px] leading-relaxed text-muted-color">{{ t('role.boardLimitsNote') }}</p>
                        </div>

                        <div class="border-t border-(--line) pt-4">
                            <div class="mb-3 text-xs text-muted-color">
                                {{ t('role.granted') }}
                                <span class="ml-1 tabular-nums">{{ t('role.grantedCount', { count: selected.permissionCodes.length }) }}</span>
                            </div>
                            <RolePermissionList v-if="selected.permissionCodes.length" :codes="selected.permissionCodes" :kind="selected.roleKind" :show-type="selectedIsGeneral" />
                            <EmptyState v-else icon="pi pi-key" :title="t('role.grantedNone')" :description="canManage ? t('role.grantedNoneHint') : ''" />
                        </div>
                    </div>
                </AppGadget>

                <!-- 오른쪽 — 운영자 역할은 "이 역할이 보는 메뉴"(읽기 전용 — 메뉴는 이 화면에서 고치지 않는다), 회원 역할은 관리자 앱 메뉴와 무관해서 "이 역할이 하는 일"(회원 권한 요약) -->
                <AppGadget :title="selected?.roleKind === 'MEMBER' ? t('role.memberDoesTitle') : t('role.menuTitle')" fill>
                    <EmptyState v-if="!selected" icon="pi pi-eye" :title="t('role.pick')" :description="t('role.pickForMenus')" />
                    <template v-else-if="selected.roleKind === 'MEMBER'">
                        <p class="mb-3 text-[11px] leading-relaxed text-muted-color">{{ t('role.memberNoMenu') }}</p>
                        <RolePermissionList v-if="selected.permissionCodes.length" :codes="selected.permissionCodes" kind="MEMBER" compact />
                        <EmptyState v-else icon="pi pi-key" :title="t('role.grantedNone')" />
                    </template>
                    <template v-else>
                        <p class="mb-3 text-[11px] leading-relaxed text-muted-color">
                            {{ t('role.menuBasis') }}
                            <span class="mt-1 block"><i class="pi pi-key text-[10px] text-primary" aria-hidden="true" /> {{ t('role.menuKeyNote') }}</span>
                        </p>
                        <RoleMenuPreview :permissions="selected.permissionCodes" />
                    </template>
                </AppGadget>
            </div>
        </template>

        <RoleEditDialog
            v-if="activeTab"
            v-model:visible="dialogOpen"
            :axis="activeTab.axis"
            :role-types="kindTypes"
            :initial-type-id="dialogTypeId"
            :initial-area-id="activeTab.areaId ?? ''"
            :role="editTarget"
            :busy="submitting"
            :error="dialogError"
            :field-errors="dialogFieldErrors"
            @submit="onSubmit"
        />
    </div>
</template>

<script setup lang="ts">
import type { RoleEditSubmit } from '~/components/role/RoleEditDialog.types';
import type { ApiError } from '~/composables/common/useApiClient';
import type { RoleView } from '~/composables/role/api/useRoleApi.types';
import type { RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';
import UtilRole, { type RoleScreenKind, type RoleTabDef } from '~/utils/util-role';
import UtilGrade from '~/utils/util-grade';
import UtilGradeMessages from '~/utils/util-grade-messages';

// 종류가 없거나 모르는 값이면 운영자 역할로 보낸다(메뉴의 부모 항목 `/settings/roles` 도 여기로 온다). 같은 화면이라 종류를 바꿔도 목록을 다시 받지 않게 키를 고정한다
definePageMeta({
    key: 'settings-roles',
    middleware: (to) => {
        if (!UtilRole.kindOf(to.params.kind)) return navigateTo({ path: `/settings/roles/${UtilRole.DEFAULT_KIND}`, query: to.query }, { replace: true });
    },
});

const { t } = useI18n();

const api = useRoleApi();
const typeApi = useRoleTypeApi();
const toast = useAppToast();
const errorOf = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { isSuper, can } = useAdminPermission();
const route = useRoute();
const router = useRouter();
const idBase = useId();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('role.manage'));

const roleTypes = ref<RoleTypeView[]>([]);
const roles = ref<RoleView[]>([]);
const selectedId = ref<string | null>(null);
const busy = ref(false);
/** 목록을 받지 못했을 때의 오류 — 화면 전체가 오류 상태(다시 시도)다. 저장·삭제 실패는 다이얼로그·토스트다 */
const loadError = ref<ApiError | null>(null);

const dialogOpen = ref(false);
const dialogTypeId = ref('');
const editTarget = ref<RoleView | null>(null);
const submitting = ref(false);
const dialogError = ref('');
const dialogFieldErrors = ref<Record<string, string>>({});
const deleting = ref(false);

// ── 그룹 · 탭 ─

/** 화면 종류 — 주소 `/settings/roles/<종류>` 가 정한다(없거나 모르는 값은 미들웨어가 운영자로 보낸 뒤라 여기서는 기본값이 쓰일 일이 없다) */
const kind = computed<RoleScreenKind>(() => UtilRole.kindOf(route.params.kind) ?? UtilRole.DEFAULT_KIND);
useSeoMeta({ title: () => t(`role.kindTitle.${kind.value}`) });

/** 역할 유형들을 화면의 탭으로 — 운영자 역할은 영역별, 회원·등급 역할은 유형별. 그중 이 종류의 탭만 보인다 */
const kindTabs = computed(() => UtilRole.tabsOfKind(UtilRole.tabsOf(roleTypes.value), kind.value));

/** 「전체」 탭 — 그 종류의 모든 역할(탭이 둘 이상인 종류만. 등급 역할은 탭이 하나라 없다) */
const allTab = computed(() => UtilRole.allTabOf(kindTabs.value));
/** 탭 줄에 그리는 탭들 — 전체 탭이 맨 앞 */
const tabs = computed<RoleTabDef[]>(() => (allTab.value ? [allTab.value, ...kindTabs.value] : kindTabs.value));

/** 지금 탭 — 주소의 `?tab=` 이 정한다(이 종류 안의 탭만). 모르는 값(없거나 다른 종류의 탭)은 「전체」(전체 탭이 없는 종류는 첫 탭) */
const activeTabId = computed(() => {
    const q = route.query.tab;
    const wanted = typeof q === 'string' ? q : '';
    return tabs.value.some((tab) => tab.id === wanted) ? wanted : (tabs.value[0]?.id ?? '');
});
const activeTab = computed<RoleTabDef | null>(() => tabs.value.find((tab) => tab.id === activeTabId.value) ?? null);
const isAllTab = computed(() => activeTabId.value === UtilRole.TAB_ALL);
/** 이 종류의 역할 유형 전부(역할 추가 다이얼로그가 고른다) — 전체 탭은 같은 유형을 모은 것이라 실제 탭(`kindTabs`)만 센다 */
const kindTypes = computed(() => kindTabs.value.flatMap((tab) => tab.types));

function countOfTab(tab: RoleTabDef): number {
    const ids = new Set(tab.types.map((type) => type.roleTypeId));
    return roles.value.filter((role) => ids.has(role.roleTypeId)).length;
}

/** 탭을 누르면 주소에 반영한다(뒤로 가기 목록을 쌓지 않는다). 다른 쿼리(`mock` 등)는 그대로 둔다 */
function selectTab(tabId: string) {
    if (tabId === activeTabId.value) return;
    void router.replace({ query: { ...route.query, tab: tabId } });
}

/** 안내 줄 — 운영자 탭은 영역·세부 유형 설명, 회원 탭은 그 유형의 설명, 회원 전체 탭은 묶음 안내 */
const tabNote = computed(() => {
    const tab = activeTab.value;
    if (!tab) return '';
    if (tab.axis === 'OPERATOR') return t('role.operatorTabNote');
    if (isAllTab.value) return t('role.memberAllTabNote');
    return tab.types[0]?.description || t('role.typeNoDescription');
});

/** 역할을 새로 만들 수 없는 회원 탭의 유형(등급·회원 유형·게시판 단위) — 목록 아래에 이유를 적는다 */
const fixedMemberType = computed(() => {
    const type = activeTab.value?.axis === 'MEMBER' && !isAllTab.value ? activeTab.value.types[0] : undefined;
    return type && !type.creatable ? type : null;
});

// ── 목록 ─

const tabRoles = computed(() => {
    const ids = new Set((activeTab.value?.types ?? []).map((type) => type.roleTypeId));
    return roles.value.filter((role) => ids.has(role.roleTypeId));
});

/** 왼쪽 목록의 소그룹 — 탭에 든 역할 유형마다 하나(운영자 영역은 세부 유형들, 그 밖은 하나). 전체 탭은 소제목에 영역 › 세부 유형(회원은 유형 이름)을 쓴다. ⚠ `computed` — 소제목 글자가 언어를 따라가게 */
const listSections = computed<{ id: string; label: string; roles: RoleView[] }[]>(() =>
    (activeTab.value?.types ?? []).map((type) => ({
        id: type.roleTypeId,
        label: isAllTab.value ? UtilRole.typePathLabel(t, type) : UtilRole.typeLabel(t, type),
        roles: sortedForType(
            type.roleTypeId,
            tabRoles.value.filter((role) => role.roleTypeId === type.roleTypeId),
        ),
    })),
);

/**
 * 한 유형 안의 표시 차례 — 등급 역할은 서버 목록의 차례(만든 순·아이디 순)가 서열과 다를 수 있어 등급 서열(`level`)대로 늘어놓는다. 등급 목록(`useGradeAppearance`)을 아직 못 받았거나
 * 목록에 없는 등급 역할은 뒤에 두고, 그 밖의 유형은 서버 차례 그대로다.
 */
function sortedForType(roleTypeId: string, list: RoleView[]): RoleView[] {
    if (roleTypeId !== UtilRole.TYPE_GRADE) return list;
    const levelOf = (role: RoleView): number => {
        const key = UtilGrade.gradeKeyOfRole(role.roleId, role.roleTypeId);
        return (key ? gradeState.gradeOf(key)?.level : undefined) ?? Number.MAX_SAFE_INTEGER;
    };
    return [...list].sort((a, b) => levelOf(a) - levelOf(b));
}
/** 세부 유형이 둘 이상인 운영자 영역과 전체 탭만 소제목을 보인다(유형이 하나뿐이면 탭 이름과 같아서 반복이다) */
const showHeadings = computed(() => (isAllTab.value || activeTab.value?.axis === 'OPERATOR') && (activeTab.value?.types.length ?? 0) > 1);

const emptyHint = computed(() => {
    const type = fixedMemberType.value;
    if (type) return type.derived ? t('role.derivedNote') : t('role.fixedNote');
    return canAdd.value ? t('role.typeEmptyHint') : '';
});

// 탭이 바뀌면 그 탭의 첫 역할을 고른다(이미 고른 역할이 그 탭에 있으면 그대로 — 새로 만든 역할의 탭으로 넘어갈 때). 화면 종류가 바뀌어도 기본 탭(전체)의 id 는 같아서 종류도 함께 본다
watch([activeTabId, kind], () => {
    if (!tabRoles.value.some((r) => r.roleId === selectedId.value)) selectFirst();
});

function selectFirst() {
    selectedId.value = listSections.value.flatMap((section) => section.roles)[0]?.roleId ?? null;
}

const selected = computed(() => tabRoles.value.find((r) => r.roleId === selectedId.value) ?? null);

// ── 고른 역할 ─

const selectedType = computed(() => roleTypes.value.find((type) => type.roleTypeId === selected.value?.roleTypeId) ?? null);
const selectedTypeName = computed(() => (selectedType.value ? UtilRole.typeLabel(t, selectedType.value) : ''));
const selectedAreaName = computed(() => (selectedType.value?.area ? t(`permission.area.${selectedType.value.area}`) : ''));
/** 영역 › 세부 유형(운영자) 또는 유형(회원) */
const selectedTypePath = computed(() => (selectedType.value ? UtilRole.typePathLabel(t, selectedType.value) : ''));
const selectedIsGeneral = computed(() => selected.value?.roleKind === 'OPERATOR' && UtilRole.isGeneralType(selected.value.roleTypeId));

/** 종합 관리 유형의 운영자 역할은 최고 관리자만 고치고 지운다(권한 상승 방지) */
const generalLocked = computed(() => selectedIsGeneral.value && !isSuper.value);
const editBlockedReason = computed(() => (generalLocked.value ? t('role.generalSuperOnly') : ''));
const deleteBlockedReason = computed(() => {
    if (generalLocked.value) return t('role.generalSuperOnly');
    return selected.value?.builtin ? t('role.deleteBuiltinTip') : '';
});

/** 이 탭에 역할을 새로 만들 수 있나 — 만들 수 있는 유형이 있고, 종합 관리 영역은 최고 관리자만 */
const canAdd = computed(() => canManage.value && (activeTab.value?.types ?? []).some((type) => canAddTo(type.roleTypeId)));

/** 회원 역할 화면에서 「역할 추가」를 보일까 — 탭과 무관하게 사용자 정의 유형 역할을 만든다(`role.manage` 가 있고 사용자 정의 유형이 있을 때) */
const canAddMemberRole = computed(() => kind.value === 'member' && canAddTo(UtilRole.TYPE_CUSTOM));

/** 이 유형에 역할을 새로 만들 수 있나 */
function canAddTo(roleTypeId: string): boolean {
    const type = roleTypes.value.find((item) => item.roleTypeId === roleTypeId);
    if (!canManage.value || !type?.creatable) return false;
    return !UtilRole.isGeneralType(roleTypeId) || isSuper.value;
}

onMounted(load);

// 등급 역할 — 등급 역할은 등급 설정의 등급 목록에서 자동으로 만들어진다(이름·승급 조건은 읽기 전용, 권한만 편집). 등급 설정과 같은 목록(`useGradeAppearance`)을 읽어 승급 조건 요약과 뱃지를 보인다.
// 등급 역할을 처음 고를 때 한 번 받고(이미 받았으면 다시 묻지 않는다), 등급 설정에서 저장하면 그 상태가 갱신돼 SPA 이동 뒤 바로 반영된다. 승급 조건 요약은 `grade.read` 가 있을 때만 보인다.
const gradeState = useGradeAppearance();
const selectedGradeKey = computed(() => (selected.value ? UtilGrade.gradeKeyOfRole(selected.value.roleId, selected.value.roleTypeId) : null));
const selectedGradeRule = computed(() => (selectedGradeKey.value ? gradeState.gradeOf(selectedGradeKey.value) : undefined));

watch(
    selectedGradeKey,
    (key) => {
        if (key) void gradeState.ensureLoaded();
    },
    { immediate: true },
);

/** 유형과 역할을 함께 읽는다 — 둘 다 와야 탭을 그릴 수 있다 */
async function load() {
    busy.value = true;
    loadError.value = null;
    try {
        // 등급 목록은 등급 역할을 서열대로 늘어놓는 데 쓴다 — 못 받아도 던지지 않는다(그때는 서버 차례)
        const [types, list] = await Promise.all([typeApi.load(), api.load(), gradeState.ensureLoaded()]);
        roleTypes.value = types;
        roles.value = list;
        // 고른 역할이 지금 탭에 없으면(없어졌거나 아직 고른 게 없으면) 첫 역할을 고른다
        if (!tabRoles.value.some((r) => r.roleId === selectedId.value)) selectFirst();
    } catch (e) {
        loadError.value = errorOf(e);
        roleTypes.value = [];
        roles.value = [];
        selectedId.value = null;
    } finally {
        busy.value = false;
    }
}

function select(roleId: string) {
    selectedId.value = roleId;
}

// ── 역할 추가·수정·삭제 ─

/** @param {string} [roleTypeId] 미리 고를 역할 유형(소제목의 「+」에서 온다) */
function openCreate(roleTypeId = '') {
    editTarget.value = null;
    dialogTypeId.value = roleTypeId;
    dialogError.value = '';
    dialogFieldErrors.value = {};
    dialogOpen.value = true;
}

function openEdit() {
    if (!selected.value) return;
    editTarget.value = selected.value;
    dialogTypeId.value = selected.value.roleTypeId;
    dialogError.value = '';
    dialogFieldErrors.value = {};
    dialogOpen.value = true;
}

/** 방금 만든 역할을 고른다 — 다른 탭(회원 역할 화면에서 사용자 정의 밖의 탭에서 추가했거나, 다이얼로그에서 다른 영역을 골랐을 때)에서 만들었으면 만든 역할이 있는 탭으로 옮겨 간다. 목록을 다시 받은 뒤에 불러야 탭이 바뀔 때 선택이 풀리지 않는다 */
function revealCreated(created: RoleView) {
    selectedId.value = created.roleId;
    if (isAllTab.value) return; // 전체 탭에는 새 역할이 이미 보인다
    const createdTab = UtilRole.tabOfType(kindTabs.value, created.roleTypeId);
    if (createdTab) selectTab(createdTab.id);
}

async function onSubmit(payload: RoleEditSubmit) {
    submitting.value = true;
    dialogError.value = '';
    dialogFieldErrors.value = {};
    const editing = editTarget.value !== null;
    let createdRole: RoleView | null = null;
    try {
        if (editing) {
            // 이름·설명이 그대로면 보내지 않는다 — 등급 역할(파생)은 이름·설명 수정을 서버가 거절(409 ROLE_DERIVED)하므로 권한만 고칠 수 있게 한다
            const target = editTarget.value;
            const identityChanged = !target || payload.roleName !== target.roleName || payload.description.trim() !== (target.description ?? '').trim();
            if (identityChanged) await api.update(payload.roleId, { roleName: payload.roleName, description: payload.description });
            if (payload.permissionsChanged) await api.setPermissions(payload.roleId, payload.permissionCodes);
        } else {
            const created = await api.create({ roleTypeId: payload.roleTypeId, roleId: payload.roleId, roleName: payload.roleName, description: payload.description });
            createdRole = created;
            // 만들기와 권한 주기는 API 가 달라(필요한 권한이 다르다) 권한 붙이기만 실패할 수 있다. 그때 역할은 이미 만들어져 있어 「추가하지 못했어요」로 읽히면 다시 눌러도 막힌다 — 사실대로 말한다
            if (payload.permissionCodes.length) {
                try {
                    await api.setPermissions(created.roleId, payload.permissionCodes);
                } catch (permError) {
                    dialogOpen.value = false;
                    await load();
                    revealCreated(created);
                    toast.error(`${t('role.createdButPermFailed')} — ${errorOf(permError).message} ${t('role.createdButPermFailedTail')}`, 10000);
                    return;
                }
            }
        }
        dialogOpen.value = false;
        await load();
        if (createdRole) revealCreated(createdRole);
        toast.success(editing ? t('role.updated') : t('role.added'));
    } catch (e) {
        const error = errorOf(e);
        dialogFieldErrors.value = error.fields ?? {};
        dialogError.value = error.fields && Object.keys(error.fields).length ? '' : error.message;
        // 이름은 저장됐는데 권한만 실패한 수정이면 목록은 이미 바뀌어 있다
        if (editing) void load();
    } finally {
        submitting.value = false;
    }
}

async function confirmDelete() {
    const role = selected.value;
    if (!role) return;
    const ok = await confirm({ title: t('role.deleteTitle'), message: t('role.deleteMessage', { name: role.roleName }), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(role.roleId);
        selectedId.value = null;
        await load();
        toast.success(t('role.deleted'));
    } catch (e) {
        // 지우지 못하는 이유가 중요하다 — 쓰는 운영자·회원이 있으면 먼저 다른 역할로 바꿔야 한다
        toast.error(`${t('role.deleteFailed')} — ${errorOf(e).message}`, 8000);
    } finally {
        deleting.value = false;
    }
}
</script>

<style scoped>
/* 3열(lg 이상)일 때 이 화면의 높이를 화면에 맞춘다 — 상단 네비(`--h-topbar`)·메뉴 줄(`--h-appmenu`)·본문 위아래 여백(`--pad-content-md` × 2, 밀도에 따라 1.5rem·1rem)을 뺀 값.
   `.app-content` 가 스크롤 영역이고 본문 래퍼에 정해진 높이가 없어 `h-full` 은 먹지 않는다. 글자 크기 배율은 `.app-zoom` 의 CSS zoom 이라 안쪽 길이가 배율만큼 커 보이므로 배율로 나눠 맞춘다.
   제목·탭 줄·안내 줄은 이 높이 안의 위쪽 줄(flex 항목)이고 3열이 `flex-1` 로 남는 높이를 쓴다 */
@media (min-width: 64rem) {
    .roles-screen {
        height: calc((100dvh - var(--h-topbar) - var(--h-appmenu) - 2 * var(--pad-content-md)) / var(--text-scale, 1));
    }
}

/* 헤더의 등급 뱃지 — 옆의 `Tag`(11px 글자, 위아래 여백 2px, 줄 높이 1.5 = 높이 20.5px)와 높이를 맞춘다. 글자 크기는 같고(작은 뱃지 11px), 줄 높이 13.75px 에 위아래 여백 합 6.75px 를 줘 20.5px 로 만든다.
   한글 글자 보정(0.045em 위로)은 `GradeBadge` 와 같은 방식 그대로 위·아래 여백의 차이로 유지한다. */
.role-grade-badge.grade-badge--small {
    padding-top: calc(3.375px - 0.045em);
    padding-bottom: calc(3.375px + 0.045em);
}

/* 뱃지(`Tag`) 글자 — 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보인다(실측: 11px 에서 1px). 글자만 그만큼 올려 가운데에 맞춘다 — `GradeBadge` 와 같은 보정 */
.role-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}

/* 탭 줄 — 폴더 탭 모양. 모든 탭에 위·왼쪽·오른쪽 외곽선(`--line`)이 있고, 선택된 탭은 더 진한 선(`--line-strong`)에 본문 바탕색으로 채워 아래 선을 지워 본문과 이어 보이게 한다.
   탭 줄 바닥선은 탭 줄 자체의 안쪽 그림자(탭 아래에 깔린다)로 그린다 — 탭에 음수 여백을 주면 탭 줄의 overflow(스크롤)에 잘린다. 이 화면(`.roles-tabs`)에만 적용되고 다른 화면의 Tabs 는 그대로다 */
.roles-tabs {
    --roles-tab-page-bg: light-dark(var(--p-surface-50), var(--p-surface-950));
    --roles-tab-idle-bg: light-dark(var(--p-surface-100), var(--p-surface-900));
}
.roles-tabs :deep(.p-tablist) {
    border-width: 0;
    background: transparent;
    box-shadow: inset 0 -1px 0 var(--line);
}
.roles-tabs :deep(.p-tablist-content) {
    gap: 0.25rem;
    padding-inline: 0.25rem;
}
.roles-tabs :deep(.p-tab) {
    padding: 0.5rem 0.875rem;
    border-width: 1px;
    border-color: var(--line);
    border-radius: 0.5rem 0.5rem 0 0;
    background: var(--roles-tab-idle-bg);
}
.roles-tabs :deep(.p-tab:not(.p-tab-active):hover) {
    border-color: var(--line-strong);
}
.roles-tabs :deep(.p-tab.p-tab-active) {
    border-color: var(--line-strong);
    border-bottom-color: var(--roles-tab-page-bg);
    background: var(--roles-tab-page-bg);
}
/* 탭이 넘칠 때 끝에 나오는 스크롤 화살표(`.p-tablist-nav-button`) — 바탕을 투명하게(기본 바탕·흰 번짐 그림자를 없앤다). 눌렀을 때·초점도 같다 */
.roles-tabs :deep(.p-tablist-nav-button),
.roles-tabs :deep(.p-tablist-nav-button:hover),
.roles-tabs :deep(.p-tablist-nav-button:focus-visible) {
    background: transparent;
    box-shadow: none;
}
/* 선택 표시는 탭 모양이 하므로 PrimeVue 의 밑줄 막대는 쓰지 않는다 */
.roles-tabs :deep(.p-tablist-active-bar) {
    display: none;
}
</style>
