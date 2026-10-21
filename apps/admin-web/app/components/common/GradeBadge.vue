<template>
    <!--
        등급 뱃지 — 회원 관리 표·회원 상세·역할 관리·등급 설정 미리보기가 **같은 모양**으로 쓰는 공통 부품. 등급 설정에서 고른 색·아이콘을 그린다.
        모양: 둥근 알약 + (아이콘) + 이름 글자. 연한 색 바탕에 진한 글자·아이콘(라이트), 어두운 면에 밝은 글자(다크) — 색은 PrimeVue 색 토큰이고 `light-dark()` 로 테마를 따른다(`UtilGrade.badgeStyle`, 코드에 색을 적지 않는다).
        아이콘은 장식(`aria-hidden`)이고 의미는 이름 글자가 전한다. 색만으로 구분하지 않는다.
    -->
    <span class="grade-badge" :class="size === 'small' ? 'grade-badge--small' : ''" :style="style">
        <i v-if="icon" :class="icon" class="grade-badge__icon" aria-hidden="true" />
        <span class="grade-badge__label">{{ label }}</span>
    </span>
</template>

<script setup lang="ts">
import UtilGrade from '~/utils/util-grade';

interface Props {
    /** 등급 이름 */
    label: string;
    /** 색 이름(`UtilGrade.BADGE_COLORS`) — 없거나 모르는 이름이면 중립(gray) */
    color?: string;
    /** PrimeIcons 클래스(예: `pi pi-star`) — 없으면 아이콘 없이 이름만 */
    icon?: string | null;
    size?: 'normal' | 'small';
}

const props = withDefaults(defineProps<Props>(), { color: 'gray', icon: null, size: 'normal' });

const style = computed(() => UtilGrade.badgeStyle(props.color));
</script>

<style scoped>
/*
 * 아이콘과 글자를 같은 기준선(baseline)에 세운다 — 가운데 맞춤(`align-items: center`)은 아이콘 상자와 글자 상자의 소수점 위치가 달라
 * 화면 세로 위치가 .25~.5 일 때 한쪽만 1px 높게 찍혔다(실측). 같은 기준선이면 둘이 항상 같이 반올림된다.
 * 한글 글꼴은 글자가 줄 상자 중앙보다 약 0.09em 아래에 앉아 알약 안에서 아래로 붙어 보이므로, 위 여백을 그만큼 줄이고 아래 여백을 늘려 둘을 함께 올린다(글자만 올리지 않는다).
 */
.grade-badge {
    display: inline-flex;
    align-items: baseline;
    gap: 0.3em;
    max-width: 100%;
    padding: calc(0.2em - 0.09em) 0.7em calc(0.2em + 0.09em);
    border-radius: 9999px;
    background: var(--gb-bg);
    color: var(--gb-fg);
    font-size: 0.75rem;
    font-weight: 600;
    line-height: 1.25;
    white-space: nowrap;
}
.grade-badge--small {
    padding: calc(0.1em - 0.045em) 0.55em calc(0.1em + 0.045em);
    font-size: 0.6875rem;
}
.grade-badge__icon {
    flex: none;
    font-size: 0.85em;
}
.grade-badge__label {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
}
</style>
