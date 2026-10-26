<template>
    <!--
        게시판 관리(목록) — 게시판을 만들고, 켜고 끄고, 순서를 정하고, 행을 눌러 게시판 설정(`/boards/[slug]`)으로 간다. community 관리자 앱 `boards/index.vue` 를 roboyam 에 맞게 옮겼다.
        표: 순서(위·아래) · 이름(+영문명)·slug · 종류(읽기 전용 배지: 일반/핫딜/직접홍보/소모임) · 목록 모양 · 글 수 · 매니저·스텝 수 · 기본값과 다른 설정 N개 · 사용 여부(켜고 끄기).
        **소모임 게시판**(종류 `GROUP` — 메뉴·게시판 페이지가 없고 소모임 글을 담는다, community ADM-006·ADM-007):
          · 표 맨 아래 별도 묶음(노란 머리 「소모임 게시판 · 메뉴·게시판 페이지가 없어요」). 소모임 게시판이 있으면 일반 게시판 위에도 「일반 게시판」 묶음 머리가 선다.
          · 그 줄에 분류 알약(누르면 `/group-categories?category=ID`, `group.read` 가 없으면 글자만) 또는 회색 「분류 없음 · 「전체」에서만 보여요」. 일반 게시판 줄에는 분류 표시가 없다.
          · 순서(▲▼)는 일반 게시판끼리만 — `PUT …/order` 는 모든 ID 를 받으므로 일반 게시판의 새 순서 뒤에 소모임 게시판을 지금 순서대로 붙여 보낸다(`UtilBoard.orderAfterMove`).
          · community 의 「소모임 N개」 숫자·끌 때 소모임 경고는 없다(소모임 서버가 아직 없다).
        community 와 다른 점:
          · **게시판 목록 순서를 여기서 정한다**(`PUT …/order`, 모든 ID 의 새 순서 — 누르면 바로 저장). 이 순서는 게시판 목록·글쓰기 게시판 고르기에 쓰인다.
            공개 사이트 내비게이션 순서의 주인은 메뉴(`main` 그룹, `/menus/public`)다 — 표 아래 안내에 그 화면 링크를 둔다(`layout.read` 가 있을 때만). 새 일반 게시판은 서버가 `main` 그룹의 자동 추가 폴더 끝에 넣는다.
          · 메뉴 폴더 묶음(menuPath)이 없다(개념 없음). 「지킴이 수」 대신 매니저·스텝 수.
          · 게시판 만들기에서 고유 말머리를 함께 받지 않는다 — 만든 뒤 설정 화면의 말머리 구획에서 더한다(공통 말머리 4개는 서버가 만든다). 만들면 그 게시판 설정 화면으로 간다.
        게시판 만들기(이 화면 전용 대화상자): 맨 위 종류 카드 둘(일반 · 소모임, 기본 일반). 소모임이면 목록 모양 칸이 없고(요청은 `LIST`) 소모임 분류 칸(선택, 기본 「분류 없음」, 분류 sortOrder 순)과 안내 줄이 나온다.
          분류 칸은 `group.read` 가 없으면 숨고, `group.settings` 가 없으면 「분류 없음」에 고정된다(`groupCategoryId` 를 싣지 않는다). 분류가 0개면 상자 대신 분류 화면 링크, 목록을 못 받아도 「분류 없음」으로 만들 수 있다.
          `?create=group&category=<ID>` 로 들어오면(소모임 분류 화면의 [소모임 게시판 만들기]) 종류가 소모임으로 고정되고 그 분류가 미리 골라진 채 열린다(주소의 두 칸은 지운다). 「소모임 게시판」 칩을 켠 채 열면 종류만 소모임으로 미리 고른다.
        끄면 그 게시판은 공개 사이트의 목록·검색·글 상세에서 모두 숨는다(글은 지워지지 않고 다시 켜면 돌아온다) — 끌 때만 확인을 묻는다.
        위쪽: 검색(이름·영문명·slug) · 사용 여부 · 기본값과 다른 것만(`?differing=1` 로 들어오면 켜진 채 시작 — 기본 설정 화면의 링크) · 「소모임 게시판 N」 칩(`?filter=group` 으로 들어오면 켜진 채 시작, 숫자 = 소모임 게시판 수).
        권한: 조회 `board.read`(메뉴) · 만들기·켜고 끄기·순서 `board.manage` — 없으면 단추가 숨고 사용 여부는 글자 배지로만 보인다. 소모임 분류 보기 `group.read` · 고르기 `group.settings`.
        로딩 · 빈 결과(게시판 없음 / 조건에 맞는 것 없음 / 소모임 게시판 없음) · 오류를 모두 그린다. 서버 연결됨(`useBoardApi` · `useGroupCategoryApi`) — 만들기의 이름·주소 중복은 409(`BOARD_NAME_DUPLICATE`·`BOARD_SLUG_DUPLICATE`) + `fields` 로 와 칸 아래에 붙는다(`fields.kind`·`fields.groupCategoryId` 도 그 칸 아래).
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('board.listTitle')" :subtitle="t('board.listSubtitle')">
            <template v-if="canManage" #actions>
                <Button :label="t('board.create')" icon="pi pi-plus" size="small" @click="openCreateFromList" />
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
                <Button v-if="canManage" :label="t('board.create')" icon="pi pi-plus" @click="openCreate()" />
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
                <!-- 소모임 게시판만 — 숫자는 종류가 소모임인 게시판 수(다른 조건과 무관) -->
                <Button type="button" size="small" rounded icon="pi pi-users" :label="t('board.filterGroup', { n: groupBoardCount })" :severity="groupOnly ? undefined : 'secondary'" :variant="groupOnly ? undefined : 'outlined'" :aria-pressed="groupOnly" @click="groupOnly = !groupOnly" />
                <p class="ml-auto text-[13px] text-muted-color" aria-live="polite">{{ t('board.listCount', { n: filtered.length, total: boards.length }) }}</p>
            </div>

            <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)">
                <!-- 소모임 게시판이 줄에 있으면 묶음 머리(일반 · 소모임)를 세운다 — 걸러진 줄은 일반 게시판 뒤에 소모임 게시판(`UtilBoard.filterBoards`) -->
                <DataTable
                    :value="filtered"
                    data-key="id"
                    row-hover
                    scrollable
                    :loading="busy"
                    class="min-w-0"
                    :row-group-mode="hasGroupRows ? 'subheader' : undefined"
                    group-rows-by="section"
                    :row-class="() => 'cursor-pointer'"
                    @row-click="onRowClick"
                >
                    <template #groupheader="{ data }">
                        <div
                            class="flex flex-wrap items-baseline gap-x-3 gap-y-0.5 rounded-lg border px-2.5 py-1.5"
                            :class="data.section === 'GROUP' ? 'border-amber-300 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/40' : 'border-(--line) bg-surface-100 dark:bg-surface-800'"
                        >
                            <span class="text-[13px] font-semibold">{{ t(data.section === 'GROUP' ? 'board.sectionGroup' : 'board.sectionGeneral') }}</span>
                            <small class="text-xs text-muted-color">{{ t(data.section === 'GROUP' ? 'board.sectionGroupNote' : 'board.sectionGeneralNote') }}</small>
                        </div>
                    </template>

                    <template #empty>
                        <!-- 소모임 게시판 칩을 켰는데 소모임 게시판이 하나도 없다 -->
                        <EmptyState v-if="groupOnly && !groupBoardCount" icon="pi pi-users" :title="t('board.groupEmptyTitle')" :description="t('board.groupEmptyBody')">
                            <div class="flex flex-wrap justify-center gap-2">
                                <Button v-if="canManage" type="button" :label="t('board.create')" icon="pi pi-plus" size="small" @click="openCreate({ kind: 'GROUP' })" />
                                <Button type="button" :label="t('board.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                            </div>
                        </EmptyState>
                        <EmptyState v-else icon="pi pi-filter-slash" :title="t('common.emptyFiltered')">
                            <Button type="button" :label="t('board.clearFilter')" size="small" severity="secondary" variant="outlined" @click="clearFilter" />
                        </EmptyState>
                    </template>

                    <Column :header="t('board.col.order')" style="width: 5.5rem">
                        <template #body="{ data }">
                            <!-- 소모임 게시판은 순서를 옮기지 않는다(일반 게시판끼리만) -->
                            <span v-if="data.section === 'GROUP'" aria-hidden="true" />
                            <div v-else-if="canManage" class="flex items-center gap-0.5">
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
                                        <!-- 소모임 게시판의 분류 — 알약(분류 화면 링크, `group.read` 가 없으면 글자만) 또는 회색 「분류 없음」 -->
                                        <template v-if="data.section === 'GROUP'">
                                            <template v-if="data.groupCategoryId">
                                                <NuxtLink v-if="canReadGroup" :to="{ path: '/group-categories', query: { category: data.groupCategoryId } }" class="inline-flex shrink-0 no-underline hover:opacity-80" :aria-label="t('board.categoryLinkLabel', { name: data.groupCategoryName ?? '' })">
                                                    <Tag class="board-tag" :value="data.groupCategoryName ?? ''" severity="secondary" icon="pi pi-tags" />
                                                </NuxtLink>
                                                <span v-else class="shrink-0 text-[12px] text-muted-color">{{ data.groupCategoryName }}</span>
                                            </template>
                                            <template v-else>
                                                <Tag class="board-tag shrink-0" :value="t('board.categoryNone')" severity="secondary" />
                                                <span class="shrink-0 text-[11px] text-muted-color">{{ t('board.categoryNoneNote') }}</span>
                                            </template>
                                        </template>
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
            <p class="text-xs text-muted-color">
                {{ t('board.orderNote') }}
                <NuxtLink v-if="canReadMenu" to="/menus/public" class="ml-1 whitespace-nowrap text-primary hover:underline">{{ t('board.orderMenuLink') }}<i class="pi pi-arrow-right ml-1 text-[10px]" aria-hidden="true" /></NuxtLink>
            </p>
        </template>

        <!-- 게시판 만들기 — 이 화면 전용 대화상자 -->
        <Dialog v-model:visible="createOpen" modal :draggable="false" :header="t('board.createTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
            <form :id="`${idBase}-create`" class="flex flex-col gap-4" novalidate @submit.prevent="create">
                <!-- 종류 — 맨 위. 일반 | 소모임, 기본 일반. 소모임 분류 화면에서 열면 소모임 하나만 보이고 바꿀 수 없다 -->
                <fieldset class="m-0 min-w-0 border-0 p-0" :aria-describedby="`${idBase}-kind-help`">
                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('board.createKindLabel') }}</legend>
                    <div class="grid grid-cols-1 gap-2" :class="kindOptions.length > 1 ? 'sm:grid-cols-2' : ''">
                        <label
                            v-for="option in kindOptions"
                            :key="option.value"
                            :for="`${idBase}-kind-${option.value}`"
                            class="flex items-start gap-2 rounded-lg border px-3 py-2.5 text-sm"
                            :class="[createKind === option.value ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', creating || kindLocked ? 'cursor-not-allowed' : 'cursor-pointer']"
                        >
                            <RadioButton v-model="createKind" :value="option.value" :input-id="`${idBase}-kind-${option.value}`" :name="`${idBase}-kind`" :disabled="creating || kindLocked" class="mt-0.5" />
                            <span class="flex min-w-0 flex-col">
                                <span :class="createKind === option.value ? 'font-semibold' : ''">{{ option.label }}</span>
                                <small class="text-xs text-muted-color">{{ option.note }}</small>
                            </span>
                        </label>
                    </div>
                    <p v-if="kindLocked" class="m-0 mt-1.5 text-xs text-muted-color">{{ t('board.createKindFixed') }}</p>
                    <p :id="`${idBase}-kind-help`" class="m-0 mt-1.5 text-xs leading-relaxed text-muted-color">{{ t(`board.createKindHelp.${createKind}`) }}</p>
                    <p v-if="createServerFields.kind" role="alert" class="m-0 mt-1 text-xs text-red-600 dark:text-red-400">{{ createServerFields.kind }}</p>
                </fieldset>

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

                <!-- 소모임 분류 — 소모임 게시판일 때만, 선택. 고르면 만들면서 그 분류에 할당된다. 목록을 못 받아도 「분류 없음」으로 만들 수 있다 -->
                <LabeledField v-if="createIsGroup && canReadGroup" :label="t('board.createCategoryLabel')" :hint="t('board.createCategoryRange')" :help="categoryHelp" :error="createServerFields.groupCategoryId ?? ''">
                    <template #default="{ id, describedBy, invalid }">
                        <!-- 고를 권한(`group.settings`)이 없다 — 「분류 없음」에 고정 -->
                        <Select v-if="!canPickCategory" :input-id="id" :model-value="NO_CATEGORY" :options="noCategoryOption" option-label="label" option-value="value" disabled fluid :aria-describedby="describedBy" />
                        <Skeleton v-else-if="categoryStatus === 'idle' || categoryStatus === 'loading'" height="2.25rem" border-radius="0.375rem" role="status" :aria-label="t('board.createCategoryLoading')" />
                        <div v-else-if="categoryStatus === 'error'" class="flex flex-wrap items-center gap-2 rounded-lg border border-(--line) p-3 text-xs text-muted-color">
                            <span class="min-w-0 flex-1">{{ t('board.createCategoryFailed') }}</span>
                            <Button type="button" :label="t('board.createCategoryRetry')" size="small" severity="secondary" variant="outlined" :disabled="creating" @click="loadCategories" />
                        </div>
                        <p v-else-if="!categories.length" class="m-0 text-[13px] text-muted-color">
                            {{ t('board.createCategoryEmpty') }}
                            <NuxtLink to="/group-categories" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('board.createCategoryEmptyLink') }} ›</NuxtLink>
                        </p>
                        <Select v-else v-model="createCategoryId" :input-id="id" :options="categoryOptions" option-label="label" option-value="value" fluid :invalid="invalid" :disabled="creating" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <!-- 목록 모양 — 일반 게시판만(소모임 게시판은 게시판 페이지가 없다) -->
                <div v-if="!createIsGroup" class="flex flex-col gap-1.5">
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
import type { BoardCreateKind, BoardCreateRequest, BoardLayout, BoardListItem } from '~/composables/board/api/useBoardApi.types';
import { BOARD_MIN_GUEST } from '~/composables/board/api/useBoardApi.types';
import type { GroupCategoryItem } from '~/composables/group/api/useGroupCategoryApi.types';
import type { BoardActiveFilter, BoardTextProblem } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilBoardMessages from '~/utils/util-board-messages';

