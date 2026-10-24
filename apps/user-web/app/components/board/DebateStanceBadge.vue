<template>
    <!--
        하이라이트 토론의 입장 배지 — 찬성(파랑) · 반대(빨강). 댓글 머리와 「내 입장」 칩이 같이 쓴다. 글자는 상하좌우 중앙(한글 내려앉음 보정은 글자 요소에만 — components.md 2-4)
        색은 `main.css` 「토론 입장 색」 토큰(`stance-*-soft`, admin-web 의견 관리와 같은 값 — 라이트·다크 대비 6.8:1 이상). 색만으로 가르지 않게 「찬성」·「반대」 글자를 늘 쓴다
    -->
    <span class="inline-flex shrink-0 items-center justify-center rounded px-1.5 py-1 text-[0.6875rem] font-semibold leading-none" :class="stance === 'PRO' ? 'stance-pro-soft' : 'stance-con-soft'">
        <span class="relative top-[-0.09em]">{{ t(`comment.stance.${stance}`) }}</span>
    </span>
</template>

<script setup lang="ts">
import type { DebateStance } from '~/composables/board/api/useDebateApi.types';

interface Props {
    stance: DebateStance;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
