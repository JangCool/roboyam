<template>
    <!--
        메뉴 관리 › 관리자 사이트 메뉴(`/menus/admin`) — 관리자 앱 자신의 사이드 메뉴 구성. 메뉴 그룹은 고정 둘: 일반 메뉴(`normal`) · 설정 메뉴(`settings`)(`UtilAdminMenu` 의 모드와 같다, 추가·삭제 없음).
        구조는 지금 관리자 메뉴와 같다: 묶음(구획 제목, 최상위만) → 화면 항목 또는 하위 메뉴(부모 항목) → 하위 화면. 최상위 낱개 항목(대시보드처럼)도 된다.
        연결 유형: 관리자 화면(레지스트리 = `UtilAdminMenu.ITEMS` 의 모든 잎 화면 — 한 화면은 두 모드 통틀어 한 번) · 외부 링크(새 탭 기본 켬).
        권한은 편집하지 않는다 — 화면의 권한 코드를 줄에 읽기 전용 딱지로 보인다. 외부 링크만 권한 하나를 고를 수 있다(안 고르면 모두에게 보인다).
        편집: 순서·묶음 이동·하위로 넣기(손잡이 끌기 · Alt+방향키 · 손잡이 메뉴) · 이름 덮어쓰기(ko/en, 비우면 기본 이름) · 아이콘(하위 화면은 없음) · 표시 여부 · 다른 메뉴(모드)로 옮기기(최상위 노드).
        「메뉴에 없는 화면 N개」 경고 + [넣기]. 미리보기: 운영자 역할을 골라 그 역할이 보는 메뉴.
        저장: 두 모드를 한 번에(`PUT /api/admin/admin-menu`, version) — 충돌·검증 오류 처리는 공개 사이트 메뉴와 같다. 권한: 조회 `layout.read` · 저장 `admin-menu.manage`.
        ⚠ 이번 단계에서는 실제 셸(사이드바)이 여전히 `util-admin-menu.ts` 를 읽는다 — 이 화면은 목업에만 저장한다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('menuConfig.adminTitle')" :subtitle="t('menuConfig.adminSubtitle')">
            <template #actions>
                <Button :label="t('menuConfig.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" :disabled="saving" @click="reload" />
            </template>
        </PageTitle>

        <div v-if="loading && !meta" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2.5rem" width="16rem" />
            <Skeleton v-for="n in 8" :key="n" height="2.75rem" />
        </div>
        <AppGadget v-else-if="!meta" :title="t('menuConfig.adminTitle')">
            <ErrorState :title="t('menuConfig.loadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
        </AppGadget>

        <template v-else>
            <Message v-if="readonly" severity="info" :closable="false" size="small">{{ t('menuConfig.readOnly') }}</Message>

            <MenuUnlinkedNotice v-if="!readonly" :title="t('menuConfig.unlinkedScreens', { n: unlinked.length })" :note="t('menuConfig.unlinkedScreensNote')" :entries="unlinkedEntries" :disabled="saving" @put="putScreen" />

            <div class="flex flex-wrap items-center gap-3">
                <SelectButton v-model="mode" :options="modeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('menuConfig.modeLabel')" />
                <span class="text-xs tabular-nums text-muted-color">{{ t('menuConfig.counterNodesPlain', { n: nodeCount }) }}</span>
            </div>

            <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_19rem]">
                <AppGadget :title="t(`menuConfig.mode.${mode}`)">
                    <template v-if="!readonly" #actions>
                        <Button type="button" icon="pi pi-plus" :label="t('menuConfig.add')" size="small" :disabled="saving" aria-haspopup="menu" @click="(e: MouseEvent) => addMenu?.toggle(e)" />
                        <Menu ref="addMenu" :model="addItems" popup />
                    </template>
                    <div class="flex flex-col gap-3">
                        <EmptyState v-if="!current.length" icon="pi pi-bars" :title="t('menuConfig.treeEmpty')" />
                        <MenuTreeEditor v-else :rows="rows" :check="checkMove" :label="t(`menuConfig.mode.${mode}`)" :disabled="readonly || saving" @move="onMove">
                            <template #row="{ row }">
                                <AdminMenuNodeRow
                                    :node="row.node"
                                    :mode="mode"
                                    :top-level="row.depth === 1"
                                    :dimmed="row.dimmed"
                                    :errors="errorsOf(row.node.key)"
                                    :readonly="readonly || saving"
                                    @edit="openEdit(row.node)"
                                    @remove="removeNode(row.node)"
                                    @switch-mode="switchMode(row.node)"
                                />
                            </template>
                        </MenuTreeEditor>
                        <p v-if="!readonly && current.length" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('menuConfig.adminTreeFoot') }}</p>
                    </div>
                </AppGadget>

                <AppGadget :title="t('menuConfig.previewTitle')" class="xl:sticky xl:top-16">
                    <AdminMenuPreview :nodes="current" :viewers="viewers" :roles-note="rolesNote" />
                </AppGadget>
            </div>

            <StickySaveBar v-if="!readonly" revertable :dirty="dirty" :saving="saving" :disabled="errorCount > 0" :error-count="errorCount" :error="saveError" @save="save" @revert="revert">
                <template #extra>
                    <Button v-if="conflict" type="button" :label="t('menuConfig.reloadAfterConflict')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="loading" @click="load" />
                </template>
            </StickySaveBar>
        </template>

        <AdminMenuNodeDialog v-model:visible="dialogOpen" :node="dialogNode" :blank="dialogBlank" :sub-item="dialogSubItem" :parents="dialogParents" :parent-key="dialogParent" :used="used" @save="onDialogSave" />
    </div>
