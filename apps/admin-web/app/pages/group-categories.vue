<template>
    <!--
        소모임 분류(`/group-categories`) — community 관리자 앱 `group-categories.vue` 를 옮겼다. 왼쪽에 분류 목록(이름·게시판 수·소모임 수·투자 고지·▲▼ 즉시 저장), 오른쪽에 선택한 분류(이름·[분류 삭제]·게시판 고르기·추가한 게시판·저장 줄).
        분류 1 : 소모임 게시판(게시판 종류 「소모임」) N. 소모임의 분류는 그 게시판이 할당된 분류로 정해진다 — 게시판을 옮기거나 빼면 그 게시판 소모임의 분류가 따라 바뀐다(소모임은 사라지지 않는다).
        - 저장은 **선택한 분류 단위**(이름 + 게시판 전체 교체). 다른 분류를 누르거나 [분류 추가]를 누를 때 저장 전 변경이 있으면 묻고, 다른 화면으로 떠날 때도 묻는다.
        - 열어 둔 분류는 주소의 `?category=` — 새로고침해도 같은 분류가 선택된다. 분류에 안 든 소모임 게시판이 있으면 위에 노란 줄 + [할당하기](선택한 분류의 입력에 더한다 — 저장해야 반영).
        - 그 사이 다른 관리자가 먼저 고쳤으면(`409 GROUP_CATEGORY_STALE`) 최신 상태를 받고 쓰던 입력을 버린 채 알린다. 소모임이 있는 분류는 지울 수 없다(`409 GROUP_CATEGORY_NOT_EMPTY`).
        권한: 화면 `group.read` · 만들기·저장·순서·삭제 `group.settings`(community 는 최고 관리자 전용 — 로보얌은 권한 코드로 위임한다. 없으면 읽기 전용). 게시판 관리 링크는 `board.read`.
        ⚠ 목업 API(`useGroupCategoryApi` — `?mock=categories-fail` · `categories-empty` · `category-stale` · `order-fail`).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('group.categoriesTitle')" :subtitle="t('group.categoriesSubtitle')">
            <template v-if="canEdit && data && data.categories.length" #actions>
                <Button :label="t('group.categoryAdd')" icon="pi pi-plus" size="small" :disabled="busy" @click="openAdd" />
            </template>
        </PageTitle>

        <Message v-if="!canEdit" severity="info" :closable="false" size="small">{{ t('group.settingsReadOnly') }}</Message>

        <div v-if="loading && !data" class="grid grid-cols-1 items-start gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]" role="status" :aria-label="t('common.loading')">
            <AppGadget :title="t('group.categoriesTitle')">
                <div class="flex flex-col gap-2"><Skeleton v-for="n in 4" :key="n" height="3.25rem" border-radius="0.5rem" /></div>
            </AppGadget>
            <AppGadget :title="t('common.loading')">
                <div class="flex flex-col gap-3"><Skeleton height="2.25rem" width="min(28rem, 100%)" /><Skeleton height="6rem" /></div>
            </AppGadget>
        </div>

        <AppGadget v-else-if="!data" :title="t('group.categoriesTitle')">
            <ErrorState :title="t('group.categoriesLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load()" />
        </AppGadget>

        <AppGadget v-else-if="!data.categories.length" :title="t('group.categoriesTitle')">
            <EmptyState icon="pi pi-tags" :title="t('group.categoriesEmptyTitle')" :description="t('group.categoriesEmptyBody')">
                <Button v-if="canEdit" :label="t('group.categoryAdd')" icon="pi pi-plus" :disabled="busy" @click="openAdd" />
            </EmptyState>
        </AppGadget>

        <div v-else class="grid grid-cols-1 items-start gap-4 lg:grid-cols-[20rem_minmax(0,1fr)]" :aria-busy="busy">
            <!-- 분류에 안 든 소모임 게시판 — [할당하기]는 선택한 분류의 입력에 더한다 -->
            <section v-if="unassigned.length" class="rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 lg:col-span-2 dark:border-amber-800 dark:bg-amber-950/40" :aria-labelledby="unassignedId">
                <p :id="unassignedId" class="m-0 text-[13px] text-amber-900 dark:text-amber-200">
                    <b>{{ t('group.unassignedTitle', { n: unassigned.length }) }}</b> — {{ t('group.unassignedNote') }}
                </p>
                <ul class="m-0 mt-1.5 flex list-none flex-col gap-1 p-0">
                    <li v-for="board in unassigned" :key="board.boardId" class="flex flex-wrap items-center gap-x-3 gap-y-1 text-[13px]">
                        <span class="min-w-0 flex-1 basis-40 truncate font-medium">{{ board.name }} <small class="font-normal text-muted-color">/{{ board.slug }}</small></span>
                        <Button v-if="canEdit && current" type="button" size="small" severity="secondary" variant="outlined" :label="t('group.unassignedAssign')" :aria-label="t('group.unassignedAssignLabel', { board: board.name, category: current.name })" :disabled="busy" @click="addBoard(board.boardId)" />
                    </li>
                </ul>
            </section>

            <GroupCategoryList :categories="data.categories" :selected-id="selectedId" :busy="busy" :can-edit="canEdit" :order-error="orderError" @select="requestSelect" @move="move" />

            <GroupCategoryPanel
                v-if="current && draft"
                :key="current.id"
                :category="current"
                :name="draft.name"
                :name-error="nameError"
                :name-long="nameLong"
                :rows="rows"
                :candidates="candidates"
                :no-boards="data.available.length === 0"
                :board-total="boardTotal"
                :change-count="changeCount"
                :mixed="mixed"
                :can-save="canSave"
                :can-edit="canEdit"
                :can-board="can('board.read')"
                :saving="saving"
                :busy="busy"
                :boards-error="serverErrors.boardIds ?? ''"
                :save-error="saveError"
                :delete-error="deleteError"
                @update:name="setName"
                @add="addBoard"
                @remove="removeBoard"
                @save="save"
                @revert="revert"
                @delete="remove"
            />
        </div>

        <!-- 분류 추가 — 이름 하나만 받는다(이 화면 전용 대화상자) -->
        <Dialog v-model:visible="addOpen" modal :draggable="false" :closable="!creating" :header="t('group.categoryAddTitle')" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }">
            <form :id="`${uid}-add`" class="flex flex-col gap-3" novalidate @submit.prevent="create">
                <LabeledField :label="t('group.categoryNameLabel')" :hint="t('group.categoryNameHint')" :help="t('group.categoryAddHelp')" :error="addError">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="addName" fluid autocomplete="off" :placeholder="t('group.categoryAddPlaceholder')" :invalid="invalid" :readonly="creating" :aria-describedby="describedBy" aria-required="true" />
                    </template>
                </LabeledField>
            </form>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="creating" @click="addOpen = false" />
                <Button type="submit" :form="`${uid}-add`" :label="t('group.categoryAdd')" :loading="creating" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { GroupCategoriesResponse } from '~/composables/group/api/useGroupCategoryApi.types';
