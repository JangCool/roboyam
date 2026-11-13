<template>
    <!--
        글 대표 썸네일 바꾸기(list-thumbnail-contract 「2단계」) — 게시글 관리 글 서랍의 「대표 썸네일」 구획 [바꾸기]가 연다(`post.manage`).
        고르기: 글에 붙은 이미지 격자에서 하나 · 새 그림 올리기(고르는 즉시 올려 받은 파일 ID 를 저장 때 보낸다 — 화면에서 JPEG·PNG·WebP·GIF, 10MB 를 미리 본다) ·
          썸네일 없음 · 자동(첫 이미지)으로 되돌리기. 옆(좁으면 아래)에 목록 칸 두 비율(16:10 목록 줄 · 4:3 갤러리)로 `object-cover` 가 어떻게 자르는지 미리 보인다.
        운영자가 정하면 글쓴이가 고른 것보다 우선한다(그동안 글쓴이는 못 바꾼다). 「자동으로 되돌리기」는 설정을 지워 잠금을 푼다.
        서버 호출은 페이지가 준 `actions.upload` 와 `save` 이벤트로만 한다. 열 때마다 지금 상태로 새로 채운다.
        올린 그림의 미리보기는 브라우저 임시 주소라 바꾸거나 창이 사라지면 푼다.
    -->
    <Dialog
        :visible="visible"
        modal
        :draggable="false"
        :header="t('postThumbnail.dialogTitle')"
        :style="{ width: 'min(46rem, calc(100vw - 2rem))' }"
        @update:visible="(v: boolean) => emit('update:visible', v)"
    >
        <form v-if="view" :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="info" size="small" :closable="false" icon="pi pi-info-circle">{{ t('postThumbnail.operatorWins') }}</Message>

            <div class="grid gap-5 md:grid-cols-[minmax(0,1fr)_13rem]">
                <!-- 고르기 -->
                <div class="flex min-w-0 flex-col gap-4" role="radiogroup" :aria-label="t('postThumbnail.choiceLabel')">
                    <!-- 글의 이미지에서 고르기 -->
                    <div class="flex flex-col gap-2">
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="pick.mode" value="IMAGE" :name="radioName" :input-id="`${formId}-image`" :disabled="saving || view.images.length === 0" @update:model-value="onModeImage" />
                            <label :for="`${formId}-image`" class="text-sm font-medium" :class="view.images.length === 0 ? 'text-muted-color' : ''">{{ t('postThumbnail.optionImage') }}</label>
                        </div>
                        <p v-if="view.images.length === 0" class="m-0 pl-7 text-xs text-muted-color">{{ t('postThumbnail.noImages') }}</p>
                        <ul v-else class="m-0 grid max-h-60 list-none grid-cols-3 gap-2 overflow-y-auto p-0 pl-7 sm:grid-cols-4" :aria-label="t('postThumbnail.imagesLabel')">
                            <li v-for="(img, i) in view.images" :key="img.imageId">
                                <button
                                    type="button"
                                    class="relative block aspect-[16/10] w-full overflow-hidden rounded-md border bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary dark:bg-surface-800"
                                    :class="isPicked(img.imageId) ? 'border-primary ring-2 ring-primary' : 'border-(--line) hover:border-primary'"
                                    :aria-pressed="isPicked(img.imageId)"
                                    :aria-label="t('postThumbnail.imageN', { n: i + 1 })"
                                    :disabled="saving"
                                    @click="pickImage(img.imageId)"
                                >
                                    <img :src="img.url" alt="" class="size-full object-cover" loading="lazy" />
                                    <span v-if="isPicked(img.imageId)" class="absolute top-1 left-1 inline-flex items-center justify-center rounded bg-primary px-1.5 py-0.5 text-[10px] leading-none font-semibold text-primary-contrast">
                                        <span class="relative top-[-0.09em]">{{ t('postThumbnail.pickedBadge') }}</span>
                                    </span>
                                    <span v-else-if="i === 0" class="absolute top-1 left-1 inline-flex items-center justify-center rounded bg-black/55 px-1.5 py-0.5 text-[10px] leading-none text-white">
                                        <span class="relative top-[-0.09em]">{{ t('postThumbnail.firstBadge') }}</span>
                                    </span>
                                </button>
                            </li>
                        </ul>
                    </div>

                    <!-- 새 그림 올리기 -->
                    <div class="flex flex-col gap-2">
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="pick.mode" value="UPLOAD" :name="radioName" :input-id="`${formId}-upload`" :disabled="saving" />
                            <label :for="`${formId}-upload`" class="text-sm font-medium">{{ t('postThumbnail.optionUpload') }}</label>
                        </div>
                        <div class="flex flex-wrap items-center gap-3 pl-7">
                            <div v-if="pick.uploadUrl" class="aspect-[16/10] w-24 shrink-0 overflow-hidden rounded-md border border-(--line) bg-surface-100 dark:bg-surface-800">
                                <img :src="pick.uploadUrl" alt="" class="size-full object-cover" />
                            </div>
                            <div class="flex min-w-0 flex-col gap-1">
                                <Button type="button" :label="pick.uploadUrl ? t('postThumbnail.uploadAgain') : t('postThumbnail.uploadPick')" icon="pi pi-upload" size="small" variant="outlined" class="self-start" :loading="uploading" :disabled="saving" @click="pickFile" />
                                <p class="m-0 text-xs text-muted-color">{{ t('postThumbnail.uploadHint', { max: UtilPostThumbnail.FILE_MAX_BYTES / (1024 * 1024) }) }}</p>
                            </div>
                            <input ref="fileInput" type="file" :accept="UtilPostThumbnail.FILE_ACCEPT" class="hidden" tabindex="-1" :aria-label="t('postThumbnail.optionUpload')" @change="onFileChange" />
                        </div>
                        <p v-if="uploadError" role="alert" class="m-0 pl-7 text-xs text-red-600 dark:text-red-400">{{ uploadError }}</p>
                    </div>

                    <!-- 썸네일 없음 -->
                    <div class="flex flex-col gap-1">
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="pick.mode" value="NONE" :name="radioName" :input-id="`${formId}-none`" :disabled="saving" />
                            <label :for="`${formId}-none`" class="text-sm font-medium">{{ t('postThumbnail.optionNone') }}</label>
                        </div>
                        <p class="m-0 pl-7 text-xs text-muted-color">{{ t('postThumbnail.optionNoneHelp') }}</p>
                    </div>

                    <!-- 자동으로 되돌리기 -->
                    <div class="flex flex-col gap-1">
                        <div class="flex items-center gap-2">
                            <RadioButton v-model="pick.mode" value="AUTO" :name="radioName" :input-id="`${formId}-auto`" :disabled="saving" />
                            <label :for="`${formId}-auto`" class="text-sm font-medium">{{ t('postThumbnail.optionAuto') }}</label>
                        </div>
                        <p class="m-0 pl-7 text-xs text-muted-color">{{ t('postThumbnail.optionAutoHelp') }}</p>
                    </div>
                </div>

                <!-- 목록 칸에서 잘리는 모양 -->
                <section class="flex flex-col gap-2" :aria-labelledby="`${formId}-preview`">
                    <h3 :id="`${formId}-preview`" class="m-0 text-sm font-medium">{{ t('postThumbnail.previewTitle') }}</h3>
                    <div class="grid grid-cols-2 gap-3 md:grid-cols-1">
                        <figure v-for="frame in frames" :key="frame.key" class="m-0 flex flex-col gap-1">
                            <div class="flex w-full items-center justify-center overflow-hidden rounded-md border border-(--line) bg-surface-100 dark:bg-surface-800" :class="frame.box">
                                <img v-if="previewUrl" :src="previewUrl" alt="" class="size-full object-cover" />
                                <span v-else class="flex flex-col items-center gap-1 text-xs text-muted-color">
                                    <i class="pi pi-image text-base" aria-hidden="true" />{{ t('postThumbnail.previewNone') }}
                                </span>
                            </div>
                            <figcaption class="text-[11px] text-muted-color">{{ frame.label }}</figcaption>
                        </figure>
                    </div>
                    <p class="m-0 text-[11px] leading-relaxed text-muted-color">{{ t('postThumbnail.previewHint') }}</p>
                </section>
            </div>

            <p v-if="pick.mode !== 'AUTO' && !UtilPostThumbnail.complete(pick)" class="m-0 text-xs text-muted-color">{{ pick.mode === 'IMAGE' ? t('postThumbnail.needImage') : t('postThumbnail.needUpload') }}</p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false">{{ error }}</Message>
        </form>

        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="t('common.save')" :loading="saving" :disabled="!canSave" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import { ApiError } from '~/composables/common/useApiClient';