</template>

<script setup lang="ts">
import type { MenuItem } from 'primevue/menuitem';
import { ApiError } from '~/composables/common/useApiClient';
import type { AdminMenuConfigKind, AdminMenuTargetType } from '~/composables/menu/api/useMenuApi.types';
import type { MenuTreeEditorRow } from '~/components/menu/MenuTreeEditor.types';
import type { AdminMenuMode } from '~/utils/util-admin-menu';
import type { AdminMenuEditModes, AdminMenuEditNode } from '~/utils/util-admin-menu-config';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import UtilMenuMessages from '~/utils/util-menu-messages';
import type { MenuMoveAction } from '~/utils/util-menu-tree';
import UtilMenuTree from '~/utils/util-menu-tree';

const { t } = useI18n();
useSeoMeta({ title: () => t('menuConfig.adminTitle') });

const api = useMenuApi();
const roleApi = useRoleApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can, isSuper, permissions } = useAdminPermission();
const { localizedName } = useLocalizedName();

const meta = ref<{ version: string; editable: boolean } | null>(null);
const loading = ref(false);
const loadError = ref('');
const draft = ref<AdminMenuEditModes>({ normal: [], settings: [] });
const baseline = ref('');
const mode = ref<AdminMenuMode>('normal');
const saving = ref(false);
const saveError = ref('');
const conflict = ref(false);
const serverRows = ref<Record<string, Record<string, string>>>({});

const readonly = computed(() => !can('admin-menu.manage') || meta.value?.editable === false);
const serialize = (modes: AdminMenuEditModes) => JSON.stringify({ normal: UtilAdminMenuConfig.toDto(modes.normal), settings: UtilAdminMenuConfig.toDto(modes.settings) });
const dirty = computed(() => meta.value !== null && serialize(draft.value) !== baseline.value);
const current = computed(() => draft.value[mode.value]);
const nodeCount = computed(() => UtilMenuTree.count(current.value));

const modeOptions = computed(() => UtilAdminMenuConfig.MODES.map((value) => ({ value, label: t(`menuConfig.mode.${value}`) })));

async function load() {
    loading.value = true;
    loadError.value = '';
    try {
        const res = await api.adminMenu();
        draft.value = { normal: UtilAdminMenuConfig.toEdit(res.modes.normal), settings: UtilAdminMenuConfig.toEdit(res.modes.settings) };
        baseline.value = serialize(draft.value);
        meta.value = { version: res.version, editable: res.editable };
        saveError.value = '';
        conflict.value = false;
        serverRows.value = {};
    } catch (e) {
        if (!meta.value) loadError.value = UtilMenuMessages.of(t, e);
        else toast.error(UtilMenuMessages.of(t, e), 8000);
    } finally {
        loading.value = false;
    }
}

async function reload() {
    if (dirty.value && !(await confirmLeave())) return;
    await load();
}

// ── 줄 · 검증 ─

type Row = MenuTreeEditorRow & { node: AdminMenuEditNode; dimmed: boolean };

const rows = computed<Row[]>(() =>
    UtilMenuTree.flatten(current.value, (node) => !node.visible).map((row) => ({
        key: row.node.key,
        depth: row.depth,
        container: row.node.kind !== 'SCREEN',
        name: UtilMenuMessages.adminNodeName(t, row.node, localizedName).text,
        node: row.node,
        dimmed: row.hiddenByAncestor,
    })),
);

