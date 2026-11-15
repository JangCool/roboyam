<template>
    <!--
        글 태그 칸(post-tags-contract) — 글쓰기·고치기가 같이 쓴다(게시판 `tagsEnabled` 일 때만 화면이 그린다). 값은 태그 목록(`v-model`), 저장은 화면이 한다.
        엔터·쉼표·스페이스로 칩을 넣고(앞의 `#` 는 뗀다 — `UtilPostTag`), 칩의 × 로 지운다. 빈 칸에서 지우기(Backspace)는 마지막 칩을 지운다.
        한글 입력(IME) 중 엔터는 글자 조합이 끝난 뒤 넣는다(조합 중 엔터로 반쯤 만든 글자가 들어가지 않게). 겹치는 태그는 조용히 무시, 너무 길거나 다 찼으면 칸 아래 안내.
        서버 오류(`fields.tags` · `fields."tags[0]"`)는 화면이 `error` 로 준다.
    -->
    <LabeledField :label="t('postTag.label')" :hint="t('postTag.optional', { max })" :error="shownError" :help="t('postTag.help', { len: UtilPostTag.LEN_MAX })" data-testid="post-tags">
        <template #default="{ id, describedBy, invalid }">
            <div class="flex flex-col gap-2">
                <InputText
                    :id="id"
                    :model-value="text"
                    :placeholder="t('postTag.placeholder')"
                    :maxlength="60"
                    :invalid="invalid"
                    :aria-describedby="describedBy"
                    :disabled="disabled"
                    autocomplete="off"
                    enterkeyhint="done"
                    fluid
                    data-testid="post-tags-input"
                    @update:model-value="onInput(String($event ?? ''))"
                    @keydown="onKeydown"
                    @compositionend="onCompositionEnd"
                    @blur="commit()"
                />
                <ul v-if="modelValue.length" class="m-0 flex list-none flex-wrap gap-1.5 p-0" :aria-label="t('postTag.listLabel')" data-testid="post-tags-list">
                    <li v-for="(tag, i) in modelValue" :key="tag">
                        <span class="inline-flex min-h-7 items-center gap-0.5 rounded-full border border-surface bg-surface-50 pl-2.5 pr-0.5 text-xs leading-none dark:bg-surface-800">
                            <span class="relative top-[-0.09em]"><span class="text-muted-color" aria-hidden="true">#</span>{{ tag }}</span>
                            <button
                                type="button"
                                class="grid size-6 place-items-center rounded-full text-muted-color hover:bg-surface-200 hover:text-color focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50 dark:hover:bg-surface-700"
                                :aria-label="t('postTag.remove', { name: tag })"
                                :disabled="disabled"
                                data-testid="post-tags-remove"
                                @click="remove(i)"
                            >
                                <i class="pi pi-times text-[0.625rem]" aria-hidden="true" />
                            </button>
                        </span>
                    </li>
                </ul>
            </div>
        </template>
        <template #aside>
            <span class="text-xs text-muted-color tabular-nums">{{ modelValue.length }} / {{ max }}</span>
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
import type { PostTagProblem } from '~/utils/util-post-tag';
import UtilPostTag from '~/utils/util-post-tag';

interface Props {
    modelValue: string[];
    /** 최대 태그 수 */
    max?: number;
    disabled?: boolean;
    /** 서버 오류 문구(칸에 붙는 것) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { max: UtilPostTag.MAX, disabled: false, error: '' });
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>();

const { t } = useI18n();

/** 아직 넣지 않은 글자 */
const text = ref('');
const problem = ref<PostTagProblem | null>(null);
/** 한글 조합 중 엔터를 눌렀다 — 조합이 끝나면 넣는다 */
let commitAfterCompose = false;

const problemText = computed(() => {
    if (problem.value === 'TOO_MANY') return t('postTag.tooMany', { max: props.max });
    if (problem.value === 'TOO_LONG') return t('postTag.tooLong', { len: UtilPostTag.LEN_MAX });
    if (problem.value === 'INVALID') return t('postTag.invalid');
    return '';
});
const shownError = computed(() => problemText.value || props.error);

/** 쉼표·공백이 들어오면 그 앞까지를 칩으로(붙여 넣은 여러 태그도) — 마지막 조각은 칸에 남긴다 */
function onInput(value: string): void {
    problem.value = null;
    const parts = value.split(/[,\s]/);
    if (parts.length === 1) {
        text.value = value;
        return;
    }
    const rest = parts.pop() ?? '';
    let list = props.modelValue;
    for (const part of parts) {
        const r = UtilPostTag.add(list, part, props.max);
        list = r.list;
        if (r.problem) problem.value = r.problem;
    }
    if (!UtilPostTag.same(list, props.modelValue)) emit('update:modelValue', list);
    text.value = rest;
}

/** 칸의 글자를 칩으로 */
function commit(): void {
    if (!text.value.trim()) return;
    const r = UtilPostTag.add(props.modelValue, text.value, props.max);
    problem.value = r.problem;
    if (r.problem) return;
    if (!UtilPostTag.same(r.list, props.modelValue)) emit('update:modelValue', r.list);
    text.value = '';
}

function onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter') {
        // 폼 제출을 막는다 — 태그 칸의 엔터는 칩 넣기
        event.preventDefault();
        if (event.isComposing) {
            commitAfterCompose = true;
            return;
        }
        commit();
    } else if (event.key === 'Backspace' && !text.value && !event.isComposing && props.modelValue.length) {
        remove(props.modelValue.length - 1);
    }
}

function onCompositionEnd(): void {
    if (!commitAfterCompose) return;
    commitAfterCompose = false;
    // 조합이 끝난 글자가 칸 값에 들어온 뒤에 넣는다
    void nextTick(commit);
}

function remove(i: number): void {
    problem.value = null;
    emit(
        'update:modelValue',
        props.modelValue.filter((_, at) => at !== i),
    );
}
</script>
