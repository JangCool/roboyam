<template>
    <!-- 보이는 대상 고르기 — 모두 · 비로그인만 · 로그인 · 등급 이상(등급 고르기) · 게시판 매니저·스텝. 폴더·항목 대화상자가 함께 쓴다 -->
    <div class="flex flex-col gap-1.5">
        <span :id="`${idBase}-label`" class="text-sm font-medium">{{ t('menuConfig.field.audience') }}</span>
        <div class="flex flex-wrap items-start gap-2">
            <Select
                :model-value="audience"
                :options="audienceOptions"
                option-label="label"
                option-value="value"
                :disabled="disabled"
                :invalid="Boolean(audienceError)"
                :aria-labelledby="`${idBase}-label`"
                class="min-w-44"
                @update:model-value="(v: MenuAudience) => emit('update:audience', v)"
            />
            <Select
                v-if="audience === 'GRADE_MIN'"
                :model-value="minGrade"
                :options="gradeOptions"
                option-label="label"
                option-value="value"
                :disabled="disabled"
                :invalid="Boolean(gradeError)"
                :placeholder="t('menuConfig.gradePlaceholder')"
                :aria-label="t('menuConfig.gradePlaceholder')"
                class="min-w-36"
                @update:model-value="(v: string) => emit('update:minGrade', v)"
            />
        </div>
        <p v-if="audienceError || gradeError" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ audienceError || gradeError }}</p>
        <p v-else-if="help" class="m-0 text-xs text-muted-color">{{ help }}</p>
    </div>
</template>

<script setup lang="ts">
import type { MenuAudience, MenuGradeOption } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

interface Props {
    audience: MenuAudience;
    minGrade: string | null;
    /** 등급 목록(서열 순서) */
    grades: MenuGradeOption[];
    disabled?: boolean;
    /** 보이는 대상 칸 오류(로그인 필요 화면 등) */
    audienceError?: string;
    /** 등급 칸 오류 */
    gradeError?: string;
    /** 평소 안내 */
    help?: string;
}

const props = withDefaults(defineProps<Props>(), { disabled: false, audienceError: '', gradeError: '', help: '' });
const emit = defineEmits<{ (e: 'update:audience', value: MenuAudience): void; (e: 'update:minGrade', value: string | null): void }>();

const { t } = useI18n();
const idBase = useId();

const audienceOptions = computed(() => UtilMenu.AUDIENCES.map((value) => ({ value, label: t(`menuConfig.audienceOption.${value}`) })));
const gradeOptions = computed(() => props.grades.map((g) => ({ value: g.gradeKey, label: t('menuConfig.audienceGrade', { grade: g.label }) })));
</script>
