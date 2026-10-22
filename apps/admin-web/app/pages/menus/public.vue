<template>
    <!--
        메뉴 관리 › 공개 사이트 메뉴(`/menus/public`) — 공개 사이트의 메뉴를 **메뉴 그룹(메뉴 세트)** 별로 구성한다. community 의 메뉴 구성(트리 하나)을 그룹 여럿으로 옮겼다.
        왼쪽: 메뉴 그룹 목록(추가). 오른쪽: 고른 그룹의 정보(수정·삭제) · 메뉴에 없는 게시판 경고(SIDE 그룹) · 트리 편집기 · 미리보기. 좁은 화면은 위아래로 쌓인다.
        트리: 폴더(깊이 ≤ 3)와 항목(깊이 ≤ 4), 그룹당 노드 ≤ 80. 순서·단계는 손잡이 끌기 · Alt+방향키 · 손잡이 메뉴(`MenuTreeEditor`). 항목 연결 유형은 게시판 · 앱 화면 · 페이지 · 외부 링크(+ 준비 중 넷).
        저장: 그룹 단위로 트리 전체를 한 번에 바꾼다(하단 떠 있는 저장 줄 — `forms.md` 2-4). 바뀐 게 없으면 저장이 꺼지고, 그룹을 바꾸거나 다른 화면으로 갈 때 저장 안 한 변경을 묻는다.
          저장할 때 `version` 을 보내 다른 사람이 먼저 저장했으면 409 `MENU_VERSION_CONFLICT`(→ [다시 불러오기]). 서버 입력 오류(`fields["nodes[1].children[2].label"]`)는 해당 줄 아래에 선다.
        메뉴 그룹 정보(추가·수정·삭제)는 트리 저장과 별개로 바로 저장한다.
        권한: 조회 `layout.read`(메뉴 정의가 건다) · 저장 `layout.settings` — 없으면(또는 서버가 `editable: false`) 읽기 전용(편집 단추·손잡이·저장 줄이 없다).
        ⚠ MOCK: `useMenuApi` 가 목업을 부른다 — 스위치는 `composables/menu/api/mock/menu-mock.ts` 머리말.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('menuConfig.publicTitle')" :subtitle="t('menuConfig.publicSubtitle')">
            <template #actions>
                <Button :label="t('menuConfig.reload')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="setsLoading || treeLoading" :disabled="saving" @click="reloadAll" />
            </template>
        </PageTitle>

        <!-- 메뉴 그룹 목록을 읽는 중 · 실패 -->
        <div v-if="setsLoading && !sets" class="grid grid-cols-1 gap-4 lg:grid-cols-[16rem_minmax(0,1fr)]" role="status" :aria-label="t('common.loading')">
            <Skeleton height="14rem" class="rounded-xl" />
            <Skeleton height="24rem" class="rounded-xl" />
        </div>
        <AppGadget v-else-if="!sets" :title="t('menuConfig.setsTitle')">
            <ErrorState :title="t('menuConfig.loadFailed')" :description="setsError" retry :retrying="setsLoading" @retry="loadSets" />
        </AppGadget>

        <template v-else>
            <Message v-if="readonly && tree" severity="info" :closable="false" size="small">{{ t('menuConfig.readOnly') }}</Message>

            <div class="grid grid-cols-1 items-start gap-4 lg:grid-cols-[16rem_minmax(0,1fr)]">
                <!-- 왼쪽: 메뉴 그룹 -->
                <AppGadget :title="t('menuConfig.setsTitle')" class="lg:sticky lg:top-16">
                    <template v-if="canManage" #actions>
                        <Button type="button" icon="pi pi-plus" size="small" severity="secondary" variant="text" rounded :aria-label="t('menuConfig.setAdd')" v-tooltip.top="t('menuConfig.setAdd')" @click="openSetDialog(null)" />
                    </template>
                    <EmptyState v-if="!sets.length" icon="pi pi-bars" :title="t('menuConfig.setsEmpty')" />
                    <MenuSetList v-else :sets="sets" :selected-key="selectedKey" @select="selectSet" />
                </AppGadget>

                <!-- 오른쪽: 고른 그룹 -->
                <div class="flex min-w-0 flex-col gap-4">
                    <div v-if="treeLoading && !tree" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                        <Skeleton height="5rem" class="rounded-xl" />
                        <Skeleton v-for="n in 6" :key="n" height="2.75rem" />
                    </div>
                    <AppGadget v-else-if="treeError && !tree" :title="t('menuConfig.treeTitle')">
                        <ErrorState :title="t('menuConfig.treeLoadFailed')" :description="treeError" retry :retrying="treeLoading" @retry="selectedKey && loadTree(selectedKey)" />
                    </AppGadget>

                    <template v-else-if="tree && currentSet && options">
                        <!-- 그룹 정보 -->
                        <AppGadget :title="localizedName(currentSet.name, currentSet.nameEn)">
                            <template #title-extra>
                                <MenuTag :value="t(`menuConfig.placement.${currentSet.placement}`)" :severity="side ? 'info' : 'secondary'" />
                                <MenuTag v-if="!currentSet.enabled" :value="t('menuConfig.setDisabled')" severity="warn" />
                                <MenuTag v-if="currentSet.system" :value="t('menuConfig.systemSet')" icon="pi pi-lock" />
                            </template>
                            <template v-if="canManage" #actions>
                                <Button type="button" :label="t('common.edit')" icon="pi pi-pencil" size="small" severity="secondary" variant="text" @click="openSetDialog(currentSet)" />
                                <Button v-if="!currentSet.system" type="button" :label="t('common.delete')" icon="pi pi-trash" size="small" severity="danger" variant="text" @click="removeSet(currentSet)" />
                            </template>
                            <dl class="m-0 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-1.5 text-[13px]">
                                <dt class="text-muted-color">{{ t('menuConfig.field.setKey') }}</dt>
                                <dd class="m-0"><code>{{ currentSet.key }}</code></dd>
                                <dt class="text-muted-color">{{ t('menuConfig.field.placement') }}</dt>
                                <dd class="m-0">{{ t(`menuConfig.placementDesc.${currentSet.placement}`) }}</dd>
                                <template v-if="currentSet.description">
                                    <dt class="text-muted-color">{{ t('menuConfig.field.description') }}</dt>
                                    <dd class="m-0">{{ currentSet.description }}</dd>
                                </template>
                            </dl>
                        </AppGadget>

                        <MenuUnlinkedNotice v-if="side && !readonly" :title="t('menuConfig.unlinkedBoards', { n: unlinked.length })" :note="t('menuConfig.unlinkedBoardsNote')" :entries="unlinkedEntries" :disabled="saving" @put="putBoard" />

                        <div class="grid grid-cols-1 items-start gap-4 2xl:grid-cols-[minmax(0,1fr)_19rem]">
                            <!-- 트리 -->
                            <AppGadget :title="t('menuConfig.treeTitle')">
                                <template v-if="!readonly" #actions>
                                    <Button type="button" :label="t('menuConfig.addFolder')" icon="pi pi-folder-plus" size="small" severity="secondary" variant="outlined" :disabled="saving || nodesFull" @click="openFolder(null, '')" />
                                    <Button type="button" :label="t('menuConfig.addItem')" icon="pi pi-plus" size="small" :disabled="saving || nodesFull" @click="openItem(null, '')" />
                                </template>
                                <div class="flex flex-col gap-3">
                                    <p class="m-0 text-xs tabular-nums text-muted-color">{{ UtilMenuMessages.counter(t, nodeCount, side ? tabCount : null) }}</p>
                                    <Message v-for="code in problems.tree" :key="code" severity="error" variant="simple" size="small" :closable="false">{{ UtilMenuMessages.treeProblem(t, code) }}</Message>

                                    <EmptyState v-if="!draft.length" icon="pi pi-bars" :title="t('menuConfig.treeEmpty')" :description="readonly ? '' : t('menuConfig.treeEmptyBody')">
                                        <Button v-if="!readonly" type="button" :label="t('menuConfig.addItem')" icon="pi pi-plus" @click="openItem(null, '')" />
                                    </EmptyState>
                                    <MenuTreeEditor v-else :rows="rows" :check="checkMove" :label="t('menuConfig.treeTitle')" :disabled="readonly || saving" @move="onMove">
                                        <template #row="{ row }">
                                            <MenuNodeRow
                                                :node="row.node"
                                                :ctx="ctx"
                                                :dimmed="row.dimmed"
                                                :errors="errorsOf(row.node.key)"
                                                :readonly="readonly || saving"
                                                :can-add-inside="!nodesFull && UtilMenu.canAddInside(draft, row.node.key, 'ITEM')"
                                                @edit="editNode(row.node)"
                                                @remove="removeNode(row.node)"
                                                @add-inside="openItem(null, row.node.key)"
                                            />
                                        </template>
                                    </MenuTreeEditor>
                                    <p v-if="!readonly && draft.length" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('menuConfig.treeFoot') }}</p>
                                </div>
                            </AppGadget>

                            <!-- 미리보기 -->
                            <AppGadget :title="t('menuConfig.previewTitle')" class="2xl:sticky 2xl:top-16">
                                <MenuPreview :nodes="draft" :ctx="ctx" />
                            </AppGadget>
                        </div>

                        <StickySaveBar v-if="!readonly" revertable :dirty="dirty" :saving="saving" :disabled="errorCount > 0" :error-count="errorCount" :error="saveError" @save="save" @revert="revert">
                            <template #extra>
                                <Button v-if="conflict" type="button" :label="t('menuConfig.reloadAfterConflict')" icon="pi pi-refresh" size="small" severity="secondary" variant="outlined" :loading="treeLoading" @click="reloadAfterConflict" />
                            </template>
                        </StickySaveBar>
                    </template>
                </div>
            </div>
        </template>

        <MenuSetDialog v-model:visible="setDialogOpen" :set="setDialogTarget" :taken-keys="(sets ?? []).map((s) => s.key)" :saving="setSaving" :field-errors="setFieldErrors" :error="setError" @submit="submitSet" />
        <MenuFolderDialog v-if="options" v-model:visible="folderOpen" :folder="folderTarget" :parents="folderParents" :parent-key="dialogParent" :ctx="ctx" @save="onFolderSave" />
        <MenuItemDialog v-if="options" v-model:visible="itemOpen" :item="itemTarget" :parents="itemParents" :parent-key="dialogParent" :ctx="ctx" :linked="linked" :tab-count="itemTabCount" @save="onItemSave" />
    </div>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { MenuSet, MenuSetCreateRequest, MenuTargetOptions } from '~/composables/menu/api/useMenuApi.types';
