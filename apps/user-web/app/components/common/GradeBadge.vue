<template>
    <!--
        등급 표시 — 도트 캐릭터 + 등급 이름 글자(grade-level-contract 3절 — 데스크톱 계정 메뉴·쪽지·차단·메모 목록 등). 칩(알약)은 없어졌다.
        `name` 을 끄면 도트만(작성자 줄 `PostAuthorLabel` — 닉네임 바로 뒤). 이름·도트는 등급 키로 사이트 정보의 등급 목록에서 찾는다(`useGrades`) — 모르는 키면 서버가 준 이름 글자만.
        `size="lg"` 는 큰 판(2배 — 공개 프로필·내 정보).
    -->
    <span v-if="look || (name && fallbackLabel)" class="inline-flex shrink-0 items-center gap-1" data-testid="grade-badge">
        <GradeSprite v-if="look" :sprite="look.sprite" :label="look.label" :size="size" />
        <span v-if="name" class="text-xs font-semibold text-muted-color">{{ look?.label ?? fallbackLabel }}</span>
    </span>
</template>

<script setup lang="ts">
/** 등급 표시 — 키(`SEEDLING`·`GRADE_n` …)와 서버가 준 이름(`label`)을 받는다 */
interface Props {
    grade?: string | null;
    label?: string | null;
    /** 등급 이름 글자를 도트 옆에 보인다 — 작성자 줄은 끈다(도트만) */
    name?: boolean;
    /** 도트 크기 — 작은 판 · 큰 판(2배) */
    size?: 'sm' | 'lg';
}

const props = withDefaults(defineProps<Props>(), {
    grade: null,
    label: null,
    name: true,
    size: 'sm',
});

const { gradeOf } = useGrades();
const look = computed(() => gradeOf(props.grade, props.label));
/** 모르는 키(등급 목록 없이 받은 운영자 등급) — 서버가 준 이름만 */
const fallbackLabel = computed(() => props.label ?? '');
</script>
