<template>
    <!--
        하이라이트 만들기·고치기 대화상자 — 대상 글(만들 때만 고른다: 글 번호·제목으로 찾기) · 노출 제목(40자) · 요약(100자) · 대표 이미지 · 노출 켬/끔 · 노출 기간 · 찬성·반대 의견 받기 · 중립도 받기(기본 켬).
        서버 호출은 페이지가 준 `actions`(글 찾기·이미지 올리기)와 `save` 이벤트(만들기·고치기 요청)로만 한다. 열 때마다 입력을 새로 채운다(고칠 때는 그 하이라이트 값, 만들 때는 빈 칸).
        대표 이미지는 고르는 즉시 올려(`purpose=HIGHLIGHT_IMAGE`) 받은 파일 ID 를 저장 때 보낸다 — 저장하지 않으면 그 파일은 서버의 「올리다 만 파일 정리」가 지운다. [지우기]는 저장 때 `imageFileId: null`(지우기)로 간다.
        화면 검사(`UtilHighlight`)는 미리 알려 주는 것이고, 서버가 거절한 칸(`fields.postId|title|summary|showUntil|imageFileId`)은 그 칸 아래에 서버 문구 그대로, 칸에 붙지 않는 것은 아래 한 줄.
        권한이 없으면 이 대화상자를 열지 않는다(페이지가 단추를 감춘다).
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="item ? t('highlight.editTitle') : t('highlight.createTitle')"
        :style="{ width: 'min(40rem, calc(100vw - 2rem))' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <form :id="formId" class="flex flex-col gap-5" novalidate @submit.prevent="submit">
            <!-- 대상 글 -->
            <section class="flex flex-col gap-2" :aria-labelledby="`${formId}-post`">
                <h3 :id="`${formId}-post`" class="text-sm font-medium">{{ t('highlight.postLabel') }}</h3>
                <div v-if="form.post" class="flex items-start justify-between gap-3 rounded-lg border border-(--line) px-3 py-2">
                    <ContentPostCell :post="form.post">
                        <Tag v-if="form.post.state !== 'PUBLIC'" :value="t('highlight.postNotPublic')" severity="danger" class="text-[11px]!" />
                    </ContentPostCell>
                    <Button v-if="!item" type="button" :label="t('highlight.changePost')" size="small" severity="secondary" variant="text" :disabled="saving" @click="clearPost" />
                </div>
                <p v-if="item" class="m-0 text-xs text-muted-color">{{ t('highlight.postFixed') }}</p>

                <template v-if="!item && !form.post">
                    <div class="flex flex-wrap gap-2" role="search">
                        <InputText
                            v-model="query"
                            type="search"
                            class="min-w-0 flex-1"
                            :placeholder="t('highlight.searchPlaceholder')"
                            :aria-label="t('highlight.searchPlaceholder')"
                            :invalid="Boolean(postError)"
                            @keydown.enter.prevent="runSearch"
                        />
                        <Button type="button" :label="t('common.search')" icon="pi pi-search" size="small" :loading="searching" :disabled="!UtilHighlight.searchable(query)" @click="runSearch" />
                    </div>
                    <p class="m-0 text-xs text-muted-color">{{ t('highlight.searchHelp') }}</p>
                    <p v-if="searchError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ searchError }}</p>
                    <p v-else-if="searched && results.length === 0" class="m-0 text-xs text-muted-color">{{ t('highlight.searchEmpty') }}</p>
                    <ul v-if="results.length" class="m-0 flex max-h-64 list-none flex-col divide-y divide-(--line) overflow-y-auto rounded-lg border border-(--line) p-0">
                        <li v-for="post in results" :key="post.id" class="flex items-start justify-between gap-3 px-3 py-2">
                            <ContentPostCell :post="post" compact />
                            <Button type="button" :label="t('highlight.choosePost')" size="small" severity="secondary" variant="outlined" @click="choosePost(post)" />
                        </li>
                    </ul>
                </template>
                <p v-if="postError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ postError }}</p>
            </section>

            <LabeledField :label="t('highlight.titleLabel')" :hint="t('highlight.counter', { n: UtilHighlight.length(form.title), max: UtilHighlight.TITLE_MAX })" :help="t('highlight.titleHelp')" :error="fieldError('title')">
                <template #default="{ id, describedBy, invalid }">
                    <InputText :id="id" v-model="form.title" name="title" fluid :placeholder="form.post?.title ?? ''" :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <LabeledField :label="t('highlight.summaryLabel')" :hint="t('highlight.counter', { n: UtilHighlight.length(form.summary), max: UtilHighlight.SUMMARY_MAX })" :help="t('highlight.summaryHelp')" :error="fieldError('summary')">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="form.summary" name="summary" rows="2" fluid auto-resize :disabled="saving" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <!-- 대표 이미지 — 고르면 바로 올리고, 저장 때 그 파일을 지정한다 -->
            <section class="flex flex-col gap-1.5" :aria-labelledby="`${formId}-image`">
                <h3 :id="`${formId}-image`" class="text-sm font-medium">{{ t('highlight.imageLabel') }}</h3>
                <div class="flex items-start gap-3">
                    <div class="flex aspect-[16/9] w-36 shrink-0 items-center justify-center overflow-hidden rounded-lg border border-(--line) bg-surface-100 dark:bg-surface-800">
                        <img v-if="form.imageUrl && !broken" :src="form.imageUrl" alt="" class="size-full object-cover" @error="broken = true" />
                        <i v-else class="pi pi-image text-lg text-muted-color" aria-hidden="true" />
                    </div>
                    <div class="flex min-w-0 flex-col gap-1.5">
                        <div class="flex flex-wrap gap-2">
                            <Button type="button" :label="t('highlight.imageUpload')" icon="pi pi-upload" size="small" variant="outlined" :loading="uploading" :disabled="saving" @click="pickFile" />
                            <Button v-if="form.imageFileId" type="button" :label="t('highlight.imageRemove')" icon="pi pi-times" size="small" severity="danger" variant="text" :disabled="uploading || saving" @click="removeImage" />
                        </div>
                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('highlight.imageHint', { max: UtilHighlight.IMAGE_MAX_BYTES / (1024 * 1024) }) }}</p>
                        <input ref="fileInput" type="file" :accept="UtilHighlight.IMAGE_ACCEPT" class="hidden" tabindex="-1" :aria-label="t('highlight.imageUpload')" @change="onFileChange" />
                    </div>
                </div>
                <p v-if="imageError || fieldError('imageFileId')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ imageError || fieldError('imageFileId') }}</p>
            </section>

            <div class="flex flex-col gap-1">
                <div class="flex items-center gap-3">
                    <ToggleSwitch v-model="form.visible" :input-id="`${formId}-visible`" :disabled="saving" />
                    <label :for="`${formId}-visible`" class="text-sm font-medium">{{ t('highlight.visibleLabel') }}</label>
                </div>
                <p class="m-0 text-xs text-muted-color">{{ t('highlight.visibleHelp') }}</p>
            </div>

            <!-- 노출 기간 — 비우면 상시 -->
            <section class="flex flex-col gap-1.5" :aria-labelledby="`${formId}-period`">
                <h3 :id="`${formId}-period`" class="text-sm font-medium">{{ t('highlight.periodLabel') }}</h3>
                <div class="flex flex-wrap items-center gap-2">
                    <AppDateTimePicker v-model="form.showFrom" :input-id="`${formId}-from`" :minute-step="10" show-button-bar :show-zone="false" :disabled="saving" :placeholder="t('highlight.periodFrom')" class="w-52" />
                    <span class="text-muted-color" aria-hidden="true">~</span>
                    <AppDateTimePicker
                        v-model="form.showUntil"
                        :input-id="`${formId}-until`"
                        :minute-step="10"
                        show-button-bar
                        :show-zone="false"
                        :min="form.showFrom || undefined"
                        :disabled="saving"
                        :invalid="Boolean(fieldError('showUntil'))"
                        :placeholder="t('highlight.periodUntil')"
                        class="w-52"
                    />
                </div>
                <p v-if="fieldError('showUntil')" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ fieldError('showUntil') }}</p>
                <p v-else class="m-0 text-xs text-muted-color">{{ t('highlight.periodHelp') }}</p>
            </section>

            <div class="flex flex-col gap-1">
                <div class="flex items-center gap-3">
                    <ToggleSwitch v-model="form.debate" :input-id="`${formId}-debate`" :disabled="saving" />
                    <label :for="`${formId}-debate`" class="text-sm font-medium">{{ t('highlight.debateLabel') }}</label>
                </div>
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('highlight.debateHelp') }}</p>
            </div>

            <!-- 중립도 받기 — 의견 받기가 꺼져 있으면 비활성으로 보이되 값은 그대로 보낸다 -->
            <div class="flex flex-col gap-1 pl-4">
                <div class="flex items-center gap-3">
                    <ToggleSwitch v-model="form.neutralEnabled" :input-id="`${formId}-neutral`" :disabled="saving || !form.debate" :aria-describedby="`${formId}-neutral-help`" />
                    <label :for="`${formId}-neutral`" class="text-sm font-medium" :class="form.debate ? '' : 'text-muted-color'">{{ t('highlight.neutralLabel') }}</label>
                </div>
                <p :id="`${formId}-neutral-help`" class="m-0 text-xs leading-relaxed text-muted-color">
                    {{ t('highlight.neutralHelp') }}<template v-if="!form.debate"> {{ t('highlight.neutralIdle') }}</template>
                </p>
            </div>

            <Message v-if="formError" severity="error" size="small" variant="simple" :closable="false">{{ formError }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="item ? t('common.save') : t('highlight.create')" :loading="saving" :disabled="uploading" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostRef } from '~/composables/content/api/usePopularApi.types';
