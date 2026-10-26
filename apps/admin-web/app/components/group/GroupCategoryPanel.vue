<template>
    <!--
        소모임 분류 화면의 오른쪽 패널 — 선택한 분류 하나(community `GroupCategoryPanel`).
        ① 이름 + 머리 오른쪽 [분류 삭제] ② 추가할 소모임 게시판 고르기(선택 상자 + [추가]; 소모임 게시판이 하나도 없으면 안내 + 게시판 관리 링크) ③ 추가한 게시판 목록(이름·주소·딱지·소모임 수·[빼기]/[되돌리기])
        ④ 안내(옮기기·빼기·꺼짐·투자 고지 섞임 — 막지 않고 알린다) ⑤ 저장 줄(선택한 분류 단위 — 이름 + 게시판 할당). 설정 권한이 없으면 입력이 잠기고 단추·저장 줄이 없다.
        [소모임 게시판 만들기](`board.manage`): 게시판 관리의 만들기 대화상자로 간다(`/boards?create=group&category=<이 분류>` — 종류 소모임 고정, 이 분류가 미리 골라진 채). community 는 이 화면에서 같은 대화상자를 바로 연다.
        `board.manage` 가 없고 `board.read` 만 있으면 게시판 관리(`/boards`) 링크.
        서버를 부르지 않는다 — 값과 일은 부르는 쪽(페이지)이 들고 이벤트로 올린다.
    -->
    <AppGadget :title="category.name" :changes="changeCount" :invalid="nameError || boardsError ? 1 : 0">
        <template v-if="canEdit" #actions>
            <Button type="button" size="small" severity="danger" variant="outlined" icon="pi pi-trash" :label="t('group.categoryDelete')" :disabled="busy" @click="emit('delete')" />
        </template>

        <div class="flex flex-col gap-5">
            <!-- ① 이름 -->
            <LabeledField :label="t('group.categoryNameLabel')" :hint="t('group.categoryNameHint')" :help="nameLong ? t('group.categoryNameLong') : t('group.categoryNameHelp')" :error="nameError">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" :model-value="name" class="w-full max-w-md" autocomplete="off" :invalid="invalid" :readonly="!canEdit || busy" :aria-describedby="describedBy" @update:model-value="(text: string | undefined) => emit('update:name', text ?? '')" />
                </template>
            </LabeledField>

            <!-- ② 추가할 게시판 고르기 -->
            <div v-if="canEdit" class="flex flex-col gap-1.5">
                <label :for="pickId" class="text-sm font-medium">{{ t('group.pickLabel') }}</label>
                <div v-if="noBoards" class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-dashed border-(--line) px-3 py-3 text-[13px] text-muted-color">
                    <span class="min-w-0 flex-1">{{ t('group.pickNoBoards') }}</span>
                    <NuxtLink v-if="canCreateBoard" :to="createBoardTo" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.boardCreateLink') }} ›</NuxtLink>
                    <NuxtLink v-else-if="canBoard" to="/boards" class="font-medium whitespace-nowrap text-primary hover:underline">{{ t('group.boardManageLink') }} ›</NuxtLink>
                </div>
                <div v-else class="flex flex-wrap items-center gap-2">
                    <Select
                        :key="pickKey"
                        v-model="pick"
                        :options="candidates"
                        option-label="name"
                        option-value="boardId"
                        :filter-fields="['name', 'slug']"
                        filter
                        auto-filter-focus
                        :input-id="pickId"
                        :placeholder="t('group.pickPlaceholder')"
                        :filter-placeholder="t('group.pickFilter')"
                        :empty-filter-message="t('group.pickNoMatch')"
                        :empty-message="t('group.pickEmpty')"
                        scroll-height="16rem"
                        class="w-full max-w-sm min-w-0 flex-1"
                        :disabled="busy || candidates.length === 0"
                        :aria-describedby="`${pickId}-hint`"
                    >
                        <template #option="{ option }">
                            <span class="flex min-w-0 flex-wrap items-baseline gap-x-2 gap-y-0.5">
                                <span class="truncate">{{ option.name }}</span>
                                <code class="shrink-0 text-[11px] text-muted-color">/{{ option.slug }}</code>
                                <span v-if="option.categoryName" class="shrink-0 text-xs text-amber-700 dark:text-amber-400">{{ t('group.pickMove', { name: option.categoryName }) }}</span>
                            </span>
                        </template>
                    </Select>
                    <Button type="button" :label="t('common.add')" icon="pi pi-plus" size="small" :disabled="busy || !pick" @click="onAdd" />
                </div>
                <p :id="`${pickId}-hint`" class="m-0 text-xs leading-relaxed text-muted-color">
                    {{ t('group.pickHint') }}
                    <template v-if="!noBoards">
                        <NuxtLink v-if="canCreateBoard" :to="createBoardTo" class="ml-1 whitespace-nowrap text-primary hover:underline">{{ t('group.boardCreateLink') }} ›</NuxtLink>
                        <NuxtLink v-else-if="canBoard" to="/boards" class="ml-1 whitespace-nowrap text-primary hover:underline">{{ t('group.boardManageLink') }} ›</NuxtLink>
                    </template>
                </p>
            </div>

            <!-- ③ 추가한 게시판 -->
            <div class="flex flex-col gap-2">
                <h3 class="text-sm font-semibold">{{ t('group.boardsTitle', { n: boardTotal }) }}</h3>
                <ul v-if="rows.length" class="m-0 flex list-none flex-col gap-1.5 p-0" :aria-label="t('group.boardsLabel')">
                    <li
                        v-for="row in rows"
                        :key="row.boardId"
                        class="flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border px-3 py-2"
                        :class="[row.state === 'added' ? 'border-primary bg-primary-50/60 dark:bg-primary-950/30' : 'border-(--line)', row.state === 'removed' ? 'opacity-60' : '']"
                    >
                        <span class="min-w-0 text-[13px] font-medium [overflow-wrap:anywhere]" :class="row.state === 'removed' ? 'line-through' : ''">{{ row.name }}</span>
                        <code class="rounded bg-surface-100 px-1.5 py-0.5 text-[11px] text-muted-color dark:bg-surface-800">/{{ row.slug }}</code>
                        <GroupTag v-if="row.investmentNotice" :value="t('group.categoryInvest')" severity="warn" />
                        <GroupTag v-if="!row.active" :value="t('group.boardOff')" />
                        <GroupTag v-if="row.state === 'added'" :value="t('group.boardAdded')" severity="success" />
                        <GroupTag v-if="row.state === 'removed'" :value="t('group.boardRemoved')" severity="warn" />
                        <span class="ml-auto text-xs tabular-nums text-muted-color">{{ t('group.boardGroupCount', { n: row.groupCount.toLocaleString(locale) }) }}</span>
                        <template v-if="canEdit">
                            <Button v-if="row.state === 'removed'" type="button" size="small" severity="secondary" variant="outlined" :label="t('group.boardUndo')" :aria-label="`${row.name} ${t('group.boardUndo')}`" :disabled="busy" @click="emit('add', row.boardId)" />
                            <Button v-else type="button" size="small" severity="secondary" variant="text" :label="t('group.boardRemove')" :aria-label="`${row.name} ${t('group.boardRemove')}`" :disabled="busy" @click="emit('remove', row.boardId)" />
                        </template>
                    </li>
                </ul>
                <p v-else class="m-0 rounded-lg border border-dashed border-(--line) px-3 py-4 text-center text-[13px] text-muted-color">{{ t('group.boardsEmpty') }}</p>
            </div>

            <!-- ④ 안내 — 막지 않고 알린다 -->
            <div v-if="moves.length || removals.length || hasOff || mixed" class="flex flex-col gap-1.5" aria-live="polite">
                <Message v-for="row in moves" :key="`move-${row.boardId}`" severity="warn" :closable="false" size="small">{{ UtilGroupMessages.moveNote(t, row.name, row.from?.name ?? '', row.groupCount) }}</Message>
                <p v-for="row in removals" :key="`remove-${row.boardId}`" class="m-0 text-xs leading-relaxed text-amber-700 dark:text-amber-400">{{ UtilGroupMessages.removeNote(t, row.name, row.groupCount) }}</p>
                <p v-if="hasOff" class="m-0 text-xs leading-relaxed text-muted-color">{{ t('group.offNote') }}</p>
                <Message v-if="mixed" severity="info" :closable="false" size="small">{{ t('group.mixedNote') }}</Message>
            </div>

            <p v-if="boardsError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ boardsError }}</p>
            <p v-if="deleteError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ deleteError }}</p>
            <Message v-if="saveError" severity="error" variant="simple" size="small" role="alert">{{ saveError }}</Message>

            <!-- ⑤ 저장 줄 — 선택한 분류 단위 -->
            <GroupSaveRow v-if="canEdit" :note="changeCount > 0 ? t('group.categoryNoteChanged', { n: changeCount }) : t('group.categoryNoteClean')" :dirty="changeCount > 0" :saving="saving" :disabled="!canSave" :error-count="nameError || boardsError ? 1 : 0" @save="emit('save')" @revert="emit('revert')" />
        </div>
    </AppGadget>
