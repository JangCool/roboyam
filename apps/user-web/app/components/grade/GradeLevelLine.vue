<template>
    <!-- 등급 + 레벨 한 줄 — 큰 판 도트(2배) + 「등급 이름 · Lv.N」(grade-level-contract 4절 — 공개 프로필 닉네임 옆·내 정보·등급 안내). 레벨이 없으면(옛 응답·남의 비공개 프로필) 등급 이름만 -->
    <span class="inline-flex shrink-0 items-center gap-1.5" data-testid="grade-level-line">
        <GradeBadge :grade="grade" :label="label" :name="false" size="lg" />
        <span class="text-xs font-normal text-muted-color" data-testid="grade-level-text">{{ text }}</span>
    </span>
</template>

<script setup lang="ts">
interface Props {
    /** 등급 키 */
    grade: string;
    /** 서버가 준 등급 이름 */
    label?: string | null;
    /** 레벨 — 없으면 숨긴다 */
    level?: number | null;
}

const props = withDefaults(defineProps<Props>(), { label: null, level: null });

const { t } = useI18n();
const { gradeOf } = useGrades();
const name = computed(() => gradeOf(props.grade, props.label)?.label ?? props.label ?? '');
const text = computed(() => (props.level ? t('grade.nameWithLevel', { grade: name.value, level: props.level }) : name.value));
</script>