const { t, locale } = useI18n();
useSeoMeta({ title: () => t('board.listTitle') });

const api = useBoardApi();
const groupCategoryApi = useGroupCategoryApi();
const route = useRoute();
const router = useRouter();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();
const idBase = useId();

const canManage = computed(() => can('board.manage'));
/** 공개 사이트 메뉴(순서의 주인) 링크 — 메뉴 관리 조회 권한이 있을 때만 */
const canReadMenu = computed(() => can('layout.read'));
/** 소모임 분류 보기(분류 알약 링크 · 만들기의 분류 칸) */
const canReadGroup = computed(() => can('group.read'));
/** 만들기에서 소모임 분류 고르기 — `groupCategoryId` 를 보내려면 서버가 `board.manage` + `group.settings` 를 본다 */
const canPickCategory = computed(() => canReadGroup.value && can('group.settings'));

const boards = ref<BoardListItem[] | null>(null);
const loading = ref(false);
const loadError = ref('');
/** 줄 하나를 바꾸는 중(순서·켜고 끄기) */
const busy = ref(false);

const keyword = ref('');
const activeFilter = ref<BoardActiveFilter>('ALL');
const differingOnly = ref(route.query.differing === '1');
/** 소모임 게시판만 — `?filter=group` 으로 들어오면 켜진 채 시작 */
const groupOnly = ref(route.query.filter === 'group');

const activeOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'ACTIVE', label: t('board.active') },
    { value: 'INACTIVE', label: t('board.inactive') },
]);
const layoutOptions = computed(() => UtilBoard.LAYOUTS.map((value) => ({ value, label: t(`board.layout.${value}`) })));

/** 표의 한 줄 — 묶음(일반 · 소모임)을 함께 싣는다(DataTable 묶음 머리의 기준) */
type BoardRow = BoardListItem & { section: 'GENERAL' | 'GROUP' };

const filtered = computed<BoardRow[]>(() =>
    UtilBoard.filterBoards(boards.value ?? [], keyword.value, activeFilter.value, differingOnly.value, groupOnly.value).map((board) => ({ ...board, section: UtilBoard.isGroup(board) ? 'GROUP' : 'GENERAL' })),
);
/** 걸러진 줄에 소모임 게시판이 있다 — 묶음 머리를 세운다 */
const hasGroupRows = computed(() => filtered.value.some((row) => row.section === 'GROUP'));
/** 칩 숫자 — 종류가 소모임인 게시판 수(다른 조건과 무관) */
const groupBoardCount = computed(() => (boards.value ?? []).filter((board) => UtilBoard.isGroup(board)).length);

onMounted(() => {
    void load();
    openFromQuery();
});

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
    groupOnly.value = false;
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

