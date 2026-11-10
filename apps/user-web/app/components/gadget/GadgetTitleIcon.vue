<template>
    <!--
        가젯 머리의 제목 아이콘 한 개(`GadgetFrame` 이 쓴다 — 무엇을 그릴지는 `UtilGadget.titleIcon`, gadget-page-contract 「가젯 제목 아이콘」 2026-11-10).
          · `prime` PrimeIcons 글꼴(`pi pi-…`) · `tabler` 받은 path 조각을 요소로 만들어 그린다(글자 그대로 넣지 않는다 — `v-html` 없음, 아이콘마다 몇백 바이트라 공개 화면은 고른 것만 싣는다)
          · `svg` 올린 SVG — 모양만 따서(CSS mask) 글자색·고른 색으로 칠한다 · `image` 올린 PNG·WEBP — 원래 색 그대로
        바탕 `data-icon-bg`: `soft` = 옅은 네모(아이콘 색 12%, 다크 18% — `main.css`), `none` = 바탕 없음. 색은 껍데기가 `color`(고른 색 `--gx-ic` · 제목 색)로 준다. 장식이라 읽지 않는다.
    -->
    <span class="gadget-icon inline-grid shrink-0 place-items-center text-sm" :data-icon-bg="icon.bg === 'SOFT' ? 'soft' : 'none'" aria-hidden="true" data-testid="home-gadget-icon">
        <i v-if="icon.kind === 'prime'" :class="['pi', icon.name]" />
        <svg
            v-else-if="icon.kind === 'tabler'"
            class="block size-[1.15em]"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
            focusable="false"
        >
            <path v-for="(p, i) in icon.paths" :key="i" :d="p.d" :fill="p.fill" :stroke="p.stroke" :opacity="p.opacity" />
        </svg>
        <span v-else-if="icon.kind === 'svg'" class="block size-[1.15em] bg-current" :style="maskStyle" />
        <img v-else :src="icon.url ?? undefined" alt="" class="block size-[1.15em] object-contain" loading="lazy" decoding="async" />
    </span>
</template>

<script setup lang="ts">
import type { GadgetTitleIcon } from '~/composables/gadget/api/useGadgetApi.types';

interface Props {
    icon: GadgetTitleIcon;
}

const props = withDefaults(defineProps<Props>(), {});

/** 올린 SVG 의 모양으로 칠하기 — 주소는 읽을 때 CSS `url()` 에 넣을 수 있는 글자만 남겼다(`UtilGadget` `readIconUpload`) */
const maskStyle = computed(() => {
    if (!props.icon.url) return undefined;
    const image = `url(${JSON.stringify(props.icon.url)})`;
    return {
        maskImage: image,
        WebkitMaskImage: image,
        maskRepeat: 'no-repeat',
        WebkitMaskRepeat: 'no-repeat',
        maskPosition: 'center',
        WebkitMaskPosition: 'center',
        maskSize: 'contain',
        WebkitMaskSize: 'contain',
    };
});
</script>
