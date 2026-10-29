<template>
    <!-- 공구 대표 그림 — 그림이 없으면 상자 아이콘. 목록 카드·내 주문 목록·주문 상세가 같이 쓴다. 모서리는 테마의 사진 모서리(`gadget-thumb` — `--gadget-thumb-radius`) -->
    <div class="gadget-thumb relative overflow-hidden bg-surface-100 dark:bg-surface-800" :class="SIZE[size]">
        <img v-if="src" :src="src" :alt="alt" class="size-full object-cover" :class="zoom ? 'transition-transform duration-300 group-hover:scale-[1.04]' : ''" loading="lazy" />
        <div v-else class="grid size-full place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-box" :class="size === 'sm' ? 'text-xl' : 'text-3xl'" /></div>
        <slot />
    </div>
</template>

<script setup lang="ts">
interface Props {
    src?: string | null;
    alt?: string;
    /** `card`: 4:3 · `square`: 목록 카드 위 1:1(쇼핑몰 상품 격자) · `sm`: 줄 앞 작은 네모 */
    size?: 'card' | 'square' | 'sm';
    /** 부모 링크(`group`)를 가리키면 그림을 살짝 키운다(목록 카드) */
    zoom?: boolean;
}

withDefaults(defineProps<Props>(), { src: null, alt: '', size: 'card', zoom: false });

const SIZE = {
    card: 'aspect-[4/3] w-full',
    square: 'aspect-square w-full',
    sm: 'size-16 shrink-0',
};
</script>
