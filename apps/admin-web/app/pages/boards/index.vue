<template>
    <!--
        게시판 관리(목록) — 게시판을 만들고, 켜고 끄고, 순서를 정하고, 행을 눌러 게시판 설정(`/boards/[slug]`)으로 간다. community 관리자 앱 `boards/index.vue` 를 roboyam 에 맞게 옮겼다.
        표: 순서(위·아래) · 이름(+영문명)·slug · 종류(읽기 전용 배지: 일반/핫딜/직접홍보/소모임) · 목록 모양 · 글 수 · 매니저·스텝 수 · 기본값과 다른 설정 N개 · 사용 여부(켜고 끄기).
        community 와 다른 점:
          · **순서를 여기서 정한다** — community 는 순서의 주인이 메뉴 구성이라 게시판 순서 API 가 없었지만, roboyam 관리자 앱에는 메뉴 구성 화면이 없어 이 목록의 위·아래 단추로 정한다(가정). 누르면 바로 저장한다(`PUT …/order`, 모든 ID 의 새 순서).
          · 묶음(menuPath)·소모임 분류 칸이 없다(개념 없음). 「지킴이 수」 대신 매니저·스텝 수.
          · 게시판 만들기에서 고유 말머리를 함께 받지 않는다 — 만든 뒤 설정 화면의 말머리 구획에서 더한다(공통 말머리 4개는 서버가 만든다). 만들면 그 게시판 설정 화면으로 간다.
        끄면 그 게시판은 공개 사이트의 목록·검색·글 상세에서 모두 숨는다(글은 지워지지 않고 다시 켜면 돌아온다) — 끌 때만 확인을 묻는다.
        위쪽: 검색(이름·영문명·slug) · 사용 여부 · 기본값과 다른 것만(`?differing=1` 로 들어오면 켜진 채 시작 — 기본 설정 화면의 링크).
        권한: 조회 `board.read`(메뉴) · 만들기·켜고 끄기·순서 `board.manage` — 없으면 단추가 숨고 사용 여부는 글자 배지로만 보인다.
        로딩 · 빈 결과(게시판 없음 / 조건에 맞는 것 없음) · 오류를 모두 그린다. 서버 연결됨(`useBoardApi`) — 만들기의 이름·주소 중복은 409(`BOARD_NAME_DUPLICATE`·`BOARD_SLUG_DUPLICATE`) + `fields` 로 와 칸 아래에 붙는다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('board.listTitle')" :subtitle="t('board.listSubtitle')">
            <template v-if="canManage" #actions>
                <Button :label="t('board.create')" icon="pi pi-plus" size="small" @click="openCreate" />
            </template>
        </PageTitle>

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !boards" :title="t('board.listTitle')">
            <div class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 6" :key="n" height="2.75rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!boards" :title="t('board.listTitle')">
            <ErrorState :title="t('board.listLoadFailed')" :description="loadError" retry :retrying="loading" @retry="load" />
        </AppGadget>

        <AppGadget v-else-if="!boards.length" :title="t('board.listTitle')">
            <EmptyState icon="pi pi-th-large" :title="t('board.emptyTitle')" :description="t('board.emptyBody')">
                <Button v-if="canManage" :label="t('board.create')" icon="pi pi-plus" @click="openCreate" />
            </EmptyState>
        </AppGadget>

        <template v-else>
            <!-- 조건 -->
            <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
                <IconField class="w-full sm:w-64">
                    <InputIcon class="pi pi-search" />
                    <InputText v-model="keyword" size="small" fluid :placeholder="t('board.searchPlaceholder')" :aria-label="t('board.searchPlaceholder')" autocomplete="off" />
                </IconField>
                <SelectButton v-model="activeFilter" :options="activeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('board.filterActive')" />
                <div class="flex items-center gap-1.5">
                    <Checkbox v-model="differingOnly" binary :input-id="`${idBase}-differing`" />
                    <label :for="`${idBase}-differing`" class="cursor-pointer text-[13px]">{{ t('board.filterDiffering') }}</label>
                </div>
                <p class="ml-auto text-[13px] text-muted-color" aria-live="polite">{{ t('board.listCount', { n: filtered.length, total: boards.length }) }}</p>
            </div>

            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <DataTable :value="filtered" data-key="id" row-hover scrollable :loading="busy" class="min-w-0" :row-class="() => 'cursor-pointer'" @row-click="onRowClick">
                    <template #empty>
                        <EmptyState icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('board.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                        </EmptyState>
                    </template>

                    <Column :header="t('board.col.order')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <div v-if="canManage" class="flex items-center gap-0.5">
                                <Button :id="`${idBase}-${data.id}-up`" type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :aria-label="t('board.moveUp', { name: nameOf(data) })" :disabled="busy || !canMove(data, -1)" @click="move(data, -1)" />
                                <Button :id="`${idBase}-${data.id}-down`" type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :aria-label="t('board.moveDown', { name: nameOf(data) })" :disabled="busy || !canMove(data, 1)" @click="move(data, 1)" />
                            </div>
                            <span v-else class="text-[13px] tabular-nums text-muted-color">{{ data.sortOrder }}</span>
                        </template>
                    </Column>

                    <Column :header="t('board.col.name')" style="min-width: 14rem">
                        <template #body="{ data }">
                            <div class="flex min-w-0 items-center gap-2">
                                <span class="size-2.5 shrink-0 rounded-full" :class="data.color ? '' : 'border border-(--line)'" :style="data.color ? { background: UtilBoard.colorVar(data.color) ?? undefined } : undefined" aria-hidden="true" />
                                <div class="min-w-0">
                                    <div class="flex min-w-0 items-center gap-1.5">
                                        <NuxtLink :to="`/boards/${data.slug}`" class="truncate text-[13px] font-medium hover:underline">{{ localizedName(data.name, data.nameEn) }}</NuxtLink>
                                        <!-- 비회원이 쓸 수 있는 게시판 — 글·댓글 / 댓글만 -->
                                        <Tag v-if="guestLabelOf(data)" class="board-tag shrink-0" :value="guestLabelOf(data)" severity="info" icon="pi pi-globe" />
                                    </div>
                                    <span class="block truncate text-[11px] text-muted-color">
                                        <code>/{{ data.slug }}</code><template v-if="data.nameEn && locale !== 'en'"> · {{ data.nameEn }}</template>
                                    </span>
                                </div>
                            </div>
                        </template>
                    </Column>

                    <Column :header="t('board.col.kind')" style="width: 7rem">
                        <template #body="{ data }">
                            <BoardKindTag :kind="data.kind" />
                        </template>
                    </Column>

                    <Column :header="t('board.col.layout')" style="width: 7.5rem">
                        <template #body="{ data }">
                            <span class="inline-flex items-center gap-1.5 text-[13px]">
                                <i :class="UtilBoard.layoutIcon(data.layout)" class="text-xs text-muted-color" aria-hidden="true" />
                                {{ t(`board.layout.${data.layout}`) }}
                            </span>
                        </template>
                    </Column>

                    <Column :header="t('board.col.posts')" style="width: 6rem">
                        <template #body="{ data }">
                            <span class="block text-right text-[13px] tabular-nums">{{ data.postCount.toLocaleString(locale) }}</span>
                        </template>
                    </Column>

                    <Column :header="t('board.col.staff')" style="width: 9rem">
                        <template #body="{ data }">
                            <span class="text-[13px] tabular-nums" :class="data.managerCount + data.staffCount ? '' : 'text-muted-color'">{{ t('board.staffCounts', { managers: data.managerCount, staff: data.staffCount }) }}</span>
                        </template>
                    </Column>

                    <Column :header="t('board.col.diff')" style="width: 8rem">
                        <template #body="{ data }">
                            <Tag v-if="data.diffCount" class="board-tag" :value="t('board.diffCount', { n: data.diffCount })" severity="warn" />
                            <span v-else class="text-[12px] text-muted-color">{{ t('board.followsDefaults') }}</span>
                        </template>
                    </Column>

                    <Column :header="t('board.col.active')" style="width: 6.5rem">
                        <template #body="{ data }">
                            <ToggleSwitch v-if="canManage" :model-value="data.active" :disabled="busy" :aria-label="t('board.toggleActive', { name: nameOf(data) })" @update:model-value="(value: boolean) => setActive(data, value)" />
                            <Tag v-else class="board-tag" :value="t(data.active ? 'board.active' : 'board.inactive')" :severity="data.active ? 'success' : 'secondary'" />
                        </template>
                    </Column>
                </DataTable>
            </div>
            <p class="text-xs text-muted-color">{{ t('board.orderNote') }}</p>
        </template>

        <!-- 게시판 만들기 — 이 화면 전용 대화상자 -->
        <Dialog v-model:visible="createOpen" modal :draggable="false" :header="t('board.createTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
            <form :id="`${idBase}-create`" class="flex flex-col gap-4" novalidate @submit.prevent="create">
                <LabeledField :label="t('board.field.name')" :hint="t('board.nameHint', { max: UtilBoard.NAME_MAX })" :error="createErrorOf('name')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputText :id="id" v-model="createForm.name" name="name" :disabled="creating" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" fluid />
                    </template>
                </LabeledField>

                <LabeledField :label="t('board.field.slug')" :hint="t('board.slugHint')" :help="t('board.slugHelp')" :error="createErrorOf('slug')">
                    <template #default="{ id, describedBy, invalid }">
                        <InputGroup>
                            <InputGroupAddon>/</InputGroupAddon>
                            <InputText :id="id" v-model="createForm.slug" name="slug" :disabled="creating" :invalid="invalid" :aria-describedby="describedBy" :aria-required="true" autocomplete="off" autocapitalize="off" spellcheck="false" />
                        </InputGroup>
                    </template>
                </LabeledField>

                <LabeledField :label="t('board.field.description')" :hint="t('board.descHint', { max: UtilBoard.DESC_MAX })" :error="createErrorOf('description')">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="createForm.description" name="description" rows="2" auto-resize :disabled="creating" :invalid="invalid" :aria-describedby="describedBy" fluid />
                    </template>
                </LabeledField>

                <div class="flex flex-col gap-1.5">
                    <span :id="`${idBase}-layout`" class="text-sm font-medium">{{ t('board.field.layout') }}</span>
                    <SelectButton v-model="createForm.layout" :options="layoutOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :disabled="creating" :aria-labelledby="`${idBase}-layout`" class="self-start" />
                </div>

                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('board.createNote') }}</p>
                <Message v-if="createError" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ createError }}</Message>
            </form>

            <template #footer>
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="creating" @click="createOpen = false" />
                <Button type="submit" :form="`${idBase}-create`" :label="t('board.createSubmit')" :loading="creating" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { DataTableRowClickEvent } from 'primevue/datatable';
