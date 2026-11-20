<template>
    <!--
        짤 올리기 창(계약 my-jjal-contract 「올리기 창」) — 끌어 놓기/고르기 · 규칙 세 줄 · 확인 두 개(둘 다 체크해야 올리기).
        고른 파일은 브라우저가 바로 줄인다(긴 변 300px — 움짤 GIF · 정지 PNG/JPG, `UtilJjalImage`) — 줄인 미리 보기와 크기를 보이고, 10초·100장·2MB 를 넘으면 올리기 전에 알린다.
        줄이기 코드와 라이브러리(gifenc·omggif)는 이 창을 처음 열 때 받는다(`JjalHost` 의 `LazyJjalUploadDialog` + 안에서 `import()`).
    -->
    <Dialog v-model:visible="open" modal :header="t('jjal.uploadTitle')" :draggable="false" :style="{ width: 'min(28rem, calc(100vw - 2rem))' }" data-testid="jjal-upload" @after-hide="reset">
        <div class="flex flex-col gap-4">
            <!-- 끌어 놓기 / 고르기 -->
            <div
                class="flex flex-col items-center justify-center gap-2 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors"
                :class="dragging ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-surface'"
                data-testid="jjal-upload-drop"
                @dragenter.prevent="dragging = true"
                @dragover.prevent="dragging = true"
                @dragleave.prevent="dragging = false"
                @drop.prevent="onDrop"
            >
                <template v-if="preparing">
                    <ProgressSpinner style="width: 2rem; height: 2rem" :stroke-width="4" :aria-label="t('jjal.preparing')" />
                    <span class="text-sm text-muted-color" role="status">{{ t('jjal.preparing') }}</span>
                </template>
                <template v-else-if="result && previewUrl">
                    <img :src="previewUrl" :alt="t('jjal.previewAlt')" :width="result.width" :height="result.height" class="h-auto max-h-48 max-w-full object-contain" data-testid="jjal-upload-preview" />
                    <span class="text-xs tabular-nums text-muted-color" data-testid="jjal-upload-info">{{ preparedLine }}</span>
                    <Button type="button" :label="t('jjal.changeFile')" icon="pi pi-refresh" size="small" severity="secondary" variant="text" :disabled="uploading" @click="choose" />
                </template>
                <template v-else>
                    <i class="pi pi-images text-2xl text-muted-color" aria-hidden="true" />
                    <p class="m-0 text-sm">
                        {{ t('jjal.uploadDrop') }}
                        <button type="button" class="rounded font-semibold text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary" @click="choose">{{ t('jjal.uploadChoose') }}</button>
                    </p>
                    <span class="text-xs text-muted-color">{{ t('jjal.uploadFormats') }}</span>
                </template>
                <input ref="fileInput" type="file" :accept="UtilJjal.ACCEPT" class="sr-only" tabindex="-1" aria-hidden="true" @change="onPick" />
            </div>
            <p v-if="problem" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert" data-testid="jjal-upload-problem">{{ problem }}</p>

            <!-- 규칙 세 줄 -->
            <ul class="m-0 flex list-none flex-col gap-1 p-0 text-xs leading-relaxed text-muted-color">
                <li class="flex items-start gap-1.5"><i class="pi pi-check mt-0.5 text-[0.625rem]" aria-hidden="true" />{{ t('jjal.ruleSize') }}</li>
                <li class="flex items-start gap-1.5"><i class="pi pi-check mt-0.5 text-[0.625rem]" aria-hidden="true" />{{ t('jjal.ruleAnimated') }}</li>
                <li class="flex items-start gap-1.5"><i class="pi pi-check mt-0.5 text-[0.625rem]" aria-hidden="true" />{{ t('jjal.ruleLimit', { limit }) }}</li>
            </ul>

            <!-- 확인 두 개 -->
            <fieldset class="m-0 flex flex-col gap-2 border-0 p-0">
                <label :for="`${uid}-clean`" class="flex cursor-pointer items-start gap-2 text-sm">
                    <Checkbox v-model="confirmClean" binary :input-id="`${uid}-clean`" class="mt-0.5" data-testid="jjal-upload-clean" />
                    <span>{{ t('jjal.confirmClean') }}</span>
                </label>
                <label :for="`${uid}-rights`" class="flex cursor-pointer items-start gap-2 text-sm">
                    <Checkbox v-model="confirmRights" binary :input-id="`${uid}-rights`" class="mt-0.5" data-testid="jjal-upload-rights" />
                    <span>{{ t('jjal.confirmRights') }}</span>
                </label>
            </fieldset>
            <Message v-if="serverError" severity="error" variant="simple" size="small" role="alert">{{ serverError }}</Message>
        </div>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="uploading" @click="open = false" />
            <Button type="button" :label="t('jjal.uploadSubmit')" icon="pi pi-upload" :disabled="!canSubmit" :loading="uploading" data-testid="jjal-upload-submit" @click="submit" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { JjalPrepared } from '~/utils/util-jjal';
