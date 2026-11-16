<template>
    <!--
        글 출처 칸(shared V232 — 게시판 「출처 칸 쓰기」 `sourcesEnabled` 일 때만 화면이 그린다). 글쓰기·고치기가 같이 쓴다. 값은 입력 줄 목록(`v-model`), 저장은 화면이 한다.
        줄마다 이름 + 주소, [출처 더하기]·줄 지우기, 최대 5줄. 주소 모양·이름 길이·같은 주소(두 번째 줄부터)는 줄 아래에 미리 알리고(판정은 서버), 서버 오류(`fields.sources`)는 화면이 `error` 로 준다.
    -->
    <LabeledField :label="t('post.sourcesLabel')" :hint="t('post.sourcesHint', { n: UtilPostSource.MAX })" :error="error" data-testid="post-sources">
        <template #default="{ id, describedBy, invalid }">
            <div class="flex flex-col gap-2">
                <div v-for="(row, i) in modelValue" :key="i" class="flex flex-col gap-1">
                    <div class="flex items-start gap-2">
                        <div class="grid min-w-0 flex-1 gap-2 sm:grid-cols-[12rem_1fr]">
                            <InputText
                                :id="i === 0 ? id : undefined"
                                :model-value="row.name"
                                :maxlength="UtilPostSource.NAME_MAX + 10"
                                :placeholder="t('post.sourceNamePlaceholder')"
                                :aria-label="t('post.sourceName')"
                                :aria-describedby="describedBy"
                                :invalid="invalid || problemOf(i) === 'NAME'"
                                :disabled="disabled"
                                fluid
                                @update:model-value="change(i, { name: String($event ?? '') })"
                            />
                            <InputText
                                :model-value="row.url"
                                type="url"
                                inputmode="url"
                                :maxlength="UtilPostSource.URL_MAX + 10"
                                :placeholder="t('post.sourceUrlPlaceholder')"
                                :aria-label="t('post.sourceUrl')"
                                :invalid="invalid || problemOf(i) === 'URL' || problemOf(i) === 'DUPLICATE'"
                                :disabled="disabled"
                                fluid
                                @update:model-value="change(i, { url: String($event ?? '') })"
                            />
                        </div>
                        <Button type="button" icon="pi pi-times" severity="secondary" variant="text" rounded :aria-label="t('post.sourceRemove')" :disabled="disabled" @click="remove(i)" />
                    </div>
                    <p v-if="problemOf(i) === 'URL'" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('post.sourceUrlInvalid', { max: UtilPostSource.URL_MAX }) }}</p>
                    <p v-else-if="problemOf(i) === 'NAME'" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('post.sourceNameTooLong', { max: UtilPostSource.NAME_MAX }) }}</p>
                    <p v-else-if="problemOf(i) === 'DUPLICATE'" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ t('post.sourceUrlDuplicate') }}</p>
                </div>
                <Button
                    v-if="modelValue.length < UtilPostSource.MAX"
                    type="button"
                    :label="t('post.sourceAdd')"
                    icon="pi pi-plus"
                    severity="secondary"
                    variant="outlined"
                    size="small"
                    class="self-start"
                    :disabled="disabled"
                    data-testid="post-sources-add"
                    @click="add"
                />
            </div>
        </template>
    </LabeledField>
</template>

<script setup lang="ts">
import UtilPostSource, { type PostSourceRow } from '~/utils/util-post-source';

interface Props {
    modelValue: PostSourceRow[];
    /** 서버 오류(`fields.sources`) */
    error?: string;
    disabled?: boolean;
    /** 줄 아래 주소 미리 알림을 보이는가 — [등록]을 누른 뒤부터(화면이 정한다). 이름 길이는 늘 보인다 */
    showProblems?: boolean;
}

const props = withDefaults(defineProps<Props>(), { error: '', disabled: false, showProblems: false });
const emit = defineEmits<{ 'update:modelValue': [value: PostSourceRow[]] }>();
const { t } = useI18n();

/** 줄마다의 문제(같은 주소 포함 — `UtilPostSource.problems`) */
const problems = computed(() => UtilPostSource.problems(props.modelValue));

/**
 * 줄의 문제 — 주소 모양 문제는 [등록]을 누른 뒤에만(치는 중에 바로 빨갛게 하지 않는다).
 * 이름 길이와 같은 주소는 바로 알린다(같은 주소는 다 친 뒤에야 생기고, 알리지 않으면 등록 때 하나가 조용히 사라진다).
 */
function problemOf(index: number): 'URL' | 'NAME' | 'DUPLICATE' | null {
    const problem = problems.value[index] ?? null;
    if (problem === 'URL') return props.showProblems ? problem : null;
    return problem;
}

function change(index: number, patch: Partial<PostSourceRow>): void {
    emit(
        'update:modelValue',
        props.modelValue.map((row, i) => (i === index ? { ...row, ...patch } : row)),
    );
}

function add(): void {
    if (props.modelValue.length >= UtilPostSource.MAX) return;
    emit('update:modelValue', [...props.modelValue, { name: '', url: '' }]);
}

function remove(index: number): void {
    emit(
        'update:modelValue',
        props.modelValue.filter((_, i) => i !== index),
    );
}
</script>
