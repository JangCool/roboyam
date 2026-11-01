<template>
    <!-- 상품 썸네일 — 링크 OG 자동 이미지(또는 글쓴이가 올린 이미지). 없으면 빈 자리 아이콘. 외부 주소라 늦게 읽고 주소를 넘기지 않는다 -->
    <span class="relative inline-grid shrink-0 place-items-center overflow-hidden rounded-md border border-(--line) bg-surface-100 dark:bg-surface-800" :class="sizeClass">
        <img v-if="url && !broken" :src="url" :alt="alt" loading="lazy" referrerpolicy="no-referrer" class="size-full object-cover" :class="dim ? 'opacity-50 grayscale' : ''" @error="broken = true" />
        <i v-else class="pi pi-image text-muted-color" :class="size === 'lg' ? 'text-2xl' : 'text-sm'" :aria-label="t('hotdeal.noImage')" role="img" />
    </span>
</template>

<script setup lang="ts">
interface Props {
    url?: string | null;
    alt?: string;
    size?: 'sm' | 'lg';
    /** 종료·기간 지남 — 흐리게 */
    dim?: boolean;
}

const props = withDefaults(defineProps<Props>(), { url: null, alt: '', size: 'sm', dim: false });

const { t } = useI18n();
const broken = ref(false);
const sizeClass = computed(() => (props.size === 'lg' ? 'size-28' : 'size-10'));

watch(
    () => props.url,
    () => {
        broken.value = false;
    },
);
</script>