import type { HighlightItem, HighlightSaveRequest } from '~/composables/content/api/useHighlightApi.types';
import type { HighlightFormActions } from '~/components/highlight/HighlightFormDialog.types';
import UtilHighlight, { type HighlightForm, type HighlightFormField } from '~/utils/util-highlight';
import UtilHighlightMessages from '~/utils/util-highlight-messages';

interface Props {
    visible: boolean;
    /** 고칠 하이라이트 — 없으면 만들기 */
    item?: HighlightItem | null;
    actions: HighlightFormActions;
    saving?: boolean;
    /** 서버가 거절한 칸(`fields.<칸>`) */
    serverErrors?: Record<string, string>;
    /** 칸에 붙지 않는 저장 실패 */
    formError?: string;
}

const props = withDefaults(defineProps<Props>(), { item: null, saving: false, serverErrors: () => ({}), formError: '' });
const emit = defineEmits<{ 'update:visible': [value: boolean]; save: [body: HighlightSaveRequest] }>();

const { t } = useI18n();
const formId = useId();
const fileInput = useTemplateRef<HTMLInputElement>('fileInput');

const form = ref<HighlightForm>(UtilHighlight.emptyForm());
const tried = ref(false);
/** 서버 칸 오류를 그 칸을 고칠 때 지운다 — 고친 칸 이름 */
const touched = ref<Set<string>>(new Set());

