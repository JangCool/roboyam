<template>
    <!--
        말머리·상태 칩 위치 고르개(post-chip-position-contract) — 라디오 카드마다 이름·한 줄 설명·작은 미리보기(`BoardChipPositionPreview`).
        게시판 기본 설정(`/boards/defaults/general` — 사이트 기본 `listChipPosition`, 셋 중 하나)과 게시판 설정(`/boards/[slug]` — `chipPosition`, `inherit` 를 켜 「사이트 기본 따르기」= null 도 고른다)이 같이 쓴다.
    -->
    <div role="radiogroup" :aria-label="label" class="grid gap-3" :class="inherit ? 'sm:grid-cols-2 xl:grid-cols-4' : 'sm:grid-cols-3'">
        <label
            v-for="option in options"
            :key="option.key"
            class="flex flex-col gap-2 rounded-lg border p-3"
            :class="[selected === option.key ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', disabled ? '' : 'cursor-pointer']"
            :data-testid="`chip-position-${option.key}`"
        >
            <span class="flex items-start gap-2">
                <RadioButton :model-value="selected" :value="option.key" :name="name" :disabled="disabled" class="mt-0.5 shrink-0" @update:model-value="onPick" />
                <span class="flex min-w-0 flex-col gap-0.5">
                    <span class="text-sm font-medium">{{ option.title }}</span>
                    <span class="text-xs leading-relaxed text-muted-color">{{ option.help }}</span>
                </span>
            </span>
            <BoardChipPositionPreview :position="option.preview" />
        </label>
    </div>
</template>

<script setup lang="ts">
import type { BoardChipPosition } from '~/composables/board/api/useBoardApi.types';
import UtilBoard from '~/utils/util-board';

interface Props {
    /** 고른 값 — `null` = 사이트 기본 따르기(`inherit` 일 때만 뜻이 있다) */
    modelValue: BoardChipPosition | null;
    /** 묶음 이름(화면 읽기 프로그램) */
    label: string;
    /** 「사이트 기본 따르기」 카드를 보인다(게시판 설정) */
    inherit?: boolean;
    /** 지금 사이트 기본 — 「사이트 기본 따르기」 카드의 설명·미리보기 */
    inheritValue?: BoardChipPosition;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    inherit: false,
    inheritValue: 'ABOVE',
    disabled: false,
});

const emit = defineEmits<{ (e: 'update:modelValue', value: BoardChipPosition | null): void }>();

const { t } = useI18n();
const name = useId();
/** 라디오 값 — 「사이트 기본 따르기」(null)는 글자 `INHERIT` 로 들고 있는다 */
const INHERIT = 'INHERIT';
const selected = computed(() => props.modelValue ?? (props.inherit ? INHERIT : 'ABOVE'));

const options = computed(() => [
    ...(props.inherit
        ? [{ key: INHERIT, title: t('board.chipPositionInherit'), help: t('board.chipPositionInheritHelp', { name: t(`board.choice.listChipPosition.${props.inheritValue}`) }), preview: props.inheritValue }]
        : []),
    ...UtilBoard.CHIP_POSITIONS.map((p) => ({ key: p as string, title: t(`board.choice.listChipPosition.${p}`), help: t(`board.chipPositionHelp.${p}`), preview: p })),
]);

function onPick(value: unknown): void {
    emit('update:modelValue', value === INHERIT ? null : (value as BoardChipPosition));
}
</script>
