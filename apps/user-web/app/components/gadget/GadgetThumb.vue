<template>
    <!-- 가젯의 사진 한 장 — 주소가 있으면 그림(비율을 지키고 잘라 채움), 없거나 못 불러오면 같은 크기의 빈 썸네일(`EmptyThumb` — 일반 글은 회색 면 + 사이트 이름, 뉴스 글은 남색 면 + 신문 아이콘, 사용자 확정 시안 2026-11-16).
         장식이라 대체 글자는 비운다(옆에 제목이 있다) -->
    <span class="gadget-thumb @container relative block overflow-hidden bg-surface-100 dark:bg-surface-800" :style="{ aspectRatio: ratio }" data-testid="home-thumb">
        <img v-if="src && !broken" :src="src" alt="" class="absolute inset-0 size-full object-cover" loading="lazy" decoding="async" @error="broken = true" />
        <EmptyThumb v-else :kind="variant === 'default' ? 'post' : variant" class="absolute inset-0" data-testid="home-thumb-empty" />
        <slot />
    </span>
</template>
<script setup lang="ts">
interface Props {
    /** 그림 주소 — 없으면 빈 썸네일 */
    src?: string | null;
    /** CSS `aspect-ratio`(예: `16 / 10`) */
    ratio?: string;
    /** 빈 썸네일 모양 — 일반 글(`default`) · 뉴스 게시판 글(`news` — `UtilBoard.isNews`) */
    variant?: 'default' | 'news' | 'groupBuy';
}

const props = withDefaults(defineProps<Props>(), { src: null, ratio: '16 / 10', variant: 'default' });

const broken = ref(false);
watch(
    () => props.src,
    () => {
        broken.value = false;
    },
);
</script>