const problems = computed(() => UtilHighlight.problems(form.value, !props.item));

/** 칸 오류 — 화면 검사(제출을 눌렀거나 글자 수 초과)가 먼저, 없으면 서버 문구(그 칸을 고치기 전까지) */
function fieldError(field: HighlightFormField): string {
    const problem = problems.value[field];
    if (problem && (tried.value || problem !== 'POST_REQUIRED')) return UtilHighlightMessages.formProblem(t, problem);
    return touched.value.has(field) ? '' : (props.serverErrors[field] ?? '');
}
const postError = computed(() => fieldError('postId'));

watch(
    () => [form.value.title, form.value.summary, form.value.showFrom, form.value.showUntil, form.value.imageFileId, form.value.post?.id] as const,
    (next, prev) => {
        const names: HighlightFormField[] = ['title', 'summary', 'showUntil', 'showUntil', 'imageFileId', 'postId'];
        next.forEach((value, i) => {
            if (prev && value !== prev[i]) touched.value.add(names[i] as string);
        });
    },
);
// 서버가 새로 거절하면 다시 보인다
watch(
    () => props.serverErrors,
    () => (touched.value = new Set()),
);

/* ── 글 찾기 ── */
const query = ref('');
const results = ref<AdminPostRef[]>([]);
const searching = ref(false);
const searched = ref(false);
const searchError = ref('');
let searchAbort: AbortController | null = null;

