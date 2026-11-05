<template>
    <!--
        로보티콘 팩 대표 그림 조각 — 심사 목록·팩 관리 목록·서랍 머리가 같이 쓴다. 그림은 `<img>` 로만 그린다(투명 배경이라 옅은 바탕을 깐다).
    -->
    <span class="inline-grid shrink-0 place-items-center overflow-hidden rounded-lg border border-(--line) bg-surface-50 dark:bg-surface-800" :style="{ width: `${size}px`, height: `${size}px` }">
        <img :src="src" :alt="alt" :width="size - 4" :height="size - 4" loading="lazy" decoding="async" class="object-contain" />
    </span>
</template>

<script setup lang="ts">
interface Props {
    src: string;
    /** 팩 이름 */
    alt: string;
    /** 한 변(px) */
    size?: number;
}

withDefaults(defineProps<Props>(), { size: 40 });
</script>