import UtilGroupCategory, { type GroupCategoryDraft } from '~/utils/util-group-category';
import UtilGroupMessages from '~/utils/util-group-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('group.categoriesTitle') });

const api = useGroupCategoryApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const uid = useId();
const unassignedId = `${uid}-unassigned`;

/** 만들기·저장·순서·삭제(정의: util-permission-catalog.ts) */
const canEdit = computed(() => can('group.settings'));

const data = ref<GroupCategoriesResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const selectedId = ref(UtilGroupCategory.parseQuery(route.query));
const draft = ref<GroupCategoryDraft | null>(null);
const saving = ref(false);
const ordering = ref(false);
const deleting = ref(false);
const busy = computed(() => saving.value || ordering.value || deleting.value || creating.value);

const saveError = ref('');
const orderError = ref('');
const deleteError = ref('');
const serverErrors = ref<{ name?: string; boardIds?: string }>({});

const current = computed(() => data.value?.categories.find((c) => c.id === selectedId.value) ?? null);
const rows = computed(() => (current.value && draft.value && data.value ? UtilGroupCategory.rows(current.value, draft.value, data.value.available) : []));
const candidates = computed(() => (draft.value && data.value ? UtilGroupCategory.candidates(data.value.available, draft.value) : []));
const unassigned = computed(() => (data.value ? UtilGroupCategory.unassigned(data.value.available, draft.value) : []));
const changeCount = computed(() => (current.value && draft.value ? UtilGroupCategory.changeCount(current.value, draft.value) : 0));
const boardTotal = computed(() => rows.value.filter((r) => r.state !== 'removed').length);
const mixed = computed(() => UtilGroupCategory.mixed(rows.value));
const nameLong = computed(() => Array.from(UtilGroupCategory.normalize(draft.value?.name ?? '')).length > UtilGroupCategory.NAME_SOFT);

