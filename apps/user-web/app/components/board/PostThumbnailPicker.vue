<template>
    <!--
        글쓰기·고치기의 「대표 이미지」 줄(list-thumbnail-contract 「2단계」 — 게시판 `authorThumbnailEnabled` 를 켠 게시판에서만 페이지가 그린다) — 본문 바로 아래(`PostEditorForm` 의 `belowContent` 자리). 글쓰기(`/write`)와 고치기(`/posts/[id]/edit`)가 같이 쓴다.
        게시판 목록·최신글·가젯에 보이는 작은 그림을 고른다: 본문 이미지 작은 그림 중 하나(고른 것에 「대표」) · 기본 「자동(첫 이미지)」 · 「따로 올리기」(본문에는 넣지 않는다) · 「대표 이미지 없음」.
        왼쪽(좁으면 위)에 목록 칸(16:10)으로 잘린 미리보기. 고른 본문 이미지를 에디터에서 지우면 자동으로 보고(`UtilPostThumbnail.effective`) 그렇다고 알린다 — 저장도 그 값으로 한다(페이지).
        본문에 이미지·영상이 없고 올린 그림도 없으면 줄을 접어 「이미지가 없어요」 + [따로 올리기] 한 줄만, 올리기도 못 하면(이미지를 막은 게시판) 아무것도 그리지 않는다.
        운영자가 정한 글(`locked`)은 「운영자가 정한 대표 이미지예요」 안내와 미리보기만 — 바꿀 수 없다.
        서버 호출은 하지 않는다 — 올리기는 페이지가 준 `upload` 함수로 한다(`components.md`).
    -->
    <section v-if="shown" class="flex flex-col gap-2" :aria-labelledby="titleId" data-testid="post-thumbnail-picker">
        <div class="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
            <h3 :id="titleId" class="m-0 text-sm font-medium">{{ t('postThumbnail.title') }}</h3>
            <span class="text-xs text-muted-color">{{ t('postThumbnail.hint') }}</span>
        </div>

        <!-- 운영자가 정함 — 잠김 -->
        <div v-if="locked" class="flex items-center gap-3 rounded-lg border border-surface px-3 py-2.5" data-testid="post-thumbnail-locked">
            <div class="flex aspect-[16/10] w-24 shrink-0 items-center justify-center overflow-hidden rounded-md border border-surface bg-surface-100 dark:bg-surface-800">
                <img v-if="locked.url" :src="locked.url" alt="" class="size-full object-cover" />
                <i v-else class="pi pi-image text-base text-muted-color" aria-hidden="true" />
            </div>
            <div class="flex min-w-0 flex-col gap-0.5">
                <p class="m-0 flex items-center gap-1.5 text-sm font-medium"><i class="pi pi-lock text-xs text-muted-color" aria-hidden="true" />{{ t('postThumbnail.lockedTitle') }}</p>
                <p class="m-0 text-xs text-muted-color">{{ locked.url ? t('postThumbnail.lockedText') : t('postThumbnail.lockedNone') }}</p>
            </div>
        </div>

        <!-- 이미지가 없다 — 접은 한 줄 -->
        <div v-else-if="compact" class="flex flex-wrap items-center gap-x-3 gap-y-1.5 text-xs text-muted-color" data-testid="post-thumbnail-compact">
            <span>{{ t('postThumbnail.noImages') }}</span>
            <button v-if="uploadOn" type="button" :class="[chipClass(false)]" :disabled="disabled || uploading" @click="pickFile">
                <i :class="uploading ? 'pi pi-spin pi-spinner' : 'pi pi-upload'" class="text-xs" aria-hidden="true" />{{ t('postThumbnail.upload') }}
            </button>
        </div>

        <!-- 고르기 -->
        <div v-else class="flex flex-col gap-3 sm:flex-row sm:items-start">
            <figure class="m-0 flex shrink-0 flex-col gap-1">
                <div class="flex aspect-[16/10] w-32 items-center justify-center overflow-hidden rounded-md border border-surface bg-surface-100 dark:bg-surface-800">
                    <img v-if="previewUrl" :src="previewUrl" alt="" class="size-full object-cover" />
                    <span v-else class="flex flex-col items-center gap-0.5 text-[11px] text-muted-color"><i class="pi pi-image text-sm" aria-hidden="true" />{{ t('postThumbnail.previewNone') }}</span>
                </div>
                <figcaption class="text-[11px] text-muted-color">{{ t('postThumbnail.previewCaption') }}</figcaption>
            </figure>

            <div class="flex min-w-0 flex-1 flex-col gap-2">
                <!-- 본문 이미지 + 올린 그림 -->
                <ul v-if="images.length || modelValue.uploadUrl" class="m-0 flex list-none flex-wrap gap-2 p-0" :aria-label="t('postThumbnail.imagesLabel')">
                    <li v-for="(img, i) in images" :key="img.imageId">
                        <button
                            type="button"
                            :class="tileClass(isPickedImage(img.imageId))"
                            :aria-pressed="isPickedImage(img.imageId)"
                            :aria-label="t('postThumbnail.imageN', { n: i + 1 })"
                            :disabled="disabled"
                            @click="emitChoice({ mode: 'IMAGE', imageId: img.imageId })"
                        >
                            <img :src="img.url" alt="" class="size-full object-cover" loading="lazy" />
                            <span v-if="isPickedImage(img.imageId)" :class="badgeClass(true)"><span class="relative top-[-0.09em]">{{ t('postThumbnail.pickedBadge') }}</span></span>
                            <span v-else-if="i === 0 && effective.mode === 'AUTO'" :class="badgeClass(false)"><span class="relative top-[-0.09em]">{{ t('postThumbnail.autoBadge') }}</span></span>
                        </button>
                    </li>
                    <li v-if="modelValue.uploadUrl">
                        <button
                            type="button"
                            :class="tileClass(effective.mode === 'UPLOAD')"
                            :aria-pressed="effective.mode === 'UPLOAD'"
                            :aria-label="t('postThumbnail.uploadedLabel')"
                            :disabled="disabled"
                            @click="emitChoice({ mode: 'UPLOAD' })"
                        >
                            <img :src="modelValue.uploadUrl" alt="" class="size-full object-cover" />
                            <span v-if="effective.mode === 'UPLOAD'" :class="badgeClass(true)"><span class="relative top-[-0.09em]">{{ t('postThumbnail.pickedBadge') }}</span></span>
                            <span v-else :class="badgeClass(false)"><span class="relative top-[-0.09em]">{{ t('postThumbnail.uploadedBadge') }}</span></span>
                        </button>
                    </li>
                </ul>

                <div role="group" :aria-label="t('postThumbnail.optionsLabel')" class="flex flex-wrap gap-1.5">
                    <button type="button" :class="chipClass(effective.mode === 'AUTO')" :aria-pressed="effective.mode === 'AUTO'" :disabled="disabled" @click="emitChoice({ mode: 'AUTO' })">
                        {{ t('postThumbnail.auto') }}
                    </button>
                    <button v-if="uploadOn" type="button" :class="chipClass(false)" :disabled="disabled || uploading" @click="pickFile">
                        <i :class="uploading ? 'pi pi-spin pi-spinner' : 'pi pi-upload'" class="text-xs" aria-hidden="true" />{{ modelValue.uploadUrl ? t('postThumbnail.uploadAgain') : t('postThumbnail.upload') }}
                    </button>
                    <button type="button" :class="chipClass(effective.mode === 'NONE')" :aria-pressed="effective.mode === 'NONE'" :disabled="disabled" @click="emitChoice({ mode: 'NONE' })">
                        {{ t('postThumbnail.none') }}
                    </button>
                </div>

                <p class="m-0 text-xs text-muted-color" aria-live="polite">
                    <template v-if="fellBack">{{ t('postThumbnail.fellBack') }} </template>{{ statusText }}
                </p>
            </div>
        </div>

        <input ref="fileInput" type="file" :accept="UtilPostThumbnail.FILE_ACCEPT" class="hidden" tabindex="-1" :aria-label="t('postThumbnail.upload')" @change="onFileChange" />
        <p v-if="!locked && uploadOn" class="m-0 text-[11px] text-muted-color">{{ t('postThumbnail.uploadHint', { max: UtilPostThumbnail.FILE_MAX_BYTES / (1024 * 1024) }) }}</p>
        <p v-if="uploadError || error" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ uploadError || error }}</p>
    </section>
