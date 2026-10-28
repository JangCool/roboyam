<template>
    <!--
        기본 테마의 홈 템플릿 — 기본형(community `TemplateClassic`). **겉모양만** 정한다: 가젯 모양 `classic`(구역 이름 줄 + 아래 「더보기」 줄, 평평하지 않은 가젯은 테두리 카드).
        배치(위치·크기·칸 수)는 운영자가 관리자 홈 배치 편집기에서 정하고 공통 격자 `HomeGadgetGrid` 가 그린다(목업 기본 배치 = 본문 넓은 열(하이라이트 · 인기글 · 최신글 · 갤러리) + 오른쪽 열(공구 · 소모임 · 운영 원칙), 24칸).
        props 계약 `HomeTemplate.types.ts`.
    -->
    <div class="@container min-w-0" data-template="default" data-testid="home-template">
        <HomeGadgetGrid :layout="layout" :gadgets="gadgets" />
    </div>
</template>

<script setup lang="ts">
import type { HomeTemplateProps } from '~/components/themes/HomeTemplate.types';

interface Props extends HomeTemplateProps {}

withDefaults(defineProps<Props>(), {});

// 한 열(모바일) — 구역 모양은 그대로, 갤러리만 가로로 미는 줄
provideHomeGadgetLook('classic', { mobile: { galleryScroll: true } });
</script>
