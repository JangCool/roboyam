<template>
    <!--
        가젯 내용 설정 창(⚙) — 홈·가젯 페이지 편집기(`HomeLayoutEditor`)가 연다. 고칠 수 있는 종류와 칸은 `UtilGadgetConfig`:
          · 공동구매 — 보일 개수(1~10) · 정렬(마감 임박 · 새로 시작한 · 참여 많은 순) (사용자 결정 2026-10-29)
          · 글 목록 — 대상 게시판 · 개수(1~30) · 정렬(최신 · 인기 · 조회 · 추천 · 댓글) · 인기일 때 기간 탭(실시간·어제·이번 주)
          · 갤러리 — 대상 게시판 · 개수(1~24)
          · 게시판 카드 — 카드로 보일 게시판 · 카드마다 글 수(1~10) · 카드 머리(게시판 이름·「게시판 가기」) 보이기 — 게시판 하나짜리 카드는 꺼서 가젯 제목과 겹치지 않게
        대상 게시판: 전체 게시판(홈) · 「이 페이지가 연결된 폴더의 게시판 전체」(가젯 페이지 — 폴더에 게시판을 넣고 빼면 따라간다) · 직접 고르기(여러 개, 고른 차례 — 20개까지).
        게시판 카드는 「전체」 대신 서버가 켜진 일반 게시판 앞 6개를 보인다(서버 `HomeGadgetService`).
        [적용]은 편집 중인 값에만 넣는다(저장은 편집기 저장 줄). 고를 게시판 목록은 메뉴 대상 고르기 값(`useGadgetPageFolders`)이다.
    -->
    <Dialog :visible="visible" modal :header="t('homeLayout.settingsTitle', { title: gadget?.title ?? '' })" :draggable="false" :style="{ width: '30rem', maxWidth: '92vw' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form v-if="draft && spec && gadget" class="flex flex-col gap-4" novalidate @submit.prevent="apply">
            <p class="m-0 text-sm leading-relaxed text-muted-color">{{ t(`homeLayout.settingsLead_${gadget.type}`) }}</p>

            <!-- 대상 게시판 -->
            <div v-if="spec.boards" class="flex flex-col gap-1.5">
                <span :id="`${uid}-scope`" class="text-sm font-medium">{{ t(gadget.type === 'BOARD_CARDS' ? 'homeLayout.settingsCardBoards' : 'homeLayout.settingsBoards') }}</span>
                <SelectButton v-model="draft.boardScope" :options="scopeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-scope`" class="self-start" />
                <p v-if="draft.boardScope === 'FOLDER'" class="m-0 text-xs leading-relaxed text-muted-color" data-testid="gadget-scope-folder">
                    <template v-if="folder?.name">{{ t('homeLayout.scopeFolderNote', { folder: folder.name, boards: folderBoardNames || t('homeLayout.scopeFolderEmpty') }) }}</template>
                    <template v-else>{{ t('homeLayout.scopeFolderNone') }}</template>
                </p>
                <p v-else-if="draft.boardScope === 'ALL'" class="m-0 text-xs leading-relaxed text-muted-color">
                    {{ t(gadget.type === 'BOARD_CARDS' ? 'homeLayout.scopeAllCards' : 'homeLayout.scopeAllNote') }}
                </p>
                <template v-else>
                    <AppMultiSelect
                        v-model="pickedModel"
                        :options="boardOptions"
                        :input-id="`${uid}-boards`"
                        filter
                        :filter-placeholder="t('homeLayout.boardSearch')"
                        :placeholder="t('homeLayout.boardPickPlaceholder')"
                        :aria-label="t('homeLayout.settingsBoards')"
                        :invalid="Boolean(boardError)"
                        :max-selected-labels="6"
                        :selected-items-label="t('homeLayout.boardPicked')"
                        :empty-message="boardsLoading ? t('common.loading') : boardsError || t('homeLayout.boardEmpty')"
                    />
                    <small class="text-xs text-muted-color">{{ t('homeLayout.boardPickHint', { max: UtilGadgetConfig.BOARD_PICK_MAX }) }}</small>
                    <p v-if="boardError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ boardError }}</p>
                </template>
            </div>

            <!-- 개수 -->
            <div class="flex flex-col gap-1">
                <label :for="`${uid}-count`" class="text-sm font-medium">{{ t(`homeLayout.settingsCount_${gadget.type}`) }}</label>
                <InputNumber
                    v-model="countModel"
                    :input-id="`${uid}-count`"
                    :min="spec.count.min"
                    :max="spec.count.max"
                    show-buttons
                    button-layout="horizontal"
                    :allow-empty="false"
                    :aria-describedby="`${uid}-count-hint`"
                    class="w-40"
                    input-class="w-16 text-center"
                />
                <small :id="`${uid}-count-hint`" class="text-xs text-muted-color">{{ t('homeLayout.settingsCountHint', { min: spec.count.min, max: spec.count.max }) }}</small>
            </div>

            <!-- 정렬 -->
            <div v-if="spec.sorts.length" class="flex flex-col gap-1">
                <span :id="`${uid}-sort`" class="text-sm font-medium">{{ t('homeLayout.settingsSort') }}</span>
                <SelectButton v-if="spec.sorts.length <= 3" v-model="draft.sort" :options="sortOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-sort`" />
                <Select v-else v-model="draft.sort" :options="sortOptions" option-label="label" option-value="value" :aria-labelledby="`${uid}-sort`" class="w-56" />
            </div>

            <!-- 인기글 기간 탭 -->
            <div v-if="gadget.type === 'POST_LIST' && draft.sort === 'POPULAR'" class="flex flex-col gap-1">
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="periodModel" :input-id="`${uid}-period`" />
                    <label :for="`${uid}-period`" class="cursor-pointer text-sm">{{ t('homeLayout.settingsPeriodTabs') }}</label>
                </div>
            </div>

            <!-- 게시판 카드의 카드 머리 -->
            <div v-if="gadget.type === 'BOARD_CARDS'" class="flex flex-col gap-1">
                <div class="flex items-center gap-2">
                    <ToggleSwitch v-model="cardHeadModel" :input-id="`${uid}-head`" />
                    <label :for="`${uid}-head`" class="cursor-pointer text-sm">{{ t('homeLayout.settingsCardHead') }}</label>
                </div>
                <small class="text-xs text-muted-color">{{ t('homeLayout.settingsCardHeadHint') }}</small>
            </div>

            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(gadget.type === 'GROUP_BUY' ? 'homeLayout.settingsPreviewNote' : 'homeLayout.settingsPreviewNoteBoards') }}</p>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="emit('update:visible', false)" />
                <Button type="submit" :label="t('homeLayout.settingsApply')" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import type { HomeBoardScope, HomeGadgetConfig, HomeLayoutGadget } from '~/composables/home/api/useHomeLayoutApi.types';
