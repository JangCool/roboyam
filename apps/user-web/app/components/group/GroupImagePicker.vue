<template>
    <!--
        소모임 대표 이미지 고르기 — 개설 신청(`/groups/new`)·정보 수정(`/groups/{id}/manage/edit`)이 같이 쓴다. 고르면 바로 올리고(`useGroupApi.uploadImage`) 받은 파일을 v-model 로 돌려준다.
        저장(신청·정보 수정)은 폼이 `imageFileId` 로 한다. 미리보기 칸을 눌러도 고를 수 있다 — 비어 있으면 점선 칸에 그림 아이콘, 고르면 상세 머리와 같은 네모 미리보기.
        화면 검사(형식·10MB)는 미리 알려 주는 용도이고 판정은 서버다(`400 fields.file`).
    -->
    <div class="flex flex-col gap-1.5" data-testid="group-image-picker">
        <span :id="`${uid}-label`" class="text-sm font-medium">
            {{ t('group.fieldImage') }} <span class="font-normal text-muted-color">· {{ t('group.imageOptional') }}</span>
        </span>
        <div class="flex items-center gap-3" role="group" :aria-labelledby="`${uid}-label`" :aria-describedby="`${uid}-hint`">
            <button
                type="button"
                class="relative shrink-0 rounded-2xl focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed"
                :aria-label="modelValue ? t('group.imageChange') : t('group.imagePick')"
                :disabled="disabled || uploading"
                data-testid="group-image-preview"
                @click="fileInput?.click()"
            >
                <GroupInitial v-if="modelValue" :name="name" :src="modelValue.url" size="xl" />
                <span
                    v-else
                    class="grid size-16 place-items-center rounded-2xl border border-dashed border-surface-300 bg-surface-50 text-muted-color hover:border-primary hover:text-primary dark:border-surface-600 dark:bg-surface-900"
                >
                    <i class="pi pi-image text-xl" aria-hidden="true" />
                </span>
                <span v-if="uploading" class="absolute inset-0 grid place-items-center rounded-2xl bg-black/40" role="status" :aria-label="t('group.imageUploading')">
                    <i class="pi pi-spin pi-spinner text-lg text-white" aria-hidden="true" />
                </span>
            </button>
            <div class="min-w-0 flex-1">
                <div class="flex flex-wrap items-center gap-1">
                    <Button
                        type="button"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        :label="modelValue ? t('group.imageChange') : t('group.imagePick')"
                        :disabled="disabled || uploading"
                        data-testid="group-image-pick"
                        @click="fileInput?.click()"
                    />
                    <Button
                        v-if="modelValue"
                        type="button"
                        size="small"
                        severity="secondary"
                        variant="text"
                        :label="t('group.imageRemove')"
                        :disabled="disabled || uploading"
                        data-testid="group-image-remove"
                        @click="remove"
                    />
                </div>
                <small :id="`${uid}-hint`" class="mt-1 block text-xs text-muted-color">{{ t('group.imageHint', { max: MAX_MB }) }}</small>
            </div>
        </div>
        <input ref="fileInput" type="file" :accept="ACCEPT" class="sr-only" tabindex="-1" aria-hidden="true" data-testid="group-image-input" @change="onFile" />
        <p v-if="shownError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400" data-testid="group-image-error">{{ shownError }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupImage } from '~/composables/group/api/useGroupApi.types';
import UtilGroup from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

interface Props {
    /** 미리보기의 글자(이미지가 없을 때) — 폼에 적힌 이름 */
    name: string;
    disabled?: boolean;
    /** 폼이 붙이는 오류(저장 때 서버가 지적한 `fields.imageFileId`) */
    error?: string;
}

const props = withDefaults(defineProps<Props>(), {
    disabled: false,
    error: '',
});

const modelValue = defineModel<GroupImage | null>({ default: null });
const emit = defineEmits<{ uploading: [busy: boolean] }>();

const { t } = useI18n();
const uid = useId();
const api = useGroupApi();

const ACCEPT = UtilGroup.IMAGE_TYPES.join(',');
const MAX_MB = UtilGroup.IMAGE_MAX_BYTES / 1024 / 1024;

const fileInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const uploadError = ref('');
const shownError = computed(() => uploadError.value || props.error);

watch(uploading, (busy) => emit('uploading', busy));

async function onFile(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    uploadError.value = '';
    const problem = UtilGroup.imageProblem(file);
    if (problem) {
        uploadError.value = problem === 'TYPE' ? t('group.imageTypeError') : t('group.imageSizeError', { max: MAX_MB });
        return;
    }
    uploading.value = true;
    try {
        const { fileId, url } = await api.uploadImage(file);
        modelValue.value = { fileId, url };
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        uploadError.value = e instanceof ApiError && e.field('file') ? (e.field('file') ?? '') : UtilGroupMessages.failed(t, 'group.imageUploadFailed', e);
    } finally {
        uploading.value = false;
    }
}

function remove() {
    uploadError.value = '';
    modelValue.value = null;
}
</script>
