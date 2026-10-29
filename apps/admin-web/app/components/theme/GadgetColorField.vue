<template>
    <!--
        가젯 꾸미기의 색 고르기 — 자동(null) · 프리셋 12색(user-web `UtilColor` 프리셋 이름 = 게시판 표시색 이름) · 직접 고르기(`#rrggbb`).
        ⚙ 설정 창(`GadgetSettingsDialog`)의 제목 색·카드 색 두 칸이 쓴다. 색 동그라미는 게시판 표시색과 같은 팔레트 값(`UtilBoard.colorVar`)으로 그린다(공개 화면의 색은 user-web 이 정한다).
        직접 고르기는 PrimeVue `ColorPicker` + `#rrggbb` 글자 칸 — 글자가 모양이 맞을 때만 값으로 넣는다.
    -->
    <div class="flex flex-wrap items-center gap-2">
        <Select :model-value="mode" :options="options" option-label="label" option-value="value" :input-id="inputId" :aria-labelledby="ariaLabelledby" class="w-48" @update:model-value="onMode">
            <template #value="{ value }">
                <span class="inline-flex items-center gap-1.5 text-sm">
                    <span class="size-3 shrink-0 rounded-full" :class="swatch(value) ? '' : 'border border-dashed border-(--line-strong)'" :style="swatch(value) ? { background: swatch(value)! } : undefined" aria-hidden="true" />
                    {{ labelOf(value) }}
                </span>
            </template>
            <template #option="{ option }">
                <span class="inline-flex items-center gap-1.5 text-sm">
                    <span class="size-3 shrink-0 rounded-full" :class="swatch(option.value) ? '' : 'border border-dashed border-(--line-strong)'" :style="swatch(option.value) ? { background: swatch(option.value)! } : undefined" aria-hidden="true" />
                    {{ option.label }}
                </span>
            </template>
        </Select>
        <template v-if="mode === 'CUSTOM'">
            <ColorPicker :model-value="hexBare" format="hex" :aria-label="t('homeLayout.colorCustomPick')" @update:model-value="onPick" />
            <InputText v-model="hexText" class="w-28 font-mono" maxlength="7" :invalid="hexInvalid" :aria-label="t('homeLayout.colorCustomInput')" @update:model-value="onHex" />
        </template>
    </div>
</template>

<script setup lang="ts">
import type { BoardColor } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';
import UtilGadgetStyle from '~/utils/util-gadget-style';

/** 색 — 프리셋 이름 · `#rrggbb` · null(자동) */
const model = defineModel<string | null>({ required: true });

interface Props {
    /** 자동(null)의 이름 — 「자동(주 색상)」·「제목 색 따름」 */
    autoLabel: string;
    inputId?: string;
    ariaLabelledby?: string;
}
const props = withDefaults(defineProps<Props>(), { inputId: undefined, ariaLabelledby: undefined });

const { t } = useI18n();

/** 직접 고를 때 처음 색 — 기본 주 색상 계열 */
const CUSTOM_START = '#1c5fcf';

const mode = computed(() => (model.value === null ? 'AUTO' : UtilGadgetStyle.isHex(model.value) ? 'CUSTOM' : model.value));

const options = computed(() => [
    { value: 'AUTO', label: props.autoLabel },
    ...UtilGadgetStyle.COLOR_PRESETS.map((value) => ({ value, label: t(`board.color.${value}`) })),
    { value: 'CUSTOM', label: t('homeLayout.colorCustom') },
]);

function labelOf(value: unknown): string {
    return options.value.find((o) => o.value === value)?.label ?? '';
}

/** 동그라미 색 — 자동이면 null(점선 동그라미) */
function swatch(value: unknown): string | null {
    if (value === 'CUSTOM') return UtilGadgetStyle.isHex(model.value) ? model.value : null;
    return typeof value === 'string' && UtilGadgetStyle.COLOR_PRESETS.includes(value) ? UtilBoard.colorVar(value as BoardColor) : null;
}

function onMode(value: string): void {
    if (value === 'AUTO') model.value = null;
    else if (value === 'CUSTOM') model.value = UtilGadgetStyle.isHex(model.value) ? model.value : CUSTOM_START;
    else model.value = value;
}

/* 직접 고르기 — 글자 칸은 입력 중 틀린 값도 들고 있다가 모양이 맞을 때만 넣는다 */
const hexText = ref('');
const hexInvalid = computed(() => mode.value === 'CUSTOM' && !UtilGadgetStyle.isHex(hexText.value));
const hexBare = computed(() => (UtilGadgetStyle.isHex(model.value) ? model.value!.slice(1) : CUSTOM_START.slice(1)));

// 바깥에서 값이 바뀌면(창을 다시 열기 · 색 고르기 창) 글자 칸을 맞춘다
watch(
    model,
    (value) => {
        if (UtilGadgetStyle.isHex(value)) hexText.value = value!;
    },
    { immediate: true },
);

function onPick(value: unknown): void {
    const hex = `#${String(value ?? '').replace(/^#/, '')}`;
    if (UtilGadgetStyle.isHex(hex)) model.value = hex.toLowerCase();
}

function onHex(value: string | undefined): void {
    const v = (value ?? '').trim();
    const hex = v.startsWith('#') ? v : `#${v}`;
    if (UtilGadgetStyle.isHex(hex)) model.value = hex.toLowerCase();
}
</script>
