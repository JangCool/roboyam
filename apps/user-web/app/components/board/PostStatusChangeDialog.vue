<template>
    <!--
        처리 상태 바꾸기(운영 게시판 — 글 보기의 [상태 바꾸기]) — 「상태 고르기 + 운영진 메모(선택, 0~200자, 글자 수) + [바꾸기]」.
        지금 상태와 같은 값은 고를 수는 있어도 [바꾸기]가 꺼진다(서버도 같은 값이면 아무것도 하지 않는다). 메모는 앞뒤 공백을 떼고, 비면 보내지 않는다.
        저장(서버 호출)은 부른 화면이 한다 — 서버 `400 fields.note` 는 메모 칸 아래(`noteError`), 그 밖 오류는 폼 아래(`error`). 열 때마다 지금 상태·빈 메모로 시작한다.
    -->
    <Dialog :visible="visible" modal :header="t('post.statusChange')" :style="{ width: 'min(26rem, calc(100vw - 2rem))' }" :draggable="false" data-testid="post-status-dialog" @update:visible="close">
        <form class="flex flex-col gap-3" @submit.prevent="submit">
            <LabeledField :label="t('post.statusLabel')">
                <template #default="{ id }">
                    <Select v-model="chosen" :input-id="id" :options="options" option-label="label" option-value="value" fluid data-testid="post-status-dialog-select" />
                </template>
            </LabeledField>
            <LabeledField :label="t('post.statusNoteLabel')" :hint="t('post.statusNoteOptional')" :help="t('post.statusNoteHelp')" :error="noteError">
                <template #aside>
                    <span class="text-xs tabular-nums text-muted-color" :class="note.length > UtilPostOps.NOTE_MAX ? 'text-red-600 dark:text-red-400' : ''" aria-hidden="true" data-testid="post-status-note-count">
                        {{ note.length }}/{{ UtilPostOps.NOTE_MAX }}
                    </span>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="note" rows="3" :maxlength="UtilPostOps.NOTE_MAX" :invalid="invalid" :aria-describedby="describedBy" fluid auto-resize data-testid="post-status-note" />
                </template>
            </LabeledField>
            <Message v-if="error" severity="error" variant="simple" size="small">{{ error }}</Message>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" @click="close(false)" />
                <Button type="submit" :label="t('post.statusChangeSubmit')" :loading="loading" :disabled="!changed || loading" data-testid="post-status-dialog-submit" />
            </div>
        </form>
    </Dialog>
</template>

<script setup lang="ts">
import type { PostProcessStatus } from '~/composables/board/api/usePostApi.types';
import UtilPostOps from '~/utils/util-post-ops';

interface Props {
    visible: boolean;
    /** 지금 상태 — 열 때 고른 값으로 시작한다 */
    current: PostProcessStatus;
    loading?: boolean;
    /** 서버 문구(메모 칸이 아닌 오류) */
    error?: string;
    /** 서버 `fields.note` */
    noteError?: string;
}

const props = withDefaults(defineProps<Props>(), {
    loading: false,
    error: '',
    noteError: '',
});

const emit = defineEmits<{
    'update:visible': [value: boolean];
    /** [바꾸기] — 메모는 다듬은 값(비면 undefined) */
    submit: [status: PostProcessStatus, note: string | undefined];
}>();

const { t } = useI18n();
const chosen = ref<PostProcessStatus>(props.current);
const note = ref('');

const options = computed(() => UtilPostOps.STATUSES.map((value) => ({ value, label: t(UtilPostOps.labelKey(value)) })));
const changed = computed(() => chosen.value !== props.current);

function close(value: boolean): void {
    if (!value && props.loading) return;
    emit('update:visible', value);
}

function submit(): void {
    if (!changed.value || props.loading) return;
    emit('submit', chosen.value, UtilPostOps.noteOf(note.value));
}

// 열 때마다 지금 상태·빈 메모로 시작한다
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        chosen.value = props.current;
        note.value = '';
    },
);
</script>
