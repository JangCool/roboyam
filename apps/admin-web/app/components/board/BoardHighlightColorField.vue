<template>
    <!--
        글 줄 숫자 강조 색 고르기(게시판 기본 설정 › 일반 › 표시 — `listHighlightColor`) — 추천 색 칩(빨강 기본 · 주황 · 분홍 · 보라 · 파랑 · 초록) + 직접 고르기(PrimeVue `ColorPicker` + `#rrggbb` 글자 칸) + 작은 미리보기.
        글자 칸은 입력 중 틀린 값도 들고 있다가 모양이 맞을 때만 값으로 넣는다(`GadgetColorField` 와 같은 방식). 공개 화면은 이 색을 밝은·어두운 바탕에서 읽히게 밝기만 맞춰 그린다(user-web `UtilColor.cssPair`).
    -->
    <div class="flex flex-col gap-2">
        <div role="radiogroup" :aria-labelledby="ariaLabelledby" class="flex flex-wrap items-center gap-1.5">
            <button
                v-for="swatch in UtilBoard.HIGHLIGHT_COLORS"
                :key="swatch.key"
                type="button"
                role="radio"
                :aria-checked="model === swatch.hex"
                :aria-label="t(`board.highlightSwatch.${swatch.key}`)"
                :title="t(`board.highlightSwatch.${swatch.key}`)"
                :disabled="disabled"
                class="grid size-7 place-items-center rounded-full border-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-not-allowed disabled:opacity-60"
                :class="model === swatch.hex ? 'border-(--line-strong)' : 'border-transparent'"
                :data-testid="`highlight-color-${swatch.key}`"
                @click="model = swatch.hex"
            >
                <span class="grid size-5 place-items-center rounded-full text-[10px] text-white" :style="{ background: swatch.hex }" aria-hidden="true">
                    <i v-if="model === swatch.hex" class="pi pi-check text-[9px]" />
                </span>
            </button>
            <span class="mx-1 h-5 w-px bg-(--line)" aria-hidden="true" />
            <ColorPicker :model-value="model.slice(1)" format="hex" :disabled="disabled" :aria-label="t('board.highlightCustomPick')" @update:model-value="onPick" />
            <InputText v-model="hexText" class="w-28 font-mono" maxlength="7" size="small" :disabled="disabled" :invalid="hexInvalid" :aria-label="t('board.highlightCustomInput')" @update:model-value="onHex" />
            <span v-if="model === UtilBoard.HIGHLIGHT_COLOR" class="text-xs text-muted-color">{{ t('board.highlightDefault') }}</span>
        </div>

        <!-- 미리보기 — 밝은·어두운 바탕에 강조된 추천·조회 숫자(공개 화면은 바탕에 맞게 밝기를 보정한다) -->
        <div class="flex flex-wrap gap-2" aria-hidden="true" data-testid="highlight-preview">
            <span v-for="tile in TILES" :key="tile.key" class="inline-flex items-center gap-2 rounded-md border border-(--line) px-2.5 py-1.5 text-xs tabular-nums" :style="{ background: tile.bg, color: tile.muted }">
                <span class="text-[10px]">{{ t(`board.highlightPreview.${tile.key}`) }}</span>
                <span class="inline-flex items-center gap-0.5 font-semibold" :style="{ color: model }"><i class="pi pi-thumbs-up-fill text-[10px]" />{{ likeSample }}</span>
                <span class="inline-flex items-center gap-0.5 font-semibold" :style="{ color: model }"><i class="pi pi-eye text-[10px]" />{{ viewSample }}</span>
                <span class="inline-flex items-center gap-0.5"><i class="pi pi-thumbs-up text-[10px]" />3</span>
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
import UtilBoard from '~/utils/util-board';

/** 강조 색 `#rrggbb` */
const model = defineModel<string>({ required: true });

interface Props {
    ariaLabelledby?: string;
    disabled?: boolean;
    /** 미리보기에 보일 추천·조회 수(지금 기준값) */
    likeSample?: number;
    viewSample?: number;
}
withDefaults(defineProps<Props>(), { ariaLabelledby: undefined, disabled: false, likeSample: 10, viewSample: 1000 });

const { t } = useI18n();

/** 미리보기 바탕 — 공개 사이트의 라이트·다크 카드 바탕(user-web `UtilColor.BG_LIGHT`·`BG_DARK`) */
const TILES = [
    { key: 'light', bg: '#ffffff', muted: '#6b7280' },
    { key: 'dark', bg: '#202125', muted: '#a1a1aa' },
] as const;

const HEX = /^#[0-9a-fA-F]{6}$/;
/* 직접 고르기 글자 칸 — 바깥에서 값이 바뀌면(칩·되돌리기) 맞춘다 */
const hexText = ref('');
const hexInvalid = computed(() => !HEX.test(hexText.value));
watch(model, (value) => (hexText.value = value), { immediate: true });

function onPick(value: unknown): void {
    const hex = `#${String(value ?? '').replace(/^#/, '')}`;
    if (HEX.test(hex)) model.value = hex.toLowerCase();
}

function onHex(value: string | undefined): void {
    const v = (value ?? '').trim();
    const hex = v.startsWith('#') ? v : `#${v}`;
    if (HEX.test(hex)) model.value = hex.toLowerCase();
}
</script>
