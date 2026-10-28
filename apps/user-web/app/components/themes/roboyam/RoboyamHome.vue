<template>
    <!--
        로보얌 홈 템플릿(사용자 결정 2026-10-28 — 「로봇과 AI, 쉽게 풀어 함께 이야기해요」: 뉴스 포털처럼 빽빽하지 않게, 친근하고 깔끔하게). **겉모양만** 정한다:
        가젯 모양 `card` + 세부 모양 `heroSplit`(하이라이트 큰 글 왼쪽 · 목록 오른쪽) · `galleryColumns: 4`. 카드 값·머리 아이콘 칩·「더보기」 글자는 `main.css` 의 `html[data-theme='roboyam']`.
        배치(위치·크기·칸 수)는 운영자가 관리자 홈 배치 편집기에서 정하고 공통 격자 `HomeGadgetGrid` 가 그린다.
        목업 기본 배치(`home-gadget-mock.ts` `roboyam()`, 24칸): 본문 넓은 열 17칸(하이라이트 · 인기글 순위 두 열 · [모바일 전용 공동구매] · 영상·짤 한 줄 4칸 · 방금 올라온 글 두 열) + 오른쪽 열 7칸(공동구매 · 인기 소모임).
        **한 열(모바일 — md 미만 또는 홈 폭 36rem 미만)** 모양(`mobile`, 사용자 요청 2026-10-28): 카드는 화면 좌우 끝까지 닿는 섹션(반경 0 · 그림자 없음 · 섹션 사이 10px · 안 여백 좌우 16 — 셸 본문 여백과 줄 맞춤),
        순서는 본문 열 먼저 → 오른쪽 열(`order: 'columns'`), 하이라이트 큰 글 사진은 카드 끝까지 + 나머지 글은 가로로 미는 카드 줄, 인기글은 큰 알약 탭 + 한 열 순위, 영상·짤은 가로로 미는 썸네일 줄(약 2.3장),
        공동구매는 가로로 미는 카드 줄, 방금 올라온 글은 한 열(제목 한 줄 + 메타), 인기 소모임은 가로로 미는 작은 카드. 「더보기 ›」는 머리 오른쪽 44px 터치 영역(모든 테마 공통).
        props 계약 `HomeTemplate.types.ts`.
    -->
    <div class="@container min-w-0" data-template="roboyam" data-testid="home-template">
        <HomeGadgetGrid :layout="layout" :gadgets="gadgets" />
    </div>
</template>

<script setup lang="ts">
import type { HomeTemplateProps } from '~/components/themes/HomeTemplate.types';

interface Props extends HomeTemplateProps {}

withDefaults(defineProps<Props>(), {});

provideHomeGadgetLook('card', {
    heroSplit: true,
    galleryColumns: 4,
    // 한 열(모바일) — 앱처럼: 본문 열 먼저(하이라이트 → 인기글 → 공동구매 → 영상·짤 → 방금 올라온 글 → 인기 소모임), 큰 글 사진은 화면 끝까지, 나머지는 가로로 미는 줄
    mobile: { order: 'columns', highlightScroll: true, heroBleed: true, galleryScroll: true, groupBuyScroll: true, groupsScroll: true },
});
</script>