import type { BoardCreateRequest, BoardLayout, BoardListItem } from '~/composables/board/api/useBoardApi.types';
import { BOARD_MIN_GUEST } from '~/composables/board/api/useBoardApi.types';
import type { BoardActiveFilter, BoardTextProblem } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('board.listTitle') });

const api = useBoardApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const idBase = useId();

const canManage = computed(() => can('board.manage'));

const boards = ref<BoardListItem[] | null>(null);
const loading = ref(false);
const loadError = ref('');
/** 줄 하나를 바꾸는 중(순서·켜고 끄기) */
const busy = ref(false);

const keyword = ref('');
const activeFilter = ref<BoardActiveFilter>('ALL');
const differingOnly = ref(route.query.differing === '1');

const activeOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'ACTIVE', label: t('board.active') },
    { value: 'INACTIVE', label: t('board.inactive') },
]);
const layoutOptions = computed(() => UtilBoard.LAYOUTS.map((value) => ({ value, label: t(`board.layout.${value}`) })));

const filtered = computed(() => UtilBoard.filterBoards(boards.value ?? [], keyword.value, activeFilter.value, differingOnly.value));

onMounted(load);

async function load() {
    loading.value = true;
    loadError.value = '';
    try {
        boards.value = (await api.list()).boards;
    } catch (e) {
        loadError.value = UtilBoardMessages.of(t, e);
    } finally {
        loading.value = false;
    }
}

