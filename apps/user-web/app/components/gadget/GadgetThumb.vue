<template>
    <!-- 가젯의 사진 한 장 — 주소가 있으면 그림(비율을 지키고 잘라 채움), 없거나 못 불러오면 같은 크기의 빈 자리(그림 표시 + 「이미지 없음」 — 칸이 아주 좁으면 그림 표시만, 2026-11-06 사용자). 장식이라 대체 글자는 비운다(옆에 제목이 있다) -->
    <span class="gadget-thumb @container relative block overflow-hidden bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: ratio }" data-testid="home-thumb">
        <img v-if="src && !broken" :src="src" alt="" class="absolute inset-0 size-full object-cover" loading="lazy" decoding="async" @error="broken = true" />
        <span v-else class="absolute inset-0 flex flex-col items-center justify-center gap-0.5 text-muted-color" aria-hidden="true" data-testid="home-thumb-empty">
            <i class="pi pi-image text-base" />
            <span v-if="label" class="whitespace-nowrap text-[0.625rem] leading-none @max-[3.5rem]:hidden">{{ t('home.noImage') }}</span>
        </span>
        <slot />
    </span>
</template>
<script setup lang="ts">
interface Props {
    /** 그림 주소 — 없으면 빈 자리 */
    src?: string | null;
    /** CSS `aspect-ratio`(예: `16 / 10`) */
    ratio?: string;
    /** 빈 자리에 「이미지 없음」 글자 — 끄면 그림 표시만(작은 썸네일, /tips 모바일 최신 글 64×48) */
    label?: boolean;
}

const props = withDefaults(defineProps<Props>(), { src: null, ratio: '16 / 10', label: true });

const { t } = useI18n();
const broken = ref(false);
watch(
    () => props.src,
    () => {
        broken.value = false;
    },
);
</script>
