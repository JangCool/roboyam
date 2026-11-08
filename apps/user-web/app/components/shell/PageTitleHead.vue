<template>
    <!--
        묶음 메뉴로 들어가는 화면(핫 로토픽·커뮤니티 가젯 페이지 · 팁·강좌·사용기 · 소모임)의 제목 머리 — 한 모양으로 맞춘다(사용자 2026-11-08 「팁·강좌·사용기 제목·설명과 같은 글자 크기·여백」).
        제목(xl 굵게) + 설명 한 줄(sm 흐리게) + 오른쪽 자리(`actions` — 게시판 칩·검색·만들기 단추 등). 넓으면 한 줄(아래 맞춤), 좁으면 위아래.
        `divider` 를 켜면 아래 굵은 밑줄(같은 카드 안에서 머리와 내용을 지면처럼 끊는다 — 팁·강좌·사용기·소모임). 머리만 담은 카드(가젯 페이지)는 끈다.
    -->
    <header class="flex min-w-0 flex-col gap-2 md:flex-row md:items-end md:gap-4" :class="divider ? 'border-b-2 border-[var(--p-text-color)] pb-3' : ''">
        <div class="min-w-0 flex-1">
            <h1 class="m-0 break-words text-xl font-bold leading-tight tracking-tight" :style="titleStyle" :data-testid="titleTestid"><slot name="title">{{ title }}</slot></h1>
            <p v-if="description" class="m-0 mt-1 text-sm text-muted-color">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="flex min-w-0 shrink-0 items-center gap-1.5">
            <slot name="actions" />
        </div>
    </header>
</template>

<script setup lang="ts">
import type { StyleValue } from 'vue';

interface Props {
    title: string;
    description?: string | null;
    /** 아래 굵은 밑줄(같은 카드 안에 내용이 이어질 때) */
    divider?: boolean;
    titleTestid?: string;
    /** 제목 글자 꾸미기(게시판 색 등). 글자 대신 그릴 것은 `title` 슬롯 */
    titleStyle?: StyleValue;
}

withDefaults(defineProps<Props>(), { description: null, divider: false, titleTestid: undefined, titleStyle: undefined });
</script>
