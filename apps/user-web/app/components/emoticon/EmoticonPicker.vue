<template>
    <!--
        이모티콘 단추 + 고르기 창 — 댓글 평문 입력(아래 줄)·글/댓글 에디터 도구 모음(`RichEditor` 의 `#tools` 칸)이 같이 쓴다.
        데스크톱은 단추 아래 팝오버, 모바일은 아래에서 올라오는 시트(`AppSheet`). 이모지·이모티콘 모두 하나 넣으면 창을 닫는다(넣은 모습을 바로 보이게).
        넣는 일은 부르는 쪽이 한다(`emoji`·`emoticon` 이벤트) — 입력칸마다 커서 다루는 법이 달라서.
    -->
    <Button
        type="button"
        icon="pi pi-face-smile"
        :label="icon ? undefined : t('emoticon.pickerButton')"
        :aria-label="t('emoticon.pickerButton')"
        :title="t('emoticon.pickerButton')"
        aria-haspopup="dialog"
        :aria-expanded="open"
        size="small"
        severity="secondary"
        variant="text"
        :disabled="disabled"
        :class="icon ? 'size-8! p-0!' : ''"
        data-testid="emoticon-picker-button"
        @click="toggle"
    />

    <Popover v-if="desktop" ref="popover" :pt="{ content: { class: 'p-0!' } }" @show="open = true" @hide="open = false">
        <div class="w-[22rem]" role="dialog" :aria-label="t('emoticon.pickerTitle')">
            <EmoticonPickerPanel @emoji="onEmoji" @emoticon="onEmoticon" @navigate="close" />
        </div>
    </Popover>
    <AppSheet v-else v-model:visible="open" :title="t('emoticon.pickerTitle')">
        <EmoticonPickerPanel @emoji="onEmoji" @emoticon="onEmoticon" @navigate="close" />
    </AppSheet>
</template>

<script setup lang="ts">
interface Props {
    /** 아이콘만(도구 모음) — 아니면 아이콘 + 「이모티콘」 글자 */
    icon?: boolean;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), {
    icon: false,
    disabled: false,
});

const emit = defineEmits<{
    emoji: [value: string];
    emoticon: [id: string];
}>();

const { t } = useI18n();
const desktop = useIsDesktop();
const open = ref(false);
const popover = ref<{ toggle: (event: Event) => void; hide: () => void } | null>(null);

function toggle(event: Event): void {
    if (desktop.value) popover.value?.toggle(event);
    else open.value = !open.value;
}

function close(): void {
    if (desktop.value) popover.value?.hide();
    open.value = false;
}

function onEmoji(value: string): void {
    emit('emoji', value);
    close();
}

function onEmoticon(id: string): void {
    emit('emoticon', id);
    close();
}
</script>
