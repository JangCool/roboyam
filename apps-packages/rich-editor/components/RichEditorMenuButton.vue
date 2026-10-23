<template>
    <!-- 에디터 도구 모음의 드롭다운(community `EditorMenuButton`) — 단추를 누르면 작은 팝오버(몸에 붙여 그려서 도구 모음에 잘리지 않는다)가 열리고, 항목을 고르면 닫힌다.
         단추·항목을 눌러도 에디터의 초점과 글자 선택이 그대로이도록 mousedown 을 막는다 -->
    <span class="inline-flex shrink-0">
        <button
            type="button"
            :class="[
                'flex h-8 shrink-0 items-center gap-1 rounded-md px-1.5 text-sm focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40',
                active || open ? 'bg-primary-50 text-primary dark:bg-primary-950/60' : 'text-muted-color hover:bg-surface-100 dark:hover:bg-surface-800',
            ]"
            :aria-label="label"
            :title="label"
            aria-haspopup="menu"
            :aria-expanded="open"
            :disabled="disabled"
            @mousedown.prevent
            @click="popover?.toggle($event)"
        >
            <i v-if="icon" :class="icon" :style="tintStyle" aria-hidden="true" />
            <span v-if="text" class="min-w-4 text-center text-xs font-semibold" :style="tintStyle" aria-hidden="true">{{ text }}</span>
            <i class="pi pi-chevron-down text-[0.5rem]" aria-hidden="true" />
        </button>
        <Popover ref="popover" :aria-label="label" :pt="{ content: { class: 'p-1!' } }" @show="open = true" @hide="open = false">
            <div role="menu" :aria-label="label" class="flex min-w-36 flex-col">
                <button
                    v-for="item in items"
                    :key="item.key"
                    type="button"
                    role="menuitem"
                    :class="[
                        'flex h-8 items-center gap-2 rounded-md px-2 text-left text-sm focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40',
                        item.active ? 'bg-primary-50 text-primary dark:bg-primary-950/60' : 'hover:bg-surface-100 dark:hover:bg-surface-800',
                    ]"
                    :aria-current="item.active ? 'true' : undefined"
                    :disabled="item.disabled"
                    @mousedown.prevent
                    @click="pick(item.key)"
                >
                    <span class="grid w-6 shrink-0 place-items-center text-xs font-semibold text-muted-color" aria-hidden="true">
                        <i v-if="item.icon" :class="item.icon" />
                        <template v-else>{{ item.badge }}</template>
                    </span>
                    <span class="flex-1 whitespace-nowrap" :style="item.sample">{{ item.label }}</span>
                    <i v-if="item.active" class="pi pi-check text-xs" aria-hidden="true" />
                </button>
            </div>
        </Popover>
    </span>
</template>

<script setup lang="ts">
import type { RichMenuItem } from '#layers/rich-editor/components/RichEditorMenuButton.types';

interface PopoverHandle {
    toggle: (event: Event) => void;
    hide: () => void;
}

/** 드롭다운 단추 — `text` 는 단추에 보이는 짧은 글자, `active` 는 이 드롭다운 안의 무언가가 적용 중이라는 표시, `tint`·`fill` 은 색 단추가 지금 색을 보여 주는 용도 */
interface Props {
    label: string;
    items: RichMenuItem[];
    icon?: string;
    text?: string;
    active?: boolean;
    disabled?: boolean;
    /** 단추 아이콘·글자에 입히는 색 — 글자색 단추가 지금 글자색을 보여 준다 */
    tint?: string;
    /** 단추 아이콘 뒤에 까는 바탕색 — 배경색 단추가 지금 배경색을 보여 준다 */
    fill?: string;
}

const props = withDefaults(defineProps<Props>(), { icon: '', text: '', active: false, disabled: false, tint: '', fill: '' });

const emit = defineEmits<{ select: [key: string] }>();

const tintStyle = computed(() => {
    const style: Record<string, string> = {};
    if (props.tint) style.color = props.tint;
    if (props.fill) {
        style.backgroundColor = props.fill;
        style.borderRadius = '0.25rem';
        style.padding = '0.125rem';
        style.color = '#334155';
    }
    return Object.keys(style).length ? style : undefined;
});

const popover = ref<PopoverHandle | null>(null);
const open = ref(false);

function pick(key: string) {
    popover.value?.hide();
    emit('select', key);
}
</script>
