<template>
    <!--
        등급 도트 캐릭터(grade-level-contract 3절) — 픽셀 지도를 색별 SVG 경로로 그린다(그림 파일·GIF 없음, `crispEdges`, 한 칸 = 1 CSS px × 배율 정수).
        작은 판(`size="sm"`, 한 칸 1px — 기본 12×12 · 전설 16×14 · 신화 20×14): 글 목록·댓글·글 보기 머리·피드·가젯 작성자 줄 · 큰 판(`size="lg"`, 2배): 공개 프로필·내 정보 닉네임 옆.
        전설·신화는 4장면(220ms)을 돈다 — 장면을 모두 그려 두고 투명도만 바꾼다(`main.css` `.grade-sprite-frame`, `prefers-reduced-motion` 이면 첫 장면에서 멈춤).
        읽는 프로그램에는 등급 이름(`role="img"` + `aria-label`), 마우스를 올리면 이름(`<title>`)
    -->
    <svg
        role="img"
        :aria-label="label"
        :width="art.width * scale"
        :height="art.height * scale"
        :viewBox="`0 0 ${art.width} ${art.height}`"
        shape-rendering="crispEdges"
        class="inline-block shrink-0"
        :data-sprite="sprite"
        data-testid="grade-sprite"
    >
        <title>{{ label }}</title>
        <path v-for="layer in art.base" :key="layer.color" :d="layer.d" :fill="layer.color" />
        <g v-for="(frame, index) in art.frames" :key="index" class="grade-sprite-frame" :data-frame="index" :style="{ '--gs-delay': delayOf(index) }">
            <path v-for="layer in frame" :key="layer.color" :d="layer.d" :fill="layer.color" />
        </g>
    </svg>
</template>

<script setup lang="ts">
import type { GradeSpriteKey } from '~/composables/grade/api/useGradeApi.types';
import UtilGradeSprite from '~/utils/util-grade-sprite';

interface Props {
    sprite: GradeSpriteKey;
    /** 등급 이름 — 읽는 프로그램·마우스 올림 */
    label: string;
    /** 작은 판(한 칸 1px) · 큰 판(2배) */
    size?: 'sm' | 'lg';
}

const props = withDefaults(defineProps<Props>(), { size: 'sm' });

const art = computed(() => UtilGradeSprite.art(props.sprite));
const scale = computed(() => (props.size === 'lg' ? 2 : 1));

/** 장면 k 가 [k×220, (k+1)×220)ms 에 보이도록 4장면 주기를 앞당긴다(첫 장면은 0) */
function delayOf(index: number): string {
    const cycle = UtilGradeSprite.FRAME_MS * 4;
    return `${index === 0 ? 0 : index * UtilGradeSprite.FRAME_MS - cycle}ms`;
}
</script>