import type { MenuTreeEditorRow } from '~/components/menu/MenuTreeEditor.types';
import type { MenuContext, MenuEditFolder, MenuEditItem, MenuEditNode } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';
import UtilMenuMessages from '~/utils/util-menu-messages';
import type { MenuMoveAction } from '~/utils/util-menu-tree';
import UtilMenuTree from '~/utils/util-menu-tree';

const { t } = useI18n();
useSeoMeta({ title: () => t('menuConfig.publicTitle') });

const api = useMenuApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();

// ── 메뉴 그룹 ─

const sets = ref<MenuSet[] | null>(null);
const setsLoading = ref(false);
const setsError = ref('');
const selectedKey = ref<string | null>(null);

async function loadSets() {
    setsLoading.value = true;
    setsError.value = '';
    try {
        sets.value = await api.sets();
        const keep = sets.value.find((s) => s.key === selectedKey.value);
        const first = keep ?? sets.value.find((s) => s.placement === 'SIDE') ?? sets.value[0];
        if (first && (!tree.value || first.key !== selectedKey.value)) {
            selectedKey.value = first.key;
            await loadTree(first.key);
        }
    } catch (e) {
        setsError.value = UtilMenuMessages.of(t, e);
    } finally {
        setsLoading.value = false;
    }
}