/** 비회원 딱지 — 글쓰기가 비회원 포함이면 「비회원 글·댓글」(댓글도 비회원이면), 댓글만이면 「비회원 댓글」 */
function guestLabelOf(board: BoardListItem): string {
    const post = board.writeMin === BOARD_MIN_GUEST;
    const comment = board.commentMin === BOARD_MIN_GUEST;
    if (post && comment) return t('board.guestBoth');
    if (post) return t('board.guestPost');
    if (comment) return t('board.guestComment');
    return '';
}

function nameOf(board: BoardListItem): string {
    return localizedName(board.name, board.nameEn);
}

function clearFilter() {
    keyword.value = '';
    activeFilter.value = 'ALL';
    differingOnly.value = false;
}

/** 행을 누르면 설정 화면으로 — 줄 안의 단추·스위치·링크를 누른 것은 그 동작만 한다 */
function onRowClick(event: DataTableRowClickEvent) {
    const target = event.originalEvent.target as HTMLElement | null;
    if (target?.closest('button, a, input, [role="switch"], .p-toggleswitch')) return;
    void router.push(`/boards/${(event.data as BoardListItem).slug}`);
}

function canMove(board: BoardListItem, step: -1 | 1): boolean {
    return UtilBoard.orderAfterMove(boards.value ?? [], board.id, step) !== null;
}