/** 종류 — 일반 | 소모임. 소모임 분류 화면에서 열면 소모임에 고정(`kindLocked`) */
const createKind = ref<BoardCreateKind>('GENERAL');
const kindLocked = ref(false);
const createIsGroup = computed(() => createKind.value === 'GROUP');
const CREATE_KINDS: readonly BoardCreateKind[] = ['GENERAL', 'GROUP'];
const kindOptions = computed(() =>
    CREATE_KINDS.filter((value) => !kindLocked.value || value === createKind.value).map((value) => ({ value, label: t(`board.createKind.${value}`), note: t(`board.createKindNote.${value}`) })),
);

/** 선택 상자의 「분류 없음」 값 — 빈 글·null 을 선택지 값으로 쓰면 PrimeVue 가 「고른 것 없음」으로 읽는다 */
const NO_CATEGORY = 'none';
const createCategoryId = ref(NO_CATEGORY);
const categories = ref<GroupCategoryItem[]>([]);
const categoryStatus = ref<'idle' | 'loading' | 'ready' | 'error'>('idle');
/** 늦게 온 앞 요청의 답을 버린다 */
let categoryTicket = 0;
const noCategoryOption = computed(() => [{ value: NO_CATEGORY, label: t('board.createCategoryNone') }]);
const categoryOptions = computed(() => [...noCategoryOption.value, ...[...categories.value].sort((a, b) => a.sortOrder - b.sortOrder).map((c) => ({ value: c.id, label: c.name }))]);
/** 분류 칸 안내 — 고를 수 없으면 권한 안내, 고를 수 있을 때만 뜻 안내(자리표시·오류·「분류가 없어요」와 겹치지 않게) */
const categoryHelp = computed(() => {
    if (!canPickCategory.value) return t('board.createCategoryNoPermission');
    return categoryStatus.value === 'ready' && categories.value.length > 0 ? t('board.createCategoryHelp') : '';
});

