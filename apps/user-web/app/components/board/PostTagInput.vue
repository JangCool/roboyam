<template>
    <!--
        글 태그 칸(post-tags-contract) — 글쓰기·고치기가 같이 쓴다(게시판 `tagsEnabled` 일 때만 화면이 그린다). 값은 태그 목록(`v-model`), 저장은 화면이 한다.
        엔터·쉼표·스페이스로 칩을 넣고(앞의 `#` 는 뗀다 — `UtilPostTag`), 칩의 × 로 지운다. 빈 칸에서 지우기(Backspace)는 마지막 칩을 지운다.
        한글 입력(IME) 중 엔터는 글자 조합이 끝난 뒤 넣는다(조합 중 엔터로 반쯤 만든 글자가 들어가지 않게). 겹치는 태그는 조용히 무시, 너무 길거나 다 찼으면 칸 아래 안내.
        서버 오류(`fields.tags` · `fields."tags[0]"`)는 화면이 `error` 로 준다.
        **글쓰기 추천**(post-tags-contract 「글쓰기 추천」, 사용자 2026-11-15 — 같은 태그를 조금 다른 이름으로 달지 않게): `boardSlug` 가 있으면 그 게시판에서 쓰인 태그를 한 번 받아
          · 치는 동안 칸 아래 목록(띄어쓰기·대소문자 무시 「포함」 — 앞에서부터 맞는 것 먼저, 같으면 많이 쓴 순, 최대 6개 + 정확히 같은 태그가 없으면 「새 태그로 넣기」) — ↑·↓ 고르기, 엔터 넣기, Esc 닫기.
          · 넣을 때 띄어쓰기·대소문자만 다른 기존 태그가 있으면 그 모양으로 넣고 칸 아래에 알린다(서버도 저장 때 같은 규칙으로 맞춘다 — `PostTagStore#replace`).
          · 칸이 비었으면 「이 게시판에서 많이 쓴 태그」 칩(넣지 않은 것 위 8개) — 누르면 넣는다.
        받기에 실패하면 추천 없이 지금처럼 쓴다.
    -->
    <LabeledField :label="t('postTag.label')" :hint="t('postTag.optional', { max })" :error="shownError" :help="t('postTag.help', { len: UtilPostTag.LEN_MAX })" data-testid="post-tags">
        <template #default="{ id, describedBy, invalid }">
            <div class="flex flex-col gap-2">
                <div class="relative">
                    <InputText
                        :id="id"
                        :model-value="text"
                        :placeholder="t('postTag.placeholder')"
                        :maxlength="60"
                        :invalid="invalid"
                        :aria-describedby="describedBy"
                        :disabled="disabled"
                        role="combobox"
                        aria-autocomplete="list"
                        :aria-expanded="open"
                        :aria-controls="listId"
                        :aria-activedescendant="open && options[active] ? `${listId}-${active}` : undefined"
                        autocomplete="off"
                        enterkeyhint="done"
                        fluid
                        data-testid="post-tags-input"
                        @update:model-value="onInput(String($event ?? ''))"
                        @keydown="onKeydown"
                        @compositionend="onCompositionEnd"
                        @focus="focused = true"
                        @blur="onBlur"
                    />
                    <ul
                        v-show="open"
                        :id="listId"
                        role="listbox"
                        :aria-label="t('postTag.suggestList')"
                        class="absolute inset-x-0 top-full z-20 m-0 mt-1 list-none overflow-hidden rounded-md border border-surface bg-(--p-content-background) p-0 shadow-md"
                        data-testid="post-tags-suggest"
                    >
                        <li
                            v-for="(o, i) in options"
                            :id="`${listId}-${i}`"
                            :key="o.kind === 'tag' ? `t-${o.name}` : 'new'"
                            role="option"
                            :aria-selected="i === active"
                            class="flex cursor-pointer items-center gap-2 px-3 py-2 text-sm"
                            :class="[i === active ? 'bg-surface-100 dark:bg-surface-800' : '', o.kind === 'new' && i > 0 ? 'border-t border-surface text-muted-color' : '']"
                            @mousedown.prevent="pick(o)"
                            @mouseenter="active = i"
                        >
                            <template v-if="o.kind === 'tag'">
                                <span class="min-w-0 truncate"><span class="text-muted-color" aria-hidden="true">#</span>{{ o.name }}</span>
                                <span class="ml-auto shrink-0 text-xs tabular-nums text-muted-color">{{ t('postTag.suggestCount', { n: o.count.toLocaleString() }) }}</span>
                            </template>
                            <template v-else>
                                <i class="pi pi-plus text-xs" aria-hidden="true" />
                                <span class="min-w-0 truncate">{{ t('postTag.suggestNew', { name: o.name }) }}</span>
                            </template>
                        </li>
                    </ul>
                </div>
                <p v-if="replacedNote" class="m-0 text-xs text-amber-700 dark:text-amber-400" role="status" data-testid="post-tags-replaced">{{ replacedNote }}</p>
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
                <!-- 칸이 비었을 때 — 이 게시판에서 많이 쓴 태그(넣지 않은 것) -->
                <div v-if="popular.length && !text.trim() && modelValue.length < max" class="flex flex-col gap-1.5" data-testid="post-tags-popular">
                    <span class="text-xs text-muted-color">{{ t('postTag.suggestPopular') }}</span>
                    <ul class="m-0 flex list-none flex-wrap gap-1.5 p-0">
                        <li v-for="s in popular" :key="s.name">
                            <button
                                type="button"
                                class="inline-flex min-h-7 items-center rounded-full border border-dashed border-surface bg-transparent px-2.5 text-xs leading-none hover:border-primary hover:text-primary focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50"
                                :aria-label="t('postTag.suggestAdd', { name: s.name })"
                                :disabled="disabled"
                                @click="addTag(s.name)"
                            >
                                <span class="relative top-[-0.09em]"><span class="text-muted-color" aria-hidden="true">#</span>{{ s.name }}</span>
                            </button>
                        </li>
                    </ul>
                </div>
            </div>
        </template>
        <template #aside>
            <span class="text-xs text-muted-color tabular-nums">{{ modelValue.length }} / {{ max }}</span>
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
import type { BoardTagSuggestion } from '~/composables/board/api/useBoardApi.types';
import type { PostTagProblem } from '~/utils/util-post-tag';
import UtilPostTag from '~/utils/util-post-tag';

