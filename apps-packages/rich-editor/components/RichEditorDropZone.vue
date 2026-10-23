<template>
    <!-- 이미지 올리기 띠(community `ImageDropZone`) — 도구 모음 아래의 점선 영역. 파일을 끌어다 놓거나 눌러서 고른다(놓으면 `files`, 누르면 `pick`). 접으면 한 줄 단추만 남는다.
         에디터 본문에 끌어다 놓기·붙여넣기는 에디터가 그대로 받는다 — 이 띠는 눈에 보이는 입구를 하나 더 둔 것이다 -->
    <div class="border-b border-surface px-2 py-1.5" data-testid="image-dropzone">
        <div v-if="open" class="flex items-stretch gap-1.5">
            <div
                role="button"
                :tabindex="disabled ? -1 : 0"
                :aria-disabled="disabled || undefined"
                :aria-label="t('richEditor.dropZoneLabel')"
                class="flex min-w-0 flex-1 cursor-pointer items-center gap-2.5 rounded-md border border-dashed px-3 py-2 text-xs transition-colors *:pointer-events-none focus-visible:outline-2 focus-visible:outline-primary"
                :class="[
                    dragging ? 'border-primary bg-primary-50 text-primary dark:bg-primary-950/60' : 'border-surface-300 text-muted-color hover:border-primary hover:bg-surface-50 dark:border-surface-600 dark:hover:bg-surface-800',
                    disabled ? 'pointer-events-none opacity-50' : '',
                ]"
                @click="emit('pick')"
                @keydown.enter.prevent="emit('pick')"
                @keydown.space.prevent="emit('pick')"
                @dragenter.prevent="onDrag"
                @dragover.prevent="onDrag"
                @dragleave="dragging = false"
                @drop.prevent="onDrop"
            >
                <i class="pi pi-upload shrink-0 text-base" aria-hidden="true" />
                <span class="min-w-0">
                    <span class="font-medium" :class="dragging ? '' : 'text-color'">{{ dragging ? t('richEditor.dropZoneDrop') : t('richEditor.dropZoneText') }}</span>
                    <span class="ml-1.5 whitespace-nowrap">{{ t('richEditor.dropZoneFormats') }}</span>
                </span>
            </div>
            <button
                type="button"
                class="grid w-8 shrink-0 place-items-center rounded-md text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
                :aria-label="t('richEditor.dropZoneCollapse')"
                :title="t('richEditor.dropZoneCollapse')"
                @click="open = false"
            >
                <i class="pi pi-chevron-up text-xs" aria-hidden="true" />
            </button>
        </div>
        <button
            v-else
            type="button"
            class="flex h-7 items-center gap-1.5 rounded-md px-2 text-xs text-muted-color hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
            @click="open = true"
        >
            <i class="pi pi-chevron-down text-[0.625rem]" aria-hidden="true" />
            <span>{{ t('richEditor.dropZoneExpand') }}</span>
        </button>
    </div>
</template>

<script setup lang="ts">
/**
 * 파일을 받는 일만 한다 — 올리기(검사·목록)는 부모(`RichEditor`)가 한다. 파일이 아닌 끌기(글자·링크)는 반응하지 않는다.
 * 놓은 파일은 종류와 상관없이 부모에 넘긴다(이미지가 아니면 올리기 목록에 형식 오류로 남는다).
 */
interface Props {
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { disabled: false });

const emit = defineEmits<{
    /** 영역을 눌렀다 — 부모가 파일 고르기를 연다 */
    pick: [];
    /** 파일을 놓았다 */
    files: [files: File[]];
}>();

const { t } = useI18n();
const open = ref(true);
const dragging = ref(false);

function hasFiles(event: DragEvent): boolean {
    return Array.from(event.dataTransfer?.types ?? []).includes('Files');
}

function onDrag(event: DragEvent) {
    if (hasFiles(event)) dragging.value = true;
}

function onDrop(event: DragEvent) {
    dragging.value = false;
    const dropped = Array.from(event.dataTransfer?.files ?? []);
    if (dropped.length) emit('files', dropped);
}
</script>
