<template>
    <!-- 블록 옆 끌기 손잡이(⋮⋮) — 마우스로 블록(문단·제목·목록·이미지·유튜브 …)을 끌어 순서를 바꾼다. 손잡이가 없는 터치 화면에서는 CSS 가 숨긴다(`rt-drag-handle`) -->
    <DragHandle :editor="editor" :compute-position-config="{ placement: 'left-start' }" class="rt-drag-handle" data-testid="drag-handle">
        <i class="pi pi-ellipsis-v" aria-hidden="true" />
        <i class="pi pi-ellipsis-v -ml-1.5" aria-hidden="true" />
    </DragHandle>
</template>

<script setup lang="ts">
import { DragHandle } from '@tiptap/extension-drag-handle-vue-3';
import type { Editor } from '@tiptap/vue-3';

/** 고급 수준에서만 그린다. 부르는 쪽이 에디터가 있고 편집 가능할 때만 그린다(`v-if`) — community `RichEditorDragHandle` 그대로 */
interface Props {
    editor: Editor;
}

defineProps<Props>();
</script>