/** 다른 그룹을 연다 — 저장하지 않은 변경이 있으면 묻는다 */
async function selectSet(key: string) {
    if (key === selectedKey.value) return;
    if (dirty.value && !(await confirmLeave())) return;
    selectedKey.value = key;
    await loadTree(key);
}

// ── 트리 ─

const tree = ref<{ version: string; editable: boolean } | null>(null);
const options = ref<MenuTargetOptions | null>(null);
const treeLoading = ref(false);
const treeError = ref('');
const draft = ref<MenuEditNode[]>([]);
/** 불러온(저장된) 트리의 서버 모양 — 바뀜 판정 */
const baseline = ref('');
const saving = ref(false);
const saveError = ref('');
const conflict = ref(false);
const serverRows = ref<Record<string, Record<string, string>>>({});

const currentSet = computed(() => sets.value?.find((s) => s.key === selectedKey.value) ?? null);
const placement = computed(() => currentSet.value?.placement ?? 'CUSTOM');
const side = computed(() => UtilMenu.isSide(placement.value));
const canManage = computed(() => can('layout.settings'));
const readonly = computed(() => !canManage.value || tree.value?.editable === false);

const ctx = computed<MenuContext>(() => ({ placement: placement.value, boards: options.value?.boards ?? [], grades: options.value?.grades ?? [], salesEnabled: options.value?.salesEnabled ?? true }));

