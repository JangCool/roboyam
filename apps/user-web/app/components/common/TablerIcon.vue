<template>
    <!-- Tabler 선 아이콘 한 개 — PrimeIcons 에 없는 모양(불꽃·확성기·할인 배지)을 가젯 제목 아이콘과 같은 Tabler 세트(`@tabler/icons` 3.49.0, MIT)의 선 그대로 그린다.
         공개 화면은 쓰는 것만 싣는다(아래 표). 크기는 부모가 클래스로 준다(PrimeIcons 글자 크기에 맞출 때 `size-[1.125rem]` 등). 장식이라 읽지 않는다 -->
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
        <path v-for="(p, i) in PATHS[name]" :key="i" :d="p.d" :fill="p.fill" />
    </svg>
</template>

<script setup lang="ts">
/** 이 부품이 그릴 수 있는 Tabler 이름 — 하단 탭·메뉴 바로 가기(`UtilMenu.itemIcon` 의 `tabler:<이름>`) */
export type TablerIconName = 'flame' | 'speakerphone' | 'rosette-discount' | 'building-store';

interface Props {
    name: TablerIconName;
}

defineProps<Props>();

/** `@tabler/icons` 3.49.0 `tabler-nodes-outline.json` 의 선 그대로(인기글 불꽃은 서버 `GadgetTablerIcons.FLAME` 과 같다) */
const PATHS: Readonly<Record<TablerIconName, ReadonlyArray<{ d: string; fill?: string }>>> = {
    flame: [
        {
            d: 'M12 10.941c2.333 -3.308 .167 -7.823 -1 -8.941c0 3.395 -2.235 5.299 -3.667 6.706c-1.43 1.408 -2.333 3.294 -2.333 5.588c0 3.704 3.134 6.706 7 6.706c3.866 0 7 -3.002 7 -6.706c0 -1.712 -1.232 -4.403 -2.333 -5.588c-2.084 3.353 -3.257 3.353 -4.667 2.235',
        },
    ],
    speakerphone: [
        { d: 'M18 8a3 3 0 0 1 0 6' },
        { d: 'M10 8v11a1 1 0 0 1 -1 1h-1a1 1 0 0 1 -1 -1v-5' },
        { d: 'M12 8l4.524 -3.77a.9 .9 0 0 1 1.476 .692v12.156a.9 .9 0 0 1 -1.476 .692l-4.524 -3.77h-8a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1h8' },
    ],
    'rosette-discount': [
        { d: 'M9 15l6 -6' },
        { d: 'M9 9.5a.5 .5 0 1 0 1 0a.5 .5 0 1 0 -1 0', fill: 'currentColor' },
        { d: 'M14 14.5a.5 .5 0 1 0 1 0a.5 .5 0 1 0 -1 0', fill: 'currentColor' },
        {
            d: 'M5 7.2a2.2 2.2 0 0 1 2.2 -2.2h1a2.2 2.2 0 0 0 1.55 -.64l.7 -.7a2.2 2.2 0 0 1 3.12 0l.7 .7a2.2 2.2 0 0 0 1.55 .64h1a2.2 2.2 0 0 1 2.2 2.2v1a2.2 2.2 0 0 0 .64 1.55l.7 .7a2.2 2.2 0 0 1 0 3.12l-.7 .7a2.2 2.2 0 0 0 -.64 1.55v1a2.2 2.2 0 0 1 -2.2 2.2h-1a2.2 2.2 0 0 0 -1.55 .64l-.7 .7a2.2 2.2 0 0 1 -3.12 0l-.7 -.7a2.2 2.2 0 0 0 -1.55 -.64h-1a2.2 2.2 0 0 1 -2.2 -2.2v-1a2.2 2.2 0 0 0 -.64 -1.55l-.7 -.7a2.2 2.2 0 0 1 0 -3.12l.7 -.7a2.2 2.2 0 0 0 .64 -1.55v-1',
        },
    ],
    'building-store': [
        { d: 'M3 21l18 0' },
        { d: 'M3 7v1a3 3 0 0 0 6 0v-1m0 1a3 3 0 0 0 6 0v-1m0 1a3 3 0 0 0 6 0v-1h-18l2 -4h14l2 4' },
        { d: 'M5 21l0 -10.15' },
        { d: 'M19 21l0 -10.15' },
        { d: 'M9 21v-4a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v4' },
    ],
};
</script>
