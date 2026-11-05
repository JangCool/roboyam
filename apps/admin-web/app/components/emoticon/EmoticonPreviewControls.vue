<template>
    <!--
        로보티콘 격자 보기 조절 — 표시 크기(100 글·댓글 / 80 / 64 고르기 창) · 바탕(밝게 / 어둡게 — 다크 모드에서 흰 테두리·반투명이 어떻게 보이는지).
        심사 서랍·팩 서랍이 같이 쓴다. 값은 부모가 들고(`v-model:size`·`v-model:dark`) 같은 서랍의 격자 여럿(새 판·빠진 것)에 함께 건다.
    -->
    <div class="flex flex-wrap items-center gap-x-4 gap-y-2">
        <div class="flex items-center gap-2">
            <span :id="sizeLabelId" class="text-xs text-muted-color">{{ t('emoticon.preview.size') }}</span>
            <SelectButton v-model="size" :options="sizeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="sizeLabelId" />
        </div>
        <div class="flex items-center gap-2">
            <span :id="bgLabelId" class="text-xs text-muted-color">{{ t('emoticon.preview.background') }}</span>
            <SelectButton v-model="dark" :options="bgOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="bgLabelId" />
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilEmoticon from '~/utils/util-emoticon';

const size = defineModel<number>('size', { required: true });
const dark = defineModel<boolean>('dark', { required: true });

const { t } = useI18n();
const sizeLabelId = useId();
const bgLabelId = useId();

const sizeOptions = computed(() => UtilEmoticon.PREVIEW_SIZES.map((value) => ({ value, label: t('emoticon.preview.px', { size: value }) })));
const bgOptions = computed(() => [
    { value: false, label: t('emoticon.preview.light') },
    { value: true, label: t('emoticon.preview.dark') },
]);
</script>