const problems = computed(() => UtilAdminMenuConfig.validate(draft.value));
const errorCount = computed(() => Object.keys(problems.value.rows).length);
const used = computed(() => UtilAdminMenuConfig.usedScreens(draft.value));

function errorsOf(key: string): string[] {
    const local = Object.values(problems.value.rows[key] ?? {}).map((code) => UtilMenuMessages.problem(t, code));
    return [...local, ...Object.values(serverRows.value[key] ?? {})];
}

function checkMove(key: string, action: MenuMoveAction): string | null {
    const { problem } = UtilAdminMenuConfig.tryMove(current.value, key, action);
    return problem ? UtilMenuMessages.moveProblem(t, problem) : null;
}

function onMove(key: string, action: MenuMoveAction) {
    const { tree } = UtilAdminMenuConfig.tryMove(current.value, key, action);
    if (tree) draft.value = { ...draft.value, [mode.value]: tree };
}

function switchMode(node: AdminMenuEditNode) {
    draft.value = UtilAdminMenuConfig.switchMode(draft.value, mode.value, node.key);
    const to: AdminMenuMode = mode.value === 'normal' ? 'settings' : 'normal';
    toast.success(t('menuConfig.switchedMode', { name: UtilMenuMessages.adminNodeName(t, node, localizedName).text, mode: t(`menuConfig.mode.${to}`) }));
}

// ── 미연결 화면 ─

const unlinked = computed(() => UtilAdminMenuConfig.unlinkedScreens(draft.value));
const unlinkedEntries = computed(() => unlinked.value.map((screen) => ({ key: screen.key, label: t(screen.labelKey), sub: screen.to, reason: t(`menuConfig.mode.${screen.mode}`) })));

function putScreen(key: string) {
    const screen = UtilAdminMenuConfig.screenOf(key);
    if (!screen) return;
    draft.value = UtilAdminMenuConfig.putScreen(draft.value, screen);
    mode.value = screen.mode;
    toast.success(t('menuConfig.putDone', { name: t(screen.labelKey) }));
}

// ── 추가 · 수정 · 삭제 ─

const addMenu = ref<{ toggle: (event: Event) => void } | null>(null);
const addItems = computed<MenuItem[]>(() => [
    { label: t('menuConfig.adminAdd.SECTION'), icon: 'pi pi-folder', command: () => openAdd('SECTION') },
    { label: t('menuConfig.adminAdd.FOLDER'), icon: 'pi pi-angle-down', command: () => openAdd('FOLDER') },
    { label: t('menuConfig.adminAdd.ADMIN_SCREEN'), icon: 'pi pi-desktop', disabled: unlinked.value.length === 0, command: () => openAdd('SCREEN', 'ADMIN_SCREEN') },
    { label: t('menuConfig.adminAdd.URL'), icon: 'pi pi-external-link', command: () => openAdd('SCREEN', 'URL') },
]);

const dialogOpen = ref(false);
const dialogNode = ref<AdminMenuEditNode | null>(null);
const dialogBlank = ref<AdminMenuEditNode | null>(null);
const dialogSubItem = ref(false);
const dialogParent = ref('');
const dialogKind = ref<AdminMenuConfigKind>('SCREEN');

/** 새 노드를 넣을 수 있는 곳 — 최상위 + 이 종류를 담을 수 있는 묶음·하위 메뉴 */
const dialogParents = computed(() => [
    { value: '', label: t('menuConfig.parentTop') },
    ...UtilMenuTree.flatten(current.value)
        .filter((row) => UtilAdminMenuConfig.canContain(row.node, dialogKind.value) && row.node.kind !== 'SCREEN')
        .map((row) => ({ value: row.node.key, label: `${'· '.repeat(row.depth - 1)}${UtilMenuMessages.adminNodeName(t, row.node, localizedName).text}` })),
]);

function openAdd(kind: AdminMenuConfigKind, targetType: AdminMenuTargetType | null = null) {
    dialogKind.value = kind;
    dialogNode.value = null;
    dialogBlank.value = UtilAdminMenuConfig.newNode(kind, targetType);
    dialogSubItem.value = false;
    dialogParent.value = '';
    dialogOpen.value = true;
}

function openEdit(node: AdminMenuEditNode) {
    dialogKind.value = node.kind;
    dialogNode.value = node;
    dialogBlank.value = null;
    dialogSubItem.value = UtilAdminMenuConfig.isSubItem(current.value, node.key);
    dialogParent.value = '';
    dialogOpen.value = true;
}