interface Props {
    modelValue: string[];
    /** 최대 태그 수 */
    max?: number;
    disabled?: boolean;
    /** 서버 오류 문구(칸에 붙는 것) */
    error?: string;
    /** 추천을 받을 게시판(없으면 추천 없음 — 소모임 글 등) */
    boardSlug?: string | null;
}

const props = withDefaults(defineProps<Props>(), { max: UtilPostTag.MAX, disabled: false, error: '', boardSlug: null });
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>();

const { t } = useI18n();
const boardApi = useBoardApi();
const listId = useId();

/** 치는 동안 보이는 추천 최대 수 */
const SUGGEST_MAX = 6;
/** 빈 칸일 때 보이는 많이 쓴 태그 수 */
const POPULAR_MAX = 8;

/** 아직 넣지 않은 글자 */
const text = ref('');
const problem = ref<PostTagProblem | null>(null);
/** 한글 조합 중 엔터를 눌렀다 — 조합이 끝나면 넣는다 */
let commitAfterCompose = false;

/* ── 글쓰기 추천 ── */

/** 이 게시판에서 쓰인 태그(많이 쓴 순) */
const suggestions = ref<BoardTagSuggestion[]>([]);
const focused = ref(false);
/** 추천 목록에서 고른 줄 */
const active = ref(0);
/** Esc 로 닫았다(다시 치면 열린다) */
const dismissed = ref(false);
/** 기존 태그 모양으로 바꿔 넣었을 때 알림 */
const replacedNote = ref('');

watch(
    () => props.boardSlug,
    async (slug, _old, onCleanup) => {
        suggestions.value = [];
        if (!slug) return;
        const ctrl = new AbortController();
        onCleanup(() => ctrl.abort());
        try {
            suggestions.value = await boardApi.tags(slug, ctrl.signal);
        } catch {
            // 추천은 덤 — 실패하면 추천 없이 쓴다
        }
    },
    { immediate: true },
);

/** 띄어쓰기·대소문자 무시 열쇠 → 기존 태그 이름 */
const byCompact = computed(() => {
    const map = new Map<string, string>();
    for (const s of suggestions.value) if (!map.has(UtilPostTag.compact(s.name))) map.set(UtilPostTag.compact(s.name), s.name);
    return map;
});

type SuggestOption = { kind: 'tag'; name: string; count: number } | { kind: 'new'; name: string };

const options = computed<SuggestOption[]>(() => {
    const checked = UtilPostTag.check(text.value);
    if (!checked || 'problem' in checked) return [];
    const q = UtilPostTag.compact(checked.tag);
    if (!q) return [];
    const picked = new Set(props.modelValue.map((x) => UtilPostTag.compact(x)));
    const matched = suggestions.value
        .filter((s) => !picked.has(UtilPostTag.compact(s.name)) && UtilPostTag.compact(s.name).includes(q))
        .sort((a, b) => Number(UtilPostTag.compact(b.name).startsWith(q)) - Number(UtilPostTag.compact(a.name).startsWith(q)) || b.count - a.count)
        .slice(0, SUGGEST_MAX)
        .map((s): SuggestOption => ({ kind: 'tag', name: s.name, count: s.count }));
    const exact = byCompact.value.has(q) || picked.has(q);
    return exact ? matched : [...matched, { kind: 'new', name: checked.tag }];
});