const serialized = computed(() => JSON.stringify(UtilMenu.toDto(draft.value, placement.value)));
const dirty = computed(() => tree.value !== null && serialized.value !== baseline.value);

async function loadTree(key: string) {
    treeLoading.value = true;
    treeError.value = '';
    try {
        const [res, opts] = await Promise.all([api.tree(key), api.targetOptions(key)]);
        if (selectedKey.value !== key) return;
        options.value = opts;
        applyTree(res.set, res.nodes, res.version, res.editable);
    } catch (e) {
        tree.value = null;
        treeError.value = UtilMenuMessages.of(t, e);
    } finally {
        treeLoading.value = false;
    }
}

function applyTree(set: MenuSet, nodes: Parameters<typeof UtilMenu.toEdit>[0], version: string, editable: boolean) {
    sets.value = (sets.value ?? []).map((s) => (s.key === set.key ? set : s));
    draft.value = UtilMenu.toEdit(nodes);
    baseline.value = JSON.stringify(UtilMenu.toDto(draft.value, set.placement));
    tree.value = { version, editable };
    saveError.value = '';
    conflict.value = false;
    serverRows.value = {};
}

async function reloadAll() {
    if (dirty.value && !(await confirmLeave())) return;
    tree.value = null;
    await loadSets();
    if (selectedKey.value && !tree.value) await loadTree(selectedKey.value);
}

// ── 줄 · 검증 ─

type Row = MenuTreeEditorRow & { node: MenuEditNode; dimmed: boolean };

const rows = computed<Row[]>(() =>
    UtilMenuTree.flatten(draft.value, (node) => !node.visible).map((row) => ({
        key: row.node.key,
        depth: row.depth,
        container: row.node.kind === 'FOLDER',
        name: UtilMenuMessages.nodeName(t, row.node, ctx.value, localizedName).text,
        node: row.node,
        dimmed: row.hiddenByAncestor,
    })),
);

const problems = computed(() => UtilMenu.validate(draft.value, placement.value));
const nodeCount = computed(() => UtilMenuTree.count(draft.value));
const tabCount = computed(() => UtilMenu.mobileTabCount(draft.value));
const nodesFull = computed(() => nodeCount.value >= UtilMenu.LIMITS.nodes);
const errorCount = computed(() => Object.keys(problems.value.rows).length + problems.value.tree.length);
const linked = computed(() => UtilMenu.linkedTargets(draft.value));

/** 줄 아래 문구 — 화면 검사 + 서버 입력 오류(서버 문구 그대로) */
function errorsOf(key: string): string[] {
    const local = Object.values(problems.value.rows[key] ?? {}).map((code) => UtilMenuMessages.problem(t, code));
    return [...local, ...Object.values(serverRows.value[key] ?? {})];
}

function checkMove(key: string, action: MenuMoveAction): string | null {
    const { problem } = UtilMenu.tryMove(draft.value, key, action);
    return problem ? UtilMenuMessages.moveProblem(t, problem) : null;
}

function onMove(key: string, action: MenuMoveAction) {
    const { tree: next } = UtilMenu.tryMove(draft.value, key, action);
    if (next) draft.value = next;
}

// ── 미연결 게시판 ─

const unlinked = computed(() => (side.value ? UtilMenu.unlinkedBoards(draft.value, ctx.value) : []));
const unlinkedEntries = computed(() =>
    unlinked.value.map((entry) => ({ key: entry.board.id, label: localizedName(entry.board.name, entry.board.nameEn), sub: `/boards/${entry.board.slug.toLowerCase()}`, reason: t(`menuConfig.unlinkedReason.${entry.reason}`) })),
);

