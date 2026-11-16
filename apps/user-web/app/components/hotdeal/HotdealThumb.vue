<template>
    <!-- 딜 상품 썸네일 — 이미지가 없거나 못 불러오면 빈 썸네일(`EmptyThumb` 핫딜 — 회색 면 + 쇼핑백 아이콘 + 사이트 이름, 작은 칸은 아이콘만, 사용자 확정 시안 2026-11-16). 크기·비율은 쓰는 곳이 `class` 로 준다 -->
    <span class="block shrink-0 overflow-hidden rounded-md bg-surface-100 dark:bg-surface-800">
        <img v-if="url && !broken" :src="url" alt="" class="size-full object-cover" loading="lazy" @error="broken = true" />
        <span v-else class="block size-full" :title="t('hotdeal.noImage')">
            <EmptyThumb kind="deal" />
            <span class="sr-only">{{ t('hotdeal.noImage') }}</span>
        </span>
    </span>
</template>

<script setup lang="ts">
interface Props {
    url?: string | null;
}

const props = withDefaults(defineProps<Props>(), { url: null });

const { t } = useI18n();
const broken = ref(false);
watch(
    () => props.url,
    () => {
        broken.value = false;
    },
);
</script>
