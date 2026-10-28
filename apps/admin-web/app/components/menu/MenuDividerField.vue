<template>
    <!--
        공개 사이트 메뉴 노드의 구분선 고르기(없음 · 왼쪽 · 오른쪽) — 상단 가로 메뉴(첫 줄)에서 이 노드 옆에 세로 줄을 긋는다. 항목·폴더 대화상자가 함께 쓴다.
        최상위가 아니면 비활성으로 두고 「맨 위 메뉴에만」을 알린다(값은 그대로 두고 저장 때 `NONE` 으로 보낸다 — `UtilMenu.toDto`).
    -->
    <div class="flex flex-col gap-1.5">
        <span :id="labelId" class="text-sm font-medium">{{ t('menuConfig.field.divider') }}</span>
        <SelectButton
            :model-value="modelValue"
            :options="options"
            option-label="label"
            option-value="value"
            :allow-empty="false"
            size="small"
            :disabled="!topLevel"
            :aria-labelledby="labelId"
            :aria-describedby="helpId"
            class="self-start"
            @update:model-value="(v: MenuDivider) => emit('update:modelValue', v)"
        />
        <p :id="helpId" class="m-0 text-xs text-muted-color">{{ topLevel ? t('menuConfig.dividerHelp') : t('menuConfig.dividerTopOnly') }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MenuDivider } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

interface Props {
    modelValue: MenuDivider;
    /** 최상위 노드인가 — 아니면 비활성 */
    topLevel?: boolean;
}

withDefaults(defineProps<Props>(), { topLevel: true });
const emit = defineEmits<{ (e: 'update:modelValue', value: MenuDivider): void }>();

const { t } = useI18n();
const labelId = useId();
const helpId = useId();

const options = computed(() => UtilMenu.DIVIDERS.map((value) => ({ value, label: t(`menuConfig.divider.${value}`) })));
</script>