</template>

<script setup lang="ts">
import type { ImageUploadResult } from '~/composables/board/api/useImageApi.types';
import type { ContentFormat, PostThumbnailView } from '~/composables/board/api/usePostApi.types';
import type { PostThumbnailChoice } from '~/components/board/PostThumbnailPicker.types';
import UtilPostThumbnail from '~/utils/util-post-thumbnail';
import UtilPostThumbnailMessages from '~/utils/util-post-thumbnail-messages';

interface Props {
    modelValue: PostThumbnailChoice;
    /** 지금 본문 — 이미지 작은 그림·자동 미리보기를 여기서 읽는다 */
    content: string;
    contentFormat: ContentFormat;
    /** 「따로 올리기」를 보인다 — 이 폼에서 이미지를 올릴 수 있을 때(게시판 허용·회원 — `PostEditorForm` 의 `imageUpload`) */
    canUpload?: boolean;
    /** 그림 한 장 올리기(페이지가 API 컴포저블로 만든다) — 실패하면 `ApiError` 를 던진다 */
    upload?: ((file: File) => Promise<ImageUploadResult>) | null;
    /** 운영자가 정한 대표 썸네일 — 있으면 잠김(안내와 미리보기만) */
    locked?: PostThumbnailView | null;
    disabled?: boolean;
    /** 서버가 거절한 문구(`fields.thumbnail`) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), { canUpload: false, upload: null, locked: null, disabled: false, error: '' });
const emit = defineEmits<{ 'update:modelValue': [value: PostThumbnailChoice] }>();

const { t } = useI18n();
const titleId = useId();
const fileInput = useTemplateRef<HTMLInputElement>('fileInput');

const images = computed(() => UtilPostThumbnail.bodyImages(props.content, props.contentFormat));
const videoUrl = computed(() => UtilPostThumbnail.firstVideoUrl(props.content, props.contentFormat));
const effective = computed(() => UtilPostThumbnail.effective(props.modelValue, images.value));
const fellBack = computed(() => UtilPostThumbnail.fellBack(props.modelValue, images.value));
const previewUrl = computed(() => UtilPostThumbnail.previewUrl(effective.value, images.value, videoUrl.value));
const statusText = computed(() => UtilPostThumbnailMessages.status(t, effective.value, { hasImage: images.value.length > 0, hasVideo: videoUrl.value !== null }));
/** 고를 것이 없다 — 본문 이미지·영상·올린 그림이 없고 자동이다 */
const compact = computed(() => !props.locked && images.value.length === 0 && !videoUrl.value && !props.modelValue.uploadUrl && effective.value.mode === 'AUTO');
/** 「따로 올리기」를 보이나 — 올릴 수 있는 폼이고 올리기 함수가 있다 */
const uploadOn = computed(() => props.canUpload && props.upload !== null);
/** 그릴 것이 있나 — 잠김이거나, 고를 것이 있거나, 접은 줄에서 올릴 수 있을 때 */
const shown = computed(() => Boolean(props.locked) || !compact.value || uploadOn.value);

function isPickedImage(imageId: string): boolean {
    return effective.value.mode === 'IMAGE' && effective.value.imageId === imageId;
}

/** 고른 값을 바꾼다 — 올린 그림(`fileId`·`uploadUrl`)은 다른 것을 골라도 들고 있어 다시 고를 수 있다 */
function emitChoice(change: Partial<PostThumbnailChoice>): void {
    emit('update:modelValue', { ...props.modelValue, imageId: null, ...change });
}

function chipClass(pressed: boolean): string {
    const base = 'inline-flex min-h-8 shrink-0 items-center gap-1.5 rounded-full border px-3 py-0.5 text-xs focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60';
    return pressed
        ? `${base} border-surface-900 bg-surface-900 font-semibold text-surface-0 dark:border-surface-300 dark:bg-surface-300 dark:text-surface-900`
        : `${base} border-surface bg-surface-0 text-color hover:bg-surface-100 dark:bg-surface-900 dark:hover:bg-surface-800`;
}

function tileClass(pressed: boolean): string {
    const base = 'relative block aspect-[16/10] w-20 overflow-hidden rounded-md border bg-surface-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed dark:bg-surface-800';
    return pressed ? `${base} border-primary ring-2 ring-primary` : `${base} border-surface hover:border-primary`;
}

function badgeClass(primary: boolean): string {
    const base = 'absolute top-1 left-1 inline-flex items-center justify-center rounded px-1.5 py-0.5 text-[10px] leading-none font-semibold';
    return primary ? `${base} bg-primary text-primary-contrast` : `${base} bg-black/55 text-white`;
}

/* ── 따로 올리기 ── */
const uploading = ref(false);
const uploadError = ref('');

function pickFile(): void {
    if (uploading.value || props.disabled || !uploadOn.value) return;
    fileInput.value?.click();
}

async function onFileChange(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // 같은 파일을 다시 고를 수 있게 비운다
    input.value = '';
    if (!file || !props.upload) return;
    uploadError.value = '';
    const problem = UtilPostThumbnail.fileProblem(file);
    if (problem) {
        uploadError.value = UtilPostThumbnailMessages.fileProblem(t, problem);
        return;
    }
    uploading.value = true;
    try {
        const uploaded = await props.upload(file);
        emit('update:modelValue', { mode: 'UPLOAD', imageId: null, fileId: uploaded.id, uploadUrl: uploaded.url });
    } catch (e) {
        uploadError.value = e instanceof ApiError ? (e.fields?.file ?? e.message) : t('postThumbnail.uploadFailed');
    } finally {
        uploading.value = false;
    }
}
</script>