/** 이름 문제 — 이름을 고쳤을 때만 보인다(저장된 이름을 그대로 두면 따지지 않는다) */
const nameProblem = computed(() => {
    if (!current.value || !draft.value || !data.value) return null;
    if (UtilGroupCategory.normalize(draft.value.name) === current.value.name) return null;
    return UtilGroupCategory.nameProblem(draft.value.name, data.value.categories, current.value.id);
});
const nameError = computed(() => (nameProblem.value ? UtilGroupMessages.nameProblem(t, nameProblem.value) : (serverErrors.value.name ?? '')));
const canSave = computed(() => canEdit.value && !busy.value && changeCount.value > 0 && !nameProblem.value);

onMounted(() => void load());

/**
 * 목록을 받는다 — 선택한 분류가 남아 있으면 그대로, 없으면 첫 분류. `keepDraft` 면 같은 분류의 입력을 지킨다(조용한 새로고침).
 */
async function load(keepDraft = false): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        apply(await api.list(), keepDraft);
    } catch (e) {
        if (!data.value) loadError.value = toApiError(e);
        else toast.error(UtilGroupMessages.failed(t, 'group.refreshFailed', e), 8000);
    } finally {
        loading.value = false;
    }
}

function apply(res: GroupCategoriesResponse, keepDraft = false): void {
    data.value = res;
    const exists = res.categories.some((c) => c.id === selectedId.value);
    if (!exists) selectedId.value = res.categories[0]?.id ?? '';
    const category = res.categories.find((c) => c.id === selectedId.value);
    if (!keepDraft || !draft.value || !exists) draft.value = category ? UtilGroupCategory.draftOf(category) : null;
    serverErrors.value = {};
}

// 선택한 분류를 주소에 싣는다(기록이 쌓이지 않게 replace)
watch(selectedId, (id) => {
    if (UtilGroupCategory.parseQuery(route.query) === id) return;
    void router.replace({ query: id ? { [UtilGroupCategory.QUERY_KEY]: id } : {} });
});

function select(id: string): void {
    const category = data.value?.categories.find((c) => c.id === id);
    if (!category) return;
    selectedId.value = id;
    draft.value = UtilGroupCategory.draftOf(category);
    serverErrors.value = {};
    saveError.value = '';
    deleteError.value = '';
}

/** 저장 전 변경이 있으면 버릴지 묻는다 */
async function confirmDiscard(): Promise<boolean> {
    if (changeCount.value === 0 || !current.value) return true;
    return confirm({ title: t('group.switchTitle'), message: t('group.switchBody', { name: current.value.name }), confirmLabel: t('group.switchConfirm'), cancelLabel: t('group.switchCancel'), tone: 'danger' });
}

async function requestSelect(id: string): Promise<void> {
    if (id === selectedId.value || busy.value) return;
    if (await confirmDiscard()) select(id);
}

/* ── 입력 ── */

function setName(name: string): void {
    if (!draft.value) return;
    draft.value = { ...draft.value, name };
    if (serverErrors.value.name) serverErrors.value = { ...serverErrors.value, name: undefined };
}

function addBoard(boardId: string): void {
    if (!draft.value || draft.value.boardIds.includes(boardId)) return;
    draft.value = { ...draft.value, boardIds: [...draft.value.boardIds, boardId] };
    serverErrors.value = { ...serverErrors.value, boardIds: undefined };
}

function removeBoard(boardId: string): void {
    if (!draft.value) return;
    draft.value = { ...draft.value, boardIds: draft.value.boardIds.filter((id) => id !== boardId) };
    serverErrors.value = { ...serverErrors.value, boardIds: undefined };
}

function revert(): void {
    if (current.value) select(current.value.id);
}

/* ── 저장 ── */