/** 추천이 있을 때만(「새 태그로 넣기」 한 줄뿐이면 열지 않는다) */
const open = computed(() => focused.value && !dismissed.value && options.value.some((o) => o.kind === 'tag'));

const popular = computed(() => {
    const picked = new Set(props.modelValue.map((x) => UtilPostTag.compact(x)));
    return suggestions.value.filter((s) => !picked.has(UtilPostTag.compact(s.name))).slice(0, POPULAR_MAX);
});

const problemText = computed(() => {
    if (problem.value === 'TOO_MANY') return t('postTag.tooMany', { max: props.max });
    if (problem.value === 'TOO_LONG') return t('postTag.tooLong', { len: UtilPostTag.LEN_MAX });
    if (problem.value === 'INVALID') return t('postTag.invalid');
    return '';
});
const shownError = computed(() => problemText.value || props.error);

/**
 * 넣을 모양 — 띄어쓰기·대소문자만 다른 태그가 이미 단 목록이나 이 게시판에 있으면 그 모양(바꿨으면 알림)
 * @param {string} tag - 바로잡은 태그.
 * @returns {string} 넣을 태그.
 */
function canonical(tag: string): string {
    const k = UtilPostTag.compact(tag);
    const mine = props.modelValue.find((x) => UtilPostTag.compact(x) === k);
    const found = mine ?? byCompact.value.get(k);
    if (found && found !== tag) replacedNote.value = t('postTag.suggestReplaced', { name: found });
    return found ?? tag;
}

/** 목록에 하나 넣기 — 바꾼 모양으로. 실패 까닭은 칸 아래 */
function addOne(list: string[], raw: string): string[] {
    const checked = UtilPostTag.check(raw);
    if (!checked) return list;
    if ('problem' in checked) {
        problem.value = checked.problem;
        return list;
    }
    const r = UtilPostTag.add(list, canonical(checked.tag), props.max);
    if (r.problem) problem.value = r.problem;
    return r.list;
}

/** 쉼표·공백이 들어오면 그 앞까지를 칩으로(붙여 넣은 여러 태그도) — 마지막 조각은 칸에 남긴다 */
function onInput(value: string): void {
    problem.value = null;
    replacedNote.value = '';
    dismissed.value = false;
    active.value = 0;
    const parts = value.split(/[,\s]/);
    if (parts.length === 1) {
        text.value = value;
        return;
    }
    const rest = parts.pop() ?? '';
    let list = props.modelValue;
    for (const part of parts) list = addOne(list, part);
    if (!UtilPostTag.same(list, props.modelValue)) emit('update:modelValue', list);
    text.value = rest;
}

/** 이름 하나를 넣는다(추천 줄·많이 쓴 태그 칩) */
function addTag(name: string): void {
    problem.value = null;
    replacedNote.value = '';
    const list = addOne(props.modelValue, name);
    if (!UtilPostTag.same(list, props.modelValue)) emit('update:modelValue', list);
    text.value = '';
    active.value = 0;
}

function pick(o: SuggestOption): void {
    addTag(o.name);
}

/** 칸의 글자를 칩으로 */
function commit(): void {
    if (!text.value.trim()) return;
    problem.value = null;
    const list = addOne(props.modelValue, text.value);
    if (problem.value) return;
    if (!UtilPostTag.same(list, props.modelValue)) emit('update:modelValue', list);
    text.value = '';
    active.value = 0;
}

function onBlur(): void {
    focused.value = false;
    commit();
}

function onKeydown(event: KeyboardEvent): void {
    if (open.value && (event.key === 'ArrowDown' || event.key === 'ArrowUp')) {
        event.preventDefault();
        const n = options.value.length;
        active.value = (active.value + (event.key === 'ArrowDown' ? 1 : n - 1)) % n;
        return;
    }
    if (open.value && event.key === 'Escape') {
        event.preventDefault();
        dismissed.value = true;
        return;
    }
    if (event.key === 'Enter') {
        // 폼 제출을 막는다 — 태그 칸의 엔터는 칩 넣기
        event.preventDefault();
        if (event.isComposing) {
            commitAfterCompose = true;
            return;
        }
        const o = open.value ? options.value[active.value] : undefined;
        if (o) pick(o);
        else commit();
    } else if (event.key === 'Backspace' && !text.value && !event.isComposing && props.modelValue.length) {
        remove(props.modelValue.length - 1);
    }
}

function onCompositionEnd(): void {
    if (!commitAfterCompose) return;
    commitAfterCompose = false;
    // 조합이 끝난 글자가 칸 값에 들어온 뒤에 넣는다 — 추천 목록이 열려 있으면 고른 줄
    void nextTick(() => {
        const o = open.value ? options.value[active.value] : undefined;
        if (o) pick(o);
        else commit();
    });
}

function remove(i: number): void {
    problem.value = null;
    replacedNote.value = '';
    emit(
        'update:modelValue',
        props.modelValue.filter((_, at) => at !== i),
    );
}
</script>
