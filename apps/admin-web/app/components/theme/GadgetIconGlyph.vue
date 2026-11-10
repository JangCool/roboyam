<template>
    <!--
        아이콘 한 개 그리기(관리자 — 고르기 격자 · 미리보기). 공개 화면 user-web `GadgetTitleIcon` 과 같은 방식:
        PrimeIcons 글꼴 · Tabler path 조각(요소로 만든다 — `v-html` 없음) · 올린 SVG(모양만 따서 글자색으로 칠함 — CSS mask) · 올린 PNG·WEBP(원래 색). 장식이라 읽지 않는다.
    -->
    <i v-if="prime" :class="['pi', prime]" aria-hidden="true" />
    <svg v-else-if="paths" class="block size-[1.15em]" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" focusable="false" aria-hidden="true">
        <path v-for="(p, i) in paths" :key="i" :d="p.d" :fill="p.fill" :stroke="p.stroke" :opacity="p.opacity" />
    </svg>
    <span v-else-if="upload && upload.svg" class="block size-[1.15em] bg-current" :style="maskStyle" aria-hidden="true" />
    <img v-else-if="upload" :src="upload.url" alt="" class="block size-[1.15em] object-contain" />
</template>

<script setup lang="ts">
import type { GadgetIconUpload, GadgetTablerPath } from '~/composables/home/api/useHomeLayoutApi.types';

interface Props {
    /** PrimeIcons 이름(`pi-…`) */
    prime?: string | null;
    /** Tabler path 조각 */
    paths?: readonly GadgetTablerPath[] | null;
    /** 올린 아이콘 */
    upload?: GadgetIconUpload | null;
}

const props = withDefaults(defineProps<Props>(), { prime: null, paths: null, upload: null });

const maskStyle = computed(() => {
    if (!props.upload) return undefined;
    const image = `url(${JSON.stringify(props.upload.url)})`;
    return { maskImage: image, WebkitMaskImage: image, maskRepeat: 'no-repeat', WebkitMaskRepeat: 'no-repeat', maskPosition: 'center', WebkitMaskPosition: 'center', maskSize: 'contain', WebkitMaskSize: 'contain' };
});
</script>
