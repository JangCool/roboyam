<template>
    <!--
        짧게 줄인 글자 + 누르면 전체 글자(PrimeVue `Popover`) — 글 줄의 닉네임 등(사용자 결정 2026-11-12: 6자를 넘으면 6자 + 「…」, 누르면 전체 이름).
        글 줄 전체가 링크인 자리에서도 쓰므로 누름은 링크로 넘기지 않는다(`prevent`·`stop`). 줄이지 않은 글자는 그냥 글자(누름 없음).
        class·style 은 보이는 글자에 붙는다.
    -->
    <span
        v-if="truncated"
        v-bind="$attrs"
        role="button"
        tabindex="0"
        class="cursor-pointer whitespace-nowrap focus-visible:outline-2 focus-visible:outline-primary"
        :aria-label="text"
        aria-haspopup="dialog"
        @click.prevent.stop="toggle"
        @keydown.enter.prevent.stop="toggle"
        @keydown.space.prevent.stop="toggle"
        >{{ short }}</span
    >
    <span v-else v-bind="$attrs" class="whitespace-nowrap">{{ text }}</span>
    <Popover v-if="truncated" ref="popover" :pt="{ content: { class: 'px-2.5! py-1.5! text-xs break-all' } }">
        <span data-testid="truncated-full">{{ text }}</span>
    </Popover>
</template>

<script setup lang="ts">
defineOptions({ inheritAttrs: false });

interface Props {
    text: string;
    /** 이 글자 수를 넘으면 줄인다(글자 단위) */
    max?: number;
}

const props = withDefaults(defineProps<Props>(), { max: 6 });

const chars = computed(() => Array.from(props.text));
const truncated = computed(() => chars.value.length > props.max);
const short = computed(() => `${chars.value.slice(0, props.max).join('')}…`);

/** PrimeVue `Popover` — 바깥 누름·Esc 로 닫는 것은 Popover 가 맡는다 */
interface PopoverHandle {
    toggle: (event: Event, target?: HTMLElement) => void;
}
const popover = ref<PopoverHandle | null>(null);
function toggle(event: Event): void {
    popover.value?.toggle(event, event.currentTarget as HTMLElement);
}
</script>
