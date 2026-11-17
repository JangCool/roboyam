<template>
    <!--
        회원 칭호 도트 그림(member-title-contract 「아이콘」) — 12×12 픽셀 지도를 색별 SVG 경로로 그린다(`UtilTitleIcon`, 이미지 파일 없음, `crispEdges`, 한 칸 = `scale` CSS px 정수배). 움직임 없음.
        크기: 작성자 줄 1배(12px — 등급 도트와 같다) · 공개 프로필·내 정보 2배(24px) · 도감·명예의 전당·관리자 3~4배.
        이름(`label`)이 있으면 `role="img"` + `aria-label`·`<title>`, 비우면 장식(`aria-hidden` — 옆에 칭호 이름 글자가 같이 보이는 칩 안).
    -->
    <svg
        :width="12 * size"
        :height="12 * size"
        viewBox="0 0 12 12"
        shape-rendering="crispEdges"
        class="title-icon"
        :role="label ? 'img' : undefined"
        :aria-label="label || undefined"
        :aria-hidden="label ? undefined : 'true'"
        :data-icon="icon"
        data-testid="title-icon"
    >
        <title v-if="label">{{ label }}</title>
        <path v-for="layer in layers" :key="layer.color" :d="layer.d" :fill="layer.color" />
    </svg>
</template>

<script setup lang="ts">
import UtilTitleIcon, { type TitleTier } from '../utils/util-title-icon';

interface Props {
    /** 그림 키(서버 `icon` — 모르는 키면 메달) */
    icon: string;
    /** 단계 색(단계 없는 칭호·그림은 null) */
    tier?: TitleTier | null;
    /** 칭호 이름 — 읽는 프로그램·마우스 올림. 비우면 장식 */
    label?: string;
    /** 배율(정수 1~4) */
    scale?: number;
}

const props = withDefaults(defineProps<Props>(), { tier: null, label: '', scale: 1 });

const layers = computed(() => UtilTitleIcon.art(props.icon, props.tier));
/** 정수배로만 — 1~4 */
const size = computed(() => Math.min(4, Math.max(1, Math.round(props.scale))));
</script>

<style scoped>
.title-icon {
    display: inline-block;
    flex-shrink: 0;
    vertical-align: middle;
}
</style>