import type { AdminPostThumbnailRequest, AdminPostThumbnailView, PostThumbnailMode } from '~/composables/post/api/usePostApi.types';
import type { PostThumbnailActions } from '~/components/post/PostThumbnailDialog.types';
import UtilPostThumbnail, { type PostThumbnailPick } from '~/utils/util-post-thumbnail';
import UtilPostThumbnailMessages from '~/utils/util-post-thumbnail-messages';

interface Props {
    visible: boolean;
    /** 글의 지금 대표 썸네일 — 이미지 목록·자동 그림 포함 */
    view?: AdminPostThumbnailView | null;
    actions: PostThumbnailActions;
    saving?: boolean;
    /** 저장 실패 문구(서버 문구 그대로) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { view: null, saving: false, error: '' });
const emit = defineEmits<{ 'update:visible': [value: boolean]; save: [body: AdminPostThumbnailRequest] }>();

const { t } = useI18n();
const formId = useId();
const radioName = `${formId}-mode`;
const fileInput = useTemplateRef<HTMLInputElement>('fileInput');

const pick = ref<PostThumbnailPick>({ mode: 'AUTO', imageId: null, fileId: null, uploadUrl: null });
const uploading = ref(false);
const uploadError = ref('');

/** 목록 칸 두 비율 — 목록 줄(16:10) · 갤러리(4:3) */
const frames = computed(() => [
    { key: 'row', box: 'aspect-[16/10]', label: t('postThumbnail.frameRow') },
    { key: 'gallery', box: 'aspect-[4/3]', label: t('postThumbnail.frameGallery') },
]);

const previewUrl = computed(() => (props.view ? UtilPostThumbnail.previewUrl(props.view, pick.value) : null));
const canSave = computed(() => Boolean(props.view) && !props.saving && !uploading.value && UtilPostThumbnail.complete(pick.value) && !UtilPostThumbnail.unchanged(props.view as AdminPostThumbnailView, pick.value));

function isPicked(imageId: string): boolean {
    return pick.value.mode === 'IMAGE' && pick.value.imageId === imageId;
}

function pickImage(imageId: string): void {
    pick.value = { ...pick.value, mode: 'IMAGE', imageId };
}

/** 「글의 이미지에서 고르기」를 켜면 아직 고른 것이 없을 때 첫 이미지를 고른다 */
function onModeImage(mode: PostThumbnailMode): void {
    if (mode === 'IMAGE' && !pick.value.imageId) pick.value = { ...pick.value, imageId: props.view?.images[0]?.imageId ?? null };
}

/** 방금 올린 그림의 브라우저 임시 주소 — 다른 그림을 올리거나 창을 다시 열거나 사라지면 푼다 */
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
    uploadError.value = '';
    const problem = UtilPostThumbnail.fileProblem(file);
    if (problem) {
        uploadError.value = UtilPostThumbnailMessages.fileProblem(t, problem);
        return;
    }
    uploading.value = true;
    try {
        const uploaded = await props.actions.upload(file);
        releasePreview();
        if (uploaded.url.startsWith('blob:')) objectUrl = uploaded.url;
        pick.value = { ...pick.value, mode: 'UPLOAD', fileId: uploaded.fileId, uploadUrl: uploaded.url };
    } catch (e) {
        uploadError.value = e instanceof ApiError ? (e.fields?.file ?? e.fields?.purpose ?? e.message) : t('postThumbnail.uploadFailed');
    } finally {
        uploading.value = false;
    }
}

/* ── 열 때마다 지금 상태로 ── */
watch(
    () => props.visible,
    (open) => {
        if (!open || !props.view) return;
        releasePreview();
        pick.value = UtilPostThumbnail.pickOf(props.view);
        uploadError.value = '';
    },
    { immediate: true },
);
onBeforeUnmount(releasePreview);

function submit(): void {
    if (!canSave.value) return;
    emit('save', UtilPostThumbnail.toRequest(pick.value));
}
</script>