interface CreatePreset {
    kind?: BoardCreateKind;
    /** 종류를 바꿀 수 없다(소모임 분류 화면에서 열 때) */
    lockKind?: boolean;
    /** 미리 고를 소모임 분류 — 목록에 없으면 「분류 없음」 */
    categoryId?: string;
}

function openCreate(preset: CreatePreset = {}) {
    Object.assign(createForm, { name: '', slug: '', description: '', layout: 'LIST' });
    createKind.value = preset.kind ?? 'GENERAL';
    kindLocked.value = preset.lockKind ?? false;
    createCategoryId.value = canPickCategory.value && preset.categoryId ? preset.categoryId : NO_CATEGORY;
    categoryTicket++;
    categories.value = [];
    categoryStatus.value = 'idle';
    createSubmitted.value = false;
    createError.value = '';
    createServerFields.value = {};
    createOpen.value = true;
    if (createKind.value === 'GROUP') void loadCategories();
}

/** 머리·빈 화면의 [게시판 만들기] — 「소모임 게시판」 칩을 켠 채면 종류만 소모임으로 미리 고른다(바꿀 수 있다) */
function openCreateFromList() {
    openCreate(groupOnly.value ? { kind: 'GROUP' } : {});
}

/** `?create=group&category=<ID>`(소모임 분류 화면의 [소모임 게시판 만들기]) — 종류 소모임 고정, 그 분류를 미리 고른 채 연다. 새로고침에 다시 열리지 않게 주소의 두 칸은 지운다 */
function openFromQuery() {
    if (route.query.create !== 'group') return;
    const categoryId = typeof route.query.category === 'string' ? route.query.category : '';
    const query = { ...route.query };
    delete query.create;
    delete query.category;
    void router.replace({ query });
    if (canManage.value) openCreate({ kind: 'GROUP', lockKind: true, categoryId: categoryId || undefined });
}

