<template>
    <!-- 팩 대표 그림 — 상점 카드·팩 상세·스튜디오·내 이모티콘이 같이 쓴다. 투명 그림이라 옅은 바탕 위에 둔다. 그림이 없으면(임시 팩) 웃는 얼굴 자리표시.
         바탕·모서리는 포인트 상점 미리보기 조각(`PointShopItemThumb`)과 같다. `sm` 은 그 조각의 줄 크기(`compact` — 48px)와 같다 -->
    <span class="inline-grid shrink-0 place-items-center overflow-hidden rounded-lg bg-surface-50 dark:bg-surface-950" :class="SIZE[size]">
        <img v-if="src && !broken" :src="src" :alt="alt" class="size-full object-contain" loading="lazy" decoding="async" @error="broken = true" />
        <i v-else class="pi pi-face-smile text-muted-color" :class="ICON[size]" aria-hidden="true" />
    </span>
</template>

<script setup lang="ts">
type CoverSize = 'sm' | 'md' | 'lg' | 'fill';

interface Props {
    src?: string | null;
    /** 대체 글 — 옆에 팩 이름이 글자로 있으면 비운다 */
    alt?: string;
    size?: CoverSize;
}

withDefaults(defineProps<Props>(), {
    src: null,
    alt: '',
    size: 'md',
});

const broken = ref(false);

const SIZE: Record<CoverSize, string> = {
    sm: 'size-12',
    md: 'size-16',
    lg: 'size-28',
    fill: 'aspect-square w-full',
};

const ICON: Record<CoverSize, string> = {
    sm: 'text-base',
    md: 'text-xl',
    lg: 'text-3xl',
    fill: 'text-3xl',
};
</script>
