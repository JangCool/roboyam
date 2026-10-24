<template>
    <!--
        글 이동 대화창 — 게시판 → 말머리(그 게시판의 켜진 말머리, 「말머리 없음」 포함) → 사유. 「고정은 풀려요」 안내.
        게시판·말머리 선택지는 부른 화면이 `board-options` 로 받아 내려 준다(못 받으면 다시 불러오기). 지금 자리와 같으면 보내지 않는다(서버도 400 `fields.prefixId`).
        서버 칸 오류(`fields.boardId`·`fields.prefixId`·`fields.reason`)는 그 칸 아래, 그 밖은 대화창 안 한 줄(서버 문구 그대로). 열 때마다 비운다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :header="t('post.moveTitle')" :style="{ width: 'min(34rem, calc(100vw - 2rem))' }">
        <form v-if="item" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <div class="rounded-lg border border-(--line) px-3 py-2">
                <p class="m-0 text-sm font-medium break-words">{{ item.title }}</p>
                <p class="m-0 mt-0.5 text-xs text-muted-color">{{ t('post.moveCurrent', { board: localizedName(item.board.name, item.board.nameEn), prefix: item.prefix?.name ?? t('post.movePrefixNone') }) }}</p>
            </div>
            <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('post.moveText') }}</p>

            <p v-if="optionsError" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" role="alert">
                {{ optionsError }}
                <Button type="button" :label="t('post.optionsRetry')" size="small" severity="secondary" variant="text" @click="emit('retry-options')" />
            </p>

            <LabeledField :label="t('post.moveBoard')" :error="boardError">
                <template #default="{ id, describedBy, invalid }">
                    <Select
                        v-model="boardId"
                        :input-id="id"
                        :options="boardOptions"
                        option-label="label"
                        option-value="value"
                        :loading="optionsLoading"
                        :disabled="busy"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        :placeholder="t('post.moveBoardPlaceholder')"
                        filter
                        fluid
                    />
                </template>
            </LabeledField>

            <LabeledField :label="t('post.movePrefix')" :error="prefixError">
                <template #default="{ id, describedBy, invalid }">
                    <Select v-model="prefixId" :input-id="id" :options="prefixOptions" option-label="label" option-value="value" :disabled="busy || !board" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>

            <PostReasonField v-model="reason" :error="reasonError" :disabled="busy" />

            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
            <Button type="submit" :form="formId" :label="t('post.action.MOVE')" :loading="busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { AdminPostBoardOption, AdminPostItem } from '~/composables/post/api/usePostApi.types';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    item?: AdminPostItem | null;
    boards?: AdminPostBoardOption[];
    optionsLoading?: boolean;
    /** 게시판 선택지를 못 받았을 때의 문구 */
    optionsError?: string;
    busy?: boolean;
    /** 칸에 붙지 않는 오류(서버 문구 그대로) */
    error?: string;
    /** 서버가 준 `fields` — `boardId` · `prefixId` · `reason` */
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { item: null, boards: () => [], optionsLoading: false, optionsError: '', busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ submit: [payload: { boardId: string; prefixId: string | null; reason: string }]; 'retry-options': [] }>();

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const formId = useId();

/** 「말머리 없음」 — Select 가 빈 값을 "고르지 않음"으로 읽어 이름이 안 보이므로 따로 둔다 */
const NO_PREFIX = '__none__';

const boardId = ref<string | null>(null);
const prefixId = ref<string>(NO_PREFIX);
const reason = ref('');
const tried = ref(false);

const board = computed(() => props.boards.find((b) => b.id === boardId.value) ?? null);
const boardOptions = computed(() => props.boards.map((b) => ({ value: b.id, label: localizedName(b.name, b.nameEn) })));
const prefixOptions = computed(() => [
    { value: NO_PREFIX, label: t('post.movePrefixNone') },
    ...(board.value?.prefixes ?? []).map((p) => ({ value: p.id, label: p.staffOnly ? t('post.prefixStaffOnly', { name: p.name }) : p.name })),
]);
const chosenPrefix = computed(() => (prefixId.value === NO_PREFIX ? null : prefixId.value));
const problem = computed(() => (props.item ? UtilPost.moveProblem(props.item, board.value, chosenPrefix.value) : null));

const boardError = computed(() => props.fieldErrors.boardId || (tried.value && problem.value === 'BOARD_REQUIRED' ? UtilPostMessages.moveProblem(t, problem.value) : ''));
const prefixError = computed(() => props.fieldErrors.prefixId || (tried.value && problem.value === 'SAME_LOCATION' ? UtilPostMessages.moveProblem(t, problem.value) : ''));
const reasonError = computed(() => props.fieldErrors.reason || (tried.value ? UtilPostMessages.reasonProblem(t, UtilPost.reasonProblem(reason.value)) : ''));

// 게시판을 바꾸면 말머리를 고른 게시판의 것으로 — 같은 이름이 있으면 그것, 없으면 「말머리 없음」
watch(boardId, () => {
    const name = props.item?.prefix?.name;
    const same = name ? board.value?.prefixes.find((p) => p.name === name) : undefined;
    prefixId.value = same?.id ?? NO_PREFIX;
});

watch(open, (isOpen) => {
    if (!isOpen) return;
    boardId.value = null;
    prefixId.value = NO_PREFIX;
    reason.value = '';
    tried.value = false;
});

function submit(): void {
    if (props.busy || !props.item) return;
    tried.value = true;
    if (problem.value || UtilPost.reasonProblem(reason.value) || !board.value) return;
    emit('submit', { boardId: board.value.id, prefixId: chosenPrefix.value, reason: reason.value.trim() });
}
</script>