/** 한 칸 옮기고 바로 저장한다 — 끝 판정은 걸러진 목록이 아니라 전체 목록 기준. 옮긴 뒤에도 키보드 초점이 그 단추에 남게 한다 */
async function move(board: BoardListItem, step: -1 | 1) {
    const ids = UtilBoard.orderAfterMove(boards.value ?? [], board.id, step);
    if (!ids || busy.value) return;
    busy.value = true;
    try {
        boards.value = (await api.order({ ids })).boards;
        toast.success(t('board.orderSaved'));
        await nextTick();
        const first = document.getElementById(`${idBase}-${board.id}-${step === -1 ? 'up' : 'down'}`) as HTMLButtonElement | null;
        const other = document.getElementById(`${idBase}-${board.id}-${step === -1 ? 'down' : 'up'}`) as HTMLButtonElement | null;
        (first && !first.disabled ? first : other)?.focus();
    } catch (e) {
        toast.error(`${t('board.orderFailed')} — ${UtilBoardMessages.of(t, e)}`, 8000);
    } finally {
        busy.value = false;
    }
}

/** 켜고 끄기 — 끌 때만 묻는다(공개 사이트에서 모두 숨는다) */
async function setActive(board: BoardListItem, active: boolean) {
    if (busy.value) return;
    if (!active) {
        const ok = await confirm({ title: t('board.deactivateTitle', { name: nameOf(board) }), message: t('board.deactivateMessage'), confirmLabel: t('board.deactivateConfirm'), cancelLabel: t('common.cancel'), tone: 'danger' });
        if (!ok) return;
    }
    busy.value = true;
    try {
        const updated = await api.setActive(board.id, active);
        boards.value = (boards.value ?? []).map((b) => (b.id === updated.id ? updated : b));
        toast.success(t(active ? 'board.activated' : 'board.deactivated', { name: nameOf(updated) }));
    } catch (e) {
        toast.error(`${t('board.activeFailed')} — ${UtilBoardMessages.of(t, e)}`, 8000);
    } finally {
        busy.value = false;
    }
}

// ── 게시판 만들기 ─

const createOpen = ref(false);
const creating = ref(false);
const createSubmitted = ref(false);
const createError = ref('');
const createServerFields = ref<Record<string, string>>({});
const createForm = reactive<BoardCreateRequest>({ name: '', slug: '', description: '', layout: 'LIST' as BoardLayout });

function openCreate() {
    Object.assign(createForm, { name: '', slug: '', description: '', layout: 'LIST' });
    createSubmitted.value = false;
    createError.value = '';
    createServerFields.value = {};
    createOpen.value = true;
}

/** 칸마다의 문제 — 화면이 아는 목록으로 이름·slug 중복까지 미리 본다(서버가 다시 본다) */
const createProblems = computed<Partial<Record<'name' | 'slug' | 'description', BoardTextProblem>>>(() => {
    const list = boards.value ?? [];
    const out: Partial<Record<'name' | 'slug' | 'description', BoardTextProblem>> = {};
    const name = UtilBoard.nameProblem(createForm.name, list.map((b) => b.name));
    const slug = UtilBoard.slugProblem(createForm.slug, list.map((b) => b.slug));
    const description = UtilBoard.descProblem(createForm.description);
    if (name) out.name = name;
    if (slug) out.slug = slug;
    if (description) out.description = description;
    return out;
});

function createErrorOf(field: 'name' | 'slug' | 'description'): string {
    const problem = createProblems.value[field];
    if (problem && (createSubmitted.value || problem.endsWith('DUPLICATE') || problem === 'SLUG_FORMAT' || problem === 'SLUG_RESERVED')) return UtilBoardMessages.problem(t, problem);
    return createServerFields.value[field] ?? '';
}

async function create() {
    createSubmitted.value = true;
    if (creating.value || Object.keys(createProblems.value).length) return;
    creating.value = true;
    createError.value = '';
    createServerFields.value = {};
    try {
        const created = await api.create({ name: createForm.name.trim(), slug: createForm.slug.trim(), description: createForm.description.trim(), layout: createForm.layout });
        createOpen.value = false;
        toast.success(t('board.created', { name: created.name }));
        await router.push(`/boards/${created.slug}`);
    } catch (e) {
        const rejected = UtilBoard.fieldErrorsOf(e);
        if (rejected) createServerFields.value = rejected.fields;
        createError.value = UtilBoardMessages.of(t, e);
    } finally {
        creating.value = false;
    }
}
</script>

<style scoped>
/* 뱃지 글자 상하 중앙(`components.md` 2-4) */
.board-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    line-height: 1;
}
.board-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
}
</style>