import UtilJjal from '~/utils/util-jjal';
import UtilJjalImage, { JjalPrepareError } from '~/utils/util-jjal-image';
import UtilJjalMessages from '~/utils/util-jjal-messages';
import { ApiError } from '~/composables/common/useApiClient';

const open = defineModel<boolean>('visible', { required: true });

const { t } = useI18n();
const uid = useId();
const mine = useMyJjals();
const toast = useAppToast();

const fileInput = ref<HTMLInputElement | null>(null);
const dragging = ref(false);
const preparing = ref(false);
const uploading = ref(false);
const result = ref<JjalPrepared | null>(null);
const previewUrl = ref<string | null>(null);
const problem = ref('');
const serverError = ref('');
const confirmClean = ref(false);
const confirmRights = ref(false);
/** 고른 차례 — 늦게 끝난 줄이기가 새로 고른 파일을 덮지 않게 */
let pickSeq = 0;

const limit = computed(() => mine.list.value?.limit ?? UtilJjal.LIMIT);
const full = computed(() => {
    const list = mine.list.value;
    return Boolean(list && list.count >= list.limit);
});
const preparedLine = computed(() => (result.value ? UtilJjalMessages.prepared(t, { ...result.value, bytes: result.value.blob.size }) : ''));
const canSubmit = computed(() => Boolean(result.value) && confirmClean.value && confirmRights.value && !preparing.value && !uploading.value && !full.value);

function choose(): void {
    fileInput.value?.click();
}

function onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (file) void prepare(file);
}

function onDrop(event: DragEvent): void {
    dragging.value = false;
    const file = event.dataTransfer?.files?.[0];
    if (file) void prepare(file);
}

function clearPreview(): void {
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value);
    previewUrl.value = null;
    result.value = null;
}

/** 고른 파일을 줄인다 — 문제가 있으면 올리기 전에 알린다 */
async function prepare(file: File): Promise<void> {
    const seq = ++pickSeq;
    clearPreview();
    problem.value = '';
    serverError.value = '';
    preparing.value = true;
    try {
        const prepared = await UtilJjalImage.prepare(file);
        if (seq !== pickSeq) return;
        result.value = prepared;
        previewUrl.value = URL.createObjectURL(prepared.blob);
    } catch (e) {
        if (seq !== pickSeq) return;
        problem.value = e instanceof JjalPrepareError ? UtilJjalMessages.prepare(t, e.problem, e.detail) : t('jjal.problem.broken');
    } finally {
        if (seq === pickSeq) preparing.value = false;
    }
}

async function submit(): Promise<void> {
    const prepared = result.value;
    if (!prepared || !canSubmit.value) return;
    uploading.value = true;
    serverError.value = '';
    try {
        const saved = await mine.upload({ file: prepared.blob, filename: prepared.filename });
        toast.success(t('jjal.uploaded', { count: saved.count, limit: saved.limit }));
        open.value = false;
    } catch (e) {
        serverError.value = e instanceof ApiError && e.code === 'JJAL_LIMIT_REACHED' ? t('jjal.uploadFull', { limit: limit.value }) : UtilJjalMessages.error(t, e);
    } finally {
        uploading.value = false;
    }
}

/** 닫히면 처음 상태로 — 미리 보기 주소도 버린다 */
function reset(): void {
    pickSeq++;
    clearPreview();
    preparing.value = false;
    problem.value = '';
    serverError.value = '';
    confirmClean.value = false;
    confirmRights.value = false;
    dragging.value = false;
}

onBeforeUnmount(clearPreview);
</script>
