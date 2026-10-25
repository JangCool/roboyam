<template>
    <!-- 이용자 메모 작성·수정 시트 — 나에게만 보이는 20자 이하의 한 줄 메모. 셸(`layouts/default.vue`)에 하나만 놓고 `useMemoSheet().open` 으로 연다 -->
    <AppSheet v-model:visible="open" :title="title" :description="t('block.memoSheetHelp')">
        <form class="mt-3 px-5" novalidate :aria-label="t('block.memoFormLabel')" @submit.prevent="save">
            <label :for="inputId" class="sr-only">{{ title }}</label>
            <InputText
                :id="inputId"
                v-model="text"
                name="memo"
                autocomplete="off"
                :placeholder="t('block.memoPlaceholder')"
                :invalid="Boolean(shownError)"
                :aria-describedby="shownError ? errorId : undefined"
                :disabled="saving"
                autofocus
                fluid
                data-testid="memo-input"
            />
            <div class="mt-1.5 flex items-start justify-between gap-3 text-xs">
                <p v-if="shownError" :id="errorId" role="alert" class="m-0 min-w-0 text-red-600 dark:text-red-400" data-testid="memo-error">{{ shownError }}</p>
                <span v-else aria-hidden="true" />
                <small class="shrink-0 tabular-nums" :class="problem === 'TOO_LONG' ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'" data-testid="memo-count">
                    {{ t('block.counter', { count: length, max: UtilBlock.MEMO_MAX }) }}
                </small>
            </div>

            <div class="mt-4 flex gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="outlined" class="flex-1" :disabled="saving" @click="open = false" />
                <Button type="submit" :label="t('common.save')" class="flex-1" :loading="saving" :disabled="!canSave" data-testid="memo-save" />
            </div>
            <!-- 이미 남긴 메모가 있을 때만 — 빈 메모를 저장해도 지워진다(서버 규칙) -->
            <Button v-if="target?.memo" type="button" :label="t('block.memoRemove')" severity="danger" variant="text" fluid class="mt-2" :disabled="saving" data-testid="memo-remove" @click="remove" />
        </form>
    </AppSheet>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import UtilBlock from '~/utils/util-block';
import UtilBlockMessages from '~/utils/util-block-messages';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 이용자 메모 작성·수정 시트(community `MemoSheet`) — 프로필의 ⋯ 메뉴·쪽지 대화방·설정 > 이용자 메모의 「수정」이 같은 시트를 쓴다.
 *  - 입력 `n / 20`. 20자를 넘으면 저장을 막는다(판정은 서버도 한다). **빈 메모를 저장하면 지운다**(서버 규칙).
 *  - 저장·삭제하면 이 기기의 표시(닉네임 옆 칩)가 바로 바뀌고 토스트로 알린다. 서버 오류는 입력칸 아래에 서버 문구 그대로.
 */
const { t } = useI18n();
const sheet = useMemoSheet();
const toast = useAppToast();

const open = computed({
    get: () => sheet.state.value.open,
    set: (value: boolean) => {
        if (!value) sheet.close();
    },
});
const target = computed(() => sheet.state.value.target);
const title = computed(() => (target.value ? t('block.memoSheetTitle', { nickname: target.value.nickname }) : t('block.memoTitle')));

const uid = useId();
const inputId = `${uid}-input`;
const errorId = `${uid}-error`;

const text = ref('');
const saving = ref(false);
const serverError = ref('');

const length = computed(() => UtilBlock.memoLength(text.value));
const problem = computed(() => UtilBlock.memoProblem(text.value));
const shownError = computed(() => (problem.value ? UtilBlockMessages.memoProblem(t, problem.value, UtilBlock.MEMO_MAX) : serverError.value));
const changed = computed(() => UtilBlock.memoToSend(text.value) !== (target.value?.memo ?? ''));
const canSave = computed(() => Boolean(target.value) && !saving.value && !problem.value && changed.value);

// 열 때마다 지금 남겨 둔 메모로 시작한다
watch(open, (now) => {
    if (!now) return;
    text.value = target.value?.memo ?? '';
    serverError.value = '';
});
watch(text, () => (serverError.value = ''));

function fail(e: unknown) {
    serverError.value = e instanceof ApiError ? (e.field('memo') ?? e.message) : UtilBoardMessages.of(t, e);
}

async function save() {
    if (!canSave.value) return;
    saving.value = true;
    serverError.value = '';
    try {
        const memo = await sheet.save(UtilBlock.memoToSend(text.value));
        toast.success(memo === null ? t('block.memoRemoved') : t('block.memoSaved'));
    } catch (e) {
        fail(e);
    } finally {
        saving.value = false;
    }
}

async function remove() {
    if (saving.value) return;
    saving.value = true;
    serverError.value = '';
    try {
        await sheet.remove();
        toast.success(t('block.memoRemoved'));
    } catch (e) {
        fail(e);
    } finally {
        saving.value = false;
    }
}
</script>