async function save(): Promise<void> {
    const category = current.value;
    if (!category || !draft.value || !canSave.value) return;
    saving.value = true;
    saveError.value = '';
    serverErrors.value = {};
    try {
        const name = UtilGroupCategory.normalize(draft.value.name);
        apply(await api.save(category.id, { name, boardIds: draft.value.boardIds, updatedAt: category.updatedAt }));
        toast.success(t('group.categorySaved', { name }));
    } catch (e) {
        if (UtilGroupCategory.isStale(e)) {
            await load();
            saveError.value = t('group.categoryStale');
        } else if (UtilGroupCategory.isGone(e)) {
            await load();
            toast.show(t('group.categoryGone'), { life: 5000 });
        } else {
            const fields = UtilGroupCategory.fieldErrorsOf(e);
            if (fields) serverErrors.value = fields;
            else saveError.value = `${t('group.categorySaveFailed')} — ${UtilGroupMessages.of(t, e)}`;
        }
    } finally {
        saving.value = false;
    }
}

/* ── 순서 — 누르면 바로 저장, 실패하면 원래 순서 ── */

async function move(id: string, step: -1 | 1): Promise<void> {
    if (!data.value || busy.value) return;
    const ids = UtilGroupCategory.moved(data.value.categories, id, step);
    if (!ids) return;
    ordering.value = true;
    orderError.value = '';
    try {
        apply(await api.order({ ids }), true);
        toast.success(t('group.categoryOrderSaved'));
    } catch {
        orderError.value = t('group.categoryOrderFailed');
    } finally {
        ordering.value = false;
    }
}

/* ── 삭제 ── */

async function remove(): Promise<void> {
    const category = current.value;
    if (!category || busy.value) return;
    deleteError.value = '';
    if (category.groupCount > 0) {
        deleteError.value = t('group.categoryDeleteBlocked', { n: category.groupCount });
        return;
    }
    const ok = await confirm({ title: t('group.categoryDeleteTitle', { name: category.name }), message: UtilGroupMessages.deleteBody(t, category.boardCount), confirmLabel: t('common.delete'), cancelLabel: t('common.cancel'), tone: 'danger' });
    if (!ok) return;
    deleting.value = true;
    try {
        await api.remove(category.id);
        selectedId.value = '';
        await load();
        toast.success(t('group.categoryDeleted', { name: category.name }));
    } catch (e) {
        const blocked = UtilGroupCategory.notEmptyCount(e);
        deleteError.value = blocked !== null ? t('group.categoryDeleteBlocked', { n: blocked }) : `${t('group.categoryDeleteFailed')} — ${UtilGroupMessages.of(t, e)}`;
    } finally {
        deleting.value = false;
    }
}

/* ── 분류 추가 ── */

const addOpen = ref(false);
const addName = ref('');
const addTried = ref(false);
const addServerError = ref('');
const creating = ref(false);

const addError = computed(() => {
    const problem = data.value ? UtilGroupCategory.nameProblem(addName.value, data.value.categories, null) : null;
    if (problem && (addTried.value || problem !== 'REQUIRED')) return UtilGroupMessages.nameProblem(t, problem);
    return addServerError.value;
});

watch(addName, () => (addServerError.value = ''));

async function openAdd(): Promise<void> {
    if (busy.value || !(await confirmDiscard())) return;
    revert();
    addName.value = '';
    addTried.value = false;
    addServerError.value = '';
    addOpen.value = true;
}

async function create(): Promise<void> {
    addTried.value = true;
    if (creating.value || !data.value || UtilGroupCategory.nameProblem(addName.value, data.value.categories, null)) return;
    creating.value = true;
    try {
        const created = await api.create({ name: UtilGroupCategory.normalize(addName.value) });
        addOpen.value = false;
        selectedId.value = created.id;
        await load();
        toast.success(t('group.categoryCreated', { name: created.name }));
    } catch (e) {
        addServerError.value = UtilGroupCategory.fieldErrorsOf(e)?.name ?? UtilGroupMessages.of(t, e);
    } finally {
        creating.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async (to) => {
    if (to.path === route.path || changeCount.value === 0) return true;
    return confirm({ title: t('group.leaveTitle'), message: t('group.leaveBody', { n: changeCount.value }), confirmLabel: t('group.leaveConfirm'), cancelLabel: t('group.leaveCancel'), tone: 'danger' });
});
</script>