async function runSearch(): Promise<void> {
    if (!UtilHighlight.searchable(query.value)) return;
    searchAbort?.abort();
    const abort = new AbortController();
    searchAbort = abort;
    searching.value = true;
    searchError.value = '';
    try {
        results.value = await props.actions.searchPosts(query.value.trim(), abort.signal);
        searched.value = true;
    } catch (e) {
        if (e instanceof ApiError && e.code === 'ABORTED') return;
        results.value = [];
        searchError.value = e instanceof ApiError ? (e.fields?.q ?? e.message) : t('highlight.searchFailed');
    } finally {
        if (searchAbort === abort) searching.value = false;
    }
}

function choosePost(post: AdminPostRef): void {
    form.value = { ...form.value, post };
    results.value = [];
    searched.value = false;
}

function clearPost(): void {
    form.value = { ...form.value, post: null };
}

/* ── 대표 이미지 ── */
const uploading = ref(false);
const imageError = ref('');
const broken = ref(false);
/** 방금 올린 그림의 브라우저 임시 주소 — 바꾸거나 닫으면 푼다 */
let objectUrl: string | null = null;

function releasePreview(): void {
    if (objectUrl) URL.revokeObjectURL(objectUrl);
    objectUrl = null;
}

function pickFile(): void {
    if (uploading.value || props.saving) return;
    fileInput.value?.click();
}

async function onFileChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // 같은 파일을 다시 고를 수 있게 비운다
    input.value = '';
    if (!file) return;
    imageError.value = '';
    const problem = UtilHighlight.imageProblem(file);
    if (problem) {
        imageError.value = UtilHighlightMessages.imageProblem(t, problem);
        return;
    }
    uploading.value = true;
    try {
        const fileId = await props.actions.uploadImage(file);
        releasePreview();
        objectUrl = URL.createObjectURL(file);
        broken.value = false;
        form.value = { ...form.value, imageFileId: fileId, imageUrl: objectUrl };
    } catch (e) {
        imageError.value = e instanceof ApiError ? (e.fields?.file ?? e.fields?.purpose ?? e.message) : t('highlight.imageUploadFailed');
    } finally {
        uploading.value = false;
    }
}

function removeImage(): void {
    releasePreview();
    form.value = { ...form.value, imageFileId: null, imageUrl: null };
}

onBeforeUnmount(releasePreview);

/* ── 열 때마다 새로 ── */
watch(
    () => props.visible,
    (open) => {
        if (!open) return;
        releasePreview();
        form.value = props.item ? UtilHighlight.formOf(props.item) : UtilHighlight.emptyForm();
        tried.value = false;
        touched.value = new Set();
        query.value = '';
        results.value = [];
        searched.value = false;
        searchError.value = '';
        imageError.value = '';
        broken.value = false;
    },
    { immediate: true },
);

function submit(): void {
    tried.value = true;
    touched.value = new Set();
    if (props.saving || uploading.value || Object.keys(problems.value).length > 0) return;
    emit('save', UtilHighlight.toRequest(form.value));
}
</script>