import type { GadgetPageBoard } from '~/composables/page/api/useGadgetPageApi.types';
import UtilGadgetConfig from '~/utils/util-gadget-config';

interface Props {
    visible: boolean;
    /** 고칠 가젯 */
    gadget: HomeLayoutGadget | null;
    /** 가젯 페이지인가 — 대상 게시판에 「폴더 게시판 전체」가 생긴다 */
    inPage?: boolean;
    /** 페이지의 기준 폴더(이름·게시판) — 안내 줄 */
    folder?: { name: string | null; boards: readonly GadgetPageBoard[] } | null;
}
const props = withDefaults(defineProps<Props>(), { inPage: false, folder: null });
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'apply', config: HomeGadgetConfig): void }>();

const { t } = useI18n();
const uid = useId();
const { localizedName } = useLocalizedName();
const { boards, loading: boardsLoading, error: boardsError, load: loadBoards } = useGadgetPageFolders();

const draft = ref<HomeGadgetConfig | null>(null);
const submitted = ref(false);
const spec = computed(() => (props.gadget ? UtilGadgetConfig.spec(props.gadget.type) : null));

watch(
    () => props.visible,
    (open) => {
        if (!open || !props.gadget) return;
        draft.value = UtilGadgetConfig.normalize(props.gadget.type, props.gadget.config, props.inPage);
        submitted.value = false;
        if (spec.value?.boards) void loadBoards();
    },
    { immediate: true },
);

const scopeOptions = computed(() =>
    (props.inPage ? (['FOLDER', 'PICK', 'ALL'] as HomeBoardScope[]) : (['ALL', 'PICK'] as HomeBoardScope[])).map((value) => ({ value, label: t(`homeLayout.scope_${value}`) })),
);

const sortOptions = computed(() =>
    (spec.value?.sorts ?? []).map((value) => ({ value, label: t(props.gadget?.type === 'GROUP_BUY' ? `homeLayout.sort_${value}` : `homeLayout.postSort_${value}`) })),
);

const folderBoardNames = computed(() => (props.folder?.boards ?? []).map((b) => b.name).join(' · '));

/** 고를 게시판 — 이미 고른 slug 가 목록에 없으면(꺼짐·지움) 그 slug 로 남겨 보인다 */
const boardOptions = computed(() => {
    const known = boards.value.map((b) => ({ value: b.slug, label: `${localizedName(b.name, b.nameEn)}${b.active ? '' : ` (${t('homeLayout.boardInactive')})`}` }));
    const missing = (draft.value?.boardIds ?? []).filter((slug) => !known.some((o) => o.value === slug)).map((slug) => ({ value: slug, label: slug }));
    return [...known, ...missing];
});

const pickedModel = computed<unknown[]>({
    get: () => draft.value?.boardIds ?? [],
    set: (value) => {
        if (draft.value) draft.value.boardIds = value.map(String).slice(0, UtilGadgetConfig.BOARD_PICK_MAX);
    },
});

const countModel = computed<number>({
    get: () => (spec.value && draft.value ? Number(draft.value[spec.value.count.field] ?? spec.value.count.initial) : 1),
    set: (value) => {
        if (spec.value && draft.value) draft.value[spec.value.count.field] = value;
    },
});

const periodModel = computed<boolean>({
    get: () => draft.value?.periodTabs === true,
    set: (value) => {
        if (draft.value) draft.value.periodTabs = value;
    },
});

const cardHeadModel = computed<boolean>({
    get: () => draft.value?.cardHead !== false,
    set: (value) => {
        if (draft.value) draft.value.cardHead = value;
    },
});

const boardError = computed(() => (submitted.value && draft.value?.boardScope === 'PICK' && !draft.value.boardIds?.length ? t('homeLayout.boardPickRequired') : ''));

function apply(): void {
    submitted.value = true;
    const gadget = props.gadget;
    if (!gadget || !draft.value || boardError.value) return;
    const config = UtilGadgetConfig.normalize(gadget.type, draft.value, props.inPage);
    if (!config) return;
    emit('apply', config);
    emit('update:visible', false);
}
</script>
