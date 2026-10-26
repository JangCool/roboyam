<template>
    <!-- 둘 중 하나를 고르는 카드 — 가입 방식(자유 가입/신청제)·오프라인 모임(없음/있음). 만들기·정보 수정이 같이 쓴다 -->
    <fieldset class="m-0 min-w-0 border-0 p-0" :disabled="disabled">
        <legend class="mb-1.5 p-0 text-sm font-medium">{{ legend }}</legend>
        <div class="flex flex-col gap-2" role="radiogroup" :aria-label="legend">
            <label
                v-for="option in options"
                :key="option.value"
                :for="`${uid}-${option.value}`"
                class="flex cursor-pointer items-start gap-3 rounded-lg border px-3 py-2.5"
                :class="option.value === modelValue ? 'border-primary bg-primary-50 dark:bg-primary-950/40' : 'border-surface bg-surface-0 dark:bg-surface-900'"
            >
                <RadioButton
                    :model-value="modelValue"
                    :value="option.value"
                    :input-id="`${uid}-${option.value}`"
                    :name="uid"
                    class="mt-0.5"
                    :data-testid="`${testid}-${option.value}`"
                    @update:model-value="(value: string) => emit('update:modelValue', value)"
                />
                <span class="min-w-0 flex-1 text-sm leading-snug">
                    <b class="font-semibold">{{ option.label }}</b>
                    <small v-if="option.hint" class="ml-1 text-xs text-muted-color">{{ option.hint }}</small>
                </span>
            </label>
        </div>
        <p v-if="error" role="alert" class="mt-1.5 text-xs text-red-600 dark:text-red-400">{{ error }}</p>
    </fieldset>
</template>

<script setup lang="ts">
import type { GroupOption } from '~/components/group/GroupOptionCards.types';

interface Props {
    legend: string;
    options: readonly GroupOption[];
    modelValue: string;
    disabled?: boolean;
    error?: string;
    testid?: string;
}

const props = withDefaults(defineProps<Props>(), {
    disabled: false,
    error: '',
    testid: 'group-option',
});

const emit = defineEmits<{ 'update:modelValue': [value: string] }>();

const uid = useId();
</script>