function putBoard(boardId: string) {
    const board = ctx.value.boards.find((b) => b.id === boardId);
    if (!board) return;
    if (nodesFull.value) {
        toast.error(UtilMenuMessages.treeProblem(t, 'NODES_LIMIT'), 8000);
        return;
    }
    // 숨김 안에만 있던 게시판이면 그 항목은 두고 새 항목을 넣지 않는다 — 한 그룹에 한 번이라 숨김 항목을 꺼내 옮긴다
    const existing = linked.value.get(`BOARD:${board.id}`);
    if (existing) {
        const folder = UtilMenuTree.all(draft.value).find((node) => node.kind === 'FOLDER' && node.autoAdd);
        const spot = folder && UtilMenu.canAddInside(draft.value, folder.key, 'ITEM') ? folder.key : null;
        const hit = UtilMenuTree.find(draft.value, existing.itemKey);
        if (!hit) return;
        const moved = { ...UtilMenuTree.clone(hit.node), visible: true } as MenuEditItem;
        draft.value = UtilMenuTree.append(UtilMenuTree.remove(draft.value, existing.itemKey), spot, moved);
    } else {
        draft.value = UtilMenu.putBoard(draft.value, board);
    }
    toast.success(t('menuConfig.putDone', { name: localizedName(board.name, board.nameEn) }));
}

// ── 폴더 · 항목 대화상자 ─

const folderOpen = ref(false);
const itemOpen = ref(false);
const folderTarget = ref<MenuEditFolder | null>(null);
const itemTarget = ref<MenuEditItem | null>(null);
/** 새 노드를 넣을 곳(`''` 은 최상위) */
const dialogParent = ref('');

function parentOptions(kind: 'FOLDER' | 'ITEM') {
    return [
        { value: '', label: t('menuConfig.parentTop') },
        ...UtilMenu.parentCandidates(draft.value, kind).map(({ folder, depth }) => ({ value: folder.key, label: `${'· '.repeat(depth - 1)}${UtilMenuMessages.nodeName(t, folder, ctx.value, localizedName).text}` })),
    ];
}
const folderParents = computed(() => parentOptions('FOLDER'));
const itemParents = computed(() => parentOptions('ITEM'));
const itemTabCount = computed(() => tabCount.value - (itemTarget.value?.mobileTab ? 1 : 0));

function openFolder(folder: MenuEditFolder | null, parentKey: string) {
    folderTarget.value = folder;
    dialogParent.value = parentKey;
    folderOpen.value = true;
}

function openItem(item: MenuEditItem | null, parentKey: string) {
    itemTarget.value = item;
    dialogParent.value = parentKey;
    itemOpen.value = true;
}

function editNode(node: MenuEditNode) {
    if (node.kind === 'FOLDER') openFolder(node, '');
    else openItem(node, '');
}

/** 고친 노드를 트리에 반영 — 있으면 칸만 덮고(하위는 그대로), 없으면 넣을 곳 끝에 넣는다 */
function place(node: MenuEditNode, parentKey: string | null) {
    const exists = UtilMenuTree.find(draft.value, node.key);
    if (exists) {
        const patch: Partial<MenuEditNode> = { ...node };
        if (node.kind === 'FOLDER') delete (patch as Partial<MenuEditFolder>).children;
        draft.value = UtilMenuTree.patch(draft.value, node.key, patch);
    } else {
        draft.value = UtilMenuTree.append(draft.value, parentKey, node);
    }
}

function onFolderSave(folder: MenuEditFolder, parentKey: string | null) {
    place(folder, parentKey);
    if (side.value && folder.autoAdd) draft.value = UtilMenu.setAutoAdd(draft.value, folder.key, true);
}

function onItemSave(item: MenuEditItem, parentKey: string | null) {
    place(item, parentKey);
}

/** 지우기 — 폴더는 하위 개수를 알리고 묻는다 */
async function removeNode(node: MenuEditNode) {
    const name = UtilMenuMessages.nodeName(t, node, ctx.value, localizedName).text;
    if (node.kind === 'FOLDER') {
        const count = UtilMenuTree.descendantCount(node);
        const ok = await confirm({
            title: t('menuConfig.removeFolderTitle', { name }),
            message: count ? t('menuConfig.removeFolderMessage', { n: count }) : t('menuConfig.removeFolderEmpty'),
            confirmLabel: t('common.delete'),
            tone: 'danger',
        });
        if (!ok) return;
    }
    draft.value = UtilMenuTree.remove(draft.value, node.key);
}

