<template>
    <!--
        등급 도트 캐릭터(grade-level-contract 3절) — 픽셀 지도를 SVG 사각형으로 그린다(`UtilGradeSprite`, 이미지 파일 없음). 한 칸 = `scale` CSS px(정수배) · `shape-rendering: crispEdges`.
        관리자 화면은 작은 판 2배(`scale` 2)를 기본으로 쓰고 움직이지 않는다(첫 장면). 이름이 있으면 `role="img"` + `aria-label`·`title`, 없으면 장식(`aria-hidden`).
    -->
    <svg
        :width="image.width * scale"
        :height="image.height * scale"
        :viewBox="`0 0 ${image.width} ${image.height}`"
        shape-rendering="crispEdges"
        class="inline-block shrink-0 align-middle"
        :role="label ? 'img' : undefined"
        :aria-label="label || undefined"
        :aria-hidden="label ? undefined : 'true'"
        data-testid="grade-sprite"
    >
        <title v-if="label">{{ label }}</title>
        <rect v-for="(cell, i) in image.cells" :key="i" :x="cell.x" :y="cell.y" width="1" height="1" :fill="cell.color" />
    </svg>
</template>

<script setup lang="ts">
import type { GradeSprite } from '~/composables/grade/api/useGradeApi.types';
import UtilGradeSprite from '~/utils/util-grade-sprite';

interface Props {
    sprite: GradeSprite;
    /** 등급 이름 — 비우면 장식으로 숨긴다 */
    label?: string;
    /** 한 칸의 CSS px(정수) — 1 작은 판 · 2 관리자 미리보기 */
    scale?: number;
}

const props = withDefaults(defineProps<Props>(), { label: '', scale: 2 });

const image = computed(() => UtilGradeSprite.frame(props.sprite));
</script>
