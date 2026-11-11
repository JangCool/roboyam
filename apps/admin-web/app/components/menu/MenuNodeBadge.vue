<template>
    <!-- 메뉴 이름 뒤 배지(알약) — 배지 색 = 글자 색(기본이면 주 색). 트리 줄(`MenuNodeRow` — 항목·폴더)·미리보기(`MenuPreviewList` — 항목·폴더 제목)·항목 창과 폴더 창의 글자 색 칸(`MenuTextStyleField`)이 같이 쓴다. 글자는 상하 중앙(components.md 2-4) -->
    <span v-if="text" class="inline-flex shrink-0 items-center justify-center rounded-full px-1.5 py-0.5 text-[11px] font-semibold leading-none" :style="UtilMenu.badgeStyle({ textColor })">
        <span class="relative top-[-0.09em]">{{ text }}</span>
    </span>
</template>

<script setup lang="ts">
import UtilMenu from '~/utils/util-menu';

interface Props {
    /** 배지 글자 — 비면 그리지 않는다 */
    badge?: string;
    /** 글자 색(null 기본 · 12색 이름 · `#RRGGBB`) */
    textColor?: string | null;
}

const props = withDefaults(defineProps<Props>(), { badge: '', textColor: null });

const text = computed(() => props.badge.trim());
</script>