</template>

<script setup lang="ts">
import type { GroupCategoryCandidate, GroupCategoryItem } from '~/composables/group/api/useGroupCategoryApi.types';
import type { GroupCategoryRow } from '~/utils/util-group-category';
import UtilGroupMessages from '~/utils/util-group-messages';

interface Props {
    /** 저장된 분류 — 제목 */
    category: GroupCategoryItem;
    /** 입력 중인 이름 */
    name: string;
    nameError?: string;
    /** 12자를 넘는 이름 — 안내 문구를 바꾼다 */
    nameLong?: boolean;
    rows: GroupCategoryRow[];
    candidates: GroupCategoryCandidate[];
    /** 소모임 게시판이 하나도 없다 */
    noBoards?: boolean;
    /** 저장하면 이 분류가 갖는 게시판 수 */
    boardTotal: number;
    changeCount: number;
    mixed?: boolean;
    canSave?: boolean;
    canEdit?: boolean;
    /** 게시판 관리 링크(`board.read`) */
    canBoard?: boolean;
    /** [소모임 게시판 만들기] 링크(`board.manage`) */
    canCreateBoard?: boolean;
    saving?: boolean;
    busy?: boolean;
    boardsError?: string;
    saveError?: string;
    deleteError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    nameError: '',
    nameLong: false,
    noBoards: false,
    mixed: false,
    canSave: false,
    canEdit: false,
    canBoard: false,
    canCreateBoard: false,
    saving: false,
    busy: false,
    boardsError: '',
    saveError: '',
    deleteError: '',
});

const emit = defineEmits<{
    'update:name': [value: string];
    add: [boardId: string];
    remove: [boardId: string];
    save: [];
    revert: [];
    delete: [];
}>();

const { t, locale } = useI18n();
const pickId = useId();
const pick = ref<string | null>(null);
/** 추가하고 나면 선택 상자를 새로 만든다 — 방금 고른 값이 남아 또 더하려는 것처럼 보이지 않게 */
const pickKey = ref(0);

/** 게시판 관리의 만들기 대화상자 — 종류 소모임 고정, 이 분류가 미리 골라진 채 */
const createBoardTo = computed(() => ({ path: '/boards', query: { create: 'group', category: props.category.id } }));

const moves = computed(() => props.rows.filter((row) => row.state === 'added' && row.from));
const removals = computed(() => props.rows.filter((row) => row.state === 'removed'));
/** 꺼진 게시판이 목록에 있다(빼려는 것은 제외) */
const hasOff = computed(() => props.rows.some((row) => !row.active && row.state !== 'removed'));

function onAdd(): void {
    if (pick.value) emit('add', pick.value);
    pick.value = null;
    pickKey.value++;
}
</script>
