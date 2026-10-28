<template>
    <!-- 공구 대표 그림 — 그림이 없으면 상자 아이콘. 목록 카드·내 주문 목록·주문 상세가 같이 쓴다 -->
    <div class="relative overflow-hidden rounded-lg bg-surface-100 dark:bg-surface-800" :class="SIZE[size]">
        <img v-if="src" :src="src" :alt="alt" class="size-full object-cover" loading="lazy" />
        <div v-else class="grid size-full place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-box text-xl" /></div>
        <slot />
    </div>
</template>

<script setup lang="ts">
interface Props {
    src?: string | null;
    alt?: string;
    /** `card`: 목록 카드 위 4:3 · `sm`: 줄 앞 작은 네모 */
    size?: 'card' | 'sm';
}

withDefaults(defineProps<Props>(), { src: null, alt: '', size: 'card' });

const SIZE = {
    card: 'aspect-[4/3] w-full',
    sm: 'size-16 shrink-0',
};
</script>
