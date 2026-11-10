<template>
    <!--
        다음식 홈 템플릿 — 카드 모듈(community `TemplateDaum`, 시안 `02-home-v2`). **겉모양만** 정한다: 가젯 모양 `card`(카드 + 높이 48 머리 + 오른쪽 「더보기 ›」),
        카드 값은 테마 역할 변수(`--gadget-card-*`, `main.css` 의 다음식 값 — 로보얌 테마는 카드 값은 함께 쓰고 템플릿은 `roboyam/RoboyamHome`).
        배치(위치·크기·칸 수)는 운영자가 관리자 홈 배치 편집기에서 정하고 공통 격자 `GadgetGrid` 가 그린다(서버 기본 배치 `HomeLayoutDefaults.daum()` = 로보얌과 같은 구성 — 본문 8칸 + 오른쪽 4칸, 12칸 — 넓은 화면에서 오른쪽 약 384px).
        props 계약 `HomeTemplate.types.ts`.
    -->
    <div class="@container min-w-0" data-template="daum" data-testid="home-template">
        <!-- 격자 위 머리(가젯 페이지 제목 카드 — 이 템플릿의 가젯 모양 안에서 그려야 제목 카드에 넣은 가젯이 같은 모양이 된다) -->
        <div v-if="$slots.lead" class="mb-(--gadget-gap)"><slot name="lead" /></div>
        <GadgetGrid :layout="layout" :gadgets="gadgets" />
    </div>
</template>

<script setup lang="ts">
import type { HomeTemplateProps } from '~/components/themes/HomeTemplate.types';

interface Props extends HomeTemplateProps {}

withDefaults(defineProps<Props>(), {});

// 한 열(모바일) — 흰 카드가 화면 폭 섹션(반경 0, `main.css`)이고 갤러리만 가로로 미는 줄. 순서는 로보얌과 같이 본문 열 먼저(기본 배치가 같은 구성)
provideGadgetLook('card', { mobile: { order: 'columns', galleryScroll: true } });
</script>
