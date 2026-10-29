<template>
    <!--
        네이버형 홈 템플릿 — 언론사 카드형(community `TemplateNaver`, 시안 `23-theme-naver`). **겉모양만** 정한다: 가젯 모양 `card`, 카드 값은 테마 역할 변수(`main.css` 의 `html[data-theme='naver']`).
        배치(위치·크기·칸 수)는 운영자가 관리자 홈 배치 편집기에서 정하고 공통 격자 `HomeGadgetGrid` 가 그린다(서버 기본 배치 `HomeLayoutDefaults.naver()` = 로보얌과 같은 구성 — 본문 17칸(하이라이트 사진 카드 한 줄 · 인기글 · 영상·짤 · 방금 올라온 글) + 오른쪽 7칸(공구 · 소모임), 24칸).
        게시판 카드 격자의 열 수는 가젯이 자기 칸 폭으로 정한다(1 → 2 → 3열). props 계약 `HomeTemplate.types.ts`.
    -->
    <div class="@container min-w-0" data-template="naver" data-testid="home-template">
        <HomeGadgetGrid :layout="layout" :gadgets="gadgets" />
    </div>
</template>

<script setup lang="ts">
import type { HomeTemplateProps } from '~/components/themes/HomeTemplate.types';

interface Props extends HomeTemplateProps {}

withDefaults(defineProps<Props>(), {});

// 한 열(모바일) — 게시판 카드 격자와 갤러리를 가로로 미는 카드 줄로. 순서는 로보얌과 같이 본문 열 먼저(기본 배치가 같은 구성)
provideHomeGadgetLook('card', { mobile: { order: 'columns', boardCardsScroll: true, galleryScroll: true } });
</script>
