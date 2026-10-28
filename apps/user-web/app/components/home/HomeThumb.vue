<template>
    <!-- 가젯의 사진 한 장 — 주소가 있으면 그림(비율을 지키고 잘라 채움), 없거나 못 불러오면 같은 크기의 빈 자리(그림 표시). 장식이라 대체 글자는 비운다(옆에 제목이 있다) -->
    <span class="gadget-thumb relative block overflow-hidden bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: ratio }" data-testid="home-thumb">
        <img v-if="src && !broken" :src="src" alt="" class="absolute inset-0 size-full object-cover" loading="lazy" decoding="async" @error="broken = true" />
        <span v-else class="absolute inset-0 grid place-items-center text-muted-color" aria-hidden="true"><i class="pi pi-image text-base" /></span>
        <slot />
    </span>
</template>

<script setup lang="ts">
interface Props {
    /** 그림 주소 — 없으면 빈 자리 */
    src?: string | null;
    /** CSS `aspect-ratio`(예: `16 / 10`) */
    ratio?: string;
}

const props = withDefaults(defineProps<Props>(), { src: null, ratio: '16 / 10' });

const broken = ref(false);
watch(
    () => props.src,
    () => {
        broken.value = false;
    },
);
</script>
