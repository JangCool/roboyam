<template>
    <!-- 올리는 중·올리지 못한 이미지 목록(community `ImageUploadList`). 올리기가 끝난 이미지는 에디터에 들어가고 여기서는 사라진다 -->
    <section class="space-y-2 border-t border-surface p-2.5" :aria-label="t('richEditor.uploadListLabel')" data-testid="image-upload-list">
        <Message v-if="failedCount > 0" severity="error" :closable="false" size="small" role="alert">
            <b>{{ t('richEditor.uploadFailedTitle') }}</b><br />{{ t('richEditor.uploadFailedText') }}
        </Message>
        <p v-else class="m-0 text-xs text-muted-color" role="status">
            {{ t('richEditor.uploadingCount', { n: activeCount }) }} <span class="ml-1">{{ t('richEditor.uploadingStay') }}</span>
        </p>

        <ul class="m-0 list-none space-y-1.5 p-0">
            <li v-for="item in items" :key="item.id" class="flex flex-wrap items-start gap-x-2.5 gap-y-1.5 rounded-lg border border-surface p-2" :data-upload-status="item.status">
                <span
                    class="grid size-9 shrink-0 place-items-center rounded-md text-[0.6875rem] font-semibold"
                    :class="item.status === 'failed' ? 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-300' : 'bg-surface-100 text-muted-color dark:bg-surface-800'"
                    aria-hidden="true"
                >
                    {{ item.isGif ? 'GIF' : 'IMG' }}
                </span>

                <div class="min-w-0 flex-1 basis-40">
                    <p class="m-0 truncate text-sm" :title="item.name">{{ item.name }}</p>

                    <!-- 실패 — 서버 문구 그대로 -->
                    <p v-if="item.status === 'failed' && item.failure" class="m-0 mt-0.5 break-words text-xs text-red-600 dark:text-red-400" role="alert">{{ item.failure.message }}</p>

                    <!-- 대기·올리는 중·서버 처리 중 -->
                    <template v-else>
                        <div
                            class="mt-1.5 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-700"
                            role="progressbar"
                            :aria-label="t('richEditor.uploadingItem', { name: item.name })"
                            aria-valuemin="0"
                            aria-valuemax="100"
                            :aria-valuenow="item.status === 'uploading' ? percent(item) : undefined"
                        >
                            <div
                                class="h-full rounded-full bg-primary transition-[width] duration-150"
                                :class="item.status === 'processing' ? 'animate-pulse' : ''"
                                :style="{ width: item.status === 'queued' ? '0%' : item.status === 'processing' ? '100%' : `${percent(item)}%` }"
                            />
                        </div>
                        <p class="m-0 mt-1 text-xs text-muted-color">{{ statusText(item) }}</p>
                    </template>
                </div>

                <div class="ml-auto flex shrink-0 gap-1">
                    <Button v-if="item.status === 'failed' && item.failure?.retryable" type="button" :label="t('richEditor.retry')" size="small" severity="secondary" variant="outlined" :aria-label="t('richEditor.retryItem', { name: item.name })" @click="emit('retry', item.id)" />
                    <Button v-if="item.status === 'failed'" type="button" :label="t('richEditor.removeUpload')" size="small" severity="secondary" variant="text" :aria-label="t('richEditor.removeItem', { name: item.name })" @click="emit('remove', item.id)" />
                    <Button v-else type="button" :label="t('richEditor.cancelUpload')" size="small" severity="secondary" variant="text" :aria-label="t('richEditor.cancelItem', { name: item.name })" @click="emit('remove', item.id)" />
                </div>
            </li>
        </ul>
    </section>
</template>

<script setup lang="ts">
import type { RichUploadItem } from '#layers/rich-editor/composables/rich-editor/useRichImageUploads.types';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

interface Props {
    items: RichUploadItem[];
}

const props = withDefaults(defineProps<Props>(), {});

const emit = defineEmits<{
    retry: [id: string];
    /** 올리는 중이면 취소, 실패한 것이면 지우기 */
    remove: [id: string];
}>();

const { t } = useI18n();

const failedCount = computed(() => props.items.filter((i) => i.status === 'failed').length);
const activeCount = computed(() => props.items.length - failedCount.value);

function percent(item: RichUploadItem): number {
    return Math.min(99, Math.round(item.progress * 100));
}

function statusText(item: RichUploadItem): string {
    const size = UtilRichMedia.formatBytes(item.bytes);
    if (item.status === 'queued') return t('richEditor.uploadQueued', { size });
    if (item.status === 'processing') return t('richEditor.uploadProcessing', { size });
    return t('richEditor.uploadProgress', { percent: percent(item), size });
}
</script>
