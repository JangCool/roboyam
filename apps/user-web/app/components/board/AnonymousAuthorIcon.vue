<template>
    <!--
        익명 작성자 자리의 사람 물음표 아이콘(anonymous-board-contract) — `PostAuthorLabel` 이 익명 작성자(`kind === 'ANONYMOUS'`)를 그릴 때만 쓴다.
        PrimeIcons 에 같은 모양이 없어 Tabler Icons `user-question`(MIT, outline 3.49.0 — 관리자 앱이 쓰는 판)의 path 를 그대로 옮겼다. 장식이라 읽지 않는다(이름 「익명」이 읽힌다).
        `circle`: 회색 원 안에 그린다(글 보기 머리·목록의 프로필 동그라미 자리 — `ProfileBorderAvatar` 와 같은 크기). 끄면 글자 크기의 아이콘만.
    -->
    <span v-if="circle" class="inline-grid shrink-0 place-items-center rounded-full bg-surface-200 text-muted-color dark:bg-surface-700" :class="size === '2xs' ? 'size-5' : 'size-6'" aria-hidden="true" data-testid="author-anonymous-avatar">
        <svg class="block size-[0.9rem]" v-bind="svgAttrs"><path v-for="d in PATHS" :key="d" :d="d" /></svg>
    </span>
    <svg v-else class="block size-[1.1em] shrink-0" v-bind="svgAttrs" aria-hidden="true" data-testid="author-anonymous-icon"><path v-for="d in PATHS" :key="d" :d="d" /></svg>
</template>

<script setup lang="ts">
interface Props {
    /** 회색 원 안에 그린다 */
    circle?: boolean;
    /** 원 크기 — `xs` 글 보기 머리·글 줄(24px) · `2xs` 촘촘한 줄(20px) */
    size?: 'xs' | '2xs';
}

withDefaults(defineProps<Props>(), { circle: false, size: 'xs' });

/** Tabler `user-question` outline — 머리 · 몸 · 물음표 점 · 물음표 곡선 */
const PATHS = [
    'M8 7a4 4 0 1 0 8 0a4 4 0 0 0 -8 0',
    'M6 21v-2a4 4 0 0 1 4 -4h3.5',
    'M19 22v.01',
    'M19 19a2.003 2.003 0 0 0 .914 -3.782a1.98 1.98 0 0 0 -2.414 .483',
] as const;

const svgAttrs = {
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    'stroke-width': 2,
    'stroke-linecap': 'round',
    'stroke-linejoin': 'round',
    focusable: 'false',
} as const;
</script>
