<template>
    <!-- 딜 상품 썸네일 — 이미지가 없으면 꼬리표 아이콘 자리 그림. 크기·비율은 쓰는 곳이 `class` 로 준다 -->
    <span class="block shrink-0 overflow-hidden rounded-md bg-surface-100 dark:bg-surface-800">
        <img v-if="url && !broken" :src="url" alt="" class="size-full object-cover" loading="lazy" @error="broken = true" />
        <span v-else class="grid size-full place-items-center text-muted-color" :title="t('hotdeal.noImage')">
            <i class="pi pi-tag" aria-hidden="true" />
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
