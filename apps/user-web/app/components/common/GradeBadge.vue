<template>
    <span class="inline-block rounded px-1.5 py-px text-xs font-semibold" :class="CLASS[grade]">{{ text }}</span>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';

/** 등급 뱃지 — 새싹·일반·단골·베테랑. 서버가 준 이름(`label`)이 있으면 그것을, 없으면 등급의 기본 이름을 보인다 */
interface Props {
    grade?: Grade;
    label?: string;
}

const props = withDefaults(defineProps<Props>(), {
    grade: 'SEEDLING',
    label: '',
});

const { t } = useI18n();

const CLASS: Record<Grade, string> = {
    SEEDLING: 'bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300',
    REGULAR: 'bg-blue-100 text-blue-800 dark:bg-blue-950 dark:text-blue-300',
    LOYAL: 'bg-purple-100 text-purple-800 dark:bg-purple-950 dark:text-purple-300',
    VETERAN: 'bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300',
};

/** 등급 → 기본 이름의 키 (글자는 그릴 때 `t` 로) */
const NAME_KEY: Record<Grade, string> = {
    SEEDLING: 'shell.gradeSeedling',
    REGULAR: 'shell.gradeRegular',
    LOYAL: 'shell.gradeLoyal',
    VETERAN: 'shell.gradeVeteran',
};

const text = computed(() => props.label || t(NAME_KEY[props.grade]));
</script>