// ── 저장 ─

async function save() {
    const key = selectedKey.value;
    if (!key || !tree.value || saving.value || errorCount.value > 0) return;
    saving.value = true;
    saveError.value = '';
    conflict.value = false;
    serverRows.value = {};
    try {
        const res = await api.saveTree(key, { version: tree.value.version, nodes: UtilMenu.toDto(draft.value, placement.value) });
        applyTree(res.set, res.nodes, res.version, res.editable);
        toast.success(t('menuConfig.saved'));
    } catch (e) {
        if (e instanceof ApiError && e.code === 'MENU_VERSION_CONFLICT') conflict.value = true;
        if (e instanceof ApiError && e.fields) {
            const split = UtilMenuTree.splitFieldErrors(e.fields, UtilMenuTree.pathIndex(draft.value));
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
    draft.value = UtilMenu.toEdit(JSON.parse(baseline.value));
    saveError.value = '';
    conflict.value = false;
    serverRows.value = {};
}

/** 충돌 뒤 — 내 변경을 버리고 서버의 최신 트리를 다시 받는다 */
async function reloadAfterConflict() {
    if (selectedKey.value) await loadTree(selectedKey.value);
}

// ── 메뉴 그룹 추가 · 수정 · 삭제(바로 저장) ─

const setDialogOpen = ref(false);
const setDialogTarget = ref<MenuSet | null>(null);
const setSaving = ref(false);
const setFieldErrors = ref<Record<string, string>>({});
const setError = ref('');

function openSetDialog(set: MenuSet | null) {
    setDialogTarget.value = set;
    setFieldErrors.value = {};
    setError.value = '';
    setDialogOpen.value = true;
}

async function submitSet(body: MenuSetCreateRequest) {
    setSaving.value = true;
    setFieldErrors.value = {};
    setError.value = '';
    try {
        const target = setDialogTarget.value;
        if (target) {
            const { key: _key, ...rest } = body;
            const updated = await api.updateSet(target.key, rest);
            sets.value = (sets.value ?? []).map((s) => (s.key === updated.key ? updated : s));
            toast.success(t('menuConfig.setSaved'));
        } else {
            const created = await api.createSet(body);
            sets.value = [...(sets.value ?? []), created];
            toast.success(t('menuConfig.setCreated', { name: created.name }));
            setDialogOpen.value = false;
            await selectSet(created.key);
            return;
        }
        setDialogOpen.value = false;
    } catch (e) {
        if (e instanceof ApiError && e.fields) setFieldErrors.value = e.fields;
        setError.value = UtilMenuMessages.of(t, e);
    } finally {
        setSaving.value = false;
    }
}

async function removeSet(set: MenuSet) {
    const ok = await confirm({
        title: t('menuConfig.setRemoveTitle', { name: localizedName(set.name, set.nameEn) }),
        message: t('menuConfig.setRemoveMessage', { n: set.nodeCount }),
        confirmLabel: t('common.delete'),
        tone: 'danger',
    });
    if (!ok) return;
    try {
        await api.deleteSet(set.key);
        sets.value = (sets.value ?? []).filter((s) => s.key !== set.key);
        toast.success(t('menuConfig.setRemoved', { name: set.name }));
        // 지운 그룹의 바뀐 트리는 함께 버린다
        tree.value = null;
        const next = sets.value[0];
        selectedKey.value = next?.key ?? null;
        if (next) await loadTree(next.key);
    } catch (e) {
        toast.error(`${t('menuConfig.setRemoveFailed')} — ${UtilMenuMessages.of(t, e)}`, 8000);
    }
}

// ── 나가기 ─

function confirmLeave(): Promise<boolean> {
    return confirm({ title: t('menuConfig.leaveTitle'), message: t('menuConfig.leaveMessage'), confirmLabel: t('menuConfig.leaveConfirm'), cancelLabel: t('menuConfig.leaveCancel'), tone: 'danger' });
}

onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirmLeave();
});

onMounted(loadSets);
</script>