function onDialogSave(node: AdminMenuEditNode, parentKey: string | null) {
    const tree = current.value;
    if (UtilMenuTree.find(tree, node.key)) {
        const patch: Partial<AdminMenuEditNode> = { ...node };
        delete patch.children;
        draft.value = { ...draft.value, [mode.value]: UtilMenuTree.patch(tree, node.key, patch) };
    } else {
        // 하위 메뉴 안에 넣는 화면은 아이콘이 없다
        const parent = parentKey ? UtilMenuTree.find(tree, parentKey)?.node : null;
        const placed = parent?.kind === 'FOLDER' ? { ...node, icon: '' } : node;
        draft.value = { ...draft.value, [mode.value]: UtilMenuTree.append(tree, parentKey, placed) };
    }
}

async function removeNode(node: AdminMenuEditNode) {
    const count = UtilMenuTree.descendantCount(node);
    if (count) {
        const ok = await confirm({
            title: t('menuConfig.removeFolderTitle', { name: UtilMenuMessages.adminNodeName(t, node, localizedName).text }),
            message: t('menuConfig.removeAdminMessage', { n: count }),
            confirmLabel: t('common.delete'),
            tone: 'danger',
        });
        if (!ok) return;
    }
    draft.value = { ...draft.value, [mode.value]: UtilMenuTree.remove(current.value, node.key) };
}

// ── 미리보기의 보는 사람 ─

const roles = ref<{ value: string; label: string; codes: string[] | null }[]>([]);
const rolesNote = ref('');

const viewers = computed(() => [
    { value: 'SUPER', label: t('menuConfig.viewerSuper'), codes: null },
    { value: 'ME', label: t('menuConfig.viewerMe'), codes: isSuper.value ? null : [...permissions.value] },
    ...roles.value,
]);

async function loadRoles() {
    if (!can('role.read')) {
        rolesNote.value = t('menuConfig.rolesNoPermission');
        return;
    }
    try {
        const list = await roleApi.load();
        roles.value = list.filter((role) => role.roleKind === 'OPERATOR').map((role) => ({ value: `ROLE:${role.roleId}`, label: role.roleName, codes: role.permissionCodes }));
        rolesNote.value = '';
    } catch (e) {
        rolesNote.value = `${t('menuConfig.rolesLoadFailed')} — ${UtilMenuMessages.of(t, e)}`;
    }
}

// ── 저장 ─

async function save() {
    if (!meta.value || saving.value || errorCount.value > 0) return;
    saving.value = true;
    saveError.value = '';
    conflict.value = false;
    serverRows.value = {};
    try {
        const res = await api.saveAdminMenu({
            version: meta.value.version,
            modes: { normal: UtilAdminMenuConfig.toDto(draft.value.normal), settings: UtilAdminMenuConfig.toDto(draft.value.settings) },
        });
        draft.value = { normal: UtilAdminMenuConfig.toEdit(res.modes.normal), settings: UtilAdminMenuConfig.toEdit(res.modes.settings) };
        baseline.value = serialize(draft.value);
        meta.value = { version: res.version, editable: res.editable };
        toast.success(t('menuConfig.saved'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'MENU_VERSION_CONFLICT') conflict.value = true;
        if (e instanceof ApiError && e.fields) {
            const index = new Map([...UtilMenuTree.pathIndex(draft.value.normal, 'modes.normal'), ...UtilMenuTree.pathIndex(draft.value.settings, 'modes.settings')]);
            const split = UtilMenuTree.splitFieldErrors(e.fields, index);
            serverRows.value = split.rows;
            saveError.value = [e.message, ...split.form].join(' ');
        } else {
            saveError.value = UtilMenuMessages.of(t, e);
        }
    } finally {
        saving.value = false;
    }
}

function revert() {
    const base = JSON.parse(baseline.value) as { normal: Parameters<typeof UtilAdminMenuConfig.toEdit>[0]; settings: Parameters<typeof UtilAdminMenuConfig.toEdit>[0] };
    draft.value = { normal: UtilAdminMenuConfig.toEdit(base.normal), settings: UtilAdminMenuConfig.toEdit(base.settings) };
    saveError.value = '';
    conflict.value = false;
    serverRows.value = {};
}

// ── 나가기 ─

function confirmLeave(): Promise<boolean> {
    return confirm({ title: t('menuConfig.leaveTitle'), message: t('menuConfig.leaveMessage'), confirmLabel: t('menuConfig.leaveConfirm'), cancelLabel: t('menuConfig.leaveCancel'), tone: 'danger' });
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirmLeave();
});

onMounted(() => {
    void load();
    void loadRoles();
});
</script>