/** 소모임 분류 목록 — 고를 권한이 있을 때만, 창을 열 때마다 새로 받는다. 미리 고른 분류가 목록에 없으면 「분류 없음」 */
async function loadCategories() {
    if (!canPickCategory.value) return;
    const ticket = ++categoryTicket;
    categoryStatus.value = 'loading';
    try {
        const res = await groupCategoryApi.list();
        if (ticket !== categoryTicket) return;
        categories.value = res.categories;
        categoryStatus.value = 'ready';
        if (createCategoryId.value !== NO_CATEGORY && !res.categories.some((c) => c.id === createCategoryId.value)) createCategoryId.value = NO_CATEGORY;
    } catch {
        if (ticket === categoryTicket) categoryStatus.value = 'error';
    }
}

// 창을 연 뒤 소모임을 처음 고르면 분류 목록을 받는다
watch(createKind, (kind) => {
    if (createOpen.value && kind === 'GROUP' && categoryStatus.value === 'idle') void loadCategories();
    if (createServerFields.value.kind) createServerFields.value = { ...createServerFields.value, kind: '' };
});
watch(createCategoryId, () => {
    if (createServerFields.value.groupCategoryId) createServerFields.value = { ...createServerFields.value, groupCategoryId: '' };
});

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
        const isGroup = createIsGroup.value;
        const body: BoardCreateRequest = {
            name: createForm.name.trim(),
            slug: createForm.slug.trim(),
            description: createForm.description.trim(),
            // 소모임 게시판은 게시판 페이지가 없어 목록 모양 칸을 보이지 않는다 — 기본값을 보낸다
            layout: isGroup ? 'LIST' : createForm.layout,
            kind: createKind.value,
        };
        if (isGroup && canPickCategory.value && createCategoryId.value !== NO_CATEGORY) body.groupCategoryId = createCategoryId.value;
        const created = await api.create(body);
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
