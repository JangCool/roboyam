<template>
    <!-- 에디터 도구 모음의 아이콘 단추(community `EditorToolButton`) — 눌러도 에디터의 초점과 글자 선택이 그대로이도록 mousedown 을 막는다. 켜져 있으면 연한 바탕 -->
    <button
        type="button"
        :class="[
            'grid h-8 min-w-8 shrink-0 place-items-center rounded-md px-1.5 text-sm focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40',
            active ? 'bg-primary-50 text-primary dark:bg-primary-950/60' : 'text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800',
        ]"
        :aria-label="label"
        :title="label"
        :aria-pressed="toggle ? active : undefined"
        :disabled="disabled"
        @mousedown.prevent
        @click="emit('click')"
    >
        <i v-if="icon" :class="icon" aria-hidden="true" />
        <span v-else class="text-xs font-semibold" aria-hidden="true">{{ text }}</span>
    </button>
</template>

<script setup lang="ts">
/** 아이콘(`icon`, primeicons 클래스) 또는 짧은 글자(`text`)로 그리는 단추 */
interface Props {
    label: string;
    icon?: string;
    text?: string;
    active?: boolean;
    /** 켜고 끄는 단추면 `aria-pressed` 를 알린다 */
    toggle?: boolean;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { icon: '', text: '', active: false, toggle: false, disabled: false });

const emit = defineEmits<{ click: [] }>();
</script>
