<template>
    <!--
        표시색 고르기 줄 — [기본·자동] + 13색 원(게시판 표시색 목록·팔레트 — `UtilBoard.COLORS`·`UtilBoard.colorVar`) + 직접 고르기(PrimeVue `ColorPicker` + `#RRGGBB` 글자 칸). 고른 것에 윤곽.
        메뉴 글자 색(`MenuTextStyleField`)과 게시판 설정의 표시색(`pages/boards/[slug].vue`)이 같이 쓴다 — 값 규칙은 `UtilBoard.normalizeColor`(서버 `BoardFields.normalizeColor`).
        글자 칸은 입력 중 틀린 글자도 값으로 들고 있다가 쓰는 쪽 검사(메뉴 `TEXT_COLOR_FORMAT` · 게시판 `COLOR_FORMAT`)가 막는다 — 칸을 비우면 기본(null)으로 돌아간다.
        제목·도움말·오류 글은 쓰는 쪽이 그린다(`labelledby` 로 제목을 잇는다).
    -->
    <div role="radiogroup" :aria-labelledby="labelledby" class="flex flex-wrap items-center gap-1.5">
        <button
            type="button"
            role="radio"
            :aria-checked="model === null"
            :disabled="disabled"
            class="h-7 rounded-full border px-2.5 text-xs focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default"
            :class="[model === null ? 'border-(--p-text-color) font-semibold' : 'border-(--line) text-muted-color', disabled && model !== null ? 'opacity-50' : '']"
            :data-testid="`${testidPrefix}-default`"
            @click="model = null"
        >
            {{ defaultLabel }}
        </button>
        <button
            v-for="c in UtilBoard.COLORS"
            :key="c"
            type="button"
            role="radio"
            :aria-checked="model === c"
            :aria-label="t(`board.color.${c}`)"
            :title="t(`board.color.${c}`)"
            :disabled="disabled"
            class="grid size-7 place-items-center rounded-full border-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary disabled:cursor-default"
            :class="[model === c ? 'border-(--p-text-color)' : 'border-transparent', disabled && model !== c ? 'opacity-50' : '']"
            :data-testid="`${testidPrefix}-${c}`"
            @click="model = c"
        >
            <span class="grid size-5 place-items-center rounded-full text-white" :style="{ background: UtilBoard.colorVar(c) ?? undefined }" aria-hidden="true">
                <i v-if="model === c" class="pi pi-check text-[9px]" />
            </span>
        </button>
        <span class="mx-1 h-5 w-px bg-(--line)" aria-hidden="true" />
        <span class="inline-flex items-center gap-1.5 rounded-full border-2 py-0.5 pr-0.5 pl-2" :class="[custom ? 'border-(--p-text-color)' : 'border-transparent', disabled && !custom ? 'opacity-50' : '']">
            <span class="text-xs" :class="custom ? 'font-semibold' : 'text-muted-color'">{{ customLabel }}</span>
            <ColorPicker :model-value="pickerValue" format="hex" :disabled="disabled" :aria-label="pickLabel" :data-testid="`${testidPrefix}-pick`" @update:model-value="onPick" />
            <InputText
                :model-value="hexText"
                class="w-28 font-mono"
                maxlength="7"
                size="small"
                placeholder="#RRGGBB"
                :disabled="disabled"
                :invalid="custom && !UtilColor.isHex(model)"
                :aria-label="inputLabel"
                :data-testid="`${testidPrefix}-input`"
                @update:model-value="onHex"
            />
        </span>
    </div>
</template>

<script setup lang="ts">
import UtilBoard from '~/utils/util-board';
import UtilColor from '~/utils/util-color';

/** 고른 색 — null 기본(자동) · 13색 이름 · `#RRGGBB`(입력 중이면 틀린 글자일 수 있다) */
const model = defineModel<string | null>({ required: true });

interface Props {
    /** 줄 제목 요소 id(`aria-labelledby`) */
    labelledby: string;
    /** 첫 단추(색 없음) 글자 — 메뉴 「기본」 · 게시판 「자동」 */
    defaultLabel: string;
    /** 직접 고르기 이름 */
    customLabel: string;
    /** 색 고르개 접근성 이름 */
    pickLabel: string;
    /** `#RRGGBB` 글자 칸 접근성 이름 */
    inputLabel: string;
    /** `data-testid` 앞부분(`…-default` · `…-BLUE` · `…-pick` · `…-input`) */
    testidPrefix: string;
    disabled?: boolean;
}

withDefaults(defineProps<Props>(), { disabled: false });

const { t } = useI18n();

/** 직접 고른 색인가(기본·13색이 아닌 값 — 입력 중인 틀린 글자 포함) */
const custom = computed(() => model.value !== null && !(UtilBoard.COLORS as readonly string[]).includes(model.value));
/** 직접 고르기 글자 칸 — 직접 고른 색이면 그 값, 아니면 빈 칸 */
const hexText = computed(() => (custom.value ? (model.value ?? '') : ''));
/** 색 고르개가 보일 색(`#` 없이) — 직접 고른 색이 모양이 맞을 때만, 아니면 기본 빨강 */
const pickerValue = computed(() => (custom.value && UtilColor.isHex(model.value) ? model.value!.slice(1) : 'E5484D'));

function onPick(value: unknown): void {
    const hex = `#${String(value ?? '').replace(/^#/, '')}`;
    if (UtilColor.isHex(hex)) model.value = hex.toUpperCase();
}

function onHex(value: string | undefined): void {
    const text = (value ?? '').trim();
    if (!text) {
        model.value = null;
        return;
    }
    const hex = text.startsWith('#') ? text : `#${text}`;
    model.value = UtilColor.isHex(hex) ? hex.toUpperCase() : hex;
}
</script>
